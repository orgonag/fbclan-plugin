package com.github.orgonag.fbclan.drops;

import java.text.NumberFormat;
import java.util.Locale;

public class DropTrackingService
{
    // The threshold comes from user config (dropThresholdGp) rather than a
    // hardcoded constant, so clan members can tune what counts as "valuable".
    public static boolean isValuableDrop(int gePrice, int quantity, long thresholdGp)
    {
        return (long) gePrice * quantity >= thresholdGp;
    }

    public static String formatGp(long value)
    {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }
}
