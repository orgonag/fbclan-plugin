package com.github.orgonag.fbclan.drops;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropScreenshotServiceTest
{
    @Test
    public void testFormatPartyLineEmpty()
    {
        assertNull(DropScreenshotService.formatPartyLine(null));
        assertNull(DropScreenshotService.formatPartyLine(Collections.emptyList()));
    }

    @Test
    public void testFormatPartyLineSingle()
    {
        assertEquals("Party members: Zezima",
            DropScreenshotService.formatPartyLine(Collections.singletonList("Zezima")));
    }

    @Test
    public void testFormatPartyLineMultiple()
    {
        assertEquals("Party members: A, B, C",
            DropScreenshotService.formatPartyLine(Arrays.asList("A", "B", "C")));
    }

    @Test
    public void testBuildPathSanitizesRsn()
    {
        String path = DropScreenshotService.buildPath("Some Player", 1234567890L, 11802);
        assertEquals("Some_Player/1234567890_11802.png", path);
    }

    @Test
    public void testAnnotatePreservesDimensions()
    {
        BufferedImage frame = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        BufferedImage annotated = DropScreenshotService.annotate(frame, Arrays.asList("A", "B"));
        assertEquals(800, annotated.getWidth());
        assertEquals(600, annotated.getHeight());
    }

    @Test
    public void testAnnotateWithoutPartyDoesNotThrow()
    {
        BufferedImage frame = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        assertNotNull(DropScreenshotService.annotate(frame, Collections.emptyList()));
    }
}
