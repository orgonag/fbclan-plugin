package com.github.orgonag.fbclan;

import com.github.orgonag.fbclan.drops.DropTrackingService;
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
        description = "Settings for the LFG party board",
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
        name = "Valuable drop threshold (GP)",
        description = "Any drop worth at least this much (GE price x quantity) is logged, rare or not — 1m minimum",
        section = dropLoggingSection,
        position = 1
    )
    default int dropThresholdGp()
    {
        return 1_000_000;
    }

    @Range(min = 0, max = 1_000_000)
    @ConfigItem(
        keyName = "rareDropThreshold",
        name = "Rare drop threshold (1 in X)",
        description = "Log drops whose drop rate is 1 in X or rarer, even below the valuable threshold. "
            + "100 = 1% or rarer. 0 turns the rarity rule off. Uses the OSRS Wiki drop table; "
            + "drops it doesn't cover only qualify by value or the clan's notable list.",
        section = dropLoggingSection,
        position = 2
    )
    default int rareDropThreshold()
    {
        return 100;
    }

    @Range(min = 0)
    @ConfigItem(
        keyName = "rareDropMinValueGp",
        name = "Rare drop min value (GP)",
        description = "A rare drop must also be worth at least this much to be logged, so 1/128 rune junk "
            + "stays out. Set to 0 to log every rare drop regardless of value (untradeables included).",
        section = dropLoggingSection,
        position = 3
    )
    default int rareDropMinValueGp()
    {
        return 100_000;
    }

    @ConfigItem(
        keyName = "enableDropScreenshots",
        name = "Screenshot Drops",
        description = "Capture a full client screenshot for drops above the threshold and store it in the clan database",
        section = dropLoggingSection,
        position = 4
    )
    default boolean enableDropScreenshots()
    {
        return false;
    }

    @ConfigItem(
        keyName = "enableLfg",
        name = "Enable LFG",
        description = "Enable the Looking For Group party board and the !lfg chat command",
        section = lfgSection,
        position = 0
    )
    default boolean enableLfg()
    {
        return true;
    }

    @ConfigItem(
        keyName = "lfgPartyNotifications",
        name = "Party chat notifications",
        description = "Post LFG party events to your chatbox: applicants to your party, "
            + "your application being accepted or declined, and parties you're in forming or being disbanded",
        section = lfgSection,
        position = 1
    )
    default boolean lfgPartyNotifications()
    {
        return true;
    }

    @ConfigItem(
        keyName = "lfgDesktopNotifications",
        name = "Party desktop notifications",
        description = "Also send a desktop notification for LFG party events (uses RuneLite's notification settings)",
        section = lfgSection,
        position = 2
    )
    default boolean lfgDesktopNotifications()
    {
        return false;
    }

    @ConfigItem(
        keyName = "lfgKcLookups",
        name = "Kill count lookups",
        description = "Look up kill counts on the public OSRS hiscores when an applicant's client didn't "
            + "send one, and to prefill your own on the apply form",
        section = lfgSection,
        position = 3
    )
    default boolean lfgKcLookups()
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
}
