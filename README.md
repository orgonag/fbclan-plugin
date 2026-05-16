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

## Data & Security

This plugin communicates with a Supabase database to store drop logs and LFG entries. The Supabase **anon key** is embedded in the source code — this is intentional and safe. All data access is controlled by **Row Level Security (RLS)** policies:

| Table | INSERT | SELECT | UPDATE | DELETE |
|---|---|---|---|---|
| `drops` | Allowed | Allowed | Denied | Denied |
| `lfg_entries` | Allowed | Allowed | Allowed | Allowed |

- **Drops are append-only** — no one can modify or delete drop records via the API
- **LFG entries are fully managed** — players can set, update, and remove their own status
- A scheduled job runs every minute and deletes LFG entries whose `updated_at` is older than 60 minutes. Each entry effectively has a 60-minute TTL that resets whenever the player sets or updates their status.

The optional Discord webhook URL is stored locally in your RuneLite config and never sent to the database.

## Building

```bash
./gradlew build
```

## License

BSD 2-Clause — see [LICENSE](LICENSE)
