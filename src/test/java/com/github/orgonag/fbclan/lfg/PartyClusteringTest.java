package com.github.orgonag.fbclan.lfg;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PartyClusteringTest
{
    private static LfgEntry entry(String rsn, String partyId, Integer partySize)
    {
        String key = LfgActivity.values()[0].getKey();
        return LfgEntry.fromJson(rsn, key, "2026-07-10T10:00:00Z", partyId, partySize, null);
    }

    @Test
    public void solosAndPartiesSeparate()
    {
        List<LfgEntry> entries = Arrays.asList(
            entry("Alice", "p1", 2),
            entry("Bob", null, null),
            entry("Cara", "p1", 2),
            entry("Dan", "p2", 3));

        PartyClustering.Partitioned parts = PartyClustering.partitionByParty(entries);

        assertEquals(2, parts.parties.size());
        assertEquals(Arrays.asList("Alice", "Cara"),
            Arrays.asList(parts.parties.get("p1").get(0).getRsn(), parts.parties.get("p1").get(1).getRsn()));
        assertEquals(1, parts.parties.get("p2").size());
        assertEquals(1, parts.solos.size());
        assertEquals("Bob", parts.solos.get(0).getRsn());
    }

    @Test
    public void preservesInputOrderWithinBuckets()
    {
        // Caller hands a list sorted updated_at desc; first element of each
        // bucket must stay the freshest.
        List<LfgEntry> entries = Arrays.asList(
            entry("First", "p1", 2),
            entry("Second", "p1", 2));
        PartyClustering.Partitioned parts = PartyClustering.partitionByParty(entries);
        assertEquals("First", parts.parties.get("p1").get(0).getRsn());
    }

    @Test
    public void partyIterationOrderFollowsFirstAppearance()
    {
        List<LfgEntry> entries = Arrays.asList(
            entry("A", "late-alphabetical-z", 2),
            entry("B", "a-early", 2));
        PartyClustering.Partitioned parts = PartyClustering.partitionByParty(entries);
        assertEquals("late-alphabetical-z", parts.parties.keySet().iterator().next());
    }

    @Test
    public void emptyInput()
    {
        PartyClustering.Partitioned parts = PartyClustering.partitionByParty(Arrays.asList());
        assertTrue(parts.parties.isEmpty());
        assertTrue(parts.solos.isEmpty());
    }
}
