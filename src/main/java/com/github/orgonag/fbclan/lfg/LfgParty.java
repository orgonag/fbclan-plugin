package com.github.orgonag.fbclan.lfg;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * A hosted party advertisement (one row of lfg_parties) plus its embedded
 * applicants. Immutable; the panel rebuilds from a fresh fetch rather
 * than mutating. Membership is 1 (the host) + accepted applicants.
 */
@Value
@Builder(toBuilder = true)
public class LfgParty
{
    // Null until the row has been created server-side (the client never
    // generates ids; a fresh upsert is keyed on host_rsn instead).
    String id;
    String hostRsn;
    LfgActivity activity;
    boolean hardMode;
    // ToA invocation level; 0 when unset / not ToA.
    int invocation;
    int capacity;
    String description;
    // Null when unknown.
    Integer world;
    // 0 = no requirement.
    int minKc;
    LfgLootRule lootRule;
    // Stored composition (ToB fixed / CoX chosen); empty for BA and for
    // activities without roles.
    List<LfgRole> requiredRoles;
    LfgRole hostRole;
    boolean learner;
    boolean teacher;
    Instant createdAt;
    Instant updatedAt;
    List<LfgApplicant> applicants;

    public String getTitle()
    {
        return activity.getPartyTitle(hardMode, invocation);
    }

    public List<LfgApplicant> getAccepted()
    {
        List<LfgApplicant> out = new ArrayList<>();
        for (LfgApplicant a : safeApplicants())
        {
            if (a.isAccepted())
            {
                out.add(a);
            }
        }
        return out;
    }

    public List<LfgApplicant> getPending()
    {
        List<LfgApplicant> out = new ArrayList<>();
        for (LfgApplicant a : safeApplicants())
        {
            if (a.isPending())
            {
                out.add(a);
            }
        }
        return out;
    }

    public int getMemberCount()
    {
        return 1 + getAccepted().size();
    }

    public boolean isFull()
    {
        return getMemberCount() >= capacity;
    }

    public LfgApplicant applicantFor(String rsn)
    {
        if (rsn == null)
        {
            return null;
        }
        for (LfgApplicant a : safeApplicants())
        {
            if (LfgNames.equal(a.getRsn(), rsn))
            {
                return a;
            }
        }
        return null;
    }

    public boolean isHostedBy(String rsn)
    {
        return rsn != null && LfgNames.equal(hostRsn, rsn);
    }

    // Roles occupied by the host and accepted members (null entries for
    // members without a declared role — they still hold a seat).
    public List<LfgRole> getTakenRoles()
    {
        List<LfgRole> taken = new ArrayList<>();
        taken.add(hostRole);
        for (LfgApplicant a : getAccepted())
        {
            taken.add(a.getRole());
        }
        return taken;
    }

    public List<LfgRole> getOpenRoles()
    {
        if (!activity.hasRoles())
        {
            return Collections.emptyList();
        }
        return LfgRoles.openRoles(activity, hardMode, capacity, safeRequired(), getTakenRoles());
    }

    private List<LfgApplicant> safeApplicants()
    {
        return applicants == null ? Collections.emptyList() : applicants;
    }

    private List<LfgRole> safeRequired()
    {
        return requiredRoles == null ? Collections.emptyList() : requiredRoles;
    }

    // Payload for an upsert keyed on host_rsn. id / created_at / applicants
    // are never sent: PostgREST's merge-duplicates keeps the existing id
    // on conflict, and created_at is server-defaulted.
    public JsonObject toJson()
    {
        JsonObject data = new JsonObject();
        data.addProperty("host_rsn", hostRsn);
        data.addProperty("activity", activity.getKey());
        data.addProperty("hard_mode", hardMode);
        data.addProperty("invocation", invocation);
        data.addProperty("capacity", capacity);
        String desc = LfgService.sanitize(description, LfgPartyService.MAX_DESCRIPTION_LENGTH);
        if (desc == null)
        {
            data.add("description", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("description", desc);
        }
        if (world == null)
        {
            data.add("world", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("world", world);
        }
        data.addProperty("min_kc", Math.max(0, minKc));
        data.addProperty("loot_rule", (lootRule == null ? LfgLootRule.UNSPECIFIED : lootRule).name());
        String roles = LfgRoles.encode(requiredRoles);
        if (roles == null)
        {
            data.add("required_roles", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("required_roles", roles);
        }
        if (hostRole == null)
        {
            data.add("host_role", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("host_role", hostRole.getKey());
        }
        data.addProperty("learner", learner);
        data.addProperty("teacher", teacher);
        data.addProperty("updated_at", Instant.now().toString());
        data.addProperty("ttl_minutes", LfgPartyService.PARTY_TTL_MINUTES);
        return data;
    }

    // Tolerates missing optional columns so rows written by other builds
    // still deserialize. Returns null when the row is unusable.
    public static LfgParty fromRow(JsonObject row)
    {
        if (!row.has("id") || !row.has("host_rsn") || !row.has("activity"))
        {
            return null;
        }
        LfgActivity activity = LfgActivity.fromKey(row.get("activity").getAsString());
        if (activity == null)
        {
            return null;
        }
        List<LfgApplicant> applicants = new ArrayList<>();
        if (row.has("lfg_applicants") && row.get("lfg_applicants").isJsonArray())
        {
            JsonArray arr = row.getAsJsonArray("lfg_applicants");
            for (JsonElement el : arr)
            {
                if (el.isJsonObject())
                {
                    LfgApplicant a = LfgApplicant.fromRow(el.getAsJsonObject());
                    if (a != null)
                    {
                        applicants.add(a);
                    }
                }
            }
            // Oldest application first, so a host sees the queue in order.
            applicants.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        }
        return LfgParty.builder()
            .id(row.get("id").getAsString())
            .hostRsn(row.get("host_rsn").getAsString())
            .activity(activity)
            .hardMode(optBool(row, "hard_mode"))
            .invocation(optInt(row, "invocation", 0))
            .capacity(Math.max(1, optInt(row, "capacity", activity.getMaxPartySize())))
            .description(LfgApplicant.optString(row, "description"))
            .world(row.has("world") && !row.get("world").isJsonNull() ? row.get("world").getAsInt() : null)
            .minKc(optInt(row, "min_kc", 0))
            .lootRule(LfgLootRule.fromKey(LfgApplicant.optString(row, "loot_rule")))
            .requiredRoles(LfgRoles.decode(LfgApplicant.optString(row, "required_roles")))
            .hostRole(LfgRole.fromKey(LfgApplicant.optString(row, "host_role")))
            .learner(optBool(row, "learner"))
            .teacher(optBool(row, "teacher"))
            .createdAt(optInstant(row, "created_at"))
            .updatedAt(optInstant(row, "updated_at"))
            .applicants(applicants)
            .build();
    }

    private static boolean optBool(JsonObject row, String key)
    {
        return row.has(key) && !row.get(key).isJsonNull() && row.get(key).getAsBoolean();
    }

    private static int optInt(JsonObject row, String key, int def)
    {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsInt() : def;
    }

    private static Instant optInstant(JsonObject row, String key)
    {
        try
        {
            return row.has(key) && !row.get(key).isJsonNull()
                ? OffsetDateTime.parse(row.get(key).getAsString()).toInstant()
                : Instant.now();
        }
        catch (Exception e)
        {
            return Instant.now();
        }
    }
}
