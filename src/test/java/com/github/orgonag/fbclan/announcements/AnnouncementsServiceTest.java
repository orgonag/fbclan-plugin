package com.github.orgonag.fbclan.announcements;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnnouncementsServiceTest
{
    private static JsonArray rows(String json)
    {
        return new JsonParser().parse(json).getAsJsonArray();
    }

    @Test
    public void testParseEmptyArray()
    {
        assertTrue(AnnouncementsService.parseAnnouncements(rows("[]")).isEmpty());
    }

    @Test
    public void testParseFullRowPreservesNewlines()
    {
        List<Announcement> list = AnnouncementsService.parseAnnouncements(rows(
            "[{\"posted_at\":\"2026-07-08\",\"title\":\"Bingo\",\"body\":\"Line one\\nLine two\"}]"));
        assertEquals(1, list.size());
        assertEquals("2026-07-08", list.get(0).getDate());
        assertEquals("Bingo", list.get(0).getTitle());
        assertEquals("Line one\nLine two", list.get(0).getBody());
    }

    @Test
    public void testParsePreservesResponseOrder()
    {
        // The Supabase query orders posted_at.desc,sort_order.asc; the parser
        // must not re-sort.
        List<Announcement> list = AnnouncementsService.parseAnnouncements(rows(
            "[{\"posted_at\":\"2026-07-08\",\"title\":\"Newest\",\"body\":\"b\"},"
            + "{\"posted_at\":\"2026-07-01\",\"title\":\"Older\",\"body\":\"b\"}]"));
        assertEquals(2, list.size());
        assertEquals("Newest", list.get(0).getTitle());
        assertEquals("Older", list.get(1).getTitle());
    }

    @Test
    public void testMissingAndNullFieldsBecomeEmptyStrings()
    {
        List<Announcement> list = AnnouncementsService.parseAnnouncements(rows(
            "[{\"posted_at\":null,\"title\":\"T\"}]"));
        assertEquals(1, list.size());
        assertEquals("", list.get(0).getDate());
        assertEquals("T", list.get(0).getTitle());
        assertEquals("", list.get(0).getBody());
    }

    @Test
    public void testSkipsRowsWithNoTitleAndNoBody()
    {
        List<Announcement> list = AnnouncementsService.parseAnnouncements(rows(
            "[{\"posted_at\":\"2026-07-08\",\"title\":\"  \",\"body\":\"\"},"
            + "{\"posted_at\":\"2026-07-08\",\"title\":\"Kept\",\"body\":\"\"}]"));
        assertEquals(1, list.size());
        assertEquals("Kept", list.get(0).getTitle());
    }

    @Test
    public void testCapsTitleAt200AndBodyAt10000()
    {
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < 201; i++)
        {
            title.append('t');
        }
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 10_001; i++)
        {
            body.append('b');
        }
        List<Announcement> list = AnnouncementsService.parseAnnouncements(rows(
            "[{\"posted_at\":\"2026-07-08\",\"title\":\"" + title + "\",\"body\":\"" + body + "\"}]"));
        assertEquals(200, list.get(0).getTitle().length());
        assertEquals(10_000, list.get(0).getBody().length());
    }
}
