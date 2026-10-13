package com.github.orgonag.fbclan.lfg;

import lombok.Getter;

/** How a party shares drops. Stored by enum name; anything else (older posts' UNSPECIFIED) reads as SPLIT. */
@Getter
public enum LootRule
{
    SPLIT("Split"), FFA("FFA");

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
        return SPLIT;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
