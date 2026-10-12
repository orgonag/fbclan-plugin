package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonObject;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Uploads the member's combat-achievement points and tier through the
 * improve-only fb_submit_stats function (the collection-log board comes
 * from the hiscores via Wise Old Man). Varbits are read on the client
 * thread (after verification, then every 30 min); the submit runs on the
 * executor. Sent once per login and again only when a value changes; the
 * server keeps the highest.
 */
@Singleton
public class MemberStats
{
    // CA tier cutoffs, read live from the game (Dink pattern) so new tasks
    // re-scale without a plugin update.
    private static final int[] TIER_VARBITS = {VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM,
        VarbitID.CA_THRESHOLD_HARD, VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER};
    private static final String[] TIERS = {"Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"};

    private final Client client;
    private final FinalBossConfig config;
    private final Clan clan;
    private final Supabase db;
    private final ScheduledExecutorService executor;

    private volatile String acknowledged;
    private final AtomicBoolean sending = new AtomicBoolean();

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
        int caPoints = client.getVarbitValue(VarbitID.CA_POINTS);
        if (caPoints <= 0)
        {
            return;
        }
        String tier = tier(caPoints);
        String signature = session.getGeneration() + ":" + caPoints + ":" + tier;
        if (signature.equals(acknowledged) || !sending.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try
            {
                if (clan.current(session) && config.enableStatsUpload() && submit(session.getRsn(), caPoints, tier)
                    && clan.current(session)) acknowledged = signature;
            }
            finally { sending.set(false); }
        });
    }

    private boolean submit(String rsn, int caPoints, String tier)
    {
        // The collection-log fields are never sent and a tier the client
        // can't read yet is omitted; the RPC treats an absent field as
        // "no update".
        JsonObject p = new JsonObject();
        p.addProperty("p_rsn", rsn);
        p.addProperty("p_ca_points", caPoints);
        if (tier != null)
        {
            p.addProperty("p_ca_tier", tier);
        }
        return db.rpc("fb_submit_stats", p);
    }

    // The highest tier whose cutoff the points reach; null = below Easy
    // (or cutoffs not loaded yet).
    private String tier(int points)
    {
        String tier = null;
        for (int i = 0; i < TIERS.length; i++)
        {
            int cutoff = client.getVarbitValue(TIER_VARBITS[i]);
            if (cutoff > 0 && points >= cutoff) tier = TIERS[i];
        }
        return tier;
    }
}
