package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
public class LfgService
{
    private final OkHttpClient httpClient;

    public LfgService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public boolean setStatus(String rsn, LfgActivity activity)
    {
        JsonObject data = new JsonObject();
        data.addProperty("rsn", rsn);
        data.addProperty("activity", activity.getKey());

        try
        {
            return SupabaseClient.upsert(httpClient, "lfg_entries", data, "rsn");
        }
        catch (IOException e)
        {
            log.warn("Failed to set LFG status", e);
            return false;
        }
    }

    public boolean removeStatus(String rsn)
    {
        try
        {
            String encodedRsn = URLEncoder.encode(rsn, StandardCharsets.UTF_8);
            return SupabaseClient.delete(httpClient, "lfg_entries", "rsn=eq." + encodedRsn);
        }
        catch (IOException e)
        {
            log.warn("Failed to remove LFG status", e);
            return false;
        }
    }

    public List<LfgEntry> getActiveEntries()
    {
        List<LfgEntry> entries = new ArrayList<>();
        try
        {
            JsonArray rows = SupabaseClient.get(httpClient, "lfg_entries",
                "select=rsn,activity,updated_at&order=updated_at.desc");

            for (JsonElement element : rows)
            {
                JsonObject row = element.getAsJsonObject();
                LfgEntry entry = LfgEntry.fromJson(
                    row.get("rsn").getAsString(),
                    row.get("activity").getAsString(),
                    row.get("updated_at").getAsString()
                );
                if (entry != null)
                {
                    entries.add(entry);
                }
            }
        }
        catch (IOException e)
        {
            log.warn("Failed to fetch LFG entries", e);
        }
        return entries;
    }
}
