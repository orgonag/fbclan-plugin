package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.lfg.Activity;
import com.github.orgonag.fbclan.lfg.FormedParty;
import com.github.orgonag.fbclan.lfg.Killcounts;
import com.github.orgonag.fbclan.lfg.LootRule;
import com.github.orgonag.fbclan.lfg.Party;
import com.github.orgonag.fbclan.lfg.Party.Applicant;
import com.github.orgonag.fbclan.lfg.PartyApi;
import com.github.orgonag.fbclan.lfg.PartyBoard;
import com.github.orgonag.fbclan.lfg.Role;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * The LFG tab: host a party, browse and apply, manage applicants, and
 * see recently formed parties. Renders whatever PartyBoard holds; every
 * write goes through the board (executor) and re-renders on the EDT.
 */
@Singleton
public class PartiesTab extends JPanel
{
    private final Clan clan;
    private final FinalBossConfig config;
    private final PartyBoard board;
    private final PartyApi api;
    private final Killcounts killcounts;
    private final ItemManager items;
    private final ScheduledExecutorService executor;

    private final JPanel list;
    private final HostForm form;
    private final JButton hostButton = new JButton("Host a party");
    private final JLabel error = Ui.small("", ColorScheme.PROGRESS_ERROR_COLOR);
    private final JComboBox<Object> filterBox;
    private boolean formVisible;
    private boolean editing;
    private String applyingId;
    private Activity filter;
    private boolean hideFull;
    private boolean showFormed;
    private String addMemberDraft = "";

    @Inject
    public PartiesTab(Clan clan, FinalBossConfig config, PartyBoard board, PartyApi api, Killcounts killcounts,
                      ItemManager items, ScheduledExecutorService executor)
    {
        this.clan = clan;
        this.config = config;
        this.board = board;
        this.api = api;
        this.killcounts = killcounts;
        this.items = items;
        this.executor = executor;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel controls = Ui.column(ColorScheme.DARK_GRAY_COLOR);
        controls.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        hostButton.setAlignmentX(LEFT_ALIGNMENT);
        hostButton.addActionListener(e -> toggleForm());
        controls.add(Ui.pinHeight(hostButton));
        controls.add(Box.createRigidArea(new Dimension(0, 5)));

        form = new HostForm(killcounts, this::submitForm, () -> {
            formVisible = false;
            editing = false;
            render();
        });
        form.setVisible(false);
        controls.add(form);
        controls.add(Box.createRigidArea(new Dimension(0, 5)));

        List<Object> filters = new ArrayList<>();
        filters.add("All activities");
        Collections.addAll(filters, Activity.values());
        filterBox = new JComboBox<>(filters.toArray());
        filterBox.setRenderer(new Ui.ActivityRenderer());
        filterBox.setAlignmentX(LEFT_ALIGNMENT);
        filterBox.addActionListener(e -> {
            Object sel = filterBox.getSelectedItem();
            filter = sel instanceof Activity ? (Activity) sel : null;
            render();
        });
        controls.add(Ui.pinHeight(filterBox));
        JPanel toggles = new JPanel(new GridLayout(1, 2));
        toggles.setBackground(ColorScheme.DARK_GRAY_COLOR);
        toggles.setAlignmentX(LEFT_ALIGNMENT);
        JCheckBox hideFullBox = Ui.checkbox("Hide full");
        hideFullBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        hideFullBox.addActionListener(e -> {
            hideFull = hideFullBox.isSelected();
            render();
        });
        JCheckBox showFormedBox = Ui.checkbox("Show formed");
        showFormedBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        showFormedBox.setToolTipText("Parties that filled up in the last 7 days");
        showFormedBox.addActionListener(e -> {
            showFormed = showFormedBox.isSelected();
            render();
        });
        toggles.add(hideFullBox);
        toggles.add(showFormedBox);
        controls.add(Ui.pinHeight(toggles));
        error.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        error.setVisible(false);
        controls.add(error);
        add(controls, BorderLayout.NORTH);
        list = Ui.scrollList(this);

        board.setListener(() -> SwingUtilities.invokeLater(this::render));
        render();
    }

