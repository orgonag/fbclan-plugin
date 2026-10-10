package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.FinalBossPlugin;
import com.github.orgonag.fbclan.core.Clan;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.geom.Path2D;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * The sidebar panel: a locked card until verification passes, then the
 * icon tab bar and the four tabs. One panel for the plugin's lifetime
 * (RuneLite fixes a navigation button's panel at build time), so
 * verification just flips the visible card.
 */
@Singleton
public class Sidebar extends PluginPanel
{
    private static final String LOCKED = "LOCKED";
    private static final String MAIN = "MAIN";

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JTextArea status = Theme.wrap("", Theme.SOFT);
    private final Btn retry;
    private final CardLayout tabCards = new CardLayout();
    private final JPanel tabs = new JPanel(tabCards);
    private final Map<String, Btn> tabButtons = new LinkedHashMap<>();
    private final Map<String, Tab> tabPanels = new LinkedHashMap<>();
    private final AnnouncementsTab announcements;
    private final DropLogTab dropLog;
    private final PartiesTab parties;
    private final LeaderboardsTab leaderboards;
    private String activeTab = "Drop Log";
    // EDT. True while the member panel (not the locked card) is showing.
    private boolean unlocked;

    @Inject
    public Sidebar(Clan clan, AnnouncementsTab announcements, DropLogTab dropLog,
                   PartiesTab parties, LeaderboardsTab leaderboards)
    {
        super(false);
        this.announcements = announcements;
        this.dropLog = dropLog;
        this.parties = parties;
        this.leaderboards = leaderboards;
        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        // ---- locked card ----
        JPanel locked = Theme.stack(12);
        locked.setBorder(BorderFactory.createEmptyBorder(24, 16, 16, 16));
        JLabel logo = new JLabel(new ImageIcon(ImageUtil.loadImageResource(FinalBossPlugin.class, "icon.png")));
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel title = Theme.heading("Final Boss");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        retry = Theme.button("Retry", Btn.Kind.PRIMARY, clan::verify);
        locked.add(logo);
        locked.add(title);
        locked.add(status);
        locked.add(retry);
        JPanel lockedHolder = new JPanel(new BorderLayout());
        lockedHolder.setBackground(Theme.BG);
        lockedHolder.add(locked, BorderLayout.NORTH);

        // ---- main card: bell + word tabs over the tabs ----
        Btn bell = tab("Announcements", "", announcements);
        bell.setIcon(new Bell(announcements));
        bell.setPreferredSize(new Dimension(40, 32));
        announcements.setUnreadListener(bell::repaint);
        JPanel words = new JPanel(new GridLayout(1, 3, 4, 0));
        words.setOpaque(false);
        words.add(tab("Drop Log", "Drops", dropLog));
        words.add(tab("Looking for Group", "LFG", parties));
        words.add(tab("Leaderboards", "PBs", leaderboards));
        JPanel bar = new JPanel(new BorderLayout(4, 0));
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        bar.add(bell, BorderLayout.WEST);
        bar.add(words, BorderLayout.CENTER);
        tabs.setBackground(Theme.BG);
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);
        main.add(bar, BorderLayout.NORTH);
        main.add(tabs, BorderLayout.CENTER);

        root.add(lockedHolder, LOCKED);
        root.add(main, MAIN);
        add(root, BorderLayout.CENTER);
        showLoggedOut();
        select(activeTab);
    }

    private Btn tab(String name, String label, Tab panel)
    {
        Btn b = Theme.button(label, Btn.Kind.TAB, () -> {
            select(name);
            panel.refresh();
        });
        b.setFont(FontManager.getRunescapeBoldFont());
        b.setToolTipText(name);
        b.setPreferredSize(new Dimension(10, 32));
        tabButtons.put(name, b);
        tabPanels.put(name, panel);
        tabs.add(panel, name);
        return b;
    }

    private void select(String name)
    {
        activeTab = name;
        tabCards.show(tabs, name);
        tabButtons.forEach((n, b) -> b.setOn(n.equals(name)));
        if (name.equals("Announcements")) announcements.markRead();
    }

    public void refreshActiveTab()
    {
        tabPanels.get(activeTab).refresh();
    }

    // The panel was opened: tabs don't refresh while it is closed.
    @Override
    public void onActivate()
    {
        if (unlocked) refreshActiveTab();
    }

    // EDT. Logout, profile change, shutdown: close the pop-out windows.
    public void closeWindows()
    {
        dropLog.closeViewer();
        parties.closeWizard();
        leaderboards.closeWindow();
    }

    // A bell, drawn so it needs no asset, with a red dot while there's news you haven't opened.
    private static final class Bell implements Icon
    {
        private final AnnouncementsTab announcements;

        Bell(AnnouncementsTab announcements)
        {
            this.announcements = announcements;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = Theme.smooth(g);
            g2.setColor(c.getForeground());
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D body = new Path2D.Float();
            body.moveTo(x + 3, y + 11);
            body.lineTo(x + 3, y + 7);
            body.curveTo(x + 3, y + 1.5f, x + 13, y + 1.5f, x + 13, y + 7);
            body.lineTo(x + 13, y + 11);
            body.lineTo(x + 14.5f, y + 12.5f);
            body.lineTo(x + 1.5f, y + 12.5f);
            body.closePath();
            g2.draw(body);
            g2.drawLine(x + 6, y + 15, x + 10, y + 15);
            if (announcements.unread())
            {
                g2.setColor(Theme.RED);
                g2.fillOval(x + 11, y - 1, 6, 6);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth()
        {
            return 16;
        }

        @Override
        public int getIconHeight()
        {
            return 16;
        }
    }

    // EDT. Nobody is logged in: locked, with nothing to wait for.
    public void showLoggedOut()
    {
        status.setText("Log in on your clan character to open the clan panel.");
        retry.setVisible(false);
        cards.show(root, LOCKED);
        unlocked = false;
    }

    // EDT.
    public void show(Clan.Status s)
    {
        switch (s)
        {
            case MEMBER:
                cards.show(root, MAIN);
                unlocked = true;
                return;
            case NOT_MEMBER:
                status.setText("You're not a member of Final Boss.\n\nVisit wiseoldman.net/groups/" + Clan.WOM_GROUP_ID + " for more info.\n\nJust joined? Retry checks again.");
                retry.setVisible(true);
                break;
            case ERROR:
                status.setText("Couldn't verify membership — click to retry.");
                retry.setVisible(true);
                break;
            default:
                status.setText("Verifying clan membership...");
                retry.setVisible(false);
                break;
        }
        cards.show(root, LOCKED);
        unlocked = false;
    }
}
