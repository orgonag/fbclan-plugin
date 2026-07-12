package com.github.orgonag.fbclan.chat;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

/**
 * Maps clan members' RSNs to their combat-achievement tier, read from
 * the same read-only ca_leaderboard view the dashboard uses (rsn and
 * tier only). Only members who upload stats through the plugin appear.
 * Refresh runs on the executor; lookups happen on the client thread,
 * so the map is swapped whole through a volatile field.
 */
public class CaBadgeService
{
    private final OkHttpClient httpClient;

    private volatile Map<String, String> tierByRsn = Collections.emptyMap();

    public CaBadgeService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    // Runs on the executor.
    public void refresh()
    {
        JsonArray rows = SupabaseClient.tryGet(httpClient, "ca_leaderboard",
            "select=rsn,tier", "CA chat badges");
        if (rows != null)
        {
            tierByRsn = parseTiers(rows);
        }
    }

    /**
     * Tier for a chat sender name, or null. Chat names carry img tags
     * (clan rank icons) and non-breaking spaces; Text.standardize strips
     * both and lowercases, matching how the stored RSNs are keyed.
     */
    public String tierFor(String chatName)
    {
        if (chatName == null || chatName.isEmpty())
        {
            return null;
        }
        return tierByRsn.get(Text.standardize(chatName));
    }

    static Map<String, String> parseTiers(JsonArray rows)
    {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < rows.size(); i++)
        {
            JsonObject row = rows.get(i).getAsJsonObject();
            if (isNull(row, "rsn") || isNull(row, "tier"))
            {
                continue;
            }
            String rsn = Text.standardize(row.get("rsn").getAsString());
            String tier = row.get("tier").getAsString().trim();
            if (!rsn.isEmpty() && !tier.isEmpty())
            {
                result.put(rsn, tier);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean isNull(JsonObject row, String key)
    {
        return !row.has(key) || row.get(key).isJsonNull();
    }
}
