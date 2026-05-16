package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.drops.DropTrackingService;
import com.github.orgonag.fbclan.drops.SupabaseDropService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class DropLogPanel extends JPanel
{
    private final SupabaseDropService dropService;
    private final ScheduledExecutorService executor;
    private final JPanel listPanel;

    public DropLogPanel(SupabaseDropService dropService, ScheduledExecutorService executor)
    {
        this.dropService = dropService;
        this.executor = executor;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void refresh()
    {
        executor.submit(() -> {
            JsonArray drops = dropService.getRecentDrops(50);
            SwingUtilities.invokeLater(() -> {
                listPanel.removeAll();

                if (drops.size() == 0)
                {
                    JLabel emptyLabel = new JLabel("No drops logged yet.");
                    emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                    emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    listPanel.add(emptyLabel);
                }
                else
                {
                    for (JsonElement element : drops)
                    {
                        JsonObject drop = element.getAsJsonObject();
                        listPanel.add(createDropRow(drop));
                    }
                }

                listPanel.revalidate();
                listPanel.repaint();
            });
        });
    }

    private JPanel createDropRow(JsonObject drop)
    {
        JPanel row = new JPanel();
        row.setLayout(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        String itemName = escapeHtml(drop.get("item_name").getAsString());
        long geValue = drop.get("ge_value").getAsLong();
        String rsn = escapeHtml(drop.get("rsn").getAsString());
        String npcName = escapeHtml(drop.get("npc_name").getAsString());

        JLabel mainLabel = new JLabel("<html><b>" + itemName + "</b> (" + DropTrackingService.formatGp(geValue) + " GP)</html>");
        mainLabel.setForeground(Color.WHITE);
        mainLabel.setFont(FontManager.getRunescapeSmallFont());

        String timeAgo = formatTimeAgo(drop.get("created_at").getAsString());
        JLabel detailLabel = new JLabel(rsn + " \u2014 " + npcName + " \u2014 " + timeAgo);
        detailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        detailLabel.setFont(FontManager.getRunescapeSmallFont());

        row.add(mainLabel, BorderLayout.NORTH);
        row.add(detailLabel, BorderLayout.SOUTH);

        return row;
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String formatTimeAgo(String isoTimestamp)
    {
        try
        {
            Instant then = OffsetDateTime.parse(isoTimestamp).toInstant();
            Duration duration = Duration.between(then, Instant.now());

            long minutes = duration.toMinutes();
            if (minutes < 1) return "just now";
            if (minutes < 60) return minutes + " min ago";

            long hours = duration.toHours();
            if (hours < 24) return hours + "h ago";

            long days = duration.toDays();
            return days + "d ago";
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
