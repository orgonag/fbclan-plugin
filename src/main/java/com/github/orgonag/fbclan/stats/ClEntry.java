package com.github.orgonag.fbclan.stats;

/** One collection-log leaderboard row (cl_leaderboard view). */
public class ClEntry
{
    private final String rsn;
    private final int obtained;
    private final int total;

    public ClEntry(String rsn, int obtained, int total)
    {
        this.rsn = rsn;
        this.obtained = obtained;
        this.total = total;
    }

    public String getRsn()
    {
        return rsn;
    }

    public int getObtained()
    {
        return obtained;
    }

    public int getTotal()
    {
        return total;
    }
}
