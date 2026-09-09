package com.github.orgonag.fbclan.core;

import java.util.Locale;

/**
 * Text helpers shared by every feature: RSN comparison (in-game names
 * arrive with non-breaking spaces and mixed case) and a sanitizer for
 * user-typed text headed to the database.
 */
public final class Names
{
    private Names()
    {
    }

    public static String normalize(String name)
    {
        return name == null ? "" : name.replace(' ', ' ').trim().toLowerCase(Locale.ROOT);
    }

    public static boolean same(String a, String b)
    {
        return normalize(a).equals(normalize(b));
    }

    // Strips control characters, trims, caps; null for blank input.
    public static String sanitize(String text, int maxLength)
    {
        if (text == null)
        {
            return null;
        }
        String cleaned = text.replaceAll("\\p{Cntrl}", " ").trim();
        if (cleaned.isEmpty())
        {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength).trim() : cleaned;
    }

    // Case/whitespace-insensitive item-name key (notable list matching).
    public static String itemKey(String itemName)
    {
        return itemName == null ? "" : itemName.trim().toLowerCase(Locale.ROOT);
    }
}
