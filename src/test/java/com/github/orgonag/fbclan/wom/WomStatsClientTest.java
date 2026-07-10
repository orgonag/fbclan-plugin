package com.github.orgonag.fbclan.wom;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class WomStatsClientTest
{
    private static JsonArray rows(String json)
    {
        return new JsonParser().parse(json).getAsJsonArray();
    }

    @Test
    public void testParseGains()
    {
        List<WomEntry> list = WomStatsClient.parseGains(rows(
            "[{\"player\":{\"displayName\":\"Zezima\"},\"data\":{\"gained\":14200000}},"
            + "{\"player\":{\"displayName\":\"B0aty\"},\"data\":{\"gained\":9800000}}]"));
        assertEquals(2, list.size());
        assertEquals("Zezima", list.get(0).getRsn());
        assertEquals(14200000.0, list.get(0).getValue(), 0.001);
    }

    @Test
    public void testParseGainsSkipsMalformedRows()
    {
        List<WomEntry> list = WomStatsClient.parseGains(rows(
            "[{\"player\":null,\"data\":{\"gained\":5}},"
            + "{\"player\":{\"displayName\":\"Ok\"},\"data\":{\"gained\":7}}]"));
        assertEquals(1, list.size());
        assertEquals("Ok", list.get(0).getRsn());
    }

    @Test
    public void testParseHiscores()
    {
        List<WomEntry> list = WomStatsClient.parseHiscores(rows(
            "[{\"player\":{\"displayName\":\"AaronPVM\"},\"data\":{\"type\":\"boss\",\"rank\":975,\"kills\":15220}}]"));
        assertEquals(1, list.size());
        assertEquals("AaronPVM", list.get(0).getRsn());
        assertEquals(15220.0, list.get(0).getValue(), 0.001);
    }

    @Test
    public void testParseHiscoresSkipsMissingKills()
    {
        assertTrue(WomStatsClient.parseHiscores(rows(
            "[{\"player\":{\"displayName\":\"NoKc\"},\"data\":{\"type\":\"boss\",\"rank\":1}}]")).isEmpty());
    }

    @Test
    public void testBossDisplayNames()
    {
        assertEquals("Zulrah", WomStatsClient.bossDisplayName("zulrah"));
        assertEquals("Chambers Of Xeric Challenge Mode",
            WomStatsClient.bossDisplayName("chambers_of_xeric_challenge_mode"));
        assertEquals("Kree'arra", WomStatsClient.bossDisplayName("kreearra"));
        assertEquals("TzKal-Zuk", WomStatsClient.bossDisplayName("tzkal_zuk"));
        assertEquals("Phosani's Nightmare", WomStatsClient.bossDisplayName("phosanis_nightmare"));
    }

    @Test
    public void testBossSlugListSizeAndOrder()
    {
        // 70 boss slugs verified against wise-old-man's metric enum
        // (2026-07-10); the research prose miscounted 69 — the list wins.
        assertEquals(70, WomStatsClient.BOSS_SLUGS.size());
        // Alphabetical by slug (stable UI order)
        assertEquals("abyssal_sire", WomStatsClient.BOSS_SLUGS.get(0));
        assertEquals("zulrah", WomStatsClient.BOSS_SLUGS.get(WomStatsClient.BOSS_SLUGS.size() - 1));
    }

    @Test
    public void testCacheReturnsWithinTtl()
    {
        long[] now = {0L};
        WomStatsClient client = new WomStatsClient(null, () -> now[0]);
        client.cachePut("k", java.util.Collections.singletonList(new WomEntry("A", 1)));
        assertNotNull(client.cacheGet("k"));
        now[0] = WomStatsClient.CACHE_TTL_MS - 1;
        assertNotNull(client.cacheGet("k"));
        now[0] = WomStatsClient.CACHE_TTL_MS + 1;
        assertNull(client.cacheGet("k"));
    }
}
