package com.github.orgonag.fbclan.pbs;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display helpers for PB boss keys (as RuneLite's Chat Commands stores them) and times. */
public final class PbFormat
{
    private static final Pattern TEAM_SUFFIX = Pattern.compile(" (solo|\\d+(?:\\+|-\\d+)? players?)$");
    private static final String[][] RAID_ABBREVIATIONS = {
        {"chambers of xeric challenge mode", "CM"},
        {"chambers of xeric", "COX"},
        {"theatre of blood hard mode", "HMT"},
        {"theatre of blood", "TOB"},
        {"tombs of amascut expert mode", "TOA Expert"},
        {"tombs of amascut", "TOA"},
    };

    private PbFormat()
    {
    }

    // Port of core's secondsToTimeString: whole seconds without ".00".
    public static String seconds(double seconds)
    {
        int hours = (int) (Math.floor(seconds) / 3600);
        int minutes = (int) (Math.floor(seconds / 60) % 60);
        seconds = seconds % 60;
        String prefix = hours > 0 ? String.format(Locale.ROOT, "%d:%02d:", hours, minutes) : String.format(Locale.ROOT, "%d:", minutes);
        return prefix + (Math.floor(seconds) == seconds
            ? String.format(Locale.ROOT, "%02d", (int) seconds)
            : String.format(Locale.ROOT, "%05.2f", seconds));
    }

    // "theatre of blood 4 players" -> "TOB (4 players)".
    public static String boss(String bossKey)
    {
        String base = bossKey.toLowerCase(Locale.ROOT);
        String suffix = null;
        Matcher m = TEAM_SUFFIX.matcher(base);
        if (m.find())
        {
            suffix = m.group(1).equals("solo") ? "Solo" : m.group(1);
            base = base.substring(0, m.start());
        }
        StringBuilder sb = new StringBuilder();
        for (String[] abbr : RAID_ABBREVIATIONS)
        {
            if (base.equals(abbr[0]) || base.startsWith(abbr[0] + " "))
            {
                sb.append(abbr[1]);
                base = base.substring(abbr[0].length()).trim();
                break;
            }
        }
        for (String word : base.split(" "))
        {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        if (suffix != null) sb.append(" (").append(suffix).append(')');
        return sb.toString();
    }
}
