package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.lfg.Activity;
import com.github.orgonag.fbclan.lfg.Killcounts;
import com.github.orgonag.fbclan.lfg.LootRule;
import com.github.orgonag.fbclan.lfg.Party;
import com.github.orgonag.fbclan.lfg.Role;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Window;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.FontManager;

/**
 * Host (or edit) a party in three steps: activity, requirements,
 * details. Everything in step 2 depends on the activity, size and mode,
 * so steps are rebuilt on change; widget values survive the rebuild.
 */
class HostWizard
{
    private static final String[] STEPS = {"Activity", "Requirements", "Details"};
    private static final List<String> RUN_TYPES = Arrays.asList("Normal", "Learner", "Teacher");
    private final Killcounts killcounts;
    private final boolean editing;
    private final String editingId;
    private final WhenPicker when;
    private final Function<Party, JComponent> preview;
    private final Consumer<HostWizard> onSubmit;
    private final JDialog dialog;

    private final JComboBox<Object> activityBox = LfgUi.combo(Activity.values());
    private final JCheckBox hardModeBox = LfgUi.checkbox("Hard mode");
    private final JSpinner invocationSpinner = spinner(150, 0, Party.MAX_INVOCATION, 5);
    private final JSpinner sizeSpinner = spinner(5, Party.MIN_CAPACITY, Party.MAX_CAPACITY, 1);
    private final Choice<LootRule> loot = new Choice<>(Arrays.asList(LootRule.values()),
        r -> r == LootRule.UNSPECIFIED ? "Any" : r.getDisplayName(), LootRule.UNSPECIFIED, r -> {});
    private final JSpinner minKcSpinner = spinner(0, 0, 100_000, 10);
    private final Choice<String> runType = new Choice<>(RUN_TYPES, s -> s, "Normal", s -> {});
    private final JComboBox<Object> hostRoleBox = new JComboBox<>();
    private final Map<Role, JSpinner> coxCounts = new EnumMap<>(Role.class);
    private final JTextField descField = Theme.field("", Party.MAX_DESCRIPTION);
    private final JLabel descCount = Theme.right(Theme.text("", Theme.FAINT));
    private final JLabel worldLabel = Theme.text("", Theme.SOFT);

    private final JLabel heading = Theme.heading("");
    private final JPanel stepBody = Theme.stack(10);
    private final JTextArea error = Theme.wrap("", Theme.RED);
    private final Btn back = Theme.button("Back", Btn.Kind.GHOST, this::back);
    private final Btn details = Theme.button("Next (details)", Btn.Kind.GHOST, this::next);
    private final Btn quick = Theme.button("Post now", Btn.Kind.PRIMARY, this::submit);
    private final Btn next = Theme.button("Next", Btn.Kind.PRIMARY, this::next);
    private int step;
    private int world;
    private boolean busy;
    private boolean rebuilding;

