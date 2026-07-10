package com.github.orgonag.fbclan.wom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Read-only Wise Old Man v2 group queries for the dashboard: weekly gains
 * (XP/EHB podiums) and per-boss group hiscores (Kill Counts section,
 * fetched lazily per boss). Everything is cached for CACHE_TTL_MS —
 * weekly numbers move slowly and WOM allows 20 requests/minute
 * unauthenticated, so tab-hopping must not re-fetch.
 */
@Slf4j
public class WomStatsClient
{
    static final long CACHE_TTL_MS = 10 * 60 * 1000;
    private static final String BASE = "https://api.wiseoldman.net/v2/groups/1055";
    // WOM asks for an identifying User-Agent (no UA risks an IP ban).
    private static final String USER_AGENT = "fbclan-plugin (github.com/orgonag/fbclan-plugin)";

    // Complete WOM boss metric slug list (verified against
    // wise-old-man/server/src/types/metric.enum.ts, 2026-07-10),
    // alphabetical. New bosses arrive with plugin updates.
    public static final List<String> BOSS_SLUGS = Collections.unmodifiableList(Arrays.asList(
        "abyssal_sire", "alchemical_hydra", "amoxliatl", "araxxor", "artio",
        "barrows_chests", "brutus", "bryophyta", "callisto", "calvarion",
        "cerberus", "chambers_of_xeric", "chambers_of_xeric_challenge_mode",
        "chaos_elemental", "chaos_fanatic", "commander_zilyana",
        "corporeal_beast", "crazy_archaeologist", "dagannoth_prime",
        "dagannoth_rex", "dagannoth_supreme", "deranged_archaeologist",
        "doom_of_mokhaiotl", "duke_sucellus", "general_graardor",
        "giant_mole", "grotesque_guardians", "hespori", "kalphite_queen",
        "king_black_dragon", "kraken", "kreearra", "kril_tsutsaroth",
        "lunar_chests", "maggot_king", "mimic", "nex", "nightmare",
        "obor", "phantom_muspah", "phosanis_nightmare", "sarachnis",
        "scorpia", "scurrius", "shellbane_gryphon", "skotizo",
        "sol_heredit", "spindel", "tempoross", "the_corrupted_gauntlet",
        "the_gauntlet", "the_hueycoatl", "the_leviathan",
        "the_royal_titans", "the_whisperer", "theatre_of_blood",
        "theatre_of_blood_hard_mode", "thermonuclear_smoke_devil",
        "tombs_of_amascut", "tombs_of_amascut_expert", "tzkal_zuk",
        "tztok_jad", "vardorvis", "venenatis", "vetion", "vorkath",
        "wintertodt", "yama", "zalcano", "zulrah"
    ));

    // Slugs whose auto-derived name (underscores -> spaces, Title Case)
    // is wrong; everything else derives automatically.
    private static final Map<String, String> DISPLAY_OVERRIDES = new HashMap<>();
    static
    {
        DISPLAY_OVERRIDES.put("kreearra", "Kree'arra");
        DISPLAY_OVERRIDES.put("kril_tsutsaroth", "K'ril Tsutsaroth");
        DISPLAY_OVERRIDES.put("calvarion", "Calvar'ion");
        DISPLAY_OVERRIDES.put("vetion", "Vet'ion");
        DISPLAY_OVERRIDES.put("tzkal_zuk", "TzKal-Zuk");
        DISPLAY_OVERRIDES.put("tztok_jad", "TzTok-Jad");
        DISPLAY_OVERRIDES.put("phosanis_nightmare", "Phosani's Nightmare");
    }

    private final OkHttpClient httpClient;
    private final LongSupplier clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry
    {
        final List<WomEntry> value;
        final long at;

        CacheEntry(List<WomEntry> value, long at)
        {
            this.value = value;
            this.at = at;
        }
    }

    public WomStatsClient(OkHttpClient httpClient)
    {
        this(httpClient, System::currentTimeMillis);
    }

    WomStatsClient(OkHttpClient httpClient, LongSupplier clock)
    {
        this.httpClient = httpClient;
        this.clock = clock;
    }

    // Weekly gains podium (metric = "overall" for XP, "ehb"). Runs on the
    // executor. Returns cached/last-known data; null means "never fetched
    // successfully" so the panel can show the WOM-unreachable state.
    public List<WomEntry> fetchGains(String metric)
    {
        String url = BASE + "/gained?metric=" + metric + "&period=week&limit=3";
        return fetch(url, true);
    }

    // Top-3 KC for one boss (Kill Counts section, lazy per expand).
    public List<WomEntry> fetchBossHiscores(String bossSlug)
    {
        String url = BASE + "/hiscores?metric=" + bossSlug + "&limit=3";
        return fetch(url, false);
    }

    private List<WomEntry> fetch(String url, boolean gains)
    {
        List<WomEntry> cached = cacheGet(url);
        if (cached != null)
        {
            return cached;
        }
        try
        {
            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build();
            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful() || response.body() == null)
                {
                    log.warn("WOM GET failed: {} {}", response.code(), url);
                    return null;
                }
                JsonArray rows = new JsonParser().parse(response.body().string()).getAsJsonArray();
                List<WomEntry> parsed = gains ? parseGains(rows) : parseHiscores(rows);
                cachePut(url, parsed);
                return parsed;
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("WOM fetch failed: {}", url, e);
            return null;
        }
    }

    static List<WomEntry> parseGains(JsonArray rows)
    {
        return parse(rows, "gained");
    }

    static List<WomEntry> parseHiscores(JsonArray rows)
    {
        return parse(rows, "kills");
    }

    private static List<WomEntry> parse(JsonArray rows, String valueKey)
    {
        List<WomEntry> result = new ArrayList<>();
        for (JsonElement el : rows)
        {
            JsonObject row = el.getAsJsonObject();
            if (!row.has("player") || row.get("player").isJsonNull()
                || !row.has("data") || row.get("data").isJsonNull())
            {
                continue;
            }
            JsonObject player = row.getAsJsonObject("player");
            JsonObject data = row.getAsJsonObject("data");
            if (!player.has("displayName") || player.get("displayName").isJsonNull()
                || !data.has(valueKey) || data.get(valueKey).isJsonNull())
            {
                continue;
            }
            result.add(new WomEntry(player.get("displayName").getAsString(),
                data.get(valueKey).getAsDouble()));
        }
        return Collections.unmodifiableList(result);
    }

    public static String bossDisplayName(String slug)
    {
        String override = DISPLAY_OVERRIDES.get(slug);
        if (override != null)
        {
            return override;
        }
        StringBuilder sb = new StringBuilder();
        for (String word : slug.split("_"))
        {
            if (sb.length() > 0)
            {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)))
              .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    List<WomEntry> cacheGet(String key)
    {
        CacheEntry e = cache.get(key);
        if (e == null || clock.getAsLong() - e.at > CACHE_TTL_MS)
        {
            return null;
        }
        return e.value;
    }

    void cachePut(String key, List<WomEntry> value)
    {
        cache.put(key, new CacheEntry(value, clock.getAsLong()));
    }
}
