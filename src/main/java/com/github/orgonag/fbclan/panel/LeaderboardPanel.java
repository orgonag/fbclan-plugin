package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.pb.LeaderboardService;
import com.github.orgonag.fbclan.pb.PbEntry;
import com.github.orgonag.fbclan.pb.PbFormat;
import com.github.orgonag.fbclan.stats.CaEntry;
import com.github.orgonag.fbclan.stats.ClEntry;
import com.github.orgonag.fbclan.stats.DashboardService;
import com.github.orgonag.fbclan.stats.GpWeek;
import com.github.orgonag.fbclan.stats.StatFormat;
import com.github.orgonag.fbclan.wom.WomEntry;
import com.github.orgonag.fbclan.wom.WomStatsParser;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The clan dashboard: eight collapsible sections (weekly WOM podiums,
 * collection log and combat achievement top-20s, the phase-1 clan-bests
 * feed and A-Z PB list, WOM kill counts served from the wom_cache table,
 * GP-this-week). All shiny elements are painted components — no font
 * glyphs. Expansion state is per-session. All remote strings render as
 * plain JLabel text.
 */
public class LeaderboardPanel extends JPanel
{
    private static final String[] SECTIONS = {
        "XP Gained This Week", "EHB This Week", "Collection Log",
        "Combat Achievements", "New Clan Bests", "All-Time PBs",
        "Kill Counts", "GP This Week",
    };
    private static final boolean[] DEFAULT_EXPANDED = {
        true, true, true, true, false, false, false, false,
    };

    private final LeaderboardService leaderboardService;
    private final DashboardService dashboardService;
    private final ScheduledExecutorService executor;
    private final JPanel listPanel;

    private final boolean[] expanded = DEFAULT_EXPANDED.clone();
    private final Set<String> expandedPbBosses = new HashSet<>();
    private final Set<String> expandedKcBosses = new HashSet<>();

    private List<PbEntry> cachedBoard = Collections.emptyList();
    private List<PbEntry> cachedRecent = Collections.emptyList();

