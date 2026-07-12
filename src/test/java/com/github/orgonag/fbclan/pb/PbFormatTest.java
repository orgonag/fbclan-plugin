package com.github.orgonag.fbclan.pb;

import org.junit.Test;
import static org.junit.Assert.*;

public class PbFormatTest
{
    @Test
    public void testFormatSecondsWhole()
    {
        assertEquals("0:45", PbFormat.formatSeconds(45.0));
        assertEquals("14:21", PbFormat.formatSeconds(861.0));
        assertEquals("1:02:03", PbFormat.formatSeconds(3723.0));
    }

    @Test
    public void testFormatSecondsPrecise()
    {
        assertEquals("0:52.80", PbFormat.formatSeconds(52.8));
        assertEquals("28:15.60", PbFormat.formatSeconds(1695.6));
    }

    @Test
    public void testDisplayNamePlain()
    {
        assertEquals("Zulrah", PbFormat.displayName("zulrah"));
        assertEquals("Hallowed Sepulchre Floor 5", PbFormat.displayName("hallowed sepulchre floor 5"));
    }

    @Test
    public void testDisplayNameRaidAbbreviations()
    {
        assertEquals("COX", PbFormat.displayName("chambers of xeric"));
        assertEquals("CM", PbFormat.displayName("chambers of xeric challenge mode"));
        assertEquals("TOB", PbFormat.displayName("theatre of blood"));
        assertEquals("HMT", PbFormat.displayName("theatre of blood hard mode"));
        assertEquals("TOB Entry Mode", PbFormat.displayName("theatre of blood entry mode"));
        assertEquals("TOA", PbFormat.displayName("tombs of amascut"));
        assertEquals("TOA Expert", PbFormat.displayName("tombs of amascut expert mode"));
        assertEquals("TOA Entry Mode", PbFormat.displayName("tombs of amascut entry mode"));
    }

    @Test
    public void testDisplayNameTeamSuffix()
    {
        assertEquals("TOB (4 players)", PbFormat.displayName("theatre of blood 4 players"));
        assertEquals("COX (Solo)", PbFormat.displayName("chambers of xeric solo"));
        assertEquals("CM (11-15 players)", PbFormat.displayName("chambers of xeric challenge mode 11-15 players"));
    }
}
