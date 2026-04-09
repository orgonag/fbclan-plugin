package com.github.orgonag.fbclan.drops;

import org.junit.Test;
import static org.junit.Assert.*;

public class DropTrackingServiceTest
{
    @Test
    public void testAboveThreshold()
    {
        assertTrue(DropTrackingService.isValuableDrop(1_500_000, 1));
    }

    @Test
    public void testExactlyAtThreshold()
    {
        assertTrue(DropTrackingService.isValuableDrop(1_000_000, 1));
    }

    @Test
    public void testBelowThreshold()
    {
        assertFalse(DropTrackingService.isValuableDrop(999_999, 1));
    }

    @Test
    public void testMultipleQuantityAboveThreshold()
    {
        assertTrue(DropTrackingService.isValuableDrop(600_000, 2));
    }

    @Test
    public void testMultipleQuantityBelowThreshold()
    {
        assertFalse(DropTrackingService.isValuableDrop(400_000, 2));
    }

    @Test
    public void testZeroValue()
    {
        assertFalse(DropTrackingService.isValuableDrop(0, 1));
    }

    @Test
    public void testFormatGpValue()
    {
        assertEquals("1,500,000", DropTrackingService.formatGp(1_500_000));
        assertEquals("3,200,000", DropTrackingService.formatGp(3_200_000));
        assertEquals("999,999", DropTrackingService.formatGp(999_999));
    }
}
