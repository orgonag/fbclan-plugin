package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
public class SupabaseDropService
{
    private final OkHttpClient httpClient;

    public SupabaseDropService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public boolean logDrop(String rsn, String npcName, String itemName, int itemId, long geValue, int quantity)
    {
        JsonObject data = new JsonObject();
        data.addProperty("rsn", rsn);
        data.addProperty("npc_name", npcName);
        data.addProperty("item_name", itemName);
        data.addProperty("item_id", itemId);
        data.addProperty("ge_value", geValue);
        data.addProperty("quantity", quantity);

        try
        {
            return SupabaseClient.insert(httpClient, "drops", data);
        }
        catch (IOException e)
        {
            log.warn("Failed to log drop to Supabase", e);
            return false;
        }
    }

    public JsonArray getRecentDrops(int limit)
    {
        try
        {
            return SupabaseClient.get(httpClient, "drops",
                "select=*&order=created_at.desc&limit=" + limit);
        }
        catch (IOException e)
        {
            log.warn("Failed to fetch drops from Supabase", e);
            return new JsonArray();
        }
    }
}
