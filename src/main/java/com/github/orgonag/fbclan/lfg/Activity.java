package com.github.orgonag.fbclan.lfg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import net.runelite.client.hiscore.HiscoreSkill;

/**
 * Everything a party can be hosted for. The enum name is the key stored
 * in the database and must never be renamed. Declaration order is the
 * order in the activity pickers.
 */
@Getter
public enum Activity
{
    // Raids
    COX("Chambers of Xeric", Category.RAIDS, 1, 100, "CM", 20997),
    TOB("Theatre of Blood", Category.RAIDS, 1, 5, "HM", 22325),
    TOA("Tombs of Amascut", Category.RAIDS, 1, 8, "Expert", 27275),

    // God Wars Dungeon
    KREEARRA("Kree'arra", Category.GOD_WARS, 1, 8, null, 11828),
    GRAARDOR("General Graardor", Category.GOD_WARS, 1, 8, null, 11832),
    KRIL("K'ril Tsutsaroth", Category.GOD_WARS, 1, 8, null, 11824),
    ZILYANA("Commander Zilyana", Category.GOD_WARS, 1, 8, null, 11838),
    NEX("Nex", Category.GOD_WARS, 1, 40, null, 26235),

    // Other group bosses
    NIGHTMARE("The Nightmare", Category.BOSSES, 1, 80, null, 24417),
    CORP("Corporeal Beast", Category.BOSSES, 1, 30, null, 12817),
    DKS("Dagannoth Kings", Category.BOSSES, 1, 100, null, 6737),
    HUEYCOATL("The Hueycoatl", Category.BOSSES, 1, 10, null, 30064),
    YAMA("Yama", Category.BOSSES, 1, 2, null, 30750),
    ROYAL_TITANS("Royal Titans", Category.BOSSES, 1, 2, null, 30634),

    // Minigames
    BA("Barbarian Assault", Category.MINIGAMES, 5, 5, null, 10551),
    ZALCANO("Zalcano", Category.MINIGAMES, 1, 30, null, 23953),
    VOLCANIC_MINE("Volcanic Mine", Category.MINIGAMES, 1, 30, null, 21622),
    CASTLE_WARS("Castle Wars", Category.MINIGAMES, 1, 50, null, 4067),
    GOTR("Guardians of the Rift", Category.MINIGAMES, 1, 30, null, 26822),
    WINTERTODT("Wintertodt", Category.MINIGAMES, 1, 30, null, 20708),

    // Catch-alls for anything without a dedicated entry
    GROUP_BOSS("Group Boss", Category.GENERAL, 1, 100, null, 13576),
    MINIGAME("Minigame", Category.GENERAL, 1, 100, null, 3853),
    PVP("PvP", Category.GENERAL, 1, 100, null, 964),
    SKILLING("Skilling", Category.GENERAL, 1, 100, null, 11850),
    CHILLING("Chilling", Category.GENERAL, 1, 100, null, 1978);

    @Getter
    public enum Category
    {
        RAIDS("Raids"), GOD_WARS("God Wars"), BOSSES("Bosses"), MINIGAMES("Minigames"), GENERAL("General");

        private final String displayName;

        Category(String displayName)
        {
            this.displayName = displayName;
        }
    }

    private final String displayName;
    private final Category category;
    private final int minPartySize;
    private final int maxPartySize;
    // "CM" / "HM" / "Expert", or null when there's no harder variant.
    private final String hardModeLabel;
    // A representative item sprite from RuneLite's item cache.
    private final int iconItemId;

    Activity(String displayName, Category category, int minPartySize, int maxPartySize, String hardModeLabel, int iconItemId)
    {
        this.displayName = displayName;
        this.category = category;
        this.minPartySize = minPartySize;
        this.maxPartySize = maxPartySize;
        this.hardModeLabel = hardModeLabel;
        this.iconItemId = iconItemId;
    }

    public String key()
    {
        return name();
    }

    public boolean isRaid()
    {
        return category == Category.RAIDS;
    }

    // ToA's difficulty is an invocation level rather than a toggle.
    public boolean usesInvocation()
    {
        return this == TOA;
    }

    public boolean hasHardMode()
    {
        return hardModeLabel != null && !usesInvocation();
    }

    public boolean hasRoles()
    {
        return this == TOB || this == COX || this == BA;
    }

    // True when a kill count exists a host can set a minimum for.
    public boolean hasKillcount()
    {
        return !hiscoreSkills().isEmpty();
    }

    // Community abbreviation for card titles and chat lines.
    public String shortName()
    {
        switch (this)
        {
            case COX: case TOB: case TOA: case NEX: case DKS: case BA: case GOTR:
                return name();
            case KREEARRA: return "Arma";
            case GRAARDOR: return "Bandos";
            case KRIL: return "Zammy";
            case ZILYANA: return "Sara";
            case NIGHTMARE: return "Nightmare";
            case ROYAL_TITANS: return "Titans";
            case HUEYCOATL: return "Huey";
            case VOLCANIC_MINE: return "VM";
            case CASTLE_WARS: return "CW";
            case WINTERTODT: return "WT";
            default: return displayName;
        }
    }

