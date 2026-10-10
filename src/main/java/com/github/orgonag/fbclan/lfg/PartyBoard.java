package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Session;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;

/**
 * The live state behind the LFG tab: the 30-second poll, the cached
 * snapshot, every write, and the diff between consecutive polls that
 * becomes chat/desktop notifications. The tab only renders what this holds.
 */
@Slf4j
@Singleton
public class PartyBoard
{
    // ASAP first (newest first), then scheduled by start time, then
    // scheduled posts whose start (plus grace) has passed.
    public static final Comparator<Party> ORDER = (a, b) -> {
        int byGroup = Integer.compare(group(a), group(b));
        if (byGroup != 0) return byGroup;
        int byStart = a.isScheduled() ? a.getScheduledFor().compareTo(b.getScheduledFor()) : 0;
        return byStart != 0 ? byStart : b.getCreatedAt().compareTo(a.getCreatedAt());
    };
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);

    public interface Listener
    {
        // Executor thread; the tab hops to the EDT.
        void onChanged();
    }

    private final Client client;
    private final ClientThread clientThread;
    private final FinalBossConfig config;
    private final Clan clan;
    private final PartyApi api;
    private final Notifier notifier;
    private final ScheduledExecutorService executor;

    private volatile List<Party> parties = Collections.emptyList();
    private volatile List<FormedParty> formed = Collections.emptyList();
    private volatile Set<String> online = Collections.emptySet();
    private volatile int world;
    private volatile Listener listener = () -> {};
    private volatile boolean enabled;
    private volatile long epoch;
    private volatile String refreshError;
    private ScheduledFuture<?> poll;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean writing = new AtomicBoolean();

    // Diff state; guarded by `this`.
    private Map<String, Party> previous = Collections.emptyMap();
    private Set<String> previousFormed = Collections.emptySet();
    private boolean primed;
    private final Set<String> selfLeft = new HashSet<>();
    // Scheduled parties already announced as starting soon / unfilled.
    private final Set<String> startingSoon = new HashSet<>();
    private final Set<String> unfilled = new HashSet<>();

    @Inject
    public PartyBoard(Client client, ClientThread clientThread, FinalBossConfig config, Clan clan, PartyApi api,
                      Notifier notifier, ScheduledExecutorService executor)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.clan = clan;
        this.api = api;
        this.notifier = notifier;
        this.executor = executor;
    }

    // ------------------------------------------------------------ reads

    public void setListener(Listener listener)
    {
        this.listener = listener;
    }

    public List<Party> parties()
    {
        return parties;
    }

    public List<FormedParty> formed()
    {
        return formed;
    }

    // Normalized names of clan members currently in the clan channel.
    public Set<String> online()
    {
        return online;
    }

    public int world()
    {
        return world;
    }

    public String refreshError()
    {
        return refreshError;
    }

    public boolean isBusy()
    {
        return writing.get();
    }

    // True while the board is polling (verified, LFG on).
    public boolean running()
    {
        return enabled;
    }

    // Every post this player hosts, in ORDER.
    public List<Party> mine()
    {
        String rsn = clan.rsn();
        List<Party> out = new ArrayList<>();
        for (Party p : parties)
        {
            if (p.isHostedBy(rsn)) out.add(p);
        }
        out.sort(ORDER);
        return out;
    }

    // This player's ASAP post, else null.
    public Party mineAsap()
    {
        for (Party p : mine())
        {
            if (!p.isScheduled()) return p;
        }
        return null;
    }

    public int mineScheduled()
    {
        int n = 0;
        for (Party p : mine())
        {
            if (p.isScheduled()) n++;
        }
        return n;
    }

    // "ASAP", or the start in the player's time zone: "Sat 20:00".
    public static String when(Instant at)
    {
        return at == null ? "ASAP" : DAY_TIME.withZone(ZoneId.systemDefault()).format(at);
    }

    private static int group(Party p)
    {
        if (!p.isScheduled()) return 0;
        return Instant.now().isAfter(p.getScheduledFor().plus(Party.GRACE)) ? 2 : 1;
    }

    // ------------------------------------------------------------ lifecycle

    public synchronized void start()
    {
        if (!config.enableLfg() || poll != null) return;
        enabled = true;
        poll = executor.scheduleAtFixedRate(() -> {
            try
            {
                refresh();
            }
            catch (Exception e)
            {
                log.warn("LFG poll error", e);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    public synchronized void stop()
    {
        enabled = false;
        epoch++;
        online = Collections.emptySet();
        world = 0;
        refreshError = null;
        api.publish(PartyApi.EMPTY);
        if (poll != null)
        {
            poll.cancel(true);
            poll = null;
        }
        previous = Collections.emptyMap();
        previousFormed = Collections.emptySet();
        primed = false;
        selfLeft.clear();
        startingSoon.clear();
        unfilled.clear();
        parties = Collections.emptyList();
        formed = Collections.emptyList();
    }

    // Called before the local player leaves/cancels, so the next diff
    // doesn't announce their own action back at them.
    public synchronized void expectSelfLeave(String partyId)
    {
        if (partyId != null) selfLeft.add(partyId);
    }

    // ------------------------------------------------------------ poll and writes

    // Executor. A failed fetch leaves the snapshot alone: an outage must
    // never read as "every party was cancelled".
    public void refresh()
    {
        Session session = clan.snapshot();
        long capturedEpoch = epoch;
        if (!enabled || !config.enableLfg() || !clan.current(session) || !refreshing.compareAndSet(false, true)) return;
        try
        {
            clientThread.invokeLater(() -> { if (enabled && capturedEpoch == epoch && clan.current(session)) readClient(); });
            PartyApi.Snapshot fetched = api.fetch();
            if (fetched == null)
            {
                if (enabled && capturedEpoch == epoch && clan.current(session))
                {
                    refreshError = "Board unavailable. Showing the last loaded parties.";
                    listener.onChanged();
                }
                return;
            }
            synchronized (this)
            {
                if (!enabled || capturedEpoch != epoch || !clan.current(session)) return;
                refreshError = null;
                api.publish(fetched);
                parties = fetched.getParties();
                formed = fetched.getFormed();
                notify(parties, formed, session.getRsn());
            }
            if (enabled && capturedEpoch == epoch && clan.current(session)) listener.onChanged();
        }
        finally
        {
            refreshing.set(false);
        }
    }

    // One write at a time, on the executor. `onError` gets null on success,
    // else the server's reason ("Party is full") or `failure`.
    public void run(BooleanSupplier action, String failure, Consumer<String> onError)
    {
        Session session = clan.snapshot();
        long capturedEpoch = epoch;
        if (!enabled || !config.enableLfg() || !clan.current(session) || !writing.compareAndSet(false, true))
        {
            onError.accept("Wait for the current action, or reconnect and refresh.");
            return;
        }
        listener.onChanged();
        executor.submit(() -> {
            boolean ok = false;
            String reason = null;
            try
            {
                if (enabled && capturedEpoch == epoch && config.enableLfg()) ok = api.inSession(session, action);
                reason = api.refusal();
            }
            catch (RuntimeException e)
            {
                log.warn("LFG action failed", e);
            }
            finally
            {
                writing.set(false);
            }
            if (enabled && capturedEpoch == epoch && clan.current(session))
            {
                onError.accept(ok ? null : reason != null ? reason : failure);
                refresh();
                listener.onChanged();
            }
        });
    }

    // Client thread: who is in the clan channel, and the current world.
    private void readClient()
    {
        Set<String> names = new HashSet<>();
        ClanChannel cc = client.getClanChannel();
        if (cc != null)
        {
            for (ClanChannelMember m : cc.getMembers())
            {
                if (m.getName() != null) names.add(Names.normalize(m.getName()));
            }
        }
        boolean changed = !online.equals(names) || world != client.getWorld();
        online = Collections.unmodifiableSet(names);
        world = client.getWorld();
        if (changed) listener.onChanged();
    }

    // ------------------------------------------------------------ notifications

    private void notify(List<Party> now, List<FormedParty> formedNow, String rsn)
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
                newPosts(previous, now, rsn, announcements);
                reminders(now, rsn, messages);
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
                // reminders aren't replayed on every login.
                reminders(now, rsn, new ArrayList<>());
            }
            previous = nowById;
            previousFormed = formedIds;
            primed = true;
        }
        messages.forEach(this::deliver);
        if (config.lfgNewPostAnnouncements()) announcements.forEach(this::chat);
    }

    // Other members' posts that appeared since the last poll: "Dopezt
    // created a party of 4 for ToB (ASAP)." Posts made while this player
    // was offline are on the board already and aren't replayed.
    private static void newPosts(Map<String, Party> prev, List<Party> now, String rsn, List<String> out)
    {
        for (Party p : now)
        {
            if (prev.containsKey(p.getId()) || p.isHostedBy(rsn)) continue;
            out.add(p.getHostRsn() + " created a party of " + p.getCapacity() + " for " + p.title()
                + " (" + when(p.getScheduledFor()) + ").");
        }
    }

    private void diff(Map<String, Party> prev, Map<String, Party> now, String rsn, Map<String, FormedParty> formedFrom, List<String> out)
    {
        for (Party p : now.values())
        {
            Party before = prev.get(p.getId());
            if (p.isHostedBy(rsn))
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
                continue;
            }
            Party.Applicant mine = p.applicantFor(rsn);
            Party.Applicant mineBefore = before == null ? null : before.applicantFor(rsn);
            if (mine != null && mine.getStatus() != Party.Status.DECLINED && before != null
                && !Objects.equals(before.getScheduledFor(), p.getScheduledFor()))
            {
                out.add(p.getHostRsn() + " moved their " + p.title() + " party to " + when(p.getScheduledFor()) + ".");
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
            // Only a seated member is "removed"; a pending application also
            // disappears quietly when the player joins another ASAP party.
            else if (mine == null && mineBefore != null && !selfLeft.remove(p.getId()) && mineBefore.isAccepted())
            {
                out.add("You were removed from " + p.getHostRsn() + "'s " + p.title() + " party.");
            }
        }
        // Parties that vanished. Members of one that formed hear it from the
        // formed list; everyone else involved is told here.
        for (Party before : prev.values())
        {
            if (now.containsKey(before.getId())) continue;
            FormedParty formedAs = formedFrom.get(before.getId());
            boolean formed = formedAs != null;
            boolean expired = !Instant.now().isBefore(before.expiresAt());
            if (before.isHostedBy(rsn))
            {
                if (!formed && expired) out.add("Your " + before.title() + " post expired after 7 days without filling.");
                continue;
            }
            Party.Applicant mineBefore = before.applicantFor(rsn);
            if (mineBefore == null || mineBefore.getStatus() == Party.Status.DECLINED || selfLeft.remove(before.getId())) continue;
            if (!formed)
            {
                out.add(before.getHostRsn() + "'s " + before.title() + " party " + (expired ? "expired." : "was cancelled."));
            }
            else if (!formedAs.includes(rsn))
            {
                out.add(before.getHostRsn() + "'s " + before.title() + " party filled without you.");
            }
        }
        selfLeft.clear();
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
        long capturedEpoch = epoch;
        clientThread.invokeLater(() -> {
            if (!enabled || capturedEpoch != epoch || !clan.current(session)) return true;
            GameState state = client.getGameState();
            // Mid-teleport or hop: hold the line until the chatbox is back.
            if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.CONNECTION_LOST) return false;
            if (state == GameState.LOGGED_IN) client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[LFG] " + message, null);
            return true;
        });
    }
}
