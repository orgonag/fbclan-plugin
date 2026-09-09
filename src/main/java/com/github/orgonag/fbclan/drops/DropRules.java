package com.github.orgonag.fbclan.drops;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;

/** What counts as a loggable drop. Pure rules; no I/O. */
public final class DropRules
{
    // Floor for the valuable threshold so nobody floods the shared log.
    public static final int MIN_THRESHOLD_GP = 1_000_000;

    // Chat lines the game prints for a pet (pets never appear in loot
    // events); the same set the core Screenshot plugin matches.
    private static final String PET_FOLLOWER = "You have a funny feeling like you're being followed";
    private static final String PET_BACKPACK = "You feel something weird sneaking into your backpack";
    private static final String PET_DUPLICATE = "You have a funny feeling like you would have been followed";

    // Loot the core Loot Tracker reports as an NPC record with no NPC-kill
    // event (it comes from a reward chest): picked up from the tracker's
    // own event instead. Same list Dink keeps.
    public static final String GAUNTLET_BOSS = "Crystalline Hunllef";
    public static final String CORRUPTED_GAUNTLET_BOSS = "Corrupted Hunllef";
    public static final String GAUNTLET = "The Gauntlet";
    public static final String CORRUPTED_GAUNTLET = "The Corrupted Gauntlet";
    public static final Set<String> CHEST_LOOT_NPCS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        GAUNTLET_BOSS, CORRUPTED_GAUNTLET_BOSS, "The Whisperer", "Araxxor",
        "Branda the Fire Queen", "Eldric the Ice King")));

    private DropRules()
    {
    }

    public static long threshold(long configuredGp)
    {
        return Math.max(configuredGp, MIN_THRESHOLD_GP);
    }

    public static boolean valuable(long totalValue, long thresholdGp)
    {
        return totalValue >= thresholdGp;
    }

    // 1 in `denominator` or rarer; 0 disables; unknown never qualifies.
    public static boolean rare(OptionalDouble rarity, int denominator)
    {
        return denominator > 0 && rarity.isPresent() && rarity.getAsDouble() <= 1.0 / denominator;
    }

    // Never logged by the automatic rules regardless of value or rate:
    // clue scrolls, long/curved bones, champion scrolls, and keys. The
    // clan's notable list is an explicit choice and bypasses this.
    public static boolean neverLogged(String itemName)
    {
        if (itemName == null)
        {
            return false;
        }
        String n = itemName.toLowerCase(Locale.ROOT).trim();
        return n.startsWith("clue scroll")
            || n.equals("long bone")
            || n.equals("curved bone")
            || n.endsWith("champion scroll")
            || n.endsWith(" key")
            || n.endsWith(" half of key")
            || n.contains(" key (");
    }

    // The Gauntlet's loot is logged under the activity, not the boss.
    public static String displaySource(String lootSource)
    {
        if (GAUNTLET_BOSS.equals(lootSource))
        {
            return GAUNTLET;
        }
        if (CORRUPTED_GAUNTLET_BOSS.equals(lootSource))
        {
            return CORRUPTED_GAUNTLET;
        }
        return lootSource;
    }

    public static boolean isPetMessage(String message)
    {
        return message != null
            && (message.contains(PET_FOLLOWER) || message.contains(PET_BACKPACK) || message.contains(PET_DUPLICATE));
    }

    // The only variant where the new pet is readable off getFollower().
    public static boolean isFollowerPet(String message)
    {
        return message != null && message.contains(PET_FOLLOWER);
    }

    public static boolean isDuplicatePet(String message)
    {
        return message != null && message.contains(PET_DUPLICATE);
    }

    public static String formatGp(long value)
    {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    // "1/512", rounded to the nearest whole denominator.
    public static String formatRarity(double probability)
    {
        return probability <= 0 ? "" : "1/" + Math.round(1 / probability);
    }
}
