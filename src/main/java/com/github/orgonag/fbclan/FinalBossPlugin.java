package com.github.orgonag.fbclan;

import com.github.orgonag.fbclan.clan.ClanContent;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.drops.DropLogger;
import com.github.orgonag.fbclan.drops.DropRates;
import com.github.orgonag.fbclan.drops.DropRules;
import com.github.orgonag.fbclan.lfg.Killcounts;
import com.github.orgonag.fbclan.lfg.LfgCommand;
import com.github.orgonag.fbclan.lfg.PartyBoard;
import com.github.orgonag.fbclan.pbs.PersonalBests;
import com.github.orgonag.fbclan.stats.CaBadges;
import com.github.orgonag.fbclan.stats.MemberStats;
import com.github.orgonag.fbclan.ui.AnnouncementsTab;
import com.github.orgonag.fbclan.ui.DropLogTab;
import com.github.orgonag.fbclan.ui.PartiesTab;
import com.github.orgonag.fbclan.ui.Sidebar;
import com.google.inject.Provides;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Clan plugin for the Final Boss OSRS clan (wiseoldman.net group 1055).
 *
 * External services: Wise Old Man (one read per login to verify
 * membership), the clan's Supabase database (drop log, LFG parties,
 * screenshots, personal bests, member stats, and the read-only curated
 * content), the OSRS hiscores via RuneLite's client (optional LFG kill
 * counts), and an optional user-supplied Discord webhook. Nothing about
 * the player leaves the client until the WOM check passes; each upload
 * can be switched off individually. See {@link FinalBossConfig}.
 */
