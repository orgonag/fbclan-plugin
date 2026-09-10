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
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/** Version 3 transactional API. Each logical write has one retry identity. */
@Singleton
public class PartyApi
{
    public enum AddResult { OK, REJECTED, FAILED }
    @Value public static class Snapshot { List<Party> parties; List<FormedParty> formed; }
    private final Supabase db;
    private final Clan clan;
    private final ThreadLocal<Session> actor = new ThreadLocal<>();
    private volatile Snapshot snapshot = new Snapshot(Collections.emptyList(), Collections.emptyList());
    @Inject public PartyApi(Supabase db, Clan clan) { this.db = db; this.clan = clan; }

    public boolean inSession(Session session, java.util.function.BooleanSupplier action)
    {
        actor.set(session);
        try { return clan.current(session) && action.getAsBoolean(); }
        finally { actor.remove(); }
    }

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
                Party p = Party.fromRow(el.getAsJsonObject());
                if (p == null) return null;
                parties.add(p);
            }
            for (JsonElement el : body.getAsJsonArray("formed"))
            {
                FormedParty f = FormedParty.fromRow(el.getAsJsonObject());
                if (f == null) return null;
                formed.add(f);
            }
        }
        catch (RuntimeException e) { return null; }
        return new Snapshot(Collections.unmodifiableList(parties), Collections.unmodifiableList(formed));
    }

    public void publish(Snapshot value) { snapshot = value; }
    private Party find(String id)
    {
        for (Party p : snapshot.parties) if (p.getId().equals(id)) return p;
        return null;
    }
    private Party hosted(String rsn)
    {
        for (Party p : snapshot.parties) if (p.isHostedBy(rsn)) return p;
        return null;
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
        for (int attempt = 0; attempt < 2 && clan.current(session); attempt++)
        {
            ApiResult result = db.rpcResult("fb_lfg", args);
            if (result.successful()) return true;
            if (!result.retryable()) return false;
        }
        return false;
    }
    private JsonObject data(String id) { JsonObject d = new JsonObject(); d.addProperty("id", id); return d; }
    private boolean command(String action, JsonObject d)
    {
        Party p = find(Supabase.str(d, "id"));
        return command(action, d, p == null ? null : p.getVersion());
    }
    public boolean save(Party party)
    {
        JsonObject d = party.toJson();
        if (party.getId() != null) d.addProperty("id", party.getId());
        return command(party.getId() == null ? "create" : "edit", d,
            party.getId() == null ? null : party.getVersion());
    }
    public boolean heartbeat(String rsn)
    {
        Party p = hosted(rsn);
        return p != null && command("heartbeat", data(p.getId()), null);
    }
    public boolean disband(String id)
    {
        return command("disband", data(id));
    }
    public boolean apply(String id, String rsn, Role role, boolean learner, Integer kc, Party.KcSource source)
    {
        JsonObject d = data(id);
        Supabase.put(d, "role", role == null ? null : role.key());
        d.addProperty("learner", learner);
        Supabase.put(d, "kc", kc);
        Supabase.put(d, "kc_source", source == null ? null : source.name());
        return command("apply", d);
    }
    public boolean withdraw(String id, String rsn)
    {
        Party p = find(id);
        Session s = actor.get() == null ? clan.snapshot() : actor.get();
        JsonObject d = data(id); d.addProperty("rsn", rsn);
        return command(p != null && p.isHostedBy(s.getRsn()) ? "kick" : "leave", d);
    }
    public boolean setStatus(String id, String rsn, Party.Status status)
    {
        JsonObject d = data(id); d.addProperty("rsn", rsn);
        return command(status == Party.Status.ACCEPTED ? "accept" : "decline", d);
    }
    public AddResult addMember(String id, String rsn, Role role)
    {
        JsonObject d = data(id); d.addProperty("rsn", rsn);
        Supabase.put(d, "role", role == null ? null : role.key());
        return command("add", d) ? AddResult.OK : AddResult.REJECTED;
    }
    public boolean deleteFormed(String id) { return command("remove_formed", data(id), null); }
}
