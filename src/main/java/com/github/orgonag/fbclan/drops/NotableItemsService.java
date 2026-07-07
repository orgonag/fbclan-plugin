package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Clan-curated list of "notable" item names — drops worth logging even
 * though they have no GE value (untradeables like Araxyte fang). The list
 * lives in a Supabase table kept in sync from a Google Sheet by a Google
 * Apps Script (maintained outside this repo); the plugin only ever reads it.
 */
@Slf4j
public class NotableItemsService
{
    private final OkHttpClient httpClient;

    // Written once by the startup fetch on the executor thread, read on
    // the client thread by handleLoot — hence volatile + immutable set.
    private volatile Set<String> notableNames = Collections.emptySet();

    public NotableItemsService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public Set<String> getNotableNames()
    {
        return notableNames;
    }

    // Fetched once at plugin startup: the list changes rarely (curated by
    // hand, synced daily), so a session-long cache is enough. Two failure
    // paths: a network-level IOException keeps the previous set, while an
    // HTTP error surfaces from SupabaseClient.get as an empty array and
    // resets the cache to empty. With a single startup fetch both amount to
    // the same thing — threshold-only behavior, identical to before this
    // feature existed.
    public void refresh()
    {
        try
        {
            JsonArray rows = SupabaseClient.get(httpClient, "notable_items", "select=name");
            notableNames = parseNames(rows);
            log.info("Loaded {} notable item names", notableNames.size());
        }
        // RuntimeException too: a malformed 2xx body (JsonSyntaxException,
        // IllegalStateException from getAsJsonObject) would otherwise escape
        // into the executor's Future and fail with zero log output.
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch notable items; keeping previous list", e);
        }
    }

    static Set<String> parseNames(JsonArray rows)
    {
        Set<String> names = new HashSet<>();
        for (JsonElement row : rows)
        {
            JsonObject obj = row.getAsJsonObject();
            if (!obj.has("name") || obj.get("name").isJsonNull())
            {
                continue;
            }
            String normalized = DropTrackingService.normalizeItemName(obj.get("name").getAsString());
            if (!normalized.isEmpty())
            {
                names.add(normalized);
            }
        }
        return Collections.unmodifiableSet(names);
    }
}
