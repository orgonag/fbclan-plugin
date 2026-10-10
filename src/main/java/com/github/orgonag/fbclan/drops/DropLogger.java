package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.clan.ClanContent;
import com.github.orgonag.fbclan.core.ApiResult;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.RuneLite;
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
 * price of a single item at or above the threshold), <b>rare</b> (1 in X or rarer from
 * this source AND worth the rare minimum), on the clan's <b>notable</b>
 * list, or a <b>pet</b>; never when it is on the clan's <b>ignored</b>
 * list. It then optionally grabs an annotated
 * screenshot and fans out to the clan drop log and the user's Discord
 * webhook. Also serves the drop-log tab's recent rows.
 */
@Slf4j
@Singleton
public class DropLogger
{
    private static final String BUCKET = "drop-screenshots";
    private static final String COLUMNS = "rsn,npc_name,item_name,item_id,ge_value,quantity,occurred_at,screenshot_url,rarity";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    // The only shape the server accepts (fb_attach_screenshot): "<uuid>.png".
    private static final Pattern SCREENSHOT_FILE = Pattern.compile("[0-9a-f-]{36}\\.png");

    private final DropOutbox outbox = new DropOutbox(RuneLite.RUNELITE_DIR.toPath().resolve("finalboss-outbox"));
    private final AtomicBoolean flushing = new AtomicBoolean();
    private final AtomicBoolean framePending = new AtomicBoolean();
    // Rows waiting for the next rendered frame; one screenshot covers all of them.
    private final List<JsonObject> awaitingFrame = new ArrayList<>();
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
    // Pet attribution state; client thread only.
    private String lastSource;
    private int lastSourceTick = Integer.MIN_VALUE / 2;
    private String petMessage;
    private int petTick = -1;
    private long petLogin;
    private String petSource;
    private int petItem;
    private int newItemId;
    private int newItemTick = -1;
    private Map<Integer, Integer> inventoryCounts = Collections.emptyMap();

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
        this.http = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build();
    }

    // A file in the plugin's own screenshot bucket. The drop viewer opens nothing else.
    public static boolean isScreenshot(String url)
    {
        String prefix = Supabase.publicUrl(BUCKET, "");
        return url != null && url.startsWith(prefix) && SCREENSHOT_FILE.matcher(url.substring(prefix.length())).matches();
    }

    // ------------------------------------------------------------ inputs

    // `source` is what was killed or opened (NPC, raid, chest). Each kind
    // of loot reaches here from exactly one event (see FinalBossPlugin).
    // Client thread.
    public void onLoot(String source, Collection<ItemStack> items)
    {
        String rsn = clan.rsn();
        if (!clan.canUpload() || !config.enableDropLogging())
        {
            return;
        }
        long threshold = Math.max(config.dropThresholdGp(), DropRules.MIN_THRESHOLD_GP);
        int rareDenominator = config.rareDropThreshold();
        long rareMin = Math.max(0, config.rareDropMinValueGp());
        Set<String> notable = content.notableItems();
        Set<String> ignored = content.ignoredItems();
        String display = DropRules.displaySource(source);
        lastSource = display;
        lastSourceTick = client.getTickCount();
        if (petTick >= 0 && petSource == null && lastSourceTick - petTick <= 1) petSource = display;

        List<Drop> drops = new ArrayList<>();
        for (ItemStack stack : items)
        {
            int id = stack.getId();
            int qty = stack.getQuantity();
            if (qty <= 0) continue;
            long unitPrice = itemManager.getItemPrice(id);
            long value = unitPrice * qty;
            String name = itemManager.getItemComposition(id).getName();
            // The clan's ignore list beats every rule, the notable list included.
            String key = Names.normalize(name);
            if (ignored.contains(key)) continue;
            boolean blocked = DropRules.neverLogged(name);
            // One item must pass on its own: a stack of runes or seeds never
            // becomes "valuable" through quantity.
            boolean valuable = !blocked && unitPrice >= threshold;
            boolean isNotable = notable.contains(key);
            // The rate table is only consulted for items that could be logged
            // (or labelled): most loot is common and cheap.
            if (!valuable && !isNotable && (blocked || value < rareMin)) continue;
            OptionalDouble rarity = rates.rarity(display, id, qty);
            boolean rare = !blocked && DropRules.rare(rarity, rareDenominator) && value >= rareMin;
            if (valuable || rare || isNotable)
            {
                drops.add(new Drop(name, id, value, qty, rarity.isPresent() ? rarity.getAsDouble() : null));
            }
        }
        if (!drops.isEmpty()) dispatch(rsn, display, drops);
    }

    // Pets only announce themselves in chat. Untradeable, so they bypass
    // the value rules. The message, the loot, the follower and a backpack
    // item can arrive in any order, so a pet is settled two ticks after
    // its message (onTick). Its source is the loot within a tick of the
    // message, its item the one untradeable that appeared within a tick
    // of it: whichever came first, taken when it arrives. Client thread.
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE || !DropRules.isPetMessage(event.getMessage())) return;
        petMessage = event.getMessage();
        petTick = client.getTickCount();
        petLogin = clan.snapshot().getGeneration();
        petSource = petTick - lastSourceTick <= 1 ? lastSource : null;
        petItem = petTick - newItemTick <= 1 ? newItemId : 0;
    }

    // Notes an untradeable item that just appeared: a backpack pet. Client thread.
    public void onInventoryChanged(ItemContainer inventory)
    {
        Map<Integer, Integer> now = new HashMap<>();
        for (Item item : inventory.getItems())
        {
            if (item.getId() > 0) now.merge(item.getId(), item.getQuantity(), Integer::sum);
        }
        int added = 0;
        for (Map.Entry<Integer, Integer> e : now.entrySet())
        {
            if (e.getValue() > inventoryCounts.getOrDefault(e.getKey(), 0)
                && !itemManager.getItemComposition(e.getKey()).isTradeable())
            {
                // Two at once: no telling which is the pet.
                added = added == 0 ? e.getKey() : -1;
            }
        }
        inventoryCounts = now;
        if (added == 0) return;
        newItemId = added;
        newItemTick = client.getTickCount();
        if (petTick >= 0 && petItem == 0 && newItemTick - petTick <= 1) petItem = added;
    }

    // Settles a waiting pet. The name comes from the new follower, or for
    // a backpack pet from its item (else it is logged unnamed). Client thread.
    public void onTick()
    {
        if (petTick < 0 || client.getTickCount() - petTick < 2) return;
        String message = petMessage;
        petTick = -1;
        // Still the login that got the message.
        if (!clan.canUpload() || !config.enableDropLogging() || clan.snapshot().getGeneration() != petLogin) return;
        String source = petSource != null ? petSource : "Pet drop";
        String name = "Pet";
        int itemId = 0;
        if (DropRules.isDuplicatePet(message))
        {
            name = "Pet (duplicate)";
        }
        else if (DropRules.isFollowerPet(message))
        {
            NPC follower = client.getFollower();
            if (follower != null && follower.getName() != null) name = "Pet (" + follower.getName() + ")";
        }
        else if (petItem > 0)
        {
            itemId = petItem;
            name = "Pet (" + itemManager.getItemComposition(itemId).getName() + ")";
        }
        dispatch(clan.rsn(), source, Collections.singletonList(new Drop(name, itemId, 0, 1, null)));
    }

    // ------------------------------------------------------------ reads

    // The newest 50 drops. Explicit column list so a future column can't
    // silently ship to every viewer. Rows on the clan's ignore list are
    // dropped (older clients may still log them), so the list is loaded
    // first if the startup fetch missed it. Executor.
    public JsonArray recent()
    {
        JsonArray rows = db.getOrNull("drops", "select=" + COLUMNS + "&order=created_at.desc,id.desc&limit=50");
        if (rows == null) throw new IllegalStateException("drop feed unavailable");
        content.loadMissing();
        Set<String> ignored = content.ignoredItems();
        JsonArray shown = new JsonArray();
        for (JsonElement el : rows)
        {
            JsonObject row = el.getAsJsonObject();
            if (ignored.contains(Names.normalize(Supabase.str(row, "item_name")))) continue;
            String path = Supabase.str(row, "screenshot_url");
            if (SCREENSHOT_FILE.matcher(path).matches()) row.addProperty("screenshot_url", Supabase.publicUrl(BUCKET, path));
            shown.add(row);
        }
        return shown;
    }

    // ------------------------------------------------------------ pipeline

    private void dispatch(String rsn, String source, List<Drop> drops)
    {
        Session session = clan.snapshot();
        String worldType = clan.onStandardWorld() ? "standard" : "special";
        String occurred = Instant.now().toString();
        List<JsonObject> rows = new ArrayList<>();
        for (Drop d : drops)
        {
            JsonObject row = new JsonObject();
            row.addProperty("event_id", UUID.randomUUID().toString());
            row.addProperty("rsn", rsn); row.addProperty("npc_name", source);
            row.addProperty("item_name", d.name); row.addProperty("item_id", d.itemId);
            row.addProperty("ge_value", d.value); row.addProperty("quantity", d.quantity);
            row.addProperty("world_type", worldType); row.addProperty("occurred_at", occurred);
            if (d.rarity != null && d.rarity > 0 && d.rarity <= 1) row.addProperty("rarity", d.rarity);
            rows.add(row);
        }
        executor.submit(() -> {
            // Saved even if the player has since logged out; flush sends it at their next login.
            for (JsonObject row : rows)
            {
                try { outbox.add(session, row); }
                catch (IOException e) { log.warn("Could not persist a drop for retry", e); }
            }
            flush();
        });
        // Screenshots are optional enrichment; no rendered frame is needed to
        // save a drop. Loot from two kills in the same tick shares one frame.
        if (!config.enableDropScreenshots()) return;
        synchronized (awaitingFrame)
        {
            awaitingFrame.addAll(rows);
            if (!framePending.compareAndSet(false, true)) return;
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<String> party = partyNames();
        drawManager.requestNextFrameListener(frame -> {
            List<JsonObject> batch;
            synchronized (awaitingFrame)
            {
                batch = new ArrayList<>(awaitingFrame);
                awaitingFrame.clear();
                framePending.set(false);
            }
            if (System.nanoTime() > deadline || !screenshotting(session)) return;
            BufferedImage copy = new BufferedImage(frame.getWidth(null), frame.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = copy.createGraphics();
            try { graphics.drawImage(frame, 0, 0, null); } finally { graphics.dispose(); }
            executor.submit(() -> {
                if (!screenshotting(session)) return;
                String path = uploadScreenshot(copy, party);
                if (path == null) return;
                for (JsonObject row : batch)
                {
                    if (!screenshotting(session)) return;
                    JsonObject attachment = new JsonObject();
                    attachment.addProperty("p_event", Supabase.str(row, "event_id"));
                    attachment.addProperty("p_path", path);
                    if (db.rpc("fb_attach_screenshot", attachment)) continue;
                    // Not there yet (the outbox hasn't sent it): send it now, then attach.
                    JsonObject payload = new JsonObject(); payload.add("p_row", row);
                    if (db.rpc("fb_submit_drop", payload)) db.rpc("fb_attach_screenshot", attachment);
                }
            });
        });
    }

    private boolean screenshotting(Session session)
    {
        return clan.current(session) && config.enableDropLogging() && config.enableDropScreenshots();
    }

    /** Worker only; retry all pending drops for this verified profile. */
    public void flush()
    {
        Session session = clan.snapshot();
        if (!clan.current(session) || !config.enableDropLogging() || !flushing.compareAndSet(false, true)) return;
        try
        {
            int unexplained = 0;
            for (JsonObject row : outbox.pending(session))
            {
                if (!clan.current(session) || !config.enableDropLogging()) break;
                JsonObject payload = new JsonObject(); payload.add("p_row", row);
                ApiResult result = db.rpcResult("fb_submit_drop", payload);
                if (!result.successful())
                {
                    // Only a refusal sets a drop aside. Offline or a server fault: everything waits.
                    // Anything else stays queued, and a few of those in a row end this attempt.
                    if (result.retryable() || (!result.refused() && ++unexplained >= 3)) break;
                    if (!result.refused()) continue;
                    outbox.reject(Supabase.str(row, "event_id"));
                    log.warn("Drop rejected by database; saved locally for inspection: {}", result.message());
                    continue;
                }
                outbox.remove(Supabase.str(row, "event_id"));
                // Webhooks are deliberately best effort, independent of the durable clan record.
                if (clan.current(session) && config.enableDropLogging()) discord(Supabase.str(row, "rsn"), Supabase.str(row, "npc_name"),
                    new Drop(Supabase.str(row, "item_name"), row.get("item_id").getAsInt(), row.get("ge_value").getAsLong(), row.get("quantity").getAsInt(),
                        Supabase.has(row, "rarity") ? row.get("rarity").getAsDouble() : null));
            }
        }
        catch (IOException | RuntimeException e) { log.warn("Drop retry failed", e); }
        finally { flushing.set(false); }
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

    // `image` is this drop's own copy of the frame; the party line is drawn onto it.
    private String uploadScreenshot(BufferedImage image, List<String> party)
    {
        try
        {
            Graphics2D g = image.createGraphics();
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
            String path = UUID.randomUUID() + ".png";
            return db.upload(BUCKET, path, out.toByteArray(), "image/png") == null ? null : path;
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
