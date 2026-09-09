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

    // Where an applicant's kill count came from. LOCAL is read from the
    // applicant's own RuneLite config (what the game told their client);
    // HISCORES was prefilled from a hiscore lookup; MANUAL was typed.
    public enum KcSource
    {
        LOCAL, HISCORES, MANUAL;

        public static KcSource fromKey(String key)
        {
            if (key == null)
            {
                return null;
            }
            try
            {
                return valueOf(key.trim().toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                return null;
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
    // Kill count the applicant sent with the application (null = none).
    Integer kc;
    KcSource kcSource;
    // True when the host added this member directly (a buddy who isn't on
    // LFG); such rows are created already ACCEPTED.
    boolean addedByHost;

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
        Integer kc = row.has("kc") && !row.get("kc").isJsonNull() ? row.get("kc").getAsInt() : null;
        return new LfgApplicant(
            row.get("party_id").getAsString(),
            row.get("rsn").getAsString(),
            LfgRole.fromKey(optString(row, "role")),
            row.has("learner") && !row.get("learner").isJsonNull() && row.get("learner").getAsBoolean(),
            Status.fromKey(optString(row, "status")),
            createdAt,
            kc,
            KcSource.fromKey(optString(row, "kc_source")),
            row.has("added_by_host") && !row.get("added_by_host").isJsonNull()
                && row.get("added_by_host").getAsBoolean());
    }

    static String optString(JsonObject row, String key)
    {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : null;
    }
}
