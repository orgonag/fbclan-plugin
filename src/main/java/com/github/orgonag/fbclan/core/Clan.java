package com.github.orgonag.fbclan.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Who the local player is and whether they're in the clan. The one
 * deliberate non-Supabase call: once per login, Wise Old Man's group
 * list for the player is checked for group 1055. Every feature asks
 * {@link #canUpload()} before sending anything anywhere.
 *
 * State is written on the client thread (RSN capture, reset) and the
 * executor (verification) and read everywhere; fields are volatile.
 */
@Slf4j
@Singleton
public class Clan
{
    public static final int WOM_GROUP_ID = 1055;

    public enum Status
    {
        VERIFYING, MEMBER, NOT_MEMBER, ERROR
    }

    public interface Listener
    {
        // On whatever thread produced the change; hop to the EDT for UI.
        void onStatus(Status status);
    }

    private final Client client;
    private final ClientThread clientThread;
    private final ScheduledExecutorService executor;
    private final OkHttpClient http;

    private volatile String rsn;
    private volatile boolean verified;
    private volatile Boolean womAnswer; // cached for the login
    private volatile Listener listener = s -> {};

    @Inject
    public Clan(Client client, ClientThread clientThread, ScheduledExecutorService executor, OkHttpClient http)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.executor = executor;
        this.http = http;
    }

    public void setListener(Listener listener)
    {
        this.listener = listener;
    }

    public String rsn()
    {
        return rsn;
    }

    public boolean isVerified()
    {
        return verified;
    }

    // Player data may leave the client only for a verified member whose
    // RSN is known. Capture rsn() into a local before gating on this so a
    // concurrent reset() can't null it mid-method.
    public boolean canUpload()
    {
        return verified && rsn != null;
    }

    // Leagues, Deadman, speedruns and the like never feed clan boards.
    public boolean onStandardWorld()
    {
        EnumSet<WorldType> types = client.getWorldType();
        return !types.contains(WorldType.SEASONAL)
            && !types.contains(WorldType.DEADMAN)
            && !types.contains(WorldType.TOURNAMENT_WORLD)
            && !types.contains(WorldType.BETA_WORLD)
            && !types.contains(WorldType.QUEST_SPEEDRUNNING)
            && !types.contains(WorldType.NOSAVE_MODE)
            && !types.contains(WorldType.FRESH_START_WORLD)
            && !types.contains(WorldType.PVP_ARENA);
    }

    // Captures the RSN after the login flow settles (the local player's
    // name isn't reliable the instant the game state flips), then verifies.
    public void verifyAfter(long delaySeconds)
    {
        executor.schedule(() -> clientThread.invokeLater(() -> {
            if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
            {
                return;
            }
            rsn = client.getLocalPlayer().getName();
            verify();
        }), delaySeconds, TimeUnit.SECONDS);
    }

    // Re-runs the WOM check (Retry button). Cached per login.
    public void verify()
    {
        String name = rsn;
        if (name == null)
        {
            return;
        }
        listener.onStatus(Status.VERIFYING);
        executor.submit(() -> {
            try
            {
                Boolean answer = womAnswer;
                if (answer == null)
                {
                    answer = inGroup(name);
                    womAnswer = answer;
                }
                verified = answer;
                listener.onStatus(answer ? Status.MEMBER : Status.NOT_MEMBER);
            }
            catch (Exception e)
            {
                log.warn("WOM verification failed", e);
                listener.onStatus(Status.ERROR);
            }
        });
    }

    // Logout / plugin off: forget everything so the next login re-verifies.
    public void reset()
    {
        verified = false;
        rsn = null;
        womAnswer = null;
    }

    private boolean inGroup(String name) throws IOException
    {
        String url = "https://api.wiseoldman.net/v2/players/"
            + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20") + "/groups";
        Request request = new Request.Builder().url(url).header("User-Agent", "FinalBoss-RuneLite-Plugin").build();
        try (Response response = http.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("WOM API returned " + response.code());
            }
            JsonArray groups = new JsonParser().parse(response.body().string()).getAsJsonArray();
            for (JsonElement el : groups)
            {
                JsonObject group = el.getAsJsonObject().getAsJsonObject("group");
                if (group != null && group.get("id").getAsInt() == WOM_GROUP_ID)
                {
                    return true;
                }
            }
            return false;
        }
    }
}
