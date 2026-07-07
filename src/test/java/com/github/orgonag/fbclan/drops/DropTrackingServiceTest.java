package com.github.orgonag.fbclan.drops;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropTrackingServiceTest
{
    private static final long THRESHOLD = 1_000_000;

    @Test
    public void testEffectiveThresholdClampsToMinimum()
    {
        assertEquals(DropTrackingService.MIN_THRESHOLD_GP, DropTrackingService.effectiveThreshold(0));
        assertEquals(DropTrackingService.MIN_THRESHOLD_GP, DropTrackingService.effectiveThreshold(-1));
        assertEquals(DropTrackingService.MIN_THRESHOLD_GP,
            DropTrackingService.effectiveThreshold(DropTrackingService.MIN_THRESHOLD_GP));
        assertEquals(5_000_000L, DropTrackingService.effectiveThreshold(5_000_000L));
    }

    @Test
    public void testPetMessagesDetected()
    {
        assertTrue(DropTrackingService.isPetMessage(
            "You have a funny feeling like you're being followed."));
        assertTrue(DropTrackingService.isPetMessage(
            "You feel something weird sneaking into your backpack."));
        assertTrue(DropTrackingService.isPetMessage(
            "You have a funny feeling like you would have been followed..."));
        assertFalse(DropTrackingService.isPetMessage("You have been followed by a strange dog."));
        assertFalse(DropTrackingService.isPetMessage(""));
        assertFalse(DropTrackingService.isPetMessage(null));
    }

    @Test
    public void testFollowerPetMessageOnlyMatchesFollowerVariant()
    {
        assertTrue(DropTrackingService.isFollowerPetMessage(
            "You have a funny feeling like you're being followed."));
        assertFalse(DropTrackingService.isFollowerPetMessage(
            "You feel something weird sneaking into your backpack."));
        assertFalse(DropTrackingService.isFollowerPetMessage(
            "You have a funny feeling like you would have been followed..."));
    }

    @Test
    public void testDuplicatePetMessageOnlyMatchesDuplicateVariant()
    {
        assertTrue(DropTrackingService.isDuplicatePetMessage(
            "You have a funny feeling like you would have been followed..."));
        assertFalse(DropTrackingService.isDuplicatePetMessage(
            "You have a funny feeling like you're being followed."));
    }

    @Test
    public void testAboveThreshold()
    {
        assertTrue(DropTrackingService.isValuableDrop(1_500_000, 1, THRESHOLD));
    }

    @Test
    public void testExactlyAtThreshold()
    {
        assertTrue(DropTrackingService.isValuableDrop(1_000_000, 1, THRESHOLD));
    }

    @Test
    public void testBelowThreshold()
    {
        assertFalse(DropTrackingService.isValuableDrop(999_999, 1, THRESHOLD));
    }

    @Test
    public void testMultipleQuantityAboveThreshold()
    {
        assertTrue(DropTrackingService.isValuableDrop(600_000, 2, THRESHOLD));
    }

    @Test
    public void testMultipleQuantityBelowThreshold()
    {
        assertFalse(DropTrackingService.isValuableDrop(400_000, 2, THRESHOLD));
    }

    @Test
    public void testZeroValue()
    {
        assertFalse(DropTrackingService.isValuableDrop(0, 1, THRESHOLD));
    }

    @Test
    public void testCustomThreshold()
    {
        assertTrue(DropTrackingService.isValuableDrop(50_000, 1, 50_000));
        assertFalse(DropTrackingService.isValuableDrop(49_999, 1, 50_000));
    }

    @Test
    public void testZeroThresholdLogsEverything()
    {
        assertTrue(DropTrackingService.isValuableDrop(1, 1, 0));
    }

    @Test
    public void testFormatGpValue()
    {
        assertEquals("1,500,000", DropTrackingService.formatGp(1_500_000));
        assertEquals("3,200,000", DropTrackingService.formatGp(3_200_000));
        assertEquals("999,999", DropTrackingService.formatGp(999_999));
    }

    @Test
    public void testNormalizeItemName()
    {
        assertEquals("araxyte fang", DropTrackingService.normalizeItemName("Araxyte fang"));
        assertEquals("araxyte fang", DropTrackingService.normalizeItemName("  ARAXYTE FANG  "));
        assertEquals("", DropTrackingService.normalizeItemName(null));
        assertEquals("", DropTrackingService.normalizeItemName("   "));
    }

    @Test
    public void testNotableDropMatchesListedName()
    {
        Set<String> notable = new HashSet<>();
        notable.add("araxyte fang");
        assertTrue(DropTrackingService.isNotableDrop("Araxyte fang", notable));
        assertTrue(DropTrackingService.isNotableDrop("  araxyte FANG ", notable));
    }

    @Test
    public void testNotableDropRejectsUnlistedName()
    {
        Set<String> notable = new HashSet<>();
        notable.add("araxyte fang");
        assertFalse(DropTrackingService.isNotableDrop("Bones", notable));
        assertFalse(DropTrackingService.isNotableDrop("araxyte", notable));
    }

    @Test
    public void testNotableDropHandlesEmptyAndNull()
    {
        assertFalse(DropTrackingService.isNotableDrop("Araxyte fang", Collections.emptySet()));
        assertFalse(DropTrackingService.isNotableDrop("Araxyte fang", null));
        assertFalse(DropTrackingService.isNotableDrop(null, Collections.singleton("araxyte fang")));
    }
}
