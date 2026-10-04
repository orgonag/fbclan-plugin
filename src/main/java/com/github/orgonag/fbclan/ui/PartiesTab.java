package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.lfg.Activity;
import com.github.orgonag.fbclan.lfg.FormedParty;
import com.github.orgonag.fbclan.lfg.Killcounts;
import com.github.orgonag.fbclan.lfg.LootRule;
import com.github.orgonag.fbclan.lfg.Party.Applicant;
import com.github.orgonag.fbclan.lfg.Party;
import com.github.orgonag.fbclan.lfg.PartyApi;
import com.github.orgonag.fbclan.lfg.PartyBoard;
import com.github.orgonag.fbclan.lfg.Role;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;

/**
 * The LFG tab: host a party (via {@link HostWizard}), browse and apply,
 * manage applicants, and see recently formed parties. Renders whatever
 * PartyBoard holds; every write goes through the board (executor) and
 * re-renders on the EDT.
 */
@Singleton
public class PartiesTab extends Tab
{
    private static final Color OFFLINE = new Color(0xBF3F3F);
    // Scheduled posts a host may keep at once (the server enforces it too).
    private static final int MAX_SCHEDULED = 7;

    private final Clan clan;
    private final FinalBossConfig config;
    private final PartyBoard board;
    private final PartyApi api;
    private final Killcounts killcounts;
    private final ItemManager items;
    private final Btn hostButton;
    private HostWizard wizard;
    private String actionError;
    private String applyingId;
    private Object filter;
    private boolean hideFull;
    private boolean showFormed = true;
    private String addMemberDraft = "";
    private Role addMemberRole;
    private final ApplyForm applyForm;

    @Inject
    public PartiesTab(Clan clan, FinalBossConfig config, PartyBoard board, PartyApi api, Killcounts killcounts,
                      ItemManager items, ScheduledExecutorService executor)
    {
        super("Looking for Group", executor);
        this.clan = clan;
        this.config = config;
        this.board = board;
        this.api = api;
        this.killcounts = killcounts;
        this.items = items;
        applyForm = new ApplyForm(clan, config, killcounts, board, api, this::render, message -> {
            if (message == null) applyingId = null;
            setActionError(message);
        }, () -> {
            applyingId = null;
            render();
        });

        hostButton = Theme.button("Host a party", Btn.Kind.PRIMARY, () -> openWizard(null));
        control(hostButton);
        List<Object> filters = new ArrayList<>();
        filters.add("All activities");
        Collections.addAll(filters, Activity.Category.values());
        Collections.addAll(filters, Activity.values());
        JComboBox<Object> filterBox = LfgUi.combo(filters.toArray());
        filterBox.addActionListener(e -> {
            Object sel = filterBox.getSelectedItem();
            filter = sel instanceof String ? null : sel;
            render();
        });
        control(filterBox);
        Btn formedChip = toggle("Formed (7d)", showFormed, on -> showFormed = on);
        formedChip.setToolTipText("Parties that filled up in the last 7 days");
        control(LfgUi.pair(toggle("Hide full", hideFull, on -> hideFull = on), formedChip));

        board.setListener(() -> SwingUtilities.invokeLater(this::render));
        render();
    }

    @Override
    public void refresh()
    {
        executor.submit(board::refresh);
    }

    // Logout / config change / shutdown: drop transient UI state. The
    // wizard is left alone here (a settings change must not eat a
    // half-filled form); closeWizard() handles logout and shutdown.
    public void reset()
    {
        SwingUtilities.invokeLater(() -> {
            applyingId = null;
            applyForm.reset();
            addMemberDraft = "";
            actionError = null;
            render();
        });
    }

    public void closeWizard()
    {
        if (wizard != null) wizard.close();
    }

    // ------------------------------------------------------------ host wizard

    // `target` = the post to edit, or null to host a new one.
    private void openWizard(Party target)
    {
        String id = target == null ? null : target.getId();
        if (wizard != null && wizard.isOpen())
        {
            if (Objects.equals(wizard.editingId(), id))
            {
                wizard.focus();
                return;
            }
            // A window open for a different post (or for a new one) would
            // save the wrong thing; start over for this one.
            wizard.close();
        }
        wizard = new HostWizard(SwingUtilities.getWindowAncestor(this), killcounts, target, limits(target),
            p -> partyCard(p, false), this::submit);
        wizard.setBusy(board.isBusy());
        wizard.setWorld(board.world());
    }

