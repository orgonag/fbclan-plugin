package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.github.orgonag.fbclan.wom.WomEntry;
import com.github.orgonag.fbclan.wom.WomStatsClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private volatile List<ClEntry> clBoard = Collections.emptyList();
    private volatile List<CaEntry> caBoard = Collections.emptyList();
    private volatile GpWeek gpWeek = new GpWeek(0, 0, Collections.emptyList());
    private volatile List<WomEntry> xpWeek;  // null = WOM never reached
    private volatile List<WomEntry> ehbWeek; // null = WOM never reached
    private volatile Map<String, List<WomEntry>> kcBoards = Collections.emptyMap();
    private volatile String womSyncedAt = ""; // newest wom_cache updated_at

    public DashboardService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
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

    public Map<String, List<WomEntry>> getKcBoards()
    {
        return kcBoards;
    }

    public String getWomSyncedAt()
    {
        return womSyncedAt;
    }

    // Runs on the executor. Each source fails soft and independently.
    public void refresh()
    {
        JsonArray cl = SupabaseClient.tryGet(httpClient, "cl_leaderboard",
            "select=rsn,cl_obtained,cl_total", "collection log board");
        if (cl != null)
        {
            clBoard = parseCl(cl);
        }
        JsonArray ca = SupabaseClient.tryGet(httpClient, "ca_leaderboard",
            "select=rsn,ca_points,tier", "combat achievements board");
        if (ca != null)
        {
            caBoard = parseCa(ca);
        }
        JsonArray gpTotal = SupabaseClient.tryGet(httpClient, "gp_week_total",
            "select=total_gp,drop_count", "GP week total");
        JsonArray gpTop = SupabaseClient.tryGet(httpClient, "gp_week_top",
            "select=rsn,gp", "GP week top");
        if (gpTotal != null && gpTop != null)
        {
            gpWeek = parseGpWeek(gpTotal, gpTop);
        }
        JsonArray wom = SupabaseClient.tryGet(httpClient, "wom_cache",
            "select=metric,payload,updated_at", "WOM cache");
        if (wom != null)
        {
            applyWomCache(wom);
        }
    }

    // payload is the RAW WOM response array, so the WomStatsClient
    // parsers apply unchanged. Unknown metrics are ignored (forward
    // compatible). An absent/empty table leaves everything null/empty →
    // the panel shows "waiting for WOM sync".
    void applyWomCache(JsonArray rows)
    {
        Map<String, List<WomEntry>> kc = new HashMap<>();
        List<WomEntry> xp = null;
        List<WomEntry> ehb = null;
        String newest = "";
        for (int i = 0; i < rows.size(); i++)
        {
            JsonObject row = rows.get(i).getAsJsonObject();
            String metric = str(row, "metric");
            if (metric.isEmpty() || !row.has("payload") || !row.get("payload").isJsonArray())
            {
                continue;
            }
            JsonArray payload = row.getAsJsonArray("payload");
            String updated = str(row, "updated_at");
            if (updated.compareTo(newest) > 0)
            {
                newest = updated;
            }
            if ("gains_overall_week".equals(metric))
            {
                xp = WomStatsClient.parseGains(payload);
            }
            else if ("gains_ehb_week".equals(metric))
            {
                ehb = WomStatsClient.parseGains(payload);
            }
            else if (metric.startsWith("kc_"))
            {
                kc.put(metric.substring(3), WomStatsClient.parseHiscores(payload));
            }
        }
        if (xp != null)
        {
            xpWeek = xp;
        }
        if (ehb != null)
        {
            ehbWeek = ehb;
        }
        if (!kc.isEmpty())
        {
            kcBoards = Collections.unmodifiableMap(kc);
        }
        if (!newest.isEmpty())
        {
            womSyncedAt = newest;
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
