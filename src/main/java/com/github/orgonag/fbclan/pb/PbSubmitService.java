package com.github.orgonag.fbclan.pb;

import com.github.orgonag.fbclan.util.SupabaseClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

/**
 * Ships PB submissions to the submit_pbs Postgres function, which only
 * accepts a time that beats the member's existing one (worse/equal times
 * are silent no-ops). Failure is logged and dropped — the next kill or
 * the next session's seed self-heals.
 */
@Slf4j
public class PbSubmitService
{
    // submit_pbs rejects arrays over 300 entries; chunk well below that so
    // one oversized seed can't fail wholesale.
    static final int MAX_BATCH = 250;

    private final OkHttpClient httpClient;

    public PbSubmitService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    // Runs on the executor.
    public void submit(String rsn, List<PbSubmission> submissions)
    {
        if (submissions.isEmpty())
        {
            return;
        }
        for (List<PbSubmission> chunk : partition(submissions))
        {
            try
            {
                boolean ok = SupabaseClient.rpc(httpClient, "submit_pbs", buildPayload(rsn, chunk));
                if (ok)
                {
                    log.debug("Submitted {} PB(s) for {}", chunk.size(), rsn);
                }
            }
            catch (IOException | RuntimeException e)
            {
                log.warn("Failed to submit PB batch", e);
            }
        }
    }

    static List<List<PbSubmission>> partition(List<PbSubmission> submissions)
    {
        List<List<PbSubmission>> chunks = new ArrayList<>();
        for (int start = 0; start < submissions.size(); start += MAX_BATCH)
        {
            chunks.add(submissions.subList(start, Math.min(start + MAX_BATCH, submissions.size())));
        }
        return chunks;
    }

    static JsonObject buildPayload(String rsn, List<PbSubmission> submissions)
    {
        JsonArray entries = new JsonArray();
        for (PbSubmission s : submissions)
        {
            JsonObject e = new JsonObject();
            e.addProperty("boss_key", s.getBossKey());
            e.addProperty("seconds", s.getSeconds());
            e.addProperty("source", s.getSource());
            entries.add(e);
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("p_rsn", rsn);
        payload.add("p_entries", entries);
        return payload;
    }
}
