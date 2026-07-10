package com.github.orgonag.fbclan.panel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Painted three-bar podium (2nd | 1st | 3rd): names above the bars,
 * values inside. Entries arrive ranked (index 0 = 1st). Fewer than
 * three entries paints only what exists.
 */
class PodiumComponent extends JComponent
{
    private static final int HEIGHT = 82;
    private static final int[] BAR_HEIGHTS = {56, 42, 32}; // 1st, 2nd, 3rd
    private static final Color[][] BAR_COLORS = {
        {new Color(0xffd700), new Color(0xb8860b)},
        {new Color(0xd8d8d8), new Color(0x909090)},
        {new Color(0xd68a4a), new Color(0x8b4513)},
    };
    // Painting order across the row: 2nd, 1st, 3rd.
    private static final int[] SLOT_TO_RANK = {1, 0, 2};

    private String[] names = new String[0];
    private String[] values = new String[0];

    PodiumComponent()
    {
        setPreferredSize(new Dimension(0, HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    // names/values pre-formatted and rank-ordered (index 0 = 1st).
    void setEntries(List<String> rankedNames, List<String> rankedValues)
    {
        names = rankedNames.toArray(new String[0]);
        values = rankedValues.toArray(new String[0]);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int slotW = (w - 16) / 3;
        Font nameFont = FontManager.getRunescapeSmallFont();
        Font valueFont = nameFont.deriveFont(Font.BOLD);

        for (int slot = 0; slot < 3; slot++)
        {
            int rank = SLOT_TO_RANK[slot];
            if (rank >= names.length)
            {
                continue;
            }
            int barH = BAR_HEIGHTS[rank];
            int x = 4 + slot * (slotW + 4);
            int barY = HEIGHT - barH;

            g2.setPaint(new GradientPaint(x, barY, BAR_COLORS[rank][0], x, HEIGHT, BAR_COLORS[rank][1]));
            g2.fillRoundRect(x, barY, slotW, barH, 4, 4);

            // Name above the bar (light gray), value inside (dark).
            g2.setFont(nameFont);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
            String name = clip(names[rank], fm, slotW);
            g2.drawString(name, x + (slotW - fm.stringWidth(name)) / 2, barY - 3);

            g2.setFont(valueFont);
            fm = g2.getFontMetrics();
            g2.setColor(new Color(0x1e1e1e));
            String value = clip(values[rank], fm, slotW);
            g2.drawString(value, x + (slotW - fm.stringWidth(value)) / 2, barY + fm.getAscent() + 2);
        }
        g2.dispose();
    }

    private static String clip(String s, FontMetrics fm, int maxW)
    {
        if (fm.stringWidth(s) <= maxW - 4)
        {
            return s;
        }
        String out = s;
        while (out.length() > 1 && fm.stringWidth(out + "..") > maxW - 4)
        {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }
}
