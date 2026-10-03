package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.pbs.Leaderboards;
import com.github.orgonag.fbclan.pbs.Leaderboards.Entry;
import com.github.orgonag.fbclan.pbs.PbParser;
import com.github.orgonag.fbclan.stats.Dashboard;
import com.github.orgonag.fbclan.stats.Dashboard.Named;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.DoubleFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * The clan dashboard: seven collapsible sections (weekly WOM podiums,
 * collection log and CA top-20s, new clan bests, all-time PBs, GP this
 * week). Expansion state lives for the session.
 */
@Singleton
public class LeaderboardsTab extends Tab
{
    private static final String[] SECTIONS = {
        "XP Gained This Week", "EHB This Week", "Collection Log", "Combat Achievements",
        "New Clan Bests", "All-Time PBs", "GP This Week",
    };
    private static final Color[] PLACE = {new Color(0xFFD700), new Color(0xC8C8C8), new Color(0xCD7F32)};

    private final Leaderboards pbs;
    private final Dashboard dashboard;
    private final ItemManager items;
    private final boolean[] expanded = {true, true, true, true, false, false, false};
    private final Set<String> openBosses = new HashSet<>();

    @Inject
    public LeaderboardsTab(Leaderboards pbs, Dashboard dashboard, ItemManager items, ScheduledExecutorService executor)
    {
        super("Leaderboards", executor);
        this.pbs = pbs;
        this.dashboard = dashboard;
        this.items = items;
        render();
    }

    @Override
    public void refresh()
    {
        // Render whatever did update, even when one source failed.
        load(() -> pbs.refresh() & dashboard.refresh(), ok -> {
            render();
            if (!ok) showError("Refresh failed. Previous data may be out of date.");
        });
    }

    private void render()
    {
        fill(() -> {
            for (int i = 0; i < SECTIONS.length; i++)
            {
                int index = i;
                Theme.Card card = Theme.card(null);
                card.add(toggle(SECTIONS[i], expanded[i], true, () -> {
                    expanded[index] = !expanded[index];
                    render();
                }));
                if (expanded[i]) section(i, card);
                list.add(card);
            }
        });
    }

    private void section(int index, JPanel card)
    {
        switch (index)
        {
            case 0:
                podium(card, dashboard.xpWeek(), v -> Dashboard.shortNumber((long) v), "via Wise Old Man" + synced());
                break;
            case 1:
                podium(card, dashboard.ehbWeek(), Dashboard::oneDecimal, "efficient hours bossed" + synced());
                break;
            case 2:
                if (dashboard.clBoard().isEmpty()) card.add(Theme.centered("No collection logs uploaded yet."));
                int rank = 1;
                for (Dashboard.ClEntry e : dashboard.clBoard())
                {
                    card.add(rankRow(rank++, e.getRsn(), null, String.format("%,d/%,d", e.getObtained(), e.getTotal())));
                }
                break;
            case 3:
                if (dashboard.caBoard().isEmpty()) card.add(Theme.centered("No combat achievements uploaded yet."));
                int r = 1;
                for (Dashboard.CaEntry e : dashboard.caBoard())
                {
                    card.add(rankRow(r++, e.getRsn(), e.getTier(), String.format("%,d", e.getPoints())));
                }
                break;
            case 4:
                if (pbs.recent().isEmpty()) card.add(Theme.centered("No new clan bests yet."));
                for (Entry e : pbs.recent())
                {
                    JPanel text = Theme.stack(1);
                    text.add(Theme.bold(PbParser.displayName(e.getBossKey()), Theme.TEXT));
                    text.add(Theme.text(e.getRsn() + " · " + Theme.timeAgo(e.getAchievedAt()), Theme.SUB));
                    card.add(Theme.row(Theme.tile(items, ItemID.GIANT_STOPWATCH, 1, Theme.Tier.PET, 26), text,
                        Theme.bold(PbParser.formatSeconds(e.getSeconds()), Theme.GOLD)));
                }
                break;
            case 5:
                allTimePbs(card);
                break;
            default:
                Dashboard.GpWeek gp = dashboard.gpWeek();
                card.add(Theme.text("Clan total: " + Dashboard.shortNumber(gp.getTotalGp()) + " GP · " + gp.getDropCount() + " drops", Theme.GOLD));
                List<Named> top = gp.getTop();
                if (top.isEmpty())
                {
                    card.add(Theme.centered("No logged drops in the last 7 days."));
                    break;
                }
                podium(card, top, v -> Dashboard.shortNumber((long) v), "logged drops (1M+/notables) · last 7 days");
                break;
        }
    }

    private void podium(JPanel card, List<Named> entries, DoubleFunction<String> fmt, String caption)
    {
        if (entries == null)
        {
            card.add(Theme.centered("waiting for WOM sync"));
            return;
        }
        if (entries.isEmpty())
        {
            card.add(Theme.centered("No data this week."));
            return;
        }
        card.add(new Podium(entries, fmt));
        // Places 4-5 under the podium.
        for (int i = 3; i < entries.size(); i++)
        {
            card.add(rankRow(i + 1, entries.get(i).getRsn(), null, fmt.apply(entries.get(i).getValue())));
        }
        card.add(caption(caption));
    }

