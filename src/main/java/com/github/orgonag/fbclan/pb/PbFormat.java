package com.github.orgonag.fbclan.pb;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display helpers for PB times and boss keys. Pure static. */
public final class PbFormat
{
    // Trailing raid team-size suffix on a canonical boss key, e.g.
    // "... 4 players" / "... solo". "floor 5" deliberately doesn't match.
    private static final Pattern TEAM_SUFFIX =
        Pattern.compile(" (solo|\\d+(?:\\+|-\\d+)? players?)$");

    private PbFormat()
    {
    }

    // Port of core's secondsToTimeString: whole seconds shown without the
    // trailing .00 (a whole-second value is ambiguous between precise and
    // imprecise timing), fractional shown as .XX.
    public static String formatSeconds(double seconds)
    {
        int hours = (int) (Math.floor(seconds) / 3600);
        int minutes = (int) (Math.floor(seconds / 60) % 60);
        seconds = seconds % 60;

        String timeString = hours > 0 ? String.format("%d:%02d:", hours, minutes) : String.format("%d:", minutes);
        return timeString + (Math.floor(seconds) == seconds
            ? String.format("%02d", (int) seconds)
            : String.format("%05.2f", seconds));
    }

    // "theatre of blood 4 players" -> "Theatre Of Blood (4 players)".
    // Purely cosmetic; keys stay canonical everywhere else.
    public static String displayName(String bossKey)
    {
        String base = bossKey;
        String suffix = null;
        Matcher m = TEAM_SUFFIX.matcher(bossKey);
        if (m.find())
        {
            base = bossKey.substring(0, m.start());
            String s = m.group(1);
            suffix = s.equals("solo") ? "Solo" : s;
        }

        StringBuilder sb = new StringBuilder();
        for (String word : base.split(" "))
        {
            if (word.isEmpty())
            {
                continue;
            }
            if (sb.length() > 0)
            {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)))
              .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        if (suffix != null)
        {
            sb.append(" (").append(suffix).append(')');
        }
        return sb.toString();
    }
}
