package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.ClanSession;
import com.github.orgonag.fbclan.FinalBossConfig;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
 * summary: how many members are looking per event, or which parties are
 * open. Read-only by design — nothing is set, cleared, or joined from
 * chat. The typed message still posts to chat normally (it doubles as a
 * visible ask); this handler never consumes or modifies it, and never
 * reacts to other players' commands.
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
    private final LfgService lfgService;
    private final LfgPartyService partyService;

    public LfgChatCommandHandler(Client client, ClientThread clientThread, FinalBossConfig config,
        ClanSession session, ScheduledExecutorService executor, LfgService lfgService,
        LfgPartyService partyService)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.session = session;
        this.executor = executor;
        this.lfgService = lfgService;
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
            case WHO:
                // Network read - off the client thread; the reply hops back.
                replyAsync(() -> summarize(lfgService.getActiveEntries()));
                break;
            case PARTIES:
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

    // Counts active entries per activity in enum declaration order (the
    // panel's grouped-view order), skipping empty activities.
    // Package-private for tests.
    static String summarize(List<LfgEntry> entries)
    {
        Map<LfgActivity, Integer> counts = new EnumMap<>(LfgActivity.class);
        for (LfgEntry entry : entries)
        {
            counts.merge(entry.getActivity(), 1, Integer::sum);
        }
        if (counts.isEmpty())
        {
            return "No one is looking for a group right now.";
        }
        StringBuilder sb = new StringBuilder("LFG: ");
        boolean first = true;
        for (LfgActivity activity : LfgActivity.values())
        {
            Integer count = counts.get(activity);
            if (count == null)
            {
                continue;
            }
            if (!first)
            {
                sb.append(", ");
            }
            first = false;
            sb.append(shortLabel(activity)).append(" (").append(count).append(')');
        }
        return sb.toString();
    }

    // All-caps community abbreviations for the raids (PbFormat's style);
    // display names elsewhere.
    private static String shortLabel(LfgActivity activity)
    {
        switch (activity)
        {
            case COX:
            case TOB:
            case TOA:
                return activity.name();
            default:
                return activity.getDisplayName();
        }
    }

    // Sender names can carry icon img tags and non-breaking spaces.
    private boolean isLocalPlayer(String senderName)
    {
        String rsn = session.getRsn();
        return rsn != null && senderName != null
            && normalize(Text.removeTags(senderName)).equalsIgnoreCase(normalize(rsn));
    }

    private static String normalize(String name)
    {
        return name.replace('\u00A0', ' ').trim();
    }

    // Local-only feedback line; nothing is sent to the server.
    private void sendGameMessage(String message)
    {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }
}
