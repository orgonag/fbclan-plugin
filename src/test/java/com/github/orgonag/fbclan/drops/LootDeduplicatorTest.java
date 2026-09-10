package com.github.orgonag.fbclan.drops;
import org.junit.Test;
import static org.junit.Assert.*;
public class LootDeduplicatorTest
{
    @Test public void suppressesOnlyMatchingCrossChannelMultiplicity()
    {
        LootDeduplicator d=new LootDeduplicator();
        assertTrue(d.accept(1,"loot",false));
        assertTrue(d.accept(1,"loot",false));
        assertFalse(d.accept(1,"loot",true));
        assertFalse(d.accept(1,"loot",true));
        assertTrue(d.accept(1,"loot",true));
        assertTrue(d.accept(2,"loot",false));
    }
    @Test public void worksWhenTrackerArrivesFirst()
    {
        LootDeduplicator d=new LootDeduplicator();
        assertTrue(d.accept(1,"loot",true));
        assertFalse(d.accept(1,"loot",false));
        assertTrue(d.accept(1,"other",false));
    }
}
