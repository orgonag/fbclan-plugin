package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.core.Clan;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar panel: a locked card until verification passes, then the
 * four tabs. One panel for the plugin's lifetime (RuneLite fixes a
 * navigation button's panel at build time), so verification just flips
 * the visible card.
 */
@Singleton
public class Sidebar extends PluginPanel
{
    private static final String LOCKED = "LOCKED";
    private static final String MAIN = "MAIN";

    private final Clan clan;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JLabel status = new JLabel();
    private final JButton retry = new JButton("Retry");
    private final CardLayout tabCards = new CardLayout();
    private final JPanel tabs = new JPanel(tabCards);
    private final Map<String, JButton> tabButtons = new LinkedHashMap<>();
    private final Map<String, Runnable> refreshers = new LinkedHashMap<>();
    private String activeTab = "Drop Log";

    @Inject
    public Sidebar(Clan clan, AnnouncementsTab announcements, DropLogTab dropLog, PartiesTab parties, LeaderboardsTab leaderboards)
    {
        super(false);
        this.clan = clan;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // ---- locked card ----
        JPanel locked = new JPanel(new BorderLayout());
        locked.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(ColorScheme.DARK_GRAY_COLOR);
        center.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        JLabel title = new JLabel("Final Boss");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(CENTER_ALIGNMENT);
        status.setForeground(Ui.MUTED);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        status.setAlignmentX(CENTER_ALIGNMENT);
        retry.setAlignmentX(CENTER_ALIGNMENT);
        retry.addActionListener(e -> clan.verify());
        center.add(title);
        center.add(Box.createRigidArea(new Dimension(0, 15)));
        center.add(status);
        center.add(Box.createRigidArea(new Dimension(0, 10)));
        center.add(retry);
        locked.add(center, BorderLayout.NORTH);

        // ---- main card: two-row tab bar (Announcements on top) ----
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        top.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
        top.add(tab("Announcements", announcements, announcements::refresh), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        bottom.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bottom.add(tab("Drop Log", dropLog, dropLog::refresh));
        bottom.add(tab("LFG", parties, parties::refresh));
        // "PBs" not "Leaderboards": a third wide button wraps off the row.
        bottom.add(tab("PBs", leaderboards, leaderboards::refresh));
        bar.add(top);
        bar.add(bottom);
        tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
        main.add(bar, BorderLayout.NORTH);
        main.add(tabs, BorderLayout.CENTER);

        root.add(locked, LOCKED);
        root.add(main, MAIN);
        add(root, BorderLayout.CENTER);
        show(Clan.Status.VERIFYING);
        select(activeTab);
    }

    private JButton tab(String name, JPanel panel, Runnable refresh)
    {
        JButton b = new JButton(name);
        b.addActionListener(e -> {
            select(name);
            refresh.run();
        });
        tabButtons.put(name, b);
        refreshers.put(name, refresh);
        tabs.add(panel, name);
        return b;
    }

    private void select(String name)
    {
        activeTab = name;
        tabCards.show(tabs, name);
        tabButtons.forEach((n, b) -> b.setBackground(n.equals(name) ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR));
    }

    public void refreshActiveTab()
    {
        refreshers.get(activeTab).run();
    }

    // EDT.
    public void show(Clan.Status s)
    {
        switch (s)
        {
            case MEMBER:
                cards.show(root, MAIN);
                return;
            case NOT_MEMBER:
                status.setText("<html><center>You're not a member of Final Boss.<br><br>Visit wiseoldman.net/groups/"
                    + Clan.WOM_GROUP_ID + " for more info.</center></html>");
                retry.setVisible(false);
                break;
            case ERROR:
                status.setText("<html><center>Couldn't verify membership — click to retry.</center></html>");
                retry.setVisible(true);
                break;
            default:
                status.setText("<html><center>Verifying clan membership...</center></html>");
                retry.setVisible(false);
                break;
        }
        cards.show(root, LOCKED);
    }
}
