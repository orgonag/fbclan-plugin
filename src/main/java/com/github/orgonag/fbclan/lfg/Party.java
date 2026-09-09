package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * A hosted party (one lfg_parties row) with its embedded applicants.
 * Immutable: the board rebuilds from each fetch. Membership is the host
 * plus accepted applicants.
 */
@Value
@Builder(toBuilder = true)
public class Party
{
    public static final int MAX_DESCRIPTION = 120;
    public static final int MIN_CAPACITY = 2;
    public static final int MAX_CAPACITY = 100;
    public static final int MAX_INVOCATION = 600;
    public static final int TTL_MINUTES = 30;

    public enum Status
    {
        PENDING, ACCEPTED, DECLINED;

        static Status fromKey(String key)
        {
            try
            {
                return key == null ? PENDING : valueOf(key.trim().toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                return PENDING;
            }
        }
    }

    // Where an applicant's kill count came from: their own client's
    // record, a hiscore lookup, or typed by hand.
    public enum KcSource
    {
        LOCAL, HISCORES, MANUAL;

        static KcSource fromKey(String key)
        {
            try
            {
                return key == null ? null : valueOf(key.trim().toUpperCase());
            }
            catch (IllegalArgumentException e)
            {
                return null;
            }
        }
    }

    /** One lfg_applicants row. */
    @Value
    public static class Applicant
    {
        String rsn;
        Role role;      // null for role-less activities
        boolean learner;
        Status status;
        Instant createdAt;
        Integer kc;     // null = unknown
        KcSource kcSource;
        boolean addedByHost;

        public boolean isAccepted()
        {
            return status == Status.ACCEPTED;
        }

        public boolean isPending()
        {
            return status == Status.PENDING;
        }

        static Applicant fromRow(JsonObject row)
        {
            String rsn = Supabase.str(row, "rsn");
            return rsn.isEmpty() ? null : new Applicant(rsn, Role.fromKey(Supabase.str(row, "role")),
                Supabase.bool(row, "learner"), Status.fromKey(Supabase.str(row, "status")),
                Supabase.instant(row, "created_at", Instant.EPOCH), Supabase.intOrNull(row, "kc"),
                KcSource.fromKey(Supabase.str(row, "kc_source")), Supabase.bool(row, "added_by_host"));
        }
    }

    String id;          // null until created server-side
    String hostRsn;
    Activity activity;
    boolean hardMode;
    int invocation;     // ToA only; 0 otherwise
    int capacity;
    String description;
    Integer world;
    int minKc;          // 0 = none
    LootRule lootRule;
    List<Role> requiredRoles;
    Role hostRole;
    boolean learner;
    boolean teacher;
    Instant createdAt;
    List<Applicant> applicants;

    public String title()
    {
        return activity.partyTitle(hardMode, invocation);
    }

    // CM / HMT, or a ToA at expert-level invocation.
    public boolean isHard()
    {
        return hardMode || (activity.usesInvocation() && invocation >= 300);
    }

    public List<Applicant> accepted()
    {
        List<Applicant> out = new ArrayList<>();
        for (Applicant a : applicants)
        {
            if (a.isAccepted())
            {
                out.add(a);
            }
        }
        return out;
    }

    public List<Applicant> pending()
    {
        List<Applicant> out = new ArrayList<>();
        for (Applicant a : applicants)
        {
            if (a.isPending())
            {
                out.add(a);
            }
        }
        return out;
    }

    public int memberCount()
    {
        return 1 + accepted().size();
    }

    public boolean isFull()
    {
        return memberCount() >= capacity;
    }

    public boolean isHostedBy(String rsn)
    {
        return rsn != null && Names.same(hostRsn, rsn);
    }

    public Applicant applicantFor(String rsn)
    {
        for (Applicant a : applicants)
        {
            if (rsn != null && Names.same(a.getRsn(), rsn))
            {
                return a;
            }
        }
        return null;
    }

    public List<Role> openRoles()
    {
        if (!activity.hasRoles())
        {
            return Collections.emptyList();
        }
        List<Role> taken = new ArrayList<>();
        taken.add(hostRole);
        for (Applicant a : accepted())
        {
            taken.add(a.getRole());
        }
        return Role.open(activity, hardMode, capacity, requiredRoles, taken);
    }

    // Upsert payload keyed on host_rsn; id and created_at are server-owned.
    public JsonObject toJson()
    {
        JsonObject d = new JsonObject();
        d.addProperty("host_rsn", hostRsn);
        d.addProperty("activity", activity.key());
        d.addProperty("hard_mode", hardMode);
        d.addProperty("invocation", invocation);
        d.addProperty("capacity", capacity);
        Supabase.put(d, "description", Names.sanitize(description, MAX_DESCRIPTION));
        Supabase.put(d, "world", world);
        d.addProperty("min_kc", Math.max(0, minKc));
        d.addProperty("loot_rule", (lootRule == null ? LootRule.UNSPECIFIED : lootRule).name());
        Supabase.put(d, "required_roles", Role.encode(requiredRoles));
        Supabase.put(d, "host_role", hostRole == null ? null : hostRole.key());
        d.addProperty("learner", learner);
        d.addProperty("teacher", teacher);
        d.addProperty("updated_at", Instant.now().toString());
        d.addProperty("ttl_minutes", TTL_MINUTES);
        return d;
    }

    // Tolerates missing optional columns; null when unusable.
    public static Party fromRow(JsonObject row)
    {
        Activity activity = Activity.fromKey(Supabase.str(row, "activity"));
        if (activity == null || Supabase.str(row, "id").isEmpty() || Supabase.str(row, "host_rsn").isEmpty())
        {
            return null;
        }
        List<Applicant> applicants = new ArrayList<>();
        if (Supabase.has(row, "lfg_applicants") && row.get("lfg_applicants").isJsonArray())
        {
            for (JsonElement el : row.getAsJsonArray("lfg_applicants"))
            {
                Applicant a = el.isJsonObject() ? Applicant.fromRow(el.getAsJsonObject()) : null;
                if (a != null)
                {
                    applicants.add(a);
                }
            }
            applicants.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        }
        return Party.builder()
            .id(Supabase.str(row, "id"))
            .hostRsn(Supabase.str(row, "host_rsn"))
            .activity(activity)
            .hardMode(Supabase.bool(row, "hard_mode"))
            .invocation(Supabase.intOr(row, "invocation", 0))
            .capacity(Math.max(1, Supabase.intOr(row, "capacity", activity.getMaxPartySize())))
            .description(Supabase.has(row, "description") ? Supabase.str(row, "description") : null)
            .world(Supabase.intOrNull(row, "world"))
            .minKc(Supabase.intOr(row, "min_kc", 0))
            .lootRule(LootRule.fromKey(Supabase.str(row, "loot_rule")))
            .requiredRoles(Role.decode(Supabase.str(row, "required_roles")))
            .hostRole(Role.fromKey(Supabase.str(row, "host_role")))
            .learner(Supabase.bool(row, "learner"))
            .teacher(Supabase.bool(row, "teacher"))
            .createdAt(Supabase.instant(row, "created_at", Instant.now()))
            .applicants(Collections.unmodifiableList(applicants))
            .build();
    }
}
