package com.github.orgonag.fbclan.lfg;

import lombok.Getter;

/** How a party splits drops. Stored by enum name; unknown reads as UNSPECIFIED. */
@Getter
public enum LootRule
{
    UNSPECIFIED("Loot: any"), FFA("FFA"), SPLIT("Split");

    private final String displayName;

    LootRule(String displayName)
    {
        this.displayName = displayName;
    }

    public static LootRule fromKey(String key)
    {
        for (LootRule rule : values())
        {
            if (key != null && rule.name().equalsIgnoreCase(key.trim()))
            {
                return rule;
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
