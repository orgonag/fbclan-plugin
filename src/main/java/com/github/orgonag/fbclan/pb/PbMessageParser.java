package com.github.orgonag.fbclan.pb;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static parsing of OSRS kill-count / personal-best chat messages.
 * All patterns are copied verbatim from RuneLite core ChatCommandsPlugin
 * (master @ 0c6debb) and match the RAW message with colour tags intact —
 * do not strip tags before calling. Boss keys are canonicalized exactly
 * like core stores them: renames applied, colons stripped, lowercased.
 */
public final class PbMessageParser
{
    static final Pattern KILLCOUNT_PATTERN = Pattern.compile("Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?<col=[0-9a-f]{6}>(?<kc>[0-9,]+)</col>");
    private static final String TEAM_SIZES = "(?<teamsize>\\d+(?:\\+|-\\d+)? players?|Solo)";
    static final Pattern RAIDS_PB_PATTERN = Pattern.compile("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>" + TEAM_SIZES + "</col> Duration:</col> <col=ff0000>(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col> \\(new personal best\\)</col>");
    static final Pattern RAIDS_DURATION_PATTERN = Pattern.compile("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>" + TEAM_SIZES + "</col> Duration:</col> <col=ff0000>[0-9:.]+</col> Personal best: </col><col=ff0000>(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col>");
    static final Pattern KILL_DURATION_PATTERN = Pattern.compile("(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|(?<!total )completion time:) <col=[0-9a-f]{6}>[0-9:.]+</col>\\. Personal best: (?:<col=ff0000>)?(?<pb>[0-9:]+(?:\\.[0-9]+)?)");
    static final Pattern NEW_PB_PATTERN = Pattern.compile("(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|(?<!total )completion time:) <col=[0-9a-f]{6}>(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col> \\(new personal best\\)");
    static final Pattern HS_PB_PATTERN = Pattern.compile("Floor (?<floor>\\d) time: <col=ff0000>(?<floortime>[0-9:]+(?:\\.[0-9]+)?)</col>(?: \\(new personal best\\)|. Personal best: (?<floorpb>[0-9:]+(?:\\.[0-9]+)?))" +
        "(?:<br>Overall time: <col=ff0000>(?<otime>[0-9:]+(?:\\.[0-9]+)?)</col>(?: \\(new personal best\\)|. Personal best: (?<opb>[0-9:]+(?:\\.[0-9]+)?)))?");

    private static final Map<String, String> KILLCOUNT_RENAMES = ImmutableMap.of(
        "Barrows chest", "Barrows Chests"
    );

    private PbMessageParser()
    {
    }

    /** Duration line: the PB time, whether it's a fresh record, and the
     *  CoX team size when the message carries one (ToB/ToA don't). */
    public static class Duration
    {
        private final double seconds;
        private final boolean newPb;
        private final String teamSize; // null unless a raids-pattern match

        Duration(double seconds, boolean newPb, String teamSize)
        {
            this.seconds = seconds;
            this.newPb = newPb;
            this.teamSize = teamSize;
        }

        public double getSeconds()
        {
            return seconds;
        }

        public boolean isNewPb()
        {
            return newPb;
        }

        public String getTeamSize()
        {
            return teamSize;
        }
    }

    // KC line carries the boss name; returns the canonical lowercase key.
    // Rows with neither pre nor post are personal-chest openings core
    // ignores for PBs; we ignore them too (they never precede a PB line).
    public static Optional<String> parseKillCount(String message)
    {
        Matcher m = KILLCOUNT_PATTERN.matcher(message);
        if (!m.find())
        {
            return Optional.empty();
        }
        String boss = m.group("boss");
        return Optional.of(canonicalBossKey(boss));
    }

    public static Optional<Duration> parseDuration(String message)
    {
        Matcher m = NEW_PB_PATTERN.matcher(message);
        if (m.find())
        {
            return Optional.of(new Duration(timeStringToSeconds(m.group("pb")), true, null));
        }
        m = KILL_DURATION_PATTERN.matcher(message);
        if (m.find())
        {
            return Optional.of(new Duration(timeStringToSeconds(m.group("pb")), false, null));
        }
        m = RAIDS_PB_PATTERN.matcher(message);
        if (m.find())
        {
            return Optional.of(new Duration(timeStringToSeconds(m.group("pb")), true, m.group("teamsize")));
        }
        m = RAIDS_DURATION_PATTERN.matcher(message);
        if (m.find())
        {
            return Optional.of(new Duration(timeStringToSeconds(m.group("pb")), false, m.group("teamsize")));
        }
        return Optional.empty();
    }

    // Hallowed Sepulchre is self-contained (boss identity is in the line
    // itself, no KC pairing): floor time always present, overall optional.
    // A "(new personal best)" segment leaves the pb group null → live;
    // a restated "Personal best: X" → that value, as backfill (seed).
    public static List<PbSubmission> parseSepulchre(String message)
    {
        List<PbSubmission> result = new ArrayList<>();
        Matcher m = HS_PB_PATTERN.matcher(message);
        if (!m.find())
        {
            return result;
        }
        String floor = m.group("floor");
        String floorPb = m.group("floorpb");
        String floorTime = m.group("floortime");
        if (floorPb != null)
        {
            result.add(new PbSubmission("hallowed sepulchre floor " + floor, timeStringToSeconds(floorPb), "seed"));
        }
        else
        {
            result.add(new PbSubmission("hallowed sepulchre floor " + floor, timeStringToSeconds(floorTime), "live"));
        }
        String oTime = m.group("otime");
        if (oTime != null)
        {
            String oPb = m.group("opb");
            if (oPb != null)
            {
                result.add(new PbSubmission("hallowed sepulchre", timeStringToSeconds(oPb), "seed"));
            }
            else
            {
                result.add(new PbSubmission("hallowed sepulchre", timeStringToSeconds(oTime), "live"));
            }
        }
        return result;
    }

    // Mirrors core: rename map, then strip colons (config keys can't hold
    // them), then lowercase (core lowercases at setPb time).
    public static String canonicalBossKey(String boss)
    {
        return KILLCOUNT_RENAMES.getOrDefault(boss, boss)
            .replace(":", "")
            .toLowerCase(Locale.ROOT);
    }

    // Verbatim port of core's timeStringToSeconds.
    public static double timeStringToSeconds(String timeString)
    {
        String[] s = timeString.split(":");
        if (s.length == 2) // mm:ss
        {
            return Integer.parseInt(s[0]) * 60 + Double.parseDouble(s[1]);
        }
        else if (s.length == 3) // h:mm:ss
        {
            return Integer.parseInt(s[0]) * 60 * 60 + Integer.parseInt(s[1]) * 60 + Double.parseDouble(s[2]);
        }
        return Double.parseDouble(timeString);
    }
}
