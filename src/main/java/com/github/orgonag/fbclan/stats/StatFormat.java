package com.github.orgonag.fbclan.stats;

import java.util.Locale;

/** Compact number formatting for podium bars and stat rows. */
public final class StatFormat
{
    private StatFormat()
    {
    }

    // 9_800_000 -> "9.8M", 14_200_000 -> "14M": one decimal only below
    // ten units, integer (floored) above — keeps podium values short and
    // avoids the "1000K" rounding edge (999_999 -> "999K").
    public static String shortNumber(long n)
    {
        if (n >= 1_000_000_000L)
        {
            return unit(n, 1_000_000_000L) + "B";
        }
        if (n >= 1_000_000L)
        {
            return unit(n, 1_000_000L) + "M";
        }
        if (n >= 1_000L)
        {
            return unit(n, 1_000L) + "K";
        }
        return Long.toString(n);
    }

    public static String oneDecimal(double d)
    {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private static String unit(long n, long divisor)
    {
        double d = (double) n / divisor;
        if (d >= 10)
        {
            return Long.toString(n / divisor);
        }
        String s = String.format(Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
