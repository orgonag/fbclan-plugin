package com.github.orgonag.fbclan.pb;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PbSeedServiceTest
{
    @Test
    public void testBuildSeedSubmissions()
    {
        Map<String, Double> stored = new LinkedHashMap<>();
        stored.put("zulrah", 52.8);
        stored.put("theatre of blood 4 players", 861.0);
        stored.put("broken", null);      // unreadable value → skipped
        stored.put("negative", -5.0);    // nonsense → skipped
        stored.put("", 10.0);            // empty key → skipped

        List<PbSubmission> subs = PbSeedService.buildSeedSubmissions(stored);
        assertEquals(2, subs.size());
        assertEquals("zulrah", subs.get(0).getBossKey());
        assertEquals(52.8, subs.get(0).getSeconds(), 0.001);
        assertEquals("seed", subs.get(0).getSource());
        assertEquals("theatre of blood 4 players", subs.get(1).getBossKey());
    }
}
