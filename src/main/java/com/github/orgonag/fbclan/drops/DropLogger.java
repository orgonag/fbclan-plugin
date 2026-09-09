package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.clan.ClanContent;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.DrawManager;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The drop pipeline. An item is logged when it is <b>valuable</b> (GE
 * value at or above the threshold), <b>rare</b> (1 in X or rarer from
 * this source AND worth the rare minimum), on the clan's <b>notable</b>
 * list, or a <b>pet</b>. It then optionally grabs an annotated
 * screenshot and fans out to the clan drop log and the user's Discord
 * webhook. Also serves the drop-log tab's recent rows.
 */
@Slf4j
@Singleton
public class DropLogger
{
    private static final String BUCKET = "drop-screenshots";
    private static final String COLUMNS = "rsn,npc_name,item_name,item_id,ge_value,quantity,created_at,screenshot_url,rarity";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Client client;
    private final FinalBossConfig config;
    private final Clan clan;
    private final ClanContent content;
    private final DropRates rates;
    private final Supabase db;
    private final ItemManager itemManager;
    private final DrawManager drawManager;
    private final PartyService partyService;
    private final ScheduledExecutorService executor;
    private final okhttp3.OkHttpClient http;

    @Inject
    public DropLogger(Client client, FinalBossConfig config, Clan clan, ClanContent content, DropRates rates,
                      Supabase db, ItemManager itemManager, DrawManager drawManager, PartyService partyService,
                      ScheduledExecutorService executor, okhttp3.OkHttpClient http)
    {
        this.client = client;
        this.config = config;
        this.clan = clan;
        this.content = content;
        this.rates = rates;
        this.db = db;
        this.itemManager = itemManager;
        this.drawManager = drawManager;
        this.partyService = partyService;
        this.executor = executor;
        this.http = http;
    }

    // Every legitimate screenshot URL starts with this; the drop-log tab
    // refuses to open anything else (the drops table is anon-writable).
    public static String screenshotPrefix()
    {
        return Supabase.publicUrl(BUCKET, "");
    }

    // ------------------------------------------------------------ inputs

    // `source` is what the Loot Tracker reported (NPC, raid, chest); it
    // doubles as the key into the rate table. Client thread.
    public void onLoot(String source, Collection<ItemStack> items)
    {
        String rsn = clan.rsn();
        if (!clan.canUpload() || !config.enableDropLogging())
        {
            return;
        }
        long threshold = DropRules.threshold(config.dropThresholdGp());
        int rareDenominator = config.rareDropThreshold();
        long rareMin = Math.max(0, config.rareDropMinValueGp());
        Set<String> notable = content.notableItems();
        String display = DropRules.displaySource(source);

        List<Drop> drops = new ArrayList<>();
        for (ItemStack stack : items)
        {
            int id = stack.getId();
            int qty = stack.getQuantity();
            long value = (long) itemManager.getItemPrice(id) * qty;
            String name = itemManager.getItemComposition(id).getName();
            OptionalDouble rarity = rates.rarity(source, id, qty);
            if (!rarity.isPresent() && !display.equals(source))
            {
                rarity = rates.rarity(display, id, qty);
            }
            boolean blocked = DropRules.neverLogged(name);
            boolean valuable = !blocked && DropRules.valuable(value, threshold);
            boolean rare = !blocked && DropRules.rare(rarity, rareDenominator) && value >= rareMin;
            boolean isNotable = notable.contains(Names.itemKey(name));
            if (valuable || rare || isNotable)
            {
                drops.add(new Drop(name, id, value, qty, rarity.isPresent() ? rarity.getAsDouble() : null));
            }
        }
        if (!drops.isEmpty())
        {
            dispatch(rsn, display, drops);
        }
    }

    // Pets only announce themselves in chat. Untradeable, so they bypass
    // the value rules. Client thread.
    public void onChatMessage(ChatMessage event)
    {
        String rsn = clan.rsn();
        if (event.getType() != ChatMessageType.GAMEMESSAGE || !clan.canUpload() || !config.enableDropLogging()
            || !DropRules.isPetMessage(event.getMessage()))
        {
            return;
        }
        String name = "Pet";
        if (DropRules.isFollowerPet(event.getMessage()))
        {
            NPC follower = client.getFollower();
            if (follower != null && follower.getName() != null)
            {
                name = "Pet (" + follower.getName() + ")";
            }
        }
        else if (DropRules.isDuplicatePet(event.getMessage()))
        {
            name = "Pet (duplicate)";
        }
        dispatch(rsn, "Pet drop", Collections.singletonList(new Drop(name, 0, 0, 1, null)));
    }

