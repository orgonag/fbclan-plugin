package com.github.orgonag.fbclan.drops;
import java.util.HashMap;
import java.util.Map;

/** Match overlapping event channels without collapsing two identical NPC kills. Client thread only. */
public final class LootDeduplicator
{
    private int tick = -1;
    private final Map<String,int[]> counts = new HashMap<>();
    public boolean accept(int currentTick, String fingerprint, boolean tracker)
    {
        if (tick != currentTick) { counts.clear(); tick = currentTick; }
        int[] seen = counts.computeIfAbsent(fingerprint, key -> new int[2]);
        int own = tracker ? 1 : 0;
        return ++seen[own] > seen[1-own];
    }
}