    private void allTimePbs(JPanel card)
    {
        if (pbs.board().isEmpty())
        {
            card.add(Theme.centered("No personal bests recorded yet."));
            return;
        }
        Map<String, List<Entry>> byBoss = new LinkedHashMap<>();
        for (Entry e : pbs.board())
        {
            byBoss.computeIfAbsent(e.getBossKey(), k -> new ArrayList<>()).add(e);
        }
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        byBoss.keySet().forEach(key -> sorted.put(PbParser.displayName(key), key));
        sorted.forEach((name, boss) -> {
            boolean open = openBosses.contains(boss);
            card.add(toggle(name, open, false, () -> {
                if (!openBosses.remove(boss)) openBosses.add(boss);
                render();
            }));
            if (open)
            {
                for (Entry e : byBoss.get(boss))
                {
                    card.add(rankRow(e.getRank(), e.getRsn(), null, PbParser.formatSeconds(e.getSeconds())));
                }
            }
        });
    }

    private String synced()
    {
        String at = dashboard.womSyncedAt();
        String ago = at.isEmpty() ? "" : Theme.timeAgo(at);
        return ago.isEmpty() ? "" : " · synced " + ago;
    }

    // ------------------------------------------------------------ pieces

    // Clickable title with a +/- marker; `section` = bold, else a quieter boss line.
    private static JPanel toggle(String title, boolean open, boolean section, Runnable onToggle)
    {
        JLabel label = section ? Theme.bold(title, Theme.TEXT) : Theme.text(title, Theme.SOFT);
        JLabel marker = Theme.bold(open ? "-" : "+", Theme.SUB);
        return Theme.onClick(Theme.row(null, label, marker), onToggle);
    }

    private static JPanel rankRow(int rank, String rsn, String tier, String value)
    {
        JLabel place = Theme.bold(Integer.toString(rank), rank >= 1 && rank <= 3 ? PLACE[rank - 1] : Theme.FAINT);
        place.setPreferredSize(new Dimension(18, 14));
        JPanel east = Theme.row(tier == null || tier.isEmpty() ? null : tierBadge(tier), null, Theme.bold(value, Theme.GOLD));
        return Theme.row(place, Theme.text(rsn, Theme.SOFT), east);
    }

    // GM cyan, Master red, Elite gold, others grey.
    private static JLabel tierBadge(String tier)
    {
        switch (tier)
        {
            case "Grandmaster": return Theme.bold("GM", new Color(0x7DF9FF));
            case "Master": return Theme.bold("MASTER", new Color(0xFF6B6B));
            case "Elite": return Theme.bold("ELITE", Theme.GOLD);
            default: return Theme.bold(tier.toUpperCase(java.util.Locale.ROOT), Theme.FAINT);
        }
    }

    private static JLabel caption(String text)
    {
        JLabel l = Theme.text(text, Theme.FAINT);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
    }

    /** 2nd | 1st | 3rd: names above flat bars, values inside; copes with fewer than three. */
    private static final class Podium extends JComponent
    {
        private static final int HEIGHT = 80;
        private static final int[] BAR = {52, 38, 30};
        private static final int[] SLOT_TO_RANK = {1, 0, 2};
        private final List<Named> entries;
        private final DoubleFunction<String> fmt;

        Podium(List<Named> entries, DoubleFunction<String> fmt)
        {
            this.entries = entries;
            this.fmt = fmt;
            setPreferredSize(new Dimension(10, HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = Theme.smooth(g);
            int slotW = (getWidth() - 8) / 3;
            for (int slot = 0; slot < 3; slot++)
            {
                int rank = SLOT_TO_RANK[slot];
                if (rank >= entries.size()) continue;
                int x = slot * (slotW + 4);
                int top = HEIGHT - BAR[rank];
                g2.setColor(rank == 0 ? Theme.ACCENT_BG : Theme.RAISED);
                g2.fillRoundRect(x, top, slotW, BAR[rank] + 4, 5, 5);
                g2.setColor(PLACE[rank]);
                g2.fillRect(x, top, slotW, 2);
                g2.setFont(FontManager.getRunescapeSmallFont());
                center(g2, entries.get(rank).getRsn(), x, slotW, top - 4, rank == 0 ? Theme.TEXT : Theme.SOFT);
                g2.setFont(FontManager.getRunescapeBoldFont());
                center(g2, Integer.toString(rank + 1), x, slotW, top + 15, PLACE[rank]);
                g2.setFont(FontManager.getRunescapeSmallFont());
                center(g2, fmt.apply(entries.get(rank).getValue()), x, slotW, top + 28, Theme.GOLD);
            }
            g2.dispose();
        }

        private static void center(Graphics2D g2, String s, int x, int w, int baseline, Color color)
        {
            FontMetrics fm = g2.getFontMetrics();
            String out = s;
            while (out.length() > 1 && fm.stringWidth(out) > w - 2)
            {
                out = out.substring(0, out.length() - 2) + ".";
            }
            g2.setColor(color);
            g2.drawString(out, x + (w - fm.stringWidth(out)) / 2, baseline);
        }
    }
}
