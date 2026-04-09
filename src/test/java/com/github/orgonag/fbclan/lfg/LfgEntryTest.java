package com.github.orgonag.fbclan.lfg;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class LfgEntryTest
{
    @Test
    public void testConstruction()
    {
        Instant now = Instant.now();
        LfgEntry entry = new LfgEntry("TestPlayer", LfgActivity.COX, now);
        assertEquals("TestPlayer", entry.getRsn());
        assertEquals(LfgActivity.COX, entry.getActivity());
        assertEquals(now, entry.getUpdatedAt());
    }

    @Test
    public void testFromJsonFields()
    {
        LfgEntry entry = LfgEntry.fromJson("PlayerOne", "TOB", "2026-04-08T12:00:00Z");
        assertEquals("PlayerOne", entry.getRsn());
        assertEquals(LfgActivity.TOB, entry.getActivity());
        assertNotNull(entry.getUpdatedAt());
    }

    @Test
    public void testFromJsonInvalidActivity()
    {
        LfgEntry entry = LfgEntry.fromJson("PlayerOne", "INVALID", "2026-04-08T12:00:00Z");
        assertNull(entry);
    }
}
