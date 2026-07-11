package com.github.orgonag.fbclan;

import com.github.orgonag.fbclan.announcements.AnnouncementsService;
import com.github.orgonag.fbclan.drops.DiscordWebhookService;
import com.github.orgonag.fbclan.drops.DropCaptureService;
import com.github.orgonag.fbclan.drops.DropScreenshotService;
import com.github.orgonag.fbclan.drops.NotableItemsService;
import com.github.orgonag.fbclan.drops.SupabaseDropService;
import com.github.orgonag.fbclan.lfg.LfgPartyBridge;
import com.github.orgonag.fbclan.lfg.LfgService;
import com.github.orgonag.fbclan.panel.AnnouncementsPanel;
import com.github.orgonag.fbclan.panel.DropLogPanel;
import com.github.orgonag.fbclan.panel.FinalBossPanel;
import com.github.orgonag.fbclan.panel.LeaderboardPanel;
import com.github.orgonag.fbclan.panel.LfgPanel;
import com.github.orgonag.fbclan.panel.LockedPanel;
import com.github.orgonag.fbclan.panel.RootPanel;
import com.github.orgonag.fbclan.pb.LeaderboardService;
import com.github.orgonag.fbclan.pb.PbSeedService;
import com.github.orgonag.fbclan.pb.PbSubmitService;
import com.github.orgonag.fbclan.pb.PbUploadCoordinator;
import com.github.orgonag.fbclan.stats.DashboardService;
import com.github.orgonag.fbclan.stats.MemberStatsService;
import com.github.orgonag.fbclan.stats.StatsUploadCoordinator;
import com.github.orgonag.fbclan.welcome.WelcomeMessagePresenter;
import com.github.orgonag.fbclan.welcome.WelcomeMessageService;
import com.github.orgonag.fbclan.wom.VerificationController;
import com.github.orgonag.fbclan.wom.WomVerificationService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
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
 *     member of the clan's WOM group. The panel stays locked for non-members.
 *     This once-per-login verification call is the plugin's single
 *     deliberate non-Supabase data source.</li>
 * <li>Supabase (clan-owned database): stores drop log rows, LFG statuses,
 *     (opt-in) drop screenshots, boss personal-best times, and collection
 *     log counts and combat achievement points (opt-out). Only ever sends
 *     the local player's own RSN, drop details, LFG activity/note,
 *     screenshots, PB times, and collection-log/CA counts. PB submissions
 *     go through the submit_pbs function, and member stats go through the
 *     submit_stats function (improve-only); leaderboard reads come from
 *     read-only views. Also reads the clan-curated notable-items list and
 *     welcome message at startup (read-only, no player data sent).</li>
 * <li>Discord webhook (optional, user-supplied URL): drop notifications.</li>
 * </ul>
 *
 * No player data is sent anywhere unless the player is a verified clan
 * member. Drop logging, PB tracking, and collection-log/combat-achievement
 * uploads are ON by default for verified members and can each be disabled
 * in the config; drop screenshots remain opt-in (default off). See
 * {@link FinalBossConfig}.
 */
