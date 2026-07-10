package com.github.orgonag.fbclan.pb;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PbMessageParserTest
{
    @Test
    public void testParseKillCountSoloBoss()
    {
        assertEquals("zulrah", PbMessageParser.parseKillCount(
            "Your Zulrah kill count is: <col=ff0000>559</col>.").orElse(null));
    }

    @Test
    public void testParseKillCountRaid()
    {
        assertEquals("chambers of xeric", PbMessageParser.parseKillCount(
            "Your completed Chambers of Xeric count is: <col=ff0000>57</col>.").orElse(null));
    }

    @Test
    public void testParseKillCountRenamesBarrowsAndStripsColons()
    {
        assertEquals("barrows chests", PbMessageParser.parseKillCount(
            "Your Barrows chest count is: <col=ff0000>100</col>.").orElse(null));
        assertEquals("theatre of blood hard mode", PbMessageParser.parseKillCount(
            "Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>3</col>.").orElse(null));
    }

    @Test
    public void testParseKillCountNonKcMessage()
    {
        assertFalse(PbMessageParser.parseKillCount("Welcome to Old School RuneScape.").isPresent());
    }

    @Test
    public void testParseKillCountIgnoresPersonalCountRows()
    {
        // pre and post both empty (e.g. Colosseum Glory): core ignores
        // these for PBs; a buffered PB must not pair with them.
        assertFalse(PbMessageParser.parseKillCount(
            "Your Glory is: <col=ff0000>25,000</col>.").isPresent());
    }

    @Test
    public void testParseDurationNewPb()
    {
        PbMessageParser.Duration d = PbMessageParser.parseDuration(
            "Fight duration: <col=ff0000>0:52.80</col> (new personal best)").orElse(null);
        assertNotNull(d);
        assertEquals(52.8, d.getSeconds(), 0.001);
        assertTrue(d.isNewPb());
        assertNull(d.getTeamSize());
    }

    @Test
    public void testParseDurationExistingPb()
    {
        PbMessageParser.Duration d = PbMessageParser.parseDuration(
            "Fight duration: <col=ff0000>1:02</col>. Personal best: 0:52.80").orElse(null);
        assertNotNull(d);
        assertEquals(52.8, d.getSeconds(), 0.001);
        assertFalse(d.isNewPb());
    }

    @Test
    public void testParseDurationImpreciseTime()
    {
        PbMessageParser.Duration d = PbMessageParser.parseDuration(
            "Challenge duration: <col=ff0000>7:34</col> (new personal best)").orElse(null);
        assertNotNull(d);
        assertEquals(454.0, d.getSeconds(), 0.001);
    }

    @Test
    public void testParseDurationTotalCompletionTimeExcluded()
    {
        // ToA/ToB "total completion time" lines must NOT match (negative lookbehind).
        assertFalse(PbMessageParser.parseDuration(
            "Total completion time: <col=ff0000>25:33</col> (new personal best)").isPresent());
    }

    @Test
    public void testParseDurationCoxRaidWithTeamSize()
    {
        PbMessageParser.Duration d = PbMessageParser.parseDuration(
            "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>28:15.60</col> (new personal best)</col>").orElse(null);
        assertNotNull(d);
        assertEquals(1695.6, d.getSeconds(), 0.001);
        assertTrue(d.isNewPb());
        assertEquals("3 players", d.getTeamSize());
    }

    @Test
    public void testParseDurationCoxRaidExistingPb()
    {
        PbMessageParser.Duration d = PbMessageParser.parseDuration(
            "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>31:00</col> Personal best: </col><col=ff0000>28:15</col>").orElse(null);
        assertNotNull(d);
        assertEquals(1695.0, d.getSeconds(), 0.001);
        assertFalse(d.isNewPb());
        assertEquals("Solo", d.getTeamSize());
    }

    @Test
    public void testParseSepulchreFloorAndOverall()
    {
        List<PbSubmission> subs = PbMessageParser.parseSepulchre(
            "Floor 5 time: <col=ff0000>1:59.40</col> (new personal best)<br>Overall time: <col=ff0000>5:30.60</col>. Personal best: 5:12.00");
        assertEquals(2, subs.size());
        assertEquals("hallowed sepulchre floor 5", subs.get(0).getBossKey());
        assertEquals(119.4, subs.get(0).getSeconds(), 0.001);
        assertEquals("live", subs.get(0).getSource());
        assertEquals("hallowed sepulchre", subs.get(1).getBossKey());
        assertEquals(312.0, subs.get(1).getSeconds(), 0.001);
        assertEquals("seed", subs.get(1).getSource());
    }

    @Test
    public void testParseSepulchreFloorOnly()
    {
        List<PbSubmission> subs = PbMessageParser.parseSepulchre(
            "Floor 1 time: <col=ff0000>1:05</col>. Personal best: 0:59.40");
        assertEquals(1, subs.size());
        assertEquals("hallowed sepulchre floor 1", subs.get(0).getBossKey());
        assertEquals(59.4, subs.get(0).getSeconds(), 0.001);
        assertEquals("seed", subs.get(0).getSource());
    }

    @Test
    public void testTimeStringToSeconds()
    {
        assertEquals(45.0, PbMessageParser.timeStringToSeconds("0:45"), 0.001);
        assertEquals(45.6, PbMessageParser.timeStringToSeconds("0:45.60"), 0.001);
        assertEquals(3723.0, PbMessageParser.timeStringToSeconds("1:02:03"), 0.001);
    }
}
