package com.github.orgonag.fbclan.pbs;

import org.junit.Test;
import static org.junit.Assert.*;

public class PbCorrelatorTest
{
    private static final String KC = "Your Vorkath kill count is: <col=ff0000>12</col>";
    private static final String PB = "Fight duration: <col=ff0000>1:12.60</col> (new personal best)";
    @Test public void pairsBothOrdersAndConsumes()
    {
        for (boolean reverse : new boolean[]{false, true})
        {
            PbCorrelator c = new PbCorrelator();
            assertTrue(c.accept(reverse ? PB : KC, 1, b -> null).isEmpty());
            java.util.List<PbParser.Submission> result = c.accept(reverse ? KC : PB, 1, b -> null);
            assertEquals(1, result.size());
            assertEquals("vorkath", result.get(0).getBossKey());
            assertEquals(72.6, result.get(0).getSeconds(), .001);
            assertTrue(c.accept(PB, 1, b -> null).isEmpty());
        }
    }
    @Test public void neitherHalfCanLeakIntoNextTick()
    {
        PbCorrelator c = new PbCorrelator();
        c.accept(KC, 1, b -> null);
        assertTrue(c.accept(PB, 2, b -> null).isEmpty());
        assertTrue(c.accept(KC, 3, b -> null).isEmpty());
    }
    @Test public void resetSeparatesAccounts()
    {
        PbCorrelator c = new PbCorrelator(); c.accept(KC, 10, b -> null); c.reset();
        assertTrue(c.accept(PB, 10, b -> null).isEmpty());
    }
    @Test public void tokenColorsAndTeamSuffixWorkInEitherOrder()
    {
        PbCorrelator c = new PbCorrelator();
        c.accept(KC.replace("<col=ff0000>", "@red@"), 1, b -> null);
        assertEquals(2, c.accept(PB.replace("<col=ff0000>", "@red@"), 1, b -> "Solo").size());
    }
    @Test public void rejectsMalformedAndOutOfRangeTimes()
    {
        for (String s : new String[]{"NaN", "Infinity", "-1", "1:60", "24:00:00", "1:2:3:4", "0", "99999999999999999999999"})
            assertTrue(s, Double.isNaN(PbParser.seconds(s)));
        assertEquals(3723.4, PbParser.seconds("1:02:03.4"), .001);
    }
    @Test public void formatIsLocaleIndependent()
    {
        java.util.Locale old = java.util.Locale.getDefault();
        try { java.util.Locale.setDefault(java.util.Locale.GERMANY); assertEquals("1:02.50", PbParser.formatSeconds(62.5)); }
        finally { java.util.Locale.setDefault(old); }
    }
}
