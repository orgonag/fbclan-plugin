package com.github.orgonag.fbclan;

import com.github.orgonag.fbclan.drops.DropTrackingService;
import com.github.orgonag.fbclan.lfg.LfgService;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

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

    @ConfigSection(
        name = "Leaderboards",
        description = "Clan leaderboards settings",
        position = 3
    )
    String leaderboardsSection = "leaderboards";

    @ConfigItem(
        keyName = "enableDropLogging",
        name = "Enable Drop Logging",
        description = "Log valuable drops to the clan database. On by default for verified clan members — disable to opt out.",
        section = dropLoggingSection,
        position = 0
    )
    default boolean enableDropLogging()
    {
        return true;
    }

    @Range(min = DropTrackingService.MIN_THRESHOLD_GP)
    @ConfigItem(
        keyName = "dropThresholdGp",
        name = "Drop Threshold (GP)",
        description = "Minimum GP value for a drop to be logged (and screenshotted, if enabled) — 1m minimum",
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

    @Range(min = LfgService.MIN_TTL_MINUTES, max = LfgService.MAX_TTL_MINUTES)
    @ConfigItem(
        keyName = "lfgTimeoutMinutes",
        name = "LFG Timeout",
        description = "How long your LFG status stays up before it expires and is removed automatically",
        section = lfgSection,
        position = 1
    )
    @Units(Units.MINUTES)
    default int lfgTimeoutMinutes()
    {
        return 240;
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

    @ConfigItem(
        keyName = "enablePbUpload",
        name = "Upload personal bests",
        description = "Send your boss personal-best times (RSN, boss name, time) to the clan's leaderboard database. "
            + "On by default — disable to opt out. Skipped on Leagues/Deadman/speedrun worlds.",
        section = leaderboardsSection,
        position = 0
    )
    default boolean enablePbUpload()
    {
        return true;
    }

    @ConfigItem(
        keyName = "enableChatBadges",
        name = "CA slayer helm chat icons",
        description = "Show the Tztok/Vampyric/Tzkal slayer helmet next to clan members' names in chat "
            + "for Elite/Master/Grandmaster combat achievement tiers",
        section = leaderboardsSection,
        position = 2
    )
    default boolean enableChatBadges()
    {
        return true;
    }

    @ConfigItem(
        keyName = "enableStatsUpload",
        name = "Upload collection log & CA",
        description = "Send your collection log count and combat achievement points (with your RSN) "
            + "to the clan's dashboard. On by default — disable to opt out. "
            + "Skipped on Leagues/Deadman/speedrun worlds.",
        section = leaderboardsSection,
        position = 1
    )
    default boolean enableStatsUpload()
    {
        return true;
    }
}
