package com.github.orgonag.fbclan.stats;

import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * Prepends a combat-achievement slayer helmet (Tztok = Elite, Vampyric
 * = Master, Tzkal = Grandmaster) to clan members' names in chat. Tiers
 * come from the same read-only ca_leaderboard view the dashboard uses,
 * so only members who upload stats get a badge.
 */
@Singleton
public class CaBadges
{
    private static final Set<ChatMessageType> BADGED = EnumSet.of(
        ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT, ChatMessageType.PRIVATECHAT,
        ChatMessageType.PRIVATECHATOUT, ChatMessageType.MODPRIVATECHAT, ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.CLAN_GIM_CHAT);

    private final Supabase db;
    private final ChatIconManager icons;
    private final Map<String, Integer> iconByTier = new HashMap<>();
    private volatile Map<String, String> tierByRsn = Collections.emptyMap();

    @Inject
    public CaBadges(Supabase db, ChatIconManager icons)
    {
        this.db = db;
        this.icons = icons;
    }

    // Icons are registered on first use, not at injection time, so a
    // loaded-but-disabled plugin registers nothing.
    private synchronized void ensureIcons()
    {
        if (iconByTier.isEmpty())
        {
            iconByTier.put("Elite", register("ca_elite.png"));
            iconByTier.put("Master", register("ca_master.png"));
            iconByTier.put("Grandmaster", register("ca_grandmaster.png"));
        }
    }

    private int register(String resource)
    {
        BufferedImage image = ImageUtil.loadImageResource(CaBadges.class, "/com/github/orgonag/fbclan/" + resource);
        return icons.registerChatIcon(image);
    }

    // Executor.
    public void refresh()
    {
        JsonArray rows = db.getOrNull("member_badges", "select=rsn,tier&order=rsn.asc");
        if (rows == null)
        {
            return; // keep the previous tiers through an outage
        }
        Map<String, String> tiers = new HashMap<>();
        for (JsonElement el : rows)
        {
            JsonObject row = el.getAsJsonObject();
            String rsn = Text.standardize(Supabase.str(row, "rsn"));
            String tier = Supabase.str(row, "tier");
            if (!rsn.isEmpty() && !tier.isEmpty())
            {
                tiers.put(rsn, tier);
            }
        }
        tierByRsn = Collections.unmodifiableMap(tiers);
    }

    // Client thread. Chat names carry img tags and non-breaking spaces;
    // Text.standardize strips both, matching how the stored RSNs are keyed.
    public void onChatMessage(ChatMessage event)
    {
        if (!BADGED.contains(event.getType()) || event.getName() == null)
        {
            return;
        }
        ensureIcons();
        Integer icon = iconByTier.get(tierByRsn.get(Text.standardize(event.getName())));
        if (icon == null)
        {
            return;
        }
        int index = icons.chatIconIndex(icon);
        if (index < 0)
        {
            return; // mod icon sprites not loaded yet (pre-login)
        }
        MessageNode node = event.getMessageNode();
        node.setName("<img=" + index + ">" + node.getName());
    }
}
