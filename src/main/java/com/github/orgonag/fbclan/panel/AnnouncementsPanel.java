package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.announcements.Announcement;
import com.github.orgonag.fbclan.announcements.AnnouncementsService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Scrollable list of clan announcements, full text stacked newest-first.
 * Bodies are rendered in plain (non-HTML) text areas, so remote text
 * cannot inject markup; newlines from the sheet are preserved.
 */
public class AnnouncementsPanel extends JPanel
{
    private final AnnouncementsService announcementsService;
    private final ScheduledExecutorService executor;
    private final JPanel listPanel;

    private List<Announcement> cached = Collections.emptyList();

    public AnnouncementsPanel(AnnouncementsService announcementsService, ScheduledExecutorService executor)
    {
        this.announcementsService = announcementsService;
        this.executor = executor;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        listPanel = new ScrollableListPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listPanel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        rebuildList();
    }

    // Fetch on the executor, rebuild on the EDT (same shape as
    // LfgPanel.refresh). Called when the tab is opened and once at startup.
    public void refresh()
    {
        executor.submit(() -> {
            announcementsService.refresh();
            List<Announcement> latest = announcementsService.getAnnouncements();
            SwingUtilities.invokeLater(() -> {
                cached = latest;
                rebuildList();
            });
        });
    }

    private void rebuildList()
    {
        listPanel.removeAll();

        if (cached.isEmpty())
        {
            JLabel emptyLabel = new JLabel("No announcements yet.");
            emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyLabel.setAlignmentX(CENTER_ALIGNMENT);
            emptyLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
            listPanel.add(emptyLabel);
        }
        else
        {
            for (int i = 0; i < cached.size(); i++)
            {
                if (i > 0)
                {
                    listPanel.add(Box.createRigidArea(new Dimension(0, 8)));
                    JSeparator separator = new JSeparator();
                    separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                    separator.setAlignmentX(LEFT_ALIGNMENT);
                    listPanel.add(separator);
                    listPanel.add(Box.createRigidArea(new Dimension(0, 8)));
                }
                addEntry(cached.get(i));
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private void addEntry(Announcement announcement)
    {
        if (!announcement.getTitle().isEmpty())
        {
            JTextArea title = plainTextArea(announcement.getTitle());
            title.setFont(FontManager.getRunescapeBoldFont());
            title.setForeground(ColorScheme.BRAND_ORANGE);
            listPanel.add(title);
        }

        if (!announcement.getDate().isEmpty())
        {
            JLabel date = new JLabel(announcement.getDate());
            date.setFont(FontManager.getRunescapeSmallFont());
            date.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            date.setAlignmentX(LEFT_ALIGNMENT);
            date.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
            listPanel.add(date);
        }

        if (!announcement.getBody().isEmpty())
        {
            listPanel.add(plainTextArea(announcement.getBody()));
        }
    }

    private static JTextArea plainTextArea(String text)
    {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        area.setFont(FontManager.getRunescapeFont());
        area.setBorder(null);
        area.setAlignmentX(LEFT_ALIGNMENT);
        // Lift BoxLayout's default max (= preferred size) so the area gets
        // the full panel width and wrapping tracks it.
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return area;
    }
}
