package com.github.orgonag.fbclan.lfg;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LfgChatCommandHandlerTest
{
    private static LfgEntry entry(String rsn, LfgActivity activity)
    {
        return new LfgEntry(rsn, activity, Instant.now(), null, null, null);
    }

    @Test
    public void testSummarizeEmpty()
    {
        assertEquals("No one is looking for a group right now.",
            LfgChatCommandHandler.summarize(Collections.emptyList()));
    }

    @Test
    public void testSummarizeCountsInActivityOrder()
    {
        List<LfgEntry> entries = new ArrayList<>();
        entries.add(entry("A", LfgActivity.TOB));
        entries.add(entry("B", LfgActivity.SKILLING));
        entries.add(entry("C", LfgActivity.TOB));
        entries.add(entry("D", LfgActivity.COX));
        // COX before TOB before SKILLING (enum declaration order), zeros skipped
        assertEquals("LFG: COX (1), TOB (2), Skilling (1)",
            LfgChatCommandHandler.summarize(entries));
    }

    @Test
    public void testSummarizeNonRaidUsesDisplayName()
    {
        assertEquals("LFG: Group Boss (1)",
            LfgChatCommandHandler.summarize(Collections.singletonList(entry("A", LfgActivity.GROUP_BOSS))));
    }
}
