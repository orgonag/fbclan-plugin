package com.github.orgonag.fbclan.core;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;

/**
 * The plugin's one chat colour: every line it prints (welcome, LFG
 * notifications, !lfg replies) uses the "Chat message color" setting
 * (purple by default) so players can tell it apart from game messages.
 * Remote text inside must already be tag-free.
 */
public final class Chat
{
    public static final Color DEFAULT = new Color(0xA020F0);

    private Chat()
    {
    }

    public static String colored(Color color, String line)
    {
        return ColorUtil.wrapWithColorTag(line, color == null ? DEFAULT : color);
    }
}
