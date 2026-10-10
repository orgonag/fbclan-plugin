package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.core.Clan;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;

/**
 * The clan logo in RuneLite's infobox row while the LFG board has posts,
 * captioned "ASAP/total"; hovering it lists the board by activity. Size,
 * position and the tooltip are RuneLite's (Overlay settings > Infobox size).
 * Reads only the board's cached snapshot (client thread, every frame).
 */
public class BoardInfoBox extends InfoBox
{
    // Theme accent-hi and sub; the ui package keeps its tokens private.
    private static final Color ACCENT_HI = new Color(0xFFB13B);
    private static final String SUB = "a5a5a5";

    private final FinalBossConfig config;
    private final PartyBoard board;
    private final Clan clan;

    public BoardInfoBox(BufferedImage logo, Plugin plugin, FinalBossConfig config, PartyBoard board, Clan clan)
    {
        super(logo, plugin);
        this.config = config;
        this.board = board;
        this.clan = clan;
        setPriority(InfoBoxPriority.LOW);
    }

    @Override
    public String getName()
    {
        return "FinalBossBoard";
    }

    @Override
    public boolean render()
    {
        return config.lfgInfoBox() && board.running() && !board.parties().isEmpty();
    }

    // "2/6": ASAP posts over every post.
    @Override
    public String getText()
    {
        List<Party> parties = board.parties();
        int asap = 0;
        for (Party p : parties)
        {
            if (!p.isScheduled()) asap++;
        }
        return asap + "/" + parties.size();
    }

    @Override
    public Color getTextColor()
    {
        return ACCENT_HI;
    }

    @Override
    public String getTooltip()
    {
        return breakdown(board.parties(), clan.rsn());
    }

    // Activity names come from the enum and times from the clock: no
    // remote text reaches the tooltip, which renders colour tags.
    static String breakdown(List<Party> parties, String rsn)
    {
        Map<String, Integer> asap = new LinkedHashMap<>();
        List<Party> scheduled = new ArrayList<>();
        int hosting = 0;
        int in = 0;
        for (Party p : parties)
        {
            if (p.isScheduled()) scheduled.add(p);
            else asap.merge(p.title(), 1, Integer::sum);
            Party.Applicant mine = p.applicantFor(rsn);
            if (p.isHostedBy(rsn)) hosting++;
            else if (mine != null && mine.isAccepted()) in++;
        }
        List<String> lines = new ArrayList<>();
        lines.add("LFG board: " + (parties.size() - scheduled.size()) + " ASAP, " + parties.size() + " total");
        if (!asap.isEmpty())
        {
            List<String> parts = new ArrayList<>();
            asap.forEach((title, n) -> parts.add(title + (n > 1 ? " x" + n : "")));
            lines.add("ASAP: " + String.join(", ", parts));
        }
        if (!scheduled.isEmpty())
        {
            scheduled.sort(PartyBoard.ORDER);
            Party next = null;
            for (Party p : scheduled)
            {
                if (p.getScheduledFor().isAfter(Instant.now()))
                {
                    next = p;
                    break;
                }
            }
            lines.add("Scheduled: " + scheduled.size()
                + (next == null ? "" : ", next " + PartyBoard.when(next.getScheduledFor()) + " " + next.title()));
        }
        if (hosting > 0 || in > 0)
        {
            lines.add("<col=" + SUB + ">You: hosting " + hosting + ", in " + in + "</col>");
        }
        return String.join("</br>", lines);
    }
}
