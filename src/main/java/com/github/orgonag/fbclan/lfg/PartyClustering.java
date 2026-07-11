package com.github.orgonag.fbclan.lfg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure party-bucketing logic behind the LFG panel's list and grouped
 * views. No Swing dependency — unit-tested directly.
 */
public final class PartyClustering
{
    private PartyClustering()
    {
    }

    // Buckets entries by party_id, preserving the input ordering inside
    // each bucket (the caller hands us a list already sorted by
    // updated_at desc, so the first element of each list is the freshest).
    public static Partitioned partitionByParty(List<LfgEntry> entries)
    {
        Map<String, List<LfgEntry>> parties = new LinkedHashMap<>();
        List<LfgEntry> solos = new ArrayList<>();
        for (LfgEntry e : entries)
        {
            if (e.getPartyId() == null)
            {
                solos.add(e);
            }
            else
            {
                parties.computeIfAbsent(e.getPartyId(), k -> new ArrayList<>()).add(e);
            }
        }
        return new Partitioned(parties, solos);
    }

    public static final class Partitioned
    {
        // Mutable — callers may remove entries (e.g. LfgPanel pulls the local
        // party out via parties.remove(id) before rendering the rest).
        public final Map<String, List<LfgEntry>> parties;
        public final List<LfgEntry> solos;

        Partitioned(Map<String, List<LfgEntry>> parties, List<LfgEntry> solos)
        {
            this.parties = parties;
            this.solos = solos;
        }
    }
}
