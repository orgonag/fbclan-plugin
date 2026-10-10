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

/**
 * The fb_board / fb_lfg API (protocol 3). Each write takes the session it
 * acts for and returns null when done, else the server's reason
 * ("Party is full"), or "" when there is no readable reason. Each logical
 * write has one retry identity.
 */
@Singleton
public class PartyApi
{
    @Value public static class Snapshot { List<Party> parties; List<FormedParty> formed; }
    private final Supabase db;
    private final Clan clan;

    @Inject
    public PartyApi(Supabase db, Clan clan)
    {
        this.db = db;
        this.clan = clan;
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

    public String save(Session s, Party party)
    {
        JsonObject d = party.toJson();
        if (party.getId() != null) d.addProperty("id", party.getId());
        return command(s, party.getId() == null ? "create" : "edit", d, party.getId() == null ? null : party.getVersion());
    }

    public String disband(Session s, String id)
    {
        return command(s, "disband", data(id), null);
    }

    public String apply(Session s, String id, Role role, boolean learner, Integer kc, Party.KcSource source)
    {
        JsonObject d = data(id);
        d.addProperty("role", role == null ? null : role.key());
        d.addProperty("learner", learner);
        d.addProperty("kc", kc);
        d.addProperty("kc_source", source == null ? null : source.name());
        return command(s, "apply", d, null);
    }

    // The player leaving a party, or withdrawing/dismissing an application.
    public String leave(Session s, String id)
    {
        JsonObject d = data(id);
        d.addProperty("rsn", s.getRsn());
        return command(s, "leave", d, null);
    }

    // The host removing someone.
    public String kick(Session s, String id, String rsn)
    {
        JsonObject d = data(id);
        d.addProperty("rsn", rsn);
        return command(s, "kick", d, null);
    }

    public String setStatus(Session s, String id, String rsn, Party.Status status)
    {
        JsonObject d = data(id);
        d.addProperty("rsn", rsn);
        return command(s, status == Party.Status.ACCEPTED ? "accept" : "decline", d, null);
    }

    // `party` as last loaded. Someone who already applied is accepted instead
    // (the server would refuse a second row for them with a database error).
    public String addMember(Session s, Party party, String rsn, Role role)
    {
        Party.Applicant existing = party.applicantFor(rsn);
        if (existing != null && existing.isAccepted()) return "They're already in this party.";
        if (existing != null)
        {
            // Pending, or declined but perhaps re-applied since the last refresh.
            String accepted = setStatus(s, party.getId(), rsn, Party.Status.ACCEPTED);
            return !existing.isPending() && "Application changed or removed".equals(accepted)
                ? "You declined them earlier. Ask them to apply again." : accepted;
        }
        JsonObject d = data(party.getId());
        d.addProperty("rsn", rsn);
        d.addProperty("role", role == null ? null : role.key());
        String added = command(s, "add", d, null);
        // They applied (or were declined) since the last refresh.
        return added != null && added.startsWith("duplicate key") ? "They have just applied. Accept them from the list above." : added;
    }

    public String deleteFormed(Session s, String id)
    {
        return command(s, "remove_formed", data(id), null);
    }

    // ------------------------------------------------------------ plumbing

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
    private String command(Session session, String action, JsonObject data, Long expected)
    {
        if (!clan.current(session)) return "";
        JsonObject args = new JsonObject();
        args.addProperty("p_action", action);
        args.addProperty("p_actor", session.getRsn());
        args.add("p_data", data);
        args.addProperty("p_operation", UUID.randomUUID().toString());
        if (expected != null) args.addProperty("p_expected", expected);
        String reason = "";
        for (int attempt = 0; attempt < 2 && clan.current(session); attempt++)
        {
            ApiResult result = db.rpcResult("fb_lfg", args);
            if (result.successful()) return null;
            // A domain refusal (HTTP 200 with a non-ok status) carries a
            // readable reason; transport errors don't.
            if (result.getHttpStatus() >= 200 && result.getHttpStatus() < 300 && result.getError() == null) reason = result.message();
            if (!result.retryable()) return reason;
        }
        return reason;
    }
}
