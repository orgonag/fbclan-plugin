package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.core.Names;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;

/**
 * Kill counts for parties. Two sources: the local player's own counts
 * as recorded by the core Chat Commands plugin (instant, only for this
 * account), and the public OSRS hiscores via RuneLite's hiscore client
 * (any player, cached 30 min, failures retried after a minute).
 */
@Singleton
public class Killcounts
{
    private static final long OK_TTL_MS = 30 * 60_000L;
    private static final long FAIL_TTL_MS = 60_000L;
    private static final long TIMEOUT_MS = 15_000L;

    /** A hiscore answer; -1 = unranked/unknown. */
    @Value
    public static class Hiscore
    {
        int kc;
        int hardKc;
        boolean unavailable;
        long fetchedAt;

        public boolean known(boolean hard)
        {
            return !unavailable && (hard ? hardKc : kc) >= 0;
        }

        public int kc(boolean hard)
        {
            return hard ? hardKc : kc;
        }

        boolean stale()
        {
            return System.currentTimeMillis() - fetchedAt > (unavailable ? FAIL_TTL_MS : OK_TTL_MS);
        }
    }

    private static final int MAX_ENTRIES = 250;

    private final ConfigManager configManager;
    private final HiscoreClient hiscores;
    // One entry per (player, activity) a host has browsed; bounded LRU.
    private final Map<String, CompletableFuture<Hiscore>> cache = new LinkedHashMap<String, CompletableFuture<Hiscore>>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<Hiscore>> eldest)
        {
            return size() > MAX_ENTRIES;
        }
    };

    @Inject
    public Killcounts(ConfigManager configManager, HiscoreClient hiscores)
    {
        this.configManager = configManager;
        this.hiscores = hiscores;
    }

    // The local account's count from RuneLite config, or null. DKs sum
    // the three kings.
    public Integer local(Activity activity, boolean hard)
    {
        if (configManager.getRSProfileKey() == null)
        {
            return null;
        }
        Integer total = null;
        for (String key : activity.localKillcountKeys(hard))
        {
            Integer kc;
            try
            {
                kc = configManager.getRSProfileConfiguration("killcount", key.toLowerCase(Locale.ROOT), int.class);
            }
            catch (RuntimeException e)
            {
                kc = null;
            }
            if (kc == null)
            {
                continue;
            }
            if (activity != Activity.DKS)
            {
                return kc;
            }
            total = (total == null ? 0 : total) + kc;
        }
        return total;
    }

    // Logout: nothing browsed is relevant to the next session.
    public synchronized void clear()
    {
        cache.clear();
    }

    // Cached hiscore answer, or null when none is known yet.
    public synchronized Hiscore cached(String rsn, Activity activity)
    {
        if (rsn == null || activity == null)
        {
            return null;
        }
        CompletableFuture<Hiscore> f = cache.get(key(rsn, activity));
        Hiscore h = f == null ? null : f.getNow(null);
        return h == null || h.stale() ? null : h;
    }

    // Starts a lookup unless one is fresh or in flight; `onDone` always
    // runs once an answer exists — on the hiscore client's thread, so
    // callers hop to the EDT themselves.
    public synchronized void lookup(String rsn, Activity activity, Runnable onDone)
    {
        if (rsn == null || activity == null)
        {
            return;
        }
        String key = key(rsn, activity);
        CompletableFuture<Hiscore> f = cache.get(key);
        if (f != null && (!f.isDone() || !f.getNow(null).stale()))
        {
            f.thenRun(onDone);
            return;
        }
        List<HiscoreSkill> skills = activity.hiscoreSkills();
        if (skills.isEmpty())
        {
            // No hiscore entry for this activity: a known "unranked".
            cache.put(key, CompletableFuture.completedFuture(new Hiscore(-1, -1, false, System.currentTimeMillis())));
            onDone.run();
            return;
        }
        CompletableFuture<Hiscore> next;
        try
        {
            next = hiscores.lookupAsync(rsn, HiscoreEndpoint.NORMAL)
                .orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .handle((result, ex) -> {
                    if (ex != null || result == null)
                    {
                        return new Hiscore(-1, -1, true, System.currentTimeMillis());
                    }
                    try
                    {
                        HiscoreSkill hard = activity.hardModeHiscoreSkill();
                        return new Hiscore(sum(result, skills), hard == null ? -1 : level(result, hard), false,
                            System.currentTimeMillis());
                    }
                    catch (RuntimeException e)
                    {
                        return new Hiscore(-1, -1, true, System.currentTimeMillis());
                    }
                });
        }
        catch (RuntimeException e)
        {
            next = CompletableFuture.completedFuture(new Hiscore(-1, -1, true, System.currentTimeMillis()));
        }
        cache.put(key, next);
        next.thenRun(onDone);
    }

    private static int sum(HiscoreResult result, List<HiscoreSkill> skills)
    {
        int total = -1;
        for (HiscoreSkill s : skills)
        {
            int kc = level(result, s);
            if (kc >= 0)
            {
                total = Math.max(total, 0) + kc;
            }
        }
        return total;
    }

    private static int level(HiscoreResult result, HiscoreSkill skill)
    {
        Skill s = result.getSkill(skill);
        return s == null ? -1 : s.getLevel();
    }

    private static String key(String rsn, Activity activity)
    {
        return Names.normalize(rsn) + "|" + activity.key();
    }
}
