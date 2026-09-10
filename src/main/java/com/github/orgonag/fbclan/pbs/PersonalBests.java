package com.github.orgonag.fbclan.pbs;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.core.Supabase;
import com.github.orgonag.fbclan.pbs.PbParser.Duration;
import com.github.orgonag.fbclan.pbs.PbParser.Submission;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;

/**
 * Upload side of the PB leaderboard. Pairs the two chat lines a kill
 * produces (kill count carries the boss, duration carries the time,
 * either order, same tick) exactly like core ChatCommands, and once per
 * session seeds the PBs RuneLite already stores locally. Everything goes
 * through the improve-only submit_pbs function, so a worse time is a
 * silent no-op server-side.
 */
@Slf4j
@Singleton
public class PersonalBests
{
    private static final int MAX_BATCH = 250; // submit_pbs rejects > 300

    private final Client client;
    private final FinalBossConfig config;
    private final Clan clan;
    private final Supabase db;
    private final ConfigManager configManager;
    private final ScheduledExecutorService executor;

    private final PbCorrelator correlator = new PbCorrelator();
    private volatile long seededGeneration = -1;
    private final java.util.concurrent.atomic.AtomicBoolean seeding = new java.util.concurrent.atomic.AtomicBoolean();

    @Inject
    public PersonalBests(Client client, FinalBossConfig config, Clan clan, Supabase db,
                         ConfigManager configManager, ScheduledExecutorService executor)
    {
        this.client = client;
        this.config = config;
        this.clan = clan;
        this.db = db;
        this.configManager = configManager;
        this.executor = executor;
    }

    public void resetSession()
    {
        seededGeneration = -1;
        correlator.reset();
    }

    // Client thread (chat dispatch).
    public void onChatMessage(ChatMessage event)
    {
        ChatMessageType type = event.getType();
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM
            && type != ChatMessageType.FRIENDSCHATNOTIFICATION)
        {
            return;
        }
        Session session = clan.snapshot();
        if (!clan.canUpload() || !config.enablePbUpload() || !clan.onStandardWorld())
        {
            return;
        }
        List<Submission> subs = correlator.accept(event.getMessage(), client.getTickCount(), this::raidTeam);
        if (!subs.isEmpty())
        {
            executor.submit(() -> { if (!submit(session, subs) && clan.current(session)) seededGeneration = -1; });
        }
    }

    // Once per session, after verification: push RuneLite's stored PBs so
    // the board is complete from day one.
    public void maybeSeed()
    {
        Session session = clan.snapshot();
        if (!clan.current(session) || seededGeneration == session.getGeneration() || !config.enablePbUpload()
            || !clan.onStandardWorld() || !seeding.compareAndSet(false, true)) return;
        // Capture all profile-dependent reads on the client thread, before queuing I/O.
        List<Submission> seeds = new ArrayList<>();
        try
        {
            if (!session.getProfile().equals(configManager.getRSProfileKey())) return;
            for (String key : configManager.getRSProfileConfigurationKeys("personalbest", session.getProfile(), ""))
            {
                try
                {
                    Double seconds = configManager.getRSProfileConfiguration("personalbest", key, double.class);
                    if (key != null && !key.isEmpty() && seconds != null && Double.isFinite(seconds) && seconds > 0 && seconds < 86400)
                        seeds.add(new Submission(key, seconds, "seed"));
                }
                catch (RuntimeException e) { log.debug("Ignoring invalid stored PB"); }
            }
        }
        finally { seeding.set(false); }
        if (!seeding.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try { if (submit(session, seeds) && clan.current(session)) seededGeneration = session.getGeneration(); }
            finally { seeding.set(false); }
        });
    }

    private String raidTeam(String boss)
    {
        if (boss.contains("theatre of blood")) return teamLabel(teamSize(VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1,
            VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4));
        if (boss.contains("tombs of amascut")) return teamLabel(teamSize(VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1,
            VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3, VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5,
            VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7));
        return null;
    }

    private int teamSize(int... varbits)
    {
        int n = 0;
        for (int v : varbits)
        {
            n += Math.min(client.getVarbitValue(v), 1);
        }
        return n;
    }

    private static String teamLabel(int size)
    {
        return size == 0 ? null : size == 1 ? "Solo" : size + " players";
    }

    // ------------------------------------------------------------ submit

    private boolean submit(Session session, List<Submission> subs)
    {
        for (int start = 0; start < subs.size(); start += MAX_BATCH)
        {
            if (!clan.current(session) || !config.enablePbUpload()) return false;
            List<Submission> chunk = subs.subList(start, Math.min(start + MAX_BATCH, subs.size()));
            JsonArray entries = new JsonArray();
            for (Submission s : chunk)
            {
                JsonObject e = new JsonObject();
                e.addProperty("boss_key", s.getBossKey());
                e.addProperty("seconds", s.getSeconds());
                e.addProperty("source", s.getSource());
                entries.add(e);
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("p_rsn", session.getRsn());
            payload.add("p_entries", entries);
            if (!db.rpc("fb_submit_pbs", payload)) return false;
        }
        return true;
    }
}
