package com.github.orgonag.fbclan.lfg;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Pure parser for the "!lfg" chat command. No I/O and no client types so
 * it can be unit tested directly; dispatch lives in LfgChatCommandHandler.
 *
 * Grammar (case-insensitive, strict keywords - no aliases):
 *   !lfg <event> [note]   -> SET, note capped at LfgService.MAX_NOTE_LENGTH
 *   !lfg off|clear|remove -> CLEAR
 *   !lfg / unknown event  -> HELP
 * Anything not starting with the whole-word trigger is not our command
 * and parses to null.
 */
public final class LfgChatCommand
{
    public enum Action
    {
        SET, CLEAR, HELP
    }

    @Value
    public static class Result
    {
        Action action;

        // Non-null only for SET.
        LfgActivity activity;

        // SET only; null when absent. Trimmed and capped here so the panel
        // can mirror it into the note field (whose DocumentFilter rejects
        // over-length inserts outright rather than truncating them).
        String note;
    }

    // ASCII punctuation only - this string renders in the in-game chat font.
    // Square brackets, not angle brackets: the chat renderer treats <...>
    // as a formatting tag and swallows it.
    public static final String USAGE =
        "Usage: !lfg [Event] [Note] or !lfg off. Events: cox, tob, toa, groupboss, minigame, pvp, skilling, chilling";

    private static final String TRIGGER = "!lfg";

    private static final Map<String, LfgActivity> EVENT_KEYWORDS = new HashMap<>();
    static
    {
        EVENT_KEYWORDS.put("cox", LfgActivity.COX);
        EVENT_KEYWORDS.put("tob", LfgActivity.TOB);
        EVENT_KEYWORDS.put("toa", LfgActivity.TOA);
        EVENT_KEYWORDS.put("groupboss", LfgActivity.GROUP_BOSS);
        EVENT_KEYWORDS.put("minigame", LfgActivity.MINIGAME);
        EVENT_KEYWORDS.put("pvp", LfgActivity.PVP);
        EVENT_KEYWORDS.put("skilling", LfgActivity.SKILLING);
        EVENT_KEYWORDS.put("chilling", LfgActivity.CHILLING);
    }

    private LfgChatCommand()
    {
    }

    // Returns null when the message is not an !lfg command at all.
    public static Result parse(String message)
    {
        if (message == null)
        {
            return null;
        }
        String trimmed = message.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(TRIGGER))
        {
            return null;
        }
        String rest = trimmed.substring(TRIGGER.length());
        if (!rest.isEmpty() && !Character.isWhitespace(rest.charAt(0)))
        {
            // "!lfgsomething" - a different command, not ours.
            return null;
        }
        rest = rest.trim();
        if (rest.isEmpty())
        {
            return new Result(Action.HELP, null, null);
        }

        String keyword;
        String note;
        int split = indexOfWhitespace(rest);
        if (split < 0)
        {
            keyword = rest;
            note = null;
        }
        else
        {
            keyword = rest.substring(0, split);
            note = rest.substring(split).trim();
        }
        keyword = keyword.toLowerCase(Locale.ROOT);

        if (keyword.equals("off") || keyword.equals("clear") || keyword.equals("remove"))
        {
            return new Result(Action.CLEAR, null, null);
        }

        LfgActivity activity = EVENT_KEYWORDS.get(keyword);
        if (activity == null)
        {
            return new Result(Action.HELP, null, null);
        }

        if (note != null)
        {
            if (note.isEmpty())
            {
                note = null;
            }
            else if (note.length() > LfgService.MAX_NOTE_LENGTH)
            {
                note = note.substring(0, LfgService.MAX_NOTE_LENGTH).trim();
            }
        }
        return new Result(Action.SET, activity, note);
    }

    private static int indexOfWhitespace(String s)
    {
        for (int i = 0; i < s.length(); i++)
        {
            if (Character.isWhitespace(s.charAt(i)))
            {
                return i;
            }
        }
        return -1;
    }
}
