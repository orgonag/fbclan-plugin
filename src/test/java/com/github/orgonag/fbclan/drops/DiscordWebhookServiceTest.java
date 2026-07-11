package com.github.orgonag.fbclan.drops;

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
import static org.junit.Assert.assertTrue;

/**
 * Exercises the webhook URL allowlist and embed payload against a
 * MockWebServer; an interceptor rewrites allowlisted discord.com URLs to
 * the mock server (same technique as SupabaseClientHttpTest).
 */
public class DiscordWebhookServiceTest
{
    private static final String VALID_URL = "https://discord.com/api/webhooks/123/token";

    private MockWebServer server;
    private DiscordWebhookService service;

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
        service = new DiscordWebhookService(new OkHttpClient.Builder().addInterceptor(redirect).build());
    }

    @After
    public void tearDown() throws IOException
    {
        server.shutdown();
    }

    @Test
    public void skipsNullOrEmptyUrl()
    {
        service.sendDropNotification(null, "Alice", "Twisted bow", 1_500_000_000L, "Chambers of Xeric");
        service.sendDropNotification("", "Alice", "Twisted bow", 1_500_000_000L, "Chambers of Xeric");
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void refusesNonDiscordUrl()
    {
        service.sendDropNotification("https://evil.example.com/api/webhooks/1/x",
            "Alice", "Twisted bow", 1_500_000_000L, "Chambers of Xeric");
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void acceptsDiscordappPrefix()
    {
        server.enqueue(new MockResponse().setResponseCode(204));
        service.sendDropNotification("https://discordapp.com/api/webhooks/123/token",
            "Alice", "Twisted bow", 1_500_000_000L, "Chambers of Xeric");
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void sendsGoldEmbedWithValue() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(204));
        service.sendDropNotification(VALID_URL, "Alice", "Twisted bow", 1_500_000_000L, "Chambers of Xeric");
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"title\":\"Alice received a drop!\""));
        assertTrue(body.contains("Twisted bow"));
        assertTrue(body.contains("Chambers of Xeric"));
        assertTrue(body.contains("GP"));
    }

    @Test
    public void omitsGpSuffixForZeroValueDrops() throws Exception
    {
        // Pets have GE value 0 — the embed must not say "(0 GP)".
        server.enqueue(new MockResponse().setResponseCode(204));
        service.sendDropNotification(VALID_URL, "Alice", "Pet (Ikkle hydra)", 0, "Alchemical Hydra");
        RecordedRequest req = server.takeRequest();
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("Pet (Ikkle hydra) from Alchemical Hydra"));
    }
}
