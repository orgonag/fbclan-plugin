package com.github.orgonag.fbclan.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class SupabaseClientTest
{
    @Test
    public void testBuildUrl()
    {
        String url = SupabaseClient.buildUrl("drops");
        assertTrue(url.endsWith("/rest/v1/drops"));
        assertTrue(url.startsWith("https://"));
    }

    @Test
    public void testBuildUrlWithQuery()
    {
        String url = SupabaseClient.buildUrl("lfg_entries", "select=*&order=updated_at.desc");
        assertTrue(url.contains("/rest/v1/lfg_entries?select=*&order=updated_at.desc"));
    }

    @Test
    public void testBuildRpcUrl()
    {
        assertEquals("https://rzhtoqadvbxylwjndnlo.supabase.co/rest/v1/rpc/submit_pbs",
            SupabaseClient.buildRpcUrl("submit_pbs"));
    }
}