    public void refresh()
    {
        executor.submit(board::refresh);
    }

    public void reset()
    {
        SwingUtilities.invokeLater(() -> {
            applyingId = null;
            applicationPanel = null;
            applicationShape = null;
            formVisible = false;
            editing = false;
            addMemberDraft = "";
            render();
        });
    }

    // ------------------------------------------------------------ host form

    private void toggleForm()
    {
        Party mine = board.mine();
        if (mine != null)
        {
            form.populate(mine);
            editing = true;
            formVisible = true;
        }
        else
        {
            formVisible = !formVisible;
            editing = false;
        }
        render();
    }

    private void submitForm()
    {
        String rsn = clan.rsn();
        if (rsn == null)
        {
            return;
        }
        try
        {
            Party draft = form.build(rsn, board.world());
            Party existing = editing ? board.mine() : null;
            Party party = existing == null ? draft : draft.toBuilder().id(existing.getId()).version(existing.getVersion()).build();
            board.run(() -> api.save(party), "Couldn't save your party. Refresh and try again.", message -> SwingUtilities.invokeLater(() -> {
                showError(message);
                if (message == null) { formVisible = false; editing = false; }
                render();
            }));
        }
        catch (IllegalArgumentException e) { showError(e.getMessage()); }

    }

    // ------------------------------------------------------------ render

