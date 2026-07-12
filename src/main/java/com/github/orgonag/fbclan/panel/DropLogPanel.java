package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.drops.DropScreenshotService;
import com.github.orgonag.fbclan.drops.DropTrackingService;
import com.github.orgonag.fbclan.drops.DropLogService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

public class DropLogPanel extends JPanel
{
    private final DropLogService dropService;
    private final ScheduledExecutorService executor;
    private final JPanel listPanel;

    public DropLogPanel(DropLogService dropService, ScheduledExecutorService executor)
    {
        this.dropService = dropService;
        this.executor = executor;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        listPanel = new ScrollableListPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void refresh()
    {
        PanelUi.asyncRefresh(executor, () -> dropService.getRecentDrops(50), drops -> {
            listPanel.removeAll();
            if (drops.size() == 0)
            {
                listPanel.add(PanelUi.emptyStateLabel("No drops logged yet."));
            }
            else
            {
                for (JsonElement element : drops)
                {
                    listPanel.add(createDropRow(element.getAsJsonObject()));
                }
            }
            listPanel.revalidate();
            listPanel.repaint();
        });
    }

    private JPanel createDropRow(JsonObject drop)
    {
        JPanel row = new JPanel();
        row.setLayout(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        String itemName = drop.get("item_name").getAsString();
        long geValue = drop.get("ge_value").getAsLong();
        String rsn = drop.get("rsn").getAsString();
        String npcName = drop.get("npc_name").getAsString();

        // Untradeables (pets) have no GE value — suppress the "(0 GP)".
        String valueSuffix = geValue > 0 ? " (" + DropTrackingService.formatGp(geValue) + " GP)" : "";
        // Plain label, not HTML: an HTML label wraps when the panel is
        // narrow (growing the row) where a plain label truncates with "...",
        // and plain text needs no escaping.
        JLabel mainLabel = new JLabel(itemName + valueSuffix);
        mainLabel.setForeground(Color.WHITE);
        mainLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));

        String timeAgo = formatTimeAgo(drop.get("created_at").getAsString());
        JLabel detailLabel = new JLabel(rsn + " \u2014 " + npcName + " \u2014 " + timeAgo);
        detailLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        detailLabel.setFont(FontManager.getRunescapeSmallFont());

        // Labels stack in CENTER so EAST keeps its width and the text
        // truncates instead of pushing the [pic] tag off the panel edge.
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        stack.add(mainLabel);
        stack.add(detailLabel);
        row.add(stack, BorderLayout.CENTER);

        // Drops logged with a screenshot get a [pic] tag and open the image
        // in the browser on click. The drops table is anon-writable, so the
        // URL is only honored if it points into the plugin's own public
        // screenshot bucket — a spoofed row can't send viewers elsewhere.
        JsonElement screenshotElement = drop.get("screenshot_url");
        if (screenshotElement != null && !screenshotElement.isJsonNull()
            && screenshotElement.getAsString().startsWith(DropScreenshotService.publicBucketPrefix()))
        {
            final String screenshotUrl = screenshotElement.getAsString();
            JLabel cameraLabel = new JLabel("[pic]");
            cameraLabel.setForeground(ColorScheme.BRAND_ORANGE);
            cameraLabel.setFont(FontManager.getRunescapeSmallFont());
            cameraLabel.setToolTipText("Click to view screenshot");
            row.add(cameraLabel, BorderLayout.EAST);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.setToolTipText("Click to view screenshot");
            row.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    LinkBrowser.browse(screenshotUrl);
                }
            });
        }

        return row;
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
