package com.github.orgonag.fbclan.pb;

/**
 * One PB write headed for Supabase. bossKey follows RuneLite core's
 * convention (lowercase, colons stripped, raid team-size suffixes) so
 * seeded and live data share keys. source: 'live' = fresh achievement
 * (eligible for the "New clan bests" feed), 'seed' = backfill of an
 * already-held time (config seed or a restated "Personal best:" line).
 */
public class PbSubmission
{
    private final String bossKey;
    private final double seconds;
    private final String source;

    public PbSubmission(String bossKey, double seconds, String source)
    {
        this.bossKey = bossKey;
        this.seconds = seconds;
        this.source = source;
    }

    public String getBossKey()
    {
        return bossKey;
    }

    public double getSeconds()
    {
        return seconds;
    }

    public String getSource()
    {
        return source;
    }
}
