package com.github.orgonag.fbclan.ui;

import com.github.orgonag.fbclan.core.Clan;
import com.github.orgonag.fbclan.core.Names;
import com.github.orgonag.fbclan.core.Supabase;
import com.github.orgonag.fbclan.drops.DropLogger;
import com.github.orgonag.fbclan.drops.DropRules;
import com.github.orgonag.fbclan.stats.Dashboard;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;

/** The last 50 clan drops: a top-drop highlight, filter chips, and item rows that open {@link DropViewer}. */
@Singleton
public class DropLogTab extends Tab
{
    private enum Filter
    {
        ALL("All"), MINE("Mine"), RARE("Rare+"), PETS("Pets");

        final String label;

        Filter(String label)
        {
            this.label = label;
        }
    }

    private final DropLogger drops;
    private final DropViewer viewer;
    private final ItemManager items;
    private final Clan clan;
    private List<Drop> rows = Collections.emptyList();
    private Filter filter = Filter.ALL;

    @Inject
    public DropLogTab(DropLogger drops, DropViewer viewer, ItemManager items, Clan clan, ScheduledExecutorService executor)
    {
        super("Drop Log", executor);
        this.drops = drops;
        this.viewer = viewer;
        this.items = items;
        this.clan = clan;
        control(new Choice<>(Arrays.asList(Filter.values()), f -> f.label, filter, f -> {
            filter = f;
            render();
        }));
    }

    @Override
    public void refresh()
    {
        load(() -> parse(drops.recent()), parsed -> {
            rows = parsed;
            render();
        });
    }

    public void closeViewer()
    {
        viewer.close();
    }

    private void render()
    {
        List<Drop> shown = new ArrayList<>();
        for (Drop d : rows)
        {
            if (matches(d)) shown.add(d);
        }
        Drop top = null;
        long dayGp = 0;
        int dayCount = 0;
        for (Drop d : shown)
        {
            if (Duration.between(d.at, Instant.now()).toHours() >= 24) continue;
            dayCount++;
            dayGp += d.value;
            if (d.value > 0 && (top == null || d.value > top.value)) top = d;
        }
        note.setText(dayCount == 0 ? "" : dayCount + " in 24h · " + Dashboard.shortNumber(dayGp));
        Drop best = top;
        fill(() -> {
            if (best != null) list.add(topCard(best));
            if (shown.isEmpty())
            {
                list.add(Theme.centered(rows.isEmpty() ? "No drops logged yet." : "No drops match this filter."));
            }
            for (Drop d : shown)
            {
                list.add(row(d));
            }
        });
    }

    private boolean matches(Drop d)
    {
        switch (filter)
        {
            case MINE: return clan.rsn() != null && Names.same(d.rsn, clan.rsn());
            case RARE: return d.tier() != Theme.Tier.COMMON;
            case PETS: return d.tier() == Theme.Tier.PET;
            default: return true;
        }
    }

    private JPanel topCard(Drop d)
    {
        Card card = Theme.card(Theme.ACCENT);
        card.add(Theme.row(Theme.badge("TOP DROP · 24H"), null, Theme.text(Theme.timeAgo(d.at), Theme.SUB)));
        JPanel text = Theme.stack(1);
        text.add(Theme.bold(d.name, Theme.TEXT));
        text.add(Theme.text(d.rsn + " · " + d.npc, Theme.SUB));
        JLabel value = Theme.bold(Dashboard.shortNumber(d.value) + (d.rarity > 0 ? "  " + DropRules.formatRarity(d.rarity) : ""), Theme.GOLD);
        text.add(value);
        card.add(Theme.row(Theme.tile(items, d.itemId(), d.quantity, d.tier(), 40), text, null));
        card.add(Theme.text(d.hasScreenshot() ? "Click to view the screenshot" : "Click for drop details", Theme.ACCENT_HI));
        return clickable(card, d);
    }

