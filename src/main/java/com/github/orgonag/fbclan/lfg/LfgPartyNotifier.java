package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.ClanSession;
import com.github.orgonag.fbclan.FinalBossConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;

/**
 * Turns the 30s party poll into events for the local player by diffing
 * consecutive snapshots: new applicants to the party they host, their
 * own application being accepted/declined, being removed from a party,
 * a party they were in forming (filling up) or being disbanded. Events surface as chatbox
 * game messages and, optionally, desktop notifications. Everything here
 * is local — nothing is sent anywhere.
 */
public class LfgPartyNotifier
{
    private final Client client;
    private final ClientThread clientThread;
    private final FinalBossConfig config;
    private final ClanSession session;
    private final Notifier notifier;

    // Guarded by `this`: the poll runs on the executor; reset/local-action
    // hints arrive from the EDT.
    private Map<String, LfgParty> previous = Collections.emptyMap();
    private Set<String> previousFormed = Collections.emptySet();
    private boolean primed = false;
    // Parties the local player withdrew from / disbanded themselves, so the
    // next diff doesn't announce their own action back at them.
    private final Set<String> selfLeft = new HashSet<>();

    public LfgPartyNotifier(Client client, ClientThread clientThread, FinalBossConfig config,
                            ClanSession session, Notifier notifier)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.session = session;
        this.notifier = notifier;
    }

    public synchronized void reset()
    {
        previous = Collections.emptyMap();
        previousFormed = Collections.emptySet();
        primed = false;
        selfLeft.clear();
    }

    // Called by the panel before it withdraws from / disbands a party.
    public synchronized void expectSelfLeave(String partyId)
    {
        if (partyId != null)
        {
            selfLeft.add(partyId);
        }
    }

    // Runs on the executor after each successful fetch. The first snapshot
    // only primes the baseline so a fresh login never replays history.
    // `formed` is the current formed-party list: a party that vanished
    // because it formed is announced as such, not as disbanded.
    public void onPartiesFetched(List<LfgParty> parties, List<LfgFormedParty> formed)
    {
        String rsn = session.getRsn();
        if (rsn == null)
        {
            return;
        }
        Map<String, LfgParty> now = new HashMap<>();
        for (LfgParty p : parties)
        {
            now.put(p.getId(), p);
        }
        Set<String> formedIds = new HashSet<>();
        Set<String> formedSourceIds = new HashSet<>();
        for (LfgFormedParty f : formed)
        {
            formedIds.add(f.getId());
            if (f.getPartyId() != null)
            {
                formedSourceIds.add(f.getPartyId());
            }
        }
        List<String> messages = new ArrayList<>();
        synchronized (this)
        {
            if (primed)
            {
                diff(previous, now, rsn, formedSourceIds, messages);
                for (LfgFormedParty f : formed)
                {
                    if (!previousFormed.contains(f.getId()) && f.includes(rsn))
                    {
                        String whose = f.isHostedBy(rsn) ? "Your" : f.getHostRsn() + "'s";
                        messages.add(whose + " " + f.getTitle() + " party has formed"
                            + (f.getWorld() != null ? " (World " + f.getWorld() + ")" : "")
                            + ": " + f.getRoster() + ".");
                    }
                }
            }
            previous = now;
            previousFormed = formedIds;
            primed = true;
        }
        for (String m : messages)
        {
            deliver(m);
        }
    }

    private void diff(Map<String, LfgParty> prev, Map<String, LfgParty> now, String rsn,
                      Set<String> formedSourceIds, List<String> out)
    {
        // Host: new pending applicants.
        for (LfgParty p : now.values())
        {
            if (!p.isHostedBy(rsn))
            {
                continue;
            }
            LfgParty before = prev.get(p.getId());
            for (LfgApplicant a : p.getPending())
            {
                if (before == null || before.applicantFor(a.getRsn()) == null)
                {
                    String role = a.getRole() == null ? "" : " as " + a.getRole().getDisplayName();
                    out.add(a.getRsn() + " applied to your " + p.getTitle() + " party" + role
                        + (a.isLearner() ? " (learner)" : "") + ".");
                }
            }
        }

        // Applicant: status changes on parties that still exist.
        for (LfgParty p : now.values())
        {
            if (p.isHostedBy(rsn))
            {
                continue;
            }
            LfgApplicant mine = p.applicantFor(rsn);
            LfgParty before = prev.get(p.getId());
            LfgApplicant mineBefore = before == null ? null : before.applicantFor(rsn);
            if (mine != null && mineBefore != null && mine.getStatus() != mineBefore.getStatus())
            {
                if (mine.isAccepted())
                {
                    out.add("You've been accepted to " + p.getHostRsn() + "'s " + p.getTitle() + " party"
                        + (p.getWorld() != null ? " (World " + p.getWorld() + ")" : "") + ".");
                }
                else if (mine.getStatus() == LfgApplicant.Status.DECLINED)
                {
                    out.add(p.getHostRsn() + " declined your " + p.getTitle() + " application.");
                }
            }
            else if (mine == null && mineBefore != null && !selfLeft.remove(p.getId())
                && mineBefore.getStatus() != LfgApplicant.Status.DECLINED)
            {
                out.add("You were removed from " + p.getHostRsn() + "'s " + p.getTitle() + " party.");
            }
        }

        // Parties that vanished while the player was in them (a party that
        // formed is announced separately, from the formed list).
        for (LfgParty before : prev.values())
        {
            if (now.containsKey(before.getId()) || before.isHostedBy(rsn)
                || formedSourceIds.contains(before.getId()))
            {
                continue;
            }
            LfgApplicant mineBefore = before.applicantFor(rsn);
            if (mineBefore != null && mineBefore.getStatus() != LfgApplicant.Status.DECLINED
                && !selfLeft.remove(before.getId()))
            {
                out.add(before.getHostRsn() + "'s " + before.getTitle() + " party was disbanded.");
            }
        }
        selfLeft.clear();
    }

    private void deliver(String message)
    {
        if (config.lfgPartyNotifications())
        {
            clientThread.invokeLater(() -> {
                if (client.getGameState() == GameState.LOGGED_IN)
                {
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[LFG] " + message, null);
                }
            });
        }
        if (config.lfgDesktopNotifications())
        {
            notifier.notify("Final Boss LFG: " + message);
        }
    }
}
