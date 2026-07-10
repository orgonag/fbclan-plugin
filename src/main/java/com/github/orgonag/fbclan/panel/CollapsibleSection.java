package com.github.orgonag.fbclan.panel;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Dashboard section: clickable "+/-" header over a content panel.
 * Expansion state lives for the session (owned by the caller via the
 * expanded flag it passes on rebuild). Font-safe: plain +/- text, no
 * glyphs (RuneScape fonts render unknown chars as boxes).
 */
class CollapsibleSection extends JPanel
{
    private final JPanel content;

    CollapsibleSection(String title, boolean expanded, Runnable onToggle)
    {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setAlignmentX(LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel((expanded ? "- " : "+ ") + title);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(ColorScheme.BRAND_ORANGE);
        header.add(label, BorderLayout.CENTER);
        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                onToggle.run();
            }
        });
        add(header);

        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setAlignmentX(LEFT_ALIGNMENT);
        content.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        content.setVisible(expanded);
        add(content);
    }

    JPanel getContent()
    {
        return content;
    }
}