    private void render()
    {
        String rsn = clan.rsn();
        if (board.refreshError() != null) { error.setText(board.refreshError()); error.setVisible(true); }
        Party mine = board.mine();
        form.setVisible(formVisible);
        form.setEditing(editing);
        form.setWorld(board.world());
        hostButton.setEnabled(!board.isBusy() && clan.canUpload() && config.enableLfg());
        hostButton.setText(mine != null ? "Edit your party" : formVisible ? "Hide form" : "Host a party");

        list.removeAll();
        if (mine != null)
        {
            list.add(Ui.header("Your party"));
            list.add(hostCard(mine));
        }
        List<Party> others = new ArrayList<>();
        for (Party p : board.parties())
        {
            boolean isMine = mine != null && p.getId().equals(mine.getId());
            boolean filtered = filter != null && p.getActivity() != filter;
            boolean hidden = hideFull && p.isFull() && p.applicantFor(rsn) == null;
            if (!isMine && !filtered && !hidden)
            {
                others.add(p);
            }
        }
        // Parties the player is in (or waiting on) float to the top.
        others.sort((a, b) -> {
            boolean ia = a.applicantFor(rsn) != null;
            boolean ib = b.applicantFor(rsn) != null;
            return ia != ib ? (ia ? -1 : 1) : b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        if (mine != null)
        {
            list.add(Ui.header("Open parties (" + others.size() + ")"));
        }
        if (others.isEmpty())
        {
            list.add(Ui.empty(board.parties().isEmpty() ? "No parties are being hosted right now." : "No parties match your filters."));
        }
        for (Party p : others)
        {
            list.add(partyCard(p));
        }
        if (showFormed)
        {
            List<FormedParty> formed = new ArrayList<>();
            for (FormedParty f : board.formed())
            {
                if (filter == null || f.getActivity() == filter)
                {
                    formed.add(f);
                }
            }
            list.add(Ui.header("Formed parties (" + formed.size() + ")"));
            if (formed.isEmpty())
            {
                list.add(Ui.empty("No parties have formed in the last 7 days."));
            }
            for (FormedParty f : formed)
            {
                list.add(formedCard(f));
            }
        }
        list.revalidate();
        list.repaint();
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------ cards

    private JPanel partyCard(Party p)
    {
        String rsn = clan.rsn();
        JPanel card = Ui.card();
        boolean hostOnline = board.online().contains(Names.normalize(p.getHostRsn()));
        card.add(titleRow(p.getActivity(), "<b>" + Ui.esc(p.title()) + "</b>" + world(p.getWorld())
            + "  <font color='" + (hostOnline ? "#3FBF3F" : "#BF3F3F") + "'>" + Ui.esc(p.getHostRsn()) + "</font>"));

        StringBuilder meta = new StringBuilder().append(p.memberCount()).append('/').append(p.getCapacity());
        if (p.getLootRule() != LootRule.UNSPECIFIED)
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
        meta.append(" · ").append(Ui.timeAgo(p.getCreatedAt()));
        card.add(Ui.small(meta.toString(), Ui.MUTED));
        needsLine(card, p);
        if (p.getDescription() != null)
        {
            card.add(Ui.wrapped("\"" + p.getDescription() + "\"", Ui.MUTED));
        }
        List<Applicant> accepted = p.accepted();
        if (!accepted.isEmpty())
        {
            StringBuilder with = new StringBuilder("With: ");
            for (int i = 0; i < accepted.size(); i++)
            {
                with.append(i > 0 ? ", " : "").append(accepted.get(i).getRsn());
            }
            card.add(Ui.wrapped(with.toString(), Ui.MUTED));
        }

        Applicant mine = p.applicantFor(rsn);
        JPanel actions = actionRow();
        if (mine != null)
        {
            String state = mine.isAccepted() ? "Accepted" : mine.isPending() ? "Pending" : "Declined";
            JButton leave = new JButton(mine.isAccepted() ? "Leave party" : mine.isPending() ? "Withdraw" : "Dismiss");
            leave.addActionListener(e -> {
                board.expectSelfLeave(p.getId());
                run(() -> api.withdraw(p.getId(), rsn), "Couldn't withdraw — try again.");
            });
            actions.setLayout(new BorderLayout(5, 0));
            actions.add(Ui.small(state, mine.isAccepted() ? Ui.ONLINE : Ui.MUTED), BorderLayout.CENTER);
            actions.add(leave, BorderLayout.EAST);
            card.add(actions);
        }
        else if (p.getId().equals(applyingId))
        {
            card.add(applyRow(p));
        }
        else
        {
            JButton apply = new JButton("Apply");
            if (rsn == null)
            {
                apply.setEnabled(false);
            }
            else if (board.mine() != null)
            {
                apply.setText("Disband yours to apply");
                apply.setEnabled(false);
            }
            else if (p.isFull())
            {
                apply.setText("Full");
                apply.setEnabled(false);
            }
            else if (p.getActivity().hasRoles() && p.openRoles().isEmpty())
            {
                apply.setText("No open roles");
                apply.setEnabled(false);
            }
            apply.addActionListener(e -> {
                applyingId = p.getId();
                render();
            });
            actions.add(apply);
            card.add(actions);
        }
        return Ui.pinHeight(card);
    }

    private JPanel applicationPanel;
    private String applicationShape;
    private JPanel applyRow(Party p)
    {
        String shape = p.getId() + ":" + p.getActivity() + ":" + p.isHardMode() + ":" + p.openRoles();
        if (shape.equals(applicationShape) && applicationPanel != null) return applicationPanel;
        applicationShape = shape;

        String rsn = clan.rsn();
        JPanel row = Ui.column(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JComboBox<Role> roleBox = null;
        if (p.getActivity().hasRoles())
        {
            roleBox = new JComboBox<>(Role.applyOptions(p.getActivity(), p.isHardMode(), p.openRoles()).toArray(new Role[0]));
            row.add(Ui.labeled("Role", roleBox));
        }
        JCheckBox learner = null;
        if (p.getActivity().isRaid())
        {
            learner = Ui.checkbox("I'm a learner");
            row.add(learner);
        }
        // Your KC for this activity: your own client's record first, else
        // a hiscore lookup, else blank to type. Editing it marks the value
        // self-reported. The host sees it; it never blocks applying.
        JSpinner kcSpinner = null;
        Integer prefill = null;
        Party.KcSource source = null;
        if (p.getActivity().hasKillcount())
        {
            prefill = killcounts.local(p.getActivity(), p.isHard());
            if (prefill != null)
            {
                source = Party.KcSource.LOCAL;
            }
            else if (rsn != null && config.lfgKcLookups())
            {
                Killcounts.Hiscore h = killcounts.cached(rsn, p.getActivity());
                if (h == null)
                {
                    killcounts.lookup(rsn, p.getActivity(), () -> SwingUtilities.invokeLater(this::render));
                }
                else if (h.known(p.isHard()))
                {
                    prefill = h.kc(p.isHard());
                    source = Party.KcSource.HISCORES;
                }
            }
            kcSpinner = Ui.spinner(prefill == null ? 0 : prefill, 0, 100_000, 1);
            row.add(Ui.labeled(source == Party.KcSource.LOCAL ? "Your KC (auto)"
                : source == Party.KcSource.HISCORES ? "Your KC (hiscores)" : "Your KC", kcSpinner));
            if (p.getMinKc() > 0)
            {
                boolean below = prefill != null && prefill < p.getMinKc();
                JLabel ask = Ui.note("Host asks for " + p.getMinKc() + "+ KC" + (below ? " - you're below it" : ""));
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
        JComboBox<Role> roleBoxF = roleBox;
        JCheckBox learnerF = learner;
        JSpinner kcSpinnerF = kcSpinner;
        Integer prefillF = prefill;
        Party.KcSource sourceF = source;
        confirm.addActionListener(e -> {
            if (rsn == null)
            {
                return;
            }
            Role role = roleBoxF == null ? null : (Role) roleBoxF.getSelectedItem();
            boolean isLearner = learnerF != null && learnerF.isSelected();
            Integer kc = null;
            Party.KcSource kcSource = null;
            if (kcSpinnerF != null)
            {
                try { kcSpinnerF.commitEdit(); } catch (java.text.ParseException ex) { showError("Enter a valid kill count."); return; }
                int typed = (Integer) kcSpinnerF.getValue();
                if (prefillF != null && typed == prefillF)
                {
                    kc = typed;
                    kcSource = sourceF;
                }
                else if (typed > 0)
                {
                    kc = typed;
                    kcSource = Party.KcSource.MANUAL;
                }
            }
            Integer kcF = kc;
            Party.KcSource kcSourceF = kcSource;
            board.run(() -> api.apply(p.getId(), rsn, role, isLearner, kcF, kcSourceF), "Couldn't apply. Refresh and try again.",
                message -> SwingUtilities.invokeLater(() -> { if (message == null) applyingId = null; showError(message); render(); }));
        });
        cancel.addActionListener(e -> {
            applyingId = null;
            render();
        });
        buttons.add(confirm);
        buttons.add(cancel);
        row.add(Ui.pinHeight(buttons));
        applicationPanel = Ui.pinHeight(row);
        return applicationPanel;
    }

    private JPanel hostCard(Party p)
    {
        JPanel card = Ui.card();
        card.add(titleRow(p.getActivity(), "<b>" + Ui.esc(p.title()) + "</b>" + world(p.getWorld())
            + "  " + p.memberCount() + "/" + p.getCapacity()));
        needsLine(card, p);
        if (p.getDescription() != null)
        {
            card.add(Ui.wrapped("\"" + p.getDescription() + "\"", Ui.MUTED));
        }
        List<Applicant> pending = p.pending();
        List<Applicant> accepted = p.accepted();
        if (!pending.isEmpty())
        {
            card.add(Ui.small("Applicants (" + pending.size() + ")", ColorScheme.BRAND_ORANGE));
            for (Applicant a : pending)
            {
                card.add(applicantRow(p, a, true));
            }
        }
        if (!accepted.isEmpty())
        {
            card.add(Ui.small("Members", ColorScheme.BRAND_ORANGE));
            for (Applicant a : accepted)
            {
                card.add(applicantRow(p, a, false));
            }
        }
        if (pending.isEmpty() && accepted.isEmpty())
        {
            card.add(Ui.small("No applicants yet.", Ui.MUTED));
        }
        if (!p.isFull())
        {
            card.add(addMemberRow(p));
        }
        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JButton edit = new JButton("Edit");
        edit.addActionListener(e -> toggleForm());
        JButton disband = new JButton("Disband");
        disband.addActionListener(e -> {
            board.expectSelfLeave(p.getId());
            run(() -> api.disband(p.getId()), "Couldn't disband — try again.");
        });
        buttons.add(edit);
        buttons.add(disband);
        card.add(Ui.pinHeight(buttons));
        return Ui.pinHeight(card);
    }

    private JPanel applicantRow(Party p, Applicant a, boolean pending)
    {
        JPanel row = Ui.row(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        boolean online = board.online().contains(Names.normalize(a.getRsn()));
        StringBuilder text = new StringBuilder("<font color='" + (online ? "#3FBF3F" : "#BF3F3F") + "'>")
            .append(Ui.esc(a.getRsn())).append("</font>");
        // Second line: role · learner · KC, kept off the name line so the
        // buttons never clip it.
        List<String> detail = new ArrayList<>();
        if (a.getRole() != null)
        {
            detail.add(Ui.esc(a.getRole().getDisplayName()));
        }
        if (a.isLearner())
        {
            detail.add("learner");
        }
        if (a.isAddedByHost())
        {
            detail.add("added by you");
        }
        else
        {
            String kc = applicantKc(p, a);
            if (kc != null)
            {
                detail.add(kc);
            }
        }
        if (!detail.isEmpty())
        {
            text.append("<br><font color='#A0A0A0'>").append(String.join(" · ", detail)).append("</font>");
        }
        row.add(Ui.html(text.toString()), BorderLayout.CENTER);
        // Pending rows stack Accept over Decline to keep the text column wide.
        JPanel buttons = new JPanel(new GridLayout(pending ? 2 : 1, 1, 0, 2));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        if (pending)
        {
            JButton accept = Ui.smallButton("Accept");
            accept.setEnabled(!p.isFull());
            accept.addActionListener(e -> run(() -> api.setStatus(p.getId(), a.getRsn(), Party.Status.ACCEPTED), "Couldn't accept — try again."));
            JButton decline = Ui.smallButton("Decline");
            decline.addActionListener(e -> run(() -> api.setStatus(p.getId(), a.getRsn(), Party.Status.DECLINED), "Couldn't decline — try again."));
            buttons.add(accept);
            buttons.add(decline);
        }
        else
        {
            JButton kick = Ui.smallButton("Kick");
            kick.addActionListener(e -> run(() -> api.withdraw(p.getId(), a.getRsn()), "Couldn't kick — try again."));
            buttons.add(kick);
        }
        row.add(buttons, BorderLayout.EAST);
        return Ui.pinHeight(row);
    }

    // Seat a buddy who isn't on LFG so the spot shows as taken to everyone.
    private Role addMemberRole;
    private JPanel addMemberRow(Party p)
    {
        JPanel row = Ui.column(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        row.add(Ui.small("Add a member who isn't on LFG", ColorScheme.BRAND_ORANGE));
        JTextField name = new JTextField(addMemberDraft);
        name.setToolTipText("Their RSN");
        Ui.capLength(name, 12);
        name.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { addMemberDraft = name.getText(); }
            public void removeUpdate(DocumentEvent e) { addMemberDraft = name.getText(); }
            public void changedUpdate(DocumentEvent e) { addMemberDraft = name.getText(); }
        });
        row.add(Ui.labeled("Name", name));
        JComboBox<Role> roleBox = null;
        if (p.getActivity().hasRoles())
        {
            List<Role> options = new ArrayList<>();
            for (Role r : p.openRoles())
            {
                if (!options.contains(r))
                {
                    options.add(r);
                }
            }
            if (options.isEmpty())
            {
                options = Role.playable(p.getActivity(), p.isHardMode());
            }
            roleBox = new JComboBox<>(options.toArray(new Role[0]));
            if (options.contains(addMemberRole)) roleBox.setSelectedItem(addMemberRole);
            JComboBox<Role> selected = roleBox;
            roleBox.addActionListener(e -> addMemberRole = (Role) selected.getSelectedItem());
            row.add(Ui.labeled("Role", roleBox));
        }
        JComboBox<Role> roleBoxF = roleBox;
        Runnable submit = () -> {
            String rsn = name.getText().trim();
            if (rsn.isEmpty())
            {
                showError("Type their name first.");
                return;
            }
            if (Names.same(rsn, p.getHostRsn()))
            {
                showError("That's you — you're already in.");
                return;
            }
            Role role = roleBoxF == null ? null : (Role) roleBoxF.getSelectedItem();
            board.run(() -> api.addMember(p.getId(), rsn, role) == PartyApi.AddResult.OK,
                "Couldn't add member. Refresh and check the name and available role.", message -> SwingUtilities.invokeLater(() -> {
                    if (message == null) addMemberDraft = "";
                    showError(message);
                }));
        };
        JButton add = new JButton("Add member");
        add.addActionListener(e -> submit.run());
        name.addActionListener(e -> submit.run());
        JPanel buttons = new JPanel(new GridLayout(1, 1));
        buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(add);
        row.add(Ui.pinHeight(buttons));
        return Ui.pinHeight(row);
    }

    // Read-only record of a party that filled up.
    private JPanel formedCard(FormedParty f)
    {
        String rsn = clan.rsn();
        JPanel card = Ui.card();
        card.add(titleRow(f.getActivity(), "<b>" + Ui.esc(f.title()) + "</b>" + world(f.getWorld())
            + "  " + f.getMembers().size() + "/" + f.getCapacity()));
        card.add(Ui.small("Formed " + Ui.timeAgo(f.getFormedAt()) + " · hosted by " + f.getHostRsn(), Ui.MUTED));
        card.add(Ui.wrapped(f.roster(), f.includes(rsn) ? Color.WHITE : Ui.MUTED));
        if (f.isHostedBy(rsn))
        {
            JPanel actions = actionRow();
            JButton remove = new JButton("Remove");
            remove.setToolTipText("Take this off the formed list now instead of in 7 days");
            remove.addActionListener(e -> run(() -> api.deleteFormed(f.getId()), "Couldn't remove — try again."));
            actions.add(remove);
            card.add(actions);
        }
        return Ui.pinHeight(card);
    }

    // ------------------------------------------------------------ pieces

    // What a host sees next to an applicant, in order of trust: the KC
    // their own client recorded, else a hiscore lookup, else what they
    // typed "(self)", else unknown. Null when the activity has no KC.
    private String applicantKc(Party p, Applicant a)
    {
        if (!p.getActivity().hasKillcount())
        {
            return null;
        }
        if (a.getKc() != null && a.getKcSource() == Party.KcSource.LOCAL)
        {
            return "KC " + a.getKc();
        }
        boolean pending = false;
        if (config.lfgKcLookups())
        {
            Killcounts.Hiscore h = killcounts.cached(a.getRsn(), p.getActivity());
            if (h == null)
            {
                killcounts.lookup(a.getRsn(), p.getActivity(), () -> SwingUtilities.invokeLater(this::render));
                pending = true;
            }
            else if (h.known(p.isHard()))
            {
                return "KC " + h.kc(p.isHard()) + " (hiscores)";
            }
        }
        if (a.getKc() != null)
        {
            return "KC " + a.getKc() + " (self)";
        }
        return pending ? "KC ..." : "KC ?";
    }

    private void needsLine(JPanel card, Party p)
    {
        if (p.getActivity().hasRoles())
        {
            String needs = p.isFull() ? "" : Role.summarize(p.openRoles());
            card.add(Ui.wrapped(p.isFull() ? "Full" : needs.isEmpty() ? "Roles: any" : "Needs: " + needs, Ui.MUTED));
        }
        else if (p.isFull())
        {
            card.add(Ui.small("Full", Ui.MUTED));
        }
    }

    private JPanel titleRow(Activity activity, String html)
    {
        JPanel row = Ui.row(ColorScheme.DARKER_GRAY_COLOR);
        row.add(Ui.icon(items, activity), BorderLayout.WEST);
        row.add(Ui.html(html), BorderLayout.CENTER);
        return Ui.pinHeight(row);
    }

    private static String world(Integer world)
    {
        return world == null ? "" : " <font color='#A0A0A0'>W" + world + "</font>";
    }

    private static JPanel actionRow()
    {
        JPanel actions = new JPanel(new GridLayout(1, 1, 5, 0));
        actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        return actions;
    }

    private void run(java.util.function.BooleanSupplier action, String failure)
    {
        board.run(action, failure, this::showError);
    }

    private void showError(String message)
    {
        SwingUtilities.invokeLater(() -> {
            error.setText(message == null ? "" : message);
            error.setVisible(message != null);
        });
    }
}
