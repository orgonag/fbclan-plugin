package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.lfg.Activity;
import com.github.orgonag.fbclan.lfg.Killcounts;
import com.github.orgonag.fbclan.lfg.LootRule;
import com.github.orgonag.fbclan.lfg.Party;
import com.github.orgonag.fbclan.lfg.Role;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.ui.ColorScheme;

/**
 * The host-a-party form. Everything below the activity picker depends
 * on the activity (and size / hard mode), so that part is rebuilt on
 * change; widget values survive the rebuild.
 */
class HostForm extends JPanel
{
    private final Killcounts killcounts;
    private final JComboBox<Activity> activityBox = new JComboBox<>(Activity.values());
    private final JPanel dynamic = Ui.column(ColorScheme.DARKER_GRAY_COLOR);
    private final JCheckBox hardModeBox = Ui.checkbox("Hard mode");
    private final JSpinner invocationSpinner = Ui.spinner(150, 0, Party.MAX_INVOCATION, 5);
    private final JSpinner sizeSpinner = Ui.spinner(5, Party.MIN_CAPACITY, Party.MAX_CAPACITY, 1);
    private final JComboBox<LootRule> lootBox = new JComboBox<>(LootRule.values());
    private final JSpinner minKcSpinner = Ui.spinner(0, 0, 100_000, 10);
    private final JCheckBox learnerBox = Ui.checkbox("Learner");
    private final JCheckBox teacherBox = Ui.checkbox("Teacher");
    private final JComboBox<Role> hostRoleBox = new JComboBox<>();
    private final Map<Role, JSpinner> coxCounts = new EnumMap<>(Role.class);
    private final JTextField descField = new JTextField();
    private final JLabel worldLabel = Ui.note("World: unknown");
    private final JButton submit = new JButton("Create");
    private boolean rebuilding;

