package com.github.orgonag.fbclan.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The clan database (Supabase / PostgREST). The anon key below is public
 * by design: Row Level Security on every table decides what it may do,
 * and the two write paths that need server-side judgement (personal
 * bests, member stats) go through improve-only Postgres functions. See
 * README "Data & Security" for the per-table matrix.
 *
 * Every call is blocking network I/O — run on the executor. Reads fail
 * soft (empty array), writes return success or the HTTP status.
 */
@Slf4j
@Singleton
public class Supabase
{
    private static final String DEFAULT_PROJECT_URL = "https://rzhtoqadvbxylwjndnlo.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ6aHRvcWFkdmJ4eWx3am5kbmxvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU2OTU5MDMsImV4cCI6MjA5MTI3MTkwM30.WzWJXS2cpvwnRVBQEroLTsu_iU0j_kkI1wSQhM8eJY0";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http;

    @Inject
    public Supabase(OkHttpClient http)
    {
        this.http = http.newBuilder().callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build();
        boolean urlSet = System.getProperty("finalboss.apiUrl") != null;
        boolean keySet = System.getProperty("finalboss.anonKey") != null;
        if (urlSet != keySet) throw new IllegalArgumentException("Development endpoint requires both finalboss.apiUrl and finalboss.anonKey");
        okhttp3.HttpUrl parsed = okhttp3.HttpUrl.parse(projectUrl());
        if (parsed == null || (!"https".equals(parsed.scheme()) && !"localhost".equals(parsed.host()) && !"127.0.0.1".equals(parsed.host())))
            throw new IllegalArgumentException("Supabase endpoint must use HTTPS (except localhost)");
    }

    // ------------------------------------------------------------ reads

    // Rows matching a PostgREST query ("select=a,b&order=x.desc"); an
    // empty array on any failure, for callers that render what they get.
    public JsonArray get(String table, String query)
    {
        JsonArray rows = getOrNull(table, query);
        return rows == null ? new JsonArray() : rows;
    }

    // Same, but null on failure so a cache can keep its previous value
    // and still clear when the table is genuinely empty.
    public JsonArray getOrNull(String table, String query)
    {
        JsonArray all = new JsonArray();
        boolean limited = query.contains("limit=");
        for (int offset = 0; offset < 100_000;)
        {
            Request.Builder builder = base(projectUrl() + "/rest/v1/" + table + "?" + query
                + (limited ? "" : "&offset=" + offset + "&limit=500"));
            if (!limited) builder.header("Prefer", "count=exact");
            ApiResult result = execute(builder.get().build());
            if (!result.successful() || result.getBody() == null || !result.getBody().isJsonArray()) return null;
            JsonArray rows = result.getBody().getAsJsonArray();
            all.addAll(rows);
            long total = result.totalRows();
            if (limited || rows.size() == 0 || (total >= 0 ? all.size() >= total : rows.size() < 500)) return all;
            offset += rows.size();
        }
        log.warn("Supabase result exceeds supported size for {}", table);
        return null;
    }

    public static String projectUrl()
    {
        return System.getProperty("finalboss.apiUrl", DEFAULT_PROJECT_URL).replaceAll("/+$", "");
    }

    public ApiResult rpcResult(String function, JsonObject args)
    {
        return execute(base(projectUrl() + "/rest/v1/rpc/" + function)
            .post(RequestBody.create(JSON, args.toString())).build());
    }

