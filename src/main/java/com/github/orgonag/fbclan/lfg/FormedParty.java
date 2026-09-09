package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * A party that filled up: a self-contained snapshot (one
 * lfg_formed_parties row) with no links to the live tables, which are
 * deleted when it's written. Kept server-side for 7 days as history.
 */
@Value
public class FormedParty
{
    @Value
    public static class Member
    {
        String rsn;
        Role role;
        boolean addedByHost;
    }

    String id;
    String partyId;   // the live party it came from
    String hostRsn;
    Activity activity;
    boolean hardMode;
    int invocation;
    int capacity;
    Integer world;
    List<Member> members;  // host first
    Instant formedAt;

    public String title()
    {
        return activity.partyTitle(hardMode, invocation);
    }

    public boolean isHostedBy(String rsn)
    {
        return rsn != null && Names.same(hostRsn, rsn);
    }

    public boolean includes(String rsn)
    {
        return rsn != null && members.stream().anyMatch(m -> Names.same(m.getRsn(), rsn));
    }

    // "Shok (Melee), Bud (Ranged), Pal"
    public String roster()
    {
        StringBuilder sb = new StringBuilder();
        for (Member m : members)
        {
            sb.append(sb.length() > 0 ? ", " : "").append(m.getRsn());
            if (m.getRole() != null)
            {
                sb.append(" (").append(m.getRole().getDisplayName()).append(')');
            }
        }
        return sb.toString();
    }

    public static FormedParty from(Party p)
    {
        List<Member> members = new ArrayList<>();
        members.add(new Member(p.getHostRsn(), p.getHostRole(), false));
        for (Party.Applicant a : p.accepted())
        {
            members.add(new Member(a.getRsn(), a.getRole(), a.isAddedByHost()));
        }
        return new FormedParty(null, p.getId(), p.getHostRsn(), p.getActivity(), p.isHardMode(), p.getInvocation(),
            p.getCapacity(), p.getWorld(), members, Instant.now());
    }

    public JsonObject toJson()
    {
        JsonObject d = new JsonObject();
        Supabase.put(d, "party_id", partyId);
        d.addProperty("host_rsn", hostRsn);
        d.addProperty("activity", activity.key());
        d.addProperty("hard_mode", hardMode);
        d.addProperty("invocation", invocation);
        d.addProperty("capacity", capacity);
        Supabase.put(d, "world", world);
        JsonArray arr = new JsonArray();
        for (Member m : members)
        {
            JsonObject o = new JsonObject();
            o.addProperty("rsn", m.getRsn());
            Supabase.put(o, "role", m.getRole() == null ? null : m.getRole().key());
            o.addProperty("added_by_host", m.isAddedByHost());
            arr.add(o);
        }
        d.add("members", arr);
        return d;
    }

    public static FormedParty fromRow(JsonObject row)
    {
        Activity activity = Activity.fromKey(Supabase.str(row, "activity"));
        if (activity == null || Supabase.str(row, "id").isEmpty() || Supabase.str(row, "host_rsn").isEmpty())
        {
            return null;
        }
        List<Member> members = new ArrayList<>();
        if (Supabase.has(row, "members") && row.get("members").isJsonArray())
        {
            for (JsonElement el : row.getAsJsonArray("members"))
            {
                if (!el.isJsonObject())
                {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String rsn = Supabase.str(o, "rsn");
                if (!rsn.isEmpty())
                {
                    members.add(new Member(rsn, Role.fromKey(Supabase.str(o, "role")), Supabase.bool(o, "added_by_host")));
                }
            }
        }
        return new FormedParty(Supabase.str(row, "id"),
            Supabase.has(row, "party_id") ? Supabase.str(row, "party_id") : null,
            Supabase.str(row, "host_rsn"), activity, Supabase.bool(row, "hard_mode"),
            Supabase.intOr(row, "invocation", 0), Supabase.intOr(row, "capacity", Math.max(1, members.size())),
            Supabase.intOrNull(row, "world"), members, Supabase.instant(row, "formed_at", Instant.now()));
    }
}