    // `editing` = the party to edit, or null to host a new one.
    HostWizard(Window owner, Killcounts killcounts, Party editing, WhenPicker.Limits limits, Function<Party, JComponent> preview, Consumer<HostWizard> onSubmit)
    {
        this.killcounts = killcounts;
        this.editing = editing != null;
        this.editingId = editing == null ? null : editing.getId();
        this.preview = preview;
        this.onSubmit = onSubmit;
        this.when = new WhenPicker(editing, limits);

        // Only the activity changes what step 1 shows; later steps rebuild on
        // entry. A new post starts at the activity's usual party size.
        activityBox.addActionListener(e -> {
            if (editingId == null && !rebuilding) sizeSpinner.setValue(activity().defaultPartySize());
            rebuild();
        });
        if (editingId == null) sizeSpinner.setValue(activity().defaultPartySize());
        descField.setToolTipText("Optional description (max " + Party.MAX_DESCRIPTION + " chars)");
        descField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { described(); }
            public void removeUpdate(DocumentEvent e) { described(); }
            public void changedUpdate(DocumentEvent e) { described(); }
        });
        if (editing != null) populate(editing);

        JPanel top = Theme.stack(8);
        top.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        top.add(heading);
        top.add(new Progress());
        stepBody.setBorder(BorderFactory.createEmptyBorder(4, 14, 8, 14));
        JPanel bodyHolder = new JPanel(new BorderLayout());
        bodyHolder.setBackground(Theme.BG);
        bodyHolder.add(stepBody, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(bodyHolder);
        scroll.setBorder(null);
        Theme.slim(scroll);
        JPanel footer = Theme.stack(6);
        footer.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 14));
        error.setVisible(false);
        footer.add(error);
        JPanel forward = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        forward.setOpaque(false);
        forward.add(details);
        forward.add(quick);
        forward.add(next);
        footer.add(Theme.row(back, null, forward));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        dialog = new JDialog(owner, this.editing ? "Final Boss · Edit your party" : "Final Boss · Host a party");
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getRootPane().registerKeyboardAction(e -> close(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setContentPane(root);
        dialog.setSize(new Dimension(360, 500));
        dialog.setLocationRelativeTo(owner);
        show(0);
        dialog.setVisible(true);
    }

    // ------------------------------------------------------------ outside

    boolean isOpen()
    {
        return dialog.isDisplayable();
    }

    // The id of the post being edited, else null.
    String editingId()
    {
        return editingId;
    }

    void focus()
    {
        dialog.toFront();
    }

    void close()
    {
        dialog.dispose();
    }

    void showError(String message)
    {
        error.setText(message == null ? "" : message);
        error.setVisible(message != null);
        setBusy(busy);
        error.getParent().revalidate();
    }

    void setBusy(boolean busy)
    {
        this.busy = busy;
        next.setEnabled(!busy);
        quick.setEnabled(!busy);
    }

    void setWorld(int world)
    {
        this.world = world;
        worldLabel.setText(world > 0 ? "W" + world + " (your current world)" : "Unknown — log in to a world");
    }

    // The party the form describes; id/created_at are server-owned.
    Party build(String hostRsn, int world)
    {
        try
        {
            sizeSpinner.commitEdit();
            invocationSpinner.commitEdit();
            minKcSpinner.commitEdit();
            for (JSpinner s : coxCounts.values()) s.commitEdit();
        }
        catch (ParseException e)
        {
            throw new IllegalArgumentException("Enter valid numeric values.");
        }
        Activity activity = activity();
        boolean hard = activity.hasHardMode() && hardModeBox.isSelected();
        int capacity = (Integer) sizeSpinner.getValue();
        // A quick post skips step 2, so pick a valid default role here.
        if (activity.hasRoles()) syncRoles(activity, capacity, hard);
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        coxCounts.forEach((r, s) -> counts.put(r, (Integer) s.getValue()));
        return Party.builder()
            .hostRsn(hostRsn)
            .activity(activity)
            .hardMode(hard)
            .invocation(activity.usesInvocation() ? (Integer) invocationSpinner.getValue() : 0)
            .capacity(capacity)
            .description(descField.getText())
            .world(world > 0 ? world : null)
            .minKc(activity.hasKillcount() ? (Integer) minKcSpinner.getValue() : 0)
            .lootRule(loot.value())
            .requiredRoles(Role.required(activity, hard, capacity, counts))
            .hostRole(activity.hasRoles() ? (Role) hostRoleBox.getSelectedItem() : null)
            .learner(activity.isRaid() && "Learner".equals(runType.value()))
            .teacher(activity.isRaid() && "Teacher".equals(runType.value()))
            .createdAt(Instant.now())
            .scheduledFor(when.start())
            .applicants(Collections.emptyList())
            .build();
    }

    // ------------------------------------------------------------ steps

    private void back()
    {
        show(step - 1);
    }

    // Post (or save) with whatever is set; untouched steps keep their defaults.
    private void submit()
    {
        onSubmit.accept(this);
    }

    private void next()
    {
        if (step == STEPS.length - 1)
        {
            submit();
            return;
        }
        if (step == 1)
        {
            // Validate before the preview needs a whole party.
            try
            {
                build("You", 0);
            }
            catch (IllegalArgumentException e)
            {
                showError(e.getMessage());
                return;
            }
        }
        show(step + 1);
    }

    private void show(int index)
    {
        step = index;
        showError(null);
        heading.setText(new String[]{"Pick an activity", "Set requirements", "Final details"}[step]);
        back.setVisible(step > 0);
        details.setVisible(step == 0);
        quick.setVisible(step == 0);
        quick.setText(editing ? "Save now" : "Post now");
        next.setVisible(step > 0);
        next.setText(step < STEPS.length - 1 ? "Next" : editing ? "Save changes" : "Post party");
        rebuild();
        dialog.repaint();
    }

    // Listeners on the widgets adjusted here call back in; the guard stops
    // the nested call from re-adding rows mid-rebuild.
    private void rebuild()
    {
        if (rebuilding) return;
        rebuilding = true;
        try
        {
            // Changing the activity rebuilds step 1 around its own combo; keep keyboard focus on it.
            boolean comboFocused = activityBox.isFocusOwner();
            stepBody.removeAll();
            Activity activity = activity();
            int size = clampSize(activity);
            if (step == 0) activityStep(activity);
            else if (step == 1) requirementsStep(activity, size);
            else detailsStep();
            stepBody.revalidate();
            stepBody.repaint();
            if (comboFocused) activityBox.requestFocusInWindow();
        }
        finally
        {
            rebuilding = false;
        }
    }

    private void activityStep(Activity activity)
    {
        stepBody.add(Theme.text("What are you running?", Theme.SUB));
        stepBody.add(activityBox);
        if (activity.hasHardMode())
        {
            hardModeBox.setText(activity == Activity.TOB ? "Hard mode (HMT)"
                : activity == Activity.COX ? "Challenge mode (CM)" : activity.getHardModeLabel());
            stepBody.add(hardModeBox);
        }
        else
        {
            hardModeBox.setSelected(false);
        }
        if (activity.usesInvocation()) stepBody.add(Theme.labeled("Invocation", invocationSpinner));
        stepBody.add(Theme.labeled("Party size", sizeSpinner));
        stepBody.add(when);
        when.summarize();
    }

    private void requirementsStep(Activity activity, int size)
    {
        stepBody.add(Theme.text("Loot", Theme.SUB));
        stepBody.add(loot);
        if (activity.hasKillcount())
        {
            stepBody.add(Theme.labeled("Min KC (0=any)", minKcSpinner));
            Integer mine = killcounts.local(activity, activity.hasHardMode() && hardModeBox.isSelected());
            if (mine != null) stepBody.add(Theme.text("Your KC: " + mine, Theme.SUB));
        }
        else
        {
            minKcSpinner.setValue(0);
        }
        if (activity.isRaid())
        {
            stepBody.add(Theme.text("Run type", Theme.SUB));
            stepBody.add(runType);
        }
        else
        {
            runType.set("Normal");
        }
        if (!activity.hasRoles())
        {
            hostRoleBox.removeAllItems();
            return;
        }
        boolean hard = hardModeBox.isSelected();
        syncRoles(activity, size, hard);
        stepBody.add(Theme.labeled("Your role", hostRoleBox));
        if (activity == Activity.TOB)
        {
            stepBody.add(Theme.wrap("Team: " + Role.summarize(Role.tobComposition(size, hard)), Theme.SUB));
        }
        else if (activity == Activity.COX)
        {
            stepBody.add(Theme.text("Roles wanted (rest = Fill):", Theme.SUB));
            Map<Role, Integer> keep = new EnumMap<>(Role.class);
            coxCounts.forEach((r, s) -> keep.put(r, (Integer) s.getValue()));
            coxCounts.clear();
            int max = (Integer) ((SpinnerNumberModel) sizeSpinner.getModel()).getMaximum();
            for (Role r : Role.playable(activity, hard))
            {
                if (r.isFill()) continue;
                JSpinner s = spinner(keep.getOrDefault(r, 0), 0, max, 1);
                coxCounts.put(r, s);
                stepBody.add(Theme.labeled("  " + r.getDisplayName(), s));
            }
        }
        else
        {
            stepBody.add(Theme.text("One of each role; a 5th may double up.", Theme.SUB));
        }
    }

    private void detailsStep()
    {
        stepBody.add(Theme.text("Description (optional)", Theme.SUB));
        stepBody.add(descField);
        stepBody.add(descCount);
        stepBody.add(Theme.labeled("World", worldLabel));
        stepBody.add(Theme.text("Preview", Theme.SUB));
        stepBody.add(previewCard());
        described();
    }

    // Live counter, and the preview follows the text.
    private void described()
    {
        descCount.setText(descField.getText().length() + " / " + Party.MAX_DESCRIPTION);
        if (step == 2 && stepBody.getComponentCount() > 0)
        {
            stepBody.remove(stepBody.getComponentCount() - 1);
            stepBody.add(previewCard());
            stepBody.revalidate();
            stepBody.repaint();
        }
    }

    private JComponent previewCard()
    {
        try
        {
            return preview.apply(build("You", world));
        }
        catch (IllegalArgumentException e)
        {
            return Theme.text(e.getMessage(), Theme.RED);
        }
    }

    // Fill from an existing party (Edit).
    private void populate(Party p)
    {
        activityBox.setSelectedItem(p.getActivity());
        hardModeBox.setSelected(p.isHardMode());
        invocationSpinner.setValue(p.getInvocation());
        clampSize(p.getActivity());
        SpinnerNumberModel m = (SpinnerNumberModel) sizeSpinner.getModel();
        sizeSpinner.setValue(Math.max((Integer) m.getMinimum(), Math.min((Integer) m.getMaximum(), p.getCapacity())));
        loot.set(p.getLootRule());
        minKcSpinner.setValue(p.getMinKc());
        runType.set(p.isLearner() ? "Learner" : p.isTeacher() ? "Teacher" : "Normal");
        descField.setText(p.getDescription() == null ? "" : p.getDescription());
        if (p.getHostRole() != null)
        {
            hostRoleBox.addItem(p.getHostRole());
            hostRoleBox.setSelectedItem(p.getHostRole());
        }
        if (p.getActivity() == Activity.COX)
        {
            for (Role r : p.getRequiredRoles())
            {
                JSpinner s = coxCounts.computeIfAbsent(r, k -> spinner(0, 0, Party.MAX_CAPACITY, 1));
                s.setValue((Integer) s.getValue() + 1);
            }
        }
    }

    // ------------------------------------------------------------ helpers

    // The host-role choices for this activity, size and mode; keeps the
    // current pick when it is still valid, else the first role.
    private void syncRoles(Activity activity, int size, boolean hard)
    {
        Object previous = hostRoleBox.getSelectedItem();
        hostRoleBox.removeAllItems();
        List<Role> roles = activity == Activity.TOB ? distinct(Role.tobComposition(size, hard)) : Role.playable(activity, hard);
        roles.forEach(hostRoleBox::addItem);
        if (previous != null && roles.contains(previous)) hostRoleBox.setSelectedItem(previous);
    }

    private Activity activity()
    {
        return (Activity) activityBox.getSelectedItem();
    }

    // Party size limits follow the activity; returns the clamped size.
    private int clampSize(Activity activity)
    {
        int min = Math.max(Party.MIN_CAPACITY, activity.getMinPartySize());
        int max = Math.max(min, activity.getMaxPartySize());
        SpinnerNumberModel model = (SpinnerNumberModel) sizeSpinner.getModel();
        int size = Math.max(min, Math.min(max, (Integer) sizeSpinner.getValue()));
        model.setMinimum(min);
        model.setMaximum(max);
        if ((Integer) sizeSpinner.getValue() != size) sizeSpinner.setValue(size);
        return size;
    }

    private static JSpinner spinner(int value, int min, int max, int step)
    {
        return new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(max, value)), min, max, step));
    }

    private static List<Role> distinct(List<Role> roles)
    {
        List<Role> out = new ArrayList<>();
        for (Role r : roles)
        {
            if (!out.contains(r)) out.add(r);
        }
        return out;
    }

    // Three segments, filled up to the current step, with their names.
    private final class Progress extends JComponent
    {
        Progress()
        {
            setPreferredSize(new Dimension(10, 20));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = Theme.smooth(g);
            int seg = (getWidth() - 8) / 3;
            g2.setFont(FontManager.getRunescapeSmallFont());
            for (int i = 0; i < STEPS.length; i++)
            {
                int x = i * (seg + 4);
                g2.setColor(i <= step ? Theme.ACCENT : Theme.LINE);
                g2.fillRoundRect(x, 0, seg, 4, 4, 4);
                g2.setColor(i == step ? Theme.ACCENT_HI : i < step ? Theme.SOFT : Theme.FAINT);
                g2.drawString(STEPS[i].toUpperCase(Locale.ROOT), x, 17);
            }
            g2.dispose();
        }
    }
}
