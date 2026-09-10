package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Uploads the member's collection-log count and combat-achievement
 * points through the improve-only submit_stats function. Varps/varbits
 * are read on the client thread (verification success and varb changes);
 * the submit runs on the executor. Values only ever rise, so a
 * resubmission happens only when a counter passes the last sent value.
 */
@Singleton
public class MemberStats
{
    private final Client client;
    private final FinalBossConfig config;
    private final Clan clan;
    private final Supabase db;
    private final ScheduledExecutorService executor;

    private volatile String acknowledged;
    private final java.util.concurrent.atomic.AtomicBoolean sending = new java.util.concurrent.atomic.AtomicBoolean();

    @Inject
    public MemberStats(Client client, FinalBossConfig config, Clan clan, Supabase db, ScheduledExecutorService executor)
    {
        this.client = client;
        this.config = config;
        this.clan = clan;
        this.db = db;
        this.executor = executor;
    }

    // Client thread (varp reads assert it under -ea).
    public void maybeSubmit()
    {
        Session session = clan.snapshot();
        if (!clan.canUpload() || !config.enableStatsUpload() || !clan.onStandardWorld())
        {
            return;
        }
        int clObtained = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
        int clTotal = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
        int caPoints = client.getVarbitValue(VarbitID.CA_POINTS);
        if ((clObtained <= 0 && caPoints <= 0))
        {
            return;
        }
        String tier = tierFor(caPoints, thresholds());
        String signature = session.getGeneration() + ":" + clObtained + ":" + clTotal + ":" + caPoints + ":" + tier;
        if (signature.equals(acknowledged) || !sending.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try
            {
                if (clan.current(session) && config.enableStatsUpload() && submit(session.getRsn(), clObtained, clTotal, caPoints, tier)
                    && clan.current(session)) acknowledged = signature;
            }
            finally { sending.set(false); }
        });
    }

    private boolean submit(String rsn, int clObtained, int clTotal, int caPoints, String tier)
    {
        // Fields the client can't read yet are omitted; the RPC treats an
        // absent field as "no update".
        JsonObject p = new JsonObject();
        p.addProperty("p_rsn", rsn);
        boolean hasCl = clObtained > 0 && clTotal >= clObtained;
        boolean hasCa = caPoints > 0;
        if (hasCl)
        {
            p.addProperty("p_cl_obtained", clObtained);
            p.addProperty("p_cl_total", clTotal);
        }
        if (hasCa)
        {
            p.addProperty("p_ca_points", caPoints);
            if (tier != null)
            {
                p.addProperty("p_ca_tier", tier);
            }
        }
        return (hasCl || hasCa) && db.rpc("fb_submit_stats", p);
    }

    // Tier cutoffs read live from the game (Dink pattern) so new tasks
    // re-scale without a plugin update. Null = below Easy.
    private static String tierFor(int points, TreeMap<Integer, String> thresholds)
    {
        Map.Entry<Integer, String> e = thresholds.floorEntry(points);
        return e == null ? null : e.getValue();
    }

    private TreeMap<Integer, String> thresholds()
    {
        TreeMap<Integer, String> t = new TreeMap<>();
        put(t, VarbitID.CA_THRESHOLD_EASY, "Easy");
        put(t, VarbitID.CA_THRESHOLD_MEDIUM, "Medium");
        put(t, VarbitID.CA_THRESHOLD_HARD, "Hard");
        put(t, VarbitID.CA_THRESHOLD_ELITE, "Elite");
        put(t, VarbitID.CA_THRESHOLD_MASTER, "Master");
        put(t, VarbitID.CA_THRESHOLD_GRANDMASTER, "Grandmaster");
        return t;
    }

    private void put(TreeMap<Integer, String> map, int varbit, String tier)
    {
        int value = client.getVarbitValue(varbit);
        if (value > 0)
        {
            map.put(value, tier);
        }
    }
}
