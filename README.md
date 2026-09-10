# Final Boss Clan Plugin

RuneLite clan tools for Final Boss (Wise Old Man group 1055).
Membership verification is automatic; no Discord sign-in or account linking is required.

## Features

- Clan announcements and welcome messages.
- Configurable valuable, rare, notable and pet drop logging, with optional screenshots and Discord webhook notifications.
- Looking-for-group parties with roles, applications, kill counts, host-added members and formed-party history.
- Personal-best leaderboards, collection-log and combat-achievement stats, weekly WOM and GP summaries, and CA chat badges.

Settings are available in RuneLite's Final Boss configuration panel. Uploads can be disabled individually. Chest loot depends on the core Loot Tracker plugin.

## Build and run

Use JDK 17. The build produces Java 11 bytecode and pins RuneLite 1.12.38.

```sh
./gradlew build
./gradlew run
```

On Windows, use `gradlew.bat`. The single class under `src/test` is the development-client launcher, not a unit-test suite. The JAR under `build/libs` is a plugin artifact, not a standalone RuneLite client.

## External services and data

The plugin connects to Wise Old Man for membership verification, the clan's Supabase backend for clan features, RuneLite's hiscore client for optional LFG kill-count lookups, and an optional user-configured Discord webhook for drop alerts. The WOM lookup sends the player name; clan uploads start only after verification. The webhook URL stays in local RuneLite configuration.

Player identity, kill counts and achievements are client-reported. Database transactions protect consistency but do not prove account ownership. Version 3 requires the clan's v3 backend API; the backend is administered separately from this plugin repository.

Drops are queued locally for retry with stable event IDs (up to 1,000 pending records). Only the matching verified profile sends its queue; disabling logging pauses sends. Screenshots and Discord notifications are best effort and do not block the core drop record. Screenshots include the client frame and may include visible party names.

PB/stat uploads exclude special worlds. New special-world drops are marked and excluded from normal GP totals. Formed-party history lasts seven days; live advertisements expire after 30 minutes without host activity.

## Plugin Hub submission

Follow the [RuneLite Plugin Hub guide](https://github.com/runelite/plugin-hub#submitting-a-plugin). A submission references this public repository and a full commit SHA in the Plugin Hub manifest. Merging this repository does not publish a Plugin Hub update; RuneLite review and acceptance are separate.

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
