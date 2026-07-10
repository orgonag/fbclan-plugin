package com.github.orgonag.fbclan.stats;

import java.util.Locale;

/** Compact number formatting for podium bars and stat rows. */
public final class StatFormat
{
    private StatFormat()
    {
    }

    // 14_200_000 -> "14.2M"; trailing ".0" dropped ("312M", not "312.0M").
    public static String shortNumber(long n)
    {
        if (n >= 1_000_000_000L)
        {
            return trim(n / 1_000_000_000.0) + "B";
        }
        if (n >= 1_000_000L)
        {
            return trim(n / 1_000_000.0) + "M";
        }
        if (n >= 1_000L)
        {
            return trim(n / 1_000.0) + "K";
        }
        return Long.toString(n);
    }

    public static String oneDecimal(double d)
    {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private static String trim(double d)
    {
        String s = String.format(Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
