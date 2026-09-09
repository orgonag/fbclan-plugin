package com.github.orgonag.fbclan.lfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;

/**
 * Looks up a player's kill count for an activity on the public OSRS
 * hiscores (RuneLite's own hiscore client), since a client can't read
 * another account's boss KC locally. Used to show a host each
 * applicant's KC and to tell a viewer whether they meet a party's
 * minimum. Results are cached per (player, activity) and time-limited.
 * This is the only outbound call the LFG feature makes besides Supabase.
 */
@Slf4j
public class LfgKillcountService
{
    public static final int UNKNOWN = -1;

    private static final long SUCCESS_TTL_MS = 30 * 60_000L;
    private static final long FAILURE_RETRY_MS = 60_000L;
    private static final int MAX_ENTRIES = 250;
    // A lookup that never answers (429/5xx leaves the client's future
    // uncompleted) would strand callers on "Checking..." forever.
    private static final long LOOKUP_TIMEOUT_MS = 15_000L;

    public static final class Result
    {
        public final int killCount;
        public final int hardModeKillCount;
        public final boolean unavailable;
        private final long fetchedAt = System.currentTimeMillis();

        Result(int killCount, int hardModeKillCount, boolean unavailable)
        {
            this.killCount = killCount;
            this.hardModeKillCount = hardModeKillCount;
            this.unavailable = unavailable;
        }

        public int killcount(boolean hardMode)
        {
            return hardMode ? hardModeKillCount : killCount;
        }

        public boolean isKnown(boolean hardMode)
        {
            return !unavailable && killcount(hardMode) >= 0;
        }

        private boolean isStale()
        {
            long age = System.currentTimeMillis() - fetchedAt;
            return age > (unavailable ? FAILURE_RETRY_MS : SUCCESS_TTL_MS);
        }
    }

