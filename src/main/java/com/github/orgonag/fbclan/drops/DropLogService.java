package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

@Slf4j
public class DropLogService
{
    private final OkHttpClient httpClient;

    public DropLogService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    // screenshotUrl is null unless the user opted into drop screenshots and
    // the upload succeeded; omitted from the payload when null so the column
    // stays NULL for screenshot-less drops.
    public boolean logDrop(String rsn, String npcName, String itemName, int itemId, long geValue, int quantity, String screenshotUrl)
    {
        JsonObject data = new JsonObject();
        data.addProperty("rsn", rsn);
        data.addProperty("npc_name", npcName);
        data.addProperty("item_name", itemName);
        data.addProperty("item_id", itemId);
        data.addProperty("ge_value", geValue);
        data.addProperty("quantity", quantity);
        if (screenshotUrl != null)
        {
            data.addProperty("screenshot_url", screenshotUrl);
        }

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

    // Explicit column list rather than select=* so adding a column to the
    // drops table (e.g. a future moderation flag) doesn't silently start
    // shipping it to every viewer's client.
    private static final String DROPS_COLUMNS =
        "rsn,npc_name,item_name,item_id,ge_value,quantity,created_at,screenshot_url";

    public JsonArray getRecentDrops(int limit)
    {
        try
        {
            return SupabaseClient.get(httpClient, "drops",
                "select=" + DROPS_COLUMNS + "&order=created_at.desc&limit=" + limit);
        }
        catch (IOException e)
        {
            log.warn("Failed to fetch drops from Supabase", e);
            return new JsonArray();
        }
    }
}
