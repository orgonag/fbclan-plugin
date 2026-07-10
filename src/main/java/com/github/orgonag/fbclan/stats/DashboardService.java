package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.github.orgonag.fbclan.wom.WomEntry;
import com.github.orgonag.fbclan.wom.WomStatsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Read side of the dashboard. Each source fails soft and independently:
 * a WOM outage leaves the Supabase boards intact and vice versa. WOM
 * results are null-until-first-success (the panel renders an
 * unreachable notice); Supabase boards keep last-known data.
 */
@Slf4j
public class DashboardService
{
    private final OkHttpClient httpClient;
    private final WomStatsClient womStatsClient;

    private volatile List<ClEntry> clBoard = Collections.emptyList();
    private volatile List<CaEntry> caBoard = Collections.emptyList();
    private volatile GpWeek gpWeek = new GpWeek(0, 0, Collections.emptyList());
    private volatile List<WomEntry> xpWeek;  // null = WOM never reached
    private volatile List<WomEntry> ehbWeek; // null = WOM never reached

    public DashboardService(OkHttpClient httpClient, WomStatsClient womStatsClient)
    {
        this.httpClient = httpClient;
        this.womStatsClient = womStatsClient;
    }

    public List<ClEntry> getClBoard()
    {
        return clBoard;
    }

    public List<CaEntry> getCaBoard()
    {
        return caBoard;
    }

    public GpWeek getGpWeek()
    {
        return gpWeek;
    }

    public List<WomEntry> getXpWeek()
    {
        return xpWeek;
    }

    public List<WomEntry> getEhbWeek()
    {
        return ehbWeek;
    }

    // Runs on the executor. WomStatsClient handles its own 10-min cache,
    // so calling this on every tab open is safe.
    public void refresh()
    {
        try
        {
            clBoard = parseCl(SupabaseClient.get(httpClient, "cl_leaderboard",
                "select=rsn,cl_obtained,cl_total"));
            caBoard = parseCa(SupabaseClient.get(httpClient, "ca_leaderboard",
                "select=rsn,ca_points,tier"));
            gpWeek = parseGpWeek(
                SupabaseClient.get(httpClient, "gp_week_total", "select=total_gp,drop_count"),
                SupabaseClient.get(httpClient, "gp_week_top", "select=rsn,gp"));
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch dashboard boards", e);
        }

        List<WomEntry> xp = womStatsClient.fetchGains("overall");
        if (xp != null)
        {
            xpWeek = xp;
        }
        List<WomEntry> ehb = womStatsClient.fetchGains("ehb");
        if (ehb != null)
        {
            ehbWeek = ehb;
        }
    }

    static List<ClEntry> parseCl(JsonArray rows)
    {
        List<ClEntry> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++)
        {
            JsonObject row = rows.get(i).getAsJsonObject();
            String rsn = str(row, "rsn");
            if (rsn.isEmpty() || isNull(row, "cl_obtained") || isNull(row, "cl_total"))
            {
                continue;
            }
            result.add(new ClEntry(rsn, row.get("cl_obtained").getAsInt(), row.get("cl_total").getAsInt()));
        }
        return Collections.unmodifiableList(result);
    }

    static List<CaEntry> parseCa(JsonArray rows)
    {
        List<CaEntry> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++)
        {
            JsonObject row = rows.get(i).getAsJsonObject();
            String rsn = str(row, "rsn");
            if (rsn.isEmpty() || isNull(row, "ca_points"))
            {
                continue;
            }
            result.add(new CaEntry(rsn, row.get("ca_points").getAsInt(), str(row, "tier")));
        }
        return Collections.unmodifiableList(result);
    }

    static GpWeek parseGpWeek(JsonArray totalRows, JsonArray topRows)
    {
        long total = 0;
        int count = 0;
        if (totalRows.size() > 0)
        {
            JsonObject row = totalRows.get(0).getAsJsonObject();
            total = isNull(row, "total_gp") ? 0 : row.get("total_gp").getAsLong();
            count = isNull(row, "drop_count") ? 0 : row.get("drop_count").getAsInt();
        }
        List<WomEntry> top = new ArrayList<>();
        for (int i = 0; i < topRows.size(); i++)
        {
            JsonObject row = topRows.get(i).getAsJsonObject();
            String rsn = str(row, "rsn");
            if (rsn.isEmpty() || isNull(row, "gp"))
            {
                continue;
            }
            top.add(new WomEntry(rsn, row.get("gp").getAsDouble()));
        }
        return new GpWeek(total, count, Collections.unmodifiableList(top));
    }

    private static boolean isNull(JsonObject row, String key)
    {
        return !row.has(key) || row.get(key).isJsonNull();
    }

    private static String str(JsonObject row, String key)
    {
        return isNull(row, key) ? "" : row.get(key).getAsString().trim();
    }
}
