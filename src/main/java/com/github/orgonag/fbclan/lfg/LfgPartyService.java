package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Supabase access for hosted parties (lfg_parties), their applicants
 * (lfg_applicants) and formed-party snapshots (lfg_formed_parties). The
 * anon key has full CRUD on the live tables (insert/select/delete on the
 * snapshots), membership is verified before anything is written, and
 * rows are scoped by RSN client-side. All calls are blocking network
 * I/O — run them on the executor.
 */
@Slf4j
public class LfgPartyService
{
    public static final int MAX_DESCRIPTION_LENGTH = 120;
    public static final int MIN_CAPACITY = 2;
    public static final int MAX_CAPACITY = 100;
    public static final int MAX_INVOCATION = 600;

    // A party lives while its host's client keeps it alive: the host
    // heartbeats updated_at every HEARTBEAT_MINUTES, and the server-side
    // cleanup job deletes rows once updated_at + ttl_minutes has passed.
    // So a party outlives a crashed/closed client by at most ~TTL.
    public static final int PARTY_TTL_MINUTES = 30;
    public static final int HEARTBEAT_MINUTES = 5;

    private static final String SELECT = "select=*,lfg_applicants(*)&order=created_at.desc";

    private final OkHttpClient httpClient;

    public LfgPartyService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public List<LfgParty> getParties()
    {
        List<LfgParty> parties = new ArrayList<>();
        try
        {
            JsonArray rows = SupabaseClient.get(httpClient, "lfg_parties", SELECT);
            for (JsonElement el : rows)
            {
                if (!el.isJsonObject())
                {
                    continue;
                }
                LfgParty p = LfgParty.fromRow(el.getAsJsonObject());
                if (p != null)
                {
                    parties.add(p);
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch LFG parties", e);
        }
        return parties;
    }

    // Create or edit the caller's party. One party per host: the upsert is
    // keyed on host_rsn, so editing simply overwrites the same row.
    public boolean upsertParty(LfgParty party)
    {
        try
        {
            return SupabaseClient.upsert(httpClient, "lfg_parties", party.toJson(), "host_rsn");
        }
        catch (IOException e)
        {
            log.warn("Failed to save LFG party", e);
            return false;
        }
    }

    // Keep-alive: bumps updated_at only.
    public boolean heartbeat(String hostRsn)
    {
        JsonObject data = new JsonObject();
        data.addProperty("updated_at", Instant.now().toString());
        try
        {
            return SupabaseClient.update(httpClient, "lfg_parties", "host_rsn=eq." + enc(hostRsn), data);
        }
        catch (IOException e)
        {
            log.warn("Failed to heartbeat LFG party", e);
            return false;
        }
    }

    // Applicants go with it (ON DELETE CASCADE).
    public boolean disband(String hostRsn)
    {
        try
        {
            return SupabaseClient.delete(httpClient, "lfg_parties", "host_rsn=eq." + enc(hostRsn));
        }
        catch (IOException e)
        {
            log.warn("Failed to disband LFG party", e);
            return false;
        }
    }

    // A player is in at most one party at a time: applying elsewhere
    // withdraws every existing application first.
    // kc / kcSource are the applicant's own kill count for the party's
    // activity (see LfgLocalKillcounts); both null when unknown.
    public boolean apply(String partyId, String rsn, LfgRole role, boolean learner,
                         Integer kc, LfgApplicant.KcSource kcSource)
    {
        if (!withdrawAll(rsn))
        {
            return false;
        }
        JsonObject data = new JsonObject();
        data.addProperty("party_id", partyId);
        data.addProperty("rsn", rsn);
        if (role == null)
        {
            data.add("role", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("role", role.getKey());
        }
        data.addProperty("learner", learner);
        data.addProperty("status", LfgApplicant.Status.PENDING.name());
        if (kc == null || kcSource == null)
        {
            data.add("kc", JsonNull.INSTANCE);
            data.add("kc_source", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("kc", Math.max(0, Math.min(100_000, kc)));
            data.addProperty("kc_source", kcSource.name());
        }
        try
        {
            return SupabaseClient.insert(httpClient, "lfg_applicants", data);
        }
        catch (IOException e)
        {
            log.warn("Failed to apply to LFG party", e);
            return false;
        }
    }

    public boolean withdraw(String partyId, String rsn)
    {
        return deleteApplicant(partyId, rsn);
    }

    public boolean withdrawAll(String rsn)
    {
        try
        {
            return SupabaseClient.delete(httpClient, "lfg_applicants", "rsn=eq." + enc(rsn));
        }
        catch (IOException e)
        {
            log.warn("Failed to withdraw LFG applications", e);
            return false;
        }
    }

    // Host-side: accept or decline a pending applicant.
    public boolean setStatus(String partyId, String rsn, LfgApplicant.Status status)
    {
        JsonObject data = new JsonObject();
        data.addProperty("status", status.name());
        data.addProperty("updated_at", Instant.now().toString());
        try
        {
            return SupabaseClient.update(httpClient, "lfg_applicants",
                "party_id=eq." + enc(partyId) + "&rsn=eq." + enc(rsn), data);
        }
        catch (IOException e)
        {
            log.warn("Failed to update LFG applicant", e);
            return false;
        }
    }

    // Host-side kick (or a declined applicant tidying up).
    public boolean removeApplicant(String partyId, String rsn)
    {
        return deleteApplicant(partyId, rsn);
    }

    public enum AddResult
    {
        OK,
        // The name is already an applicant/member somewhere (unique index),
        // or the row was otherwise rejected by a server-side rule.
        REJECTED,
        FAILED
    }

    // Host-side: seat a player who isn't on LFG (a buddy) directly as an
    // accepted member. Server rules still apply: one party per name, not
    // the host themselves, never past capacity.
    public AddResult addMember(String partyId, String rsn, LfgRole role)
    {
        JsonObject data = new JsonObject();
        data.addProperty("party_id", partyId);
        data.addProperty("rsn", rsn.trim());
        if (role == null)
        {
            data.add("role", JsonNull.INSTANCE);
        }
        else
        {
            data.addProperty("role", role.getKey());
        }
        data.addProperty("learner", false);
        data.addProperty("status", LfgApplicant.Status.ACCEPTED.name());
        data.addProperty("added_by_host", true);
        try
        {
            int code = SupabaseClient.insertForCode(httpClient, "lfg_applicants", data);
            if (code >= 200 && code < 300)
            {
                return AddResult.OK;
            }
            return code == 409 || code == 400 ? AddResult.REJECTED : AddResult.FAILED;
        }
        catch (IOException e)
        {
            log.warn("Failed to add LFG party member", e);
            return AddResult.FAILED;
        }
    }

    // ------------------------------------------------------------ formed

    private static final String FORMED_SELECT = "select=*&order=formed_at.desc";

    public List<LfgFormedParty> getFormed()
    {
        List<LfgFormedParty> out = new ArrayList<>();
        try
        {
            JsonArray rows = SupabaseClient.get(httpClient, "lfg_formed_parties", FORMED_SELECT);
            for (JsonElement el : rows)
            {
                if (!el.isJsonObject())
                {
                    continue;
                }
                LfgFormedParty f = LfgFormedParty.fromRow(el.getAsJsonObject());
                if (f != null)
                {
                    out.add(f);
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to fetch formed LFG parties", e);
        }
        return out;
    }

    // Host-side, when the party fills: snapshot it, then delete the live
    // row (applicants cascade). Snapshot first so a failure leaves the
    // party open rather than silently lost.
    public boolean form(LfgParty party)
    {
        try
        {
            if (!SupabaseClient.insert(httpClient, "lfg_formed_parties", LfgFormedParty.from(party).toJson()))
            {
                return false;
            }
            return SupabaseClient.delete(httpClient, "lfg_parties", "id=eq." + enc(party.getId()));
        }
        catch (IOException e)
        {
            log.warn("Failed to form LFG party", e);
            return false;
        }
    }

    public boolean deleteFormed(String id)
    {
        try
        {
            return SupabaseClient.delete(httpClient, "lfg_formed_parties", "id=eq." + enc(id));
        }
        catch (IOException e)
        {
            log.warn("Failed to remove formed LFG party", e);
            return false;
        }
    }

    private boolean deleteApplicant(String partyId, String rsn)
    {
        try
        {
            return SupabaseClient.delete(httpClient, "lfg_applicants",
                "party_id=eq." + enc(partyId) + "&rsn=eq." + enc(rsn));
        }
        catch (IOException e)
        {
            log.warn("Failed to remove LFG applicant", e);
            return false;
        }
    }

    private static String enc(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
