package com.github.orgonag.fbclan.panel;

import java.awt.Dimension;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Shared Swing helpers for the sidebar panels. Every label uses
 * LEFT_ALIGNMENT + a full-width max and centers its text via
 * horizontalAlignment instead of CENTER_ALIGNMENT: BoxLayout shifts
 * children sideways when siblings mix alignmentX values.
 */
final class PanelUi
{
    private PanelUi()
    {
    }

    // Centered gray one-liner for "nothing here yet" states.
    static JLabel emptyStateLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        return label;
    }

    // The panels' shared refresh shape: fetch on the executor (network),
    // apply the result on the EDT. Never block the EDT on network.
    static <T> void asyncRefresh(ScheduledExecutorService executor,
        Supplier<T> fetch, Consumer<T> applyOnEdt)
    {
        executor.submit(() -> {
            T result = fetch.get();
            SwingUtilities.invokeLater(() -> applyOnEdt.accept(result));
        });
    }
}
