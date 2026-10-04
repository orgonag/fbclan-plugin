package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.core.ApiResult;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/** The fb_board / fb_lfg API (protocol 3, schema 4). Each logical write has one retry identity. */
@Singleton
public class PartyApi
{
    @Value public static class Snapshot { List<Party> parties; List<FormedParty> formed; }
    public static final Snapshot EMPTY = new Snapshot(Collections.emptyList(), Collections.emptyList());
    private final Supabase db;
    private final Clan clan;
    private final ThreadLocal<Session> actor = new ThreadLocal<>();
    // The server's reason for the last refused command on this thread, else null.
    private final ThreadLocal<String> refusal = new ThreadLocal<>();
    private volatile Snapshot snapshot = EMPTY;

    @Inject
    public PartyApi(Supabase db, Clan clan)
    {
        this.db = db;
        this.clan = clan;
    }

    public boolean inSession(Session session, BooleanSupplier action)
    {
        actor.set(session);
        refusal.remove();
        try { return clan.current(session) && action.getAsBoolean(); }
        finally { actor.remove(); }
    }

    // Executor. Null when the board can't be read or parsed.
    public Snapshot fetch()
    {
        ApiResult response = db.rpcResult("fb_board", new JsonObject());
        if (!response.successful() || response.getBody() == null || !response.getBody().isJsonObject()) return null;
        JsonObject body = response.getBody().getAsJsonObject();
        if (Supabase.intOr(body, "protocol", 0) != 3) return null;
        List<Party> parties = new ArrayList<>();
        List<FormedParty> formed = new ArrayList<>();
        try
        {
            for (JsonElement el : body.getAsJsonArray("parties"))
            {
                // A row this version can't read (a newer activity) is skipped, not fatal.
                Party p = Party.fromRow(el.getAsJsonObject());
                if (p != null) parties.add(p);
            }
            for (JsonElement el : body.getAsJsonArray("formed"))
            {
                FormedParty f = FormedParty.fromRow(el.getAsJsonObject());
                if (f != null) formed.add(f);
            }
        }
        catch (RuntimeException e) { return null; }
        return new Snapshot(Collections.unmodifiableList(parties), Collections.unmodifiableList(formed));
    }

    // Executor: why the server refused the last command run on this thread
    // ("You already have an ASAP party"), or null when it never answered.
    public String refusal()
    {
        return refusal.get();
    }

    public void publish(Snapshot value)
    {
        snapshot = value;
    }

    public boolean save(Party party)
    {
        JsonObject d = party.toJson();
        if (party.getId() != null) d.addProperty("id", party.getId());
        return command(party.getId() == null ? "create" : "edit", d, party.getId() == null ? null : party.getVersion());
    }

    public boolean disband(String id)
    {
        return command("disband", data(id));
    }

    public boolean apply(String id, Role role, boolean learner, Integer kc, Party.KcSource source)
    {
        JsonObject d = data(id);
        Supabase.put(d, "role", role == null ? null : role.key());
        d.addProperty("learner", learner);
        Supabase.put(d, "kc", kc);
        Supabase.put(d, "kc_source", source == null ? null : source.name());
        return command("apply", d);
    }

    // The host removing someone is a kick; anyone else is leaving.
    public boolean withdraw(String id, String rsn)
    {
        Party p = find(id);
        Session s = actor.get() == null ? clan.snapshot() : actor.get();
        JsonObject d = data(id);
        d.addProperty("rsn", rsn);
        return command(p != null && p.isHostedBy(s.getRsn()) ? "kick" : "leave", d);
    }

    public boolean setStatus(String id, String rsn, Party.Status status)
    {
        JsonObject d = data(id);
        d.addProperty("rsn", rsn);
        return command(status == Party.Status.ACCEPTED ? "accept" : "decline", d);
    }

    public boolean addMember(String id, String rsn, Role role)
    {
        // Someone who already applied is accepted instead (the server would
        // refuse a second row for them with a database error).
        Party p = find(id);
        Party.Applicant existing = p == null ? null : p.applicantFor(rsn);
        if (existing != null && existing.isPending()) return setStatus(id, rsn, Party.Status.ACCEPTED);
        if (existing != null)
        {
            refusal.set(existing.isAccepted() ? "They're already in this party." : "You declined them earlier. Ask them to apply again.");
            return false;
        }
        JsonObject d = data(id);
        d.addProperty("rsn", rsn);
        Supabase.put(d, "role", role == null ? null : role.key());
        return command("add", d);
    }

    public boolean deleteFormed(String id)
    {
        return command("remove_formed", data(id));
    }

    // ------------------------------------------------------------ plumbing

    private Party find(String id)
    {
        for (Party p : snapshot.parties) if (p.getId().equals(id)) return p;
        return null;
    }

    private static JsonObject data(String id)
    {
        JsonObject d = new JsonObject();
        d.addProperty("id", id);
        return d;
    }

    // Only an edit carries the party's version (so it can't overwrite a
    // newer one). Everything else is re-checked by the server under its
    // lock; a version there would refuse the action whenever anyone else
    // touched the party since the last poll.
    private boolean command(String action, JsonObject d)
    {
        return command(action, d, null);
    }

    private boolean command(String action, JsonObject data, Long expected)
    {
        Session session = actor.get() == null ? clan.snapshot() : actor.get();
        if (!clan.current(session)) return false;
        JsonObject args = new JsonObject();
        args.addProperty("p_action", action);
        args.addProperty("p_actor", session.getRsn());
        args.add("p_data", data);
        args.addProperty("p_operation", UUID.randomUUID().toString());
        if (expected != null) args.addProperty("p_expected", expected);
        refusal.remove();
        for (int attempt = 0; attempt < 2 && clan.current(session); attempt++)
        {
            ApiResult result = db.rpcResult("fb_lfg", args);
            if (result.successful()) return true;
            // A domain refusal (HTTP 200 with a non-ok status) carries a
            // readable reason; transport errors don't.
            if (result.getHttpStatus() >= 200 && result.getHttpStatus() < 300 && result.getError() == null) refusal.set(result.message());
            if (!result.retryable()) return false;
        }
        return false;
    }
}