    public LeaderboardPanel(LeaderboardService leaderboardService,
        DashboardService dashboardService, ScheduledExecutorService executor)
    {
        this.leaderboardService = leaderboardService;
        this.dashboardService = dashboardService;
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

    private static final class Boards
    {
        final List<PbEntry> board;
        final List<PbEntry> recent;

        Boards(List<PbEntry> board, List<PbEntry> recent)
        {
            this.board = board;
            this.recent = recent;
        }
    }

    public void refresh()
    {
        PanelUi.asyncRefresh(executor, () -> {
            leaderboardService.refresh();
            dashboardService.refresh();
            return new Boards(leaderboardService.getLeaderboard(), leaderboardService.getRecentBests());
        }, boards -> {
            cachedBoard = boards.board;
            cachedRecent = boards.recent;
            rebuild();
        });
    }

    private void toggleSection(int index)
    {
        expanded[index] = !expanded[index];
        rebuild();
    }

    private void rebuild()
    {
        listPanel.removeAll();
        for (int i = 0; i < SECTIONS.length; i++)
        {
            final int index = i;
            CollapsibleSection section = new CollapsibleSection(
                SECTIONS[i], expanded[i], () -> toggleSection(index));
            if (expanded[i])
            {
                buildSectionContent(i, section.getContent());
            }
            listPanel.add(section);
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private void buildSectionContent(int index, JPanel content)
    {
        switch (index)
        {
            case 0:
                buildPodiumSection(content, dashboardService.getXpWeek(),
                    v -> StatFormat.shortNumber((long) v), "via Wise Old Man" + syncedSuffix());
                break;
            case 1:
                buildPodiumSection(content, dashboardService.getEhbWeek(),
                    StatFormat::oneDecimal, "efficient hours bossed" + syncedSuffix());
                break;
            case 2:
                buildClSection(content);
                break;
            case 3:
                buildCaSection(content);
                break;
            case 4:
                buildRecentSection(content);
                break;
            case 5:
                buildPbSection(content);
                break;
            case 6:
                buildKcSection(content);
                break;
            case 7:
                buildGpSection(content);
                break;
        }
    }

    private interface ValueFormatter
    {
        String format(double value);
    }

    private void buildPodiumSection(JPanel content, List<WomEntry> entries,
        ValueFormatter fmt, String caption)
    {
        if (entries == null)
        {
            // Null = the wom_cache row hasn't been read yet (sync not run,
            // or Supabase unreachable) — same wording as the KC section.
            content.add(PanelUi.emptyStateLabel("waiting for WOM sync"));
            return;
        }
        if (entries.isEmpty())
        {
            content.add(PanelUi.emptyStateLabel("No data this week."));
            return;
        }
        PodiumComponent podium = new PodiumComponent();
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (WomEntry e : entries)
        {
            names.add(e.getRsn());
            values.add(fmt.format(e.getValue()));
        }
        podium.setEntries(names, values);
        content.add(podium);
        content.add(captionLabel(caption));
    }

    private void buildClSection(JPanel content)
    {
        List<ClEntry> board = dashboardService.getClBoard();
        if (board.isEmpty())
        {
            content.add(PanelUi.emptyStateLabel("No collection logs uploaded yet."));
            return;
        }
        int rank = 1;
        for (ClEntry e : board)
        {
            content.add(statRow(rank++, e.getRsn(), null,
                String.format("%,d/%,d", e.getObtained(), e.getTotal())));
        }
    }

    private void buildCaSection(JPanel content)
    {
        List<CaEntry> board = dashboardService.getCaBoard();
        if (board.isEmpty())
        {
            content.add(PanelUi.emptyStateLabel("No combat achievements uploaded yet."));
            return;
        }
        int rank = 1;
        for (CaEntry e : board)
        {
            content.add(statRow(rank++, e.getRsn(), e.getTier(),
                String.format("%,d", e.getPoints())));
        }
    }

    private void buildGpSection(JPanel content)
    {
        GpWeek gp = dashboardService.getGpWeek();
        JLabel total = new JLabel("Clan total: " + StatFormat.shortNumber(gp.getTotalGp())
            + " GP · " + gp.getDropCount() + " drops");
        total.setFont(FontManager.getRunescapeSmallFont());
        total.setForeground(new Color(0xffd700));
        total.setAlignmentX(LEFT_ALIGNMENT);
        total.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 0));
        content.add(total);

        if (gp.getTop().isEmpty())
        {
            content.add(PanelUi.emptyStateLabel("No logged drops in the last 7 days."));
            return;
        }
        PodiumComponent podium = new PodiumComponent();
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (WomEntry e : gp.getTop())
        {
            names.add(e.getRsn());
            values.add(StatFormat.shortNumber((long) e.getValue()));
        }
        podium.setEntries(names, values);
        content.add(podium);
        content.add(captionLabel("logged drops (1M+/notables) · last 7 days"));
    }

    private void buildRecentSection(JPanel content)
    {
        if (cachedRecent.isEmpty())
        {
            content.add(PanelUi.emptyStateLabel("No new clan bests yet."));
            return;
        }
        for (PbEntry e : cachedRecent)
        {
            content.add(recentRow(e));
        }
    }

    private void buildPbSection(JPanel content)
    {
        if (cachedBoard.isEmpty())
        {
            content.add(PanelUi.emptyStateLabel("No personal bests recorded yet."));
            return;
        }
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
        for (Map.Entry<String, String> e : sorted.entrySet())
        {
            String bossKey = e.getValue();
            boolean open = expandedPbBosses.contains(bossKey);
            content.add(CollapsibleSection.toggleHeader(e.getKey(), open, () -> {
                if (!expandedPbBosses.remove(bossKey))
                {
                    expandedPbBosses.add(bossKey);
                }
                rebuild();
            }, false));
            if (open)
            {
                for (PbEntry entry : byBoss.get(bossKey))
                {
                    content.add(pbRow(entry));
                }
            }
        }
    }

    private void buildKcSection(JPanel content)
    {
        Map<String, List<WomEntry>> boards = dashboardService.getKcBoards();
        content.add(captionLabel("top 5 per boss · via Wise Old Man" + syncedSuffix()));
        if (boards.isEmpty())
        {
            content.add(PanelUi.emptyStateLabel("waiting for WOM sync"));
            return;
        }
        for (String slug : WomStatsParser.BOSS_SLUGS)
        {
            boolean open = expandedKcBosses.contains(slug);
            content.add(CollapsibleSection.toggleHeader(WomStatsParser.bossDisplayName(slug), open, () -> {
                if (!expandedKcBosses.remove(slug))
                {
                    expandedKcBosses.add(slug);
                }
                rebuild();
            }, false));
            if (open)
            {
                List<WomEntry> rows = boards.get(slug);
                if (rows == null)
                {
                    content.add(PanelUi.emptyStateLabel("not synced yet"));
                }
                else if (rows.isEmpty())
                {
                    content.add(PanelUi.emptyStateLabel("no ranked members"));
                }
                else
                {
                    int rank = 1;
                    for (WomEntry e : rows.subList(0, Math.min(5, rows.size())))
                    {
                        content.add(statRow(rank++, e.getRsn(), null,
                            String.format("%,d", (long) e.getValue())));
                    }
                }
            }
        }
    }

    // "· synced 2h ago" appended to WOM captions; empty until first sync.
    private String syncedSuffix()
    {
        String at = dashboardService.getWomSyncedAt();
        if (at.isEmpty())
        {
            return "";
        }
        String ago = formatTimeAgo(at);
        return ago.isEmpty() ? "" : " · synced " + ago;
    }

    // ---- shared row builders ----

    private JPanel statRow(int rank, String rsn, String tier, String value)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 6));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel rankLabel = new JLabel();
        MedalIcon medal = MedalIcon.forRank(rank);
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

        JLabel name = new JLabel(rsn);
        name.setFont(FontManager.getRunescapeSmallFont());
        name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(name, BorderLayout.CENTER);

        JPanel east = new JPanel();
        east.setLayout(new BoxLayout(east, BoxLayout.X_AXIS));
        east.setBackground(ColorScheme.DARK_GRAY_COLOR);
        if (tier != null && !tier.isEmpty())
        {
            east.add(TierBadge.of(tier));
            east.add(javax.swing.Box.createRigidArea(new Dimension(5, 0)));
        }
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(FontManager.getRunescapeSmallFont());
        valueLabel.setForeground(new Color(0xffd700));
        east.add(valueLabel);
        row.add(east, BorderLayout.EAST);
        return row;
    }

    private JPanel pbRow(PbEntry entry)
    {
        return statRow(entry.getRank(), entry.getRsn(), null,
            PbFormat.formatSeconds(entry.getSeconds()));
    }

    private JPanel recentRow(PbEntry entry)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        // Plain hyphen: em dash is outside Latin-1 and may box in the
        // RuneScape font (same family of glyph failures as the old arrows).
        JLabel top = new JLabel(PbFormat.displayName(entry.getBossKey())
            + " - " + PbFormat.formatSeconds(entry.getSeconds()));
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

    private JLabel captionLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        // LEFT + full-width max, NOT CENTER_ALIGNMENT: BoxLayout shifts
        // children sideways when siblings mix alignmentX values (this is
        // what squeezed the podium off-center). Text centers via
        // horizontalAlignment inside the full-width label instead.
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return label;
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
