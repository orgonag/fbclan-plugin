package com.github.orgonag.fbclan.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import net.runelite.client.ui.FontManager;

/** A flat, self-painted button; CHIP and TAB kinds can be switched on. */
final class Btn extends JButton
{
    enum Kind { PRIMARY, GHOST, DANGER, CHIP, TAB }

    private static final Color DANGER_EDGE = new Color(0x5A2A2A);

    private final Kind kind;
    private boolean on;

    Btn(String text, Kind kind, Runnable action)
    {
        super(text);
        this.kind = kind;
        setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
        addActionListener(e -> action.run());
        restyle();
    }

    void setOn(boolean on)
    {
        this.on = on;
        restyle();
        repaint();
    }

    private boolean filled()
    {
        return kind == Kind.PRIMARY || (kind == Kind.CHIP && on);
    }

    private void restyle()
    {
        setForeground(filled() ? Theme.INK : kind == Kind.DANGER ? Theme.RED : kind == Kind.TAB && on ? Theme.ACCENT_HI : Theme.SOFT);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = Theme.smooth(g);
        int w = getWidth() - 1;
        int h = getHeight() - 1;
        int arc = kind == Kind.CHIP ? h : 6;
        boolean hover = isEnabled() && getModel().isRollover();
        Color fill = !isEnabled() ? Theme.RAISED
            : filled() ? (hover ? Theme.ACCENT_HI : Theme.ACCENT)
            : kind == Kind.TAB && on ? Theme.ACCENT_BG
            : hover ? Theme.RAISED : null;
        Color edge = filled() || !isEnabled() ? null
            : kind == Kind.DANGER ? DANGER_EDGE
            : kind == Kind.TAB && on ? Theme.ACCENT : Theme.LINE;
        if (fill != null)
        {
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, arc, arc);
        }
        if (edge != null)
        {
            g2.setColor(edge);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w, h, arc, arc);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
