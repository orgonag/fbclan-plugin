package com.github.orgonag.fbclan.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class SupabaseClient
{
    // Supabase anon key — intentionally public. Row Level Security (RLS) policies
    // on the database restrict what operations are allowed. The anon key only permits:
    // - drops table: INSERT and SELECT (no UPDATE, no DELETE)
    // - lfg_entries table: INSERT, SELECT, UPDATE, DELETE (needed for LFG lifecycle)
    // - notable_items table: SELECT only (clan-curated list, written solely by
    //   the sheet-sync Apps Script's service-role key)
    // - welcome_message table: SELECT only (clan-curated text, written solely
    //   by the sheet-sync Apps Script's service-role key)
    // - announcements table: SELECT only (clan-curated posts, written solely
    //   by the sheet-sync Apps Script's service-role key)
    // - personal_bests table: NO direct anon access at all. Writes go through
    //   the submit_pbs() function (improve-only); reads through the
    //   pb_leaderboard / recent_clan_bests views (SELECT granted on views only)
    // - drop-screenshots storage bucket: INSERT only (screenshots are immutable
    //   once uploaded; the bucket is public-read so the panel can link to them)
    private static final String PROJECT_URL = "https://rzhtoqadvbxylwjndnlo.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ6aHRvcWFkdmJ4eWx3am5kbmxvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU2OTU5MDMsImV4cCI6MjA5MTI3MTkwM30.WzWJXS2cpvwnRVBQEroLTsu_iU0j_kkI1wSQhM8eJY0";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public static String buildUrl(String table)
    {
        return PROJECT_URL + "/rest/v1/" + table;
    }

    public static String buildUrl(String table, String query)
    {
        return PROJECT_URL + "/rest/v1/" + table + "?" + query;
    }

    public static String buildStorageUrl(String bucket, String path)
    {
        return PROJECT_URL + "/storage/v1/object/" + bucket + "/" + path;
    }

    public static String publicStorageUrl(String bucket, String path)
    {
        return PROJECT_URL + "/storage/v1/object/public/" + bucket + "/" + path;
    }

    public static String buildRpcUrl(String function)
    {
        return PROJECT_URL + "/rest/v1/rpc/" + function;
    }

    public static Request.Builder baseRequest(String url)
    {
        return new Request.Builder()
            .url(url)
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer " + ANON_KEY);
    }

    public static JsonArray get(OkHttpClient httpClient, String table, String query) throws IOException
    {
        String url = buildUrl(table, query);
        Request request = baseRequest(url)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                log.warn("Supabase GET failed: {} {}", response.code(), response.message());
                return new JsonArray();
            }
            return new JsonParser().parse(response.body().string()).getAsJsonArray();
        }
    }

    public static boolean insert(OkHttpClient httpClient, String table, JsonObject data) throws IOException
    {
        String url = buildUrl(table);
        RequestBody body = RequestBody.create(JSON, data.toString());
        Request request = baseRequest(url)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Supabase INSERT failed: {} {}", response.code(), response.message());
                return false;
            }
            return true;
        }
    }

    public static boolean upsert(OkHttpClient httpClient, String table, JsonObject data, String onConflict) throws IOException
    {
        String url = buildUrl(table, "on_conflict=" + onConflict);
        RequestBody body = RequestBody.create(JSON, data.toString());
        Request request = baseRequest(url)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Supabase UPSERT failed: {} {}", response.code(), response.message());
                return false;
            }
            return true;
        }
    }

    public static boolean uploadFile(OkHttpClient httpClient, String bucket, String path, byte[] bytes, String contentType) throws IOException
    {
        String url = buildStorageUrl(bucket, path);
        RequestBody body = RequestBody.create(MediaType.parse(contentType), bytes);
        Request request = baseRequest(url)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Supabase storage upload failed: {} {}", response.code(), response.message());
                return false;
            }
            return true;
        }
    }

    // Partial update: PATCHes only the columns present in `data`, leaving
    // all other columns (including updated_at) untouched. `filter` is a
    // PostgREST filter expression such as "rsn=eq.PlayerName".
    public static boolean update(OkHttpClient httpClient, String table, String filter, JsonObject data) throws IOException
    {
        String url = buildUrl(table, filter);
        RequestBody body = RequestBody.create(JSON, data.toString());
        Request request = baseRequest(url)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .patch(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Supabase UPDATE failed: {} {}", response.code(), response.message());
                return false;
            }
            return true;
        }
    }

    public static boolean delete(OkHttpClient httpClient, String table, String filter) throws IOException
    {
        String url = buildUrl(table, filter);
        Request request = baseRequest(url)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Supabase DELETE failed: {} {}", response.code(), response.message());
                return false;
            }
            return true;
        }
    }

    // Calls a Postgres function exposed via PostgREST. Used for writes that
    // need server-side validation the anon key can't be trusted with
    // (e.g. submit_pbs only accepts a time that beats the existing one).
    public static boolean rpc(OkHttpClient httpClient, String function, JsonObject args) throws IOException
    {
        String url = buildRpcUrl(function);
        RequestBody body = RequestBody.create(JSON, args.toString());
        Request request = baseRequest(url)
            .header("Content-Type", "application/json")
            .post(body)
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
            {
                log.warn("Supabase RPC {} failed: {} {}", function, response.code(), response.message());
                return false;
            }
            return true;
        }
    }
}
