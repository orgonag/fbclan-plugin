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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private volatile Set<String> onlineNames = Collections.emptySet();

    // Local Party plugin state, pushed in from FinalBossPlugin. Both null
    // when the user is not in a party. Read on the EDT (rendering) and on
    // the executor (re-upsert) — hence volatile.
    private volatile String localPartyId = null;
    private volatile Integer localPartySize = null;

    // Tracks whether the user currently has an LFG status posted. Set to
    // the chosen activity on Set Status, cleared on Remove. Used by
    // onLocalPartyStateChanged to decide whether to silently re-upsert.
    private volatile LfgActivity activeLfgActivity = null;

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

    public void setOnlineNames(Set<String> rawNames)
    {
        Set<String> normalized = new HashSet<>();
        for (String n : rawNames)
        {
            normalized.add(normalize(n));
        }
        this.onlineNames = normalized;
        SwingUtilities.invokeLater(this::rebuildList);
    }

    // Called by FinalBossPlugin whenever the local Party plugin state may
    // have changed (PartyChanged, UserJoin, UserPart, or the periodic
    // poll). Updates the cached state used at Set Status time, and — if
    // the user currently has an active LFG status — silently re-upserts
    // their row so the DB reflects their new party affiliation without
    // requiring them to re-click Set Status.
    public void onLocalPartyStateChanged(String partyId, Integer partySize)
    {
        this.localPartyId = partyId;
        this.localPartySize = partySize;

        LfgActivity activity = activeLfgActivity;
        String rsn = currentRsn;
        if (activity != null && rsn != null)
        {
            executor.submit(() -> lfgService.setStatus(rsn, activity, partyId, partySize));
        }

        SwingUtilities.invokeLater(this::rebuildList);
    }

    private static String normalize(String name)
    {
        return name == null ? "" : name.replace(' ', ' ').trim().toLowerCase();
    }

    private static String escapeHtml(String text)
    {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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

    // List view sort order:
    //   1. Your Party (if visible in the DB)         — top
    //   2. Other parties, most-recent-update first   — middle
    //   3. Solo entries, most-recent-update first    — bottom
    private void renderListView(List<LfgEntry> entries)
    {
        Partitioned parts = partitionByParty(entries);
        String myPartyId = localPartyId;

        if (myPartyId != null && parts.parties.containsKey(myPartyId))
        {
            renderPartyCluster(parts.parties.remove(myPartyId), true);
        }

        // Other parties: order by the freshest member in each
        parts.parties.entrySet().stream()
            .sorted((a, b) -> b.getValue().get(0).getUpdatedAt()
                .compareTo(a.getValue().get(0).getUpdatedAt()))
            .forEach(e -> renderPartyCluster(e.getValue(), false));

        if (!parts.solos.isEmpty())
        {
            listPanel.add(buildSectionHeader("Solo"));
            for (LfgEntry e : parts.solos)
            {
                listPanel.add(createEntryRow(e));
            }
        }
    }

    // Grouped view: activity is the top-level grouping. Within each
    // activity we sub-cluster by party, with the local user's own party
    // (if represented in this activity) shown first. Party counts in this
    // view are scoped to the activity — a cross-activity party will appear
    // in multiple sections with the count reflecting only the members in
    // each section. This keeps the math local and the visuals clean.
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

            listPanel.add(buildSectionHeader(activity.getDisplayName() + " (" + group.size() + ")"));

            Partitioned parts = partitionByParty(group);
            String myPartyId = localPartyId;

            if (myPartyId != null && parts.parties.containsKey(myPartyId))
            {
                renderPartySubCluster(parts.parties.remove(myPartyId), true);
            }
            for (List<LfgEntry> partyMembers : parts.parties.values())
            {
                renderPartySubCluster(partyMembers, false);
            }
            for (LfgEntry e : parts.solos)
            {
                listPanel.add(createEntryRow(e));
            }
        }
    }

    // Renders a top-level party cluster used by the List view. The
    // declared size comes from MAX(party_size) across the visible rows,
    // which is the freshest known value. If the declared size is larger
    // than the number of rows actually in the panel, we surface the gap
    // as "(N not on LFG)" so viewers see the full party context.
    private void renderPartyCluster(List<LfgEntry> partyMembers, boolean isMyParty)
    {
        int declaredSize = partyMembers.stream()
            .mapToInt(e -> e.getPartySize() == null ? 0 : e.getPartySize())
            .max().orElse(partyMembers.size());
        declaredSize = Math.max(declaredSize, partyMembers.size());

        String headerText = (isMyParty ? "Your Party" : "Party") + " · " + declaredSize;
        listPanel.add(buildSectionHeader(headerText));

        for (LfgEntry e : partyMembers)
        {
            listPanel.add(createEntryRow(e));
        }

        int notOnLfg = declaredSize - partyMembers.size();
        if (notOnLfg > 0)
        {
            listPanel.add(buildFooterLabel("(" + notOnLfg + " not on LFG)"));
        }
    }

    private void renderPartySubCluster(List<LfgEntry> partyMembers, boolean isMyParty)
    {
        String headerText = "  " + (isMyParty ? "Your Party" : "Party")
            + " · " + partyMembers.size();
        JLabel header = new JLabel(headerText);
        header.setFont(FontManager.getRunescapeSmallFont());
        header.setForeground(isMyParty ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        header.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        listPanel.add(header);

        for (LfgEntry e : partyMembers)
        {
            listPanel.add(createEntryRow(e));
        }
    }

    private JLabel buildSectionHeader(String text)
    {
        JLabel header = new JLabel(" " + text);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setForeground(ColorScheme.BRAND_ORANGE);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        header.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        return header;
    }

    private JLabel buildFooterLabel(String text)
    {
        JLabel footer = new JLabel(" " + text);
        footer.setFont(FontManager.getRunescapeSmallFont());
        footer.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        footer.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 0));
        return footer;
    }

    // Buckets entries by party_id, preserving the input ordering inside
    // each bucket (the caller hands us a list already sorted by
    // updated_at desc, so the first element of each list is the freshest).
    private Partitioned partitionByParty(List<LfgEntry> entries)
    {
        Map<String, List<LfgEntry>> parties = new LinkedHashMap<>();
        List<LfgEntry> solos = new ArrayList<>();
        for (LfgEntry e : entries)
        {
            if (e.getPartyId() == null)
            {
                solos.add(e);
            }
            else
            {
                parties.computeIfAbsent(e.getPartyId(), k -> new ArrayList<>()).add(e);
            }
        }
        return new Partitioned(parties, solos);
    }

    private static final class Partitioned
    {
        final Map<String, List<LfgEntry>> parties;
        final List<LfgEntry> solos;

        Partitioned(Map<String, List<LfgEntry>> parties, List<LfgEntry> solos)
        {
            this.parties = parties;
            this.solos = solos;
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
        boolean online = onlineNames.contains(normalize(entry.getRsn()));
        String nameColor = online ? "#3FBF3F" : "#BF3F3F";
        String html = "<html><font color='" + nameColor + "'>" + escapeHtml(entry.getRsn()) + "</font>"
            + " \u2014 " + entry.getActivity().getDisplayName() + " \u2014 " + timeAgo + "</html>";
        JLabel label = new JLabel(html);
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

        // Capture party state at click time. If the user later joins or
        // leaves a party, onLocalPartyStateChanged will re-upsert with the
        // new values automatically.
        activeLfgActivity = selected;
        String rsn = currentRsn;
        String partyId = localPartyId;
        Integer partySize = localPartySize;

        executor.submit(() -> {
            lfgService.setStatus(rsn, selected, partyId, partySize);
            refresh();
        });
    }

    private void onRemoveStatus()
    {
        if (currentRsn == null)
        {
            return;
        }
        activeLfgActivity = null;
        String rsn = currentRsn;
        executor.submit(() -> {
            lfgService.removeStatus(rsn);
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
