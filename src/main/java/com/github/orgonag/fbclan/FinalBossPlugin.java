package com.github.orgonag.fbclan;

import com.github.orgonag.fbclan.drops.DiscordWebhookService;
import com.github.orgonag.fbclan.drops.DropTrackingService;
import com.github.orgonag.fbclan.drops.SupabaseDropService;
import com.github.orgonag.fbclan.lfg.LfgService;
import com.github.orgonag.fbclan.panel.DropLogPanel;
import com.github.orgonag.fbclan.panel.FinalBossPanel;
import com.github.orgonag.fbclan.panel.LfgPanel;
import com.github.orgonag.fbclan.panel.LockedPanel;
import com.github.orgonag.fbclan.wom.WomVerificationService;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import com.google.inject.Provides;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
    name = "Final Boss",
    description = "Clan tools for Final Boss — drop logging, LFG, and more",
    tags = {"clan", "final boss", "drops", "lfg", "looking for group"},
    enabledByDefault = true
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

    // RuneLite core service for the Party plugin. Always available — the
    // Party plugin ships with every RuneLite client. We only ever read
    // partyId (a long secret) and member count; we never read or transmit
    // the passphrase, even though PartyService exposes it.
    @Inject
    private PartyService partyService;

    private NavigationButton navButton;
    private LockedPanel lockedPanel;
    private FinalBossPanel mainPanel;

    private WomVerificationService womService;
    private SupabaseDropService dropService;
    private DiscordWebhookService discordService;
    private LfgService lfgService;

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
        lfgService = new LfgService(okHttpClient);

        dropLogPanel = new DropLogPanel(dropService, executor);
        lfgPanel = new LfgPanel(lfgService, executor);

        lockedPanel = new LockedPanel();
        mainPanel = new FinalBossPanel(dropLogPanel, lfgPanel);

        lockedPanel.setRetryAction(e -> verifyMembership());

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navButton = NavigationButton.builder()
            .tooltip("Final Boss")
            .icon(icon)
            .priority(10)
            .panel(lockedPanel)
            .build();

        clientToolbar.addNavigation(navButton);

        // If the plugin is being enabled mid-session (player already logged
        // in), no GameStateChanged event will fire to kick off verification,
        // and the locked panel would sit forever on "Verifying...". Trigger
        // the same flow as onGameStateChanged(LOGGED_IN) when we detect we
        // started up while already in the game.
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            kickOffInitialVerification(0);
        }
    }

    @Override
    protected void shutDown()
    {
        // Snapshot currentRsn into a local before submitting to the
        // executor. The field is nulled a few lines below; if the
        // executor hadn't picked the task up yet, the lambda would read
        // null from the field and removeStatus(null) would throw NPE
        // inside URLEncoder, escaping the IOException catch and silently
        // leaving the user's LFG row in the DB until the 60-minute TTL.
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
                clientToolbar.removeNavigation(navButton);
                navButton = NavigationButton.builder()
                    .tooltip("Final Boss")
                    .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
                    .priority(10)
                    .panel(lockedPanel)
                    .build();
                clientToolbar.addNavigation(navButton);
                lockedPanel.showVerifying();
            });
        }
    }

    // Schedules the post-login work that captures the player's RSN and
    // triggers WOM verification. Called from two places: the normal LOGGED_IN
    // event handler (with a small delay to let the client settle), and
    // startUp() when the plugin is enabled while already in-game (with no
    // delay — the client has already settled).
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
        clientToolbar.removeNavigation(navButton);
        navButton = NavigationButton.builder()
            .tooltip("Final Boss")
            .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
            .priority(10)
            .panel(mainPanel)
            .build();
        clientToolbar.addNavigation(navButton);
        mainPanel.refreshActiveTab();
    }

    private void startPolling()
    {
        if (config.enableLfg())
        {
            updateOnlineClanMembers();
            // Seed the panel with the initial Party plugin state. From here
            // on, party changes are driven by the PartyChanged / UserJoin /
            // UserPart event handlers below — NOT by the poll. Pushing
            // party state on every poll tick used to re-upsert the user's
            // LFG row every 30s, which pinned its updated_at to now() and
            // stuck the "X min ago" timer at "just now" forever.
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

    // Snapshots the local Party plugin state for the panel. Called from the
    // executor / event bus; safe from any thread because PartyService
    // accessors are simple lock-free reads on the EDT-owned member list.
    // Returns (null, null) when the local user is not in a party.
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

    // We hash the raw partyId before it ever leaves the client. The raw
    // long is derived from the passphrase (PartyService.passphraseToId)
    // and could in principle be used by an attacker to eavesdrop on the
    // party WebSocket channel. The truncated SHA-256 preserves equality
    // (same partyId -> same hash, so grouping still works) while making
    // it computationally infeasible to reverse-derive the passphrase or
    // partyId from a leaked Supabase row.
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

    // Three events keep party state on the panel in sync without the user
    // having to re-click Set Status: PartyChanged when the local user joins
    // or leaves a party, UserJoin/UserPart when other members move in or out
    // of the current party. The panel decides whether to re-upsert (only
    // when there is an active LFG status).
    @Subscribe
    public void onPartyChanged(PartyChanged event)
    {
        if (lfgPanel != null)
        {
            pushLocalPartyStateToPanel();
        }
    }

    @Subscribe
    public void onUserJoin(UserJoin event)
    {
        if (lfgPanel != null)
        {
            pushLocalPartyStateToPanel();
        }
    }

    @Subscribe
    public void onUserPart(UserPart event)
    {
        if (lfgPanel != null)
        {
            pushLocalPartyStateToPanel();
        }
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
        String rsn = currentRsn;
        if (!verified || !config.enableDropLogging() || rsn == null)
        {
            return;
        }

        String npcName = event.getNpc().getName();

        for (ItemStack itemStack : event.getItems())
        {
            int itemId = itemStack.getId();
            int quantity = itemStack.getQuantity();
            int gePrice = itemManager.getItemPrice(itemId);

            if (DropTrackingService.isValuableDrop(gePrice, quantity))
            {
                ItemComposition itemComp = itemManager.getItemComposition(itemId);
                String itemName = itemComp.getName();
                long totalValue = (long) gePrice * quantity;

                executor.submit(() -> {
                    dropService.logDrop(rsn, npcName, itemName, itemId, totalValue, quantity);

                    String webhookUrl = config.discordWebhookUrl();
                    discordService.sendDropNotification(webhookUrl, rsn, itemName, totalValue, npcName);
                });
            }
        }
    }
}
