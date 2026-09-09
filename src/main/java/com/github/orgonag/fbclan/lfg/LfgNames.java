package com.github.orgonag.fbclan.lfg;

import java.util.Locale;

/**
 * RSN comparison helper. In-game names can arrive with non-breaking
 * spaces (chat/clan channel) where the database has plain spaces, and
 * casing differs between sources, so every "is this me / same player"
 * check goes through here.
 */
public final class LfgNames
{
    private LfgNames()
    {
    }

    public static String normalize(String name)
    {
        return name == null ? "" : name.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
    }

    public static boolean equal(String a, String b)
    {
        return normalize(a).equals(normalize(b));
    }
}
