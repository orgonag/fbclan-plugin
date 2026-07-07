# Final Boss Clan Plugin

A RuneLite plugin for the **Final Boss** OSRS clan.

## Features

- **Clan Verification** — Verifies membership via the Wise Old Man API (Group 1055)
- **Drop Logging** — Logs valuable drops (configurable GP threshold, opt-in) to a shared clan database, including raid chest loot (CoX/ToB/ToA — requires the core Loot Tracker plugin, enabled by default) and pet drops (always logged regardless of threshold)
- **Drop Screenshots** — Optional full-client screenshot per logged drop, annotated with party member names and viewable from the drop log
- **Discord Notifications** — Optional webhook for drop alerts
- **Looking For Group** — Find clan members to group up with, including party clustering and an optional note per request (e.g. "HMT NFRZ")

## Setup

1. Install from the RuneLite Plugin Hub
2. (Optional) Set a Discord webhook URL in plugin settings for drop notifications

## Configuration

| Setting | Description | Default |
|---|---|---|
| Enable Drop Logging | Log valuable drops to clan database (opt-in) | Off |
| Drop Threshold (GP) | Minimum GP value for a drop to be logged/screenshotted (1,000,000 minimum) | 1,000,000 |
| Screenshot Drops | Upload a full client screenshot for drops above the threshold | Off |
| Enable LFG | Enable Looking For Group feature | On |
| LFG Timeout | Minutes before your LFG status expires and is removed (10–720) | 60 |
| Discord Webhook URL | Discord webhook for drop notifications | Empty |

## Data & Security

This plugin communicates with a Supabase database to store drop logs and LFG entries. The Supabase **anon key** is embedded in the source code — this is intentional and safe. All data access is controlled by **Row Level Security (RLS)** policies:

| Table / Bucket | INSERT | SELECT | UPDATE | DELETE |
|---|---|---|---|---|
| `drops` | Allowed | Allowed | Denied | Denied |
| `lfg_entries` | Allowed | Allowed | Allowed | Allowed |
| `drop-screenshots` (storage) | Allowed | Public read | Denied | Denied |

- **Drop logging and screenshots are opt-in** — both config toggles default to off, so no drop data leaves the client unless the user enables it
- **Drops are append-only** — no one can modify or delete drop records via the API
- **Screenshots are immutable** — uploaded once, never modifiable via the anon key; the bucket is public-read so drop log entries can link to their screenshot
- **LFG entries are fully managed** — players can set, update, and remove their own status; the optional free-text note is capped at 60 characters both client-side and by a database CHECK constraint
- A scheduled job runs every minute and deletes LFG entries whose configured timeout has elapsed since `updated_at`. Each entry's TTL is the lister's "LFG Timeout" setting (10–720 minutes, default 60, bounded by a database CHECK constraint) and resets whenever the player sets their status again.

The optional Discord webhook URL is stored locally in your RuneLite config and never sent to the database.

## Building

```bash
./gradlew build
```

## License

BSD 2-Clause — see [LICENSE](LICENSE)
