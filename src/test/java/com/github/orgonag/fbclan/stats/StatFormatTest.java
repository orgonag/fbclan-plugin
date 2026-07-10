package com.github.orgonag.fbclan.stats;

import org.junit.Test;
import static org.junit.Assert.*;

public class StatFormatTest
{
    @Test
    public void testShortNumber()
    {
        assertEquals("0", StatFormat.shortNumber(0));
        assertEquals("945", StatFormat.shortNumber(945));
        assertEquals("14.2K", StatFormat.shortNumber(14_200));
        assertEquals("14.2M", StatFormat.shortNumber(14_200_000));
        assertEquals("1.2B", StatFormat.shortNumber(1_230_000_000L));
        assertEquals("312M", StatFormat.shortNumber(312_000_000L));
    }

    @Test
    public void testDecimal()
    {
        assertEquals("38.5", StatFormat.oneDecimal(38.52));
        assertEquals("0.0", StatFormat.oneDecimal(0));
    }
}
