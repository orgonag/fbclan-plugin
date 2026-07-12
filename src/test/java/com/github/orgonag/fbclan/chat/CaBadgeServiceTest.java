package com.github.orgonag.fbclan.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.Map;
import net.runelite.client.util.Text;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaBadgeServiceTest
{
    private static JsonArray rows(String json)
    {
        return new JsonParser().parse(json).getAsJsonArray();
    }

    @Test
    public void parseTiersStandardizesKeys()
    {
        Map<String, String> tiers = CaBadgeService.parseTiers(rows(
            "[{\"rsn\":\"B ee b\",\"tier\":\"Grandmaster\"},"
            + "{\"rsn\":\"AaronPVM\",\"tier\":\"Master\"},"
            + "{\"rsn\":\"TheQuietCat\",\"tier\":\"Elite\"}]"));
        assertEquals("Grandmaster", tiers.get("b ee b"));
        assertEquals("Master", tiers.get("aaronpvm"));
        assertEquals("Elite", tiers.get("thequietcat"));
    }

    @Test
    public void parseTiersSkipsRowsWithoutRsnOrTier()
    {
        Map<String, String> tiers = CaBadgeService.parseTiers(rows(
            "[{\"rsn\":\"NoTier\",\"tier\":null},"
            + "{\"rsn\":\"\",\"tier\":\"Elite\"},"
            + "{\"tier\":\"Elite\"},"
            + "{\"rsn\":\"Blank\",\"tier\":\"  \"}]"));
        assertTrue(tiers.isEmpty());
    }

    // tierFor keys chat names through Text.standardize — this pins the
    // assumption that it strips img tags (clan rank icons) and converts
    // the non-breaking spaces chat names carry, matching how parseTiers
    // keys stored RSNs.
    @Test
    public void chatNameStandardizationMatchesStoredKeys()
    {
        String nbsp = "\u00A0";
        assertEquals("b ee b", Text.standardize("<img=41>B" + nbsp + "ee" + nbsp + "b"));
        assertEquals("aaronpvm", Text.standardize("AaronPVM"));
    }
}
