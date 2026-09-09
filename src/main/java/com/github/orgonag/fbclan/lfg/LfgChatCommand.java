package com.github.orgonag.fbclan.lfg;

import java.util.Locale;
import lombok.Value;

/**
 * Pure parser for the "!lfg" chat command. No I/O and no client types so
 * it can be unit tested directly; dispatch lives in LfgChatCommandHandler.
 *
 * Grammar (case-insensitive; event keywords and aliases live on LfgActivity):
 *   !lfg <event> [note]   -> SET, note capped at LfgService.MAX_NOTE_LENGTH
 *   !lfg off|clear|remove -> CLEAR
 *   !lfg who              -> WHO (who's looking, per event)
 *   !lfg parties|party    -> PARTIES (open hosted parties)
 *   !lfg / unknown event  -> HELP
 * Anything not starting with the whole-word trigger is not our command
 * and parses to null.
 */
public final class LfgChatCommand
{
    public enum Action
    {
        SET, CLEAR, WHO, PARTIES, HELP
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

    // ASCII punctuation only - these strings render in the in-game chat
    // font. Square brackets, not angle brackets: the chat renderer treats
    // <...> as a formatting tag and swallows it.
    public static final String USAGE =
        "Usage: !lfg [Event] [Note], !lfg who, !lfg parties, or !lfg off.";
    public static final String EVENTS = "Events: " + eventKeywords();

    private static final String TRIGGER = "!lfg";

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
        if (keyword.equals("who"))
        {
            return new Result(Action.WHO, null, null);
        }
        if (keyword.equals("parties") || keyword.equals("party"))
        {
            return new Result(Action.PARTIES, null, null);
        }

        LfgActivity activity = LfgActivity.fromKeyword(keyword);
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

    private static String eventKeywords()
    {
        StringBuilder sb = new StringBuilder();
        for (LfgActivity activity : LfgActivity.values())
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(activity.getKeyword());
        }
        return sb.toString();
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
