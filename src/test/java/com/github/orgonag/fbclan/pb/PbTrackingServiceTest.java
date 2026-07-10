package com.github.orgonag.fbclan.pb;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PbTrackingServiceTest
{
    private static final String ZULRAH_KC = "Your Zulrah kill count is: <col=ff0000>559</col>.";
    private static final String ZULRAH_NEW_PB = "Fight duration: <col=ff0000>0:52.80</col> (new personal best)";
    private static final String ZULRAH_OLD_PB = "Fight duration: <col=ff0000>1:02</col>. Personal best: 0:52.80";
    private static final String TOB_KC = "Your completed Theatre of Blood count is: <col=ff0000>10</col>.";
    private static final String TOB_NEW_PB = "Theatre of Blood completion time: <col=ff0000>14:21</col> (new personal best)";
    private static final String COX_KC = "Your completed Chambers of Xeric count is: <col=ff0000>57</col>.";
    private static final String COX_NEW_PB = "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>28:15.60</col> (new personal best)</col>";

    private PbTrackingService service(int tobSize, int toaSize)
    {
        return new PbTrackingService(() -> tobSize, () -> toaSize);
    }

    @Test
    public void testKcThenPbEmitsSingleLive()
    {
        PbTrackingService s = service(0, 0);
        assertTrue(s.onGameMessage(ZULRAH_KC, 100).isEmpty());
        List<PbSubmission> subs = s.onGameMessage(ZULRAH_NEW_PB, 100);
        assertEquals(1, subs.size());
        assertEquals("zulrah", subs.get(0).getBossKey());
        assertEquals(52.8, subs.get(0).getSeconds(), 0.001);
        assertEquals("live", subs.get(0).getSource());
    }

    @Test
    public void testKcThenRestatedPbEmitsSeed()
    {
        PbTrackingService s = service(0, 0);
        s.onGameMessage(ZULRAH_KC, 100);
        List<PbSubmission> subs = s.onGameMessage(ZULRAH_OLD_PB, 100);
        assertEquals(1, subs.size());
        assertEquals("seed", subs.get(0).getSource());
        assertEquals(52.8, subs.get(0).getSeconds(), 0.001);
    }

    @Test
    public void testPbThenKcEmitsOnKc()
    {
        PbTrackingService s = service(0, 0);
        assertTrue(s.onGameMessage(ZULRAH_NEW_PB, 100).isEmpty());
        List<PbSubmission> subs = s.onGameMessage(ZULRAH_KC, 100);
        assertEquals(1, subs.size());
        assertEquals("zulrah", subs.get(0).getBossKey());
        assertEquals("live", subs.get(0).getSource());
    }

    @Test
    public void testTobPbThenKcAddsVarbitTeamSize()
    {
        PbTrackingService s = service(4, 0);
        s.onGameMessage(TOB_NEW_PB, 100);
        List<PbSubmission> subs = s.onGameMessage(TOB_KC, 100);
        assertEquals(2, subs.size());
        assertEquals("theatre of blood", subs.get(0).getBossKey());
        assertEquals("theatre of blood 4 players", subs.get(1).getBossKey());
    }

    @Test
    public void testTobSoloUsesSoloSuffix()
    {
        PbTrackingService s = service(1, 0);
        s.onGameMessage(TOB_NEW_PB, 100);
        List<PbSubmission> subs = s.onGameMessage(TOB_KC, 100);
        assertEquals(2, subs.size());
        assertEquals("theatre of blood solo", subs.get(1).getBossKey());
    }

    @Test
    public void testCoxPbThenKcUsesMessageTeamSize()
    {
        PbTrackingService s = service(0, 0);
        s.onGameMessage(COX_NEW_PB, 100);
        List<PbSubmission> subs = s.onGameMessage(COX_KC, 100);
        assertEquals(2, subs.size());
        assertEquals("chambers of xeric", subs.get(0).getBossKey());
        assertEquals("chambers of xeric 3 players", subs.get(1).getBossKey());
        assertEquals(1695.6, subs.get(1).getSeconds(), 0.001);
    }

    @Test
    public void testKcInvalidatedOnLaterTick()
    {
        PbTrackingService s = service(0, 0);
        s.onGameMessage(ZULRAH_KC, 100);
        // Unrelated message on a later tick clears the remembered KC...
        s.onGameMessage("Welcome to Old School RuneScape.", 101);
        // ...so the PB line now has nothing to pair with and is buffered.
        assertTrue(s.onGameMessage(ZULRAH_NEW_PB, 101).isEmpty());
    }

    @Test
    public void testSepulchrePassesThroughWithoutPairing()
    {
        PbTrackingService s = service(0, 0);
        List<PbSubmission> subs = s.onGameMessage(
            "Floor 5 time: <col=ff0000>1:59.40</col> (new personal best)", 100);
        assertEquals(1, subs.size());
        assertEquals("hallowed sepulchre floor 5", subs.get(0).getBossKey());
    }

    @Test
    public void testUnrelatedMessagesEmitNothing()
    {
        PbTrackingService s = service(0, 0);
        assertTrue(s.onGameMessage("Zezima: gz on the pet!", 100).isEmpty());
    }

    @Test
    public void testStandardWorldCheck()
    {
        assertTrue(PbTrackingService.isStandardWorld(java.util.EnumSet.of(
            net.runelite.api.WorldType.MEMBERS)));
        assertFalse(PbTrackingService.isStandardWorld(java.util.EnumSet.of(
            net.runelite.api.WorldType.MEMBERS, net.runelite.api.WorldType.SEASONAL)));
        assertFalse(PbTrackingService.isStandardWorld(java.util.EnumSet.of(
            net.runelite.api.WorldType.DEADMAN)));
    }
}
