package com.github.orgonag.fbclan.lfg;

import com.github.orgonag.fbclan.FinalBossConfig;
import com.github.orgonag.fbclan.FinalBossPlugin;
import com.github.orgonag.fbclan.core.Clan;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.TextComponent;
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
    private final BackgroundComponent background = new BackgroundComponent();
    private final TextComponent text = new TextComponent();

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
        text.setColor(ACCENT_HI);
    }

    // The saved position is keyed on this; a plain class name could clash
    // with another plugin's overlay.
    @Override
    public String getName()
    {
        return "FinalBossBoardOverlay";
    }

    // Drawn by hand: a horizontal PanelComponent gives its children no
    // width, so a logo-and-text row can't use it.
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
        String line = asap + " ASAP, " + parties.size() + " total";
        FontMetrics fm = graphics.getFontMetrics();
        int pad = ComponentConstants.STANDARD_BORDER;
        int width = pad + logo.getWidth() + pad + fm.stringWidth(line) + pad;
        int height = pad + Math.max(logo.getHeight(), fm.getHeight()) + pad;
        background.setRectangle(new Rectangle(0, 0, width, height));
        background.render(graphics);
        graphics.drawImage(logo, pad, (height - logo.getHeight()) / 2, null);
        text.setText(line);
        text.setPosition(pad + logo.getWidth() + pad, (height - fm.getHeight()) / 2 + fm.getAscent());
        text.render(graphics);
        if (getBounds().contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY()))
        {
            tooltips.add(new Tooltip(breakdown(parties, clan.rsn())));
        }
        return new Dimension(width, height);
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
