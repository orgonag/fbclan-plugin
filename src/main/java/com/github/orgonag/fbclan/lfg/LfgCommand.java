package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/**
 * The read-only "!lfg" chat command: the local player's own "!lfg" (or
 * "!lfg parties") prints the open parties to their chatbox; anything
 * else prints usage. Nothing is hosted, applied to, or changed from
 * chat, the typed message still posts normally, and other players'
 * commands are ignored.
 */
@Singleton
public class LfgCommand
{
    private static final String USAGE = "Usage: !lfg (lists open parties). Use the Final Boss panel to host or apply.";
    private static final Set<ChatMessageType> LOCAL_AUTHOR = EnumSet.of(
        ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT, ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.PRIVATECHATOUT);

    private final Client client;
    private final ClientThread clientThread;
    private final FinalBossConfig config;
    private final Clan clan;
    private final PartyApi api;
    private final ScheduledExecutorService executor;

    @Inject
    public LfgCommand(Client client, ClientThread clientThread, FinalBossConfig config, Clan clan, PartyApi api,
                      ScheduledExecutorService executor)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.config = config;
        this.clan = clan;
        this.api = api;
        this.executor = executor;
    }

    // Client thread.
    public void onChatMessage(ChatMessage event)
    {
        if (!config.enableLfg() || !clan.canUpload() || !LOCAL_AUTHOR.contains(event.getType()))
        {
            return;
        }
        // PRIVATECHATOUT's name is the recipient; the author is us by definition.
        if (event.getType() != ChatMessageType.PRIVATECHATOUT
            && !Names.same(Text.removeTags(event.getName()), clan.rsn()))
        {
            return;
        }
        String text = Text.removeTags(event.getMessage()).trim();
        if (!text.toLowerCase(Locale.ROOT).startsWith("!lfg"))
        {
            return;
        }
        String rest = text.substring(4);
        if (!rest.isEmpty() && !Character.isWhitespace(rest.charAt(0)))
        {
            return; // "!lfgsomething" is a different command
        }
        String keyword = rest.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (keyword.isEmpty() || keyword.equals("parties") || keyword.equals("party"))
        {
            executor.submit(() -> {
                String reply = summarize(api.parties());
                clientThread.invokeLater(() -> {
                    if (client.getGameState() == GameState.LOGGED_IN)
                    {
                        print(reply);
                    }
                });
            });
        }
        else
        {
            print(USAGE);
        }
    }

    // "Parties: HMT - Host 3/5 W420 - needs Melee, North freeze | ..."
    static String summarize(List<Party> parties)
    {
        if (parties.isEmpty())
        {
            return "No parties are being hosted right now.";
        }
        StringBuilder sb = new StringBuilder("Parties: ");
        boolean first = true;
        for (Party p : parties)
        {
            sb.append(first ? "" : " | ").append(p.title()).append(" - ").append(p.getHostRsn())
                .append(' ').append(p.memberCount()).append('/').append(p.getCapacity());
            first = false;
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
                String needs = Role.summarize(p.openRoles());
                if (!needs.isEmpty())
                {
                    sb.append(" - needs ").append(needs);
                }
            }
        }
        return sb.toString();
    }

    private void print(String message)
    {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }
}
