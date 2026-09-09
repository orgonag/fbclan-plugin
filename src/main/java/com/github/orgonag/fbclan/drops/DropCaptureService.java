package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.ClanSession;
import com.github.orgonag.fbclan.FinalBossConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.DrawManager;

/**
 * The drop pipeline. A loot item is logged when ANY of these hold:
 * <ul>
 * <li><b>Valuable</b>: GE price x quantity is at or above the GP threshold.</li>
 * <li><b>Rare</b>: its drop rate from this source is 1 in X or rarer (X from
 *     config, 0 = off) AND it's worth at least the rare-drop minimum value
 *     (so 1/128 rune junk stays out; set the minimum to 0 to log every
 *     rare drop regardless of value). Rates come from the bundled OSRS
 *     Wiki drop table; drops without table data can't qualify this way.</li>
 * <li><b>Notable</b>: on the clan-curated notable-items list.</li>
 * <li><b>Pet</b>: announced in chat (pets never appear in loot events).</li>
 * </ul>
 * Clue scrolls are always skipped. It then optionally grabs an annotated
 * screenshot and fans out to the Supabase drop log and the user's Discord
 * webhook.
 */
public class DropCaptureService
{
    private final Client client;
    private final FinalBossConfig config;
    private final ClanSession session;
    private final ItemManager itemManager;
    private final DrawManager drawManager;
    private final PartyService partyService;
    private final ScheduledExecutorService executor;
    private final NotableItemsService notableItemsService;
    private final DropRarityService rarityService;
    private final DropLogService dropService;
    private final DiscordWebhookService discordService;
    private final DropScreenshotService screenshotService;

    public DropCaptureService(Client client, FinalBossConfig config, ClanSession session,
        ItemManager itemManager, DrawManager drawManager, PartyService partyService,
        ScheduledExecutorService executor, NotableItemsService notableItemsService,
        DropRarityService rarityService, DropLogService dropService,
        DiscordWebhookService discordService, DropScreenshotService screenshotService)
    {
        this.client = client;
        this.config = config;
        this.session = session;
        this.itemManager = itemManager;
        this.drawManager = drawManager;
        this.partyService = partyService;
        this.executor = executor;
        this.notableItemsService = notableItemsService;
        this.rarityService = rarityService;
        this.dropService = dropService;
        this.discordService = discordService;
        this.screenshotService = screenshotService;
    }

    // Pets never appear in loot events — the game only announces them in
    // chat. They are untradeable (GE value 0), so they bypass the GP
    // threshold and are always logged while drop logging is enabled.
    public void handlePetChatMessage(ChatMessage event)
    {
        String rsn = session.getRsn();
        if (event.getType() != ChatMessageType.GAMEMESSAGE || !session.canUpload()
            || !config.enableDropLogging())
        {
            return;
        }
        String message = event.getMessage();
        if (!DropTrackingService.isPetMessage(message))
        {
            return;
        }

        // The pet's name is only knowable when it spawned as the player's
        // follower. A pet that went to the backpack — or a duplicate — can't
        // be resolved via getFollower(), which may be a previously-owned pet.
        String itemName = "Pet";
        if (DropTrackingService.isFollowerPetMessage(message))
        {
            NPC follower = client.getFollower();
            if (follower != null && follower.getName() != null)
            {
                itemName = "Pet (" + follower.getName() + ")";
            }
        }
        else if (DropTrackingService.isDuplicatePetMessage(message))
        {
            itemName = "Pet (duplicate)";
        }

        dispatchDrops(rsn, "Pet drop",
            Collections.singletonList(new PendingDrop(itemName, 0, 0, 1, null)));
    }

