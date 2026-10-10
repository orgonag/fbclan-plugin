package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Supabase;
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
    Instant scheduledFor;  // null = was an ASAP party

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

    public static FormedParty fromRow(JsonObject row)
    {
        Activity activity = Activity.fromKey(Supabase.str(row, "activity"));
        String host = Names.untagged(Supabase.str(row, "host_rsn"));
        if (activity == null || Supabase.str(row, "id").isEmpty() || host.isEmpty())
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
                String rsn = Names.untagged(Supabase.str(o, "rsn"));
                if (!rsn.isEmpty())
                {
                    members.add(new Member(rsn, Role.fromKey(Supabase.str(o, "role")), Supabase.bool(o, "added_by_host")));
                }
            }
        }
        return new FormedParty(Supabase.str(row, "id"),
            Supabase.has(row, "party_id") ? Supabase.str(row, "party_id") : null,
            host, activity, Supabase.bool(row, "hard_mode"),
            Supabase.intOr(row, "invocation", 0), Supabase.intOr(row, "capacity", Math.max(1, members.size())),
            Supabase.intOrNull(row, "world"), members, Supabase.instant(row, "formed_at", Instant.now()),
            Supabase.instant(row, "scheduled_for", null));
    }
}
