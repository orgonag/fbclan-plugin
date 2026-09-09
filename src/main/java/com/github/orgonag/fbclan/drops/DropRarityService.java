package com.github.orgonag.fbclan.drops;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;

/**
 * Drop-rate lookup: "how rare is item X from source Y?" Backed by the
 * bundled npc_drops.json — the OSRS Wiki's monster drop tables
 * (CC BY-NC-SA 3.0), parsed by Flipping Utilities' parsed-osrs and
 * transformed into this compact form by the Dink plugin (BSD-2-Clause,
 * (c) Jake Barter). The lookup logic mirrors Dink's AbstractRarityService.
 * A small in-code supplement covers reward chests the wiki table lacks
 * (the Gauntlet).
 *
 * Loaded once on the executor at startup (~700 KB of JSON); lookups
 * before that completes report "unknown" rather than blocking.
 */
@Slf4j
public class DropRarityService
{
    private static final String RESOURCE = "/com/github/orgonag/fbclan/npc_drops.json";

    private final ItemManager itemManager;
    private volatile Map<String, Collection<RareDrop>> dropsBySource = Collections.emptyMap();

    public DropRarityService(ItemManager itemManager)
    {
        this.itemManager = itemManager;
    }

    // Parses the bundled table. Safe to call more than once; the last
    // successful load wins.
    public void load()
    {
        Map<String, List<RawDrop>> raw;
        try (InputStream is = DropRarityService.class.getResourceAsStream(RESOURCE);
             Reader reader = new BufferedReader(new InputStreamReader(
                 java.util.Objects.requireNonNull(is, "missing " + RESOURCE), StandardCharsets.UTF_8)))
        {
            raw = new Gson().fromJson(reader, new TypeToken<Map<String, List<RawDrop>>>() {}.getType());
        }
        catch (Exception e)
        {
            log.error("Failed to read drop rate table", e);
            return;
        }
        Map<String, Collection<RareDrop>> table = new HashMap<>(raw.size() * 2);
        raw.forEach((source, rawDrops) -> {
            List<RareDrop> drops = new ArrayList<>();
            for (RawDrop r : rawDrops)
            {
                drops.addAll(r.expand());
            }
            table.put(source, drops);
        });
        table.putAll(supplement());
        dropsBySource = table;
        log.info("Loaded drop rates for {} sources", table.size());
    }

    // Probability (0..1) that one kill/completion of `source` yields this
    // item stack, or empty when the source or item isn't in the table.
    // Noted items resolve to the unnoted id; item variants (charged /
    // ornamented) match when they share a name, as in Dink.
    public OptionalDouble getRarity(String source, int itemId, int quantity)
    {
        Collection<RareDrop> drops = dropsBySource.get(source);
        if (drops == null || drops.isEmpty())
        {
            return OptionalDouble.empty();
        }
        ItemComposition comp = itemId >= 0 ? itemManager.getItemComposition(itemId) : null;
        int canonical = comp != null && comp.getNote() != -1 ? comp.getLinkedNoteId() : itemId;
        String itemName = comp != null ? comp.getMembersName() : "";
        Collection<Integer> variants = new HashSet<>(
            ItemVariationMapping.getVariations(ItemVariationMapping.map(canonical)));

        double total = 0;
        boolean any = false;
        for (RareDrop d : drops)
        {
            if (quantity < d.minQuantity || quantity > d.maxQuantity)
            {
                continue;
            }
            boolean match = d.itemId == itemId
                || (variants.contains(d.itemId)
                    && itemName.equals(itemManager.getItemComposition(d.itemId).getMembersName()));
            if (match)
            {
                total += d.probability;
                any = true;
            }
        }
        return any ? OptionalDouble.of(total) : OptionalDouble.empty();
    }

    // Reward chests the wiki monster table doesn't cover. Rates from the
    // OSRS Wiki's Gauntlet reward pages.
    static Map<String, Collection<RareDrop>> supplement()
    {
        Map<String, Collection<RareDrop>> m = new HashMap<>();
        // Item ids: enhanced crystal weapon seed, crystal armour seed,
        // crystal weapon seed.
        m.put(DropTrackingService.GAUNTLET, Arrays.asList(
            new RareDrop(25859, 1, 1, 1.0 / 2000),
            new RareDrop(23956, 1, 1, 1.0 / 120),
            new RareDrop(4207, 1, 1, 1.0 / 120)));
        m.put(DropTrackingService.CORRUPTED_GAUNTLET, Arrays.asList(
            new RareDrop(25859, 1, 1, 1.0 / 400),
            new RareDrop(23956, 1, 1, 1.0 / 50),
            new RareDrop(4207, 1, 1, 1.0 / 50)));
        return m;
    }

    @Value
    static class RareDrop
    {
        int itemId;
        int minQuantity;
        int maxQuantity;
        double probability;
    }

    // One line of npc_drops.json: item id, denominator (1/d per roll),
    // optional roll count, and a fixed quantity or a min..max range.
    private static class RawDrop
    {
        @SerializedName("i") int itemId;
        @SerializedName("r") Integer rolls;
        @SerializedName("d") double denominator;
        @SerializedName("q") Integer quantity;
        @SerializedName("m") Integer quantMin;
        @SerializedName("n") Integer quantMax;

        // Multi-roll tables become one entry per possible success count,
        // each with the binomial probability of exactly that many hits.
        Collection<RareDrop> expand()
        {
            int rounds = rolls != null ? rolls : 1;
            int min = quantMin != null ? quantMin : (quantity != null ? quantity : 1);
            int max = quantMax != null ? quantMax : (quantity != null ? quantity : 1);
            double p = denominator > 0 ? 1 / denominator : 0;
            if (rounds <= 1)
            {
                return Collections.singletonList(new RareDrop(itemId, min, max, p));
            }
            List<RareDrop> out = new ArrayList<>(rounds);
            for (int k = 1; k <= rounds; k++)
            {
                out.add(new RareDrop(itemId, min * k, max * k, binomial(p, rounds, k)));
            }
            return out;
        }

        private static double binomial(double p, int n, int k)
        {
            double coeff = 1;
            for (int i = 1; i <= k; i++)
            {
                coeff = coeff * (n - k + i) / i;
            }
            return coeff * Math.pow(p, k) * Math.pow(1 - p, n - k);
        }
    }
}
