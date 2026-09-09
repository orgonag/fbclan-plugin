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
    private static final String PROJECT_URL = "https://rzhtoqadvbxylwjndnlo.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ6aHRvcWFkdmJ4eWx3am5kbmxvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU2OTU5MDMsImV4cCI6MjA5MTI3MTkwM30.WzWJXS2cpvwnRVBQEroLTsu_iU0j_kkI1wSQhM8eJY0";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http;

    @Inject
    public Supabase(OkHttpClient http)
    {
        this.http = http;
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
        Request request = base(PROJECT_URL + "/rest/v1/" + table + "?" + query).get().build();
        try (Response response = http.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("Supabase GET {} failed: {}", table, response.code());
                return null;
            }
            return new JsonParser().parse(response.body().string()).getAsJsonArray();
        }
        catch (IOException | RuntimeException e)
        {
            log.warn("Supabase GET {} failed", table, e);
            return null;
        }
    }

    // ------------------------------------------------------------ writes

    public boolean insert(String table, JsonObject row)
    {
        return ok(insertStatus(table, row));
    }

    // HTTP status of an insert, so a caller can tell a constraint
    // rejection (409 / 400) from an outage.
    public int insertStatus(String table, JsonObject row)
    {
        return send(base(PROJECT_URL + "/rest/v1/" + table)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .post(RequestBody.create(JSON, row.toString())), "INSERT " + table);
    }

    // Insert-or-replace keyed on `onConflict` (PostgREST merge-duplicates).
    public boolean upsert(String table, JsonObject row, String onConflict)
    {
        return ok(send(base(PROJECT_URL + "/rest/v1/" + table + "?on_conflict=" + onConflict)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(RequestBody.create(JSON, row.toString())), "UPSERT " + table));
    }

    // PATCHes only the columns present in `fields` on rows matching `filter`.
    public boolean patch(String table, String filter, JsonObject fields)
    {
        return ok(send(base(PROJECT_URL + "/rest/v1/" + table + "?" + filter)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .patch(RequestBody.create(JSON, fields.toString())), "PATCH " + table));
    }

    public boolean delete(String table, String filter)
    {
        return ok(send(base(PROJECT_URL + "/rest/v1/" + table + "?" + filter).delete(), "DELETE " + table));
    }

    // A Postgres function exposed by PostgREST (improve-only submits).
    public boolean rpc(String function, JsonObject args)
    {
        return ok(send(base(PROJECT_URL + "/rest/v1/rpc/" + function)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(JSON, args.toString())), "RPC " + function));
    }

    // Storage upload; returns the public URL or null.
    public String upload(String bucket, String path, byte[] bytes, String contentType)
    {
        int status = send(base(PROJECT_URL + "/storage/v1/object/" + bucket + "/" + path)
            .post(RequestBody.create(MediaType.parse(contentType), bytes)), "UPLOAD " + bucket);
        return ok(status) ? publicUrl(bucket, path) : null;
    }

    public static String publicUrl(String bucket, String path)
    {
        return PROJECT_URL + "/storage/v1/object/public/" + bucket + "/" + path;
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
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer " + ANON_KEY);
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
        return el == null || el.isJsonNull() ? "" : el.getAsString().trim();
    }

    public static boolean has(JsonObject row, String key)
    {
        JsonElement el = row.get(key);
        return el != null && !el.isJsonNull();
    }

    public static int intOr(JsonObject row, String key, int def)
    {
        return has(row, key) ? row.get(key).getAsInt() : def;
    }

    public static long longOr(JsonObject row, String key, long def)
    {
        return has(row, key) ? row.get(key).getAsLong() : def;
    }

    public static Integer intOrNull(JsonObject row, String key)
    {
        return has(row, key) ? row.get(key).getAsInt() : null;
    }

    public static boolean bool(JsonObject row, String key)
    {
        return has(row, key) && row.get(key).getAsBoolean();
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
