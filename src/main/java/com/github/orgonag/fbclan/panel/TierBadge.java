package com.github.orgonag.fbclan.panel;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Small bordered CA tier label: GM cyan, Master red, Elite gold, rest gray. */
final class TierBadge
{
    private TierBadge()
    {
    }

    static JLabel of(String tier)
    {
        String text;
        Color color;
        switch (tier == null ? "" : tier)
        {
            case "Grandmaster":
                text = "GM";
                color = new Color(0x7df9ff);
                break;
            case "Master":
                text = "MASTER";
                color = new Color(0xff6b6b);
                break;
            case "Elite":
                text = "ELITE";
                color = new Color(0xffd700);
                break;
            case "":
                text = "";
                color = ColorScheme.MEDIUM_GRAY_COLOR;
                break;
            default:
                text = tier.toUpperCase(java.util.Locale.ROOT);
                color = ColorScheme.MEDIUM_GRAY_COLOR;
                break;
        }
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(color);
        if (!text.isEmpty())
        {
            label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1),
                BorderFactory.createEmptyBorder(0, 3, 0, 3)));
        }
        return label;
    }
}
