package com.github.orgonag.fbclan.wom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static WOM parsing helpers: boss metric slugs, display names, and parsers for
 * raw WOM response arrays served from the wom_cache table (the plugin no
 * longer calls WOM directly — an hourly Apps Script fills the cache).
 */
public final class WomStatsParser
{
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

    private WomStatsParser()
    {
    }

    public static List<WomEntry> parseGains(JsonArray rows)
    {
        return parse(rows, "gained");
    }

    public static List<WomEntry> parseHiscores(JsonArray rows)
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
}