@Slf4j
@PluginDescriptor(
    name = "Final Boss",
    description = "Clan tools for Final Boss — announcements, drop log, LFG, and PB leaderboards",
    tags = {"clan", "final boss", "drops", "lfg", "looking for group", "leaderboard", "personal best", "pb", "announcements"}
)
public class FinalBossPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar toolbar;
    @Inject private ScheduledExecutorService executor;
    @Inject private FinalBossConfig config;
    @Inject private Clan clan;
    @Inject private ClanContent content;
    @Inject private DropRates dropRates;
    @Inject private DropLogger drops;
    @Inject private PersonalBests pbs;
    @Inject private MemberStats stats;
    @Inject private CaBadges badges;
    @Inject private PartyBoard parties;
    @Inject private Killcounts killcounts;
    @Inject private LfgCommand lfgCommand;
    @Inject private Sidebar sidebar;
    @Inject private AnnouncementsTab announcementsTab;
    @Inject private DropLogTab dropLogTab;
    @Inject private PartiesTab partiesTab;

    private NavigationButton navButton;
    private ScheduledFuture<?> dropRefresh;
    // CA icon tiers loaded this client session.
    private volatile boolean badgesLoaded;

    @Provides
    FinalBossConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FinalBossConfig.class);
    }

    @Override
    protected void startUp()
    {
        clan.activate();
        clan.setListener(this::onStatus);
        // Once per client session: the welcome line and the CA icon list.
        content.resetSession();
        badgesLoaded = false;

        // Startup fetches, all off the client thread.
        executor.submit(dropRates::load);
        executor.submit(this::loadContent);
        // Warms the cache and populates the tab before it's first opened.
        announcementsTab.refresh();

        navButton = NavigationButton.builder()
            .tooltip("Final Boss")
            .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
            .priority(10)
            .panel(sidebar)
            .build();
        toolbar.addNavigation(navButton);

        // Enabled mid-session: no LOGGED_IN event will fire.
        clientThread.invokeLater(() -> {
            if (client.getGameState() == GameState.LOGGED_IN) clan.verifyAfter(0);
        });
    }

    @Override
    protected void shutDown()
    {
        clan.deactivate();
        stopPolling();
        SwingUtilities.invokeLater(sidebar::closeWindows);
        toolbar.removeNavigation(navButton);
    }

    private void onStatus(Clan.Status status)
    {
        long generation = clan.snapshot().getGeneration();
        SwingUtilities.invokeLater(() -> {
            if (clan.snapshot().getGeneration() != generation) return;
            sidebar.show(status);
            if (status == Clan.Status.MEMBER)
            {
                sidebar.refreshActiveTab();
            }
        });
        if (status != Clan.Status.MEMBER)
        {
            return;
        }
        startPolling();
        // Retries whatever the startup fetch missed.
        executor.submit(this::loadContent);
        // World-type and varp reads belong on the client thread.
        clientThread.invokeLater(() -> {
            pbs.sync();
            stats.maybeSubmit();
            executor.submit(drops::flush);
        });
    }

    private void loadContent()
    {
        content.loadMissing();
        content.maybeShowWelcome();
    }

    private void startPolling()
    {
        parties.start();
        // The feed refreshes whether or not this player uploads their own drops.
        if (dropRefresh == null)
        {
            dropRefresh = executor.scheduleAtFixedRate(() -> {
                try
                {
                    dropLogTab.refresh();
                }
                catch (Exception e)
                {
                    log.warn("Drop refresh error", e);
                }
            }, 60, 60, TimeUnit.SECONDS);
        }
        loadBadges();
    }

    // CA icon tiers: once per client session, and only while the icons are on.
    private void loadBadges()
    {
        if (config.enableChatBadges() && !badgesLoaded)
        {
            executor.submit(() -> {
                badgesLoaded = badges.refresh();
            });
        }
    }

    private void stopPolling()
    {
        parties.stop();
        SwingUtilities.invokeLater(partiesTab::reset);
        killcounts.clear();
        if (dropRefresh != null)
        {
            dropRefresh.cancel(true);
            dropRefresh = null;
        }
    }

    // ------------------------------------------------------------ events

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            if (!clan.isVerified())
            {
                // Let the login flow settle before reading the player name.
                clan.verifyAfter(3);
            }
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN && (clan.isVerified() || clan.rsn() != null))
        {
            clan.reset();
            stopPolling();
            SwingUtilities.invokeLater(() -> {
                sidebar.closeWindows();
                sidebar.show(Clan.Status.VERIFYING);
            });
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        clan.reset();
        stopPolling();
        SwingUtilities.invokeLater(sidebar::closeWindows);
        if (client.getGameState() == GameState.LOGGED_IN) clan.verifyAfter(1);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // Only two settings start or stop something; the rest are read when
        // used. (Restarting on any change would wipe a half-filled LFG form.)
        if (!"finalboss".equals(event.getGroup())) return;
        if ("enableChatBadges".equals(event.getKey()))
        {
            if (clan.isVerified()) loadBadges();
        }
        else if ("enableLfg".equals(event.getKey()))
        {
            clientThread.invokeLater(() -> {
                stopPolling();
                if (clan.isVerified()) startPolling();
            });
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == InventoryID.INV)
        {
            drops.onInventoryChanged(event.getItemContainer());
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        drops.onTick();
        // Retry waiting drop uploads about every 30 seconds.
        if (client.getTickCount() % 50 == 0)
        {
            executor.submit(drops::flush);
        }
        // PBs (from RuneLite's own records) and CL/CA stats: every 30 minutes.
        if (client.getTickCount() % 3000 == 0)
        {
            pbs.sync();
            stats.maybeSubmit();
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        if (event.getNpc().getName() != null) drops.onLoot(event.getNpc().getName(), event.getItems(), false);
    }

    // Loot with no NPC kill behind it: raid chests, Barrows, and the few
    // bosses whose reward-chest loot the Loot Tracker reports as an NPC
    // record without an NPC-kill event (Gauntlet, Whisperer, Araxxor,
    // Royal Titans). Requires the core Loot Tracker plugin (on by default).
    @Subscribe
    public void onLootReceived(LootReceived event)
    {
        boolean chestNpc = event.getType() == LootRecordType.NPC && DropRules.CHEST_LOOT_NPCS.contains(event.getName());
        if (event.getType() == LootRecordType.EVENT || chestNpc)
        {
            drops.onLoot(event.getName(), event.getItems(), true);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        drops.onChatMessage(event);
        if (config.enableChatBadges())
        {
            badges.onChatMessage(event);
        }
        lfgCommand.onChatMessage(event);
    }
}
