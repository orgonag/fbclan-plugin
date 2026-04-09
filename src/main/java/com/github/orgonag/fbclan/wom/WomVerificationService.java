package com.github.orgonag.fbclan.wom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class WomVerificationService
{
    private static final String WOM_BASE_URL = "https://api.wiseoldman.net/v2";
    static final int FINAL_BOSS_GROUP_ID = 1055;

    private final OkHttpClient httpClient;
    private volatile Boolean cachedResult = null;

    public WomVerificationService(OkHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public static String buildPlayerGroupsUrl(String rsn)
    {
        String encoded = URLEncoder.encode(rsn, StandardCharsets.UTF_8).replace("+", "%20");
        return WOM_BASE_URL + "/players/" + encoded + "/groups";
    }

    public static boolean isInGroup(JsonArray groups, int groupId)
    {
        for (JsonElement element : groups)
        {
            JsonObject entry = element.getAsJsonObject();
            JsonObject group = entry.getAsJsonObject("group");
            if (group != null && group.get("id").getAsInt() == groupId)
            {
                return true;
            }
        }
        return false;
    }

    public boolean verify(String rsn) throws IOException
    {
        if (cachedResult != null)
        {
            return cachedResult;
        }

        String url = buildPlayerGroupsUrl(rsn);
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", "FinalBoss-RuneLite-Plugin")
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("WOM API returned " + response.code());
            }

            JsonArray groups = new JsonParser().parse(response.body().string()).getAsJsonArray();
            cachedResult = isInGroup(groups, FINAL_BOSS_GROUP_ID);
            return cachedResult;
        }
    }

    public void clearCache()
    {
        cachedResult = null;
    }

    public Boolean getCachedResult()
    {
        return cachedResult;
    }
}
