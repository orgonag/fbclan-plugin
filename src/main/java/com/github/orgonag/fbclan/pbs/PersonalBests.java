package com.github.orgonag.fbclan.pbs;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Keeps the clan PB board in step with the PBs RuneLite's Chat Commands
 * plugin already records per account (the source of truth, raid team
 * sizes included); the clan database is only a copy of it for display.
 * The first sync of each login sends every PB, so that copy always
 * matches this client whatever happened in between (a rename, another
 * backend, a reset); every 30 minutes after that, only what changed.
 * A PB that is new since the last successful sync is sent as "live"
 * (the clan-bests list shows those); everything else as "seed".
 */
@Slf4j
@Singleton
public class PersonalBests
{
    private static final int MAX_BATCH = 250; // fb_submit_pbs rejects > 300
    // What was last synced, per RuneLite profile (kept outside the
    // plugin's settings group).
    private static final String GROUP = "finalbossui";
    private static final String KEY = "pbSynced";
    private static final Type TIMES = new TypeToken<Map<String, Double>>() {}.getType();

    private final FinalBossConfig config;
    private final Clan clan;
    private final Supabase db;
    private final ConfigManager configManager;
    private final ScheduledExecutorService executor;
    private final Gson gson;
    private final AtomicBoolean syncing = new AtomicBoolean();
    // The login (session generation) whose PBs have all been sent.
    private volatile long fullSync = -1;

    @Inject
    public PersonalBests(FinalBossConfig config, Clan clan, Supabase db, ConfigManager configManager,
                         ScheduledExecutorService executor, Gson gson)
    {
        this.config = config;
        this.clan = clan;
        this.db = db;
        this.configManager = configManager;
        this.executor = executor;
        this.gson = gson;
    }

    // Client thread (the world-type check reads the client).
    public void sync()
    {
        Session session = clan.snapshot();
        if (!clan.canUpload() || !config.enablePbUpload() || !clan.onStandardWorld() || !syncing.compareAndSet(false, true)) return;
        boolean queued = false;
        try
        {
            if (!session.getProfile().equals(configManager.getRSProfileKey())) return;
            Map<String, Double> now = stored();
            Map<String, Double> before = lastSynced(session);
            boolean everything = fullSync != session.getGeneration();
            List<JsonObject> sending = new ArrayList<>();
            now.forEach((key, seconds) -> {
                boolean fresh = before == null || !seconds.equals(before.get(key));
                if (!fresh && !everything) return;
                JsonObject e = new JsonObject();
                e.addProperty("boss_key", key);
                e.addProperty("seconds", seconds);
                e.addProperty("source", fresh && before != null ? "live" : "seed");
                sending.add(e);
            });
            String snapshot = gson.toJson(now);
            if (sending.isEmpty())
            {
                // Nothing to send, but record the first sync so later PBs count as live.
                if (before == null) configManager.setConfiguration(GROUP, session.getProfile(), KEY, snapshot);
                return;
            }
            queued = true;
            executor.submit(() -> {
                try
                {
                    // Written to the profile it was read from, whichever account is logged in by now.
                    if (submit(session, sending))
                    {
                        configManager.setConfiguration(GROUP, session.getProfile(), KEY, snapshot);
                        fullSync = session.getGeneration();
                    }
                }
                finally
                {
                    syncing.set(false);
                }
            });
        }
        finally
        {
            if (!queued) syncing.set(false);
        }
    }

    // Every PB the Chat Commands plugin holds for the current profile.
    private Map<String, Double> stored()
    {
        Map<String, Double> out = new TreeMap<>();
        for (String key : configManager.getRSProfileConfigurationKeys("personalbest", configManager.getRSProfileKey(), ""))
        {
            try
            {
                Double seconds = configManager.getRSProfileConfiguration("personalbest", key, double.class);
                if (key != null && !key.isEmpty() && seconds != null && Double.isFinite(seconds) && seconds > 0 && seconds < 86400)
                {
                    out.put(key, seconds);
                }
            }
            catch (RuntimeException e)
            {
                log.debug("Ignoring invalid stored PB");
            }
        }
        return out;
    }

    // Null when this profile has never synced.
    private Map<String, Double> lastSynced(Session session)
    {
        String json = configManager.getConfiguration(GROUP, session.getProfile(), KEY);
        if (json == null || json.isEmpty()) return null;
        try
        {
            return gson.fromJson(json, TIMES);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    // Executor.
    private boolean submit(Session session, List<JsonObject> entries)
    {
        for (int start = 0; start < entries.size(); start += MAX_BATCH)
        {
            if (!clan.current(session) || !config.enablePbUpload()) return false;
            JsonArray batch = new JsonArray();
            entries.subList(start, Math.min(start + MAX_BATCH, entries.size())).forEach(batch::add);
            JsonObject payload = new JsonObject();
            payload.addProperty("p_rsn", session.getRsn());
            payload.add("p_entries", batch);
            if (!db.rpc("fb_submit_pbs", payload)) return false;
        }
        return true;
    }
}
