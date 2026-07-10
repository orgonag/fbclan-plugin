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
}
