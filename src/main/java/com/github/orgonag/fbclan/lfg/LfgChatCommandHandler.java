package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.ClanSession;
import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.panel.LfgPanel;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

/**
 * Reacts to the local player's own "!lfg" chat messages by setting or
 * clearing their LFG status through the panel's shared submit path. The
 * typed message still posts to chat normally (it doubles as a visible
 * advertisement); this handler never consumes or modifies it, and never
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
    private final FinalBossConfig config;
    private final ClanSession session;
    private final LfgPanel lfgPanel;

    public LfgChatCommandHandler(Client client, FinalBossConfig config,
        ClanSession session, LfgPanel lfgPanel)
    {
        this.client = client;
        this.config = config;
        this.session = session;
        this.lfgPanel = lfgPanel;
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
            case SET:
                lfgPanel.setStatusFromCommand(result.getActivity(), result.getNote());
                sendGameMessage("LFG status set: " + result.getActivity().getDisplayName()
                    + " - expires in " + config.lfgTimeoutMinutes() + " min");
                break;
            case CLEAR:
                lfgPanel.removeStatusFromCommand();
                sendGameMessage("LFG status removed.");
                break;
            case HELP:
                sendGameMessage(LfgChatCommand.USAGE);
                break;
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
