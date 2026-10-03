package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.pbs.Leaderboards;
import com.github.orgonag.fbclan.pbs.Leaderboards.Entry;
import com.github.orgonag.fbclan.pbs.PbFormat;
import com.github.orgonag.fbclan.stats.Dashboard;
import com.github.orgonag.fbclan.stats.Dashboard.Named;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.DoubleFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The clan dashboard as six highlight cards (weekly XP and EHB from Wise
 * Old Man, collection log, combat achievements, clan bests, GP this
 * week): top three and your own rank. A card opens the full board in
 * {@link BoardWindow}.
 */
@Singleton
public class LeaderboardsTab extends Tab
{
    static final String[] TITLES = {
        "XP Gained This Week", "EHB This Week", "Collection Log", "Combat Achievements", "Clan Bests", "GP This Week",
    };
    static final String[] SHORT = {"XP week", "EHB week", "Col log", "Combat ach.", "PBs", "GP week"};
    static final Color[] PLACE = {new Color(0xFFD700), new Color(0xC8C8C8), new Color(0xCD7F32)};

    /** One ranked line. `tier` is the CA tier, else null. */
    static final class Row
    {
        final int rank;
        final String rsn;
        final String name;
        final String value;
        final String tier;

        Row(int rank, String rsn, String name, String value, String tier)
        {
            this.rank = rank;
            this.rsn = rsn;
            this.name = name;
            this.value = value;
            this.tier = tier;
        }
    }

    /** A whole board: its rows, the line shown when there are none, and a source caption. */
    static final class Board
    {
        final List<Row> rows;
        final String empty;
        final String caption;

        Board(List<Row> rows, String empty, String caption)
        {
            this.rows = rows;
            this.empty = empty;
            this.caption = caption;
        }
    }

    private final Leaderboards pbs;
    private final Dashboard dashboard;
    private final Clan clan;
    private final BoardWindow window;

