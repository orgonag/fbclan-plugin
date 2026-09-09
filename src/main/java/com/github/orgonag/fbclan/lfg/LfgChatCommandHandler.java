package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.ClanSession;
import com.github.orgonag.fbclan.FinalBossConfig;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/**
 * Answers the local player's own "!lfg" chat messages with a local-only
 * summary of the open parties. Read-only by design — nothing is hosted,
 * applied to, or changed from chat. The typed message still posts to
 * chat normally (it doubles as a visible ask); this handler never
 * consumes or modifies it, and never reacts to other players' commands.
 */
public class LfgChatCommandHandler
{
    // Chat types the local player can be the author of. For all but
    // PRIVATECHATOUT the event name is the sender, checked against the
    // session RSN below; PRIVATECHATOUT's name is the recipient, but the
    // author is by definition the local player. MODCHAT is public chat
    // from a pmod account.
    private static final Set<ChatMessageType> LOCAL_AUTHOR_TYPES = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.CLAN_GUEST_CHAT,
        ChatMessageType.PRIVATECHATOUT);

    private final Client client;
    private final ClientThread clientThread;
    private final FinalBossConfig config;
    private final ClanSession session;
    private final ScheduledExecutorService executor;
    private final LfgPartyService partyService;

    public LfgChatCommandHandler(Client client, ClientThread clientThread, FinalBossConfig config,
        ClanSession session, ScheduledExecutorService executor, LfgPartyService partyService)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.session = session;
        this.executor = executor;
        this.partyService = partyService;
    }

    // Runs on the client thread (chat message dispatch).
    public void onChatMessage(ChatMessage event)
    {
        if (!config.enableLfg() || !session.canUpload())
        {
            return;
        }
        if (!LOCAL_AUTHOR_TYPES.contains(event.getType()))
        {
            return;
        }
        if (event.getType() != ChatMessageType.PRIVATECHATOUT
            && !isLocalPlayer(event.getName()))
        {
            return;
        }

        // Chat effects (flash/glow) arrive as tags in the raw text.
        LfgChatCommand.Result result = LfgChatCommand.parse(Text.removeTags(event.getMessage()));
        if (result == null)
        {
            return;
        }

        switch (result.getAction())
        {
            case PARTIES:
                // Network read - off the client thread; the reply hops back.
                replyAsync(() -> summarizeParties(partyService.getParties()));
                break;
            case HELP:
                sendGameMessage(LfgChatCommand.USAGE);
                break;
        }
    }

    private void replyAsync(java.util.function.Supplier<String> fetch)
    {
        executor.submit(() -> {
            String reply = fetch.get();
            clientThread.invokeLater(() -> {
                // The fetch may outlive the session (shared executor); don't
                // print into a client that is no longer logged in.
                if (client.getGameState() == GameState.LOGGED_IN)
                {
                    sendGameMessage(reply);
                }
            });
        });
    }

    // One line per open party, newest first: "HMT - Host 3/5 W420 - needs
    // Melee, N freeze". Package-private for tests.
    static String summarizeParties(List<LfgParty> parties)
    {
        if (parties.isEmpty())
        {
            return "No parties are being hosted right now.";
        }
        StringBuilder sb = new StringBuilder("Parties: ");
        boolean first = true;
        for (LfgParty p : parties)
        {
            if (!first)
            {
                sb.append(" | ");
            }
            first = false;
            sb.append(p.getTitle()).append(" - ").append(p.getHostRsn())
                .append(' ').append(p.getMemberCount()).append('/').append(p.getCapacity());
            if (p.getWorld() != null)
            {
                sb.append(" W").append(p.getWorld());
            }
            if (p.isFull())
            {
                sb.append(" (full)");
            }
            else
            {
                String needs = LfgRoles.summarize(p.getOpenRoles());
                if (!needs.isEmpty())
                {
                    sb.append(" - needs ").append(needs);
                }
            }
        }
        return sb.toString();
    }

    // Sender names can carry icon img tags (stripped here) and
    // non-breaking spaces (handled by LfgNames).
    private boolean isLocalPlayer(String senderName)
    {
        String rsn = session.getRsn();
        return rsn != null && senderName != null
            && LfgNames.equal(Text.removeTags(senderName), rsn);
    }

    // Local-only feedback line; nothing is sent to the server.
    private void sendGameMessage(String message)
    {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }
}
