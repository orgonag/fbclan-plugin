package com.github.orgonag.fbclan.wom;

import com.github.orgonag.fbclan.ClanSession;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/**
 * The verification state machine: captures the local RSN after login
 * settles, checks clan membership against the Wise Old Man group (the
 * plugin's single deliberate non-Supabase call, once per login), and
 * reports the outcome to the plugin via the Listener.
 * Callbacks fire on whichever thread reaches them — onRsnCaptured on the
 * client thread, onVerifying on the caller's thread (client thread or
 * EDT), and onVerified/onNotMember/onError on the executor thread.
 * Implementations must hop to the EDT for UI work and must not block.
 */
@Slf4j
public class VerificationController
{
    public interface Listener
    {
        void onRsnCaptured(String rsn);

        void onVerifying();

        void onVerified();

        void onNotMember();

        void onError();
    }

    private final Client client;
    private final ClientThread clientThread;
    private final ScheduledExecutorService executor;
    private final WomVerificationService womService;
    private final ClanSession session;
    private final Listener listener;

    public VerificationController(Client client, ClientThread clientThread,
        ScheduledExecutorService executor, WomVerificationService womService,
        ClanSession session, Listener listener)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.executor = executor;
        this.womService = womService;
        this.session = session;
        this.listener = listener;
    }

    // Captures the player's RSN and triggers WOM verification, after an
    // optional delay to let the login flow settle.
    public void kickOff(long delaySeconds)
    {
        executor.schedule(() -> clientThread.invokeLater(() -> {
            if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null)
            {
                return;
            }
            String rsn = client.getLocalPlayer().getName();
            session.setRsn(rsn);
            listener.onRsnCaptured(rsn);
            verify();
        }), delaySeconds, TimeUnit.SECONDS);
    }

    public void verify()
    {
        listener.onVerifying();
        executor.submit(() -> {
            try
            {
                if (womService.verify(session.getRsn()))
                {
                    session.setVerified(true);
                    listener.onVerified();
                }
                else
                {
                    listener.onNotMember();
                }
            }
            catch (Exception e)
            {
                log.warn("WOM verification failed", e);
                listener.onError();
            }
        });
    }

    // Logout / plugin disable: forget the session and the cached WOM
    // answer so the next login re-verifies.
    public void reset()
    {
        session.reset();
        womService.clearCache();
    }
}
