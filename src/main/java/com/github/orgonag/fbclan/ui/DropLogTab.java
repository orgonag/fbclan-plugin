package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.core.Supabase;
import com.github.orgonag.fbclan.drops.DropLogger;
import com.github.orgonag.fbclan.drops.DropRules;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** The last 50 clan drops. */
@Singleton
public class DropLogTab extends JPanel
{
    private final DropLogger drops;
    private final ScheduledExecutorService executor;
    private final JPanel list;

    @Inject
    public DropLogTab(DropLogger drops, ScheduledExecutorService executor)
    {
        this.drops = drops;
        this.executor = executor;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        list = Ui.scrollList(this);
    }

    public void refresh()
    {
        Ui.async(executor, () -> drops.recent(50), this::render);
    }

    private void render(JsonArray rows)
    {
        list.removeAll();
        if (rows.size() == 0)
        {
            list.add(Ui.empty("No drops logged yet."));
        }
        for (JsonElement el : rows)
        {
            list.add(row(el.getAsJsonObject()));
        }
        list.revalidate();
        list.repaint();
    }

    private JPanel row(JsonObject drop)
    {
        JPanel row = Ui.row(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        long value = Supabase.longOr(drop, "ge_value", 0);
        String suffix = value > 0 ? " (" + DropRules.formatGp(value) + " GP)" : "";
        if (Supabase.has(drop, "rarity") && drop.get("rarity").getAsDouble() > 0)
        {
            suffix += " [" + DropRules.formatRarity(drop.get("rarity").getAsDouble()) + "]";
        }
        // Plain labels truncate with "..." where HTML would wrap and grow.
        JLabel main = new JLabel(Supabase.str(drop, "item_name") + suffix);
        main.setForeground(Color.WHITE);
        main.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        JLabel detail = Ui.small(Supabase.str(drop, "rsn") + " — " + Supabase.str(drop, "npc_name")
            + " — " + Ui.timeAgo(Supabase.str(drop, "created_at")), Ui.MUTED);
        JPanel stack = Ui.column(ColorScheme.DARKER_GRAY_COLOR);
        stack.add(main);
        stack.add(detail);
        row.add(stack, BorderLayout.CENTER);

        // The drops table is anon-writable, so a screenshot link is only
        // honoured when it points into the plugin's own public bucket.
        String url = Supabase.str(drop, "screenshot_url");
        if (url.startsWith(DropLogger.screenshotPrefix()))
        {
            JLabel pic = Ui.small("[pic]", ColorScheme.BRAND_ORANGE);
            pic.setToolTipText("Click to view screenshot");
            row.add(pic, BorderLayout.EAST);
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    LinkBrowser.browse(url);
                }
            });
        }
        return row;
    }
}
