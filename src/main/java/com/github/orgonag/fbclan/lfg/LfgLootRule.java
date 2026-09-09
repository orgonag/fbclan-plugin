package com.github.orgonag.fbclan.lfg;

import lombok.Getter;

/** How a party splits drops. Stored in the database by enum name. */
@Getter
public enum LfgLootRule
{
    UNSPECIFIED("Loot: any"),
    FFA("FFA"),
    SPLIT("Split");

    private final String displayName;

    LfgLootRule(String displayName)
    {
        this.displayName = displayName;
    }

    // Lenient: unknown or blank -> UNSPECIFIED, so an older client never
    // fails to render a party over a rule it doesn't know.
    public static LfgLootRule fromKey(String key)
    {
        if (key != null)
        {
            for (LfgLootRule rule : values())
            {
                if (rule.name().equalsIgnoreCase(key.trim()))
                {
                    return rule;
                }
            }
        }
        return UNSPECIFIED;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
