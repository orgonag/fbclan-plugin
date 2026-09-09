package com.github.orgonag.fbclan.lfg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the local player's own kill counts from RuneLite's profile
 * config, where the core Chat Commands plugin records them every time
 * the game prints "Your ... kill count is: N" (the same store this
 * plugin already reads personal bests from). Instant and current as of
 * the last kill, but only for the local account — so it's what an
 * applicant sends with their application, never what a host looks up.
 */
public class LfgLocalKillcounts
{
    private static final String GROUP = "killcount";

    private final ConfigManager configManager;

    public LfgLocalKillcounts(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    // Null when nothing is recorded (Chat Commands off, never killed it,
    // or not logged in). Dagannoth Kings sums the three kings.
    public Integer read(LfgActivity activity, boolean hardMode)
    {
        if (configManager == null || configManager.getRSProfileKey() == null)
        {
            return null;
        }
        List<String> keys = keysFor(activity, hardMode);
        if (keys.isEmpty())
        {
            return null;
        }
        if (activity == LfgActivity.DKS)
        {
            Integer total = null;
            for (String key : keys)
            {
                Integer kc = get(key);
                if (kc != null)
                {
                    total = (total == null ? 0 : total) + kc;
                }
            }
            return total;
        }
        for (String key : keys)
        {
            Integer kc = get(key);
            if (kc != null)
            {
                return kc;
            }
        }
        return null;
    }

    private Integer get(String bossKey)
    {
        try
        {
            return configManager.getRSProfileConfiguration(GROUP, bossKey.toLowerCase(Locale.ROOT), int.class);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    // Chat Commands stores boss names as they appear in the game message,
    // lower-cased. Where the exact wording is uncertain, alternatives are
    // tried in order.
    static List<String> keysFor(LfgActivity activity, boolean hardMode)
    {
        switch (activity)
        {
            case COX:
                return Collections.singletonList(hardMode ? "Chambers of Xeric Challenge Mode" : "Chambers of Xeric");
            case TOB:
                return Collections.singletonList(hardMode ? "Theatre of Blood Hard Mode" : "Theatre of Blood");
            case TOA:
                return Collections.singletonList(hardMode ? "Tombs of Amascut Expert Mode" : "Tombs of Amascut");
            case KREEARRA:
                return Collections.singletonList("Kree'arra");
            case GRAARDOR:
                return Collections.singletonList("General Graardor");
            case KRIL:
                return Collections.singletonList("K'ril Tsutsaroth");
            case ZILYANA:
                return Collections.singletonList("Commander Zilyana");
            case NEX:
                return Collections.singletonList("Nex");
            case NIGHTMARE:
                return Arrays.asList("Nightmare", "The Nightmare");
            case CORP:
                return Collections.singletonList("Corporeal Beast");
            case DKS:
                return Arrays.asList("Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme");
            case HUEYCOATL:
                return Arrays.asList("The Hueycoatl", "Hueycoatl");
            case YAMA:
                return Collections.singletonList("Yama");
            case ROYAL_TITANS:
                return Arrays.asList("The Royal Titans", "Royal Titans");
            case ZALCANO:
                return Collections.singletonList("Zalcano");
            case GOTR:
                return Collections.singletonList("Guardians of the Rift");
            case WINTERTODT:
                return Collections.singletonList("Wintertodt");
            default:
                return Collections.emptyList();
        }
    }
}
