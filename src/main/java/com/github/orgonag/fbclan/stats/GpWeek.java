package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.wom.WomEntry;
import java.util.List;

/** GP This Week snapshot: clan totals + top earners podium. */
public class GpWeek
{
    private final long totalGp;
    private final int dropCount;
    private final List<WomEntry> top; // reuses (rsn, value) shape for the podium

    public GpWeek(long totalGp, int dropCount, List<WomEntry> top)
    {
        this.totalGp = totalGp;
        this.dropCount = dropCount;
        this.top = top;
    }

    public long getTotalGp()
    {
        return totalGp;
    }

    public int getDropCount()
    {
        return dropCount;
    }

    public List<WomEntry> getTop()
    {
        return top;
    }
}
