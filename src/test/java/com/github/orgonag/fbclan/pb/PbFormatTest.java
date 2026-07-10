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
        assertEquals("Chambers Of Xeric", PbFormat.displayName("chambers of xeric"));
    }

    @Test
    public void testDisplayNameTeamSuffix()
    {
        assertEquals("Theatre Of Blood (4 players)", PbFormat.displayName("theatre of blood 4 players"));
        assertEquals("Chambers Of Xeric (Solo)", PbFormat.displayName("chambers of xeric solo"));
        assertEquals("Hallowed Sepulchre Floor 5", PbFormat.displayName("hallowed sepulchre floor 5"));
    }
}
