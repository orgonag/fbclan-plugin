package com.github.orgonag.fbclan.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** A rounded surface with an optional outline; children stack top to bottom. */
final class Card extends JPanel
{
    private final Color fill;
    private final Color edge;

    Card(Color fill, Color edge)
    {
        super(new Stack(5));
        this.fill = fill;
        this.edge = edge;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = Theme.smooth(g);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        if (edge != null)
        {
            g2.setColor(edge);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        }
        g2.dispose();
    }
}
