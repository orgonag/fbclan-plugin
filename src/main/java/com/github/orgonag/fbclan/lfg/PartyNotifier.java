package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Session;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;

/**
 * Turns two consecutive board snapshots into chat/desktop lines for the
 * local player: applications, accepts, kicks, leaves, members going
 * offline, moved starts, reminders, and posts forming, expiring or being
 * disbanded, plus the clan-wide new-post line. A snapshot diff needs no
 * server state, so an outage can't produce wrong lines. The player's own
 * leaves and kicks are recorded just before they are sent (on the same
 * single executor thread as the poll), so they are never announced back.
 */
@Singleton
public class PartyNotifier
{
    // An ASAP member missing from clan chat this long is reported offline;
    // a world hop or a slow login doesn't count.
    private static final Duration OFFLINE_GRACE = Duration.ofMinutes(3);
    // The server sweeps at a post's expiry; this poll can land a little
    // later on a slow PC clock.
    private static final Duration EXPIRY_SLACK = Duration.ofMinutes(2);

    private final Client client;
    private final ClientThread clientThread;
    private final FinalBossConfig config;
    private final Clan clan;
    private final Notifier notifier;

    private volatile long generation;
    // Guarded by `this`.
    private Map<String, Party> previous = Collections.emptyMap();
    private Set<String> previousFormed = Collections.emptySet();
    private boolean primed;
    // The player's own leaves (party id) and kicks (party id + name).
    private final Set<String> selfLeft = new HashSet<>();
    private final Set<String> kicked = new HashSet<>();
    // Scheduled parties already announced as starting soon / unfilled.
    private final Set<String> startingSoon = new HashSet<>();
    private final Set<String> unfilled = new HashSet<>();
    // ASAP members of the player's own posts (party id + name).
    private final Set<String> seenOnline = new HashSet<>();
    private final Map<String, Instant> missingSince = new HashMap<>();
    private final Set<String> reportedOffline = new HashSet<>();

