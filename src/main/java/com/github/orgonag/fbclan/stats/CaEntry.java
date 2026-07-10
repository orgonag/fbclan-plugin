package com.github.orgonag.fbclan.stats;

/** One combat-achievements leaderboard row (ca_leaderboard view). */
public class CaEntry
{
    private final String rsn;
    private final int points;
    private final String tier; // "", "Easy".."Grandmaster"

    public CaEntry(String rsn, int points, String tier)
    {
        this.rsn = rsn;
        this.points = points;
        this.tier = tier;
    }

    public String getRsn()
    {
        return rsn;
    }

    public int getPoints()
    {
        return points;
    }

    public String getTier()
    {
        return tier;
    }
}
