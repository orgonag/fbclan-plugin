package com.github.orgonag.fbclan.pbs;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
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

    // Pairing state; client thread only.
    private String lastBossKey;
    private int lastBossTick = -1;
    private double lastPbSeconds = -1;
    private boolean lastPbNew;
    private String lastPbTeamSize;
    private volatile boolean seeded;

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
        seeded = false;
        lastBossKey = null;
        lastBossTick = -1;
        lastPbSeconds = -1;
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
        String rsn = clan.rsn();
        if (!clan.canUpload() || !config.enablePbUpload() || !clan.onStandardWorld())
        {
            return;
        }
        List<Submission> subs = process(event.getMessage(), client.getTickCount());
        if (!subs.isEmpty())
        {
            executor.submit(() -> submit(rsn, subs));
        }
    }

    // Once per session, after verification: push RuneLite's stored PBs so
    // the board is complete from day one.
    public void maybeSeed()
    {
        String rsn = clan.rsn();
        if (!clan.canUpload() || seeded || !config.enablePbUpload() || !clan.onStandardWorld())
        {
            return;
        }
        seeded = true;
        executor.submit(() -> {
            String profile = configManager.getRSProfileKey();
            if (profile == null)
            {
                return;
            }
            List<Submission> seeds = new ArrayList<>();
            for (String key : configManager.getRSProfileConfigurationKeys("personalbest", profile, ""))
            {
                Double seconds = configManager.getRSProfileConfiguration("personalbest", key, double.class);
                if (key != null && !key.isEmpty() && seconds != null && seconds > 0)
                {
                    seeds.add(new Submission(key, seconds, "seed"));
                }
            }
            submit(rsn, seeds);
        });
    }

    // ------------------------------------------------------------ pairing

    private List<Submission> process(String message, int tick)
    {
        List<Submission> result = handle(message, tick);
        // Core forgets the remembered KC at the end of any message on a
        // later tick: the KC/PB pair always lands within one tick.
        if (lastBossKey != null && lastBossTick != tick)
        {
            lastBossKey = null;
            lastBossTick = -1;
        }
        return result;
    }

    private List<Submission> handle(String message, int tick)
    {
        Optional<String> kc = PbParser.killCount(message);
        if (kc.isPresent())
        {
            return onKillCount(kc.get(), tick);
        }
        List<Submission> sepulchre = PbParser.sepulchre(message);
        if (!sepulchre.isEmpty())
        {
            return sepulchre;
        }
        Optional<Duration> duration = PbParser.duration(message);
        return duration.isPresent() ? onDuration(duration.get()) : Collections.emptyList();
    }

    private List<Submission> onKillCount(String bossKey, int tick)
    {
        if (lastPbSeconds > -1)
        {
            // PB line arrived first (raids do this); attach it now.
            String teamSize = lastPbTeamSize;
            if (bossKey.contains("theatre of blood"))
            {
                teamSize = teamLabel(teamSize(VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2,
                    VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4));
            }
            else if (bossKey.contains("tombs of amascut"))
            {
                teamSize = teamLabel(teamSize(VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2,
                    VarbitID.TOA_CLIENT_P3, VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6,
                    VarbitID.TOA_CLIENT_P7));
            }
            String source = lastPbNew ? "live" : "seed";
            List<Submission> out = new ArrayList<>();
            out.add(new Submission(bossKey, lastPbSeconds, source));
            if (teamSize != null)
            {
                out.add(new Submission(bossKey + " " + teamSize.toLowerCase(Locale.ROOT), lastPbSeconds, source));
            }
            lastPbSeconds = -1;
            lastPbTeamSize = null;
            return out;
        }
        lastBossKey = bossKey;
        lastBossTick = tick;
        return Collections.emptyList();
    }

    private List<Submission> onDuration(Duration d)
    {
        if (lastBossKey != null)
        {
            // KC line arrived first (most bosses).
            Submission sub = new Submission(lastBossKey, d.getSeconds(), d.isNewPb() ? "live" : "seed");
            lastPbSeconds = -1;
            lastPbTeamSize = null;
            return Collections.singletonList(sub);
        }
        lastPbSeconds = d.getSeconds();
        lastPbNew = d.isNewPb();
        lastPbTeamSize = d.getTeamSize();
        return Collections.emptyList();
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
        return size == 1 ? "Solo" : size + " players";
    }

    // ------------------------------------------------------------ submit

    private void submit(String rsn, List<Submission> subs)
    {
        for (int start = 0; start < subs.size(); start += MAX_BATCH)
        {
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
            payload.addProperty("p_rsn", rsn);
            payload.add("p_entries", entries);
            if (db.rpc("submit_pbs", payload))
            {
                log.debug("Submitted {} PB(s) for {}", chunk.size(), rsn);
            }
        }
    }
}
