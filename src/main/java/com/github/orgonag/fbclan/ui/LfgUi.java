package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.lfg.Activity;
import com.github.orgonag.fbclan.lfg.Party;
import com.github.orgonag.fbclan.lfg.PartyBoard;
import java.awt.Component;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/** Small LFG display pieces shared by the tab, the host window and the apply form. */
final class LfgUi
{
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private LfgUi()
    {
    }

    // A scheduled post's time line: "Starts Today 20:00 · in 3h", then
    // "Started 40m ago" until the server clears it (3 h after the start).
    // Null for ASAP posts.
    static JLabel start(Party p)
    {
        if (!p.isScheduled()) return null;
        Instant now = Instant.now();
        Instant at = p.getScheduledFor();
        if (now.isBefore(at))
        {
            return Theme.bold((p.isFull() ? "Full · starts " : "Starts ") + day(at) + " · in " + span(Duration.between(now, at)), Theme.ACCENT_HI);
        }
        // Green only when it actually has a full team; an unfilled one is stale.
        return Theme.bold("Started " + Theme.timeAgo(at), p.isFull() ? Theme.GREEN : Theme.ACCENT);
    }

    // "Today 20:00", "Tomorrow 09:30", else "Sat 20:00", in the player's zone.
    static String day(Instant at)
    {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = at.atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(zone);
        String time = HOUR_MINUTE.withZone(zone).format(at);
        if (date.equals(today)) return "Today " + time;
        if (date.equals(today.plusDays(1))) return "Tomorrow " + time;
        return PartyBoard.when(at);
    }

    // "45m", "3h", "2d 4h".
    static String span(Duration d)
    {
        long minutes = Math.max(1, d.toMinutes());
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d" + (hours % 24 == 0 ? "" : " " + (hours % 24) + "h");
    }

    // Tile colour per activity group.
    static Theme.Tier tier(Activity a)
    {
        switch (a.getCategory())
        {
            case RAIDS: return Theme.Tier.MEGA;
            case GOD_WARS: return Theme.Tier.RARE;
            case BOSSES: return Theme.Tier.PET;
            default: return Theme.Tier.COMMON;
        }
    }

    // A disabled button that says why ("Full", "No open roles").
    static void disable(Btn b, String reason)
    {
        b.setText(reason);
        b.setEnabled(false);
    }

    static Btn small(Btn b)
    {
        b.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return b;
    }

    static JPanel pair(Component a, Component b)
    {
        JPanel p = new JPanel(new GridLayout(1, 2, 5, 0));
        p.setOpaque(false);
        p.add(a);
        p.add(b);
        return p;
    }

    static JCheckBox checkbox(String text)
    {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setForeground(Theme.TEXT);
        box.setFont(FontManager.getRunescapeSmallFont());
        return box;
    }

    // Activities read "Category: Name"; categories read "All <category>".
    static JComboBox<Object> combo(Object[] values)
    {
        JComboBox<Object> box = new JComboBox<>(values);
        box.setFont(FontManager.getRunescapeSmallFont());
        box.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
            {
                Object shown = value;
                if (value instanceof Activity)
                {
                    Activity a = (Activity) value;
                    shown = a.getCategory() == Activity.Category.GENERAL ? a.getDisplayName() : a.getCategory().getDisplayName() + ": " + a.getDisplayName();
                }
                else if (value instanceof Activity.Category)
                {
                    shown = "All " + ((Activity.Category) value).getDisplayName();
                }
                return super.getListCellRendererComponent(list, shown, index, selected, focus);
            }
        });
        return box;
    }
}