    private final HiscoreClient hiscoreClient;
    private final Object lock = new Object();
    private final Map<String, Result> cache = new LinkedHashMap<String, Result>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Result> eldest)
        {
            return size() > MAX_ENTRIES;
        }
    };
    private final Set<String> inFlight = new HashSet<>();
    private final Map<String, List<Runnable>> waiting = new HashMap<>();

    public LfgKillcountService(HiscoreClient hiscoreClient)
    {
        this.hiscoreClient = hiscoreClient;
    }

    public Result cached(String rsn, LfgActivity activity)
    {
        if (rsn == null || activity == null)
        {
            return null;
        }
        synchronized (lock)
        {
            Result hit = cache.get(key(rsn, activity));
            return hit == null || hit.isStale() ? null : hit;
        }
    }

    // Fetches at most once per (player, activity) until the cached result
    // goes stale. onComplete always fires on the EDT — also when the
    // result was already cached or another lookup is in flight.
    public void lookup(String rsn, LfgActivity activity, Runnable onComplete)
    {
        if (rsn == null || activity == null)
        {
            return;
        }
        String key = key(rsn, activity);
        synchronized (lock)
        {
            Result cached = cache.get(key);
            if (cached != null && !cached.isStale())
            {
                if (onComplete != null)
                {
                    SwingUtilities.invokeLater(onComplete);
                }
                return;
            }
            if (onComplete != null)
            {
                waiting.computeIfAbsent(key, k -> new ArrayList<>()).add(onComplete);
            }
            if (!inFlight.add(key))
            {
                return;
            }
        }

        List<HiscoreSkill> skills = skillsFor(activity);
        if (skills.isEmpty())
        {
            complete(key, new Result(UNKNOWN, UNKNOWN, false));
            return;
        }
        try
        {
            hiscoreClient.lookupAsync(rsn, HiscoreEndpoint.NORMAL)
                .orTimeout(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> {
                    int kc = UNKNOWN;
                    int hard = UNKNOWN;
                    boolean unavailable = ex != null || result == null;
                    try
                    {
                        if (!unavailable)
                        {
                            kc = count(result, skills);
                            HiscoreSkill hardSkill = hardSkillFor(activity);
                            if (hardSkill != null)
                            {
                                hard = count(result, Collections.singletonList(hardSkill));
                            }
                        }
                        else
                        {
                            log.debug("Hiscore lookup failed for {}", rsn, ex);
                        }
                    }
                    catch (RuntimeException e)
                    {
                        log.debug("Hiscore result unreadable for {}", rsn, e);
                        unavailable = true;
                    }
                    complete(key, new Result(kc, hard, unavailable));
                });
        }
        catch (RuntimeException e)
        {
            log.debug("Hiscore lookup could not start for {}", rsn, e);
            complete(key, new Result(UNKNOWN, UNKNOWN, true));
        }
    }

    private void complete(String key, Result result)
    {
        List<Runnable> callbacks;
        synchronized (lock)
        {
            cache.put(key, result);
            inFlight.remove(key);
            callbacks = waiting.remove(key);
        }
        if (callbacks != null)
        {
            for (Runnable r : callbacks)
            {
                SwingUtilities.invokeLater(r);
            }
        }
    }

    // Sums the entries an activity spans (the three kings for DKs);
    // unranked entries count as nothing, and only a player unranked on
    // all of them reads as UNKNOWN.
    private static int count(HiscoreResult result, List<HiscoreSkill> skills)
    {
        int total = UNKNOWN;
        for (HiscoreSkill skill : skills)
        {
            Skill s = result.getSkill(skill);
            int kc = s == null ? UNKNOWN : s.getLevel();
            if (kc >= 0)
            {
                total = Math.max(total, 0) + kc;
            }
        }
        return total;
    }

    private static String key(String rsn, LfgActivity activity)
    {
        return LfgNames.normalize(rsn) + "|" + activity.getKey();
    }

    static List<HiscoreSkill> skillsFor(LfgActivity activity)
    {
        switch (activity)
        {
            case COX:
                return Collections.singletonList(HiscoreSkill.CHAMBERS_OF_XERIC);
            case TOB:
                return Collections.singletonList(HiscoreSkill.THEATRE_OF_BLOOD);
            case TOA:
                return Collections.singletonList(HiscoreSkill.TOMBS_OF_AMASCUT);
            case KREEARRA:
                return Collections.singletonList(HiscoreSkill.KREEARRA);
            case GRAARDOR:
                return Collections.singletonList(HiscoreSkill.GENERAL_GRAARDOR);
            case KRIL:
                return Collections.singletonList(HiscoreSkill.KRIL_TSUTSAROTH);
            case ZILYANA:
                return Collections.singletonList(HiscoreSkill.COMMANDER_ZILYANA);
            case NEX:
                return Collections.singletonList(HiscoreSkill.NEX);
            case NIGHTMARE:
                return Collections.singletonList(HiscoreSkill.NIGHTMARE);
            case CORP:
                return Collections.singletonList(HiscoreSkill.CORPOREAL_BEAST);
            case DKS:
                return Arrays.asList(HiscoreSkill.DAGANNOTH_PRIME, HiscoreSkill.DAGANNOTH_REX,
                    HiscoreSkill.DAGANNOTH_SUPREME);
            case HUEYCOATL:
                return Collections.singletonList(HiscoreSkill.THE_HUEYCOATL);
            case YAMA:
                return Collections.singletonList(HiscoreSkill.YAMA);
            case ROYAL_TITANS:
                return Collections.singletonList(HiscoreSkill.THE_ROYAL_TITANS);
            case ZALCANO:
                return Collections.singletonList(HiscoreSkill.ZALCANO);
            case GOTR:
                // Scored as rifts closed; the hiscores carry it in the same
                // field a boss kill count uses.
                return Collections.singletonList(HiscoreSkill.RIFTS_CLOSED);
            case WINTERTODT:
                return Collections.singletonList(HiscoreSkill.WINTERTODT);
            default:
                return Collections.emptyList();
        }
    }

    static HiscoreSkill hardSkillFor(LfgActivity activity)
    {
        switch (activity)
        {
            case COX:
                return HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE;
            case TOB:
                return HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE;
            case TOA:
                return HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT;
            default:
                return null;
        }
    }
}
