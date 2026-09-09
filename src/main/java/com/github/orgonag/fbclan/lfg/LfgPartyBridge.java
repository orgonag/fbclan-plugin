package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.panel.LfgPartiesPanel;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.client.callback.ClientThread;

/**
 * Owns the 30s LFG poll: refreshes the parties board and feeds it the
 * clan-channel roster (who's online) and the current world.
 */
@Slf4j
public class LfgPartyBridge
{
    private final Client client;
    private final ClientThread clientThread;
    private final FinalBossConfig config;
    private final ScheduledExecutorService executor;
    private final LfgPartiesPanel partiesPanel;

    private ScheduledFuture<?> pollFuture;

    public LfgPartyBridge(Client client, ClientThread clientThread, FinalBossConfig config,
        ScheduledExecutorService executor, LfgPartiesPanel partiesPanel)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.executor = executor;
        this.partiesPanel = partiesPanel;
    }

    // Called on verification success.
    public void startPolling()
    {
        if (!config.enableLfg())
        {
            return;
        }
        updateOnlineClanMembers();
        pollFuture = executor.scheduleAtFixedRate(() -> {
            try
            {
                updateOnlineClanMembers();
                partiesPanel.refresh();
            }
            catch (Exception e)
            {
                log.warn("LFG poll error", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public void stopPolling()
    {
        if (pollFuture != null)
        {
            pollFuture.cancel(true);
            pollFuture = null;
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
            partiesPanel.setOnlineNames(online);
            partiesPanel.setCurrentWorld(client.getWorld());
        });
    }
}
