package com.github.orgonag.fbclan.pbs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Parsing and formatting for OSRS kill-count / personal-best chat lines.
 * Patterns are copied verbatim from RuneLite core's ChatCommandsPlugin
 * and match the RAW message with colour tags intact. Boss keys are
 * canonicalized exactly like core stores them (renames, colons stripped,
 * lowercased) so seeded and live data share keys.
 */
public final class PbParser
{
    private static final Pattern KILLCOUNT = Pattern.compile("Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?<col=[0-9a-f]{6}>(?<kc>[0-9,]+)</col>");
    private static final String TEAM = "(?<teamsize>\\d+(?:\\+|-\\d+)? players?|Solo)";
    private static final Pattern RAID_NEW_PB = Pattern.compile("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>" + TEAM + "</col> Duration:</col> <col=ff0000>(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col> \\(new personal best\\)</col>");
    private static final Pattern RAID_DURATION = Pattern.compile("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>" + TEAM + "</col> Duration:</col> <col=ff0000>[0-9:.]+</col> Personal best: </col><col=ff0000>(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col>");
    private static final Pattern KILL_DURATION = Pattern.compile("(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|(?<!total )completion time:) <col=[0-9a-f]{6}>[0-9:.]+</col>\\. Personal best: (?:<col=ff0000>)?(?<pb>[0-9:]+(?:\\.[0-9]+)?)");
    private static final Pattern NEW_PB = Pattern.compile("(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|(?<!total )completion time:) <col=[0-9a-f]{6}>(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col> \\(new personal best\\)");
    private static final Pattern SEPULCHRE = Pattern.compile("Floor (?<floor>\\d) time: <col=ff0000>(?<floortime>[0-9:]+(?:\\.[0-9]+)?)</col>(?: \\(new personal best\\)|. Personal best: (?<floorpb>[0-9:]+(?:\\.[0-9]+)?))"
        + "(?:<br>Overall time: <col=ff0000>(?<otime>[0-9:]+(?:\\.[0-9]+)?)</col>(?: \\(new personal best\\)|. Personal best: (?<opb>[0-9:]+(?:\\.[0-9]+)?)))?");
    private static final Pattern TEAM_SUFFIX = Pattern.compile(" (solo|\\d+(?:\\+|-\\d+)? players?)$");
    private static final String[][] RAID_ABBREVIATIONS = {
        {"chambers of xeric challenge mode", "CM"},
        {"chambers of xeric", "COX"},
        {"theatre of blood hard mode", "HMT"},
        {"theatre of blood", "TOB"},
        {"tombs of amascut expert mode", "TOA Expert"},
        {"tombs of amascut", "TOA"},
    };

    private PbParser()
    {
    }

    /** One PB headed for the database. source: "live" = fresh record, "seed" = backfill. */
    @Value
    public static class Submission
    {
        String bossKey;
        double seconds;
        String source;
    }

    /** A duration line: the time, whether it's a fresh record, CoX team size if any. */
    @Value
    public static class Duration
    {
        double seconds;
        boolean newPb;
        String teamSize;
    }

    // KC line -> canonical boss key. Personal-count rows ("Your Glory
    // is: ...") have neither pre nor post and are not boss KCs.
    public static Optional<String> killCount(String message)
    {
        Matcher m = KILLCOUNT.matcher(message);
        if (!m.find())
        {
            return Optional.empty();
        }
        String pre = m.group("pre");
        String post = m.group("post");
        if ((pre == null || pre.isEmpty()) && (post == null || post.isEmpty()))
        {
            return Optional.empty();
        }
        return Optional.of(canonicalKey(m.group("boss")));
    }

    public static Optional<Duration> duration(String message)
    {
        Matcher m = NEW_PB.matcher(message);
        if (m.find())
        {
            return Optional.of(new Duration(seconds(m.group("pb")), true, null));
        }
        m = KILL_DURATION.matcher(message);
        if (m.find())
        {
            return Optional.of(new Duration(seconds(m.group("pb")), false, null));
        }
        m = RAID_NEW_PB.matcher(message);
        if (m.find())
        {
            return Optional.of(new Duration(seconds(m.group("pb")), true, m.group("teamsize")));
        }
        m = RAID_DURATION.matcher(message);
        if (m.find())
        {
            return Optional.of(new Duration(seconds(m.group("pb")), false, m.group("teamsize")));
        }
        return Optional.empty();
    }

    // Hallowed Sepulchre carries its identity in the line itself: floor
    // time always, overall time optionally. A "(new personal best)"
    // segment is live; a restated "Personal best: X" is backfill.
    public static List<Submission> sepulchre(String message)
    {
        List<Submission> out = new ArrayList<>();
        Matcher m = SEPULCHRE.matcher(message);
        if (!m.find())
        {
            return out;
        }
        String floor = "hallowed sepulchre floor " + m.group("floor");
        String floorPb = m.group("floorpb");
        out.add(floorPb != null
            ? new Submission(floor, seconds(floorPb), "seed")
            : new Submission(floor, seconds(m.group("floortime")), "live"));
        String oTime = m.group("otime");
        if (oTime != null)
        {
            String oPb = m.group("opb");
            out.add(oPb != null
                ? new Submission("hallowed sepulchre", seconds(oPb), "seed")
                : new Submission("hallowed sepulchre", seconds(oTime), "live"));
        }
        return out;
    }

    public static String canonicalKey(String boss)
    {
        String renamed = "Barrows chest".equals(boss) ? "Barrows Chests" : boss;
        return renamed.replace(":", "").toLowerCase(Locale.ROOT);
    }

    // Verbatim port of core's timeStringToSeconds.
    public static double seconds(String time)
    {
        String[] s = time.split(":");
        if (s.length == 2)
        {
            return Integer.parseInt(s[0]) * 60 + Double.parseDouble(s[1]);
        }
        if (s.length == 3)
        {
            return Integer.parseInt(s[0]) * 3600 + Integer.parseInt(s[1]) * 60 + Double.parseDouble(s[2]);
        }
        return Double.parseDouble(time);
    }

    // Port of core's secondsToTimeString: whole seconds without ".00".
    public static String formatSeconds(double seconds)
    {
        int hours = (int) (Math.floor(seconds) / 3600);
        int minutes = (int) (Math.floor(seconds / 60) % 60);
        seconds = seconds % 60;
        String prefix = hours > 0 ? String.format("%d:%02d:", hours, minutes) : String.format("%d:", minutes);
        return prefix + (Math.floor(seconds) == seconds
            ? String.format("%02d", (int) seconds)
            : String.format("%05.2f", seconds));
    }

    // "theatre of blood 4 players" -> "TOB (4 players)". Cosmetic only.
    public static String displayName(String bossKey)
    {
        String base = bossKey;
        String suffix = null;
        Matcher m = TEAM_SUFFIX.matcher(bossKey);
        if (m.find())
        {
            base = bossKey.substring(0, m.start());
            suffix = m.group(1).equals("solo") ? "Solo" : m.group(1);
        }
        StringBuilder sb = new StringBuilder();
        for (String[] abbr : RAID_ABBREVIATIONS)
        {
            if (base.equals(abbr[0]))
            {
                sb.append(abbr[1]);
                base = "";
                break;
            }
            if (base.startsWith(abbr[0] + " "))
            {
                sb.append(abbr[1]);
                base = base.substring(abbr[0].length() + 1);
                break;
            }
        }
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
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        if (suffix != null)
        {
            sb.append(" (").append(suffix).append(')');
        }
        return sb.toString();
    }
}
