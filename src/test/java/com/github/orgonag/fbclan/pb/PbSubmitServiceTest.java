package com.github.orgonag.fbclan.pb;

import com.google.gson.JsonObject;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class PbSubmitServiceTest
{
    @Test
    public void testBuildPayload()
    {
        JsonObject payload = PbSubmitService.buildPayload("Woox", Arrays.asList(
            new PbSubmission("zulrah", 52.8, "live"),
            new PbSubmission("vorkath", 64.2, "seed")));

        assertEquals("Woox", payload.get("p_rsn").getAsString());
        assertEquals(2, payload.getAsJsonArray("p_entries").size());
        JsonObject first = payload.getAsJsonArray("p_entries").get(0).getAsJsonObject();
        assertEquals("zulrah", first.get("boss_key").getAsString());
        assertEquals(52.8, first.get("seconds").getAsDouble(), 0.001);
        assertEquals("live", first.get("source").getAsString());
    }

    @Test
    public void testPartitionSplitsAtMaxBatch()
    {
        java.util.List<PbSubmission> subs = new java.util.ArrayList<>();
        for (int i = 0; i < PbSubmitService.MAX_BATCH * 2 + 1; i++)
        {
            subs.add(new PbSubmission("boss " + i, i + 1.0, "seed"));
        }
        java.util.List<java.util.List<PbSubmission>> chunks = PbSubmitService.partition(subs);
        assertEquals(3, chunks.size());
        assertEquals(PbSubmitService.MAX_BATCH, chunks.get(0).size());
        assertEquals(PbSubmitService.MAX_BATCH, chunks.get(1).size());
        assertEquals(1, chunks.get(2).size());
        // Order preserved across chunk boundaries
        assertEquals("boss 0", chunks.get(0).get(0).getBossKey());
        assertEquals("boss " + PbSubmitService.MAX_BATCH, chunks.get(1).get(0).getBossKey());
    }
}
