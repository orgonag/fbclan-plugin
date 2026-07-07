package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
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
    // Hard cap on the free-text note, enforced here, in the panel's input
    // field, and by a CHECK constraint in the database (the anon key is
    // public, so the client-side cap alone is not sufficient).
    public static final int MAX_NOTE_LENGTH = 60;

    private final OkHttpClient httpClient;

    public LfgService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    // partyId / partySize are optional. When the caller is in a Party plugin
    // party, attaching them lets viewers cluster LFG rows by party. Both are
    // omitted from the payload when null so solo entries explicitly write
    // NULL into the columns rather than empty strings / zero.
    // The note is always written — an empty note writes NULL so a stale note
    // from a previous status doesn't linger on the new one.
    public boolean setStatus(String rsn, LfgActivity activity, String partyId, Integer partySize, String note)
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
        String sanitizedNote = sanitizeNote(note);
        if (sanitizedNote == null)
        {
            data.add("note", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("note", sanitizedNote);
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

    // Updates only party_id / party_size for an existing LFG row, leaving
    // activity and updated_at untouched. Called from background party-sync
    // triggers (PartyChanged / UserJoin / UserPart) so a member joining or
    // leaving the user's party doesn't reset the row's "X min ago" timer.
    // Null partyId / partySize clear the columns (user left their party).
    public boolean updateParty(String rsn, String partyId, Integer partySize)
    {
        JsonObject data = new JsonObject();
        if (partyId != null)
        {
            data.addProperty("party_id", partyId);
        }
        else
        {
            data.add("party_id", JsonNull.INSTANCE);
        }
        if (partySize != null)
        {
            data.addProperty("party_size", partySize);
        }
        else
        {
            data.add("party_size", JsonNull.INSTANCE);
        }

        try
        {
            String encodedRsn = URLEncoder.encode(rsn, StandardCharsets.UTF_8);
            return SupabaseClient.update(httpClient, "lfg_entries",
                "rsn=eq." + encodedRsn, data);
        }
        catch (IOException e)
        {
            log.warn("Failed to update LFG party info", e);
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
                "select=rsn,activity,updated_at,party_id,party_size,note&order=updated_at.desc");

            for (JsonElement element : rows)
            {
                LfgEntry entry = LfgEntry.fromRow(element.getAsJsonObject());
                if (entry != null)
                {
                    entries.add(entry);
                }
            }
        }
        // RuntimeException too: a malformed response (e.g. an error object
        // where an array was expected) would otherwise escape into the
        // executor and silently stop the panel from refreshing.
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch LFG entries", e);
        }
        return entries;
    }

    // Trims, collapses control characters, and caps the note. Returns null
    // for empty/blank input so callers can distinguish "no note".
    static String sanitizeNote(String note)
    {
        if (note == null)
        {
            return null;
        }
        String cleaned = note.replaceAll("\\p{Cntrl}", " ").trim();
        if (cleaned.isEmpty())
        {
            return null;
        }
        if (cleaned.length() > MAX_NOTE_LENGTH)
        {
            cleaned = cleaned.substring(0, MAX_NOTE_LENGTH).trim();
        }
        return cleaned;
    }
}
