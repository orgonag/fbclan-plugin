package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
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

    private volatile int lastCl = -1;
    private volatile int lastCa = -1;

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
        String rsn = clan.rsn();
        if (!clan.canUpload() || !config.enableStatsUpload() || !clan.onStandardWorld())
        {
            return;
        }
        int clObtained = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
        int clTotal = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
        int caPoints = client.getVarbitValue(VarbitID.CA_POINTS);
        if ((clObtained <= 0 && caPoints <= 0) || (clObtained <= lastCl && caPoints <= lastCa))
        {
            return;
        }
        String tier = tierFor(caPoints, thresholds());
        executor.submit(() -> submit(rsn, clObtained, clTotal, caPoints, tier));
    }

    private void submit(String rsn, int clObtained, int clTotal, int caPoints, String tier)
    {
        // Fields the client can't read yet are omitted; the RPC treats an
        // absent field as "no update".
        JsonObject p = new JsonObject();
        p.addProperty("p_rsn", rsn);
        boolean hasCl = clObtained > 0 && clTotal > 0;
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
        if ((!hasCl && !hasCa) || !db.rpc("submit_stats", p))
        {
            return;
        }
        // Record only what was actually sent, so an omitted pair retries
        // once it becomes readable.
        if (hasCl)
        {
            lastCl = Math.max(lastCl, clObtained);
        }
        if (hasCa)
        {
            lastCa = Math.max(lastCa, caPoints);
        }
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
