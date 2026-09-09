package com.github.orgonag.fbclan.lfg;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class LfgChatCommandHandlerTest
{
    private static LfgParty party(String host, LfgActivity activity, int capacity, Integer world)
    {
        return LfgParty.builder()
            .id(host + "-id")
            .hostRsn(host)
            .activity(activity)
            .capacity(capacity)
            .world(world)
            .lootRule(LfgLootRule.UNSPECIFIED)
            .requiredRoles(Collections.emptyList())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .applicants(Collections.emptyList())
            .build();
    }

    @Test
    public void testSummarizePartiesEmpty()
    {
        assertEquals("No parties are being hosted right now.",
            LfgChatCommandHandler.summarizeParties(Collections.emptyList()));
    }

    @Test
    public void testSummarizePartiesOneLinePerParty()
    {
        String out = LfgChatCommandHandler.summarizeParties(Arrays.asList(
            party("Alice", LfgActivity.NEX, 6, 420),
            party("Bob", LfgActivity.CHILLING, 2, null)));
        assertTrue(out.startsWith("Parties: "));
        assertTrue(out.contains("Alice 1/6 W420"));
        assertTrue(out.contains(" | "));
        assertTrue(out.contains("Bob 1/2"));
    }
}
