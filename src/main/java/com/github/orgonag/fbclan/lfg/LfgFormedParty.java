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
 * A party that filled up (one row of lfg_formed_parties): a self-contained
 * snapshot of the live party at the moment it formed. It has no links to
 * lfg_parties / lfg_applicants — those rows are deleted when the snapshot
 * is written, freeing the host to host again and the members to apply
 * elsewhere. Kept server-side for 7 days as clan history.
 */
@Value
@Builder(toBuilder = true)
public class LfgFormedParty
{
    @Value
    public static class Member
    {
        String rsn;
        // Null for role-less activities.
        LfgRole role;
        boolean addedByHost;
    }

    String id;
    // Id of the live party this came from; lets a client tell "formed" from
    // "disbanded" when a party vanishes from the board.
    String partyId;
    String hostRsn;
    LfgActivity activity;
    boolean hardMode;
    int invocation;
    int capacity;
    Integer world;
    // Host first, then members in acceptance order.
    List<Member> members;
    Instant formedAt;

    public String getTitle()
    {
        return activity.getPartyTitle(hardMode, invocation);
    }

    public boolean isHostedBy(String rsn)
    {
        return rsn != null && LfgNames.equal(hostRsn, rsn);
    }

    public boolean includes(String rsn)
    {
        if (rsn == null)
        {
            return false;
        }
        for (Member m : safeMembers())
        {
            if (LfgNames.equal(m.getRsn(), rsn))
            {
                return true;
            }
        }
        return false;
    }

    // "Shok (Melee), Bud (Ranged), Pal" — host first.
    public String getRoster()
    {
        StringBuilder sb = new StringBuilder();
        for (Member m : safeMembers())
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(m.getRsn());
            if (m.getRole() != null)
            {
                sb.append(" (").append(m.getRole().getDisplayName()).append(')');
            }
        }
        return sb.toString();
    }

    private List<Member> safeMembers()
    {
        return members == null ? Collections.emptyList() : members;
    }

    // Snapshot of a live party: the host, then every accepted member.
    public static LfgFormedParty from(LfgParty p)
    {
        List<Member> members = new ArrayList<>();
        members.add(new Member(p.getHostRsn(), p.getHostRole(), false));
        for (LfgApplicant a : p.getAccepted())
        {
            members.add(new Member(a.getRsn(), a.getRole(), a.isAddedByHost()));
        }
        return LfgFormedParty.builder()
            .partyId(p.getId())
            .hostRsn(p.getHostRsn())
            .activity(p.getActivity())
            .hardMode(p.isHardMode())
            .invocation(p.getInvocation())
            .capacity(p.getCapacity())
            .world(p.getWorld())
            .members(members)
            .formedAt(Instant.now())
            .build();
    }

    // Insert payload. id / formed_at are server-owned.
    public JsonObject toJson()
    {
        JsonObject data = new JsonObject();
        if (partyId == null)
        {
            data.add("party_id", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("party_id", partyId);
        }
        data.addProperty("host_rsn", hostRsn);
        data.addProperty("activity", activity.getKey());
        data.addProperty("hard_mode", hardMode);
        data.addProperty("invocation", invocation);
        data.addProperty("capacity", capacity);
        if (world == null)
        {
            data.add("world", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("world", world);
        }
        JsonArray arr = new JsonArray();
        for (Member m : safeMembers())
        {
            JsonObject o = new JsonObject();
            o.addProperty("rsn", m.getRsn());
            if (m.getRole() == null)
            {
                o.add("role", JsonNull.INSTANCE);
            }
            else
            {
                o.addProperty("role", m.getRole().getKey());
            }
            o.addProperty("added_by_host", m.isAddedByHost());
            arr.add(o);
        }
        data.add("members", arr);
        return data;
    }

    // Returns null when the row is unusable.
    public static LfgFormedParty fromRow(JsonObject row)
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
        List<Member> members = new ArrayList<>();
        if (row.has("members") && row.get("members").isJsonArray())
        {
            for (JsonElement el : row.getAsJsonArray("members"))
            {
                if (!el.isJsonObject())
                {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String rsn = LfgApplicant.optString(o, "rsn");
                if (rsn == null || rsn.trim().isEmpty())
                {
                    continue;
                }
                members.add(new Member(rsn, LfgRole.fromKey(LfgApplicant.optString(o, "role")),
                    o.has("added_by_host") && !o.get("added_by_host").isJsonNull()
                        && o.get("added_by_host").getAsBoolean()));
            }
        }
        Instant formedAt;
        try
        {
            formedAt = row.has("formed_at") && !row.get("formed_at").isJsonNull()
                ? OffsetDateTime.parse(row.get("formed_at").getAsString()).toInstant()
                : Instant.now();
        }
        catch (Exception e)
        {
            formedAt = Instant.now();
        }
        return LfgFormedParty.builder()
            .id(row.get("id").getAsString())
            .partyId(LfgApplicant.optString(row, "party_id"))
            .hostRsn(row.get("host_rsn").getAsString())
            .activity(activity)
            .hardMode(row.has("hard_mode") && !row.get("hard_mode").isJsonNull() && row.get("hard_mode").getAsBoolean())
            .invocation(row.has("invocation") && !row.get("invocation").isJsonNull() ? row.get("invocation").getAsInt() : 0)
            .capacity(row.has("capacity") && !row.get("capacity").isJsonNull()
                ? row.get("capacity").getAsInt() : Math.max(1, members.size()))
            .world(row.has("world") && !row.get("world").isJsonNull() ? row.get("world").getAsInt() : null)
            .members(members)
            .formedAt(formedAt)
            .build();
    }
}