    // "CM", "HMT", "ToA (300)", else the short name.
    public String partyTitle(boolean hardMode, int invocation)
    {
        if (usesInvocation())
        {
            return invocation > 0 ? "ToA (" + invocation + ")" : "ToA";
        }
        if (hardMode && hasHardMode())
        {
            return this == TOB ? "HMT" : this == COX ? "CoX CM" : shortName() + " " + hardModeLabel;
        }
        return shortName();
    }

    // Hiscore entries the activity's kill count spans (three for DKs).
    public List<HiscoreSkill> hiscoreSkills()
    {
        switch (this)
        {
            case COX: return Collections.singletonList(HiscoreSkill.CHAMBERS_OF_XERIC);
            case TOB: return Collections.singletonList(HiscoreSkill.THEATRE_OF_BLOOD);
            case TOA: return Collections.singletonList(HiscoreSkill.TOMBS_OF_AMASCUT);
            case KREEARRA: return Collections.singletonList(HiscoreSkill.KREEARRA);
            case GRAARDOR: return Collections.singletonList(HiscoreSkill.GENERAL_GRAARDOR);
            case KRIL: return Collections.singletonList(HiscoreSkill.KRIL_TSUTSAROTH);
            case ZILYANA: return Collections.singletonList(HiscoreSkill.COMMANDER_ZILYANA);
            case NEX: return Collections.singletonList(HiscoreSkill.NEX);
            case NIGHTMARE: return Collections.singletonList(HiscoreSkill.NIGHTMARE);
            case CORP: return Collections.singletonList(HiscoreSkill.CORPOREAL_BEAST);
            case DKS: return Arrays.asList(HiscoreSkill.DAGANNOTH_PRIME, HiscoreSkill.DAGANNOTH_REX, HiscoreSkill.DAGANNOTH_SUPREME);
            case HUEYCOATL: return Collections.singletonList(HiscoreSkill.THE_HUEYCOATL);
            case YAMA: return Collections.singletonList(HiscoreSkill.YAMA);
            case ROYAL_TITANS: return Collections.singletonList(HiscoreSkill.THE_ROYAL_TITANS);
            case ZALCANO: return Collections.singletonList(HiscoreSkill.ZALCANO);
            case GOTR: return Collections.singletonList(HiscoreSkill.RIFTS_CLOSED);
            case WINTERTODT: return Collections.singletonList(HiscoreSkill.WINTERTODT);
            default: return Collections.emptyList();
        }
    }

    public HiscoreSkill hardModeHiscoreSkill()
    {
        switch (this)
        {
            case COX: return HiscoreSkill.CHAMBERS_OF_XERIC_CHALLENGE_MODE;
            case TOB: return HiscoreSkill.THEATRE_OF_BLOOD_HARD_MODE;
            case TOA: return HiscoreSkill.TOMBS_OF_AMASCUT_EXPERT;
            default: return null;
        }
    }

    // Boss names as the core Chat Commands plugin stores kill counts
    // (lower-cased on read). Alternatives where the wording is uncertain.
    public List<String> localKillcountKeys(boolean hardMode)
    {
        switch (this)
        {
            case COX: return Collections.singletonList(hardMode ? "Chambers of Xeric Challenge Mode" : "Chambers of Xeric");
            case TOB: return Collections.singletonList(hardMode ? "Theatre of Blood Hard Mode" : "Theatre of Blood");
            case TOA: return Collections.singletonList(hardMode ? "Tombs of Amascut Expert Mode" : "Tombs of Amascut");
            case KREEARRA: return Collections.singletonList("Kree'arra");
            case GRAARDOR: return Collections.singletonList("General Graardor");
            case KRIL: return Collections.singletonList("K'ril Tsutsaroth");
            case ZILYANA: return Collections.singletonList("Commander Zilyana");
            case NEX: return Collections.singletonList("Nex");
            case NIGHTMARE: return Arrays.asList("Nightmare", "The Nightmare");
            case CORP: return Collections.singletonList("Corporeal Beast");
            case DKS: return Arrays.asList("Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme");
            case HUEYCOATL: return Arrays.asList("The Hueycoatl", "Hueycoatl");
            case YAMA: return Collections.singletonList("Yama");
            case ROYAL_TITANS: return Arrays.asList("The Royal Titans", "Royal Titans");
            case ZALCANO: return Collections.singletonList("Zalcano");
            case GOTR: return Collections.singletonList("Guardians of the Rift");
            case WINTERTODT: return Collections.singletonList("Wintertodt");
            default: return Collections.emptyList();
        }
    }

    public static Activity fromKey(String key)
    {
        try
        {
            return key == null ? null : valueOf(key);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
