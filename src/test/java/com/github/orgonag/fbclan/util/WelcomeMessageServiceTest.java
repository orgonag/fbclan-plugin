package com.github.orgonag.fbclan.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class WelcomeMessageServiceTest
{
    private static JsonArray rows(String json)
    {
        return new JsonParser().parse(json).getAsJsonArray();
    }

    @Test
    public void testSanitizePassesPlainTextThrough()
    {
        assertEquals("Sign up for bingo!", WelcomeMessageService.sanitize("Sign up for bingo!"));
    }

    @Test
    public void testSanitizeHandlesNullAndBlank()
    {
        assertEquals("", WelcomeMessageService.sanitize(null));
        assertEquals("", WelcomeMessageService.sanitize("   "));
    }

    @Test
    public void testSanitizeStripsTags()
    {
        assertEquals("red text", WelcomeMessageService.sanitize("<col=ff0000>red</col> text"));
        assertEquals("img gone", WelcomeMessageService.sanitize("<img=12>img gone"));
    }

    @Test
    public void testSanitizeRemovesUnclosedAngleBrackets()
    {
        // The unclosed tag's text survives (harmless once '<' is gone);
        // only the angle bracket itself is removed.
        assertEquals("dangling col=ff stuff", WelcomeMessageService.sanitize("dangling <col=ff stuff"));
        assertEquals("a b", WelcomeMessageService.sanitize("a >< b"));
    }

    @Test
    public void testSanitizeCollapsesWhitespace()
    {
        assertEquals("a b c", WelcomeMessageService.sanitize("  a\n\tb   c  "));
    }

    @Test
    public void testSanitizeCapsAt200Chars()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 201; i++)
        {
            sb.append('x');
        }
        String out = WelcomeMessageService.sanitize(sb.toString());
        assertEquals(200, out.length());
        // Exactly 200 stays intact
        assertEquals(200, WelcomeMessageService.sanitize(sb.substring(0, 200)).length());
    }

    @Test
    public void testParseMessageEmptyArray()
    {
        assertEquals("", WelcomeMessageService.parseMessage(rows("[]")));
    }

    @Test
    public void testParseMessageReadsAndSanitizesRow()
    {
        assertEquals("Sign up for bingo!",
            WelcomeMessageService.parseMessage(rows("[{\"message\":\" Sign up for <b>bingo</b>! \"}]")));
    }

    @Test
    public void testParseMessageNullOrMissingMessage()
    {
        assertEquals("", WelcomeMessageService.parseMessage(rows("[{\"message\":null}]")));
        assertEquals("", WelcomeMessageService.parseMessage(rows("[{\"other\":\"x\"}]")));
    }

    @Test
    public void testSanitizeNeverEmitsAngleBrackets()
    {
        // The actual security property: no output may contain chat-markup
        // delimiters, even for nested/adversarial input.
        String[] nasty = {"<co<x>l=ff0000>hi</col>", "<img=<img=12>12>", "<<>><", "<lt><gt>"};
        for (String input : nasty)
        {
            String out = WelcomeMessageService.sanitize(input);
            assertFalse(out, out.contains("<"));
            assertFalse(out, out.contains(">"));
        }
    }
}
