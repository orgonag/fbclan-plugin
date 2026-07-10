package com.github.orgonag.fbclan.pb;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Read side of the PB leaderboard: fetches the pb_leaderboard (top 3 per
 * boss, server-ranked) and recent_clan_bests (live-sourced current #1s,
 * newest first) views. Same fail-soft caching as the other services.
 */
@Slf4j
public class LeaderboardService
{
    private final OkHttpClient httpClient;

    private volatile List<PbEntry> leaderboard = Collections.emptyList();
    private volatile List<PbEntry> recentBests = Collections.emptyList();

    public LeaderboardService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public List<PbEntry> getLeaderboard()
    {
        return leaderboard;
    }

    public List<PbEntry> getRecentBests()
    {
        return recentBests;
    }

    public void refresh()
    {
        try
        {
            JsonArray board = SupabaseClient.get(httpClient, "pb_leaderboard",
                "select=rsn,boss_key,seconds,achieved_at,rank&order=boss_key.asc,rank.asc");
            leaderboard = parseEntries(board);
            JsonArray recent = SupabaseClient.get(httpClient, "recent_clan_bests",
                "select=rsn,boss_key,seconds,achieved_at");
            recentBests = parseEntries(recent);
            log.debug("Loaded {} leaderboard row(s), {} recent best(s)",
                leaderboard.size(), recentBests.size());
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch PB leaderboard", e);
        }
    }

    static List<PbEntry> parseEntries(JsonArray rows)
    {
        List<PbEntry> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++)
        {
            JsonObject row = rows.get(i).getAsJsonObject();
            String rsn = stringField(row, "rsn");
            String bossKey = stringField(row, "boss_key");
            if (rsn.isEmpty() || bossKey.isEmpty()
                || !row.has("seconds") || row.get("seconds").isJsonNull())
            {
                continue;
            }
            double seconds = row.get("seconds").getAsDouble();
            String achievedAt = stringField(row, "achieved_at");
            int rank = row.has("rank") && !row.get("rank").isJsonNull()
                ? row.get("rank").getAsInt() : 1;
            result.add(new PbEntry(rsn, bossKey, seconds, achievedAt, rank));
        }
        return Collections.unmodifiableList(result);
    }

    private static String stringField(JsonObject row, String key)
    {
        if (!row.has(key) || row.get(key).isJsonNull())
        {
            return "";
        }
        return row.get(key).getAsString().trim();
    }
}
