package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.lfg.FormedParty;
import com.github.orgonag.fbclan.lfg.PartyApi;
import com.github.orgonag.fbclan.lfg.PartyBoard;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;

/**
 * A card on the Formed list: one row per member. The host can kick anyone
 * else and a member can leave; either way the server puts the post back on
 * the board with the rest still seated. The host can also hide the card.
 */
final class FormedCard
{
    private final ItemManager items;
    private final PartyBoard board;
    private final PartyApi api;
    // PartiesTab.run: the write, and the line shown when it fails.
    private final BiConsumer<Function<Session, String>, String> run;

    FormedCard(ItemManager items, PartyBoard board, PartyApi api, BiConsumer<Function<Session, String>, String> run)
    {
        this.items = items;
        this.board = board;
        this.api = api;
        this.run = run;
    }

    JPanel view(FormedParty f, String rsn)
    {
        boolean host = f.isHostedBy(rsn);
        Card card = Theme.card(null);
        JLabel when = Theme.text("Formed " + Theme.timeAgo(f.getFormedAt()) + " · hosted by " + f.getHostRsn()
            + (f.getScheduledFor() == null ? "" : " · for " + LfgUi.day(f.getScheduledFor())), Theme.SUB);
        card.add(LfgUi.head(items, f.getActivity(), f.title(), f.getWorld(), when, f.getMembers().size() + "/" + f.getCapacity()));
        for (FormedParty.Member m : f.getMembers())
        {
            boolean me = Names.same(m.getRsn(), rsn);
            JLabel who = Theme.text(m.getRsn() + (m.getRole() == null ? "" : " (" + m.getRole().getDisplayName() + ")"), me ? Theme.TEXT : Theme.SUB);
            Btn action = null;
            if (host && !f.isHostedBy(m.getRsn()))
            {
                action = LfgUi.small(Theme.button("Kick", Btn.Kind.DANGER,
                    () -> run.accept(s -> board.kickFormed(s, f, m.getRsn()), "Couldn't kick — try again.")));
                action.setToolTipText("Puts the party back on the board without them");
            }
            else if (me && !host)
            {
                action = LfgUi.small(Theme.button("Leave party", Btn.Kind.GHOST,
                    () -> run.accept(s -> board.leaveFormed(s, f), "Couldn't leave — try again.")));
                action.setToolTipText("Puts the party back on the board without you");
            }
            card.add(Theme.row(null, who, action));
        }
        if (host)
        {
            Btn remove = Theme.button("Remove", Btn.Kind.GHOST, () -> run.accept(s -> api.deleteFormed(s, f.getId()), "Couldn't remove — try again."));
            remove.setToolTipText("Hide this from the formed list now (the record is kept)");
            card.add(remove);
        }
        return card;
    }
}
