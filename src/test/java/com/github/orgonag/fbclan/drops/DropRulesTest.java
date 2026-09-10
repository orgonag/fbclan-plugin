package com.github.orgonag.fbclan.drops;
import java.util.OptionalDouble;
import org.junit.Test;
import static org.junit.Assert.*;
public class DropRulesTest
{
    @Test public void valueUsesLongAndThresholdHasFloor()
    {
        assertEquals(1000000,DropRules.threshold(-1));
        assertTrue(DropRules.valuable(5000000000L,1000000));
        assertFalse(DropRules.valuable(999999,DropRules.threshold(0)));
    }
    @Test public void unknownRarityAndDisabledRarityDoNotQualify()
    {
        assertFalse(DropRules.rare(OptionalDouble.empty(),128));
        assertFalse(DropRules.rare(OptionalDouble.of(1.0/512),0));
        assertTrue(DropRules.rare(OptionalDouble.of(1.0/512),128));
    }
    @Test public void chestAliasesAndPetMessages()
    {
        assertEquals("The Corrupted Gauntlet",DropRules.displaySource("Corrupted Hunllef"));
        assertTrue(DropRules.CHEST_LOOT_NPCS.contains("The Whisperer"));
        assertTrue(DropRules.CHEST_LOOT_NPCS.contains("Branda the Fire Queen"));
        assertTrue(DropRules.isPetMessage("You feel something weird sneaking into your backpack."));
        assertTrue(DropRules.isDuplicatePet("You have a funny feeling like you would have been followed."));
        assertFalse(DropRules.isPetMessage("An unrelated message"));
    }
}
