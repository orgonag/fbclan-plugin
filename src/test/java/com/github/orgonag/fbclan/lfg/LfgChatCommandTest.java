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
    public void testBareTriggerIsWho()
    {
        assertEquals(LfgChatCommand.Action.WHO, LfgChatCommand.parse("!lfg").getAction());
        assertEquals(LfgChatCommand.Action.WHO, LfgChatCommand.parse("  !LFG   ").getAction());
    }

    @Test
    public void testWhoKeyword()
    {
        assertEquals(LfgChatCommand.Action.WHO, LfgChatCommand.parse("!lfg who").getAction());
        assertEquals(LfgChatCommand.Action.WHO, LfgChatCommand.parse("!lfg WHO").getAction());
        // Trailing text after who is tolerated and ignored
        assertEquals(LfgChatCommand.Action.WHO, LfgChatCommand.parse("!lfg who is on").getAction());
    }

    @Test
    public void testPartiesKeyword()
    {
        assertEquals(LfgChatCommand.Action.PARTIES, LfgChatCommand.parse("!lfg parties").getAction());
        assertEquals(LfgChatCommand.Action.PARTIES, LfgChatCommand.parse("!lfg party").getAction());
        assertEquals(LfgChatCommand.Action.PARTIES, LfgChatCommand.parse("!lfg Parties please").getAction());
    }

    @Test
    public void testOldSetAndClearFormsAreHelp()
    {
        // Setting/clearing from chat was removed: these now just print usage.
        assertEquals(LfgChatCommand.Action.HELP, LfgChatCommand.parse("!lfg tob need 2").getAction());
        assertEquals(LfgChatCommand.Action.HELP, LfgChatCommand.parse("!lfg cox").getAction());
        assertEquals(LfgChatCommand.Action.HELP, LfgChatCommand.parse("!lfg off").getAction());
        assertEquals(LfgChatCommand.Action.HELP, LfgChatCommand.parse("!lfg raids need 2").getAction());
    }

    @Test
    public void testUsageMentionsBothCommands()
    {
        assertTrue(LfgChatCommand.USAGE.contains("!lfg"));
        assertTrue(LfgChatCommand.USAGE.contains("parties"));
    }
}
