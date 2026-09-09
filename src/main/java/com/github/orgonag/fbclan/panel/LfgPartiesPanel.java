package com.github.orgonag.fbclan.panel;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.lfg.LfgActivity;
import com.github.orgonag.fbclan.lfg.LfgApplicant;
import com.github.orgonag.fbclan.lfg.LfgFormedParty;
import com.github.orgonag.fbclan.lfg.LfgKillcountService;
import com.github.orgonag.fbclan.lfg.LfgLocalKillcounts;
import com.github.orgonag.fbclan.lfg.LfgLootRule;
import com.github.orgonag.fbclan.lfg.LfgNames;
import com.github.orgonag.fbclan.lfg.LfgParty;
import com.github.orgonag.fbclan.lfg.LfgPartyNotifier;
import com.github.orgonag.fbclan.lfg.LfgPartyService;
import com.github.orgonag.fbclan.lfg.LfgRole;
import com.github.orgonag.fbclan.lfg.LfgRoles;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Hosted parties board: browse open parties and apply for a role, or host
 * your own and manage applicants. State is a cached snapshot from the
 * 30s poll (plus an immediate refresh after every local action); all
 * network I/O runs on the executor, all Swing work on the EDT.
 */
public class LfgPartiesPanel extends JPanel
{
    private static final Color ONLINE = new Color(0x3F, 0xBF, 0x3F);
    private static final Color OFFLINE = new Color(0xBF, 0x3F, 0x3F);
    private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
    // Card inner width: sidebar (242) minus the card's 8px side padding.
    private static final int WRAP_WIDTH = 226;

    private final LfgPartyService partyService;
    private final LfgKillcountService killcounts;
    private final LfgLocalKillcounts localKillcounts;
    private final LfgPartyNotifier notifier;
    private final ScheduledExecutorService executor;
    private final FinalBossConfig config;
    private final LfgIconSource icons;

    // Session state
    private volatile String currentRsn;
    private volatile int currentWorld = 0;
    private volatile Set<String> onlineNames = Collections.emptySet();
    private volatile long lastHeartbeatMs = 0;

    // EDT-only
    private List<LfgParty> cached = new ArrayList<>();
    private List<LfgFormedParty> cachedFormed = new ArrayList<>();
    private boolean formVisible = false;
    private boolean editing = false;
    // rebuildDynamicForm() adjusts widgets whose listeners call it back;
    // this stops the nested call from re-adding rows mid-rebuild.
    private boolean rebuildingForm = false;
    private String applyingPartyId = null;
    private LfgActivity filterActivity = null;
    private boolean hideFull = false;
    private boolean showFormed = false;
    // Text the host has typed into the Add-member box, kept across the
    // rebuild every poll triggers.
    private String addMemberDraft = "";

    // Widgets
    private final JPanel listPanel;
    private final JPanel formPanel;
    private final JPanel dynamicForm;
    private final JButton hostButton;
    private final JLabel errorLabel;
    private final JComboBox<LfgActivity> activityBox;
    private final JCheckBox hardModeBox;
    private final JSpinner invocationSpinner;
    private final JSpinner sizeSpinner;
    private final JComboBox<LfgLootRule> lootBox;
    private final JSpinner minKcSpinner;
    private final JCheckBox learnerBox;
    private final JCheckBox teacherBox;
    private final JComboBox<LfgRole> hostRoleBox;
    private final Map<LfgRole, JSpinner> coxCountSpinners = new EnumMap<>(LfgRole.class);
    private final JTextField descField;
    private final JLabel worldLabel;
    private final JButton submitButton;
    private final JComboBox<Object> filterBox;
    private final JCheckBox hideFullBox;
    private final JCheckBox showFormedBox;

