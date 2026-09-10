# Final Boss Clan Plugin

A RuneLite plugin for the **Final Boss OSRS clan**, bringing clan news, drop sharing, group finding and leaderboards into one side panel.

The panel unlocks automatically after your character is verified against **Wise Old Man group 1055**. No Discord login, account linking or database setup is required for clan members.

## Getting started

1. Enable **Final Boss** in RuneLite's plugin settings once the plugin is installed.
2. Log into your clan character and open the Final Boss sidebar icon.
3. Wait for membership verification, then use the **Announcements**, **Drop Log**, **LFG** and **PBs** tabs.
4. Open the plugin's configuration to choose which uploads and notifications you want enabled.

If verification fails temporarily, use **Retry** in the panel. Your character must be listed in the clan's Wise Old Man group.

## Announcements

Read clan announcements in their own tab and receive the clan welcome message in chat. This content is maintained by clan staff.

## Drop Log

Share qualifying loot with the clan automatically. Drops can qualify through:

- **Value:** GE price multiplied by quantity meets your configured threshold.
- **Rarity:** a known drop rate meets your rarity threshold and the drop meets the rare-drop minimum value.
- **Notable items:** the clan's curated list includes the item, including selected untradeables.
- **Pets:** supported pet and duplicate-pet chat messages are logged regardless of GP value.

The log shows the player, item, source, GP value, time and known rarity. Clue scrolls, long/curved bones, champion scrolls and keys are excluded by the automatic value/rarity rules; an explicit clan notable-item entry can override that exclusion.

NPC kills and supported raid/reward chests are covered. Keep RuneLite's core **Loot Tracker** enabled for chest/event loot, including raids and Gauntlet-style rewards. Unknown drop rates can still qualify through value or the notable list.

**Optional screenshots** capture the full client frame and can include RuneLite party member names. Uploaded screenshots can be opened from the drop log. **Optional Discord alerts** send drop notifications to a webhook you supply; leave the URL blank to disable them.

Drops are queued locally for retry during temporary connection failures. Screenshots and Discord alerts are best effort: their failure does not prevent the core drop record from being submitted.

## Looking For Group

Find clanmates for raids, God Wars bosses, group bosses and minigames, or create a general PvP, skilling or social group.

**Hosts can:**

- Choose an activity, party size, world, description and loot rule.
- Set CM/HMT or ToA invocation where applicable, and learner/teacher tags for raids.
- Choose roles for ToB/HMT, CoX/CM and Barbarian Assault parties.
- Display a preferred minimum kill count.
- Accept or decline applications, remove members, add a buddy by name, edit the party or disband it.

**Applicants can** browse and filter parties, choose an available role, mark themselves as a learner and submit their kill count. Kill counts may come from local RuneLite records, public hiscores or manual entry; the panel labels the source. The host's minimum KC is advisory—the host decides who to accept.

When the final seat is filled, the party moves into **formed-party history**, with its roster and world. History is kept for seven days. Live advertisements expire after 30 minutes without host activity.

Chat notifications report relevant applications and party changes; desktop notifications are optional. Typing **`!lfg`** lists open parties in your own chatbox. Hosting and applications are handled through the panel.

## PBs and clan leaderboards

The **PBs** tab includes more than personal-best times:

- **All-Time PBs:** the clan's top three times per tracked boss/activity, including supported raid team-size variants.
- **New Clan Bests:** recent live-sourced records that currently lead the clan.
- **Collection Log:** the clan's top 20 uploaded collection-log counts.
- **Combat Achievements:** the clan's top 20 uploaded CA point totals and tiers.
- **Weekly XP and EHB:** Wise Old Man gains, with EHB representing efficient bossing hours.
- **GP This Week:** logged drop value over the last seven days and the leading contributors.

With PB uploads enabled, the plugin reads supported completion messages and backfills personal bests already stored by RuneLite. A slower time does not replace a faster one. Collection-log and CA statistics are submitted as they become available.

PB and stat uploads are skipped on special worlds such as Leagues and Deadman. Newly recorded special-world drops are excluded from normal GP totals; older records may have unknown world provenance. Weekly WOM results depend on the clan's backend sync.

## CA chat badges

Show slayer-helmet icons beside eligible clan members' names in supported chat channels:

| CA tier | Icon |
|---|---|
| Elite | Tztok slayer helmet |
| Master | Vampyric slayer helmet |
| Grandmaster | Tzkal slayer helmet |

Badges use uploaded CA tiers and can be disabled independently of stat uploads.

## Default settings

| Setting | Default |
|---|---|
| Drop logging | On |
| Valuable-drop threshold | 1,000,000 GP; cannot be set lower |
| Rare-drop threshold | 1 in 100 or rarer; set to 0 to disable this rule |
| Rare-drop minimum value | 100,000 GP |
| Drop screenshots | Off |
| LFG and kill-count lookups | On |
| Party chat notifications | On |
| Party desktop notifications | Off |
| Discord webhook | Blank / disabled |
| PB and collection-log/CA uploads | On |
| CA chat badges | On |

## External services and privacy

The plugin uses **Wise Old Man** for membership verification, the clan's **Supabase** backend for shared features, RuneLite's **hiscore client** for optional KC lookups, and **Discord** only when a webhook is configured.

The membership lookup sends your character name. Clan uploads begin only after verification and can be disabled in settings. Depending on enabled features, shared records include your RSN, loot, PB times, collection-log/CA statistics and LFG details. Screenshots include whatever is visible in the captured client frame and are stored in a public screenshot bucket; enable them only if you want to share that image. The Discord webhook URL stays in your local RuneLite configuration.

Player names, achievements and kill counts are client-reported. The plugin is a clan coordination and sharing tool, not independent verification of those claims.

## Building from source

Use **JDK 17**. The build targets Java 11 bytecode.

```sh
./gradlew build
./gradlew run
```

On Windows, use `gradlew.bat`. The `run` task launches a RuneLite development client with Final Boss loaded. The JAR under `build/libs` is a plugin artifact, not a standalone client. The single class under `src/test` is the development launcher.

Version 3 connects to the clan's v3 backend, which is maintained separately. Availability through RuneLite's Plugin Hub requires RuneLite's separate review and acceptance.

## Credits

Item drop rates (`npc_drops.json`) are sourced from the
[OSRS Wiki](https://oldschool.runescape.wiki/) (licensed under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/)),
parsed by [Flipping Utilities](https://github.com/Flipping-Utilities/parsed-osrs)
and transformed into the bundled format by the
[Dink](https://github.com/pajlads/DinkPlugin) plugin (BSD 2-Clause), whose
rarity lookup this plugin mirrors.

## License

BSD 2-Clause — see [LICENSE](LICENSE)
