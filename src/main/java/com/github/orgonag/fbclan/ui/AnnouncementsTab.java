package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.clan.ClanContent;
import com.github.orgonag.fbclan.clan.ClanContent.Announcement;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Clan announcements, full text, newest first. Plain text: nothing to inject. */
@Singleton
public class AnnouncementsTab extends JPanel
{
    private final ClanContent content;
    private final ScheduledExecutorService executor;
    private final JPanel list;

    @Inject
    public AnnouncementsTab(ClanContent content, ScheduledExecutorService executor)
    {
        this.content = content;
        this.executor = executor;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        list = Ui.scrollList(this);
        list.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        render(content.announcements());
    }

    public void refresh()
    {
        Ui.async(executor, () -> {
            content.refreshAnnouncements();
            return content.announcements();
        }, this::render);
    }

    private void render(List<Announcement> items)
    {
        list.removeAll();
        if (items.isEmpty())
        {
            JLabel empty = Ui.empty("No announcements yet.");
            empty.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
            list.add(empty);
        }
        for (int i = 0; i < items.size(); i++)
        {
            if (i > 0)
            {
                list.add(Box.createRigidArea(new Dimension(0, 8)));
                JSeparator sep = new JSeparator();
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                sep.setAlignmentX(LEFT_ALIGNMENT);
                list.add(sep);
                list.add(Box.createRigidArea(new Dimension(0, 8)));
            }
            Announcement a = items.get(i);
            if (!a.getTitle().isEmpty())
            {
                JTextArea title = Ui.plain(a.getTitle());
                title.setFont(FontManager.getRunescapeBoldFont());
                title.setForeground(ColorScheme.BRAND_ORANGE);
                list.add(title);
            }
            if (!a.getDate().isEmpty())
            {
                JLabel date = Ui.small(a.getDate(), Ui.MUTED);
                date.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
                list.add(date);
            }
            if (!a.getBody().isEmpty())
            {
                list.add(Ui.plain(a.getBody()));
            }
        }
        list.revalidate();
        list.repaint();
    }
}
