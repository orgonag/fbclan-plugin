package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.pbs.Leaderboards;
import com.github.orgonag.fbclan.pbs.Leaderboards.Entry;
import com.github.orgonag.fbclan.pbs.PbParser;
import com.github.orgonag.fbclan.stats.Dashboard;
import com.github.orgonag.fbclan.stats.Dashboard.Named;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The clan dashboard: seven collapsible sections (weekly WOM podiums,
 * collection log and CA top-20s, new clan bests, all-time PBs, GP this
 * week). Shiny bits are painted, not font glyphs (the RuneScape font
 * has no medals). Expansion state lives for the session.
 */
@Singleton
public class LeaderboardsTab extends JPanel
{
    private static final String[] SECTIONS = {
        "XP Gained This Week", "EHB This Week", "Collection Log", "Combat Achievements",
        "New Clan Bests", "All-Time PBs", "GP This Week",
    };

    private final Leaderboards pbs;
    private final Dashboard dashboard;
    private final ScheduledExecutorService executor;
    private final JPanel list;
    private final boolean[] expanded = {true, true, true, true, false, false, false};
    private final Set<String> openBosses = new HashSet<>();

    @Inject
    public LeaderboardsTab(Leaderboards pbs, Dashboard dashboard, ScheduledExecutorService executor)
    {
        this.pbs = pbs;
        this.dashboard = dashboard;
        this.executor = executor;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        list = Ui.scrollList(this);
        list.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        render();
    }

    public void refresh()
    {
        Ui.async(executor, () -> {
            pbs.refresh();
            dashboard.refresh();
            return null;
        }, v -> render());
    }

    private void render()
    {
        list.removeAll();
        for (int i = 0; i < SECTIONS.length; i++)
        {
            int index = i;
            list.add(toggle(SECTIONS[i], expanded[i], () -> {
                expanded[index] = !expanded[index];
                render();
            }, true));
            if (expanded[i])
            {
                JPanel content = Ui.column(ColorScheme.DARK_GRAY_COLOR);
                content.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
                section(i, content);
                list.add(content);
            }
        }
        list.revalidate();
        list.repaint();
    }

    private void section(int index, JPanel content)
    {
        switch (index)
        {
            case 0:
                podium(content, dashboard.xpWeek(), v -> Dashboard.shortNumber((long) v), "via Wise Old Man" + synced());
                break;
            case 1:
                podium(content, dashboard.ehbWeek(), Dashboard::oneDecimal, "efficient hours bossed" + synced());
                break;
            case 2:
                if (dashboard.clBoard().isEmpty())
                {
                    content.add(Ui.empty("No collection logs uploaded yet."));
                }
                int rank = 1;
                for (Dashboard.ClEntry e : dashboard.clBoard())
                {
                    content.add(statRow(rank++, e.getRsn(), null, String.format("%,d/%,d", e.getObtained(), e.getTotal())));
                }
                break;
            case 3:
                if (dashboard.caBoard().isEmpty())
                {
                    content.add(Ui.empty("No combat achievements uploaded yet."));
                }
                int r = 1;
                for (Dashboard.CaEntry e : dashboard.caBoard())
                {
                    content.add(statRow(r++, e.getRsn(), e.getTier(), String.format("%,d", e.getPoints())));
                }
                break;
            case 4:
                if (pbs.recent().isEmpty())
                {
                    content.add(Ui.empty("No new clan bests yet."));
                }
                for (Entry e : pbs.recent())
                {
                    content.add(recentRow(e));
                }
                break;
            case 5:
                allTimePbs(content);
                break;
            case 6:
                gpWeek(content);
                break;
        }
    }

    private void podium(JPanel content, List<Named> entries, DoubleFunction<String> fmt, String caption)
    {
        if (entries == null)
        {
            content.add(Ui.empty("waiting for WOM sync"));
            return;
        }
        if (entries.isEmpty())
        {
            content.add(Ui.empty("No data this week."));
            return;
        }
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (Named e : entries)
        {
            names.add(e.getRsn());
            values.add(fmt.apply(e.getValue()));
        }
        content.add(new Podium(names, values));
        content.add(caption(caption));
    }

