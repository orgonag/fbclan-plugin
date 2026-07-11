package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.ClanSession;
import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.pb.PbTrackingService;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Upload side of the dashboard stats: reads the collection-log varps and
 * combat-achievement varbits and submits improve-only counters through
 * MemberStatsService. Fired on verification success and on varb changes.
 */
public class StatsUploadCoordinator
{
    private final Client client;
    private final FinalBossConfig config;
    private final ClanSession session;
    private final ScheduledExecutorService executor;
    private final MemberStatsService memberStatsService;

    public StatsUploadCoordinator(Client client, FinalBossConfig config, ClanSession session,
        ScheduledExecutorService executor, MemberStatsService memberStatsService)
    {
        this.client = client;
        this.config = config;
        this.session = session;
        this.executor = executor;
        this.memberStatsService = memberStatsService;
    }

    // Reads the collection-log varps and CA varbits — both callers arrive
    // on the client thread (varb events natively; the verification path
    // hops via clientThread.invokeLater, since varp reads assert the
    // client thread under -ea). Submission itself goes to the executor.
    public void maybeSubmit()
    {
        String rsn = session.getRsn();
        if (!session.canUpload() || !config.enableStatsUpload()
            || !PbTrackingService.isStandardWorld(client.getWorldType()))
        {
            return;
        }
        int clObtained = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
        int clTotal = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
        int caPoints = client.getVarbitValue(VarbitID.CA_POINTS);
        if (clObtained <= 0 && caPoints <= 0)
        {
            // Nothing readable yet (fresh login, log data not loaded) —
            // don't schedule no-op submissions on every varb change.
            return;
        }
        if (!memberStatsService.shouldSubmit(clObtained, caPoints))
        {
            return;
        }
        String caTier = MemberStatsService.tierFor(caPoints, caTierThresholds());
        executor.submit(() -> memberStatsService.submit(rsn, clObtained, clTotal, caPoints, caTier));
    }

    // Tier cutoffs read live from the game (Dink pattern): re-scales
    // automatically when Jagex adds tasks. Zero-valued varbits (not yet
    // loaded) are skipped; an empty map yields a null tier.
    private TreeMap<Integer, String> caTierThresholds()
    {
        TreeMap<Integer, String> thresholds = new TreeMap<>();
        putThreshold(thresholds, VarbitID.CA_THRESHOLD_EASY, "Easy");
        putThreshold(thresholds, VarbitID.CA_THRESHOLD_MEDIUM, "Medium");
        putThreshold(thresholds, VarbitID.CA_THRESHOLD_HARD, "Hard");
        putThreshold(thresholds, VarbitID.CA_THRESHOLD_ELITE, "Elite");
        putThreshold(thresholds, VarbitID.CA_THRESHOLD_MASTER, "Master");
        putThreshold(thresholds, VarbitID.CA_THRESHOLD_GRANDMASTER, "Grandmaster");
        return thresholds;
    }

    private void putThreshold(TreeMap<Integer, String> map, int varbitId, String tier)
    {
        int value = client.getVarbitValue(varbitId);
        if (value > 0)
        {
            map.put(value, tier);
        }
    }
}