    // Which start-time choices are open, not counting the post being edited.
    private WhenPicker.Limits limits(Party editing)
    {
        Party asap = board.mineAsap();
        boolean asapTaken = asap != null && (editing == null || !asap.getId().equals(editing.getId()));
        int scheduled = board.mineScheduled() - (editing != null && editing.isScheduled() ? 1 : 0);
        return new WhenPicker.Limits(asapTaken, scheduled >= MAX_SCHEDULED);
    }

    private Party find(String id)
    {
        for (Party p : board.parties())
        {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    // From the wizard's Post/Save. Result: null closes it, else the error shows in it.
    private void submit(HostWizard w)
    {
        String rsn = clan.rsn();
        if (rsn == null)
        {
            w.showError("Log in to host a party.");
            return;
        }
        try
        {
            Party draft = w.build(rsn, board.world());
            Party existing = w.editingId() == null ? null : find(w.editingId());
            if (w.editingId() != null && existing == null)
            {
                w.showError("This post is no longer listed. Close this and host a new one.");
                return;
            }
            Party party = existing == null ? draft : draft.toBuilder().id(existing.getId()).version(existing.getVersion()).build();
            board.run(() -> api.save(party), "Couldn't save your party. Refresh and try again.", message -> SwingUtilities.invokeLater(() -> {
                if (message == null) w.close();
                else w.showError(message);
                render();
            }));
        }
        catch (IllegalArgumentException e)
        {
            w.showError(e.getMessage());
        }
    }

    // ------------------------------------------------------------ render

    private void render()
    {
        String rsn = clan.rsn();
        List<Party> mine = board.mine();
        showError(actionError != null ? actionError : board.refreshError());
        note.setText(board.online().isEmpty() ? "" : board.online().size() + " in clan chat");
        // Host stays available until both the ASAP slot and all scheduled slots are used.
        boolean full = board.mineAsap() != null && board.mineScheduled() >= MAX_SCHEDULED;
        hostButton.setEnabled(!board.isBusy() && clan.canUpload() && config.enableLfg() && !full);
        hostButton.setToolTipText(full ? "Post limit reached (1 ASAP + " + MAX_SCHEDULED + " scheduled)" : null);
        if (wizard != null && wizard.isOpen())
        {
            wizard.setBusy(board.isBusy());
            wizard.setWorld(board.world());
        }

        List<Party> others = new ArrayList<>();
        for (Party p : board.parties())
        {
            boolean hidden = hideFull && p.isFull() && p.applicantFor(rsn) == null;
            if (!p.isHostedBy(rsn) && passes(p.getActivity()) && !hidden) others.add(p);
        }
        // Parties the player is in (or waiting on) float to the top; then ASAP, then soonest.
        others.sort((a, b) -> {
            boolean ia = a.applicantFor(rsn) != null;
            boolean ib = b.applicantFor(rsn) != null;
            return ia != ib ? (ia ? -1 : 1) : PartyBoard.ORDER.compare(a, b);
        });
        fill(() -> {
            if (!mine.isEmpty())
            {
                list.add(Theme.caps("Your posts (" + mine.size() + ")"));
                mine.forEach(p -> list.add(hostCard(p)));
                list.add(Theme.caps("Open parties (" + others.size() + ")"));
            }
            if (others.isEmpty())
            {
                list.add(Theme.centered(board.parties().isEmpty() ? "No parties are being hosted right now." : "No parties match your filters."));
            }
            for (Party p : others)
            {
                list.add(partyCard(p, true));
            }
            if (showFormed)
            {
                List<FormedParty> formed = new ArrayList<>();
                for (FormedParty f : board.formed())
                {
                    if (passes(f.getActivity())) formed.add(f);
                }
                list.add(Theme.caps("Formed parties (" + formed.size() + ")"));
                if (formed.isEmpty()) list.add(Theme.centered("No parties have formed in the last 7 days."));
                for (FormedParty f : formed)
                {
                    list.add(formedCard(f));
                }
            }
        });
    }

    private boolean passes(Activity a)
    {
        return filter == null || filter == a || filter == a.getCategory();
    }

    // ------------------------------------------------------------ cards

    // Board card, or (interactive = false) the wizard's preview of a draft.
    JComponent partyCard(Party p, boolean interactive)
    {
        String rsn = clan.rsn();
        Card card = Theme.card(null);
        boolean hostOnline = board.online().contains(Names.normalize(p.getHostRsn()));
        String asap = p.isScheduled() ? "" : " · ASAP";
        JLabel host = Theme.text(p.getHostRsn() + asap + " · " + Theme.timeAgo(p.getCreatedAt()), hostOnline ? Theme.GREEN : OFFLINE);
        card.add(head(p.getActivity(), p.title(), p.getWorld(), host, p.memberCount() + "/" + p.getCapacity()));
        JLabel start = LfgUi.start(p);
        if (start != null) card.add(start);
        card.add(Theme.seats(p.memberCount(), p.getCapacity()));
        List<String> meta = new ArrayList<>();
        if (p.getLootRule() != LootRule.UNSPECIFIED) meta.add(p.getLootRule().getDisplayName());
        if (p.getMinKc() > 0) meta.add(p.getMinKc() + "+ KC");
        if (p.isLearner()) meta.add("Learner");
        if (p.isTeacher()) meta.add("Teacher");
        if (!meta.isEmpty()) card.add(Theme.text(String.join(" · ", meta), Theme.SOFT));
        body(card, p);
        List<Applicant> accepted = p.accepted();
        if (!accepted.isEmpty())
        {
            List<String> names = new ArrayList<>();
            accepted.forEach(a -> names.add(a.getRsn()));
            card.add(Theme.wrap("With: " + String.join(", ", names), Theme.SUB));
        }
        if (!interactive) return card;

        Applicant mine = p.applicantFor(rsn);
        if (mine != null)
        {
            String state = mine.isAccepted() ? "Accepted" : mine.isPending() ? "Pending" : "Declined";
            Btn leave = Theme.button(mine.isAccepted() ? "Leave party" : mine.isPending() ? "Withdraw" : "Dismiss", Btn.Kind.GHOST, () -> {
                board.expectSelfLeave(p.getId());
                run(() -> api.withdraw(p.getId(), rsn), "Couldn't withdraw — try again.");
            });
            card.add(Theme.row(null, Theme.bold(state, mine.isAccepted() ? Theme.GREEN : Theme.SUB), leave));
        }
        else if (p.getId().equals(applyingId))
        {
            card.add(applyForm.view(p));
        }
        else
        {
            Btn apply = Theme.button("Apply", Btn.Kind.PRIMARY, () -> {
                applyingId = p.getId();
                render();
            });
            if (rsn == null) apply.setEnabled(false);
            else if (p.isFull()) LfgUi.disable(apply, "Full");
            else if (p.getActivity().hasRoles() && p.openRoles().isEmpty()) LfgUi.disable(apply, "No open roles");
            card.add(apply);
        }
        return card;
    }

    private JPanel hostCard(Party p)
    {
        Card card = Theme.card(Theme.ACCENT);
        JLabel asap = p.isScheduled() ? null : Theme.text("ASAP · " + Theme.timeAgo(p.getCreatedAt()), Theme.SUB);
        card.add(head(p.getActivity(), p.title(), p.getWorld(), asap, p.memberCount() + "/" + p.getCapacity()));
        JLabel start = LfgUi.start(p);
        if (start != null) card.add(start);
        card.add(Theme.seats(p.memberCount(), p.getCapacity()));
        body(card, p);
        List<Applicant> pending = p.pending();
        List<Applicant> accepted = p.accepted();
        if (!pending.isEmpty())
        {
            card.add(Theme.bold("Applicants (" + pending.size() + ")", Theme.ACCENT_HI));
            pending.forEach(a -> card.add(applicantRow(p, a, true)));
        }
        if (!accepted.isEmpty())
        {
            card.add(Theme.bold("Members", Theme.ACCENT_HI));
            accepted.forEach(a -> card.add(applicantRow(p, a, false)));
        }
        if (pending.isEmpty() && accepted.isEmpty()) card.add(Theme.text("No applicants yet.", Theme.SUB));
        if (!p.isFull()) card.add(addMemberRow(p));
        Btn edit = Theme.button("Edit", Btn.Kind.GHOST, () -> openWizard(p));
        edit.setEnabled(!board.isBusy() && clan.canUpload() && config.enableLfg());
        card.add(LfgUi.pair(edit,
            Theme.button("Cancel post", Btn.Kind.DANGER, () -> {
                board.expectSelfLeave(p.getId());
                run(() -> api.disband(p.getId()), "Couldn't cancel — try again.");
            })));
        return card;
    }

    private JPanel formedCard(FormedParty f)
    {
        String rsn = clan.rsn();
        Card card = Theme.card(null);
        JLabel when = Theme.text("Formed " + Theme.timeAgo(f.getFormedAt()) + " · hosted by " + f.getHostRsn()
            + (f.getScheduledFor() == null ? "" : " · for " + LfgUi.day(f.getScheduledFor())), Theme.SUB);
        card.add(head(f.getActivity(), f.title(), f.getWorld(), when, f.getMembers().size() + "/" + f.getCapacity()));
        card.add(Theme.wrap(f.roster(), f.includes(rsn) ? Theme.TEXT : Theme.SUB));
        if (f.isHostedBy(rsn))
        {
            Btn remove = Theme.button("Remove", Btn.Kind.GHOST, () -> run(() -> api.deleteFormed(f.getId()), "Couldn't remove — try again."));
            remove.setToolTipText("Take this off the formed list now instead of in 7 days");
            card.add(remove);
        }
        return card;
    }

    // Sprite tile, title (+ world), an optional second line, and the head count.
    private JPanel head(Activity activity, String title, Integer world, JLabel second, String count)
    {
        JPanel text = Theme.stack(1);
        JLabel name = Theme.bold(title, Theme.TEXT);
        text.add(Theme.row(name, world == null ? null : Theme.text("W" + world, Theme.SUB), null));
        if (second != null) text.add(second);
        return Theme.row(Theme.tile(items, activity.getIconItemId(), 1, LfgUi.tier(activity), 30), text, Theme.bold(count, Theme.GOLD));
    }

    // Needs line and quoted description.
    private void body(JPanel card, Party p)
    {
        if (p.getActivity().hasRoles())
        {
            String needs = p.isFull() ? "" : Role.summarize(p.openRoles());
            card.add(Theme.wrap(p.isFull() ? "Full" : needs.isEmpty() ? "Roles: any" : "Needs: " + needs, Theme.SUB));
        }
        else if (p.isFull())
        {
            card.add(Theme.text("Full", Theme.SUB));
        }
        if (p.getDescription() != null && !p.getDescription().trim().isEmpty()) card.add(Theme.wrap("\"" + p.getDescription() + "\"", Theme.SUB));
    }

    private JPanel applicantRow(Party p, Applicant a, boolean pending)
    {
        boolean online = board.online().contains(Names.normalize(a.getRsn()));
        List<String> detail = new ArrayList<>();
        if (a.getRole() != null) detail.add(a.getRole().getDisplayName());
        if (a.isLearner()) detail.add("learner");
        if (a.isAddedByHost()) detail.add("added by you");
        else
        {
            String kc = applicantKc(p, a);
            if (kc != null) detail.add(kc);
        }
        JPanel who = Theme.stack(0);
        who.add(Theme.text(a.getRsn(), online ? Theme.GREEN : OFFLINE));
        if (!detail.isEmpty()) who.add(Theme.text(String.join(" · ", detail), Theme.SUB));
        JPanel buttons = Theme.stack(2);
        if (pending)
        {
            Btn accept = LfgUi.small(Theme.button("Accept", Btn.Kind.PRIMARY,
                () -> run(() -> api.setStatus(p.getId(), a.getRsn(), Party.Status.ACCEPTED), "Couldn't accept — try again.")));
            if (p.isFull())
            {
                accept.setEnabled(false);
                accept.setToolTipText("Party is full");
            }
            buttons.add(accept);
            buttons.add(LfgUi.small(Theme.button("Decline", Btn.Kind.GHOST,
                () -> run(() -> api.setStatus(p.getId(), a.getRsn(), Party.Status.DECLINED), "Couldn't decline — try again."))));
        }
        else
        {
            buttons.add(LfgUi.small(Theme.button("Kick", Btn.Kind.DANGER,
                () -> run(() -> api.withdraw(p.getId(), a.getRsn()), "Couldn't kick — try again."))));
        }
        return Theme.row(null, who, buttons);
    }

    // Seat a buddy who isn't on LFG so the spot shows as taken to everyone.
    private JPanel addMemberRow(Party p)
    {
        JPanel row = Theme.stack(4);
        row.add(Theme.bold("Add a member who isn't on LFG", Theme.ACCENT_HI));
        JTextField name = Theme.field(addMemberDraft, 12);
        name.setToolTipText("Their RSN");
        name.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { addMemberDraft = name.getText(); }
            public void removeUpdate(DocumentEvent e) { addMemberDraft = name.getText(); }
            public void changedUpdate(DocumentEvent e) { addMemberDraft = name.getText(); }
        });
        row.add(Theme.labeled("Name", name));
        JComboBox<Object> roleBox = null;
        if (p.getActivity().hasRoles())
        {
            List<Role> options = new ArrayList<>();
            for (Role r : p.openRoles())
            {
                if (!options.contains(r)) options.add(r);
            }
            if (options.isEmpty()) options = Role.playable(p.getActivity(), p.isHardMode());
            roleBox = LfgUi.combo(options.toArray());
            if (options.contains(addMemberRole)) roleBox.setSelectedItem(addMemberRole);
            JComboBox<Object> box = roleBox;
            roleBox.addActionListener(e -> addMemberRole = (Role) box.getSelectedItem());
            row.add(Theme.labeled("Role", roleBox));
        }
        JComboBox<Object> roleBoxF = roleBox;
        Runnable submit = () -> {
            String rsn = name.getText().trim();
            if (rsn.isEmpty())
            {
                setActionError("Type their name first.");
                return;
            }
            if (Names.same(rsn, p.getHostRsn()))
            {
                setActionError("That's you — you're already in.");
                return;
            }
            Role role = roleBoxF == null ? null : (Role) roleBoxF.getSelectedItem();
            board.run(() -> api.addMember(p.getId(), rsn, role),
                "Couldn't add member. Refresh and check the name and available role.", message -> SwingUtilities.invokeLater(() -> {
                    if (message == null) addMemberDraft = "";
                    setActionError(message);
                }));
        };
        name.addActionListener(e -> submit.run());
        row.add(Theme.button("Add member", Btn.Kind.GHOST, submit));
        return row;
    }

