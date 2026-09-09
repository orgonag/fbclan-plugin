package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Supabase I/O for parties: lfg_parties (+ embedded lfg_applicants) and
 * the lfg_formed_parties snapshots. Membership is verified before any
 * write; rows are scoped by RSN client-side and the database enforces
 * the invariants (one party per player, capacity, well-formed rows).
 * Executor only.
 */
@Singleton
public class PartyApi
{
    public enum AddResult
    {
        OK, REJECTED, FAILED
    }

    private final Supabase db;

    @Inject
    public PartyApi(Supabase db)
    {
        this.db = db;
    }

    // Null when the fetch failed, so the board can keep its snapshot
    // rather than announce every party as disbanded.
    public List<Party> parties()
    {
        JsonArray rows = db.getOrNull("lfg_parties", "select=*,lfg_applicants(*)&order=created_at.desc");
        if (rows == null)
        {
            return null;
        }
        List<Party> out = new ArrayList<>();
        for (JsonElement el : rows)
        {
            Party p = el.isJsonObject() ? Party.fromRow(el.getAsJsonObject()) : null;
            if (p != null)
            {
                out.add(p);
            }
        }
        return out;
    }

    public List<FormedParty> formed()
    {
        JsonArray rows = db.getOrNull("lfg_formed_parties", "select=*&order=formed_at.desc");
        if (rows == null)
        {
            return null;
        }
        List<FormedParty> out = new ArrayList<>();
        for (JsonElement el : rows)
        {
            FormedParty f = el.isJsonObject() ? FormedParty.fromRow(el.getAsJsonObject()) : null;
            if (f != null)
            {
                out.add(f);
            }
        }
        return out;
    }

    // Create or edit: one party per host, keyed on host_rsn. Hosting and
    // applying are exclusive, so any application goes first.
    public boolean save(Party party)
    {
        return withdrawAll(party.getHostRsn()) && db.upsert("lfg_parties", party.toJson(), "host_rsn");
    }

    public boolean heartbeat(String hostRsn)
    {
        JsonObject d = new JsonObject();
        d.addProperty("updated_at", Instant.now().toString());
        return db.patch("lfg_parties", "host_rsn=eq." + Supabase.enc(hostRsn), d);
    }

    // Applicants cascade.
    public boolean disband(String hostRsn)
    {
        return db.delete("lfg_parties", "host_rsn=eq." + Supabase.enc(hostRsn));
    }

    // A player is in at most one party: applying withdraws everywhere first.
    public boolean apply(String partyId, String rsn, Role role, boolean learner, Integer kc, Party.KcSource source)
    {
        if (!withdrawAll(rsn))
        {
            return false;
        }
        JsonObject d = new JsonObject();
        d.addProperty("party_id", partyId);
        d.addProperty("rsn", rsn);
        Supabase.put(d, "role", role == null ? null : role.key());
        d.addProperty("learner", learner);
        d.addProperty("status", Party.Status.PENDING.name());
        Supabase.put(d, "kc", kc == null || source == null ? null : Math.max(0, Math.min(100_000, kc)));
        Supabase.put(d, "kc_source", kc == null || source == null ? null : source.name());
        return db.insert("lfg_applicants", d);
    }

    public boolean withdraw(String partyId, String rsn)
    {
        return db.delete("lfg_applicants", "party_id=eq." + Supabase.enc(partyId) + "&rsn=eq." + Supabase.enc(rsn));
    }

    public boolean withdrawAll(String rsn)
    {
        return db.delete("lfg_applicants", "rsn=eq." + Supabase.enc(rsn));
    }

    public boolean setStatus(String partyId, String rsn, Party.Status status)
    {
        JsonObject d = new JsonObject();
        d.addProperty("status", status.name());
        d.addProperty("updated_at", Instant.now().toString());
        return db.patch("lfg_applicants", "party_id=eq." + Supabase.enc(partyId) + "&rsn=eq." + Supabase.enc(rsn), d);
    }

    // Host seats a buddy who isn't on LFG: created already accepted.
    // Server rules still apply (one party per name, capacity, not self).
    public AddResult addMember(String partyId, String rsn, Role role)
    {
        JsonObject d = new JsonObject();
        d.addProperty("party_id", partyId);
        d.addProperty("rsn", rsn.trim());
        Supabase.put(d, "role", role == null ? null : role.key());
        d.addProperty("learner", false);
        d.addProperty("status", Party.Status.ACCEPTED.name());
        d.addProperty("added_by_host", true);
        int code = db.insertStatus("lfg_applicants", d);
        if (code >= 200 && code < 300)
        {
            return AddResult.OK;
        }
        return code == 409 || code == 400 ? AddResult.REJECTED : AddResult.FAILED;
    }

    // When a party fills: snapshot first (a failure leaves it open rather
    // than lost), then delete the live row.
    public boolean form(Party party)
    {
        return db.insert("lfg_formed_parties", FormedParty.from(party).toJson())
            && db.delete("lfg_parties", "id=eq." + Supabase.enc(party.getId()));
    }

    public boolean deleteFormed(String id)
    {
        return db.delete("lfg_formed_parties", "id=eq." + Supabase.enc(id));
    }
}
