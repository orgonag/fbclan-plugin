package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.clan.ClanContent.Announcement;
import com.github.orgonag.fbclan.clan.ClanContent;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JTextArea;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;

/** Clan announcements, full text, newest first. Plain text: nothing to inject. */
@Singleton
public class AnnouncementsTab extends Tab
{
    // Remembers, in the local RuneLite config, the newest post this player has opened.
    // Its own group: a write under "finalboss" would restart polling (onConfigChanged).
    private static final String GROUP = "finalbossui";
    private static final String SEEN_KEY = "announcementSeen";

    private final ClanContent content;
    private final ConfigManager configManager;
    private String newest = "";
    private Runnable unreadListener = () -> {};

    @Inject
    public AnnouncementsTab(ClanContent content, ConfigManager configManager, ScheduledExecutorService executor)
    {
        super("Announcements", executor);
        this.content = content;
        this.configManager = configManager;
        render(content.announcements());
    }

    // EDT. True while the newest post hasn't been opened.
    boolean unread()
    {
        return !newest.isEmpty() && !newest.equals(configManager.getConfiguration(GROUP, SEEN_KEY));
    }

    // EDT. The tab was opened.
    void markRead()
    {
        if (!unread()) return;
        configManager.setConfiguration(GROUP, SEEN_KEY, newest);
        unreadListener.run();
    }

    void setUnreadListener(Runnable listener)
    {
        unreadListener = listener;
    }

    @Override
    public void refresh()
    {
        load(() -> {
            if (!content.refreshAnnouncements()) throw new IllegalStateException("Refresh unavailable");
            return content.announcements();
        }, this::render);
    }

    private void render(List<Announcement> items)
    {
        newest = items.isEmpty() ? "" : Integer.toHexString((items.get(0).getDate() + "|" + items.get(0).getTitle() + "|" + items.get(0).getBody()).hashCode());
        if (isShowing()) markRead();
        unreadListener.run();
        fill(() -> {
            if (items.isEmpty())
            {
                list.add(Theme.centered("No announcements yet."));
            }
            for (int i = 0; i < items.size(); i++)
            {
                Announcement a = items.get(i);
                // The clan's top entry (its own ordering) is outlined.
                Card card = Theme.card(i == 0 ? Theme.ACCENT : null);
                if (!a.getDate().isEmpty())
                {
                    card.add(Theme.text(a.getDate(), Theme.SUB));
                }
                if (!a.getTitle().isEmpty())
                {
                    JTextArea title = Theme.wrap(a.getTitle(), Theme.ACCENT_HI);
                    title.setFont(FontManager.getRunescapeBoldFont());
                    card.add(title);
                }
                if (!a.getBody().isEmpty())
                {
                    JTextArea body = Theme.wrap(a.getBody(), Theme.SOFT);
                    body.setFont(FontManager.getRunescapeFont());
                    card.add(body);
                }
                list.add(card);
            }
        });
    }
}
