package com.github.orgonag.fbclan.lfg;

/**
 * Free-text sanitizer for anything user-typed that reaches the database
 * (party descriptions): strips control characters, trims, caps at
 * {@code maxLength}, and returns null for blank input.
 */
public final class LfgText
{
    private LfgText()
    {
    }

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
        if (cleaned.length() > maxLength)
        {
            cleaned = cleaned.substring(0, maxLength).trim();
        }
        return cleaned;
    }
}
