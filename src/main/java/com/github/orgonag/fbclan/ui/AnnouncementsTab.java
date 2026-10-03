package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.clan.ClanContent;
import com.github.orgonag.fbclan.clan.ClanContent.Announcement;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.FontManager;

/** Clan announcements, full text, newest first. Plain text: nothing to inject. */
@Singleton
public class AnnouncementsTab extends Tab
{
    private final ClanContent content;

    @Inject
    public AnnouncementsTab(ClanContent content, ScheduledExecutorService executor)
    {
        super("Announcements", executor);
        this.content = content;
        render(content.announcements());
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
        fill(() -> {
            if (items.isEmpty())
            {
                list.add(Theme.centered("No announcements yet."));
            }
            for (int i = 0; i < items.size(); i++)
            {
                Announcement a = items.get(i);
                // The clan's top entry (its own ordering) is outlined.
                Theme.Card card = Theme.card(i == 0 ? Theme.ACCENT : null);
                if (!a.getDate().isEmpty())
                {
                    card.add(Theme.text(a.getDate(), Theme.SUB));
                }
                if (!a.getTitle().isEmpty())
                {
                    javax.swing.JTextArea title = Theme.wrap(a.getTitle(), Theme.ACCENT_HI);
                    title.setFont(FontManager.getRunescapeBoldFont());
                    card.add(title);
                }
                if (!a.getBody().isEmpty())
                {
                    javax.swing.JTextArea body = Theme.wrap(a.getBody(), Theme.SOFT);
                    body.setFont(FontManager.getRunescapeFont());
                    card.add(body);
                }
                list.add(card);
            }
        });
    }
}
