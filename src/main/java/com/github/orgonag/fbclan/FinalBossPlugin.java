package com.github.orgonag.fbclan;

import com.github.orgonag.fbclan.drops.DiscordWebhookService;
import com.github.orgonag.fbclan.drops.DropScreenshotService;
import com.github.orgonag.fbclan.drops.DropTrackingService;
import com.github.orgonag.fbclan.drops.NotableItemsService;
import com.github.orgonag.fbclan.drops.SupabaseDropService;
import com.github.orgonag.fbclan.lfg.LfgService;
import com.github.orgonag.fbclan.panel.DropLogPanel;
import com.github.orgonag.fbclan.panel.FinalBossPanel;
import com.github.orgonag.fbclan.panel.LfgPanel;
import com.github.orgonag.fbclan.panel.LockedPanel;
import com.github.orgonag.fbclan.wom.WomVerificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.loottracker.LootRecordType;
import com.google.inject.Provides;
import okhttp3.OkHttpClient;

/**
 * Clan plugin for the Final Boss OSRS clan (wiseoldman.net group 1055).
 *
 * External services this plugin communicates with:
 * <ul>
 * <li>Wise Old Man API (read-only): checks whether the logged-in player is a
 *     member of the clan's WOM group. The panel stays locked for non-members.</li>
 * <li>Supabase (clan-owned database): stores drop log rows, LFG statuses, and
 *     (opt-in) drop screenshots. Only ever sends the local player's own RSN,
 *     drop details, LFG activity/note, and screenshots. Also reads the
 *     clan-curated notable-items list at startup (read-only, no player data
 *     sent).</li>
 * <li>Discord webhook (optional, user-supplied URL): drop notifications.</li>
 * </ul>
 *
 * No player data is sent anywhere unless the player is a verified clan
 * member. Drop logging and screenshots are additionally gated behind opt-in
 * config toggles (both default off; see {@link FinalBossConfig}).
 */
