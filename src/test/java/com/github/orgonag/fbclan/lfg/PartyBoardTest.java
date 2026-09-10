package com.github.orgonag.fbclan.lfg;
import com.github.orgonag.fbclan.core.*;
import com.github.orgonag.fbclan.FinalBossConfig;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.Notifier;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PartyBoardTest
{
    @Test public void refreshCompletingAfterStopCannotRepopulateBoard() throws Exception
    {
        Clan clan = mock(Clan.class); Session session = new Session(1,"Alice","p",true);
        when(clan.snapshot()).thenReturn(session); when(clan.current(session)).thenReturn(true);
        FinalBossConfig config = mock(FinalBossConfig.class); when(config.enableLfg()).thenReturn(true);
        PartyApi api = mock(PartyApi.class);
        CountDownLatch fetching = new CountDownLatch(1), release = new CountDownLatch(1);
        Party party = Party.builder().id("x").hostRsn("Bob").activity(Activity.YAMA).capacity(2).applicants(Collections.emptyList()).build();
        when(api.fetch()).thenAnswer(call -> {
            fetching.countDown(); release.await(3,TimeUnit.SECONDS);
            return new PartyApi.Snapshot(Collections.singletonList(party), Collections.emptyList());
        });
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        PartyBoard board = new PartyBoard(mock(Client.class),mock(ClientThread.class),config,clan,api,mock(Notifier.class),scheduler);
        board.start();
        Thread worker = new Thread(board::refresh); worker.start();
        try
        {
            assertTrue(fetching.await(3,TimeUnit.SECONDS)); board.stop(); release.countDown(); worker.join(3000);
            assertFalse(worker.isAlive()); assertTrue(board.parties().isEmpty()); assertTrue(board.formed().isEmpty());
        }
        finally { release.countDown(); }
    }
}
