package com.github.orgonag.fbclan.lfg;

import org.junit.Test;
import static org.junit.Assert.*;

public class LfgChatCommandTest
{
    @Test
    public void testNonCommandReturnsNull()
    {
        assertNull(LfgChatCommand.parse(null));
        assertNull(LfgChatCommand.parse(""));
        assertNull(LfgChatCommand.parse("hello there"));
        assertNull(LfgChatCommand.parse("lfg tob"));
        // Trigger must be a whole word - "!lfgx" is not our command
        assertNull(LfgChatCommand.parse("!lfgx tob"));
    }

    @Test
    public void testBareTriggerListsParties()
    {
        assertEquals(LfgChatCommand.Action.PARTIES, LfgChatCommand.parse("!lfg").getAction());
        assertEquals(LfgChatCommand.Action.PARTIES, LfgChatCommand.parse("  !LFG   ").getAction());
    }

    @Test
    public void testPartiesKeywords()
    {
        assertEquals(LfgChatCommand.Action.PARTIES, LfgChatCommand.parse("!lfg parties").getAction());
        assertEquals(LfgChatCommand.Action.PARTIES, LfgChatCommand.parse("!lfg party").getAction());
        assertEquals(LfgChatCommand.Action.PARTIES, LfgChatCommand.parse("!lfg Parties please").getAction());
    }

    @Test
    public void testUnknownKeywordsAreHelp()
    {
        // The command is read-only: anything that isn't a listing request
        // just prints usage rather than doing something.
        assertEquals(LfgChatCommand.Action.HELP, LfgChatCommand.parse("!lfg who").getAction());
        assertEquals(LfgChatCommand.Action.HELP, LfgChatCommand.parse("!lfg tob need 2").getAction());
        assertEquals(LfgChatCommand.Action.HELP, LfgChatCommand.parse("!lfg cox").getAction());
        assertEquals(LfgChatCommand.Action.HELP, LfgChatCommand.parse("!lfg off").getAction());
    }

    @Test
    public void testUsageMentionsCommand()
    {
        assertTrue(LfgChatCommand.USAGE.contains("!lfg"));
        assertTrue(LfgChatCommand.USAGE.contains("parties"));
    }
}
