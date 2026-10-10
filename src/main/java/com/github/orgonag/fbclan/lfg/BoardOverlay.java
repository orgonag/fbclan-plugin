package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.FinalBossPlugin;
import com.github.orgonag.fbclan.core.Clan;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;

/**
 * The clan logo and "2 ASAP, 6 total" on the game canvas while the LFG
 * board has posts; hovering it lists the board by activity. Reads only
 * the board's cached snapshot (client thread, every frame). Hidden when
 * the board is empty, stopped, or the setting is off.
 */
@Singleton
public class BoardOverlay extends Overlay
{
    // Theme accent-hi and sub; the ui package keeps its tokens private.
    private static final Color ACCENT_HI = new Color(0xFFB13B);
    private static final String SUB = "a5a5a5";

    private final Client client;
    private final FinalBossConfig config;
    private final PartyBoard board;
    private final Clan clan;
    private final TooltipManager tooltips;
    private final BufferedImage logo = ImageUtil.loadImageResource(FinalBossPlugin.class, "icon.png");
    private final PanelComponent panel = new PanelComponent();

    @Inject
    BoardOverlay(FinalBossPlugin plugin, Client client, FinalBossConfig config, PartyBoard board, Clan clan,
                 TooltipManager tooltips)
    {
        super(plugin);
        this.client = client;
        this.config = config;
        this.board = board;
        this.clan = clan;
        this.tooltips = tooltips;
        setPosition(OverlayPosition.TOP_LEFT);
        panel.setOrientation(ComponentOrientation.HORIZONTAL);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        List<Party> parties = board.parties();
        if (!config.lfgOverlay() || !board.running() || parties.isEmpty()) return null;
        int asap = 0;
        for (Party p : parties)
        {
            if (!p.isScheduled()) asap++;
        }
        panel.getChildren().clear();
        panel.getChildren().add(new ImageComponent(logo));
        panel.getChildren().add(TitleComponent.builder()
            .text(asap + " ASAP, " + parties.size() + " total")
            .color(ACCENT_HI)
            .build());
        Dimension size = panel.render(graphics);
        if (getBounds().contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY()))
        {
            tooltips.add(new Tooltip(breakdown(parties, clan.rsn())));
        }
        return size;
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
