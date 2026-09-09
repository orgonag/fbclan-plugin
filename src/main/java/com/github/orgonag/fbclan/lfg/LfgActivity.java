package com.github.orgonag.fbclan.lfg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.Getter;

/**
 * Every activity a member can post an LFG status or host a party for.
 * The enum name is the stable key stored in the database, so existing
 * keys must never be renamed. Declaration order is the display order in
 * the panel and the "!lfg who" summary.
 *
 * Specific bosses/raids/minigames carry party-size bounds, an optional
 * hard-mode label, and the chat keyword(s) that select them; the
 * catch-all GENERAL entries at the bottom are the original status-only
 * categories and are kept for anything without a dedicated entry.
 */
@Getter
public enum LfgActivity
{
    // Raids
    COX("Chambers of Xeric", Category.RAIDS, 1, 100, "CM", "cox"),
    TOB("Theatre of Blood", Category.RAIDS, 1, 5, "HM", "tob"),
    TOA("Tombs of Amascut", Category.RAIDS, 1, 8, "Expert", "toa"),

    // God Wars Dungeon
    KREEARRA("Kree'arra", Category.GOD_WARS, 1, 8, null, "kree", "arma"),
    GRAARDOR("General Graardor", Category.GOD_WARS, 1, 8, null, "graardor", "bandos"),
    KRIL("K'ril Tsutsaroth", Category.GOD_WARS, 1, 8, null, "kril", "zammy"),
    ZILYANA("Commander Zilyana", Category.GOD_WARS, 1, 8, null, "zilyana", "sara"),
    NEX("Nex", Category.GOD_WARS, 1, 40, null, "nex"),

    // Other group bosses
    NIGHTMARE("The Nightmare", Category.BOSSES, 1, 80, null, "nightmare", "nm"),
    CORP("Corporeal Beast", Category.BOSSES, 1, 30, null, "corp"),
    DKS("Dagannoth Kings", Category.BOSSES, 1, 100, null, "dks"),
    HUEYCOATL("The Hueycoatl", Category.BOSSES, 1, 10, null, "huey", "hueycoatl"),
    YAMA("Yama", Category.BOSSES, 1, 2, null, "yama"),
    ROYAL_TITANS("Royal Titans", Category.BOSSES, 1, 2, null, "titans"),

    // Minigames
    BA("Barbarian Assault", Category.MINIGAMES, 5, 5, null, "ba"),
    ZALCANO("Zalcano", Category.MINIGAMES, 1, 30, null, "zalcano"),
    VOLCANIC_MINE("Volcanic Mine", Category.MINIGAMES, 1, 30, null, "vm"),
    CASTLE_WARS("Castle Wars", Category.MINIGAMES, 1, 50, null, "cw"),
    GOTR("Guardians of the Rift", Category.MINIGAMES, 1, 30, null, "gotr"),
    WINTERTODT("Wintertodt", Category.MINIGAMES, 1, 30, null, "wt", "todt"),

    // Catch-alls (the original status-only categories)
    GROUP_BOSS("Group Boss", Category.GENERAL, 1, 100, null, "groupboss"),
    MINIGAME("Minigame", Category.GENERAL, 1, 100, null, "minigame"),
    PVP("PvP", Category.GENERAL, 1, 100, null, "pvp"),
    SKILLING("Skilling", Category.GENERAL, 1, 100, null, "skilling"),
    CHILLING("Chilling", Category.GENERAL, 1, 100, null, "chilling");

    @Getter
    public enum Category
    {
        RAIDS("Raids"),
        GOD_WARS("God Wars"),
        BOSSES("Bosses"),
        MINIGAMES("Minigames"),
        GENERAL("General");

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
    // Name of the harder variant ("CM", "HM", "Expert"), or null when none.
    private final String hardModeLabel;
    // Chat keywords for "!lfg <keyword>"; the first is the canonical one
    // shown in usage text.
    private final List<String> keywords;

    LfgActivity(String displayName, Category category, int minPartySize, int maxPartySize,
                String hardModeLabel, String... keywords)
    {
        this.displayName = displayName;
        this.category = category;
        this.minPartySize = minPartySize;
        this.maxPartySize = maxPartySize;
        this.hardModeLabel = hardModeLabel;
        this.keywords = Collections.unmodifiableList(Arrays.asList(keywords));
    }

    public String getKey()
    {
        return name();
    }

    public String getKeyword()
    {
        return keywords.get(0);
    }

    public boolean isRaid()
    {
        return category == Category.RAIDS;
    }

    public boolean hasHardMode()
    {
        return hardModeLabel != null && !usesInvocation();
    }

    // ToA's difficulty is an invocation level rather than a CM/HM toggle.
    public boolean usesInvocation()
    {
        return this == TOA;
    }

    public boolean hasRoles()
    {
        return this == TOB || this == COX || this == BA;
    }

    // True when the OSRS hiscores track a kill count a host can set a
    // minimum for. The catch-alls and score-less minigames have none.
    public boolean hasKillcount()
    {
        switch (this)
        {
            case BA:
            case VOLCANIC_MINE:
            case CASTLE_WARS:
            case GROUP_BOSS:
            case MINIGAME:
            case PVP:
            case SKILLING:
            case CHILLING:
                return false;
            default:
                return true;
        }
    }

    // Community abbreviation for compact rows and chat lines.
    public String getShortName()
    {
        switch (this)
        {
            case COX:
            case TOB:
            case TOA:
            case NEX:
            case DKS:
            case BA:
            case GOTR:
                return name();
            case KREEARRA:
                return "Arma";
            case GRAARDOR:
                return "Bandos";
            case KRIL:
                return "Zammy";
            case ZILYANA:
                return "Sara";
            case NIGHTMARE:
                return "Nightmare";
            case ROYAL_TITANS:
                return "Titans";
            case HUEYCOATL:
                return "Huey";
            case VOLCANIC_MINE:
                return "VM";
            case CASTLE_WARS:
                return "CW";
            case WINTERTODT:
                return "WT";
            default:
                return displayName;
        }
    }

    // Short title for a hosted party: "CM", "HMT", "ToA (300)", else the
    // short name.
    public String getPartyTitle(boolean hardMode, int invocation)
    {
        if (usesInvocation())
        {
            return invocation > 0 ? "ToA (" + invocation + ")" : "ToA";
        }
        if (hardMode && hasHardMode())
        {
            return this == TOB ? "HMT" : (this == COX ? "CoX CM" : getShortName() + " " + hardModeLabel);
        }
        return getShortName();
    }

    public static LfgActivity fromKey(String key)
    {
        if (key == null)
        {
            return null;
        }
        try
        {
            return valueOf(key);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    // Case-insensitive chat keyword lookup; null when unknown.
    public static LfgActivity fromKeyword(String keyword)
    {
        if (keyword == null)
        {
            return null;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        for (LfgActivity activity : values())
        {
            if (activity.keywords.contains(lower))
            {
                return activity;
            }
        }
        return null;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
