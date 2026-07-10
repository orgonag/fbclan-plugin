package com.github.orgonag.fbclan.pb;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LeaderboardServiceTest
{
    private static JsonArray rows(String json)
    {
        return new JsonParser().parse(json).getAsJsonArray();
    }

    @Test
    public void testParseEntriesEmpty()
    {
        assertTrue(LeaderboardService.parseEntries(rows("[]")).isEmpty());
    }

    @Test
    public void testParseEntries()
    {
        List<PbEntry> entries = LeaderboardService.parseEntries(rows(
            "[{\"rsn\":\"Woox\",\"boss_key\":\"zulrah\",\"seconds\":52.8,\"achieved_at\":\"2026-07-08T10:00:00+00:00\",\"rank\":1},"
            + "{\"rsn\":\"Zezima\",\"boss_key\":\"zulrah\",\"seconds\":55.2,\"achieved_at\":\"2026-07-01T10:00:00+00:00\",\"rank\":2}]"));
        assertEquals(2, entries.size());
        assertEquals("Woox", entries.get(0).getRsn());
        assertEquals("zulrah", entries.get(0).getBossKey());
        assertEquals(52.8, entries.get(0).getSeconds(), 0.001);
        assertEquals(1, entries.get(0).getRank());
        assertEquals("2026-07-08T10:00:00+00:00", entries.get(0).getAchievedAt());
    }

    @Test
    public void testParseEntriesSkipsMalformedRows()
    {
        List<PbEntry> entries = LeaderboardService.parseEntries(rows(
            "[{\"rsn\":null,\"boss_key\":\"zulrah\",\"seconds\":52.8,\"rank\":1},"
            + "{\"rsn\":\"Ok\",\"boss_key\":\"vorkath\",\"seconds\":64.2,\"achieved_at\":\"2026-07-08T10:00:00+00:00\",\"rank\":1}]"));
        assertEquals(1, entries.size());
        assertEquals("Ok", entries.get(0).getRsn());
    }
}