    public LfgPartiesPanel(LfgPartyService partyService, LfgKillcountService killcounts,
                           LfgLocalKillcounts localKillcounts, LfgPartyNotifier notifier,
                           ScheduledExecutorService executor, FinalBossConfig config, LfgIconSource icons)
    {
        this.partyService = partyService;
        this.killcounts = killcounts;
        this.localKillcounts = localKillcounts;
        this.notifier = notifier;
        this.executor = executor;
        this.config = config;
        this.icons = icons;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        controls.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        hostButton = new JButton("Host a party");
        hostButton.setAlignmentX(LEFT_ALIGNMENT);
        pinHeight(hostButton);
        hostButton.addActionListener(e -> toggleForm());
        controls.add(hostButton);
        controls.add(Box.createRigidArea(new Dimension(0, 5)));

        // ---- Host form ----
        formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        formPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        formPanel.setAlignmentX(LEFT_ALIGNMENT);
        formPanel.setVisible(false);

        activityBox = new JComboBox<>(LfgActivity.values());
        activityBox.setRenderer(new PanelUi.ActivityRenderer());
        activityBox.addActionListener(e -> rebuildDynamicForm());
        activityBox.setAlignmentX(LEFT_ALIGNMENT);
        pinHeight(activityBox);
        formPanel.add(activityBox);
        formPanel.add(Box.createRigidArea(new Dimension(0, 3)));

        dynamicForm = new JPanel();
        dynamicForm.setLayout(new BoxLayout(dynamicForm, BoxLayout.Y_AXIS));
        dynamicForm.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dynamicForm.setAlignmentX(LEFT_ALIGNMENT);
        formPanel.add(dynamicForm);

        hardModeBox = checkbox("Hard mode");
        invocationSpinner = spinner(150, 0, LfgPartyService.MAX_INVOCATION, 5);
        sizeSpinner = spinner(5, LfgPartyService.MIN_CAPACITY, LfgPartyService.MAX_CAPACITY, 1);
        sizeSpinner.addChangeListener(e -> rebuildDynamicForm());
        lootBox = new JComboBox<>(LfgLootRule.values());
        minKcSpinner = spinner(0, 0, 100_000, 10);
        learnerBox = checkbox("Learner");
        teacherBox = checkbox("Teacher");
        learnerBox.addActionListener(e -> {
            if (learnerBox.isSelected()) teacherBox.setSelected(false);
        });
        teacherBox.addActionListener(e -> {
            if (teacherBox.isSelected()) learnerBox.setSelected(false);
        });
        hostRoleBox = new JComboBox<>();
        hardModeBox.addActionListener(e -> rebuildDynamicForm());

        descField = new JTextField();
        descField.setToolTipText("Optional description (max " + LfgPartyService.MAX_DESCRIPTION_LENGTH + " chars)");
        capLength(descField, LfgPartyService.MAX_DESCRIPTION_LENGTH);
        formPanel.add(labeled("Description", descField));

        worldLabel = new JLabel("World: unknown");
        worldLabel.setFont(FontManager.getRunescapeSmallFont());
        worldLabel.setForeground(MUTED);
        worldLabel.setAlignmentX(LEFT_ALIGNMENT);
        worldLabel.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        formPanel.add(worldLabel);

        JPanel formButtons = new JPanel(new GridLayout(1, 2, 5, 0));
        formButtons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        formButtons.setAlignmentX(LEFT_ALIGNMENT);
        submitButton = new JButton("Create");
        submitButton.addActionListener(e -> onSubmitForm());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            formVisible = false;
            editing = false;
            updateFormVisibility();
        });
        formButtons.add(submitButton);
        formButtons.add(cancelButton);
        pinHeight(formButtons);
        formPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        formPanel.add(formButtons);
        controls.add(formPanel);
        controls.add(Box.createRigidArea(new Dimension(0, 5)));

        // ---- Filters ----
        List<Object> filterItems = new ArrayList<>();
        filterItems.add("All activities");
        Collections.addAll(filterItems, LfgActivity.values());
        filterBox = new JComboBox<>(filterItems.toArray());
        filterBox.setRenderer(new PanelUi.ActivityRenderer());
        filterBox.setAlignmentX(LEFT_ALIGNMENT);
        filterBox.addActionListener(e -> {
            Object sel = filterBox.getSelectedItem();
            filterActivity = sel instanceof LfgActivity ? (LfgActivity) sel : null;
            rebuildList();
        });
        controls.add(pinHeight(filterBox));

        JPanel toggles = new JPanel(new GridLayout(1, 2));
        toggles.setBackground(ColorScheme.DARK_GRAY_COLOR);
        toggles.setAlignmentX(LEFT_ALIGNMENT);
        hideFullBox = checkbox("Hide full");
        hideFullBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        hideFullBox.addActionListener(e -> {
            hideFull = hideFullBox.isSelected();
            rebuildList();
        });
        showFormedBox = checkbox("Show formed");
        showFormedBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showFormedBox.setToolTipText("Parties that filled up in the last 7 days");
        showFormedBox.addActionListener(e -> {
            showFormed = showFormedBox.isSelected();
            rebuildList();
        });
        toggles.add(hideFullBox);
        toggles.add(showFormedBox);
        controls.add(pinHeight(toggles));

        errorLabel = new JLabel();
        errorLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        errorLabel.setFont(FontManager.getRunescapeSmallFont());
        errorLabel.setAlignmentX(LEFT_ALIGNMENT);
        errorLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        errorLabel.setVisible(false);
        controls.add(errorLabel);

        listPanel = new ScrollableListPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setBorder(null);

        add(controls, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        rebuildDynamicForm();
        rebuildList();
    }

    // ------------------------------------------------------------ session

    public void setCurrentRsn(String rsn)
    {
        this.currentRsn = rsn;
    }

    // Pushed from the client thread on every poll tick.
    public void setCurrentWorld(int world)
    {
        this.currentWorld = world;
        Runnable update = () ->
            worldLabel.setText(world > 0 ? "World: " + world + " (your current world)" : "World: unknown");
        if (SwingUtilities.isEventDispatchThread())
        {
            update.run();
        }
        else
        {
            SwingUtilities.invokeLater(update);
        }
    }

    public void setOnlineNames(Set<String> rawNames)
    {
        Set<String> normalized = new java.util.HashSet<>();
        for (String n : rawNames)
        {
            normalized.add(LfgNames.normalize(n));
        }
        this.onlineNames = normalized;
        SwingUtilities.invokeLater(this::rebuildList);
    }

    public void reset()
    {
        SwingUtilities.invokeLater(() -> {
            cached = new ArrayList<>();
            cachedFormed = new ArrayList<>();
            applyingPartyId = null;
            addMemberDraft = "";
            rebuildList();
        });
    }

    // Fetch on the executor; form the hosted party if it filled; diff for
    // notifications; heartbeat if hosting; then render on the EDT.
    public void refresh()
    {
        executor.submit(() -> {
            List<LfgParty> parties = partyService.getParties();
            if (maybeForm(parties))
            {
                parties = partyService.getParties();
            }
            List<LfgFormedParty> formed = partyService.getFormed();
            notifier.onPartiesFetched(parties, formed);
            maybeHeartbeat(parties);
            final List<LfgParty> partiesF = parties;
            SwingUtilities.invokeLater(() -> {
                cached = partiesF;
                cachedFormed = formed;
                rebuildList();
            });
        });
    }

    // The host's client is the one that forms the party: once every seat
    // is taken, snapshot it to the formed list and take it off the board.
    // Returns true when a snapshot was written (the caller re-fetches).
    private boolean maybeForm(List<LfgParty> parties)
    {
        String rsn = currentRsn;
        if (rsn == null)
        {
            return false;
        }
        for (LfgParty p : parties)
        {
            if (p.isHostedBy(rsn) && p.isFull())
            {
                return partyService.form(p);
            }
        }
        return false;
    }

    private void maybeHeartbeat(List<LfgParty> parties)
    {
        String rsn = currentRsn;
        if (rsn == null)
        {
            return;
        }
        for (LfgParty p : parties)
        {
            if (p.isHostedBy(rsn))
            {
                long now = System.currentTimeMillis();
                if (now - lastHeartbeatMs >= LfgPartyService.HEARTBEAT_MINUTES * 60_000L)
                {
                    lastHeartbeatMs = now;
                    partyService.heartbeat(rsn);
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------ host form

    private void toggleForm()
    {
        LfgParty mine = myParty();
        if (mine != null)
        {
            // Hosting already: the button edits.
            populateForm(mine);
            editing = true;
            formVisible = true;
        }
        else
        {
            formVisible = !formVisible;
            editing = false;
        }
        updateFormVisibility();
    }

    private void updateFormVisibility()
    {
        formPanel.setVisible(formVisible);
        submitButton.setText(editing ? "Save" : "Create");
        hostButton.setText(myParty() != null ? "Edit your party" : (formVisible ? "Hide form" : "Host a party"));
        revalidate();
        repaint();
    }

    private void populateForm(LfgParty p)
    {
        activityBox.setSelectedItem(p.getActivity());
        hardModeBox.setSelected(p.isHardMode());
        invocationSpinner.setValue(p.getInvocation());
        rebuildDynamicForm();
        SpinnerNumberModel sizeModel = (SpinnerNumberModel) sizeSpinner.getModel();
        sizeSpinner.setValue(clamp(p.getCapacity(),
            (Integer) sizeModel.getMinimum(), (Integer) sizeModel.getMaximum()));
        lootBox.setSelectedItem(p.getLootRule());
        minKcSpinner.setValue(p.getMinKc());
        learnerBox.setSelected(p.isLearner());
        teacherBox.setSelected(p.isTeacher());
        descField.setText(p.getDescription() == null ? "" : p.getDescription());
        rebuildDynamicForm();
        if (p.getHostRole() != null)
        {
            hostRoleBox.setSelectedItem(p.getHostRole());
        }
        if (LfgRoles.hostChoosesCounts(p.getActivity()))
        {
            for (JSpinner s : coxCountSpinners.values())
            {
                s.setValue(0);
            }
            for (LfgRole r : p.getRequiredRoles())
            {
                JSpinner s = coxCountSpinners.get(r);
                if (s != null)
                {
                    s.setValue((Integer) s.getValue() + 1);
                }
            }
        }
    }

    // Everything below the activity picker depends on the activity (and
    // size / hard mode), so it's rebuilt on change. Values of widgets that
    // survive the rebuild (spinners, checkboxes) are preserved.
    private void rebuildDynamicForm()
    {
        if (rebuildingForm)
        {
            return;
        }
        rebuildingForm = true;
        try
        {
            rebuildDynamicFormInner();
        }
        finally
        {
            rebuildingForm = false;
        }
    }

    private void rebuildDynamicFormInner()
    {
        LfgActivity activity = (LfgActivity) activityBox.getSelectedItem();
        if (activity == null)
        {
            return;
        }
        dynamicForm.removeAll();

        if (activity.hasHardMode())
        {
            hardModeBox.setText(activity == LfgActivity.TOB ? "Hard mode (HMT)"
                : activity == LfgActivity.COX ? "Challenge mode (CM)" : activity.getHardModeLabel());
            dynamicForm.add(hardModeBox);
        }
        else
        {
            hardModeBox.setSelected(false);
        }
        if (activity.usesInvocation())
        {
            dynamicForm.add(labeled("Invocation", invocationSpinner));
        }

        int min = Math.max(LfgPartyService.MIN_CAPACITY, activity.getMinPartySize());
        int max = Math.max(min, activity.getMaxPartySize());
        SpinnerNumberModel sizeModel = (SpinnerNumberModel) sizeSpinner.getModel();
        int current = clamp((Integer) sizeSpinner.getValue(), min, max);
        sizeModel.setMinimum(min);
        sizeModel.setMaximum(max);
        if ((Integer) sizeSpinner.getValue() != current)
        {
            sizeSpinner.setValue(current);
        }
        dynamicForm.add(labeled("Party size", sizeSpinner));
        dynamicForm.add(labeled("Loot", lootBox));
        if (activity.hasKillcount())
        {
            dynamicForm.add(labeled("Min KC (0=any)", minKcSpinner));
            Integer mine = localKillcounts.read(activity, activity.hasHardMode() && hardModeBox.isSelected());
            if (mine != null)
            {
                dynamicForm.add(note("Your KC: " + mine));
            }
        }
        else
        {
            minKcSpinner.setValue(0);
        }

        if (activity.isRaid())
        {
            JPanel tags = new JPanel(new GridLayout(1, 2));
            tags.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            tags.setAlignmentX(LEFT_ALIGNMENT);
            tags.add(learnerBox);
            tags.add(teacherBox);
            dynamicForm.add(pinHeight(tags));
        }
        else
        {
            learnerBox.setSelected(false);
            teacherBox.setSelected(false);
        }

        if (activity.hasRoles())
        {
            boolean hard = hardModeBox.isSelected();
            LfgRole previous = (LfgRole) hostRoleBox.getSelectedItem();
            hostRoleBox.removeAllItems();
            List<LfgRole> roles = LfgRoles.playableRoles(activity, hard);
            if (activity == LfgActivity.TOB)
            {
                // Only the roles in this size's fixed composition.
                roles = distinct(LfgRoles.tobComposition(current, hard));
            }
            for (LfgRole r : roles)
            {
                hostRoleBox.addItem(r);
            }
            if (previous != null && roles.contains(previous))
            {
                hostRoleBox.setSelectedItem(previous);
            }
            dynamicForm.add(labeled("Your role", hostRoleBox));

            if (activity == LfgActivity.TOB)
            {
                dynamicForm.add(wrapped("Team: " + LfgRoles.summarize(LfgRoles.tobComposition(current, hard)), MUTED));
            }
            else if (LfgRoles.hostChoosesCounts(activity))
            {
                dynamicForm.add(note("Roles wanted (rest = Fill):"));
                Map<LfgRole, Integer> keep = new EnumMap<>(LfgRole.class);
                for (Map.Entry<LfgRole, JSpinner> e : coxCountSpinners.entrySet())
                {
                    keep.put(e.getKey(), (Integer) e.getValue().getValue());
                }
                coxCountSpinners.clear();
                for (LfgRole r : LfgRoles.playableRoles(activity, hard))
                {
                    if (r.isFill())
                    {
                        continue;
                    }
                    JSpinner s = spinner(keep.getOrDefault(r, 0), 0, max, 1);
                    coxCountSpinners.put(r, s);
                    dynamicForm.add(labeled("  " + r.getDisplayName(), s));
                }
            }
            else if (activity == LfgActivity.BA)
            {
                dynamicForm.add(note("One of each role; a 5th may double up."));
            }
        }
        else
        {
            hostRoleBox.removeAllItems();
        }

        dynamicForm.revalidate();
        dynamicForm.repaint();
        formPanel.revalidate();
    }

    private void onSubmitForm()
    {
        String rsn = currentRsn;
        LfgActivity activity = (LfgActivity) activityBox.getSelectedItem();
        if (rsn == null || activity == null)
        {
            return;
        }
        boolean hard = activity.hasHardMode() && hardModeBox.isSelected();
        int invocation = activity.usesInvocation() ? (Integer) invocationSpinner.getValue() : 0;
        int capacity = (Integer) sizeSpinner.getValue();
        Map<LfgRole, Integer> counts = new EnumMap<>(LfgRole.class);
        for (Map.Entry<LfgRole, JSpinner> e : coxCountSpinners.entrySet())
        {
            counts.put(e.getKey(), (Integer) e.getValue().getValue());
        }
        LfgRole hostRole = activity.hasRoles() ? (LfgRole) hostRoleBox.getSelectedItem() : null;
        int world = currentWorld;

        LfgParty party = LfgParty.builder()
            .hostRsn(rsn)
            .activity(activity)
            .hardMode(hard)
            .invocation(invocation)
            .capacity(capacity)
            .description(descField.getText())
            .world(world > 0 ? world : null)
            .minKc(activity.hasKillcount() ? (Integer) minKcSpinner.getValue() : 0)
            .lootRule((LfgLootRule) lootBox.getSelectedItem())
            .requiredRoles(LfgRoles.requiredRoles(activity, hard, capacity, counts))
            .hostRole(hostRole)
            .learner(activity.isRaid() && learnerBox.isSelected())
            .teacher(activity.isRaid() && teacherBox.isSelected())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .applicants(Collections.emptyList())
            .build();

        formVisible = false;
        editing = false;
        updateFormVisibility();
        runAction(() -> {
            // Hosting and applying are exclusive: drop any application first.
            partyService.withdrawAll(rsn);
            lastHeartbeatMs = System.currentTimeMillis();
            return partyService.upsertParty(party);
        }, "Couldn't save your party — try again.");
    }

    private void onDisband(LfgParty mine)
    {
        String rsn = currentRsn;
        if (rsn == null)
        {
            return;
        }
        notifier.expectSelfLeave(mine.getId());
        runAction(() -> partyService.disband(rsn), "Couldn't disband — try again.");
    }

    // ------------------------------------------------------------ rendering

    private void rebuildList()
    {
        listPanel.removeAll();
        String rsn = currentRsn;
        LfgParty mine = myParty();

        if (mine != null)
        {
            listPanel.add(sectionHeader("Your party"));
            listPanel.add(buildHostCard(mine));
        }

        List<LfgParty> others = new ArrayList<>();
        for (LfgParty p : cached)
        {
            if (mine != null && p.getId().equals(mine.getId()))
            {
                continue;
            }
            if (filterActivity != null && p.getActivity() != filterActivity)
            {
                continue;
            }
            if (hideFull && p.isFull() && (rsn == null || p.applicantFor(rsn) == null))
            {
                continue;
            }
            others.add(p);
        }
        // Parties the player is in (or waiting on) float to the top.
        others.sort((a, b) -> {
            boolean ia = rsn != null && a.applicantFor(rsn) != null;
            boolean ib = rsn != null && b.applicantFor(rsn) != null;
            if (ia != ib)
            {
                return ia ? -1 : 1;
            }
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        if (mine != null)
        {
            listPanel.add(sectionHeader("Open parties (" + others.size() + ")"));
        }
        if (others.isEmpty())
        {
            listPanel.add(PanelUi.emptyStateLabel(cached.isEmpty()
                ? "No parties are being hosted right now."
                : "No parties match your filters."));
        }
        for (LfgParty p : others)
        {
            listPanel.add(buildPartyCard(p));
        }

        if (showFormed)
        {
            List<LfgFormedParty> formed = new ArrayList<>();
            for (LfgFormedParty f : cachedFormed)
            {
                if (filterActivity == null || f.getActivity() == filterActivity)
                {
                    formed.add(f);
                }
            }
            listPanel.add(sectionHeader("Formed parties (" + formed.size() + ")"));
            if (formed.isEmpty())
            {
                listPanel.add(PanelUi.emptyStateLabel("No parties have formed in the last 7 days."));
            }
            for (LfgFormedParty f : formed)
            {
                listPanel.add(buildFormedCard(f));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
        updateFormVisibility();
    }

    // Read-only record of a party that filled up: who went, when, where.
    private JPanel buildFormedCard(LfgFormedParty f)
    {
        String rsn = currentRsn;
        JPanel card = card();
        card.add(titleRow(f.getActivity(), html("<b>" + esc(f.getTitle()) + "</b>"
            + (f.getWorld() != null ? " <font color='#A0A0A0'>W" + f.getWorld() + "</font>" : "")
            + "  " + f.getMembers().size() + "/" + f.getCapacity())));
        card.add(small("Formed " + timeAgo(f.getFormedAt()) + " · hosted by " + f.getHostRsn(), MUTED));
        card.add(wrapped(f.getRoster(), f.includes(rsn) ? Color.WHITE : MUTED));
        if (f.isHostedBy(rsn))
        {
            JPanel actions = new JPanel(new GridLayout(1, 1));
            actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            actions.setAlignmentX(LEFT_ALIGNMENT);
            actions.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            JButton remove = new JButton("Remove");
            remove.setToolTipText("Take this off the formed list now instead of in 7 days");
            remove.addActionListener(e -> runAction(() -> partyService.deleteFormed(f.getId()),
                "Couldn't remove — try again."));
            actions.add(remove);
            card.add(pinHeight(actions));
        }
        finish(card);
        return card;
    }

    private JPanel buildPartyCard(LfgParty p)
    {
        String rsn = currentRsn;
        JPanel card = card();

        boolean hostOnline = onlineNames.contains(LfgNames.normalize(p.getHostRsn()));
        card.add(titleRow(p, html("<b>" + esc(p.getTitle()) + "</b>"
            + (p.getWorld() != null ? " <font color='#A0A0A0'>W" + p.getWorld() + "</font>" : "")
            + "  <font color='" + (hostOnline ? "#3FBF3F" : "#BF3F3F") + "'>" + esc(p.getHostRsn()) + "</font>")));

        StringBuilder meta = new StringBuilder();
        meta.append(p.getMemberCount()).append('/').append(p.getCapacity());
        if (p.getLootRule() != LfgLootRule.UNSPECIFIED)
        {
            meta.append(" · ").append(p.getLootRule().getDisplayName());
        }
        if (p.getMinKc() > 0)
        {
            meta.append(" · ").append(p.getMinKc()).append("+ KC");
        }
        if (p.isLearner())
        {
            meta.append(" · Learner");
        }
        if (p.isTeacher())
        {
            meta.append(" · Teacher");
        }
        meta.append(" · ").append(timeAgo(p.getCreatedAt()));
        card.add(small(meta.toString(), MUTED));

        if (p.getActivity().hasRoles())
        {
            String needs = p.isFull() ? "" : LfgRoles.summarize(p.getOpenRoles());
            card.add(wrapped(p.isFull() ? "Full" : (needs.isEmpty() ? "Roles: any" : "Needs: " + needs), MUTED));
        }
        else if (p.isFull())
        {
            card.add(small("Full", MUTED));
        }

        if (p.getDescription() != null)
        {
            card.add(wrapped("\"" + p.getDescription() + "\"", MUTED));
        }

        List<LfgApplicant> accepted = p.getAccepted();
        if (!accepted.isEmpty())
        {
            StringBuilder members = new StringBuilder("With: ");
            for (int i = 0; i < accepted.size(); i++)
            {
                if (i > 0)
                {
                    members.append(", ");
                }
                members.append(accepted.get(i).getRsn());
            }
            card.add(wrapped(members.toString(), MUTED));
        }

        // ---- action row ----
        LfgApplicant mine = rsn == null ? null : p.applicantFor(rsn);
        boolean hosting = myParty() != null;
        JPanel actions = new JPanel(new GridLayout(1, 1, 5, 0));
        actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        if (mine != null)
        {
            String state = mine.isAccepted() ? "Accepted" : mine.isPending() ? "Pending" : "Declined";
            JButton leave = new JButton(mine.isAccepted() ? "Leave party" : mine.isPending() ? "Withdraw" : "Dismiss");
            leave.addActionListener(e -> {
                notifier.expectSelfLeave(p.getId());
                runAction(() -> partyService.withdraw(p.getId(), rsn), "Couldn't withdraw — try again.");
            });
            actions.setLayout(new BorderLayout(5, 0));
            actions.add(small(state, mine.isAccepted() ? ONLINE : MUTED), BorderLayout.CENTER);
            actions.add(leave, BorderLayout.EAST);
            card.add(actions);
        }
        else if (p.getId().equals(applyingPartyId))
        {
            card.add(buildApplyRow(p));
        }
        else
        {
            JButton apply = new JButton("Apply");
            if (rsn == null)
            {
                apply.setEnabled(false);
            }
            else if (hosting)
            {
                apply.setText("Disband yours to apply");
                apply.setEnabled(false);
            }
            else if (p.isFull())
            {
                apply.setText("Full");
                apply.setEnabled(false);
            }
            else if (p.getActivity().hasRoles() && p.getOpenRoles().isEmpty())
            {
                apply.setText("No open roles");
                apply.setEnabled(false);
            }
            apply.addActionListener(e -> {
                applyingPartyId = p.getId();
                rebuildList();
            });
            actions.add(apply);
            card.add(actions);
        }
        finish(card);
        return card;
    }

    private JPanel buildApplyRow(LfgParty p)
    {
        String rsn = currentRsn;
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JComboBox<LfgRole> roleBox = null;
        if (p.getActivity().hasRoles())
        {
            List<LfgRole> options = LfgRoles.applyOptions(p.getActivity(), p.isHardMode(), p.getOpenRoles());
            roleBox = new JComboBox<>(options.toArray(new LfgRole[0]));
            row.add(labeled("Role", roleBox, ColorScheme.DARKER_GRAY_COLOR));
        }
        JCheckBox learner = null;
        if (p.getActivity().isRaid())
        {
            learner = checkbox("I'm a learner");
            row.add(learner);
        }

        // Your kill count for this party's activity, prefilled from your
        // own client (what the game told it), else from the hiscores, else
        // blank to type. Editing it marks the value as self-reported. The
        // host sees it; applying is never blocked by it.
        JSpinner kcSpinner = null;
        Integer prefill = null;
        LfgApplicant.KcSource prefillSource = null;
        if (p.getActivity().hasKillcount())
        {
            boolean hard = isHard(p);
            prefill = localKillcounts.read(p.getActivity(), hard);
            if (prefill != null)
            {
                prefillSource = LfgApplicant.KcSource.LOCAL;
            }
            else if (rsn != null && config.lfgKcLookups())
            {
                LfgKillcountService.Result r = killcounts.cached(rsn, p.getActivity());
                if (r == null)
                {
                    killcounts.lookup(rsn, p.getActivity(), this::rebuildList);
                }
                else if (r.isKnown(hard))
                {
                    prefill = r.killcount(hard);
                    prefillSource = LfgApplicant.KcSource.HISCORES;
                }
            }
            kcSpinner = spinner(prefill == null ? 0 : prefill, 0, 100_000, 1);
            String hint = prefillSource == LfgApplicant.KcSource.LOCAL ? "Your KC (auto)"
                : prefillSource == LfgApplicant.KcSource.HISCORES ? "Your KC (hiscores)" : "Your KC";
            row.add(labeled(hint, kcSpinner, ColorScheme.DARKER_GRAY_COLOR));
            if (p.getMinKc() > 0)
            {
                boolean below = prefill != null && prefill < p.getMinKc();
                JLabel ask = note("Host asks for " + p.getMinKc() + "+ KC" + (below ? " - you're below it" : ""));
                if (below)
                {
                    ask.setForeground(ColorScheme.BRAND_ORANGE);
                }
                row.add(ask);
            }
        }
        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        JButton confirm = new JButton("Confirm");
        JButton cancel = new JButton("Cancel");
        final JComboBox<LfgRole> roleBoxF = roleBox;
        final JCheckBox learnerF = learner;
        final JSpinner kcSpinnerF = kcSpinner;
        final Integer prefillF = prefill;
        final LfgApplicant.KcSource prefillSourceF = prefillSource;
        confirm.addActionListener(e -> {
            if (rsn == null)
            {
                return;
            }
            LfgRole role = roleBoxF == null ? null : (LfgRole) roleBoxF.getSelectedItem();
            boolean isLearner = learnerF != null && learnerF.isSelected();
            Integer kc = null;
            LfgApplicant.KcSource source = null;
            if (kcSpinnerF != null)
            {
                int typed = (Integer) kcSpinnerF.getValue();
                if (prefillF != null && typed == prefillF)
                {
                    kc = typed;
                    source = prefillSourceF;
                }
                else if (typed > 0)
                {
                    kc = typed;
                    source = LfgApplicant.KcSource.MANUAL;
                }
            }
            final Integer kcF = kc;
            final LfgApplicant.KcSource sourceF = source;
            applyingPartyId = null;
            runAction(() -> partyService.apply(p.getId(), rsn, role, isLearner, kcF, sourceF), "Couldn't apply — try again.");
        });
        cancel.addActionListener(e -> {
            applyingPartyId = null;
            rebuildList();
        });
        buttons.add(confirm);
        buttons.add(cancel);
        row.add(pinHeight(buttons));
        return row;
    }

    private JPanel buildHostCard(LfgParty p)
    {
        JPanel card = card();
        card.add(titleRow(p, html("<b>" + esc(p.getTitle()) + "</b>"
            + (p.getWorld() != null ? " <font color='#A0A0A0'>W" + p.getWorld() + "</font>" : "")
            + "  " + p.getMemberCount() + "/" + p.getCapacity())));
        if (p.getActivity().hasRoles())
        {
            String needs = LfgRoles.summarize(p.getOpenRoles());
            card.add(wrapped(p.isFull() ? "Full" : (needs.isEmpty() ? "Roles: any" : "Needs: " + needs), MUTED));
        }
        if (p.getDescription() != null)
        {
            card.add(wrapped("\"" + p.getDescription() + "\"", MUTED));
        }

        List<LfgApplicant> pending = p.getPending();
        List<LfgApplicant> accepted = p.getAccepted();
        if (!pending.isEmpty())
        {
            card.add(small("Applicants (" + pending.size() + ")", ColorScheme.BRAND_ORANGE));
            for (LfgApplicant a : pending)
            {
                card.add(buildApplicantRow(p, a, true));
            }
        }
        if (!accepted.isEmpty())
        {
            card.add(small("Members", ColorScheme.BRAND_ORANGE));
            for (LfgApplicant a : accepted)
            {
                card.add(buildApplicantRow(p, a, false));
            }
        }
        if (pending.isEmpty() && accepted.isEmpty())
        {
            card.add(small("No applicants yet.", MUTED));
        }
        if (!p.isFull())
        {
            card.add(buildAddMemberRow(p));
        }

        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JButton edit = new JButton("Edit");
        edit.addActionListener(e -> toggleForm());
        JButton disband = new JButton("Disband");
        disband.addActionListener(e -> onDisband(p));
        buttons.add(edit);
        buttons.add(disband);
        card.add(pinHeight(buttons));
        finish(card);
        return card;
    }

    // Seat someone who isn't on LFG (a buddy already in your team) so the
    // spot and its role show as taken to everyone browsing. Name + role
    // only; the row is created already accepted.
    private JPanel buildAddMemberRow(LfgParty p)
    {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        row.add(small("Add a member who isn't on LFG", ColorScheme.BRAND_ORANGE));

        JTextField name = new JTextField(addMemberDraft);
        name.setToolTipText("Their RSN");
        capLength(name, 12);
        name.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e)
            {
                addMemberDraft = name.getText();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e)
            {
                addMemberDraft = name.getText();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e)
            {
                addMemberDraft = name.getText();
            }
        });
        row.add(labeled("Name", name));

        JComboBox<LfgRole> roleBox = null;
        if (p.getActivity().hasRoles())
        {
            List<LfgRole> options = distinct(p.getOpenRoles());
            if (options.isEmpty())
            {
                options = LfgRoles.playableRoles(p.getActivity(), p.isHardMode());
            }
            roleBox = new JComboBox<>(options.toArray(new LfgRole[0]));
            row.add(labeled("Role", roleBox));
        }

        JPanel buttons = new JPanel(new GridLayout(1, 1));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        JButton add = new JButton("Add member");
        final JComboBox<LfgRole> roleBoxF = roleBox;
        Runnable submit = () -> {
            String rsn = name.getText().trim();
            if (rsn.isEmpty())
            {
                showError("Type their name first.");
                return;
            }
            if (LfgNames.equal(rsn, p.getHostRsn()))
            {
                showError("That's you — you're already in.");
                return;
            }
            LfgRole role = roleBoxF == null ? null : (LfgRole) roleBoxF.getSelectedItem();
            addMemberDraft = "";
            executor.submit(() -> {
                LfgPartyService.AddResult r = partyService.addMember(p.getId(), rsn, role);
                showError(r == LfgPartyService.AddResult.OK ? null
                    : r == LfgPartyService.AddResult.REJECTED
                        ? rsn + " is already in a party (or the party is full)."
                        : "Couldn't add " + rsn + " — try again.");
                refresh();
            });
        };
        add.addActionListener(e -> submit.run());
        name.addActionListener(e -> submit.run());
        buttons.add(add);
        row.add(pinHeight(buttons));
        return pinHeight(row);
    }

    private JPanel buildApplicantRow(LfgParty p, LfgApplicant a, boolean pending)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        boolean online = onlineNames.contains(LfgNames.normalize(a.getRsn()));
        StringBuilder text = new StringBuilder("<font color='" + (online ? "#3FBF3F" : "#BF3F3F") + "'>")
            .append(esc(a.getRsn())).append("</font>");
        // Second line: role, learner tag, KC — kept off the name line so the
        // Accept/Decline buttons never clip it.
        StringBuilder detail = new StringBuilder();
        if (a.getRole() != null)
        {
            detail.append(esc(a.getRole().getDisplayName()));
        }
        if (a.isLearner())
        {
            detail.append(detail.length() > 0 ? " · " : "").append("learner");
        }
        if (a.isAddedByHost())
        {
            detail.append(detail.length() > 0 ? " · " : "").append("added by you");
        }
        else
        {
            String kc = applicantKc(p, a);
            if (kc != null)
            {
                detail.append(detail.length() > 0 ? " · " : "").append(kc);
            }
        }
        if (detail.length() > 0)
        {
            text.append("<br><font color='#A0A0A0'>").append(detail).append("</font>");
        }
        JLabel label = html(text.toString());
        row.add(label, BorderLayout.CENTER);

        // Pending rows stack Accept over Decline so the text column keeps
        // enough width for "Role · learner · KC 123".
        JPanel buttons = new JPanel(new GridLayout(pending ? 2 : 1, 1, 0, 2));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        if (pending)
        {
            JButton accept = smallButton("Accept");
            accept.setEnabled(!p.isFull());
            accept.addActionListener(e -> runAction(
                () -> partyService.setStatus(p.getId(), a.getRsn(), LfgApplicant.Status.ACCEPTED),
                "Couldn't accept — try again."));
            JButton decline = smallButton("Decline");
            decline.addActionListener(e -> runAction(
                () -> partyService.setStatus(p.getId(), a.getRsn(), LfgApplicant.Status.DECLINED),
                "Couldn't decline — try again."));
            buttons.add(accept);
            buttons.add(decline);
        }
        else
        {
            JButton kick = smallButton("Kick");
            kick.addActionListener(e -> runAction(
                () -> partyService.removeApplicant(p.getId(), a.getRsn()),
                "Couldn't kick — try again."));
            buttons.add(kick);
        }
        row.add(buttons, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    // What a host sees next to an applicant, in order of trust: the KC the
    // applicant's own client recorded ("KC 75"), else a hiscore lookup
    // ("KC 75 (hiscores)"), else whatever they typed or prefilled
    // themselves ("KC 75 (self)"), else unknown. Null when the activity has
    // no kill count.
    private String applicantKc(LfgParty p, LfgApplicant a)
    {
        if (!p.getActivity().hasKillcount())
        {
            return null;
        }
        if (a.getKc() != null && a.getKcSource() == LfgApplicant.KcSource.LOCAL)
        {
            return "KC " + a.getKc();
        }
        boolean hard = isHard(p);
        boolean pending = false;
        if (config.lfgKcLookups())
        {
            LfgKillcountService.Result r = killcounts.cached(a.getRsn(), p.getActivity());
            if (r == null)
            {
                killcounts.lookup(a.getRsn(), p.getActivity(), this::rebuildList);
                pending = true;
            }
            else if (r.isKnown(hard))
            {
                return "KC " + r.killcount(hard) + " (hiscores)";
            }
        }
        if (a.getKc() != null)
        {
            return "KC " + a.getKc() + " (self)";
        }
        return pending ? "KC ..." : "KC ?";
    }

    // CM / HMT, or a ToA at expert-level invocation.
    private static boolean isHard(LfgParty p)
    {
        return p.isHardMode() || (p.getActivity().usesInvocation() && p.getInvocation() >= 300);
    }

    // ------------------------------------------------------------ helpers

    private LfgParty myParty()
    {
        String rsn = currentRsn;
        if (rsn == null)
        {
            return null;
        }
        for (LfgParty p : cached)
        {
            if (p.isHostedBy(rsn))
            {
                return p;
            }
        }
        return null;
    }

    // Runs a write on the executor, surfaces failure, then refreshes.
    private void runAction(java.util.function.BooleanSupplier action, String failureMessage)
    {
        executor.submit(() -> {
            boolean ok;
            try
            {
                ok = action.getAsBoolean();
            }
            catch (RuntimeException e)
            {
                ok = false;
            }
            showError(ok ? null : failureMessage);
            refresh();
        });
    }

    private void showError(String message)
    {
        SwingUtilities.invokeLater(() -> {
            errorLabel.setText(message == null ? "" : message);
            errorLabel.setVisible(message != null);
        });
    }

    // Activity sprite on the left of a card's title line.
    private JPanel titleRow(LfgParty p, JLabel title)
    {
        return titleRow(p.getActivity(), title);
    }

    private JPanel titleRow(LfgActivity activity, JLabel title)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(LfgIcons.label(icons, activity), BorderLayout.WEST);
        row.add(title, BorderLayout.CENTER);
        return pinHeight(row);
    }

    private static JPanel card()
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(5, 8, 6, 8)));
        return card;
    }

    // BoxLayout stretches children vertically unless their max height is
    // pinned to the preferred height.
    private static void finish(JPanel card)
    {
        pinHeight(card);
    }

    // Full width, natural height. Never hard-code a max height smaller
    // than the look-and-feel's preferred height: BoxLayout then clamps
    // the parent to the sum of maximums and clips whatever's below.
    private static <T extends JComponent> T pinHeight(T c)
    {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JLabel sectionHeader(String text)
    {
        JLabel header = new JLabel(" " + text);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setForeground(ColorScheme.BRAND_ORANGE);
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        header.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        return header;
    }

    private static JLabel html(String inner)
    {
        JLabel l = new JLabel("<html>" + inner + "</html>");
        l.setForeground(Color.WHITE);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel small(String text, Color color)
    {
        JLabel l = new JLabel(text);
        l.setForeground(color);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel note(String text)
    {
        JLabel l = small(text, MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return l;
    }

    // Multi-line text that wraps to the sidebar width. A JTextArea rather
    // than an HTML label: Swing's HTML layout under-measures the RuneScape
    // pixel font and clips the end of every wrapped line. Sizing the area
    // to the wrap width up front makes its preferred height reflect the
    // wrapped line count, so the card's pinned height is right.
    private static JTextArea wrapped(String text, Color color)
    {
        JTextArea t = new JTextArea(text);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setHighlighter(null);
        t.setOpaque(false);
        t.setForeground(color);
        t.setFont(FontManager.getRunescapeSmallFont());
        t.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        t.setAlignmentX(LEFT_ALIGNMENT);
        t.setSize(new Dimension(WRAP_WIDTH, Short.MAX_VALUE));
        t.setMaximumSize(new Dimension(Integer.MAX_VALUE, t.getPreferredSize().height));
        return t;
    }

    private JPanel labeled(String label, JComponent field)
    {
        return labeled(label, field, ColorScheme.DARKER_GRAY_COLOR);
    }

    private static JPanel labeled(String label, JComponent field, Color bg)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setBackground(bg);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        JLabel l = new JLabel(label);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(MUTED);
        l.setPreferredSize(new Dimension(80, 22));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return pinHeight(row);
    }

    private static JCheckBox checkbox(String text)
    {
        JCheckBox box = new JCheckBox(text);
        box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        box.setForeground(Color.WHITE);
        box.setFont(FontManager.getRunescapeSmallFont());
        box.setAlignmentX(LEFT_ALIGNMENT);
        return box;
    }

    private static JSpinner spinner(int value, int min, int max, int step)
    {
        return new JSpinner(new SpinnerNumberModel(clamp(value, min, max), min, max, step));
    }

    private static JButton smallButton(String text)
    {
        JButton b = new JButton(text);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setMargin(new java.awt.Insets(1, 4, 1, 4));
        return b;
    }

    private static int clamp(int v, int min, int max)
    {
        return Math.max(min, Math.min(max, v));
    }

    private static List<LfgRole> distinct(List<LfgRole> roles)
    {
        List<LfgRole> out = new ArrayList<>();
        for (LfgRole r : roles)
        {
            if (!out.contains(r))
            {
                out.add(r);
            }
        }
        return out;
    }

    private static void capLength(JTextField field, int max)
    {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter()
        {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException
            {
                if (string != null && fb.getDocument().getLength() + string.length() <= max)
                {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException
            {
                int newLength = fb.getDocument().getLength() - length + (text == null ? 0 : text.length());
                if (newLength <= max)
                {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
    }

    private static String esc(String text)
    {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String timeAgo(Instant then)
    {
        Duration d = Duration.between(then, Instant.now());
        long minutes = d.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = d.toHours();
        if (hours < 24) return hours + "h ago";
        return d.toDays() + "d ago";
    }
}
