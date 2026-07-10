package com.github.orgonag.fbclan.pb;

/** One leaderboard row as served by the pb_leaderboard / recent_clan_bests views. */
public class PbEntry
{
    private final String rsn;
    private final String bossKey;
    private final double seconds;
    private final String achievedAt; // ISO timestamp string, may be empty
    private final int rank;          // 1..3 from pb_leaderboard; 1 from the feed

    public PbEntry(String rsn, String bossKey, double seconds, String achievedAt, int rank)
    {
        this.rsn = rsn;
        this.bossKey = bossKey;
        this.seconds = seconds;
        this.achievedAt = achievedAt;
        this.rank = rank;
    }

    public String getRsn()
    {
        return rsn;
    }

    public String getBossKey()
    {
        return bossKey;
    }

    public double getSeconds()
    {
        return seconds;
    }

    public String getAchievedAt()
    {
        return achievedAt;
    }

    public int getRank()
    {
        return rank;
    }
}
