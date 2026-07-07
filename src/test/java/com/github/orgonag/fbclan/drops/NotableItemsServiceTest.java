package com.github.orgonag.fbclan.drops;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotableItemsServiceTest
{
    private static JsonArray rows(String json)
    {
        return new JsonParser().parse(json).getAsJsonArray();
    }

    @Test
    public void testParseNamesNormalizes()
    {
        Set<String> names = NotableItemsService.parseNames(
            rows("[{\"name\":\"Araxyte Fang\"},{\"name\":\"  elder venator fang \"}]"));
        assertEquals(2, names.size());
        assertTrue(names.contains("araxyte fang"));
        assertTrue(names.contains("elder venator fang"));
    }

    @Test
    public void testParseNamesSkipsBlankNullAndMissing()
    {
        Set<String> names = NotableItemsService.parseNames(
            rows("[{\"name\":\"\"},{\"name\":null},{\"other\":\"x\"},{\"name\":\"Dark claw\"}]"));
        assertEquals(1, names.size());
        assertTrue(names.contains("dark claw"));
    }

    @Test
    public void testParseNamesEmptyArray()
    {
        assertTrue(NotableItemsService.parseNames(rows("[]")).isEmpty());
    }

    @Test
    public void testParseNamesCollapsesDuplicates()
    {
        Set<String> names = NotableItemsService.parseNames(
            rows("[{\"name\":\"Araxyte fang\"},{\"name\":\"ARAXYTE FANG\"}]"));
        assertEquals(1, names.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testParseNamesReturnsImmutableSet()
    {
        NotableItemsService.parseNames(rows("[{\"name\":\"Araxyte fang\"}]")).add("x");
    }
}
