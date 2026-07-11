package com.github.orgonag.fbclan.wom;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class WomStatsParserTest
{
    private static JsonArray rows(String json)
    {
        return new JsonParser().parse(json).getAsJsonArray();
    }

    @Test
    public void testParseGains()
    {
        List<WomEntry> list = WomStatsParser.parseGains(rows(
            "[{\"player\":{\"displayName\":\"Zezima\"},\"data\":{\"gained\":14200000}},"
            + "{\"player\":{\"displayName\":\"B0aty\"},\"data\":{\"gained\":9800000}}]"));
        assertEquals(2, list.size());
        assertEquals("Zezima", list.get(0).getRsn());
        assertEquals(14200000.0, list.get(0).getValue(), 0.001);
    }

    @Test
    public void testParseGainsSkipsMalformedRows()
    {
        List<WomEntry> list = WomStatsParser.parseGains(rows(
            "[{\"player\":null,\"data\":{\"gained\":5}},"
            + "{\"player\":{\"displayName\":\"Ok\"},\"data\":{\"gained\":7}}]"));
        assertEquals(1, list.size());
        assertEquals("Ok", list.get(0).getRsn());
    }

    @Test
    public void testParseHiscores()
    {
        List<WomEntry> list = WomStatsParser.parseHiscores(rows(
            "[{\"player\":{\"displayName\":\"AaronPVM\"},\"data\":{\"type\":\"boss\",\"rank\":975,\"kills\":15220}}]"));
        assertEquals(1, list.size());
        assertEquals("AaronPVM", list.get(0).getRsn());
        assertEquals(15220.0, list.get(0).getValue(), 0.001);
    }

    @Test
    public void testParseHiscoresSkipsMissingKills()
    {
        assertTrue(WomStatsParser.parseHiscores(rows(
            "[{\"player\":{\"displayName\":\"NoKc\"},\"data\":{\"type\":\"boss\",\"rank\":1}}]")).isEmpty());
    }

    @Test
    public void testBossDisplayNames()
    {
        assertEquals("Zulrah", WomStatsParser.bossDisplayName("zulrah"));
        assertEquals("Chambers Of Xeric Challenge Mode",
            WomStatsParser.bossDisplayName("chambers_of_xeric_challenge_mode"));
        assertEquals("Kree'arra", WomStatsParser.bossDisplayName("kreearra"));
        assertEquals("TzKal-Zuk", WomStatsParser.bossDisplayName("tzkal_zuk"));
        assertEquals("Phosani's Nightmare", WomStatsParser.bossDisplayName("phosanis_nightmare"));
    }

    @Test
    public void testBossSlugListSizeAndOrder()
    {
        // 70 boss slugs verified against wise-old-man's metric enum
        // (2026-07-10); the research prose miscounted 69 — the list wins.
        assertEquals(70, WomStatsParser.BOSS_SLUGS.size());
        // Alphabetical by slug (stable UI order)
        assertEquals("abyssal_sire", WomStatsParser.BOSS_SLUGS.get(0));
        assertEquals("zulrah", WomStatsParser.BOSS_SLUGS.get(WomStatsParser.BOSS_SLUGS.size() - 1));
    }
}
