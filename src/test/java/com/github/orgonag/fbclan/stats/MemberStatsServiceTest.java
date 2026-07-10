package com.github.orgonag.fbclan.stats;

import com.google.gson.JsonObject;
import java.util.TreeMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class MemberStatsServiceTest
{
    @Test
    public void testBuildPayload()
    {
        JsonObject p = MemberStatsService.buildPayload("Woox", 967, 1568, 1104, "Elite");
        assertEquals("Woox", p.get("p_rsn").getAsString());
        assertEquals(967, p.get("p_cl_obtained").getAsInt());
        assertEquals(1568, p.get("p_cl_total").getAsInt());
        assertEquals(1104, p.get("p_ca_points").getAsInt());
        assertEquals("Elite", p.get("p_ca_tier").getAsString());
    }

    @Test
    public void testBuildPayloadOmitsUnknownFields()
    {
        // cl varps read 0/0 before the log data loads; CA can be missing.
        JsonObject p = MemberStatsService.buildPayload("Woox", 0, 0, 350, "Medium");
        assertFalse(p.has("p_cl_obtained"));
        assertFalse(p.has("p_cl_total"));
        assertTrue(p.has("p_ca_points"));

        JsonObject q = MemberStatsService.buildPayload("Woox", 967, 1568, 0, null);
        assertTrue(q.has("p_cl_obtained"));
        assertFalse(q.has("p_ca_points"));
        assertFalse(q.has("p_ca_tier"));
    }

    @Test
    public void testTierForPoints()
    {
        // Thresholds are read live from varbits at runtime; the map below
        // uses the currently-known cumulative values purely as fixtures.
        TreeMap<Integer, String> t = new TreeMap<>();
        t.put(33, "Easy");
        t.put(115, "Medium");
        t.put(304, "Hard");
        t.put(820, "Elite");
        t.put(1465, "Master");
        t.put(2005, "Grandmaster");
        assertNull(MemberStatsService.tierFor(0, t));
        assertNull(MemberStatsService.tierFor(32, t));
        assertEquals("Easy", MemberStatsService.tierFor(33, t));
        assertEquals("Easy", MemberStatsService.tierFor(114, t));
        assertEquals("Elite", MemberStatsService.tierFor(1104, t));
        assertEquals("Grandmaster", MemberStatsService.tierFor(2525, t));
    }

    @Test
    public void testShouldResubmitOnlyOnIncrease()
    {
        MemberStatsService s = new MemberStatsService(null);
        assertTrue(s.shouldSubmit(967, 1104));   // first time
        s.recordSubmitted(967, 1104);
        assertFalse(s.shouldSubmit(967, 1104));  // unchanged
        assertFalse(s.shouldSubmit(960, 1100));  // decrease (impossible/rollback) — ignore
        assertTrue(s.shouldSubmit(968, 1104));   // cl rose
        assertTrue(s.shouldSubmit(967, 1110));   // ca rose
    }
}
