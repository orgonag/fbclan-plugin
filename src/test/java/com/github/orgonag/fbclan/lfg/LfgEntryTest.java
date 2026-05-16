package com.github.orgonag.fbclan.lfg;

import com.google.gson.JsonObject;
import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class LfgEntryTest
{
    @Test
    public void testConstruction()
    {
        Instant now = Instant.now();
        LfgEntry entry = new LfgEntry("TestPlayer", LfgActivity.COX, now, null, null);
        assertEquals("TestPlayer", entry.getRsn());
        assertEquals(LfgActivity.COX, entry.getActivity());
        assertEquals(now, entry.getUpdatedAt());
        assertNull(entry.getPartyId());
        assertNull(entry.getPartySize());
    }

    @Test
    public void testFromJsonFields()
    {
        LfgEntry entry = LfgEntry.fromJson("PlayerOne", "TOB", "2026-04-08T12:00:00Z");
        assertEquals("PlayerOne", entry.getRsn());
        assertEquals(LfgActivity.TOB, entry.getActivity());
        assertNotNull(entry.getUpdatedAt());
        assertNull(entry.getPartyId());
        assertNull(entry.getPartySize());
    }

    @Test
    public void testFromJsonWithPartyFields()
    {
        LfgEntry entry = LfgEntry.fromJson("PlayerOne", "TOB", "2026-04-08T12:00:00Z",
            "abc123", 3);
        assertEquals("abc123", entry.getPartyId());
        assertEquals(Integer.valueOf(3), entry.getPartySize());
    }

    @Test
    public void testFromJsonInvalidActivity()
    {
        LfgEntry entry = LfgEntry.fromJson("PlayerOne", "INVALID", "2026-04-08T12:00:00Z");
        assertNull(entry);
    }

    @Test
    public void testFromRowFullRow()
    {
        JsonObject row = new JsonObject();
        row.addProperty("rsn", "Alice");
        row.addProperty("activity", "COX");
        row.addProperty("updated_at", "2026-04-08T12:00:00Z");
        row.addProperty("party_id", "deadbeef");
        row.addProperty("party_size", 4);

        LfgEntry entry = LfgEntry.fromRow(row);
        assertEquals("Alice", entry.getRsn());
        assertEquals(LfgActivity.COX, entry.getActivity());
        assertEquals("deadbeef", entry.getPartyId());
        assertEquals(Integer.valueOf(4), entry.getPartySize());
    }

    @Test
    public void testFromRowTolerantOfMissingPartyColumns()
    {
        // Older rows written before the party_id/party_size columns existed
        // must still deserialize cleanly with null party fields.
        JsonObject row = new JsonObject();
        row.addProperty("rsn", "Alice");
        row.addProperty("activity", "COX");
        row.addProperty("updated_at", "2026-04-08T12:00:00Z");

        LfgEntry entry = LfgEntry.fromRow(row);
        assertNotNull(entry);
        assertNull(entry.getPartyId());
        assertNull(entry.getPartySize());
    }

    @Test
    public void testFromRowMissingRequiredField()
    {
        JsonObject row = new JsonObject();
        row.addProperty("rsn", "Alice");
        // Missing activity and updated_at.
        assertNull(LfgEntry.fromRow(row));
    }
}
