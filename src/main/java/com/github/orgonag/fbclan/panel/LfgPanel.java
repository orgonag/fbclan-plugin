package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.lfg.LfgActivity;
import com.github.orgonag.fbclan.lfg.LfgEntry;
import com.github.orgonag.fbclan.lfg.LfgService;
import com.github.orgonag.fbclan.lfg.PartyClustering;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

public class LfgPanel extends JPanel
{
    // Enforced at input time so the user sees the cap rather than having
    // their note silently truncated.
    private static final int MAX_NOTE_LENGTH = LfgService.MAX_NOTE_LENGTH;

    private final LfgService lfgService;
    private final ScheduledExecutorService executor;
    private final FinalBossConfig config;
    private final JPanel listPanel;
    private final JComboBox<LfgActivity> activityDropdown;
    private final JTextField noteField;
    private final JButton setStatusButton;
    private final JButton removeStatusButton;
    private final JButton toggleViewButton;
    private final JLabel errorLabel;
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

    public LfgPanel(LfgService lfgService, ScheduledExecutorService executor, FinalBossConfig config)
    {
        this.lfgService = lfgService;
        this.executor = executor;
        this.config = config;

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

        // Optional note - full width, capped at MAX_NOTE_LENGTH as you type
        noteField = new JTextField();
        noteField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        noteField.setAlignmentX(LEFT_ALIGNMENT);
        noteField.setToolTipText("Optional note shown with your status, e.g. \"HMT NFRZ\" (max "
            + MAX_NOTE_LENGTH + " chars)");
        ((AbstractDocument) noteField.getDocument()).setDocumentFilter(new DocumentFilter()
        {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException
            {
                if (string != null && fb.getDocument().getLength() + string.length() <= MAX_NOTE_LENGTH)
                {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException
            {
                int newLength = fb.getDocument().getLength() - length + (text == null ? 0 : text.length());
                if (newLength <= MAX_NOTE_LENGTH)
                {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
        controlsPanel.add(noteField);
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

        // Surfaced when a Supabase write fails so the user isn't left
        // thinking their status went through (visible only on error).
        errorLabel = new JLabel();
        errorLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        errorLabel.setFont(FontManager.getRunescapeSmallFont());
        errorLabel.setAlignmentX(LEFT_ALIGNMENT);
        errorLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        errorLabel.setVisible(false);
        controlsPanel.add(errorLabel);

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
    // have changed (PartyChanged, UserJoin, UserPart, or the startup
    // seed). Updates the cached state used at Set Status time, and — if
    // the user currently has an active LFG status — silently PATCHes only
    // the party columns of their row so the DB reflects their new party
    // affiliation without requiring them to re-click Set Status. The
    // PATCH deliberately omits updated_at so the row's "X min ago" timer
    // keeps counting from the original Set Status click.
    public void onLocalPartyStateChanged(String partyId, Integer partySize)
    {
        this.localPartyId = partyId;
        this.localPartySize = partySize;

        LfgActivity activity = activeLfgActivity;
        String rsn = currentRsn;
        if (activity != null && rsn != null)
        {
            executor.submit(() -> lfgService.updateParty(rsn, partyId, partySize));
        }

        SwingUtilities.invokeLater(this::rebuildList);
    }

    private static String normalize(String name)
    {
        return name == null ? "" : name.replace('\u00A0', ' ').trim().toLowerCase();
    }

    private static String escapeHtml(String text)
    {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void refresh()
    {
        PanelUi.asyncRefresh(executor, lfgService::getActiveEntries, entries -> {
            cachedEntries = entries;
            rebuildList();
        });
    }

    private void rebuildList()
    {
        listPanel.removeAll();

        if (cachedEntries.isEmpty())
        {
            listPanel.add(PanelUi.emptyStateLabel("No one is looking for a group right now."));
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
        PartyClustering.Partitioned parts = PartyClustering.partitionByParty(entries);
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

            PartyClustering.Partitioned parts = PartyClustering.partitionByParty(group);
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

    private JPanel createEntryRow(LfgEntry entry)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        String timeAgo = formatTimeAgo(entry.getUpdatedAt());
        boolean online = onlineNames.contains(normalize(entry.getRsn()));
        String nameColor = online ? "#3FBF3F" : "#BF3F3F";
        String html = "<html><font color='" + nameColor + "'>" + escapeHtml(entry.getRsn()) + "</font>"
            + " \u2014 " + entry.getActivity().getDisplayName() + " \u2014 " + timeAgo + "</html>";
        JLabel label = new JLabel(html);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());

        row.add(label, BorderLayout.CENTER);

        if (entry.getNote() != null)
        {
            // Plain-text label with HTML rendering disabled: the note is
            // user-supplied, so it must never be interpreted as markup.
            // ASCII quotes only \u2014 the RuneScape font has no glyphs for
            // curly quotes and renders them as boxes.
            JLabel noteLabel = new JLabel("\"" + entry.getNote() + "\"");
            noteLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            noteLabel.setFont(FontManager.getRunescapeSmallFont());
            noteLabel.putClientProperty("html.disable", Boolean.TRUE);
            row.add(noteLabel, BorderLayout.SOUTH);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        }
        else
        {
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        }

        return row;
    }

    private void onSetStatus()
    {
        LfgActivity selected = (LfgActivity) activityDropdown.getSelectedItem();
        if (selected == null)
        {
            return;
        }
        submitStatus(selected, noteField.getText());
    }

    // Entry point for the !lfg chat command. Arrives on the client thread,
    // so hop to the EDT: the dropdown and note field are mirrored to match
    // the command before submitting, keeping the panel and the DB in
    // agreement. The parser has already capped the note at
    // MAX_NOTE_LENGTH, so the note field's DocumentFilter accepts it.
    public void setStatusFromCommand(LfgActivity activity, String note)
    {
        SwingUtilities.invokeLater(() -> {
            activityDropdown.setSelectedItem(activity);
            noteField.setText(note == null ? "" : note);
            submitStatus(activity, note);
        });
    }

    // Shared submit path for the Set Status button and the chat command.
    // Captures party state at submit time; if the user later joins or
    // leaves a party, onLocalPartyStateChanged re-upserts with the new
    // values automatically.
    private void submitStatus(LfgActivity selected, String note)
    {
        if (currentRsn == null)
        {
            return;
        }
        activeLfgActivity = selected;
        String rsn = currentRsn;
        String partyId = localPartyId;
        Integer partySize = localPartySize;
        int ttlMinutes = config.lfgTimeoutMinutes();

        executor.submit(() -> {
            boolean ok = lfgService.setStatus(rsn, selected, partyId, partySize, note, ttlMinutes);
            showError(ok ? null : "Couldn't set status — try again.");
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
            boolean ok = lfgService.removeStatus(rsn);
            showError(ok ? null : "Couldn't remove status — try again.");
            refresh();
        });
    }

    // Entry point for "!lfg off". Touches no Swing state (showError already
    // hops to the EDT), so it is safe to call from the client thread.
    public void removeStatusFromCommand()
    {
        onRemoveStatus();
    }

    private void showError(String message)
    {
        SwingUtilities.invokeLater(() -> {
            errorLabel.setText(message == null ? "" : message);
            errorLabel.setVisible(message != null);
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
