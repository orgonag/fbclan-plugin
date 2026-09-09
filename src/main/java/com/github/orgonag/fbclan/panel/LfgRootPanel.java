package com.github.orgonag.fbclan.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * The LFG tab: a two-way switch between hosted parties (browse, apply,
 * host) and the original "looking" status board.
 */
public class LfgRootPanel extends JPanel
{
    private static final String PARTIES = "PARTIES";
    private static final String LOOKING = "LOOKING";

    private final CardLayout cardLayout;
    private final JPanel content;
    private final JButton partiesButton;
    private final JButton lookingButton;
    private final LfgPartiesPanel partiesPanel;
    private final LfgPanel lookingPanel;
    private String active = PARTIES;

    public LfgRootPanel(LfgPartiesPanel partiesPanel, LfgPanel lookingPanel)
    {
        this.partiesPanel = partiesPanel;
        this.lookingPanel = lookingPanel;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel switcher = new JPanel(new GridLayout(1, 2, 5, 0));
        switcher.setBackground(ColorScheme.DARK_GRAY_COLOR);
        switcher.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
        partiesButton = new JButton("Parties");
        lookingButton = new JButton("Looking");
        partiesButton.addActionListener(e -> show(PARTIES));
        lookingButton.addActionListener(e -> show(LOOKING));
        switcher.add(partiesButton);
        switcher.add(lookingButton);

        cardLayout = new CardLayout();
        content = new JPanel(cardLayout);
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.add(partiesPanel, PARTIES);
        content.add(lookingPanel, LOOKING);

        add(switcher, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        cardLayout.show(content, active);
        updateStyles();
    }

    private void show(String card)
    {
        active = card;
        cardLayout.show(content, card);
        updateStyles();
        refresh();
    }

    private void updateStyles()
    {
        partiesButton.setBackground(PARTIES.equals(active) ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
        lookingButton.setBackground(LOOKING.equals(active) ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
    }

    // Refreshes whichever board is showing (tab switches / user actions).
    public void refresh()
    {
        if (PARTIES.equals(active))
        {
            partiesPanel.refresh();
        }
        else
        {
            lookingPanel.refresh();
        }
    }
}
