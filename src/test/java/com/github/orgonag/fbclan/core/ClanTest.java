package com.github.orgonag.fbclan.core;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import okhttp3.*;
import org.junit.Test;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ClanTest
{
    @Test public void lateVerificationCannotAuthorizeAnotherAccount() throws Exception
    {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        ConfigManager config = mock(ConfigManager.class);
        ClientThread thread = mock(ClientThread.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Alice");
        when(config.getRSProfileKey()).thenReturn("profile1");
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (chain.request().url().encodedPath().contains("Alice"))
            {
                started.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            String body = chain.request().url().encodedPath().contains("Alice") ? "[{\"group\":{\"id\":1055}}]" : "[]";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"),body)).build();
        }).build();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try
        {
            Clan clan = new Clan(client,thread,executor,http,config);
            clan.activate();
            clan.setListener(status -> { if (status == Clan.Status.NOT_MEMBER) finished.countDown(); });
            clan.verifyAfter(0);
            assertTrue(started.await(3,TimeUnit.SECONDS));
            long oldGeneration = clan.snapshot().getGeneration();
            clan.reset();
            when(player.getName()).thenReturn("Bob");
            when(config.getRSProfileKey()).thenReturn("profile2");
            clan.verifyAfter(0);
            release.countDown();
            assertTrue(finished.await(3,TimeUnit.SECONDS));
            assertEquals("Bob",clan.rsn());
            assertTrue(clan.snapshot().getGeneration()>oldGeneration);
            assertFalse(clan.canUpload());
            clan.deactivate();
            assertFalse(clan.canUpload());
        }
        finally { release.countDown(); executor.shutdownNow(); }
    }
}
