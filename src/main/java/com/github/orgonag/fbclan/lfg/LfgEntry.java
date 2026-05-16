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

    public static LfgEntry fromJson(String rsn, String activityKey, String updatedAtStr)
    {
        return fromJson(rsn, activityKey, updatedAtStr, null, null);
    }

    public static LfgEntry fromJson(String rsn, String activityKey, String updatedAtStr,
                                    String partyId, Integer partySize)
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
        return new LfgEntry(rsn, activity, updatedAt, partyId, partySize);
    }

    // Convenience parser for a Supabase row. Tolerates missing party_id /
    // party_size columns so older rows (or rows from clients that did not
    // attach party info) still deserialize cleanly.
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
        return fromJson(
            row.get("rsn").getAsString(),
            row.get("activity").getAsString(),
            row.get("updated_at").getAsString(),
            partyId,
            partySize
        );
    }
}
