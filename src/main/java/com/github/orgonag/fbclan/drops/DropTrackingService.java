package com.github.orgonag.fbclan.drops;

import java.text.NumberFormat;
import java.util.Locale;

public class DropTrackingService
{
    static final int DROP_THRESHOLD_GP = 1_000_000;

    public static boolean isValuableDrop(int gePrice, int quantity)
    {
        return (long) gePrice * quantity >= DROP_THRESHOLD_GP;
    }

    public static String formatGp(long value)
    {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }
}