@Slf4j
@PluginDescriptor(
    name = "Final Boss",
    description = "Clan tools for Final Boss — announcements, drop log, LFG, and PB leaderboards",
    tags = {"clan", "final boss", "drops", "lfg", "looking for group", "leaderboard", "personal best", "pb", "announcements"}
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

    @Inject
    private ConfigManager configManager;

    private NavigationButton navButton;
    private LockedPanel lockedPanel;
    private FinalBossPanel mainPanel;
    private RootPanel rootPanel;

    private SupabaseDropService dropService;
    private DiscordWebhookService discordService;
    private DropScreenshotService screenshotService;
    private DropCaptureService dropCaptureService;
    private LfgService lfgService;
    private NotableItemsService notableItemsService;
    private WelcomeMessagePresenter welcomePresenter;
    private AnnouncementsService announcementsService;
    private PbUploadCoordinator pbUploadCoordinator;
    private LeaderboardService leaderboardService;
    private StatsUploadCoordinator statsUploadCoordinator;
    private DashboardService dashboardService;
    private VerificationController verificationController;

    private DropLogPanel dropLogPanel;
    private LfgPanel lfgPanel;
    private AnnouncementsPanel announcementsPanel;
    private LeaderboardPanel leaderboardPanel;
    private LfgPartyBridge lfgPartyBridge;

    private final ClanSession session = new ClanSession();
    private ScheduledFuture<?> dropRefreshFuture;

    @Provides
    FinalBossConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FinalBossConfig.class);
    }

    @Override
    protected void startUp()
    {
        WomVerificationService womService = new WomVerificationService(okHttpClient);
        dropService = new SupabaseDropService(okHttpClient);
        discordService = new DiscordWebhookService(okHttpClient);
        screenshotService = new DropScreenshotService(okHttpClient);
        lfgService = new LfgService(okHttpClient);
        notableItemsService = new NotableItemsService(okHttpClient);
        // One fetch per session — the curated list changes rarely. Runs on the
        // executor so startup never blocks on network.
        executor.submit(notableItemsService::refresh);
        dropCaptureService = new DropCaptureService(client, config, session, itemManager,
            drawManager, partyService, executor, notableItemsService, dropService,
            discordService, screenshotService);

        WelcomeMessageService welcomeMessageService = new WelcomeMessageService(okHttpClient);
        welcomePresenter = new WelcomeMessagePresenter(client, clientThread, session, welcomeMessageService);
        welcomePresenter.reset();
        // Fetch once per session, then attempt display — covers the case where
        // verification already finished before the fetch (maybeShow is also
        // called on verification success, whichever comes last wins).
        executor.submit(() -> {
            welcomeMessageService.refresh();
            welcomePresenter.maybeShow();
        });

        announcementsService = new AnnouncementsService(okHttpClient);
        PbSubmitService pbSubmitService = new PbSubmitService(okHttpClient);
        PbSeedService pbSeedService = new PbSeedService(configManager);
        pbUploadCoordinator = new PbUploadCoordinator(client, config, session, executor,
            pbSubmitService, pbSeedService);
        pbUploadCoordinator.reset();
        leaderboardService = new LeaderboardService(okHttpClient);
        MemberStatsService memberStatsService = new MemberStatsService(okHttpClient);
        statsUploadCoordinator = new StatsUploadCoordinator(client, config, session,
            executor, memberStatsService);
        dashboardService = new DashboardService(okHttpClient);

        dropLogPanel = new DropLogPanel(dropService, executor);
        lfgPanel = new LfgPanel(lfgService, executor, config);
        lfgPartyBridge = new LfgPartyBridge(client, clientThread, partyService, config, executor, lfgPanel);
        announcementsPanel = new AnnouncementsPanel(announcementsService, executor);
        // Warm the announcements cache and populate the tab; refresh() runs
        // the fetch on the executor, so startup never blocks on network.
        announcementsPanel.refresh();
        leaderboardPanel = new LeaderboardPanel(leaderboardService, dashboardService, executor);

        lockedPanel = new LockedPanel();
        mainPanel = new FinalBossPanel(announcementsPanel, dropLogPanel, lfgPanel, leaderboardPanel);
        rootPanel = new RootPanel(lockedPanel, mainPanel);

        verificationController = new VerificationController(client, clientThread, executor,
            womService, session, new VerificationController.Listener()
        {
            @Override
            public void onRsnCaptured(String rsn)
            {
                SwingUtilities.invokeLater(() -> lfgPanel.setCurrentRsn(rsn));
            }

            @Override
            public void onVerifying()
            {
                SwingUtilities.invokeLater(() -> lockedPanel.showVerifying());
            }

            @Override
            public void onVerified()
            {
                SwingUtilities.invokeLater(() -> {
                    rootPanel.showMain();
                    mainPanel.refreshActiveTab();
                });
                startPolling();
                welcomePresenter.maybeShow();
                pbUploadCoordinator.maybeSeed();
                statsUploadCoordinator.maybeSubmit();
            }

            @Override
            public void onNotMember()
            {
                SwingUtilities.invokeLater(() -> lockedPanel.showNotMember());
            }

            @Override
            public void onError()
            {
                SwingUtilities.invokeLater(() -> lockedPanel.showError());
            }
        });

        lockedPanel.setRetryAction(e -> verificationController.verify());

        // One button for the plugin's lifetime; verification flips the
        // RootPanel card, so an open sidebar stays open.
        navButton = NavigationButton.builder()
            .tooltip("Final Boss")
            .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
            .priority(10)
            .panel(rootPanel)
            .build();
        clientToolbar.addNavigation(navButton);

        // When the plugin is enabled mid-session, no LOGGED_IN event will
        // fire, so kick off verification directly.
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            verificationController.kickOff(0);
        }
    }

    @Override
    protected void shutDown()
    {
        // Snapshot the RSN before it's nulled below — the executor task may
        // run after this method finishes.
        String rsnSnapshot = session.getRsn();
        if (rsnSnapshot != null && config.enableLfg())
        {
            executor.submit(() -> lfgService.removeStatus(rsnSnapshot));
        }

        stopPolling();

        clientToolbar.removeNavigation(navButton);
        verificationController.reset();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            if (session.isVerified())
            {
                return;
            }
            // 3s delay gives the login flow time to settle before we read
            // getLocalPlayer().getName().
            verificationController.kickOff(3);
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            if (!session.isVerified() && session.getRsn() == null)
            {
                return;
            }
            verificationController.reset();
            stopPolling();
            SwingUtilities.invokeLater(() -> {
                rootPanel.showLocked();
                lockedPanel.showVerifying();
            });
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        // Collection log count is a varp; CA points a varbit. Either
        // rising mid-session re-submits (deduped by shouldSubmit).
        if (event.getVarpId() == VarPlayerID.COLLECTION_COUNT
            || event.getVarbitId() == VarbitID.CA_POINTS)
        {
            statsUploadCoordinator.maybeSubmit();
        }
    }

    private void startPolling()
    {
        lfgPartyBridge.startPolling();

        if (config.enableDropLogging())
        {
            dropRefreshFuture = executor.scheduleAtFixedRate(() -> {
                try { dropLogPanel.refresh(); }
                catch (Exception e) { log.warn("Drop refresh error", e); }
            }, 60, 60, TimeUnit.SECONDS);
        }
    }

    // These three events keep the panel's party state in sync without the
    // user having to re-click Set Status. The panel decides whether to
    // re-upsert (only when there is an active LFG status).
    @Subscribe
    public void onPartyChanged(PartyChanged event)
    {
        lfgPartyBridge.pushLocalPartyState();
    }

    @Subscribe
    public void onUserJoin(UserJoin event)
    {
        lfgPartyBridge.pushLocalPartyState();
    }

    @Subscribe
    public void onUserPart(UserPart event)
    {
        lfgPartyBridge.pushLocalPartyState();
    }

    private void stopPolling()
    {
        lfgPartyBridge.stopPolling();
        if (dropRefreshFuture != null)
        {
            dropRefreshFuture.cancel(true);
            dropRefreshFuture = null;
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        dropCaptureService.handleLoot(event.getNpc().getName(), event.getItems());
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
        dropCaptureService.handleLoot(event.getName(), event.getItems());
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        pbUploadCoordinator.onChatMessage(event);
        // Pets never appear in loot events — the game only announces them
        // in chat, so the drop pipeline listens here too.
        dropCaptureService.handlePetChatMessage(event);
    }
}
