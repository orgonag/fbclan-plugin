package com.github.orgonag.fbclan.core;

import java.util.Locale;
import net.runelite.client.util.Text;

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

    // A name from the database, with chat tags removed: the chatbox and
    // tooltips render <col=> and <img=>, and the server accepts any name.
    public static String untagged(String name)
    {
        return name == null ? "" : Text.removeTags(name).trim();
    }

    // Case/whitespace-insensitive key for player and item names.
    public static String normalize(String name)
    {
        return name == null ? "" : name.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
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
}