    @Inject
    public LeaderboardsTab(Leaderboards pbs, Dashboard dashboard, Clan clan, ScheduledExecutorService executor)
    {
        super("Leaderboards", executor);
        this.pbs = pbs;
        this.dashboard = dashboard;
        this.clan = clan;
        this.window = new BoardWindow(this);
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

    void closeWindow()
    {
        window.close();
    }

    private void render()
    {
        String at = dashboard.womSyncedAt();
        note.setText(at.isEmpty() ? "" : "synced " + Theme.timeAgo(at));
        fill(() -> {
            for (int i = 0; i < TITLES.length; i++)
            {
                list.add(card(i));
            }
        });
        window.refresh();
    }

    // Title, #1 large, #2 and #3 small, then your rank (or the clan total for GP).
    private JPanel card(int index)
    {
        Board b = board(index, null);
        Theme.Card card = Theme.card(null);
        card.add(Theme.row(null, Theme.caps(TITLES[index]), Theme.text("›", Theme.FAINT)));
        if (b.rows.isEmpty())
        {
            card.add(Theme.text(b.empty, Theme.SUB));
        }
        else
        {
            Row first = b.rows.get(0);
            JLabel name = Theme.bold(first.name, Theme.TEXT);
            name.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
            JLabel value = Theme.bold(first.value, Theme.GOLD);
            value.setFont(net.runelite.client.ui.FontManager.getRunescapeBoldFont());
            // Clan bests are newest-first, not a ranking: no place numbers there.
            boolean ranked = index != 4;
            card.add(Theme.row(Theme.bold(ranked ? "1" : "•", PLACE[0]), name, value));
            JPanel runners = Theme.stack(1);
            for (int i = 1; i < Math.min(3, b.rows.size()); i++)
            {
                Row r = b.rows.get(i);
                runners.add(Theme.row(Theme.bold(ranked ? Integer.toString(i + 1) : "•", ranked ? PLACE[i] : Theme.FAINT),
                    Theme.text(r.name, Theme.SOFT), Theme.text(r.value, Theme.GOLD)));
            }
            card.add(runners);
        }
        String foot = footer(index, b);
        if (foot != null) card.add(Theme.text(foot, Theme.ACCENT_HI));
        card.setToolTipText("Open the full " + TITLES[index] + " board");
        return Theme.onClick(card, () -> window.open(index, card));
    }

    // "You: #24 · 4.7M", the clan GP total, or the newest best's age.
    private String footer(int index, Board b)
    {
        if (index == 5)
        {
            Dashboard.GpWeek gp = dashboard.gpWeek();
            return "Clan: " + Dashboard.shortNumber(gp.getTotalGp()) + " GP · " + gp.getDropCount() + " drops";
        }
        if (index == 4)
        {
            return pbs.recent().isEmpty() ? null : "Newest " + Theme.timeAgo(pbs.recent().get(0).getAchievedAt());
        }
        Row mine = mine(b);
        return mine == null ? null : "You: #" + mine.rank + " · " + mine.value;
    }

    Row mine(Board b)
    {
        String rsn = clan.rsn();
        for (Row r : b.rows)
        {
            if (rsn != null && r.rsn != null && Names.same(r.rsn, rsn)) return r;
        }
        return null;
    }

    // ------------------------------------------------------------ data

    // `boss` only matters for clan bests: null = the newest bests across bosses.
    Board board(int index, String boss)
    {
        String synced = dashboard.womSyncedAt().isEmpty() ? "" : " · synced " + Theme.timeAgo(dashboard.womSyncedAt());
        switch (index)
        {
            case 0:
                return named(dashboard.xpWeek(), v -> Dashboard.shortNumber((long) v), "via Wise Old Man" + synced);
            case 1:
                return named(dashboard.ehbWeek(), Dashboard::oneDecimal, "efficient hours bossed" + synced);
            case 2:
            {
                List<Row> rows = new ArrayList<>();
                int rank = 1;
                for (Dashboard.ClEntry e : dashboard.clBoard())
                {
                    rows.add(new Row(rank++, e.getRsn(), e.getRsn(), String.format("%,d/%,d", e.getObtained(), e.getTotal()), null));
                }
                return new Board(rows, "No collection logs uploaded yet.", "uploaded by members' clients");
            }
            case 3:
            {
                List<Row> rows = new ArrayList<>();
                int rank = 1;
                for (Dashboard.CaEntry e : dashboard.caBoard())
                {
                    rows.add(new Row(rank++, e.getRsn(), e.getRsn(), String.format("%,d", e.getPoints()), e.getTier()));
                }
                return new Board(rows, "No combat achievements uploaded yet.", "uploaded by members' clients");
            }
            case 4:
            {
                List<Row> rows = new ArrayList<>();
                if (boss == null)
                {
                    int rank = 1;
                    for (Entry e : pbs.recent())
                    {
                        rows.add(new Row(rank++, e.getRsn(), PbFormat.boss(e.getBossKey()) + " · " + e.getRsn(),
                            PbFormat.seconds(e.getSeconds()), null));
                    }
                    return new Board(rows, "No new clan bests yet.", "newest first");
                }
                for (Entry e : pbs.board())
                {
                    if (e.getBossKey().equals(boss))
                    {
                        rows.add(new Row(e.getRank(), e.getRsn(), e.getRsn(), PbFormat.seconds(e.getSeconds()), null));
                    }
                }
                return new Board(rows, "No personal bests recorded yet.", "all-time");
            }
            default:
            {
                Dashboard.GpWeek gp = dashboard.gpWeek();
                Board b = named(gp.getTop(), v -> Dashboard.shortNumber((long) v), "logged drops (1M+/notables) · last 7 days");
                return new Board(b.rows, "No logged drops in the last 7 days.", b.caption);
            }
        }
    }

    // All-time PB bosses, display name -> key, sorted by name.
    Map<String, String> bosses()
    {
        Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry e : pbs.board())
        {
            out.put(PbFormat.boss(e.getBossKey()), e.getBossKey());
        }
        return out;
    }

    private static Board named(List<Named> entries, DoubleFunction<String> fmt, String caption)
    {
        if (entries == null) return new Board(Collections.emptyList(), "waiting for WOM sync", caption);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++)
        {
            Named e = entries.get(i);
            rows.add(new Row(i + 1, e.getRsn(), e.getRsn(), fmt.apply(e.getValue()), null));
        }
        return new Board(rows, "No data this week.", caption);
    }

    // GM cyan, Master red, Elite gold, others grey.
    static JLabel tierBadge(String tier)
    {
        switch (tier)
        {
            case "Grandmaster": return Theme.bold("GM", new Color(0x7DF9FF));
            case "Master": return Theme.bold("MASTER", new Color(0xFF6B6B));
            case "Elite": return Theme.bold("ELITE", Theme.GOLD);
            default: return Theme.bold(tier.toUpperCase(java.util.Locale.ROOT), Theme.FAINT);
        }
    }
}
