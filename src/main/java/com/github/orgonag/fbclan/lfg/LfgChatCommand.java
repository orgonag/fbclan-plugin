package com.github.orgonag.fbclan.lfg;

import java.util.Locale;
import lombok.Value;

/**
 * Pure parser for the "!lfg" chat command. No I/O and no client types so
 * it can be unit tested directly; dispatch lives in LfgChatCommandHandler.
 *
 * The command is read-only — it never sets or clears anything. Setting a
 * status, hosting, and applying are panel-only actions.
 *
 * Grammar (case-insensitive):
 *   !lfg | !lfg who     -> WHO (how many are looking, per event)
 *   !lfg parties|party  -> PARTIES (open hosted parties)
 *   !lfg anything-else  -> HELP
 * Anything not starting with the whole-word trigger is not our command
 * and parses to null.
 */
public final class LfgChatCommand
{
    public enum Action
    {
        WHO, PARTIES, HELP
    }

    @Value
    public static class Result
    {
        Action action;
    }

    // ASCII punctuation only - this renders in the in-game chat font.
    public static final String USAGE =
        "Usage: !lfg (who's looking, per event) or !lfg parties (open parties). Use the Final Boss panel to set your status or host.";

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
            return new Result(Action.WHO);
        }
        String keyword = rest.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (keyword.equals("who"))
        {
            return new Result(Action.WHO);
        }
        if (keyword.equals("parties") || keyword.equals("party"))
        {
            return new Result(Action.PARTIES);
        }
        return new Result(Action.HELP);
    }
}
