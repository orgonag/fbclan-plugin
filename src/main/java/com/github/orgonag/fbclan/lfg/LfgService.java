package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        return setStatus(rsn, activity, null, null);
    }

    // partyId / partySize are optional. When the caller is in a Party plugin
    // party, attaching them lets viewers cluster LFG rows by party. Both are
    // omitted from the payload when null so solo entries explicitly write
    // NULL into the columns rather than empty strings / zero.
    public boolean setStatus(String rsn, LfgActivity activity, String partyId, Integer partySize)
    {
        JsonObject data = new JsonObject();
        data.addProperty("rsn", rsn);
        data.addProperty("activity", activity.getKey());
        data.addProperty("updated_at", Instant.now().toString());
        if (partyId != null)
        {
            data.addProperty("party_id", partyId);
        }
        if (partySize != null)
        {
            data.addProperty("party_size", partySize);
        }

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
                "select=rsn,activity,updated_at,party_id,party_size&order=updated_at.desc");

            for (JsonElement element : rows)
            {
                LfgEntry entry = LfgEntry.fromRow(element.getAsJsonObject());
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
