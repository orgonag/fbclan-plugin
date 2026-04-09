package com.github.orgonag.fbclan.lfg;

import org.junit.Test;
import static org.junit.Assert.*;

public class LfgActivityTest
{
    @Test
    public void testDisplayNames()
    {
        assertEquals("Chambers of Xeric", LfgActivity.COX.getDisplayName());
        assertEquals("Theatre of Blood", LfgActivity.TOB.getDisplayName());
        assertEquals("Tombs of Amascut", LfgActivity.TOA.getDisplayName());
        assertEquals("Group Boss", LfgActivity.GROUP_BOSS.getDisplayName());
        assertEquals("Minigame", LfgActivity.MINIGAME.getDisplayName());
        assertEquals("PvP", LfgActivity.PVP.getDisplayName());
        assertEquals("Skilling", LfgActivity.SKILLING.getDisplayName());
        assertEquals("Chilling", LfgActivity.CHILLING.getDisplayName());
    }

    @Test
    public void testFromKey()
    {
        assertEquals(LfgActivity.COX, LfgActivity.fromKey("COX"));
        assertEquals(LfgActivity.GROUP_BOSS, LfgActivity.fromKey("GROUP_BOSS"));
        assertNull(LfgActivity.fromKey("INVALID"));
        assertNull(LfgActivity.fromKey(null));
    }

    @Test
    public void testAllActivitiesPresent()
    {
        assertEquals(8, LfgActivity.values().length);
    }
}