@Slf4j
@PluginDescriptor(
    name = "Final Boss",
    description = "Clan tools for Final Boss — drop logging, LFG, and more",
    tags = {"clan", "final boss", "drops", "lfg", "looking for group"}
)
public class FinalBossPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private FinalBossConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ScheduledExecutorService executor;

    // Core RuneLite service backing the Party plugin. We only read the
    // partyId and member count — never the passphrase.
    @Inject
    private PartyService partyService;

    @Inject
    private DrawManager drawManager;

    private NavigationButton navButton;
    private LockedPanel lockedPanel;
    private FinalBossPanel mainPanel;

    private WomVerificationService womService;
    private SupabaseDropService dropService;
    private DiscordWebhookService discordService;
    private DropScreenshotService screenshotService;
    private LfgService lfgService;
    private NotableItemsService notableItemsService;

    private DropLogPanel dropLogPanel;
    private LfgPanel lfgPanel;

    private volatile boolean verified = false;
    private volatile String currentRsn = null;
    private ScheduledFuture<?> lfgPollFuture;
    private ScheduledFuture<?> dropRefreshFuture;

    @Provides
    FinalBossConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FinalBossConfig.class);
    }

    @Override
    protected void startUp()
    {
        womService = new WomVerificationService(okHttpClient);
        dropService = new SupabaseDropService(okHttpClient);
        discordService = new DiscordWebhookService(okHttpClient);
        screenshotService = new DropScreenshotService(okHttpClient);
        lfgService = new LfgService(okHttpClient);
        notableItemsService = new NotableItemsService(okHttpClient);
        // One fetch per session — the curated list changes rarely. Runs on the
        // executor so startup never blocks on network.
        executor.submit(notableItemsService::refresh);

        dropLogPanel = new DropLogPanel(dropService, executor);
        lfgPanel = new LfgPanel(lfgService, executor, config);

        lockedPanel = new LockedPanel();
        mainPanel = new FinalBossPanel(dropLogPanel, lfgPanel);

        lockedPanel.setRetryAction(e -> verifyMembership());

        showNavPanel(lockedPanel);

        // When the plugin is enabled mid-session, no LOGGED_IN event will
        // fire, so kick off verification directly.
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            kickOffInitialVerification(0);
        }
    }

    @Override
    protected void shutDown()
    {
        // Snapshot the RSN before it's nulled below — the executor task may
        // run after this method finishes.
        String rsnSnapshot = currentRsn;
        if (rsnSnapshot != null && config.enableLfg())
        {
            executor.submit(() -> lfgService.removeStatus(rsnSnapshot));
        }

        if (lfgPollFuture != null)
        {
            lfgPollFuture.cancel(true);
            lfgPollFuture = null;
        }
        if (dropRefreshFuture != null)
        {
            dropRefreshFuture.cancel(true);
            dropRefreshFuture = null;
        }

        clientToolbar.removeNavigation(navButton);
        verified = false;
        currentRsn = null;
        womService.clearCache();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            if (verified)
            {
                return;
            }
            // 3s delay gives the login flow time to settle before we read
            // getLocalPlayer().getName().
            kickOffInitialVerification(3);
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            if (!verified && currentRsn == null)
            {
                return;
            }
            verified = false;
            currentRsn = null;
            womService.clearCache();
            stopPolling();
            SwingUtilities.invokeLater(() -> {
                showNavPanel(lockedPanel);
                lockedPanel.showVerifying();
            });
        }
    }

    // Captures the player's RSN and triggers WOM verification, after an
    // optional delay to let the login flow settle.
    private void kickOffInitialVerification(long delaySeconds)
    {
        executor.schedule(() -> clientThread.invokeLater(() -> {
            if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
            {
                return;
            }
            String rsn = client.getLocalPlayer().getName();
            currentRsn = rsn;
            SwingUtilities.invokeLater(() -> lfgPanel.setCurrentRsn(rsn));
            verifyMembership();
        }), delaySeconds, TimeUnit.SECONDS);
    }

    private void verifyMembership()
    {
        SwingUtilities.invokeLater(() -> lockedPanel.showVerifying());

        executor.submit(() -> {
            try
            {
                boolean isMember = womService.verify(currentRsn);
                if (isMember)
                {
                    verified = true;
                    SwingUtilities.invokeLater(this::showMainPanel);
                    startPolling();
                }
                else
                {
                    SwingUtilities.invokeLater(() -> lockedPanel.showNotMember());
                }
            }
            catch (Exception e)
            {
                log.warn("WOM verification failed", e);
                SwingUtilities.invokeLater(() -> lockedPanel.showError());
            }
        });
    }

    private void showMainPanel()
    {
        showNavPanel(mainPanel);
        mainPanel.refreshActiveTab();
    }

    // NavigationButton's panel is fixed at build time, so swapping between
    // the locked and main panel means replacing the whole button.
    private void showNavPanel(PluginPanel panel)
    {
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }
        navButton = NavigationButton.builder()
            .tooltip("Final Boss")
            .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
            .priority(10)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);
    }

    private void startPolling()
    {
        if (config.enableLfg())
        {
            updateOnlineClanMembers();
            // Seed the initial party state; afterwards it is driven only by
            // the party event handlers. Pushing it on every poll tick would
            // re-upsert the LFG row and reset its "X min ago" timer.
            pushLocalPartyStateToPanel();
            lfgPollFuture = executor.scheduleAtFixedRate(() -> {
                try {
                    updateOnlineClanMembers();
                    lfgPanel.refresh();
                }
                catch (Exception e) { log.warn("LFG poll error", e); }
            }, 30, 30, TimeUnit.SECONDS);
        }

        if (config.enableDropLogging())
        {
            dropRefreshFuture = executor.scheduleAtFixedRate(() -> {
                try { dropLogPanel.refresh(); }
                catch (Exception e) { log.warn("Drop refresh error", e); }
            }, 60, 60, TimeUnit.SECONDS);
        }
    }

    // Pushes the local Party plugin state to the panel; (null, null) when
    // the user is not in a party.
    void pushLocalPartyStateToPanel()
    {
        String partyId = null;
        Integer partySize = null;
        if (partyService.isInParty())
        {
            partyId = hashPartyId(partyService.getPartyId());
            partySize = partyService.getMembers().size();
        }
        lfgPanel.onLocalPartyStateChanged(partyId, partySize);
    }

    // The raw partyId is derived from the party passphrase and could let an
    // attacker eavesdrop on the party channel, so it never leaves the client.
    // A truncated SHA-256 preserves equality (grouping still works) without
    // being reversible.
    private static String hashPartyId(long partyId)
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Long.toString(partyId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++)
            {
                sb.append(String.format("%02x", digest[i] & 0xFF));
            }
            return sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // These three events keep the panel's party state in sync without the
    // user having to re-click Set Status. The panel decides whether to
    // re-upsert (only when there is an active LFG status).
    @Subscribe
    public void onPartyChanged(PartyChanged event)
    {
        pushLocalPartyStateToPanel();
    }

    @Subscribe
    public void onUserJoin(UserJoin event)
    {
        pushLocalPartyStateToPanel();
    }

    @Subscribe
    public void onUserPart(UserPart event)
    {
        pushLocalPartyStateToPanel();
    }

    private void updateOnlineClanMembers()
    {
        clientThread.invokeLater(() -> {
            Set<String> online = new HashSet<>();
            ClanChannel cc = client.getClanChannel();
            if (cc != null)
            {
                for (ClanChannelMember m : cc.getMembers())
                {
                    if (m.getName() != null)
                    {
                        online.add(m.getName());
                    }
                }
            }
            lfgPanel.setOnlineNames(online);
        });
    }

    private void stopPolling()
    {
        if (lfgPollFuture != null)
        {
            lfgPollFuture.cancel(true);
            lfgPollFuture = null;
        }
        if (dropRefreshFuture != null)
        {
            dropRefreshFuture.cancel(true);
            dropRefreshFuture = null;
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        handleLoot(event.getNpc().getName(), event.getItems());
    }

    // Loot with no NPC kill behind it — raid chests (CoX/ToB/ToA), Barrows,
    // and similar. NPC kills already arrive via onNpcLootReceived, so only
    // EVENT-type records are handled here to avoid double-logging. These
    // events are posted by the core Loot Tracker plugin, so raid chest
    // logging requires it to be enabled (it is by default).
    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        if (event.getType() != LootRecordType.EVENT)
        {
            return;
        }
        handleLoot(event.getName(), event.getItems());
    }

    // Pets never appear in loot events — the game only announces them in
    // chat. They are untradeable (GE value 0), so they bypass the GP
    // threshold and are always logged while drop logging is enabled.
    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        String rsn = currentRsn;
        if (event.getType() != ChatMessageType.GAMEMESSAGE || !verified
            || !config.enableDropLogging() || rsn == null)
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

    private void handleLoot(String sourceName, Collection<ItemStack> items)
    {
        String rsn = currentRsn;
        if (!verified || !config.enableDropLogging() || rsn == null)
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