    // ------------------------------------------------------------ reads

    // Explicit column list so a future column can't silently ship to
    // every viewer. Executor.
    public JsonArray recent(int limit)
    {
        return db.get("drops", "select=" + COLUMNS + "&order=created_at.desc&limit=" + limit);
    }

    // ------------------------------------------------------------ pipeline

    private void dispatch(String rsn, String source, List<Drop> drops)
    {
        if (!config.enableDropScreenshots())
        {
            executor.submit(() -> submit(rsn, source, drops, null));
            return;
        }
        // One screenshot covers every qualifying item from this drop: the
        // frame is grabbed on the next render, everything else happens on
        // the executor.
        List<String> party = partyNames();
        int bestItem = drops.stream().max(Comparator.comparingLong(d -> d.value)).get().itemId;
        drawManager.requestNextFrameListener(frame -> executor.submit(() ->
            submit(rsn, source, drops, uploadScreenshot(frame, rsn, party, bestItem))));
    }

    private void submit(String rsn, String source, List<Drop> drops, String screenshotUrl)
    {
        for (Drop d : drops)
        {
            JsonObject row = new JsonObject();
            row.addProperty("rsn", rsn);
            row.addProperty("npc_name", source);
            row.addProperty("item_name", d.name);
            row.addProperty("item_id", d.itemId);
            row.addProperty("ge_value", d.value);
            row.addProperty("quantity", d.quantity);
            if (screenshotUrl != null)
            {
                row.addProperty("screenshot_url", screenshotUrl);
            }
            if (d.rarity != null && d.rarity > 0 && d.rarity <= 1)
            {
                row.addProperty("rarity", d.rarity);
            }
            db.insert("drops", row);
            discord(rsn, source, d);
        }
    }

    private void discord(String rsn, String source, Drop d)
    {
        String url = config.discordWebhookUrl();
        if (url == null || url.isEmpty())
        {
            return;
        }
        if (!url.startsWith("https://discord.com/api/webhooks/") && !url.startsWith("https://discordapp.com/api/webhooks/"))
        {
            log.warn("Discord webhook URL is not a Discord webhook, skipping");
            return;
        }
        String value = d.value > 0 ? " (" + DropRules.formatGp(d.value) + " GP)" : "";
        String rate = d.rarity != null && d.rarity > 0 ? " [" + DropRules.formatRarity(d.rarity) + "]" : "";
        JsonObject embed = new JsonObject();
        embed.addProperty("title", rsn + " received a drop!");
        embed.addProperty("description", d.name + value + rate + " from " + source);
        embed.addProperty("color", 0xFFD700);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        Request request = new Request.Builder().url(url).post(RequestBody.create(JSON, payload.toString())).build();
        try (Response response = http.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Discord webhook failed: {}", response.code());
            }
        }
        catch (IOException e)
        {
            log.warn("Discord webhook error", e);
        }
    }

    // ------------------------------------------------------------ screenshots

    private String uploadScreenshot(Image frame, String rsn, List<String> party, int itemId)
    {
        try
        {
            BufferedImage image = new BufferedImage(frame.getWidth(null), frame.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.drawImage(frame, 0, 0, null);
            if (!party.isEmpty())
            {
                String line = "Party members: " + String.join(", ", party);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(new Color(0, 0, 0, 160));
                g.fillRect(6, 8, fm.stringWidth(line) + 8, fm.getHeight() + 4);
                g.setColor(Color.WHITE);
                g.drawString(line, 10, 10 + fm.getAscent());
            }
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            String path = rsn.replaceAll("[^A-Za-z0-9_-]", "_") + "/" + System.currentTimeMillis() + "_" + itemId + ".png";
            return db.upload(BUCKET, path, out.toByteArray(), "image/png");
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to upload drop screenshot", e);
            return null;
        }
    }

    // Names of the local RuneLite party, already visible to everyone in
    // it; no party identifiers leave the client.
    private List<String> partyNames()
    {
        List<String> names = new ArrayList<>();
        if (partyService.isInParty())
        {
            for (PartyMember m : partyService.getMembers())
            {
                if (m.getDisplayName() != null && !m.getDisplayName().isEmpty())
                {
                    names.add(m.getDisplayName());
                }
            }
        }
        return names;
    }

    @Value
    private static class Drop
    {
        String name;
        int itemId;
        long value;
        int quantity;
        Double rarity;
    }
}
