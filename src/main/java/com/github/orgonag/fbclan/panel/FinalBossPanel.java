package com.github.orgonag.fbclan.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class FinalBossPanel extends PluginPanel
{
    private static final String DROP_LOG_TAB = "DROP_LOG";
    private static final String LFG_TAB = "LFG";

    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final JButton dropLogButton;
    private final JButton lfgButton;
    private final DropLogPanel dropLogPanel;
    private final LfgPanel lfgPanel;

    private String activeTab = DROP_LOG_TAB;

    public FinalBossPanel(DropLogPanel dropLogPanel, LfgPanel lfgPanel)
    {
        super(false);

        this.dropLogPanel = dropLogPanel;
        this.lfgPanel = lfgPanel;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        tabBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        dropLogButton = new JButton("Drop Log");
        lfgButton = new JButton("LFG");

        dropLogButton.addActionListener(e -> switchTab(DROP_LOG_TAB));
        lfgButton.addActionListener(e -> switchTab(LFG_TAB));

        tabBar.add(dropLogButton);
        tabBar.add(lfgButton);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        contentPanel.add(dropLogPanel, DROP_LOG_TAB);
        contentPanel.add(lfgPanel, LFG_TAB);

        add(tabBar, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        updateTabStyles();
    }

    private void switchTab(String tab)
    {
        activeTab = tab;
        cardLayout.show(contentPanel, tab);
        updateTabStyles();

        if (DROP_LOG_TAB.equals(tab))
        {
            dropLogPanel.refresh();
        }
        else
        {
            lfgPanel.refresh();
        }
    }

    private void updateTabStyles()
    {
        dropLogButton.setBackground(DROP_LOG_TAB.equals(activeTab)
            ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR);
        lfgButton.setBackground(LFG_TAB.equals(activeTab)
            ? ColorScheme.BRAND_ORANGE : ColorScheme.DARK_GRAY_COLOR);
    }

    public void refreshActiveTab()
    {
        if (DROP_LOG_TAB.equals(activeTab))
        {
            dropLogPanel.refresh();
        }
        else
        {
            lfgPanel.refresh();
        }
    }
}
