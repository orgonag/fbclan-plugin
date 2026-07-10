package com.github.orgonag.fbclan.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DashboardServiceTest
{
    private static JsonArray rows(String json)
    {
        return new JsonParser().parse(json).getAsJsonArray();
    }

    @Test
    public void testParseClEntries()
    {
        List<ClEntry> list = DashboardService.parseCl(rows(
            "[{\"rsn\":\"Woox\",\"cl_obtained\":1204,\"cl_total\":1568}]"));
        assertEquals(1, list.size());
        assertEquals("Woox", list.get(0).getRsn());
        assertEquals(1204, list.get(0).getObtained());
        assertEquals(1568, list.get(0).getTotal());
    }

    @Test
    public void testParseCaEntries()
    {
        List<CaEntry> list = DashboardService.parseCa(rows(
            "[{\"rsn\":\"Woox\",\"ca_points\":2525,\"tier\":\"Grandmaster\"}]"));
        assertEquals(1, list.size());
        assertEquals(2525, list.get(0).getPoints());
        assertEquals("Grandmaster", list.get(0).getTier());
    }

    @Test
    public void testParseCaNullTierBecomesEmpty()
    {
        List<CaEntry> list = DashboardService.parseCa(rows(
            "[{\"rsn\":\"Fresh\",\"ca_points\":10,\"tier\":null}]"));
        assertEquals("", list.get(0).getTier());
    }

    @Test
    public void testParseGpWeek()
    {
        GpWeek gp = DashboardService.parseGpWeek(
            rows("[{\"total_gp\":1200000000,\"drop_count\":214}]"),
            rows("[{\"rsn\":\"Zezima\",\"gp\":312000000},{\"rsn\":\"B0aty\",\"gp\":201000000}]"));
        assertEquals(1200000000L, gp.getTotalGp());
        assertEquals(214, gp.getDropCount());
        assertEquals(2, gp.getTop().size());
        assertEquals("Zezima", gp.getTop().get(0).getRsn());
        assertEquals(312000000.0, gp.getTop().get(0).getValue(), 0.001);
    }

    @Test
    public void testParseGpWeekEmptyTotals()
    {
        GpWeek gp = DashboardService.parseGpWeek(rows("[]"), rows("[]"));
        assertEquals(0L, gp.getTotalGp());
        assertTrue(gp.getTop().isEmpty());
    }

    @Test
    public void testParseSkipsMalformed()
    {
        assertTrue(DashboardService.parseCl(rows("[{\"rsn\":null,\"cl_obtained\":5,\"cl_total\":10}]")).isEmpty());
        assertTrue(DashboardService.parseCa(rows("[{\"rsn\":\"X\",\"ca_points\":null}]")).isEmpty());
    }

    @Test
    public void testApplyWomCache()
    {
        DashboardService s = new DashboardService(null);
        s.applyWomCache(rows(
            "[{\"metric\":\"gains_overall_week\",\"updated_at\":\"2026-07-10T20:00:00+00:00\","
            + "\"payload\":[{\"player\":{\"displayName\":\"Zezima\"},\"data\":{\"gained\":14200000}}]},"
            + "{\"metric\":\"kc_zulrah\",\"updated_at\":\"2026-07-10T19:00:00+00:00\","
            + "\"payload\":[{\"player\":{\"displayName\":\"Woox\"},\"data\":{\"kills\":5591}}]},"
            + "{\"metric\":\"mystery_future_metric\",\"updated_at\":\"2026-07-10T21:00:00+00:00\",\"payload\":[]}]"));
        assertEquals(1, s.getXpWeek().size());
        assertEquals("Zezima", s.getXpWeek().get(0).getRsn());
        assertNull(s.getEhbWeek()); // no ehb row yet → null = not synced
        assertEquals(5591.0, s.getKcBoards().get("zulrah").get(0).getValue(), 0.001);
        assertEquals("2026-07-10T21:00:00+00:00", s.getWomSyncedAt());
    }

    @Test
    public void testApplyWomCacheEmptyKeepsPrevious()
    {
        DashboardService s = new DashboardService(null);
        s.applyWomCache(rows(
            "[{\"metric\":\"gains_ehb_week\",\"updated_at\":\"t\",\"payload\":[]}]"));
        assertNotNull(s.getEhbWeek());
        assertTrue(s.getEhbWeek().isEmpty()); // synced-but-empty ≠ never synced
        s.applyWomCache(rows("[]"));
        assertNotNull(s.getEhbWeek()); // empty refresh keeps previous
    }
}
