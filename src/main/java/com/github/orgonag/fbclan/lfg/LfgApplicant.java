package com.github.orgonag.fbclan.lfg;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.Value;

/**
 * One row of lfg_applicants: a member's application to a hosted party.
 * The (partyId, rsn) pair is the primary key; status moves
 * PENDING -> ACCEPTED / DECLINED under the host's control, and the row
 * disappears on withdraw, kick, or when the party is disbanded (cascade).
 */
@Value
public class LfgApplicant
{
    public enum Status
    {
        PENDING, ACCEPTED, DECLINED;

        public static Status fromKey(String key)
        {
            if (key == null)
            {
                return PENDING;
            }
            try
            {
                return valueOf(key.trim().toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                return PENDING;
            }
        }
    }

    String partyId;
    String rsn;
    // Null for activities without roles.
    LfgRole role;
    boolean learner;
    Status status;
    Instant createdAt;

    public boolean isAccepted()
    {
        return status == Status.ACCEPTED;
    }

    public boolean isPending()
    {
        return status == Status.PENDING;
    }

    public static LfgApplicant fromRow(JsonObject row)
    {
        if (!row.has("party_id") || !row.has("rsn"))
        {
            return null;
        }
        Instant createdAt;
        try
        {
            createdAt = row.has("created_at") && !row.get("created_at").isJsonNull()
                ? OffsetDateTime.parse(row.get("created_at").getAsString()).toInstant()
                : Instant.EPOCH;
        }
        catch (Exception e)
        {
            createdAt = Instant.EPOCH;
        }
        return new LfgApplicant(
            row.get("party_id").getAsString(),
            row.get("rsn").getAsString(),
            LfgRole.fromKey(optString(row, "role")),
            row.has("learner") && !row.get("learner").isJsonNull() && row.get("learner").getAsBoolean(),
            Status.fromKey(optString(row, "status")),
            createdAt);
    }

    static String optString(JsonObject row, String key)
    {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : null;
    }
}
