package com.github.orgonag.fbclan.panel;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * Painted 13px medal circle (gold/silver/bronze with the rank numeral).
 * Drawn, not a font glyph — RuneScape fonts have no medal characters.
 */
class MedalIcon implements Icon
{
    private static final int SIZE = 13;

    private final Color light;
    private final Color dark;
    private final String numeral;

    static MedalIcon forRank(int rank)
    {
        switch (rank)
        {
            case 1:
                return new MedalIcon(new Color(0xffe97d), new Color(0xb8860b), "1");
            case 2:
                return new MedalIcon(new Color(0xe8e8e8), new Color(0x808080), "2");
            case 3:
                return new MedalIcon(new Color(0xe0a06a), new Color(0x8b4513), "3");
            default:
                return null;
        }
    }

    private MedalIcon(Color light, Color dark, String numeral)
    {
        this.light = light;
        this.dark = dark;
        this.numeral = numeral;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(x, y, light, x + SIZE, y + SIZE, dark));
        g2.fillOval(x, y, SIZE - 1, SIZE - 1);
        g2.setColor(dark.darker());
        g2.drawOval(x, y, SIZE - 1, SIZE - 1);
        g2.setColor(new Color(0x1e1e1e));
        g2.setFont(c.getFont().deriveFont(java.awt.Font.BOLD, 9f));
        java.awt.FontMetrics fm = g2.getFontMetrics();
        g2.drawString(numeral,
            x + (SIZE - fm.stringWidth(numeral)) / 2,
            y + (SIZE + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
    }

    @Override
    public int getIconWidth()
    {
        return SIZE;
    }

    @Override
    public int getIconHeight()
    {
        return SIZE;
    }
}
