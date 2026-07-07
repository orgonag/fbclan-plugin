package com.github.orgonag.fbclan.lfg;

import com.google.gson.JsonObject;
import lombok.Value;
import java.time.Instant;
import java.time.OffsetDateTime;

@Value
public class LfgEntry
{
    String rsn;
    LfgActivity activity;
    Instant updatedAt;

    // partyId/partySize are null when the lister was not in a Party plugin
    // party at the time of upsert. partyId is a SHA-256 truncation of the
    // local PartyService partyId — never the passphrase.
    String partyId;
    Integer partySize;

    // Optional free-text note the lister attached to their status, e.g.
    // "HMT NFRZ". Null when absent; always trimmed and capped at
    // LfgService.MAX_NOTE_LENGTH before it reaches the database.
    String note;

    public static LfgEntry fromJson(String rsn, String activityKey, String updatedAtStr,
                                    String partyId, Integer partySize, String note)
    {
        LfgActivity activity = LfgActivity.fromKey(activityKey);
        if (activity == null)
        {
            return null;
        }
        Instant updatedAt;
        try
        {
            updatedAt = OffsetDateTime.parse(updatedAtStr).toInstant();
        }
        catch (Exception e)
        {
            return null;
        }
        if (note != null)
        {
            note = note.trim();
            if (note.isEmpty())
            {
                note = null;
            }
        }
        return new LfgEntry(rsn, activity, updatedAt, partyId, partySize, note);
    }

    // Convenience parser for a Supabase row. Tolerates missing party_id /
    // party_size / note columns so older rows (or rows from clients that did
    // not attach them) still deserialize cleanly.
    public static LfgEntry fromRow(JsonObject row)
    {
        if (!row.has("rsn") || !row.has("activity") || !row.has("updated_at"))
        {
            return null;
        }
        String partyId = (row.has("party_id") && !row.get("party_id").isJsonNull())
            ? row.get("party_id").getAsString() : null;
        Integer partySize = (row.has("party_size") && !row.get("party_size").isJsonNull())
            ? row.get("party_size").getAsInt() : null;
        String note = (row.has("note") && !row.get("note").isJsonNull())
            ? row.get("note").getAsString() : null;
        return fromJson(
            row.get("rsn").getAsString(),
            row.get("activity").getAsString(),
            row.get("updated_at").getAsString(),
            partyId,
            partySize,
            note
        );
    }
}
