package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.lfg.Party;
import com.github.orgonag.fbclan.lfg.PartyApi;
import com.github.orgonag.fbclan.lfg.PartyBoard;
import com.github.orgonag.fbclan.lfg.Role;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * The host's "add a member who isn't on LFG" row: seats a buddy so the
 * spot shows as taken to everyone. One row per hosted post, kept across
 * re-renders (the board refreshes every 30s) so a half-typed name
 * survives; only the role list is rebuilt when the open roles change.
 */
final class AddMemberForm
{
    private static final class Row
    {
        final JTextField name = Theme.field("", 12);
        String shape;
        JPanel panel;
    }

    private final PartyBoard board;
    private final PartyApi api;
    // Null = added; else the error to show.
    private final Consumer<String> onResult;
    private final Map<String, Row> rows = new HashMap<>();

    AddMemberForm(PartyBoard board, PartyApi api, Consumer<String> onResult)
    {
        this.board = board;
        this.api = api;
        this.onResult = onResult;
    }

    // EDT. Forget every row (logout).
    void reset()
    {
        rows.clear();
    }

    // EDT. Forget rows for posts that are gone.
    void keep(Set<String> partyIds)
    {
        rows.keySet().retainAll(partyIds);
    }

    // EDT.
    JPanel view(Party p)
    {
        Row row = rows.computeIfAbsent(p.getId(), id -> new Row());
        String shape = p.getActivity() + ":" + p.isHardMode() + ":" + p.openRoles();
        if (!shape.equals(row.shape))
        {
            row.shape = shape;
            row.panel = build(p, row.name);
        }
        return row.panel;
    }

    private JPanel build(Party p, JTextField name)
    {
        String partyId = p.getId();
        String host = p.getHostRsn();
        JPanel panel = Theme.stack(4);
        panel.add(Theme.bold("Add a member who isn't on LFG", Theme.ACCENT_HI));
        name.setToolTipText("Their RSN");
        panel.add(Theme.labeled("Name", name));
        JComboBox<Object> roleBox = null;
        if (p.getActivity().hasRoles())
        {
            List<Role> options = Role.applyOptions(p.getActivity(), p.isHardMode(), p.openRoles());
            if (options.isEmpty()) options = Role.playable(p.getActivity(), p.isHardMode());
            roleBox = LfgUi.combo(options.toArray());
            panel.add(Theme.labeled("Role", roleBox));
        }
        JComboBox<Object> roles = roleBox;
        Runnable submit = () -> {
            String rsn = name.getText().trim();
            if (rsn.isEmpty())
            {
                onResult.accept("Type their name first.");
                return;
            }
            if (Names.same(rsn, host))
            {
                onResult.accept("That's you — you're already in.");
                return;
            }
            Role role = roles == null ? null : (Role) roles.getSelectedItem();
            board.run(() -> api.addMember(partyId, rsn, role),
                "Couldn't add member. Refresh and check the name and available role.", message -> SwingUtilities.invokeLater(() -> {
                    if (message == null) name.setText("");
                    onResult.accept(message);
                }));
        };
        // A rebuilt row reuses the field: drop the old row's Enter handler.
        for (ActionListener old : name.getActionListeners()) name.removeActionListener(old);
        name.addActionListener(e -> submit.run());
        panel.add(Theme.button("Add member", Btn.Kind.GHOST, submit));
        return panel;
    }
}
