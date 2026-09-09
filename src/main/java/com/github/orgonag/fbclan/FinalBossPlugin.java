package com.github.orgonag.fbclan;

import com.github.orgonag.fbclan.clan.ClanContent;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.drops.DropLogger;
import com.github.orgonag.fbclan.drops.DropRates;
import com.github.orgonag.fbclan.drops.DropRules;
import com.github.orgonag.fbclan.lfg.LfgCommand;
import com.github.orgonag.fbclan.lfg.PartyApi;
import com.github.orgonag.fbclan.lfg.PartyBoard;
import com.github.orgonag.fbclan.pbs.PersonalBests;
import com.github.orgonag.fbclan.stats.CaBadges;
import com.github.orgonag.fbclan.stats.MemberStats;
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
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
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
    @Inject private PartyApi partyApi;
    @Inject private LfgCommand lfgCommand;
    @Inject private Sidebar sidebar;
    @Inject private DropLogTab dropLogTab;
    @Inject private PartiesTab partiesTab;

    private NavigationButton navButton;
    private ScheduledFuture<?> dropRefresh;
    private ScheduledFuture<?> badgeRefresh;

    @Provides
    FinalBossConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FinalBossConfig.class);
    }

    @Override
    protected void startUp()
    {
        clan.setListener(this::onStatus);
        content.resetSession();
        pbs.resetSession();

        // Startup fetches, all off the client thread.
        executor.submit(dropRates::load);
        executor.submit(content::refreshNotableItems);
        executor.submit(() -> {
            content.refreshWelcome();
            content.maybeShowWelcome();
        });
        executor.submit(content::refreshAnnouncements);

        navButton = NavigationButton.builder()
            .tooltip("Final Boss")
            .icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
            .priority(10)
            .panel(sidebar)
            .build();
        toolbar.addNavigation(navButton);

        // Enabled mid-session: no LOGGED_IN event will fire.
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            clan.verifyAfter(0);
        }
    }

    @Override
    protected void shutDown()
    {
        // Snapshot before reset(): the executor task may run afterwards.
        String rsn = clan.rsn();
        if (rsn != null && config.enableLfg())
        {
            // A party and an application can't be kept alive without a
            // running client.
            executor.submit(() -> {
                partyApi.disband(rsn);
                partyApi.withdrawAll(rsn);
            });
        }
        stopPolling();
        toolbar.removeNavigation(navButton);
        clan.reset();
    }

    private void onStatus(Clan.Status status)
    {
        SwingUtilities.invokeLater(() -> {
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
        executor.submit(content::maybeShowWelcome);
        pbs.maybeSeed();
        // Varp reads assert the client thread.
        clientThread.invokeLater(stats::maybeSubmit);
    }

    private void startPolling()
    {
        parties.start();
        if (config.enableDropLogging())
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
        // Badge tiers: fetch on verification, then every 5 minutes so
        // newly earned tiers show without a relog.
        badgeRefresh = executor.scheduleAtFixedRate(() -> {
            try
            {
                badges.refresh();
            }
            catch (Exception e)
            {
                log.warn("CA badge refresh error", e);
            }
        }, 0, 300, TimeUnit.SECONDS);
    }

    private void stopPolling()
    {
        parties.stop();
        partiesTab.reset();
        if (dropRefresh != null)
        {
            dropRefresh.cancel(true);
            dropRefresh = null;
        }
        if (badgeRefresh != null)
        {
            badgeRefresh.cancel(true);
            badgeRefresh = null;
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
            SwingUtilities.invokeLater(() -> sidebar.show(Clan.Status.VERIFYING));
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        // Collection log count is a varp; CA points a varbit.
        if (event.getVarpId() == VarPlayerID.COLLECTION_COUNT || event.getVarbitId() == VarbitID.CA_POINTS)
        {
            stats.maybeSubmit();
        }
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        drops.onLoot(event.getNpc().getName(), event.getItems());
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
            drops.onLoot(event.getName(), event.getItems());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        pbs.onChatMessage(event);
        drops.onChatMessage(event);
        if (config.enableChatBadges())
        {
            badges.onChatMessage(event);
        }
        lfgCommand.onChatMessage(event);
    }
}
