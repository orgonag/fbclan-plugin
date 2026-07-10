package com.github.orgonag.fbclan.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class FinalBossPanel extends PluginPanel
{
    private static final String ANNOUNCEMENTS_TAB = "ANNOUNCEMENTS";
    private static final String DROP_LOG_TAB = "DROP_LOG";
    private static final String LFG_TAB = "LFG";

    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final JButton announcementsButton;
    private final JButton dropLogButton;
    private final JButton lfgButton;
    private final AnnouncementsPanel announcementsPanel;
    private final DropLogPanel dropLogPanel;
    private final LfgPanel lfgPanel;

    private String activeTab = DROP_LOG_TAB;

    public FinalBossPanel(AnnouncementsPanel announcementsPanel, DropLogPanel dropLogPanel, LfgPanel lfgPanel)
    {
        super(false);

        this.announcementsPanel = announcementsPanel;
        this.dropLogPanel = dropLogPanel;
        this.lfgPanel = lfgPanel;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Two-row tab bar: Announcements full-width on top; Drop Log + LFG
        // below (a future Leaderboards button joins the bottom row).
        JPanel tabBar = new JPanel();
        tabBar.setLayout(new BoxLayout(tabBar, BoxLayout.Y_AXIS));
        tabBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        announcementsButton = new JButton("Announcements");
        dropLogButton = new JButton("Drop Log");
        lfgButton = new JButton("LFG");

        announcementsButton.addActionListener(e -> switchTab(ANNOUNCEMENTS_TAB));
        dropLogButton.addActionListener(e -> switchTab(DROP_LOG_TAB));
        lfgButton.addActionListener(e -> switchTab(LFG_TAB));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        topRow.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
        topRow.add(announcementsButton, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        bottomRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bottomRow.add(dropLogButton);
        bottomRow.add(lfgButton);

        tabBar.add(topRow);
        tabBar.add(bottomRow);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        contentPanel.add(announcementsPanel, ANNOUNCEMENTS_TAB);
        contentPanel.add(dropLogPanel, DROP_LOG_TAB);
        contentPanel.add(lfgPanel, LFG_TAB);

        add(tabBar, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        cardLayout.show(contentPanel, activeTab);
        updateTabStyles();
    }

    private void switchTab(String tab)
    {
        activeTab = tab;
        cardLayout.show(contentPanel, tab);
        updateTabStyles();
        refreshActiveTab();
    }

    private void updateTabStyles()
    {
        announcementsButton.setBackground(ANNOUNCEMENTS_TAB.equals(activeTab)
            ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR);
        dropLogButton.setBackground(DROP_LOG_TAB.equals(activeTab)
            ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR);
        lfgButton.setBackground(LFG_TAB.equals(activeTab)
            ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR);
    }

    public void refreshActiveTab()
    {
        if (ANNOUNCEMENTS_TAB.equals(activeTab))
        {
            announcementsPanel.refresh();
        }
        else if (DROP_LOG_TAB.equals(activeTab))
        {
            dropLogPanel.refresh();
        }
        else
        {
            lfgPanel.refresh();
        }
    }
}
