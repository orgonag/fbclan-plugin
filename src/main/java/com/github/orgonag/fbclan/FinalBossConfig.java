package com.github.orgonag.fbclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("finalboss")
public interface FinalBossConfig extends Config
{
    @ConfigSection(
        name = "Drop Logging",
        description = "Settings for drop logging",
        position = 0
    )
    String dropLoggingSection = "dropLogging";

    @ConfigSection(
        name = "Looking For Group",
        description = "Settings for the LFG panel",
        position = 1
    )
    String lfgSection = "lfg";

    @ConfigSection(
        name = "Discord Integration",
        description = "Settings for Discord webhook",
        position = 2
    )
    String discordSection = "discord";

    // Off by default: no drop data leaves the client unless the user
    // explicitly opts in.
    @ConfigItem(
        keyName = "enableDropLogging",
        name = "Enable Drop Logging",
        description = "Log valuable drops to the clan database (opt-in)",
        section = dropLoggingSection,
        position = 0
    )
    default boolean enableDropLogging()
    {
        return false;
    }

    @Range(min = 0)
    @ConfigItem(
        keyName = "dropThresholdGp",
        name = "Drop Threshold (GP)",
        description = "Minimum GP value for a drop to be logged (and screenshotted, if enabled)",
        section = dropLoggingSection,
        position = 1
    )
    default int dropThresholdGp()
    {
        return 1_000_000;
    }

    @ConfigItem(
        keyName = "enableDropScreenshots",
        name = "Screenshot Drops",
        description = "Capture a full client screenshot for drops above the threshold and store it in the clan database",
        section = dropLoggingSection,
        position = 2
    )
    default boolean enableDropScreenshots()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableLfg",
        name = "Enable LFG",
        description = "Enable the Looking For Group feature",
        section = lfgSection,
        position = 0
    )
    default boolean enableLfg()
    {
        return true;
    }

    @ConfigItem(
        keyName = "discordWebhookUrl",
        name = "Discord Webhook URL",
        description = "Discord webhook URL for drop notifications (leave empty to disable)",
        section = discordSection,
        position = 0
    )
    default String discordWebhookUrl()
    {
        return "";
    }
}
