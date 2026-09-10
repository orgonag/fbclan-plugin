package com.github.orgonag.fbclan.pbs;

import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/**
 * Read side of the PB leaderboard: the pb_leaderboard view (top 3 per
 * boss, server-ranked) and recent_clan_bests (live-sourced current #1s,
 * newest first). Each fetch fails soft independently, keeping last data.
 */
@Singleton
public class Leaderboards
{
    @Value
    public static class Entry
    {
        String rsn;
        String bossKey;
        double seconds;
        String achievedAt; // ISO timestamp, may be blank
        int rank;          // 1..3 on the board, 1 in the feed
    }

    private final Supabase db;
    private volatile List<Entry> board = Collections.emptyList();
    private volatile List<Entry> recent = Collections.emptyList();

    @Inject
    public Leaderboards(Supabase db)
    {
        this.db = db;
    }

    public List<Entry> board()
    {
        return board;
    }

    public List<Entry> recent()
    {
        return recent;
    }

    // Executor. A failed fetch keeps the previous board.
    public boolean refresh()
    {
        boolean complete = true;
        JsonArray rows = db.getOrNull("pb_leaderboard", "select=rsn,boss_key,seconds,achieved_at,rank&order=boss_key.asc,rank.asc,rsn.asc");
        if (rows != null)
        {
            board = parse(rows);
        }
        else complete = false;
        rows = db.getOrNull("recent_clan_bests", "select=rsn,boss_key,seconds,achieved_at&order=achieved_at.desc");
        if (rows != null)
        {
            recent = parse(rows);
        }
        else complete = false;
        return complete;
    }

    private static List<Entry> parse(JsonArray rows)
    {
        List<Entry> out = new ArrayList<>();
        for (JsonElement el : rows)
        {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            String rsn = Supabase.str(row, "rsn");
            String boss = Supabase.str(row, "boss_key");
            if (rsn.isEmpty() || boss.isEmpty() || !Supabase.has(row, "seconds"))
            {
                continue;
            }
            double seconds = Supabase.doubleOr(row, "seconds", -1);
            if (seconds <= 0 || seconds >= 86400) continue;
            out.add(new Entry(rsn, boss, seconds,
                Supabase.str(row, "achieved_at"), Supabase.intOr(row, "rank", 1)));
        }
        return Collections.unmodifiableList(out);
    }
}
