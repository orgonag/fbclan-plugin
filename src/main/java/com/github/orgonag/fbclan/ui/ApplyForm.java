package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.lfg.Killcounts;
import com.github.orgonag.fbclan.lfg.Party;
import com.github.orgonag.fbclan.lfg.PartyApi;
import com.github.orgonag.fbclan.lfg.PartyBoard;
import com.github.orgonag.fbclan.lfg.Role;
import java.text.ParseException;
import java.util.function.Consumer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * The inline form under a party's card when the player applies: role,
 * learner, and KC. Your KC comes from your own client's record first, else
 * a hiscore lookup, else blank to type; editing it marks it self-reported.
 * The host sees it; it never blocks applying. The panel is kept across
 * re-renders (the board refreshes every 30s), and when it has to be
 * rebuilt (open roles changed, a KC lookup came back) what the player
 * already picked or typed is carried over.
 */
final class ApplyForm
{
    private final Clan clan;
    private final FinalBossConfig config;
    private final Killcounts killcounts;
    private final PartyBoard board;
    private final PartyApi api;
    private final Runnable render;
    // Null = applied (close the form); else the error to show.
    private final Consumer<String> onResult;
    private final Runnable onCancel;
    private JPanel panel;
    private String shape;
    // The current form's inputs (null where the activity has none), and the KC it was prefilled with.
    private String partyId;
    private JComboBox<Object> roleBox;
    private JCheckBox learnerBox;
    private JSpinner kcBox;
    private Integer kcPrefill;

    ApplyForm(Clan clan, FinalBossConfig config, Killcounts killcounts, PartyBoard board, PartyApi api,
              Runnable render, Consumer<String> onResult, Runnable onCancel)
    {
        this.clan = clan;
        this.config = config;
        this.killcounts = killcounts;
        this.board = board;
        this.api = api;
        this.render = render;
        this.onResult = onResult;
        this.onCancel = onCancel;
    }

    // EDT. Forget the cached form (logout).
    void reset()
    {
        panel = null;
        shape = null;
        partyId = null;
    }

    // EDT.
    JPanel view(Party p)
    {
        String rsn = clan.rsn();
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
                    killcounts.lookup(rsn, p.getActivity(), () -> SwingUtilities.invokeLater(render));
                }
                else if (h.known(p.isHard()))
                {
                    prefill = h.kc(p.isHard());
                    source = Party.KcSource.HISCORES;
                }
            }
        }
        String now = p.getId() + ":" + p.getActivity() + ":" + p.isHardMode() + ":" + p.openRoles() + ":" + p.getMinKc() + ":" + prefill + ":" + source;
        if (now.equals(shape) && panel != null) return panel;
        shape = now;
        // Same party, rebuilt: keep the role and learner picks, and a KC the player typed.
        boolean same = panel != null && p.getId().equals(partyId);
        Object role = same && roleBox != null ? roleBox.getSelectedItem() : null;
        boolean learner = same && learnerBox != null && learnerBox.isSelected();
        Integer typed = same ? typedKc() : null;
        partyId = p.getId();
        kcPrefill = prefill;
        panel = build(p, rsn, prefill, source, role, learner, typed);
        return panel;
    }

    // The KC in the box if the player changed it (including text still being typed), else null.
    private Integer typedKc()
    {
        if (kcBox == null) return null;
        try
        {
            kcBox.commitEdit();
        }
        catch (ParseException e)
        {
            return null;
        }
        Integer value = (Integer) kcBox.getValue();
        return value.equals(kcPrefill == null ? 0 : kcPrefill) ? null : value;
    }

    private JPanel build(Party p, String rsn, Integer prefill, Party.KcSource source, Object picked, boolean learner, Integer typed)
    {
        JPanel row = Theme.stack(4);
        roleBox = null;
        if (p.getActivity().hasRoles())
        {
            roleBox = LfgUi.combo(Role.applyOptions(p.getActivity(), p.isHardMode(), p.openRoles()).toArray());
            if (picked != null) roleBox.setSelectedItem(picked); // ignored when that role is no longer open
            row.add(Theme.labeled("Role", roleBox));
        }
        learnerBox = null;
        if (p.getActivity().isRaid())
        {
            learnerBox = LfgUi.checkbox("I'm a learner");
            learnerBox.setSelected(learner);
            row.add(learnerBox);
        }
        kcBox = null;
        if (p.getActivity().hasKillcount())
        {
            kcBox = new JSpinner(new SpinnerNumberModel(typed != null ? (int) typed : prefill == null ? 0 : prefill, 0, 100_000, 1));
            row.add(Theme.labeled(source == Party.KcSource.LOCAL ? "Your KC (auto)"
                : source == Party.KcSource.HISCORES ? "Your KC (hiscores)" : "Your KC", kcBox));
            if (p.getMinKc() > 0)
            {
                boolean below = prefill != null && prefill < p.getMinKc();
                row.add(Theme.text("Host asks for " + p.getMinKc() + "+ KC" + (below ? " - you're below it" : ""), below ? Theme.ACCENT_HI : Theme.SUB));
            }
        }
        JComboBox<Object> roles = roleBox;
        JCheckBox learners = learnerBox;
        JSpinner kcs = kcBox;
        Btn confirm = Theme.button("Confirm", Btn.Kind.PRIMARY, () -> {
            if (rsn == null) return;
            Role role = roles == null ? null : (Role) roles.getSelectedItem();
            boolean isLearner = learners != null && learners.isSelected();
            Integer kc = null;
            Party.KcSource kcSource = null;
            if (kcs != null)
            {
                try
                {
                    kcs.commitEdit();
                }
                catch (ParseException ex)
                {
                    onResult.accept("Enter a valid kill count.");
                    return;
                }
                int entered = (Integer) kcs.getValue();
                // Unchanged keeps where it came from; anything else typed is self-reported.
                if (prefill != null && entered == prefill)
                {
                    kc = entered;
                    kcSource = source;
                }
                else if (entered > 0)
                {
                    kc = entered;
                    kcSource = Party.KcSource.MANUAL;
                }
            }
            Integer kcF = kc;
            Party.KcSource kcSourceF = kcSource;
            board.run(() -> api.apply(p.getId(), role, isLearner, kcF, kcSourceF), "Couldn't apply. Refresh and try again.",
                message -> SwingUtilities.invokeLater(() -> onResult.accept(message)));
        });
        row.add(LfgUi.pair(confirm, Theme.button("Cancel", Btn.Kind.GHOST, onCancel)));
        return row;
    }
}
