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
 * re-renders (the board refreshes every 30s) so typed values survive, and
 * rebuilt when the party's shape or the KC prefill changes.
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
        panel = build(p, rsn, prefill, source);
        return panel;
    }

    private JPanel build(Party p, String rsn, Integer prefill, Party.KcSource source)
    {
        JPanel row = Theme.stack(4);
        JComboBox<Object> roleBox = null;
        if (p.getActivity().hasRoles())
        {
            roleBox = LfgUi.combo(Role.applyOptions(p.getActivity(), p.isHardMode(), p.openRoles()).toArray());
            row.add(Theme.labeled("Role", roleBox));
        }
        JCheckBox learner = null;
        if (p.getActivity().isRaid())
        {
            learner = LfgUi.checkbox("I'm a learner");
            row.add(learner);
        }
        JSpinner kcSpinner = null;
        if (p.getActivity().hasKillcount())
        {
            kcSpinner = new JSpinner(new SpinnerNumberModel(prefill == null ? 0 : prefill, 0, 100_000, 1));
            row.add(Theme.labeled(source == Party.KcSource.LOCAL ? "Your KC (auto)"
                : source == Party.KcSource.HISCORES ? "Your KC (hiscores)" : "Your KC", kcSpinner));
            if (p.getMinKc() > 0)
            {
                boolean below = prefill != null && prefill < p.getMinKc();
                row.add(Theme.text("Host asks for " + p.getMinKc() + "+ KC" + (below ? " - you're below it" : ""), below ? Theme.ACCENT_HI : Theme.SUB));
            }
        }
        JComboBox<Object> roles = roleBox;
        JCheckBox learnerBox = learner;
        JSpinner kcBox = kcSpinner;
        Btn confirm = Theme.button("Confirm", Btn.Kind.PRIMARY, () -> {
            if (rsn == null) return;
            Role role = roles == null ? null : (Role) roles.getSelectedItem();
            boolean isLearner = learnerBox != null && learnerBox.isSelected();
            Integer kc = null;
            Party.KcSource kcSource = null;
            if (kcBox != null)
            {
                try
                {
                    kcBox.commitEdit();
                }
                catch (ParseException ex)
                {
                    onResult.accept("Enter a valid kill count.");
                    return;
                }
                int typed = (Integer) kcBox.getValue();
                // Unchanged keeps where it came from; anything else typed is self-reported.
                if (prefill != null && typed == prefill)
                {
                    kc = typed;
                    kcSource = source;
                }
                else if (typed > 0)
                {
                    kc = typed;
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
