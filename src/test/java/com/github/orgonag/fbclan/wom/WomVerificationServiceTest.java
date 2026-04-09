package com.github.orgonag.fbclan.wom;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class WomVerificationServiceTest
{
    private static final int GROUP_ID = 1055;

    @Test
    public void testParseResponseMemberFound()
    {
        String json = "[{\"group\":{\"id\":1055,\"name\":\"Final Boss\"},\"role\":\"member\"},"
            + "{\"group\":{\"id\":999,\"name\":\"Other Clan\"},\"role\":\"member\"}]";
        JsonArray groups = new JsonParser().parse(json).getAsJsonArray();
        boolean result = WomVerificationService.isInGroup(groups, GROUP_ID);
        assertTrue(result);
    }

    @Test
    public void testParseResponseMemberNotFound()
    {
        String json = "[{\"group\":{\"id\":999,\"name\":\"Other Clan\"},\"role\":\"member\"}]";
        JsonArray groups = new JsonParser().parse(json).getAsJsonArray();
        boolean result = WomVerificationService.isInGroup(groups, GROUP_ID);
        assertFalse(result);
    }

    @Test
    public void testParseEmptyResponse()
    {
        JsonArray groups = new JsonArray();
        boolean result = WomVerificationService.isInGroup(groups, GROUP_ID);
        assertFalse(result);
    }

    @Test
    public void testBuildUrl()
    {
        String url = WomVerificationService.buildPlayerGroupsUrl("Test Player");
        assertEquals("https://api.wiseoldman.net/v2/players/Test%20Player/groups", url);
    }

    @Test
    public void testBuildUrlWithSpecialChars()
    {
        String url = WomVerificationService.buildPlayerGroupsUrl("x marks spot");
        assertEquals("https://api.wiseoldman.net/v2/players/x%20marks%20spot/groups", url);
    }
}
