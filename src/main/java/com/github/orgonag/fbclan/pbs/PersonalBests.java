package com.github.orgonag.fbclan.pbs;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Keeps the clan PB board in step with the PBs RuneLite's Chat Commands
 * plugin already records per account (the source of truth, raid team
 * sizes included). After verification and then every 30 minutes, it
 * uploads only the entries that changed since this account's last
 * successful sync: "seed" on the first sync ever, "live" afterwards (the
 * board's clan-bests list shows live ones). The server keeps only
 * faster times.
 */
@Slf4j
@Singleton
public class PersonalBests
{
    private static final int MAX_BATCH = 250; // fb_submit_pbs rejects > 300
    // Last synced snapshot, per RuneLite profile. Its own group: a write
    // under "finalboss" would restart polling.
    private static final String GROUP = "finalbossui";
    private static final String KEY = "pbSynced";

    @Value
    private static class Entry
    {
        String bossKey;
        double seconds;
        String source;
    }

    private final FinalBossConfig config;
    private final Clan clan;
    private final Supabase db;
    private final ConfigManager configManager;
    private final ScheduledExecutorService executor;
    private final Gson gson;
    private final AtomicBoolean syncing = new AtomicBoolean();

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
            Map<String, Double> before = lastSynced();
            List<Entry> changed = new ArrayList<>();
            now.forEach((key, seconds) -> {
                Double was = before == null ? null : before.get(key);
                if (was == null || !was.equals(seconds)) changed.add(new Entry(key, seconds, before == null ? "seed" : "live"));
            });
            if (changed.isEmpty()) return;
            String snapshot = gson.toJson(now);
            queued = true;
            executor.submit(() -> {
                try
                {
                    if (submit(session, changed) && clan.current(session)) configManager.setRSProfileConfiguration(GROUP, KEY, snapshot);
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
    private Map<String, Double> lastSynced()
    {
        String json = configManager.getRSProfileConfiguration(GROUP, KEY);
        if (json == null || json.isEmpty()) return null;
        try
        {
            return gson.fromJson(json, new TypeToken<Map<String, Double>>() {}.getType());
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    // Executor.
    private boolean submit(Session session, List<Entry> entries)
    {
        for (int start = 0; start < entries.size(); start += MAX_BATCH)
        {
            if (!clan.current(session) || !config.enablePbUpload()) return false;
            JsonArray batch = new JsonArray();
            for (Entry s : entries.subList(start, Math.min(start + MAX_BATCH, entries.size())))
            {
                JsonObject e = new JsonObject();
                e.addProperty("boss_key", s.getBossKey());
                e.addProperty("seconds", s.getSeconds());
                e.addProperty("source", s.getSource());
                batch.add(e);
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("p_rsn", session.getRsn());
            payload.add("p_entries", batch);
            if (!db.rpc("fb_submit_pbs", payload)) return false;
        }
        return true;
    }
}
