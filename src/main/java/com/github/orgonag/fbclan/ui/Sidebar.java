package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.FinalBossPlugin;
import com.github.orgonag.fbclan.core.Clan;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
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
    private final Theme.Btn retry;
    private final CardLayout tabCards = new CardLayout();
    private final JPanel tabs = new JPanel(tabCards);
    private final Map<String, Theme.Btn> tabButtons = new LinkedHashMap<>();
    private final Map<String, Tab> tabPanels = new LinkedHashMap<>();
    private final DropLogTab dropLog;
    private final PartiesTab parties;
    private String activeTab = "Drop Log";

    @Inject
    public Sidebar(Clan clan, ItemManager items, AnnouncementsTab announcements, DropLogTab dropLog,
                   PartiesTab parties, LeaderboardsTab leaderboards)
    {
        super(false);
        this.dropLog = dropLog;
        this.parties = parties;
        setLayout(new BorderLayout());
        setBackground(Theme.BG);

        // ---- locked card ----
        JPanel locked = Theme.stack(12);
        locked.setBorder(BorderFactory.createEmptyBorder(24, 16, 16, 16));
        JLabel logo = new JLabel(new ImageIcon(ImageUtil.loadImageResource(FinalBossPlugin.class, "icon.png")));
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel title = Theme.heading("Final Boss");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        retry = Theme.button("Retry", Theme.Btn.Kind.PRIMARY, clan::verify);
        locked.add(logo);
        locked.add(title);
        locked.add(status);
        locked.add(retry);
        JPanel lockedHolder = new JPanel(new BorderLayout());
        lockedHolder.setBackground(Theme.BG);
        lockedHolder.add(locked, BorderLayout.NORTH);

        // ---- main card: icon tab bar over the tabs ----
        JPanel bar = new JPanel(new GridLayout(1, 4, 4, 0));
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        bar.add(tab("Announcements", ItemID.LEAFLET_DROPPER_FLYER, 1, announcements, items));
        bar.add(tab("Drop Log", ItemID.COINS, 10_000, dropLog, items));
        bar.add(tab("Looking for Group", ItemID.RED_PARTYHAT, 1, parties, items));
        bar.add(tab("Leaderboards", ItemID.TZHAAR_CAPE_FIRE, 1, leaderboards, items));
        tabs.setBackground(Theme.BG);
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.BG);
        main.add(bar, BorderLayout.NORTH);
        main.add(tabs, BorderLayout.CENTER);

        root.add(lockedHolder, LOCKED);
        root.add(main, MAIN);
        add(root, BorderLayout.CENTER);
        show(Clan.Status.VERIFYING);
        select(activeTab);
    }

    private Theme.Btn tab(String name, int itemId, int quantity, Tab panel, ItemManager items)
    {
        Theme.Btn b = Theme.button("", Theme.Btn.Kind.TAB, () -> {
            select(name);
            panel.refresh();
        });
        b.setToolTipText(name);
        b.setPreferredSize(new Dimension(10, 36));
        items.getImage(itemId, quantity, quantity > 1).addTo(b);
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
    }

    public void refreshActiveTab()
    {
        tabPanels.get(activeTab).refresh();
    }

    // EDT. Logout, profile change, shutdown: close the pop-out windows.
    public void closeWindows()
    {
        dropLog.closeViewer();
        parties.closeWizard();
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
                status.setText("You're not a member of Final Boss.\n\nVisit wiseoldman.net/groups/" + Clan.WOM_GROUP_ID + " for more info.");
                retry.setVisible(false);
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
    }
}
