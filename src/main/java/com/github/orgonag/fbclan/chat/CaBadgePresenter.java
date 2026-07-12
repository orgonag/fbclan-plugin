package com.github.orgonag.fbclan.chat;

import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ImageUtil;

/**
 * Prepends the combat-achievement slayer helmet chat icon (Tztok =
 * Elite, Vampyric = Master, Tzkal = Grandmaster) to the sender's name
 * on messages from clan members. Tier data comes from CaBadgeService,
 * so badges appear only for members who upload stats via the plugin.
 */
public class CaBadgePresenter
{
    // Player-authored message types that render a sender name. System
    // clan/broadcast messages (CLAN_MESSAGE etc.) carry no name.
    private static final Set<ChatMessageType> BADGED_TYPES = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.PRIVATECHAT,
        ChatMessageType.PRIVATECHATOUT,
        ChatMessageType.MODPRIVATECHAT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.CLAN_GUEST_CHAT,
        ChatMessageType.CLAN_GIM_CHAT);

    private final ChatIconManager chatIconManager;
    private final CaBadgeService service;
    private final int eliteIcon;
    private final int masterIcon;
    private final int grandmasterIcon;

    public CaBadgePresenter(ChatIconManager chatIconManager, CaBadgeService service)
    {
        this.chatIconManager = chatIconManager;
        this.service = service;
        eliteIcon = register(chatIconManager, "ca_elite.png");
        masterIcon = register(chatIconManager, "ca_master.png");
        grandmasterIcon = register(chatIconManager, "ca_grandmaster.png");
    }

    private static int register(ChatIconManager chatIconManager, String resource)
    {
        BufferedImage image = ImageUtil.loadImageResource(CaBadgePresenter.class,
            "/com/github/orgonag/fbclan/" + resource);
        return chatIconManager.registerChatIcon(image);
    }

    // Runs on the client thread (chat message dispatch).
    public void onChatMessage(ChatMessage event)
    {
        if (!BADGED_TYPES.contains(event.getType()))
        {
            return;
        }
        int iconId = iconIdFor(service.tierFor(event.getName()));
        if (iconId == -1)
        {
            return;
        }
        int index = chatIconManager.chatIconIndex(iconId);
        if (index < 0)
        {
            // Mod icon sprites not loaded yet (pre-login).
            return;
        }
        MessageNode node = event.getMessageNode();
        node.setName("<img=" + index + ">" + node.getName());
    }

    private int iconIdFor(String tier)
    {
        if (tier == null)
        {
            return -1;
        }
        switch (tier)
        {
            case "Elite":
                return eliteIcon;
            case "Master":
                return masterIcon;
            case "Grandmaster":
                return grandmasterIcon;
            default:
                return -1;
        }
    }
}
