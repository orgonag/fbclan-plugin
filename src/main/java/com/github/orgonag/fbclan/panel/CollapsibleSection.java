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

        add(toggleHeader(title, expanded, onToggle, true));

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

    // Shared clickable "+/-" header row. `section` style = bold orange
    // (top-level dashboard section); otherwise the small gray inner-header
    // style used for per-boss rows. Font-safe: plain +/- text, no glyphs.
    static JPanel toggleHeader(String title, boolean expanded, Runnable onToggle, boolean section)
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(section
            ? BorderFactory.createEmptyBorder(5, 6, 5, 6)
            : BorderFactory.createEmptyBorder(3, 6, 3, 6));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, section ? 28 : 24));
        header.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel((expanded ? "- " : "+ ") + title);
        label.setFont(section ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
        label.setForeground(section ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
        header.add(label, BorderLayout.CENTER);
        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                onToggle.run();
            }
        });
        return header;
    }
}