    // What a host sees next to an applicant, in order of trust: the KC
    // their own client recorded, else a hiscore lookup, else what they
    // typed "(self)", else unknown. Null when the activity has no KC.
    private String applicantKc(Party p, Applicant a)
    {
        if (!p.getActivity().hasKillcount()) return null;
        if (a.getKc() != null && a.getKcSource() == Party.KcSource.LOCAL) return "KC " + a.getKc();
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
        if (a.getKc() != null) return "KC " + a.getKc() + " (self)";
        return pending ? "KC ..." : "KC ?";
    }

    // ------------------------------------------------------------ helpers

    private void run(BooleanSupplier action, String failure)
    {
        board.run(action, failure, message -> SwingUtilities.invokeLater(() -> setActionError(message)));
    }

    // An on/off chip that re-renders the list when flipped.
    private Btn toggle(String label, boolean initial, Consumer<Boolean> onChange)
    {
        boolean[] on = {initial};
        Btn[] chip = new Btn[1];
        chip[0] = Theme.button(label, Btn.Kind.CHIP, () -> {
            on[0] = !on[0];
            chip[0].setOn(on[0]);
            onChange.accept(on[0]);
            render();
        });
        chip[0].setOn(initial);
        return chip[0];
    }

    // EDT. The last action's failure wins over the board's refresh error; null clears it.
    private void setActionError(String message)
    {
        actionError = message;
        render();
    }

}
