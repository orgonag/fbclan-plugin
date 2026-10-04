package com.github.orgonag.fbclan.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.FontManager;

/**
 * The full leaderboard pop-out: pick a board, (for clan bests) a boss,
 * find a player; your own row is highlighted. One window, reused; it
 * re-renders whenever the tab's data refreshes.
 */
class BoardWindow
{
    private static final String NEWEST = "Newest clan bests";

    private final LeaderboardsTab tab;
    private JDialog dialog;
    private Choice<Integer> boards;
    private JComboBox<String> bossBox;
    private JTextField find;
    private JLabel heading;
    private JLabel caption;
    private JPanel rows;

    BoardWindow(LeaderboardsTab tab)
    {
        this.tab = tab;
    }

    // EDT.
    void open(int index, Component anchor)
    {
        if (dialog == null) build(anchor);
        boards.set(index);
        render();
        dialog.setVisible(true);
        dialog.toFront();
    }

    // EDT. Logout / shutdown.
    void close()
    {
        if (dialog != null) dialog.dispose();
    }

    // EDT. Fresh data arrived.
    void refresh()
    {
        if (dialog != null && dialog.isVisible()) render();
    }

    private void build(Component anchor)
    {
        boards = new Choice<>(Arrays.asList(0, 1, 2, 3, 4, 5), i -> LeaderboardsTab.SHORT[i], 0, i -> render());
        bossBox = new JComboBox<>();
        bossBox.setFont(FontManager.getRunescapeSmallFont());
        bossBox.addActionListener(e -> renderRows());
        find = Theme.field("", 12);
        find.setToolTipText("Filter by player name");
        find.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { renderRows(); }
            public void removeUpdate(DocumentEvent e) { renderRows(); }
            public void changedUpdate(DocumentEvent e) { renderRows(); }
        });
        heading = Theme.heading("");
        caption = Theme.text("", Theme.FAINT);
        rows = Theme.stack(0);

        JPanel top = Theme.stack(8);
        top.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        top.add(boards);
        top.add(Theme.row(null, heading, Theme.labeled("Find", find)));
        top.add(bossBox);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(Theme.SURFACE);
        holder.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        holder.add(rows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        scroll.getViewport().setBackground(Theme.BG);
        Theme.slim(scroll);
        caption.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 14));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(caption, BorderLayout.SOUTH);

        dialog = new JDialog(SwingUtilities.getWindowAncestor(anchor), "Final Boss · Leaderboards");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getRootPane().registerKeyboardAction(e -> close(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosed(WindowEvent e)
            {
                dialog = null;
            }
        });
        dialog.setContentPane(root);
        dialog.setSize(new Dimension(520, 560));
        dialog.setLocationRelativeTo(dialog.getOwner());
    }

    // Board changed or data refreshed: heading, boss list, then the rows.
    private void render()
    {
        int index = boards.value();
        heading.setText(LeaderboardsTab.TITLES[index]);
        boolean pbs = index == 4;
        bossBox.setVisible(pbs);
        if (pbs)
        {
            Object picked = bossBox.getSelectedItem();
            List<String> names = new ArrayList<>();
            names.add(NEWEST);
            names.addAll(tab.bosses().keySet());
            bossBox.removeAllItems();
            names.forEach(bossBox::addItem);
            bossBox.setSelectedItem(picked != null && names.contains(picked) ? picked : NEWEST);
        }
        renderRows();
    }

    private void renderRows()
    {
        if (boards == null) return;
        int index = boards.value();
        String bossName = index == 4 ? (String) bossBox.getSelectedItem() : null;
        Map<String, String> bosses = tab.bosses();
        LeaderboardsTab.Board board = tab.board(index, bossName == null || NEWEST.equals(bossName) ? null : bosses.get(bossName));
        LeaderboardsTab.Row mine = tab.mine(board);
        String query = find.getText().trim().toLowerCase(Locale.ROOT);
        rows.removeAll();
        if (board.rows.isEmpty()) rows.add(Theme.centered(board.empty));
        int shown = 0;
        for (LeaderboardsTab.Row r : board.rows)
        {
            if (!query.isEmpty() && !r.name.toLowerCase(Locale.ROOT).contains(query)) continue;
            shown++;
            rows.add(line(r, r == mine));
        }
        if (!board.rows.isEmpty() && shown == 0) rows.add(Theme.centered("No player matches \"" + find.getText().trim() + "\"."));
        caption.setText(board.caption + " · " + board.rows.size() + (board.rows.size() == 1 ? " entry" : " entries"));
        rows.revalidate();
        rows.repaint();
    }

    private static JPanel line(LeaderboardsTab.Row r, boolean mine)
    {
        JLabel rank = Theme.bold(Integer.toString(r.rank), r.rank >= 1 && r.rank <= 3 ? LeaderboardsTab.PLACE[r.rank - 1] : Theme.FAINT);
        rank.setPreferredSize(new Dimension(28, 22));
        JPanel east = Theme.row(r.tier == null || r.tier.isEmpty() ? null : LeaderboardsTab.tierBadge(r.tier), null, Theme.bold(r.value, Theme.GOLD));
        JPanel row = Theme.row(rank, mine ? Theme.bold(r.name + "  (you)", Theme.TEXT) : Theme.text(r.name, Theme.SOFT), east);
        if (!mine) return row;
        Card highlight = new Card(Theme.ACCENT_BG, Theme.ACCENT);
        highlight.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        highlight.add(row);
        return highlight;
    }
}
