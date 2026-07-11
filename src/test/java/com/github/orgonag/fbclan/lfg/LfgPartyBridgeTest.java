package com.github.orgonag.fbclan.lfg;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class LfgPartyBridgeTest
{
    @Test
    public void hashIsDeterministicAndTruncated()
    {
        String h1 = LfgPartyBridge.hashPartyId(123456789L);
        String h2 = LfgPartyBridge.hashPartyId(123456789L);
        assertEquals(h1, h2);
        // 8 bytes -> 16 lowercase hex chars: equality-preserving but not
        // reversible to the passphrase-derived party id.
        assertEquals(16, h1.length());
        assertTrue(h1.matches("[0-9a-f]{16}"));
    }

    @Test
    public void differentPartiesHashDifferently()
    {
        assertNotEquals(LfgPartyBridge.hashPartyId(1L), LfgPartyBridge.hashPartyId(2L));
    }
}
