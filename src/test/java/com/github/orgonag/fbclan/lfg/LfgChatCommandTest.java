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
    public void testBareTriggerIsHelp()
    {
        LfgChatCommand.Result result = LfgChatCommand.parse("!lfg");
        assertNotNull(result);
        assertEquals(LfgChatCommand.Action.HELP, result.getAction());

        result = LfgChatCommand.parse("  !lfg   ");
        assertNotNull(result);
        assertEquals(LfgChatCommand.Action.HELP, result.getAction());
    }

    @Test
    public void testUnknownKeywordIsHelp()
    {
        LfgChatCommand.Result result = LfgChatCommand.parse("!lfg raids need 2");
        assertNotNull(result);
        assertEquals(LfgChatCommand.Action.HELP, result.getAction());
    }

    @Test
    public void testAllEventKeywordsMap()
    {
        assertEquals(LfgActivity.COX, LfgChatCommand.parse("!lfg cox").getActivity());
        assertEquals(LfgActivity.TOB, LfgChatCommand.parse("!lfg tob").getActivity());
        assertEquals(LfgActivity.TOA, LfgChatCommand.parse("!lfg toa").getActivity());
        assertEquals(LfgActivity.GROUP_BOSS, LfgChatCommand.parse("!lfg groupboss").getActivity());
        assertEquals(LfgActivity.MINIGAME, LfgChatCommand.parse("!lfg minigame").getActivity());
        assertEquals(LfgActivity.PVP, LfgChatCommand.parse("!lfg pvp").getActivity());
        assertEquals(LfgActivity.SKILLING, LfgChatCommand.parse("!lfg skilling").getActivity());
        assertEquals(LfgActivity.CHILLING, LfgChatCommand.parse("!lfg chilling").getActivity());
    }

    @Test
    public void testSetActionAndCaseInsensitivity()
    {
        LfgChatCommand.Result result = LfgChatCommand.parse("!LFG ToB");
        assertNotNull(result);
        assertEquals(LfgChatCommand.Action.SET, result.getAction());
        assertEquals(LfgActivity.TOB, result.getActivity());
        assertNull(result.getNote());
    }

    @Test
    public void testNoteExtraction()
    {
        LfgChatCommand.Result result = LfgChatCommand.parse("!lfg tob need 2 for HMT");
        assertEquals(LfgChatCommand.Action.SET, result.getAction());
        assertEquals(LfgActivity.TOB, result.getActivity());
        assertEquals("need 2 for HMT", result.getNote());
    }

    @Test
    public void testBlankNoteIsNull()
    {
        LfgChatCommand.Result result = LfgChatCommand.parse("!lfg tob   ");
        assertEquals(LfgChatCommand.Action.SET, result.getAction());
        assertNull(result.getNote());
    }

    @Test
    public void testNoteCappedAtMaxLength()
    {
        StringBuilder longNote = new StringBuilder();
        for (int i = 0; i < 10; i++)
        {
            longNote.append("0123456789");
        }
        LfgChatCommand.Result result = LfgChatCommand.parse("!lfg cox " + longNote);
        assertEquals(LfgService.MAX_NOTE_LENGTH, result.getNote().length());
    }

    @Test
    public void testClearKeywords()
    {
        assertEquals(LfgChatCommand.Action.CLEAR, LfgChatCommand.parse("!lfg off").getAction());
        assertEquals(LfgChatCommand.Action.CLEAR, LfgChatCommand.parse("!lfg clear").getAction());
        assertEquals(LfgChatCommand.Action.CLEAR, LfgChatCommand.parse("!lfg remove").getAction());
        assertEquals(LfgChatCommand.Action.CLEAR, LfgChatCommand.parse("!lfg OFF").getAction());
        // Trailing text after a clear keyword is tolerated and ignored
        assertEquals(LfgChatCommand.Action.CLEAR, LfgChatCommand.parse("!lfg off thanks all").getAction());
    }
}
