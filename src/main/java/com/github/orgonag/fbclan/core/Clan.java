package com.github.orgonag.fbclan.core;

import com.google.gson.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import okhttp3.*;

/** Session generations prevent old asynchronous verification from authorizing a new login. */
@Slf4j
@Singleton
public class Clan
{
    public static final int WOM_GROUP_ID = 1055;
    public enum Status { VERIFYING, MEMBER, NOT_MEMBER, ERROR }
    public interface Listener { void onStatus(Status status); }
    private final Client client;
    private final ClientThread clientThread;
    private final ScheduledExecutorService executor;
    private final OkHttpClient http;
    private final ConfigManager configManager;
    private final AtomicLong generations = new AtomicLong();
    private volatile Session session = new Session(0, null, null, false);
    private volatile boolean enabled;
    private volatile Listener listener = s -> {};
    private volatile Future<?> delayed;
    private volatile Call verificationCall;
    private volatile boolean verifying;

    @Inject
    public Clan(Client client, ClientThread clientThread, ScheduledExecutorService executor,
                OkHttpClient http, ConfigManager configManager)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.executor = executor;
        this.http = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build();
        this.configManager = configManager;
    }
    public void setListener(Listener listener) { this.listener = listener; }
    public Session snapshot() { return session; }
    public boolean current(Session captured) {
        return enabled && captured != null && captured.canUpload()
            && captured.getGeneration() == session.getGeneration() && session.canUpload();
    }
    public String rsn() { return session.getRsn(); }
    public boolean isVerified() { return session.isVerified(); }
    public boolean canUpload() { return enabled && session.canUpload(); }
    public synchronized void activate() { enabled = true; reset(); }
    public synchronized void deactivate() { enabled = false; reset(); listener = s -> {}; }
    public synchronized void reset()
    {
        session = new Session(generations.incrementAndGet(), null, null, false);
        verifying = false;
        Future<?> task = delayed;
        if (task != null) task.cancel(false);
        Call call = verificationCall;
        if (call != null) call.cancel();
    }
    public void verifyAfter(long delaySeconds)
    {
        long generation = session.getGeneration();
        Future<?> old = delayed;
        if (old != null) old.cancel(false);
        delayed = executor.schedule(() -> clientThread.invokeLater(() -> {
            if (!enabled || generation != session.getGeneration()) return;
            if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
                || client.getLocalPlayer().getName() == null || configManager.getRSProfileKey() == null)
            {
                verifyAfter(1);
                return;
            }
            synchronized (this)
            {
                if (!enabled || generation != session.getGeneration()) return;
                String name = client.getLocalPlayer().getName();
                String profile = configManager.getRSProfileKey();
                if (session.getRsn() != null && (!Names.same(session.getRsn(), name) || !profile.equals(session.getProfile()))) reset();
                if (session.isVerified()) return;
                session = new Session(session.getGeneration(), name, profile, false);
                verify();
            }
        }), delaySeconds, TimeUnit.SECONDS);
    }
    public void verify()
    {
        clientThread.invokeLater(() -> {
            Session captured = session;
            if (!enabled || captured.getRsn() == null || verifying) return;
            verifying = true;
            listener.onStatus(Status.VERIFYING);
            executor.submit(() -> {
                Status answer;
                try { answer = inGroup(captured.getRsn()) ? Status.MEMBER : Status.NOT_MEMBER; }
                catch (Exception e) { log.debug("Membership verification unavailable", e); answer = Status.ERROR; }
                Status result = answer;
                clientThread.invokeLater(() -> {
                    synchronized (this)
                    {
                        if (!enabled || captured.getGeneration() != session.getGeneration()) return;
                        verifying = false;
                        session = new Session(captured.getGeneration(), captured.getRsn(), captured.getProfile(), result == Status.MEMBER);
                        listener.onStatus(result);
                    }
                });
            });
        });
    }
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

    private boolean inGroup(String name) throws IOException
    {
        String url = "https://api.wiseoldman.net/v2/players/"
            + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20") + "/groups";
        Request request = new Request.Builder().url(url).header("User-Agent", "FinalBoss-RuneLite-Plugin").build();
        Call call = http.newCall(request);
        verificationCall = call;
        try (Response response = call.execute())
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