    private JPanel row(Drop d)
    {
        Card card = Theme.card(null);
        JPanel text = Theme.stack(1);
        text.add(Theme.bold(d.name, Theme.TEXT));
        text.add(Theme.text(d.rsn + " · " + Theme.timeAgo(d.at), Theme.SUB));
        text.add(Theme.text(d.npc, Theme.FAINT));
        JPanel east = Theme.stack(1);
        east.add(Theme.right(Theme.bold(d.value > 0 ? Dashboard.shortNumber(d.value) : d.tier() == Theme.Tier.PET ? "Pet" : "", Theme.GOLD)));
        east.add(Theme.right(Theme.text(d.rarity > 0 ? DropRules.formatRarity(d.rarity) : "", d.tier().edge)));
        JLabel pic = Theme.right(Theme.text(d.hasScreenshot() ? "pic" : "", Theme.ACCENT_HI));
        pic.setIcon(d.hasScreenshot() ? CAMERA : null);
        east.add(pic);
        card.add(Theme.row(Theme.tile(items, d.itemId(), d.quantity, d.tier(), 32), text, east));
        return clickable(card, d);
    }

    // The whole card opens the viewer. The tooltip carries the full,
    // possibly truncated, line and starts with fixed text so a remote
    // item name can never be read as tooltip HTML.
    private JPanel clickable(Card card, Drop d)
    {
        card.setToolTipText("Drop: " + d.summary());
        return Theme.onClick(card, () -> viewer.show(card, d));
    }

    private static List<Drop> parse(JsonArray array)
    {
        List<Drop> out = new ArrayList<>();
        for (JsonElement el : array)
        {
            out.add(new Drop(el.getAsJsonObject()));
        }
        return out;
    }

    /** One drops row, as the tab and the viewer need it. */
    static final class Drop
    {
        final String name;
        final String rsn;
        final String npc;
        final int id;
        final int quantity;
        final long value;
        final double rarity;
        final Instant at;
        final String screenshot;

        Drop(JsonObject row)
        {
            name = Supabase.str(row, "item_name");
            rsn = Supabase.str(row, "rsn");
            npc = Supabase.str(row, "npc_name");
            id = Supabase.intOr(row, "item_id", 0);
            quantity = Supabase.intOr(row, "quantity", 1);
            value = Supabase.longOr(row, "ge_value", 0);
            rarity = Supabase.doubleOr(row, "rarity", 0);
            at = Supabase.instant(row, "occurred_at", Instant.now());
            // Drop rows are client-reported, so a screenshot link is only
            // honoured when it points into the plugin's own public bucket.
            String url = Supabase.str(row, "screenshot_url");
            screenshot = DropLogger.isScreenshot(url) ? url : "";
        }

        // "Item (1,234,567 GP) [1/512]", for the row tooltip.
        String summary()
        {
            return name + (value > 0 ? " (" + DropRules.formatGp(value) + " GP)" : "")
                + (rarity > 0 ? " [" + DropRules.formatRarity(rarity) + "]" : "");
        }

        boolean hasScreenshot()
        {
            return !screenshot.isEmpty();
        }

        // Follower pets carry no item id; show a stand-in pet sprite.
        int itemId()
        {
            return id > 0 ? id : ItemID.SNAKEPET;
        }

        Theme.Tier tier()
        {
            if (value == 0 && name.startsWith("Pet")) return Theme.Tier.PET;
            if ((rarity > 0 && rarity <= 0.001) || value >= 100_000_000L) return Theme.Tier.MEGA;
            if ((rarity > 0 && rarity <= 0.01) || value >= 10_000_000L) return Theme.Tier.RARE;
            return Theme.Tier.COMMON;
        }
    }

    // A tiny camera, drawn so it needs no asset or font glyph.
    private static final Icon CAMERA = new Icon()
    {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = Theme.smooth(g);
            g2.setColor(Theme.ACCENT_HI);
            g2.fillRoundRect(x, y + 2, 11, 8, 3, 3);
            g2.fillRect(x + 3, y, 5, 3);
            g2.setColor(new Color(0x1A1A1A));
            g2.fillOval(x + 3, y + 3, 5, 5);
            g2.dispose();
        }

        @Override
        public int getIconWidth()
        {
            return 11;
        }

        @Override
        public int getIconHeight()
        {
            return 10;
        }
    };
}