    // `sourceName` is what the Loot Tracker / loot manager reported (an NPC
    // name, raid, chest, ...). It doubles as the key into the rarity table.
    public void handleLoot(String sourceName, Collection<ItemStack> items)
    {
        String rsn = session.getRsn();
        if (!session.canUpload() || !config.enableDropLogging())
        {
            return;
        }

        long threshold = DropTrackingService.effectiveThreshold(config.dropThresholdGp());
        int rareDenominator = config.rareDropThreshold();
        long rareMinValue = Math.max(0, config.rareDropMinValueGp());
        Set<String> notableNames = notableItemsService.getNotableNames();
        String displaySource = DropTrackingService.displaySourceName(sourceName);

        List<PendingDrop> drops = new ArrayList<>();
        for (ItemStack itemStack : items)
        {
            int itemId = itemStack.getId();
            int quantity = itemStack.getQuantity();
            int gePrice = itemManager.getItemPrice(itemId);
            long totalValue = (long) gePrice * quantity;
            // Name is needed up front: notable matching is by name, and
            // notable items (GE price 0) would never survive a value-first gate.
            ItemComposition itemComp = itemManager.getItemComposition(itemId);
            String itemName = itemComp.getName();
            if (DropTrackingService.isClueScroll(itemName))
            {
                continue;
            }

            // Looked up for every item so a valuable drop's rarity is still
            // recorded; the display-name mapping (Hunllef -> Gauntlet) is
            // also how the supplement table is keyed.
            OptionalDouble rarity = rarityService.getRarity(sourceName, itemId, quantity);
            if (!rarity.isPresent() && !displaySource.equals(sourceName))
            {
                rarity = rarityService.getRarity(displaySource, itemId, quantity);
            }

            boolean valuable = DropTrackingService.isValuableDrop(gePrice, quantity, threshold);
            boolean rare = DropTrackingService.isRareDrop(rarity, rareDenominator) && totalValue >= rareMinValue;
            boolean notable = DropTrackingService.isNotableDrop(itemName, notableNames);
            if (valuable || rare || notable)
            {
                drops.add(new PendingDrop(itemName, itemId, totalValue, quantity,
                    rarity.isPresent() ? rarity.getAsDouble() : null));
            }
        }

        if (!drops.isEmpty())
        {
            dispatchDrops(rsn, displaySource, drops);
        }
    }

    private void dispatchDrops(String rsn, String sourceName, List<PendingDrop> drops)
    {
        if (config.enableDropScreenshots())
        {
            // One screenshot covers every qualifying item from this drop.
            // The frame is grabbed on the next render; annotating, encoding,
            // and uploading happen on the executor.
            final List<String> partyNames = getPartyMemberNames();
            final int bestItemId = drops.stream()
                .max(Comparator.comparingLong(d -> d.totalValue))
                .get().itemId;
            drawManager.requestNextFrameListener(frame ->
                executor.submit(() -> {
                    String screenshotUrl = screenshotService.upload(frame, rsn, partyNames, bestItemId);
                    submitDrops(rsn, sourceName, drops, screenshotUrl);
                }));
        }
        else
        {
            executor.submit(() -> submitDrops(rsn, sourceName, drops, null));
        }
    }

    private void submitDrops(String rsn, String npcName, List<PendingDrop> drops, String screenshotUrl)
    {
        String webhookUrl = config.discordWebhookUrl();
        for (PendingDrop drop : drops)
        {
            dropService.logDrop(rsn, npcName, drop.itemName, drop.itemId, drop.totalValue, drop.quantity,
                screenshotUrl, drop.rarity);
            discordService.sendDropNotification(webhookUrl, rsn, drop.itemName, drop.totalValue, npcName, drop.rarity);
        }
    }

    // Display names of the local user's Party plugin party, annotated onto
    // drop screenshots. Names are already visible to everyone in the party;
    // no party identifiers are included in the image.
    private List<String> getPartyMemberNames()
    {
        if (!partyService.isInParty())
        {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (PartyMember member : partyService.getMembers())
        {
            String name = member.getDisplayName();
            if (name != null && !name.isEmpty())
            {
                names.add(name);
            }
        }
        return names;
    }

    private static class PendingDrop
    {
        final String itemName;
        final int itemId;
        final long totalValue;
        final int quantity;
        // Drop probability per kill when known, else null.
        final Double rarity;

        PendingDrop(String itemName, int itemId, long totalValue, int quantity, Double rarity)
        {
            this.itemName = itemName;
            this.itemId = itemId;
            this.totalValue = totalValue;
            this.quantity = quantity;
            this.rarity = rarity;
        }
    }
}
