package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.lfg.LfgActivity;
import com.github.orgonag.fbclan.lfg.LfgEntry;
import com.github.orgonag.fbclan.lfg.LfgService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class LfgPanel extends JPanel
{
    private final LfgService lfgService;
    private final ScheduledExecutorService executor;
    private final JPanel listPanel;
    private final JComboBox<LfgActivity> activityDropdown;
    private final JButton setStatusButton;
    private final JButton removeStatusButton;
    private final JButton toggleViewButton;
    private boolean groupedView = false;
    private volatile String currentRsn;
    private List<LfgEntry> cachedEntries = new ArrayList<>();

    public LfgPanel(LfgService lfgService, ScheduledExecutorService executor)
    {
        this.lfgService = lfgService;
        this.executor = executor;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Activity dropdown - full width
        activityDropdown = new JComboBox<>(LfgActivity.values());
        activityDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        activityDropdown.setAlignmentX(LEFT_ALIGNMENT);
        controlsPanel.add(activityDropdown);
        controlsPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Buttons row
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        buttonRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonRow.setAlignmentX(LEFT_ALIGNMENT);

        setStatusButton = new JButton("Set Status");
        setStatusButton.addActionListener(e -> onSetStatus());

        removeStatusButton = new JButton("Remove");
        removeStatusButton.addActionListener(e -> onRemoveStatus());

        buttonRow.add(setStatusButton);
        buttonRow.add(removeStatusButton);

        controlsPanel.add(buttonRow);
        controlsPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // View toggle on its own row
        JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        toggleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        toggleRow.setAlignmentX(LEFT_ALIGNMENT);

        toggleViewButton = new JButton("Grouped View");
        toggleViewButton.addActionListener(e -> {
            groupedView = !groupedView;
            toggleViewButton.setText(groupedView ? "List View" : "Grouped View");
            rebuildList();
        });
        toggleRow.add(toggleViewButton);

        controlsPanel.add(toggleRow);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setBorder(null);

        add(controlsPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setCurrentRsn(String rsn)
    {
        this.currentRsn = rsn;
    }

    public void refresh()
    {
        executor.submit(() -> {
            List<LfgEntry> entries = lfgService.getActiveEntries();
            SwingUtilities.invokeLater(() -> {
                cachedEntries = entries;
                rebuildList();
            });
        });
    }

    private void rebuildList()
    {
        listPanel.removeAll();

        if (cachedEntries.isEmpty())
        {
            JLabel emptyLabel = new JLabel("No one is looking for a group right now.");
            emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            listPanel.add(emptyLabel);
        }
        else if (groupedView)
        {
            renderGroupedView(cachedEntries);
        }
        else
        {
            renderListView(cachedEntries);
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private void renderListView(List<LfgEntry> entries)
    {
        for (LfgEntry entry : entries)
        {
            listPanel.add(createEntryRow(entry));
        }
    }

    private void renderGroupedView(List<LfgEntry> entries)
    {
        Map<LfgActivity, List<LfgEntry>> grouped = entries.stream()
            .collect(Collectors.groupingBy(LfgEntry::getActivity));

        for (LfgActivity activity : LfgActivity.values())
        {
            List<LfgEntry> group = grouped.get(activity);
            if (group == null || group.isEmpty())
            {
                continue;
            }

            JLabel header = new JLabel(" " + activity.getDisplayName() + " (" + group.size() + ")");
            header.setFont(FontManager.getRunescapeBoldFont());
            header.setForeground(ColorScheme.BRAND_ORANGE);
            header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
            header.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
            listPanel.add(header);

            for (LfgEntry entry : group)
            {
                listPanel.add(createEntryRow(entry));
            }
        }
    }

    private JPanel createEntryRow(LfgEntry entry)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        String timeAgo = formatTimeAgo(entry.getUpdatedAt());
        JLabel label = new JLabel(entry.getRsn() + " \u2014 " + entry.getActivity().getDisplayName()
            + " \u2014 " + timeAgo);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());

        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private void onSetStatus()
    {
        if (currentRsn == null)
        {
            return;
        }
        LfgActivity selected = (LfgActivity) activityDropdown.getSelectedItem();
        if (selected == null)
        {
            return;
        }
        executor.submit(() -> {
            lfgService.setStatus(currentRsn, selected);
            refresh();
        });
    }

    private void onRemoveStatus()
    {
        if (currentRsn == null)
        {
            return;
        }
        executor.submit(() -> {
            lfgService.removeStatus(currentRsn);
            refresh();
        });
    }

    private static String formatTimeAgo(Instant then)
    {
        Duration duration = Duration.between(then, Instant.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = duration.toHours();
        if (hours < 24) return hours + "h ago";
        return duration.toDays() + "d ago";
    }
}
