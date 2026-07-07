package com.github.orgonag.fbclan.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Clan-curated welcome message ("Sign up for bingo") shown once per session
 * in verified members' chatboxes. The text lives in a single-row Supabase
 * table kept in sync from a Google Sheet by a Google Apps Script (maintained
 * outside this repo); the plugin only ever reads it.
 */
@Slf4j
public class WelcomeMessageService
{
    // Hard cap on displayed length; the chatbox is not the place for essays.
    static final int MAX_LENGTH = 200;

    private final OkHttpClient httpClient;

    // Written once by the startup fetch on the executor thread, read when
    // verification completes — hence volatile. Empty string = no message.
    private volatile String message = "";

    public WelcomeMessageService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public String getMessage()
    {
        return message;
    }

    // Fetched once at plugin startup. Network failure (IOException) keeps
    // the previous value (empty on first run); HTTP errors surface from
    // SupabaseClient.get as an empty array and clear the message. Either
    // way: no message this session, nothing else affected.
    // RuntimeException is caught so a malformed 2xx body can't vanish into
    // the executor's uninspected Future.
    public void refresh()
    {
        try
        {
            JsonArray rows = SupabaseClient.get(httpClient, "welcome_message", "select=message&id=eq.1");
            message = parseMessage(rows);
            log.debug("Welcome message loaded ({} chars)", message.length());
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch welcome message", e);
        }
    }

    static String parseMessage(JsonArray rows)
    {
        if (rows.size() == 0)
        {
            return "";
        }
        JsonObject row = rows.get(0).getAsJsonObject();
        if (!row.has("message") || row.get("message").isJsonNull())
        {
            return "";
        }
        return sanitize(row.get("message").getAsString());
    }

    // Remote-controlled text printed into the chatbox: strip anything that
    // could be interpreted as chat markup (<col=...>, <img=...>, stray
    // angle brackets), collapse whitespace, cap the length.
    static String sanitize(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String s = raw.replaceAll("<[^>]*>", "")
            .replace("<", "")
            .replace(">", "")
            .replaceAll("\\s+", " ")
            .trim();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s;
    }
}
