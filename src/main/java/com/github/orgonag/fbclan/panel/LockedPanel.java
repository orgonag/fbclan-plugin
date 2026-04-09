package com.github.orgonag.fbclan.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionListener;
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

public class LockedPanel extends PluginPanel
{
    private final JLabel statusLabel;
    private final JButton retryButton;

    public LockedPanel()
    {
        super(false);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JLabel titleLabel = new JLabel("Final Boss");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(CENTER_ALIGNMENT);

        statusLabel = new JLabel("<html><center>Verifying clan membership...</center></html>");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);

        retryButton = new JButton("Retry");
        retryButton.setAlignmentX(CENTER_ALIGNMENT);
        retryButton.setVisible(false);

        centerPanel.add(titleLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        centerPanel.add(statusLabel);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        centerPanel.add(retryButton);

        add(centerPanel, BorderLayout.NORTH);
    }

    public void showVerifying()
    {
        statusLabel.setText("<html><center>Verifying clan membership...</center></html>");
        retryButton.setVisible(false);
    }

    public void showNotMember()
    {
        statusLabel.setText("<html><center>You're not a member of Final Boss.<br><br>"
            + "Visit wiseoldman.net/groups/1055 for more info.</center></html>");
        retryButton.setVisible(false);
    }

    public void showError()
    {
        statusLabel.setText("<html><center>Couldn't verify membership — click to retry.</center></html>");
        retryButton.setVisible(true);
    }

    public void setRetryAction(ActionListener listener)
    {
        for (ActionListener al : retryButton.getActionListeners())
        {
            retryButton.removeActionListener(al);
        }
        retryButton.addActionListener(listener);
    }
}
