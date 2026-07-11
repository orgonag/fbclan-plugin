package com.github.orgonag.fbclan.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises SupabaseClient's HTTP helpers against a MockWebServer. The
 * production base URL is hardcoded, so an interceptor rewrites every
 * request's host/port to the mock server while preserving path + query.
 */
public class SupabaseClientHttpTest
{
    private MockWebServer server;
    private OkHttpClient client;

    @Before
    public void setUp() throws IOException
    {
        server = new MockWebServer();
        server.start();
        Interceptor redirect = chain -> {
            Request original = chain.request();
            HttpUrl mock = server.url("/");
            HttpUrl rewritten = original.url().newBuilder()
                .scheme(mock.scheme())
                .host(mock.host())
                .port(mock.port())
                .build();
            return chain.proceed(original.newBuilder().url(rewritten).build());
        };
        client = new OkHttpClient.Builder().addInterceptor(redirect).build();
    }

    @After
    public void tearDown() throws IOException
    {
        server.shutdown();
    }

    @Test
    public void tryGetReturnsRowsOnSuccess()
    {
        server.enqueue(new MockResponse().setBody("[{\"name\":\"Araxyte fang\"}]"));
        JsonArray rows = SupabaseClient.tryGet(client, "notable_items", "select=name", "notable items");
        assertNotNull(rows);
        assertEquals(1, rows.size());
        assertEquals("Araxyte fang", rows.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    public void tryGetReturnsEmptyArrayOnHttpError()
    {
        // get() maps non-2xx to an empty array; tryGet must preserve that
        // (callers CLEAR caches on HTTP error, KEEP them on network error).
        server.enqueue(new MockResponse().setResponseCode(500));
        JsonArray rows = SupabaseClient.tryGet(client, "notable_items", "select=name", "notable items");
        assertNotNull(rows);
        assertEquals(0, rows.size());
    }

    @Test
    public void tryGetReturnsNullOnMalformedBody()
    {
        server.enqueue(new MockResponse().setBody("{not json"));
        assertNull(SupabaseClient.tryGet(client, "notable_items", "select=name", "notable items"));
    }

    @Test
    public void tryGetReturnsNullOnNetworkFailure() throws IOException
    {
        server.shutdown(); // connection refused -> IOException inside get()
        assertNull(SupabaseClient.tryGet(client, "notable_items", "select=name", "notable items"));
    }

    @Test
    public void getSendsAnonAuthHeadersAndQuery() throws Exception
    {
        server.enqueue(new MockResponse().setBody("[]"));
        SupabaseClient.get(client, "drops", "select=rsn&limit=1");
        RecordedRequest req = server.takeRequest();
        assertEquals("/rest/v1/drops?select=rsn&limit=1", req.getPath());
        assertNotNull(req.getHeader("apikey"));
        assertTrue(req.getHeader("Authorization").startsWith("Bearer "));
    }

    @Test
    public void insertPostsJsonWithMinimalReturn() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(201));
        JsonObject data = new JsonObject();
        data.addProperty("rsn", "Alice");
        assertTrue(SupabaseClient.insert(client, "drops", data));
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/rest/v1/drops", req.getPath());
        assertEquals("return=minimal", req.getHeader("Prefer"));
        assertEquals("{\"rsn\":\"Alice\"}", req.getBody().readUtf8());
    }

    @Test
    public void insertReturnsFalseOnHttpError() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(403));
        assertFalse(SupabaseClient.insert(client, "drops", new JsonObject()));
    }

    @Test
    public void upsertSetsOnConflictAndMergePrefer() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(201));
        JsonObject data = new JsonObject();
        data.addProperty("rsn", "Alice");
        assertTrue(SupabaseClient.upsert(client, "lfg_entries", data, "rsn"));
        RecordedRequest req = server.takeRequest();
        assertEquals("/rest/v1/lfg_entries?on_conflict=rsn", req.getPath());
        assertEquals("resolution=merge-duplicates,return=minimal", req.getHeader("Prefer"));
    }

    @Test
    public void updatePatchesWithFilter() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(204));
        JsonObject data = new JsonObject();
        data.addProperty("party_id", "abc");
        assertTrue(SupabaseClient.update(client, "lfg_entries", "rsn=eq.Alice", data));
        RecordedRequest req = server.takeRequest();
        assertEquals("PATCH", req.getMethod());
        assertEquals("/rest/v1/lfg_entries?rsn=eq.Alice", req.getPath());
    }

    @Test
    public void deleteUsesFilter() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(204));
        assertTrue(SupabaseClient.delete(client, "lfg_entries", "rsn=eq.Alice"));
        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        assertEquals("/rest/v1/lfg_entries?rsn=eq.Alice", req.getPath());
    }

    @Test
    public void rpcPostsToFunctionUrl() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(204));
        JsonObject args = new JsonObject();
        args.addProperty("p_rsn", "Alice");
        assertTrue(SupabaseClient.rpc(client, "submit_pbs", args));
        RecordedRequest req = server.takeRequest();
        assertEquals("/rest/v1/rpc/submit_pbs", req.getPath());
        assertEquals("{\"p_rsn\":\"Alice\"}", req.getBody().readUtf8());
    }

    @Test
    public void uploadFilePostsBytesWithContentType() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200));
        byte[] bytes = {1, 2, 3};
        assertTrue(SupabaseClient.uploadFile(client, "drop-screenshots", "a/b.png", bytes, "image/png"));
        RecordedRequest req = server.takeRequest();
        assertEquals("/storage/v1/object/drop-screenshots/a/b.png", req.getPath());
        assertTrue(req.getHeader("Content-Type").startsWith("image/png"));
        assertEquals(3, req.getBodySize());
    }
}
