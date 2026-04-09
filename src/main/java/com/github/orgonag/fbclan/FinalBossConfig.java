package com.github.orgonag.fbclan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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

    @ConfigItem(
        keyName = "enableDropLogging",
        name = "Enable Drop Logging",
        description = "Log valuable drops to the clan database",
        section = dropLoggingSection,
        position = 0
    )
    default boolean enableDropLogging()
    {
        return true;
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
