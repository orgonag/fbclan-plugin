package com.github.orgonag.fbclan.panel;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Permanent host panel for the sidebar NavigationButton. RuneLite fixes a
 * button's panel at build time, so swapping locked/main by rebuilding the
 * button closes the sidebar if the user has it open when verification
 * finishes. Hosting both panels as cards keeps a single button for the
 * plugin's lifetime — verification just flips the visible card.
 */
public class RootPanel extends PluginPanel
{
    private static final String LOCKED_CARD = "LOCKED";
    private static final String MAIN_CARD = "MAIN";

    private final CardLayout cardLayout;
    private final JPanel content;

    public RootPanel(LockedPanel lockedPanel, FinalBossPanel mainPanel)
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        cardLayout = new CardLayout();
        content = new JPanel(cardLayout);
        content.add(lockedPanel, LOCKED_CARD);
        content.add(mainPanel, MAIN_CARD);
        add(content, BorderLayout.CENTER);

        cardLayout.show(content, LOCKED_CARD);
    }

    public void showLocked()
    {
        cardLayout.show(content, LOCKED_CARD);
    }

    public void showMain()
    {
        cardLayout.show(content, MAIN_CARD);
    }
}
