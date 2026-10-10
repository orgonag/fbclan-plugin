package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Session;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.client.callback.ClientThread;

/**
 * The live state behind the LFG tab: the 30-second poll, the cached
 * snapshot and every write. Each fetched snapshot goes to the
 * {@link PartyNotifier}; the tab only renders what this holds. All poll
 * and write work runs on RuneLite's single executor thread, one job at a
 * time.
 */
@Slf4j
@Singleton
public class PartyBoard
{
    // ASAP first (newest first), then scheduled by start time, then
    // scheduled posts whose start has passed (they linger up to 3 h).
    public static final Comparator<Party> ORDER = (a, b) -> {
        int byGroup = Integer.compare(group(a), group(b));
        if (byGroup != 0) return byGroup;
        int byStart = a.isScheduled() ? a.getScheduledFor().compareTo(b.getScheduledFor()) : 0;
        return byStart != 0 ? byStart : b.getCreatedAt().compareTo(a.getCreatedAt());
    };
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);

    public interface Listener
    {
        // Executor or client thread; the tab hops to the EDT.
        void onChanged();
    }

    private final Client client;
    private final ClientThread clientThread;
    private final FinalBossConfig config;
    private final Clan clan;
    private final PartyApi api;
    private final PartyNotifier notifications;
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
    private final AtomicBoolean writing = new AtomicBoolean();

    @Inject
    public PartyBoard(Client client, ClientThread clientThread, FinalBossConfig config, Clan clan, PartyApi api,
                      PartyNotifier notifications, ScheduledExecutorService executor)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.clan = clan;
        this.api = api;
        this.notifications = notifications;
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
        return Instant.now().isAfter(p.getScheduledFor()) ? 2 : 1;
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
        if (poll != null)
        {
            poll.cancel(true);
            poll = null;
        }
        notifications.reset();
        parties = Collections.emptyList();
        formed = Collections.emptyList();
    }

    // ------------------------------------------------------------ poll and writes

    // Executor. A failed fetch leaves the snapshot alone: an outage must
    // never read as "every party was disbanded".
    public void refresh()
    {
        Session session = clan.snapshot();
        long capturedEpoch = epoch;
        if (!enabled || !config.enableLfg() || !clan.current(session)) return;
        clientThread.invokeLater(() -> { if (enabled && capturedEpoch == epoch && clan.current(session)) readClient(); });
        PartyApi.Snapshot fetched = api.fetch();
        // Under the lock: stop() must not land between the check and the commit.
        synchronized (this)
        {
            if (!enabled || capturedEpoch != epoch || !clan.current(session)) return;
            if (fetched == null)
            {
                refreshError = "Board unavailable. Showing the last loaded parties.";
            }
            else
            {
                refreshError = null;
                parties = fetched.getParties();
                formed = fetched.getFormed();
                notifications.onSnapshot(parties, formed, session.getRsn(), online);
            }
        }
        listener.onChanged();
    }

    // One write at a time, on the executor. The action returns null when
    // done, else the server's reason ("Party is full"), or "" when there
    // is no readable reason; `onError` then gets the reason or `failure`.
    public void run(Function<Session, String> action, String failure, Consumer<String> onError)
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
            String outcome = "";
            try
            {
                if (enabled && capturedEpoch == epoch && config.enableLfg() && clan.current(session)) outcome = action.apply(session);
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
                onError.accept(outcome == null ? null : outcome.isEmpty() ? failure : outcome);
                refresh();
            }
        });
    }

    // Inside run(): the player leaving (or withdrawing). Recorded first so
    // the snapshot that follows the write doesn't announce it back to them
    // as a kick (kept even if the reply is lost: it may still have landed).
    public String leave(Session session, String partyId)
    {
        notifications.expectSelfLeave(partyId);
        return api.leave(session, partyId);
    }

    // Inside run(): the host kicking someone, so it isn't reported as them leaving.
    public String kick(Session session, String partyId, String rsn)
    {
        notifications.expectKick(partyId, rsn);
        return api.kick(session, partyId, rsn);
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
}
