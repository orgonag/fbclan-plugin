# Final Boss Clan Plugin

A RuneLite plugin for the **Final Boss** OSRS clan.

## Features

- **Clan Verification** — Verifies membership via the Wise Old Man API (Group 1055)
- **Drop Logging** — Logs valuable drops (1M+ GP) to a shared clan database
- **Discord Notifications** — Optional webhook for drop alerts
- **Looking For Group** — Find clan members to group up with

## Setup

1. Install from the RuneLite Plugin Hub
2. (Optional) Set a Discord webhook URL in plugin settings for drop notifications

## Configuration

| Setting | Description | Default |
|---|---|---|
| Enable Drop Logging | Log valuable drops to clan database | On |
| Enable LFG | Enable Looking For Group feature | On |
| Discord Webhook URL | Discord webhook for drop notifications | Empty |

## Building

```bash
./gradlew build
```

## License

BSD 2-Clause — see [LICENSE](LICENSE)
