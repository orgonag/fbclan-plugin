package com.github.orgonag.fbclan.welcome;

import com.github.orgonag.fbclan.ClanSession;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;

/**
 * Shows the clan-curated welcome message once per session in the local
 * chatbox, after both the startup fetch and verification have completed
 * (whichever finishes last triggers the display).
 */
public class WelcomeMessagePresenter
{
    // Hex colour for the welcome message chat line (RuneLite <col=> tag).
    private static final String WELCOME_COLOR = "a020f0";

    private final Client client;
    private final ClientThread clientThread;
    private final ClanSession session;
    private final WelcomeMessageService service;

    // Once-per-session latch. The presenter is freshly constructed each
    // startUp (so this initializes false); reset() exists as a defensive
    // API for callers that reuse one. The lambda below also un-latches on
    // a true logout so the next verification success re-attempts.
    private volatile boolean shown = false;

    public WelcomeMessagePresenter(Client client, ClientThread clientThread,
        ClanSession session, WelcomeMessageService service)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.session = session;
        this.service = service;
    }

    public void reset()
    {
        shown = false;
    }

    // Called when the startup fetch completes AND when verification succeeds;
    // whichever happens last shows the message. Both callers run on executor
    // threads — synchronized so they can't double-print.
    public synchronized void maybeShow()
    {
        String message = service.getMessage();
        if (!session.isVerified() || shown || message.isEmpty())
        {
            return;
        }
        shown = true;
        clientThread.invokeLater(() ->
        {
            GameState gs = client.getGameState();
            if (gs == GameState.LOADING || gs == GameState.HOPPING || gs == GameState.CONNECTION_LOST)
            {
                // Transient state — try again next client tick.
                return false;
            }
            if (!session.isVerified() || gs != GameState.LOGGED_IN)
            {
                // True logout (or plugin shut down mid-flight) — un-latch so the
                // next verification success re-attempts.
                shown = false;
                return true;
            }
            // postEvent=false keeps our own printed text out of the chat-event
            // pipeline (onChatMessage's pet matching would otherwise see it).
            // The col tag wraps the already-sanitized message (all angle
            // brackets stripped upstream), so the sheet text cannot alter it.
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=" + WELCOME_COLOR + ">[Final Boss] " + message + "</col>", null, false);
            return true;
        });
    }
}
