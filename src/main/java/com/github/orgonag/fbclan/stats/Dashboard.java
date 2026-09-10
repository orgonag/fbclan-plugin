package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/**
 * Read side of the clan dashboard: collection-log and combat-achievement
 * top-20s (member uploads), GP this week (logged drops), and the weekly
 * Wise Old Man podiums served from the wom_cache table (an hourly sync
 * script fills it; the plugin never calls WOM for this). Every source
 * fails soft independently, keeping last-known data.
 */
@Singleton
public class Dashboard
{
    @Value
    public static class Named
    {
        String rsn;
        double value;
    }

    @Value
    public static class ClEntry
    {
        String rsn;
        int obtained;
        int total;
    }

    @Value
    public static class CaEntry
    {
        String rsn;
        int points;
        String tier;
    }

    @Value
    public static class GpWeek
    {
        long totalGp;
        int dropCount;
        List<Named> top;
    }

    private final Supabase db;

    private volatile List<ClEntry> clBoard = Collections.emptyList();
    private volatile List<CaEntry> caBoard = Collections.emptyList();
    private volatile GpWeek gpWeek = new GpWeek(0, 0, Collections.emptyList());
    private volatile List<Named> xpWeek;   // null until the WOM cache is read
    private volatile List<Named> ehbWeek;
    private volatile String womSyncedAt = "";

    @Inject
    public Dashboard(Supabase db)
    {
        this.db = db;
    }

    public List<ClEntry> clBoard()
    {
        return clBoard;
    }

    public List<CaEntry> caBoard()
    {
        return caBoard;
    }

    public GpWeek gpWeek()
    {
        return gpWeek;
    }

    public List<Named> xpWeek()
    {
        return xpWeek;
    }

    public List<Named> ehbWeek()
    {
        return ehbWeek;
    }

    public String womSyncedAt()
    {
        return womSyncedAt;
    }

    // Executor. Each source fails soft independently, keeping its
    // previous value; an empty result is real and clears it.
    public boolean refresh()
    {
        JsonArray cl = db.getOrNull("cl_leaderboard", "select=rsn,cl_obtained,cl_total&order=cl_obtained.desc,rsn.asc");
        if (cl != null)
        {
            List<ClEntry> out = new ArrayList<>();
            for (JsonElement el : cl)
            {
                JsonObject r = el.getAsJsonObject();
                if (!Supabase.str(r, "rsn").isEmpty() && Supabase.has(r, "cl_obtained") && Supabase.has(r, "cl_total"))
                {
                    out.add(new ClEntry(Supabase.str(r, "rsn"), Supabase.intOr(r,"cl_obtained",0), Supabase.intOr(r,"cl_total",0)));
                }
            }
            clBoard = Collections.unmodifiableList(out);
        }
        JsonArray ca = db.getOrNull("ca_leaderboard", "select=rsn,ca_points,tier&order=ca_points.desc,rsn.asc");
        if (ca != null)
        {
            List<CaEntry> out = new ArrayList<>();
            for (JsonElement el : ca)
            {
                JsonObject r = el.getAsJsonObject();
                if (!Supabase.str(r, "rsn").isEmpty() && Supabase.has(r, "ca_points"))
                {
                    out.add(new CaEntry(Supabase.str(r, "rsn"), Supabase.intOr(r,"ca_points",0), Supabase.str(r, "tier")));
                }
            }
            caBoard = Collections.unmodifiableList(out);
        }
        JsonArray total = db.getOrNull("gp_week_total", "select=total_gp,drop_count");
        JsonArray top = db.getOrNull("gp_week_top", "select=rsn,gp&order=gp.desc,rsn.asc");
        if (total != null && top != null)
        {
            JsonObject t = total.size() > 0 ? total.get(0).getAsJsonObject() : new JsonObject();
            List<Named> names = new ArrayList<>();
            for (JsonElement el : top)
            {
                JsonObject r = el.getAsJsonObject();
                if (!Supabase.str(r, "rsn").isEmpty() && Supabase.has(r, "gp"))
                {
                    names.add(new Named(Supabase.str(r, "rsn"), Supabase.doubleOr(r,"gp",0)));
                }
            }
            gpWeek = new GpWeek(Supabase.longOr(t, "total_gp", 0), Supabase.intOr(t, "drop_count", 0),
                Collections.unmodifiableList(names));
        }
        JsonArray wom = db.getOrNull("wom_cache", "select=metric,payload,updated_at");
        if (wom != null)
        {
            applyWomCache(wom);
        }
        return cl != null && ca != null && total != null && top != null && wom != null;
    }

    // Each row's payload is the raw WOM response array for one metric.
    private void applyWomCache(JsonArray rows)
    {
        xpWeek = Collections.emptyList();
        ehbWeek = Collections.emptyList();
        womSyncedAt = "";
        String newest = "";
        for (JsonElement el : rows)
        {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            String metric = Supabase.str(row, "metric");
            if (metric.isEmpty() || !Supabase.has(row, "payload") || !row.get("payload").isJsonArray())
            {
                continue;
            }
            String updated = Supabase.str(row, "updated_at");
            if (updated.compareTo(newest) > 0)
            {
                newest = updated;
            }
            if ("gains_overall_week".equals(metric))
            {
                xpWeek = parseWom(row.getAsJsonArray("payload"), "gained");
            }
            else if ("gains_ehb_week".equals(metric))
            {
                ehbWeek = parseWom(row.getAsJsonArray("payload"), "gained");
            }
        }
        if (!newest.isEmpty())
        {
            womSyncedAt = newest;
        }
    }

    private static List<Named> parseWom(JsonArray rows, String valueKey)
    {
        List<Named> out = new ArrayList<>();
        for (JsonElement el : rows)
        {
            if (!el.isJsonObject()) continue;
            JsonObject row = el.getAsJsonObject();
            if (!Supabase.has(row, "player") || !Supabase.has(row, "data") || !row.get("player").isJsonObject() || !row.get("data").isJsonObject())
            {
                continue;
            }
            JsonObject player = row.getAsJsonObject("player");
            JsonObject data = row.getAsJsonObject("data");
            if (Supabase.has(player, "displayName") && Supabase.has(data, valueKey))
            {
                out.add(new Named(Supabase.str(player,"displayName"), Supabase.doubleOr(data,valueKey,0)));
            }
        }
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------ formatting

    // 9_800_000 -> "9.8M", 14_200_000 -> "14M".
    public static String shortNumber(long n)
    {
        if (n >= 1_000_000_000L)
        {
            return unit(n, 1_000_000_000L) + "B";
        }
        if (n >= 1_000_000L)
        {
            return unit(n, 1_000_000L) + "M";
        }
        if (n >= 1_000L)
        {
            return unit(n, 1_000L) + "K";
        }
        return Long.toString(n);
    }

    public static String oneDecimal(double d)
    {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private static String unit(long n, long divisor)
    {
        double d = (double) n / divisor;
        if (d >= 10)
        {
            return Long.toString(n / divisor);
        }
        String s = String.format(Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
