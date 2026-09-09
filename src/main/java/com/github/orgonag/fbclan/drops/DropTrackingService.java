package com.github.orgonag.fbclan.drops;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;

public class DropTrackingService
{
    // Chat messages the game sends when a pet drops — the same set the core
    // Screenshot plugin matches on. Pets never appear in loot events, so
    // chat is the only signal.
    private static final String PET_FOLLOWER = "You have a funny feeling like you're being followed";
    private static final String PET_BACKPACK = "You feel something weird sneaking into your backpack";
    private static final String PET_DUPLICATE = "You have a funny feeling like you would have been followed";

    // Floor for the user-configurable drop threshold. Stops a member from
    // setting it to 0 and flooding the shared drop log with junk drops.
    // Pets deliberately bypass the threshold (they are untradeable).
    public static final int MIN_THRESHOLD_GP = 1_000_000;

    // The threshold comes from user config (dropThresholdGp) rather than a
    // hardcoded constant, so clan members can tune what counts as "valuable".
    public static boolean isValuableDrop(int gePrice, int quantity, long thresholdGp)
    {
        return (long) gePrice * quantity >= thresholdGp;
    }

    // A drop counts as rare when its drop rate is 1 in `thresholdDenominator`
    // or rarer (e.g. 100 -> 1% or rarer). 0 disables the rule. Unknown
    // rarity (no table data) never counts as rare.
    public static boolean isRareDrop(OptionalDouble rarity, int thresholdDenominator)
    {
        if (thresholdDenominator <= 0 || rarity == null || !rarity.isPresent())
        {
            return false;
        }
        return rarity.getAsDouble() <= 1.0 / thresholdDenominator;
    }

    // Clue scrolls are ~1/128 from countless monsters; they'd flood the log
    // under any sane rarity threshold and are never what "rare drop" means.
    public static boolean isClueScroll(String itemName)
    {
        return itemName != null && itemName.toLowerCase(Locale.ROOT).startsWith("clue scroll");
    }

    // "1/512" style, rounded to the nearest whole denominator.
    public static String formatRarity(double probability)
    {
        if (probability <= 0)
        {
            return "";
        }
        return "1/" + Math.round(1 / probability);
    }

    // Loot sources RuneLite's Loot Tracker reports as an NPC-type record
    // WITHOUT a matching NPC-kill event (the loot comes from a reward chest
    // or interface, not the corpse). They have to be picked up from the
    // Loot Tracker's own event instead. Same list the Dink plugin keeps.
    public static final String GAUNTLET_BOSS = "Crystalline Hunllef";
    public static final String CORRUPTED_GAUNTLET_BOSS = "Corrupted Hunllef";
    public static final String GAUNTLET = "The Gauntlet";
    public static final String CORRUPTED_GAUNTLET = "The Corrupted Gauntlet";
    public static final Set<String> SPECIAL_LOOT_NPC_NAMES = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(GAUNTLET_BOSS, CORRUPTED_GAUNTLET_BOSS, "The Whisperer", "Araxxor",
            "Branda the Fire Queen", "Eldric the Ice King")));

    // The Gauntlet's loot is logged under the activity name, not the boss.
    public static String displaySourceName(String lootSourceName)
    {
        if (GAUNTLET_BOSS.equals(lootSourceName))
        {
            return GAUNTLET;
        }
        if (CORRUPTED_GAUNTLET_BOSS.equals(lootSourceName))
        {
            return CORRUPTED_GAUNTLET;
        }
        return lootSourceName;
    }

    // The config UI already bounds the value, but a hand-edited RuneLite
    // config file can hold anything — clamp at the point of use.
    public static long effectiveThreshold(long configuredGp)
    {
        return Math.max(configuredGp, MIN_THRESHOLD_GP);
    }

    public static boolean isPetMessage(String message)
    {
        return message != null
            && (message.contains(PET_FOLLOWER)
                || message.contains(PET_BACKPACK)
                || message.contains(PET_DUPLICATE));
    }

    // True only for the variant where the pet spawned as the follower —
    // the one case where the new pet's name can be read off getFollower().
    public static boolean isFollowerPetMessage(String message)
    {
        return message != null && message.contains(PET_FOLLOWER);
    }

    public static boolean isDuplicatePetMessage(String message)
    {
        return message != null && message.contains(PET_DUPLICATE);
    }

    public static String formatGp(long value)
    {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    // Names come from a clan-curated Supabase table (synced from a Google
    // Sheet). Matching is case/whitespace-insensitive so "Araxyte Fang" in
    // the sheet still matches the in-game "Araxyte fang".
    public static String normalizeItemName(String name)
    {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    // Notable drops bypass the GP threshold entirely — they are typically
    // untradeable (GE price 0) and would never qualify by value.
    // notableNames entries must already be normalized (see normalizeItemName
    // / NotableItemsService.parseNames).
    public static boolean isNotableDrop(String itemName, Set<String> notableNames)
    {
        return itemName != null && notableNames != null
            && notableNames.contains(normalizeItemName(itemName));
    }
}