    private void gpWeek(JPanel content)
    {
        Dashboard.GpWeek gp = dashboard.gpWeek();
        JLabel total = Ui.small("Clan total: " + Dashboard.shortNumber(gp.getTotalGp()) + " GP · " + gp.getDropCount() + " drops", Ui.GOLD);
        total.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 0));
        content.add(total);
        if (gp.getTop().isEmpty())
        {
            content.add(Ui.empty("No logged drops in the last 7 days."));
            return;
        }
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (Named e : gp.getTop())
        {
            names.add(e.getRsn());
            values.add(Dashboard.shortNumber((long) e.getValue()));
        }
        content.add(new Podium(names, values));
        content.add(caption("logged drops · last 7 days"));
    }

    private void allTimePbs(JPanel content)
    {
        if (pbs.board().isEmpty())
        {
            content.add(Ui.empty("No personal bests recorded yet."));
            return;
        }
        Map<String, List<Entry>> byBoss = new LinkedHashMap<>();
        for (Entry e : pbs.board())
        {
            byBoss.computeIfAbsent(e.getBossKey(), k -> new ArrayList<>()).add(e);
        }
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String key : byBoss.keySet())
        {
            sorted.put(PbParser.displayName(key), key);
        }
        for (Map.Entry<String, String> e : sorted.entrySet())
        {
            String boss = e.getValue();
            boolean open = openBosses.contains(boss);
            content.add(toggle(e.getKey(), open, () -> {
                if (!openBosses.remove(boss))
                {
                    openBosses.add(boss);
                }
                render();
            }, false));
            if (open)
            {
                for (Entry entry : byBoss.get(boss))
                {
                    content.add(statRow(entry.getRank(), entry.getRsn(), null, PbParser.formatSeconds(entry.getSeconds())));
                }
            }
        }
    }

    private String synced()
    {
        String at = dashboard.womSyncedAt();
        String ago = at.isEmpty() ? "" : Ui.timeAgo(at);
        return ago.isEmpty() ? "" : " · synced " + ago;
    }

    // ------------------------------------------------------------ rows

    private static JPanel statRow(int rank, String rsn, String tier, String value)
    {
        JPanel row = Ui.row(ColorScheme.DARK_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel rankLabel = new JLabel();
        Medal medal = Medal.forRank(rank);
        if (medal != null)
        {
            rankLabel.setIcon(medal);
        }
        else
        {
            rankLabel.setText(Integer.toString(rank));
            rankLabel.setFont(FontManager.getRunescapeSmallFont());
            rankLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        }
        rankLabel.setPreferredSize(new Dimension(20, 16));
        row.add(rankLabel, BorderLayout.WEST);
        row.add(Ui.small(rsn, Ui.MUTED), BorderLayout.CENTER);
        JPanel east = new JPanel();
        east.setLayout(new javax.swing.BoxLayout(east, javax.swing.BoxLayout.X_AXIS));
        east.setBackground(ColorScheme.DARK_GRAY_COLOR);
        if (tier != null && !tier.isEmpty())
        {
            east.add(tierBadge(tier));
            east.add(Box.createRigidArea(new Dimension(5, 0)));
        }
        east.add(Ui.small(value, Ui.GOLD));
        row.add(east, BorderLayout.EAST);
        return row;
    }

    private static JPanel recentRow(Entry e)
    {
        JPanel row = Ui.row(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JPanel stack = Ui.column(ColorScheme.DARKER_GRAY_COLOR);
        stack.add(Ui.small(PbParser.displayName(e.getBossKey()) + " - " + PbParser.formatSeconds(e.getSeconds()), Ui.MUTED));
        stack.add(Ui.small(e.getRsn() + " · " + Ui.timeAgo(e.getAchievedAt()), ColorScheme.MEDIUM_GRAY_COLOR));
        row.add(stack, BorderLayout.CENTER);
        return row;
    }

    private static JLabel caption(String text)
    {
        JLabel l = Ui.small(text, ColorScheme.MEDIUM_GRAY_COLOR);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return l;
    }

    // Clickable "+/-" header; `section` = bold orange, else small grey.
    private static JPanel toggle(String title, boolean open, Runnable onToggle, boolean section)
    {
        JPanel header = Ui.row(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(section ? BorderFactory.createEmptyBorder(5, 6, 5, 6) : BorderFactory.createEmptyBorder(3, 6, 3, 6));
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, section ? 28 : 24));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel label = new JLabel((open ? "- " : "+ ") + title);
        label.setFont(section ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
        label.setForeground(section ? ColorScheme.BRAND_ORANGE : Ui.MUTED);
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

    // GM cyan, Master red, Elite gold, others grey.
    private static JLabel tierBadge(String tier)
    {
        String text;
        Color color;
        switch (tier)
        {
            case "Grandmaster": text = "GM"; color = new Color(0x7df9ff); break;
            case "Master": text = "MASTER"; color = new Color(0xff6b6b); break;
            case "Elite": text = "ELITE"; color = Ui.GOLD; break;
            default: text = tier.toUpperCase(java.util.Locale.ROOT); color = ColorScheme.MEDIUM_GRAY_COLOR; break;
        }
        JLabel l = Ui.small(text, color);
        l.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color, 1), BorderFactory.createEmptyBorder(0, 3, 0, 3)));
        return l;
    }

    // ------------------------------------------------------------ painted

    // Three-bar podium (2nd | 1st | 3rd), names above, values inside.
    private static class Podium extends JComponent
    {
        private static final int HEIGHT = 82;
        private static final int[] BAR = {56, 42, 32};
        private static final Color[][] COLORS = {
            {new Color(0xffd700), new Color(0xb8860b)},
            {new Color(0xd8d8d8), new Color(0x909090)},
            {new Color(0xd68a4a), new Color(0x8b4513)},
        };
        private static final int[] SLOT_TO_RANK = {1, 0, 2};
        private final String[] names;
        private final String[] values;

        Podium(List<String> names, List<String> values)
        {
            this.names = names.toArray(new String[0]);
            this.values = values.toArray(new String[0]);
            setPreferredSize(new Dimension(0, HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int slotW = (getWidth() - 10) / 3;
            Font nameFont = FontManager.getRunescapeSmallFont();
            Font valueFont = nameFont.deriveFont(Font.BOLD);
            for (int slot = 0; slot < 3; slot++)
            {
                int rank = SLOT_TO_RANK[slot];
                if (rank >= names.length)
                {
                    continue;
                }
                int x = 2 + slot * (slotW + 3);
                int barY = HEIGHT - BAR[rank];
                g2.setPaint(new GradientPaint(x, barY, COLORS[rank][0], x, HEIGHT, COLORS[rank][1]));
                g2.fillRoundRect(x, barY, slotW, BAR[rank], 4, 4);
                g2.setFont(nameFont);
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(Ui.MUTED);
                String name = clip(names[rank], fm, slotW + 6);
                g2.drawString(name, x + (slotW - fm.stringWidth(name)) / 2, barY - 3);
                g2.setFont(valueFont);
                fm = g2.getFontMetrics();
                g2.setColor(new Color(0x1e1e1e));
                String value = clip(values[rank], fm, slotW);
                g2.drawString(value, x + (slotW - fm.stringWidth(value)) / 2, barY + fm.getAscent() + 2);
            }
            g2.dispose();
        }

        private static String clip(String s, FontMetrics fm, int maxW)
        {
            if (fm.stringWidth(s) <= maxW - 4)
            {
                return s;
            }
            String out = s;
            while (out.length() > 1 && fm.stringWidth(out + "..") > maxW - 4)
            {
                out = out.substring(0, out.length() - 1);
            }
            return out + "..";
        }
    }

    // 13px gold/silver/bronze medal with the rank numeral.
    private static class Medal implements Icon
    {
        private static final int SIZE = 13;
        private final Color light;
        private final Color dark;
        private final String numeral;

        static Medal forRank(int rank)
        {
            switch (rank)
            {
                case 1: return new Medal(new Color(0xffe97d), new Color(0xb8860b), "1");
                case 2: return new Medal(new Color(0xe8e8e8), new Color(0x808080), "2");
                case 3: return new Medal(new Color(0xe0a06a), new Color(0x8b4513), "3");
                default: return null;
            }
        }

        private Medal(Color light, Color dark, String numeral)
        {
            this.light = light;
            this.dark = dark;
            this.numeral = numeral;
        }

        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(x, y, light, x + SIZE, y + SIZE, dark));
            g2.fillOval(x, y, SIZE - 1, SIZE - 1);
            g2.setColor(dark.darker());
            g2.drawOval(x, y, SIZE - 1, SIZE - 1);
            g2.setColor(new Color(0x1e1e1e));
            g2.setFont(c.getFont().deriveFont(Font.BOLD, 9f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(numeral, x + (SIZE - fm.stringWidth(numeral)) / 2, y + (SIZE + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        @Override
        public int getIconWidth()
        {
            return SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return SIZE;
        }
    }
}