    @Inject
    public PartyNotifier(Client client, ClientThread clientThread, FinalBossConfig config, Clan clan, Notifier notifier)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.clan = clan;
        this.notifier = notifier;
    }

    // Board stopped (logout, profile change, LFG off): forget everything and
    // drop lines still waiting for the chatbox.
    public synchronized void reset()
    {
        generation++;
        previous = Collections.emptyMap();
        previousFormed = Collections.emptySet();
        primed = false;
        selfLeft.clear();
        kicked.clear();
        startingSoon.clear();
        unfilled.clear();
        seenOnline.clear();
        missingSince.clear();
        reportedOffline.clear();
    }

    synchronized void expectSelfLeave(String partyId, boolean expected)
    {
        if (expected) selfLeft.add(partyId);
        else selfLeft.remove(partyId);
    }

    synchronized void expectKick(String partyId, String rsn, boolean expected)
    {
        if (expected) kicked.add(member(partyId, rsn));
        else kicked.remove(member(partyId, rsn));
    }

    private static String member(String partyId, String rsn)
    {
        return partyId + "|" + Names.normalize(rsn);
    }

    // Executor, after each successful fetch. `online`: normalized names in
    // clan chat, empty when the client can't tell.
    void onSnapshot(List<Party> now, List<FormedParty> formedNow, String rsn, Set<String> online)
    {
        if (rsn == null) return;
        Map<String, Party> nowById = new HashMap<>();
        for (Party p : now)
        {
            nowById.put(p.getId(), p);
        }
        Set<String> formedIds = new HashSet<>();
        Map<String, FormedParty> formedFrom = new HashMap<>();
        for (FormedParty f : formedNow)
        {
            formedIds.add(f.getId());
            if (f.getPartyId() != null) formedFrom.put(f.getPartyId(), f);
        }
        List<String> messages = new ArrayList<>();
        List<String> announcements = new ArrayList<>();
        synchronized (this)
        {
            if (primed)
            {
                diff(previous, nowById, rsn, formedFrom, messages);
                newPosts(previous, now, announcements);
                reminders(now, rsn, messages);
                offline(now, rsn, online, messages);
                for (FormedParty f : formedNow)
                {
                    if (!previousFormed.contains(f.getId()) && f.includes(rsn))
                    {
                        messages.add((f.isHostedBy(rsn) ? "Your" : f.getHostRsn() + "'s") + " " + f.title()
                            + " party has formed" + world(f.getWorld()) + ": " + f.roster() + ".");
                    }
                }
            }
            else
            {
                // First poll after login: remember what already qualifies so
                // nothing is replayed on every login.
                reminders(now, rsn, new ArrayList<>());
                offline(now, rsn, online, new ArrayList<>());
            }
            // An expectation for a party gone from both snapshots can never match.
            Map<String, Party> before = previous;
            selfLeft.removeIf(id -> !nowById.containsKey(id) && !before.containsKey(id));
            kicked.removeIf(k -> {
                String id = k.substring(0, k.indexOf('|'));
                return !nowById.containsKey(id) && !before.containsKey(id);
            });
            previous = nowById;
            previousFormed = formedIds;
            primed = true;
        }
        messages.forEach(this::deliver);
        // A sub-option of chat notifications: off there means no LFG chat lines at all.
        if (config.lfgPartyNotifications() && config.lfgNewPostAnnouncements()) announcements.forEach(this::chat);
    }

    // Posts that appeared since the last poll, the player's own included
    // so everyone sees the same line: "Dopezt created a party of 4 for
    // ToB (ASAP)." Posts made while this player was offline are on the
    // board already and aren't replayed.
    private static void newPosts(Map<String, Party> prev, List<Party> now, List<String> out)
    {
        for (Party p : now)
        {
            if (prev.containsKey(p.getId())) continue;
            out.add(p.getHostRsn() + " created a party of " + p.getCapacity() + " for " + p.title()
                + " (" + PartyBoard.when(p.getScheduledFor()) + ").");
        }
    }

    private void diff(Map<String, Party> prev, Map<String, Party> now, String rsn, Map<String, FormedParty> formedFrom, List<String> out)
    {
        for (Party p : now.values())
        {
            Party before = prev.get(p.getId());
            if (p.isHostedBy(rsn))
            {
                hostLines(p, before, out);
                continue;
            }
            Party.Applicant mine = p.applicantFor(rsn);
            Party.Applicant mineBefore = before == null ? null : before.applicantFor(rsn);
            if (mine != null && mine.getStatus() != Party.Status.DECLINED && before != null
                && !Objects.equals(before.getScheduledFor(), p.getScheduledFor()))
            {
                out.add(p.getHostRsn() + " moved their " + p.title() + " party to " + PartyBoard.when(p.getScheduledFor()) + ".");
            }
            if (mine != null && mine.isAccepted() && (mineBefore == null || !mineBefore.isAccepted()))
            {
                out.add((mine.isAddedByHost() ? p.getHostRsn() + " added you to their " : "You've been accepted to " + p.getHostRsn() + "'s ")
                    + p.title() + " party" + world(p.getWorld()) + ".");
            }
            else if (mine != null && mineBefore != null && mine.getStatus() == Party.Status.DECLINED && mineBefore.getStatus() != Party.Status.DECLINED)
            {
                out.add(p.getHostRsn() + " declined your " + p.title() + " application.");
            }
            // Gone without leaving: kicked. Only a seated member is told; a
            // pending application also disappears quietly when the player
            // joins another ASAP party.
            else if (mine == null && mineBefore != null && !selfLeft.remove(p.getId()) && mineBefore.isAccepted())
            {
                out.add("You were kicked from " + p.getHostRsn() + "'s " + p.title() + " party.");
            }
        }
        // Parties that vanished. Members of one that formed hear it from the
        // formed list; everyone else involved is told here.
        for (Party before : prev.values())
        {
            if (now.containsKey(before.getId())) continue;
            FormedParty formedAs = formedFrom.get(before.getId());
            boolean formed = formedAs != null;
            boolean expired = before.getExpiresAt() != null && !Instant.now().plus(EXPIRY_SLACK).isBefore(before.getExpiresAt());
            if (before.isHostedBy(rsn))
            {
                if (!formed && expired) out.add("Your " + before.title() + " post expired without filling.");
                continue;
            }
            Party.Applicant mineBefore = before.applicantFor(rsn);
            if (mineBefore == null || mineBefore.getStatus() == Party.Status.DECLINED || selfLeft.remove(before.getId())) continue;
            if (!formed)
            {
                out.add(expired ? before.getHostRsn() + "'s " + before.title() + " party expired."
                    : before.getHostRsn() + " disbanded their " + before.title() + " party.");
            }
            else if (!formedAs.includes(rsn))
            {
                out.add(before.getHostRsn() + "'s " + before.title() + " party filled without you.");
            }
        }
    }

    // The host's view: new applicants, and seated members who left
    // (anyone gone that the host didn't kick).
    private void hostLines(Party p, Party before, List<String> out)
    {
        for (Party.Applicant a : p.pending())
        {
            // New, or back after being declined.
            Party.Applicant was = before == null ? null : before.applicantFor(a.getRsn());
            if (was == null || was.getStatus() == Party.Status.DECLINED)
            {
                out.add(a.getRsn() + " applied to your " + p.title() + " party"
                    + (a.getRole() == null ? "" : " as " + a.getRole().getDisplayName())
                    + (a.isLearner() ? " (learner)" : "") + ".");
            }
        }
        if (before == null) return;
        for (Party.Applicant a : before.accepted())
        {
            if (p.applicantFor(a.getRsn()) == null && !kicked.remove(member(p.getId(), a.getRsn())))
            {
                out.add(a.getRsn() + " left your " + p.title() + " party.");
            }
        }
    }

    // Seated members of the player's own ASAP posts who drop out of clan
    // chat: reported once per absence, and only after being seen online
    // (a friend added by name may not be in the clan at all). They stay in
    // the party.
    private void offline(List<Party> now, String rsn, Set<String> online, List<String> out)
    {
        if (online.isEmpty()) return;
        Instant t = Instant.now();
        Set<String> current = new HashSet<>();
        for (Party p : now)
        {
            if (p.isScheduled() || !p.isHostedBy(rsn)) continue;
            for (Party.Applicant a : p.accepted())
            {
                String key = member(p.getId(), a.getRsn());
                current.add(key);
                if (online.contains(Names.normalize(a.getRsn())))
                {
                    seenOnline.add(key);
                    missingSince.remove(key);
                    reportedOffline.remove(key);
                }
                else if (seenOnline.contains(key))
                {
                    Instant since = missingSince.computeIfAbsent(key, k -> t);
                    if (Duration.between(since, t).compareTo(OFFLINE_GRACE) >= 0 && reportedOffline.add(key))
                    {
                        out.add(a.getRsn() + " went offline (your " + p.title() + " party).");
                    }
                }
            }
        }
        seenOnline.retainAll(current);
        missingSince.keySet().retainAll(current);
        reportedOffline.retainAll(current);
    }

    // Scheduled parties: "starts in N min" to the host and accepted
    // members, and one "didn't fill" to the host once the time passes.
    private void reminders(List<Party> now, String rsn, List<String> out)
    {
        Instant t = Instant.now();
        for (Party p : now)
        {
            if (!p.isScheduled()) continue;
            boolean host = p.isHostedBy(rsn);
            Party.Applicant mine = p.applicantFor(rsn);
            boolean in = host || (mine != null && mine.isAccepted());
            long minutes = Duration.between(t, p.getScheduledFor()).toMinutes();
            // Keyed by the start time too, so a moved party reminds again.
            String key = p.getId() + "@" + p.getScheduledFor();
            if (in && minutes >= 0 && minutes <= 15 && startingSoon.add(key))
            {
                out.add((host ? "Your " : p.getHostRsn() + "'s ") + p.title() + " party starts in " + Math.max(1, minutes) + " min"
                    + world(p.getWorld()) + ".");
            }
            if (host && t.isAfter(p.getScheduledFor()) && !p.isFull() && unfilled.add(key))
            {
                out.add("Your " + p.title() + " party didn't fill by its start time. Edit the time or cancel it.");
            }
        }
    }

    private static String world(Integer world)
    {
        return world == null ? "" : " (World " + world + ")";
    }

    private void deliver(String message)
    {
        if (config.lfgPartyNotifications()) chat(message);
        if (config.lfgDesktopNotifications())
        {
            notifier.notify("Final Boss LFG: " + message);
        }
    }

    private void chat(String message)
    {
        Session session = clan.snapshot();
        long captured = generation;
        clientThread.invokeLater(() -> {
            if (captured != generation || !clan.current(session)) return true;
            GameState state = client.getGameState();
            // Mid-teleport or hop: hold the line until the chatbox is back.
            if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.CONNECTION_LOST) return false;
            if (state == GameState.LOGGED_IN) client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[LFG] " + message, null);
            return true;
        });
    }
}
