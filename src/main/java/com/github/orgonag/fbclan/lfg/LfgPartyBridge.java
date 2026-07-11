package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.panel.LfgPanel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import net.runelite.client.party.PartyService;

/**
 * Bridges RuneLite's Party plugin and clan-channel state into the LFG
 * panel: seeds/pushes local party state (hashed party id only — never the
 * passphrase), tracks which clan members are online, and owns the 30s
 * LFG poll.
 */
@Slf4j
public class LfgPartyBridge
{
    private final Client client;
    private final ClientThread clientThread;
    private final PartyService partyService;
    private final FinalBossConfig config;
    private final ScheduledExecutorService executor;
    private final LfgPanel lfgPanel;

    private ScheduledFuture<?> pollFuture;

    public LfgPartyBridge(Client client, ClientThread clientThread, PartyService partyService,
        FinalBossConfig config, ScheduledExecutorService executor, LfgPanel lfgPanel)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.partyService = partyService;
        this.config = config;
        this.executor = executor;
        this.lfgPanel = lfgPanel;
    }

    // Called on verification success. Seeds the initial party state; it is
    // afterwards driven only by the party event handlers. Pushing it on
    // every poll tick would re-upsert the LFG row and reset its
    // "X min ago" timer.
    public void startPolling()
    {
        if (!config.enableLfg())
        {
            return;
        }
        updateOnlineClanMembers();
        pushLocalPartyState();
        pollFuture = executor.scheduleAtFixedRate(() -> {
            try
            {
                updateOnlineClanMembers();
                lfgPanel.refresh();
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

    // Pushes the local Party plugin state to the panel; (null, null) when
    // the user is not in a party.
    public void pushLocalPartyState()
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
    static String hashPartyId(long partyId)
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
}
