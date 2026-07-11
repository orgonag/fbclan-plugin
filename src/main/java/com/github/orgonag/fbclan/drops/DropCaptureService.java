package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.ClanSession;
import com.github.orgonag.fbclan.FinalBossConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
 * The drop pipeline: filters loot events against the GP threshold and the
 * clan-curated notable list, detects pet chat messages, optionally grabs
 * an annotated screenshot, then fans out to the Supabase drop log and the
 * user's Discord webhook.
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
    private final SupabaseDropService dropService;
    private final DiscordWebhookService discordService;
    private final DropScreenshotService screenshotService;

    public DropCaptureService(Client client, FinalBossConfig config, ClanSession session,
        ItemManager itemManager, DrawManager drawManager, PartyService partyService,
        ScheduledExecutorService executor, NotableItemsService notableItemsService,
        SupabaseDropService dropService, DiscordWebhookService discordService,
        DropScreenshotService screenshotService)
    {
        this.client = client;
        this.config = config;
        this.session = session;
        this.itemManager = itemManager;
        this.drawManager = drawManager;
        this.partyService = partyService;
        this.executor = executor;
        this.notableItemsService = notableItemsService;
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
            Collections.singletonList(new PendingDrop(itemName, 0, 0, 1)));
    }

    public void handleLoot(String sourceName, Collection<ItemStack> items)
    {
        String rsn = session.getRsn();
        if (!session.canUpload() || !config.enableDropLogging())
        {
            return;
        }

        long threshold = DropTrackingService.effectiveThreshold(config.dropThresholdGp());
        Set<String> notableNames = notableItemsService.getNotableNames();
        List<PendingDrop> drops = new ArrayList<>();
        for (ItemStack itemStack : items)
        {
            int itemId = itemStack.getId();
            int quantity = itemStack.getQuantity();
            int gePrice = itemManager.getItemPrice(itemId);
            // Name is needed up front now: notable matching is by name, and
            // notable items (GE price 0) would never survive a value-first gate.
            ItemComposition itemComp = itemManager.getItemComposition(itemId);
            String itemName = itemComp.getName();

            if (DropTrackingService.isValuableDrop(gePrice, quantity, threshold)
                || DropTrackingService.isNotableDrop(itemName, notableNames))
            {
                drops.add(new PendingDrop(itemName, itemId, (long) gePrice * quantity, quantity));
            }
        }

        if (!drops.isEmpty())
        {
            dispatchDrops(rsn, sourceName, drops);
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
            dropService.logDrop(rsn, npcName, drop.itemName, drop.itemId, drop.totalValue, drop.quantity, screenshotUrl);
            discordService.sendDropNotification(webhookUrl, rsn, drop.itemName, drop.totalValue, npcName);
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

        PendingDrop(String itemName, int itemId, long totalValue, int quantity)
        {
            this.itemName = itemName;
            this.itemId = itemId;
            this.totalValue = totalValue;
            this.quantity = quantity;
        }
    }
}
