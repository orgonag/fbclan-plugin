package com.github.orgonag.fbclan.wom;

/** One row from a WOM group gains/hiscores response: member + numeric value. */
public class WomEntry
{
    private final String rsn;
    private final double value;

    public WomEntry(String rsn, double value)
    {
        this.rsn = rsn;
        this.value = value;
    }

    public String getRsn()
    {
        return rsn;
    }

    public double getValue()
    {
        return value;
    }
}
