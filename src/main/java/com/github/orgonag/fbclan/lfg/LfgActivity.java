package com.github.orgonag.fbclan.lfg;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LfgActivity
{
    COX("Chambers of Xeric"),
    TOB("Theatre of Blood"),
    TOA("Tombs of Amascut"),
    GROUP_BOSS("Group Boss"),
    MINIGAME("Minigame"),
    PVP("PvP"),
    SKILLING("Skilling"),
    CHILLING("Chilling");

    private final String displayName;

    public String getKey()
    {
        return name();
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

    @Override
    public String toString()
    {
        return displayName;
    }
}
