package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
 * The live state behind the parties tab. Owns the 30-second poll, the
 * cached snapshot, the host heartbeat, forming a full party, and the
 * diff between consecutive polls that becomes chat/desktop
 * notifications. The tab only renders what this holds.
 */
@Slf4j
@Singleton
public class PartyBoard
{
    public static final int HEARTBEAT_MINUTES = 5;

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
    private ScheduledFuture<?> poll;
    private long lastHeartbeat;

    // Diff state; guarded by `this`.
    private Map<String, Party> previous = Collections.emptyMap();
    private Set<String> previousFormed = Collections.emptySet();
    private boolean primed;
    private final Set<String> selfLeft = new HashSet<>();

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

    public Party mine()
    {
        String rsn = clan.rsn();
        for (Party p : parties)
        {
            if (p.isHostedBy(rsn))
            {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ lifecycle

    public void start()
    {
        if (!config.enableLfg() || poll != null)
        {
            return;
        }
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

    public void stop()
    {
        if (poll != null)
        {
            poll.cancel(true);
            poll = null;
        }
        synchronized (this)
        {
            previous = Collections.emptyMap();
            previousFormed = Collections.emptySet();
            primed = false;
            selfLeft.clear();
        }
        parties = Collections.emptyList();
        formed = Collections.emptyList();
    }

    // Called by the tab before the local player withdraws/disbands, so the
    // next diff doesn't announce their own action back at them.
    public synchronized void expectSelfLeave(String partyId)
    {
        if (partyId != null)
        {
            selfLeft.add(partyId);
        }
    }

    // Executor. Fetch; form the hosted party if it filled; heartbeat; diff.
    public void refresh()
    {
        clientThread.invokeLater(this::readClient);
        List<Party> fetched = api.parties();
        String rsn = clan.rsn();
        Party mine = null;
        for (Party p : fetched)
        {
            if (p.isHostedBy(rsn))
            {
                mine = p;
            }
        }
        if (mine != null && mine.isFull() && api.form(mine))
        {
            fetched = api.parties();
            mine = null;
        }
        if (mine != null && System.currentTimeMillis() - lastHeartbeat >= HEARTBEAT_MINUTES * 60_000L)
        {
            lastHeartbeat = System.currentTimeMillis();
            api.heartbeat(rsn);
        }
        List<FormedParty> formedNow = api.formed();
        parties = fetched;
        formed = formedNow;
        notify(fetched, formedNow, rsn);
        listener.onChanged();
    }

    // Runs a write on the executor, reports failure to `onError`, refreshes.
    public void run(java.util.function.BooleanSupplier action, String failure, java.util.function.Consumer<String> onError)
    {
        executor.submit(() -> {
            boolean ok;
            try
            {
                ok = action.getAsBoolean();
            }
            catch (RuntimeException e)
            {
                ok = false;
            }
            onError.accept(ok ? null : failure);
            refresh();
        });
    }

    public void markHeartbeat()
    {
        lastHeartbeat = System.currentTimeMillis();
    }

    private void readClient()
    {
        Set<String> names = new HashSet<>();
        ClanChannel cc = client.getClanChannel();
        if (cc != null)
        {
            for (ClanChannelMember m : cc.getMembers())
            {
                if (m.getName() != null)
                {
                    names.add(com.github.orgonag.fbclan.core.Names.normalize(m.getName()));
                }
            }
        }
        online = names;
        world = client.getWorld();
    }

    // ------------------------------------------------------------ notifications

    private void notify(List<Party> now, List<FormedParty> formedNow, String rsn)
    {
        if (rsn == null)
        {
            return;
        }
        Map<String, Party> nowById = new HashMap<>();
        for (Party p : now)
        {
            nowById.put(p.getId(), p);
        }
        Set<String> formedIds = new HashSet<>();
        Set<String> formedFrom = new HashSet<>();
        for (FormedParty f : formedNow)
        {
            formedIds.add(f.getId());
            if (f.getPartyId() != null)
            {
                formedFrom.add(f.getPartyId());
            }
        }
        List<String> messages = new ArrayList<>();
        synchronized (this)
        {
            if (primed)
            {
                diff(previous, nowById, rsn, formedFrom, messages);
                for (FormedParty f : formedNow)
                {
                    if (!previousFormed.contains(f.getId()) && f.includes(rsn))
                    {
                        messages.add((f.isHostedBy(rsn) ? "Your" : f.getHostRsn() + "'s") + " " + f.title()
                            + " party has formed" + (f.getWorld() != null ? " (World " + f.getWorld() + ")" : "")
                            + ": " + f.roster() + ".");
                    }
                }
            }
            previous = nowById;
            previousFormed = formedIds;
            primed = true;
        }
        for (String m : messages)
        {
            deliver(m);
        }
    }

    private void diff(Map<String, Party> prev, Map<String, Party> now, String rsn, Set<String> formedFrom, List<String> out)
    {
        for (Party p : now.values())
        {
            Party before = prev.get(p.getId());
            if (p.isHostedBy(rsn))
            {
                for (Party.Applicant a : p.pending())
                {
                    if (before == null || before.applicantFor(a.getRsn()) == null)
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
            if (mine != null && mineBefore != null && mine.getStatus() != mineBefore.getStatus())
            {
                if (mine.isAccepted())
                {
                    out.add("You've been accepted to " + p.getHostRsn() + "'s " + p.title() + " party"
                        + (p.getWorld() != null ? " (World " + p.getWorld() + ")" : "") + ".");
                }
                else if (mine.getStatus() == Party.Status.DECLINED)
                {
                    out.add(p.getHostRsn() + " declined your " + p.title() + " application.");
                }
            }
            else if (mine == null && mineBefore != null && !selfLeft.remove(p.getId())
                && mineBefore.getStatus() != Party.Status.DECLINED)
            {
                out.add("You were removed from " + p.getHostRsn() + "'s " + p.title() + " party.");
            }
        }
        // Parties that vanished while the player was in them (a formed
        // party is announced from the formed list instead).
        for (Party before : prev.values())
        {
            if (now.containsKey(before.getId()) || before.isHostedBy(rsn) || formedFrom.contains(before.getId()))
            {
                continue;
            }
            Party.Applicant mineBefore = before.applicantFor(rsn);
            if (mineBefore != null && mineBefore.getStatus() != Party.Status.DECLINED && !selfLeft.remove(before.getId()))
            {
                out.add(before.getHostRsn() + "'s " + before.title() + " party was disbanded.");
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