    HostForm(Killcounts killcounts, Runnable onSubmit, Runnable onCancel)
    {
        this.killcounts = killcounts;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setAlignmentX(LEFT_ALIGNMENT);

        activityBox.setRenderer(new Ui.ActivityRenderer());
        activityBox.setAlignmentX(LEFT_ALIGNMENT);
        activityBox.addActionListener(e -> rebuild());
        add(Ui.pinHeight(activityBox));
        add(Box.createRigidArea(new Dimension(0, 3)));
        add(dynamic);

        sizeSpinner.addChangeListener(e -> rebuild());
        hardModeBox.addActionListener(e -> rebuild());
        learnerBox.addActionListener(e -> {
            if (learnerBox.isSelected()) teacherBox.setSelected(false);
        });
        teacherBox.addActionListener(e -> {
            if (teacherBox.isSelected()) learnerBox.setSelected(false);
        });
        descField.setToolTipText("Optional description (max " + Party.MAX_DESCRIPTION + " chars)");
        Ui.capLength(descField, Party.MAX_DESCRIPTION);
        add(Ui.labeled("Description", descField));
        add(worldLabel);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        submit.addActionListener(e -> onSubmit.run());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> onCancel.run());
        buttons.add(submit);
        buttons.add(cancel);
        add(Box.createRigidArea(new Dimension(0, 5)));
        add(Ui.pinHeight(buttons));
        rebuild();
    }

    void setEditing(boolean editing)
    {
        submit.setText(editing ? "Save" : "Create");
    }

    void setWorld(int world)
    {
        worldLabel.setText(world > 0 ? "World: " + world + " (your current world)" : "World: unknown");
    }

    // Fill from an existing party (Edit).
    void populate(Party p)
    {
        activityBox.setSelectedItem(p.getActivity());
        hardModeBox.setSelected(p.isHardMode());
        invocationSpinner.setValue(p.getInvocation());
        rebuild();
        SpinnerNumberModel m = (SpinnerNumberModel) sizeSpinner.getModel();
        sizeSpinner.setValue(Ui.clamp(p.getCapacity(), (Integer) m.getMinimum(), (Integer) m.getMaximum()));
        lootBox.setSelectedItem(p.getLootRule());
        minKcSpinner.setValue(p.getMinKc());
        learnerBox.setSelected(p.isLearner());
        teacherBox.setSelected(p.isTeacher());
        descField.setText(p.getDescription() == null ? "" : p.getDescription());
        rebuild();
        if (p.getHostRole() != null)
        {
            hostRoleBox.setSelectedItem(p.getHostRole());
        }
        if (p.getActivity() == Activity.COX)
        {
            coxCounts.values().forEach(s -> s.setValue(0));
            for (Role r : p.getRequiredRoles())
            {
                JSpinner s = coxCounts.get(r);
                if (s != null)
                {
                    s.setValue((Integer) s.getValue() + 1);
                }
            }
        }
    }

    // The party the form describes; id/created_at are server-owned.
    Party build(String hostRsn, int world)
    {
        Activity activity = (Activity) activityBox.getSelectedItem();
        boolean hard = activity.hasHardMode() && hardModeBox.isSelected();
        int capacity = (Integer) sizeSpinner.getValue();
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
            .lootRule((LootRule) lootBox.getSelectedItem())
            .requiredRoles(Role.required(activity, hard, capacity, counts))
            .hostRole(activity.hasRoles() ? (Role) hostRoleBox.getSelectedItem() : null)
            .learner(activity.isRaid() && learnerBox.isSelected())
            .teacher(activity.isRaid() && teacherBox.isSelected())
            .createdAt(Instant.now())
            .applicants(Collections.emptyList())
            .build();
    }

    // Listeners on the widgets adjusted here call back in; the guard stops
    // the nested call from re-adding rows mid-rebuild.
    private void rebuild()
    {
        if (rebuilding)
        {
            return;
        }
        rebuilding = true;
        try
        {
            rebuildInner();
        }
        finally
        {
            rebuilding = false;
        }
    }

    private void rebuildInner()
    {
        Activity activity = (Activity) activityBox.getSelectedItem();
        dynamic.removeAll();

        if (activity.hasHardMode())
        {
            hardModeBox.setText(activity == Activity.TOB ? "Hard mode (HMT)"
                : activity == Activity.COX ? "Challenge mode (CM)" : activity.getHardModeLabel());
            dynamic.add(hardModeBox);
        }
        else
        {
            hardModeBox.setSelected(false);
        }
        if (activity.usesInvocation())
        {
            dynamic.add(Ui.labeled("Invocation", invocationSpinner));
        }

        int min = Math.max(Party.MIN_CAPACITY, activity.getMinPartySize());
        int max = Math.max(min, activity.getMaxPartySize());
        SpinnerNumberModel sizeModel = (SpinnerNumberModel) sizeSpinner.getModel();
        int size = Ui.clamp((Integer) sizeSpinner.getValue(), min, max);
        sizeModel.setMinimum(min);
        sizeModel.setMaximum(max);
        if ((Integer) sizeSpinner.getValue() != size)
        {
            sizeSpinner.setValue(size);
        }
        dynamic.add(Ui.labeled("Party size", sizeSpinner));
        dynamic.add(Ui.labeled("Loot", lootBox));
        if (activity.hasKillcount())
        {
            dynamic.add(Ui.labeled("Min KC (0=any)", minKcSpinner));
            Integer mine = killcounts.local(activity, activity.hasHardMode() && hardModeBox.isSelected());
            if (mine != null)
            {
                dynamic.add(Ui.note("Your KC: " + mine));
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
            dynamic.add(Ui.pinHeight(tags));
        }
        else
        {
            learnerBox.setSelected(false);
            teacherBox.setSelected(false);
        }

        if (activity.hasRoles())
        {
            boolean hard = hardModeBox.isSelected();
            // Read the pick before clearing, so a size/mode change keeps it.
            Role previous = (Role) hostRoleBox.getSelectedItem();
            hostRoleBox.removeAllItems();
            List<Role> roles = activity == Activity.TOB ? distinct(Role.tobComposition(size, hard)) : Role.playable(activity, hard);
            roles.forEach(hostRoleBox::addItem);
            if (previous != null && roles.contains(previous))
            {
                hostRoleBox.setSelectedItem(previous);
            }
            dynamic.add(Ui.labeled("Your role", hostRoleBox));
            if (activity == Activity.TOB)
            {
                dynamic.add(Ui.wrapped("Team: " + Role.summarize(Role.tobComposition(size, hard)), Ui.MUTED));
            }
            else if (activity == Activity.COX)
            {
                dynamic.add(Ui.note("Roles wanted (rest = Fill):"));
                Map<Role, Integer> keep = new EnumMap<>(Role.class);
                coxCounts.forEach((r, s) -> keep.put(r, (Integer) s.getValue()));
                coxCounts.clear();
                for (Role r : Role.playable(activity, hard))
                {
                    if (!r.isFill())
                    {
                        JSpinner s = Ui.spinner(keep.getOrDefault(r, 0), 0, max, 1);
                        coxCounts.put(r, s);
                        dynamic.add(Ui.labeled("  " + r.getDisplayName(), s));
                    }
                }
            }
            else
            {
                dynamic.add(Ui.note("One of each role; a 5th may double up."));
            }
        }
        else
        {
            hostRoleBox.removeAllItems();
        }
        dynamic.revalidate();
        dynamic.repaint();
        revalidate();
    }

    private static List<Role> distinct(List<Role> roles)
    {
        List<Role> out = new ArrayList<>();
        for (Role r : roles)
        {
            if (!out.contains(r))
            {
                out.add(r);
            }
        }
        return out;
    }
}
