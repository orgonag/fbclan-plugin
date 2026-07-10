package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Uploads the member's collection-log count and combat-achievement
 * points to the clan dashboard via the improve-only submit_stats RPC.
 * The plugin reads varps/varbits on the client thread and hands plain
 * ints here; submission runs on the executor. Values only ever rise,
 * so re-submission happens only when a counter increases past the last
 * submitted value (cheap dedup against varb-change spam).
 */
@Slf4j
public class MemberStatsService
{
    private final OkHttpClient httpClient;

    // Last values successfully handed to submit(); EDT/client-thread
    // reads + executor writes are tolerable here because a stale read
    // only causes one redundant improve-only submission.
    private volatile int lastClObtained = -1;
    private volatile int lastCaPoints = -1;

    public MemberStatsService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public boolean shouldSubmit(int clObtained, int caPoints)
    {
        return clObtained > lastClObtained || caPoints > lastCaPoints;
    }

    public void recordSubmitted(int clObtained, int caPoints)
    {
        lastClObtained = Math.max(lastClObtained, clObtained);
        lastCaPoints = Math.max(lastCaPoints, caPoints);
    }

    // Runs on the executor.
    public void submit(String rsn, int clObtained, int clTotal, int caPoints, String caTier)
    {
        JsonObject payload = buildPayload(rsn, clObtained, clTotal, caPoints, caTier);
        if (payload.size() <= 1) // only p_rsn — nothing readable yet
        {
            return;
        }
        try
        {
            if (SupabaseClient.rpc(httpClient, "submit_stats", payload))
            {
                recordSubmitted(clObtained, caPoints);
                log.debug("Submitted member stats for {}", rsn);
            }
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Failed to submit member stats", e);
        }
    }

    // cl varps read 0/0 until the client loads collection data once;
    // CA points can be 0 for a fresh account (no tier). Absent fields
    // are omitted — the RPC treats them as "no update".
    static JsonObject buildPayload(String rsn, int clObtained, int clTotal, int caPoints, String caTier)
    {
        JsonObject p = new JsonObject();
        p.addProperty("p_rsn", rsn);
        if (clObtained > 0 && clTotal > 0)
        {
            p.addProperty("p_cl_obtained", clObtained);
            p.addProperty("p_cl_total", clTotal);
        }
        if (caPoints > 0)
        {
            p.addProperty("p_ca_points", caPoints);
            if (caTier != null)
            {
                p.addProperty("p_ca_tier", caTier);
            }
        }
        return p;
    }

    // Highest tier whose cumulative threshold is <= points. Thresholds
    // come from the live CA_THRESHOLD_* varbits (Dink pattern), so new
    // tasks re-scale without a plugin update. Null = below Easy.
    static String tierFor(int points, TreeMap<Integer, String> thresholds)
    {
        Map.Entry<Integer, String> e = thresholds.floorEntry(points);
        return e == null ? null : e.getValue();
    }
}
