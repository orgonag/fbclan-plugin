package com.github.orgonag.fbclan.announcements;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Long-form clan announcements shown in the panel's Announcements tab.
 * Rows live in the Supabase announcements table, kept in sync from the
 * clan's Google Sheet by the sheet-sync Apps Script; the plugin only
 * ever reads them. Ordering (newest first, sheet order for ties) comes
 * from the query, not the client.
 */
@Slf4j
public class AnnouncementsService
{
    // Defensive caps on remote data. The body cap is generous — these are
    // long-form posts — but bounds panel memory/layout work.
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_BODY_LENGTH = 10_000;

    private final OkHttpClient httpClient;

    // Written on the executor thread, read on the EDT — hence volatile.
    // Always an unmodifiable list, never null.
    private volatile List<Announcement> announcements = Collections.emptyList();

    public AnnouncementsService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public List<Announcement> getAnnouncements()
    {
        return announcements;
    }

    // Network failure (IOException) keeps the previous list (empty on first
    // run); HTTP errors surface from SupabaseClient.get as an empty array
    // and clear the list. RuntimeException is caught so a malformed 2xx
    // body can't vanish into the executor's uninspected Future.
    public void refresh()
    {
        try
        {
            JsonArray rows = SupabaseClient.get(httpClient, "announcements",
                "select=posted_at,title,body&order=posted_at.desc,sort_order.asc");
            announcements = parseAnnouncements(rows);
            log.debug("Loaded {} announcement(s)", announcements.size());
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch announcements", e);
        }
    }

    // No tag stripping here (unlike WelcomeMessageService): the panel
    // renders plain Swing text components, not chat markup or HTML, so
    // remote text cannot inject anything. Newlines are deliberately kept.
    static List<Announcement> parseAnnouncements(JsonArray rows)
    {
        List<Announcement> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++)
        {
            JsonObject row = rows.get(i).getAsJsonObject();
            String date = stringField(row, "posted_at");
            String title = cap(stringField(row, "title"), MAX_TITLE_LENGTH);
            String body = cap(stringField(row, "body"), MAX_BODY_LENGTH);
            if (title.isEmpty() && body.isEmpty())
            {
                continue;
            }
            result.add(new Announcement(date, title, body));
        }
        return Collections.unmodifiableList(result);
    }

    private static String stringField(JsonObject row, String key)
    {
        if (!row.has(key) || row.get(key).isJsonNull())
        {
            return "";
        }
        return row.get(key).getAsString().trim();
    }

    private static String cap(String s, int max)
    {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