    private ApiResult execute(Request request)
    {
        try (Response response = http.newCall(request).execute())
        {
            String raw = "";
            if (response.body() != null)
            {
                byte[] bytes = response.body().byteStream().readNBytes(8_000_001);
                if (bytes.length > 8_000_000) return new ApiResult(response.code(), null, "Response too large");
                raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            if (raw.length() > 8_000_000) return new ApiResult(response.code(), null, "Response too large");
            JsonElement body = raw.isEmpty() ? null : new JsonParser().parse(raw);
            return new ApiResult(response.code(), body, null, response.header("Content-Range"));
        }
        catch (IOException | RuntimeException e)
        {
            log.debug("Supabase request unavailable", e);
            return new ApiResult(0, null, "Cannot reach the clan database");
        }
    }

    // ------------------------------------------------------------ writes

    // A Postgres function exposed by PostgREST (improve-only submits).
    public boolean rpc(String function, JsonObject args)
    {
        return rpcResult(function, args).successful();
    }

    // Storage upload; returns the public URL or null.
    public String upload(String bucket, String path, byte[] bytes, String contentType)
    {
        int status = send(base(projectUrl() + "/storage/v1/object/" + bucket + "/" + path)
            .post(RequestBody.create(MediaType.parse(contentType), bytes)), "UPLOAD " + bucket);
        return ok(status) ? publicUrl(bucket, path) : null;
    }

    public static String publicUrl(String bucket, String path)
    {
        return projectUrl() + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    // ------------------------------------------------------------ helpers

    // PostgREST filter value: "rsn=eq." + enc(name).
    public static String enc(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Request.Builder base(String url)
    {
        return new Request.Builder()
            .url(url)
            .header("apikey", System.getProperty("finalboss.anonKey", ANON_KEY))
            .header("Authorization", "Bearer " + System.getProperty("finalboss.anonKey", ANON_KEY));
    }

    private int send(Request.Builder request, String what)
    {
        try (Response response = http.newCall(request.build()).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Supabase {} failed: {} {}", what, response.code(), response.message());
            }
            return response.code();
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Supabase {} failed", what, e);
            return 0;
        }
    }

    private static boolean ok(int status)
    {
        return status >= 200 && status < 300;
    }

    // ------------------------------------------------------------ JSON

    public static String str(JsonObject row, String key)
    {
        JsonElement el = row.get(key);
        return el == null || !el.isJsonPrimitive() ? "" : el.getAsString().trim();
    }

    public static boolean has(JsonObject row, String key)
    {
        JsonElement el = row.get(key);
        return el != null && !el.isJsonNull();
    }

    public static int intOr(JsonObject row, String key, int def)
    {
        try { return has(row, key) ? new java.math.BigDecimal(str(row, key)).intValueExact() : def; }
        catch (RuntimeException e) { return def; }
    }

    public static long longOr(JsonObject row, String key, long def)
    {
        try { return has(row, key) ? new java.math.BigDecimal(str(row, key)).longValueExact() : def; }
        catch (RuntimeException e) { return def; }
    }

    public static Integer intOrNull(JsonObject row, String key)
    {
        try { return has(row, key) ? new java.math.BigDecimal(str(row, key)).intValueExact() : null; }
        catch (RuntimeException e) { return null; }
    }

    public static double doubleOr(JsonObject row, String key, double def)
    {
        try { double value = Double.parseDouble(str(row, key)); return Double.isFinite(value) ? value : def; }
        catch (RuntimeException e) { return def; }
    }

    public static boolean bool(JsonObject row, String key)
    {
        return "true".equals(str(row, key));
    }

    public static Instant instant(JsonObject row, String key, Instant def)
    {
        try
        {
            return has(row, key) ? OffsetDateTime.parse(row.get(key).getAsString()).toInstant() : def;
        }
        catch (RuntimeException e)
        {
            return def;
        }
    }

    // Adds a nullable string column (JSON null clears it server-side).
    public static void put(JsonObject row, String key, String value)
    {
        if (value == null)
        {
            row.add(key, com.google.gson.JsonNull.INSTANCE);
        }
        else
        {
            row.addProperty(key, value);
        }
    }

    public static void put(JsonObject row, String key, Integer value)
    {
        if (value == null)
        {
            row.add(key, com.google.gson.JsonNull.INSTANCE);
        }
        else
        {
            row.addProperty(key, value);
        }
    }
}
