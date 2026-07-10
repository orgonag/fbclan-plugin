package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.pb.LeaderboardService;
import com.github.orgonag.fbclan.pb.PbEntry;
import com.github.orgonag.fbclan.pb.PbFormat;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Clan PB leaderboard: "New clan bests" feed on top, then every boss with
 * data as a collapsible A–Z section showing at most its top 3. Collapsed
 * by default; expansion state lives for the session only. All remote
 * strings are rendered as plain JLabel text (no HTML mode), so no
 * escaping is needed.
 */
public class LeaderboardPanel extends JPanel
{
    // RuneLite's RuneScape fonts have no emoji/arrow glyphs (they render as
    // boxes and can eat adjacent letters), so ranks are colored text and the
    // collapse markers are plain +/-.
    private static final Color RANK_GOLD = new Color(0xffd700);
    private static final Color RANK_SILVER = new Color(0xc0c0c0);
    private static final Color RANK_BRONZE = new Color(0xcd7f32);

    private final LeaderboardService leaderboardService;
    private final ScheduledExecutorService executor;
    private final JPanel listPanel;
    private final Set<String> expandedKeys = new HashSet<>();

    private List<PbEntry> cachedBoard = Collections.emptyList();
    private List<PbEntry> cachedRecent = Collections.emptyList();

    public LeaderboardPanel(LeaderboardService leaderboardService, ScheduledExecutorService executor)
    {
        this.leaderboardService = leaderboardService;
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

        rebuild();
    }

    public void refresh()
    {
        executor.submit(() -> {
            leaderboardService.refresh();
            List<PbEntry> board = leaderboardService.getLeaderboard();
            List<PbEntry> recent = leaderboardService.getRecentBests();
            SwingUtilities.invokeLater(() -> {
                cachedBoard = board;
                cachedRecent = recent;
                rebuild();
            });
        });
    }

    private void rebuild()
    {
        listPanel.removeAll();

        if (cachedBoard.isEmpty() && cachedRecent.isEmpty())
        {
            JLabel empty = new JLabel("No personal bests recorded yet.");
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setAlignmentX(CENTER_ALIGNMENT);
            empty.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
            listPanel.add(empty);
        }
        else
        {
            if (!cachedRecent.isEmpty())
            {
                listPanel.add(sectionHeader("New clan bests"));
                for (PbEntry e : cachedRecent)
                {
                    listPanel.add(recentRow(e));
                }
            }

            // Group top-3 rows per boss, ordered A–Z by display name.
            Map<String, List<PbEntry>> byBoss = new LinkedHashMap<>();
            for (PbEntry e : cachedBoard)
            {
                byBoss.computeIfAbsent(e.getBossKey(), k -> new ArrayList<>()).add(e);
            }
            Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (String key : byBoss.keySet())
            {
                sorted.put(PbFormat.displayName(key), key);
            }

            if (!sorted.isEmpty())
            {
                listPanel.add(sectionHeader("All bosses"));
                for (Map.Entry<String, String> e : sorted.entrySet())
                {
                    String bossKey = e.getValue();
                    boolean expanded = expandedKeys.contains(bossKey);
                    listPanel.add(bossHeader(e.getKey(), bossKey, expanded));
                    if (expanded)
                    {
                        for (PbEntry entry : byBoss.get(bossKey))
                        {
                            listPanel.add(pbRow(entry));
                        }
                    }
                }
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private JLabel sectionHeader(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(ColorScheme.BRAND_ORANGE);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        return label;
    }

    private JPanel bossHeader(String displayName, String bossKey, boolean expanded)
    {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel((expanded ? "- " : "+ ") + displayName);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.add(label, BorderLayout.CENTER);

        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent ev)
            {
                if (!expandedKeys.remove(bossKey))
                {
                    expandedKeys.add(bossKey);
                }
                rebuild();
            }
        });
        return header;
    }

    private JPanel pbRow(PbEntry entry)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(2, 14, 2, 6));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel rank = new JLabel(rankText(entry.getRank()));
        rank.setFont(FontManager.getRunescapeSmallFont());
        rank.setForeground(rankColor(entry.getRank()));
        rank.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        row.add(rank, BorderLayout.WEST);

        JLabel name = new JLabel(entry.getRsn());
        name.setFont(FontManager.getRunescapeSmallFont());
        name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(name, BorderLayout.CENTER);

        JLabel time = new JLabel(PbFormat.formatSeconds(entry.getSeconds()));
        time.setFont(FontManager.getRunescapeSmallFont());
        time.setForeground(ColorScheme.BRAND_ORANGE);
        row.add(time, BorderLayout.EAST);
        return row;
    }

    private JPanel recentRow(PbEntry entry)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel top = new JLabel(PbFormat.displayName(entry.getBossKey())
            + " — " + PbFormat.formatSeconds(entry.getSeconds()));
        top.setFont(FontManager.getRunescapeSmallFont());
        top.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel bottom = new JLabel(entry.getRsn() + " · " + formatTimeAgo(entry.getAchievedAt()));
        bottom.setFont(FontManager.getRunescapeSmallFont());
        bottom.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stack.add(top);
        stack.add(bottom);
        row.add(stack, BorderLayout.CENTER);
        return row;
    }

    private static String rankText(int rank)
    {
        switch (rank)
        {
            case 1:
                return "1st";
            case 2:
                return "2nd";
            case 3:
                return "3rd";
            default:
                return rank + "th";
        }
    }

    private static Color rankColor(int rank)
    {
        switch (rank)
        {
            case 1:
                return RANK_GOLD;
            case 2:
                return RANK_SILVER;
            case 3:
                return RANK_BRONZE;
            default:
                return ColorScheme.LIGHT_GRAY_COLOR;
        }
    }

    // Same bucketing as DropLogPanel.formatTimeAgo.
    private static String formatTimeAgo(String isoTimestamp)
    {
        try
        {
            OffsetDateTime then = OffsetDateTime.parse(isoTimestamp);
            long minutes = Duration.between(then, OffsetDateTime.now()).toMinutes();
            if (minutes < 1)
            {
                return "just now";
            }
            if (minutes < 60)
            {
                return minutes + " min ago";
            }
            if (minutes < 60 * 24)
            {
                return (minutes / 60) + "h ago";
            }
            return (minutes / (60 * 24)) + "d ago";
        }
        catch (DateTimeParseException e)
        {
            return "";
        }
    }
}
