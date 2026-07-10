# Final Boss Clan Plugin

A RuneLite plugin for the **Final Boss** OSRS clan: announcements, drop
logging, looking-for-group, and clan personal-best leaderboards in one
side panel. The panel unlocks after clan membership is verified.

## Features

- **Clan Verification** — Verifies membership via the Wise Old Man API
  (Group 1055). Non-members see a locked panel; no data leaves the client
  unverified.
- **Announcements** — Clan-curated long-form announcements written by the
  clan staff (via a Google Sheet synced to the clan database), shown
  newest-first in their own tab.
- **Drop Log** — Logs valuable drops (configurable GP threshold, opt-in)
  to a shared clan database, including raid chest loot (CoX/ToB/ToA —
  requires the core Loot Tracker plugin, enabled by default), pet drops
  (always logged regardless of threshold), and a clan-curated "notable
  items" list (untradeables like Araxyte fang logged regardless of value).
- **Drop Screenshots** — Optional full-client screenshot per logged drop,
  annotated with party member names and viewable from the drop log.
- **Discord Notifications** — Optional webhook for drop alerts.
- **Looking For Group** — Find clan members to group up with, including
  party clustering and an optional note per request (e.g. "HMT NFRZ").
- **PB Leaderboards** — Clan-wide top-3 personal best times for every
  boss, raid (per team size), Gauntlet/Colosseum/Inferno, Wintertodt/
  Tempoross, Hallowed Sepulchre, and agility courses RuneLite tracks,
  plus a "New clan bests" feed of recently broken records. Uploading your
  times is opt-in: new PBs are captured live from chat (mirroring the
  core Chat Commands plugin's detection), and your existing PBs stored by
  RuneLite are seeded once per session so the board is complete from day
  one. Times from Leagues, Deadman, speedrun, and other non-standard
  worlds are never uploaded. Viewing the leaderboard requires no opt-in.
- **Welcome Message** — A clan-curated one-liner shown once per session
  in verified members' chatboxes.
- **Clan Dashboard** — Weekly XP gained and EHB podiums sourced live from
  the clan's Wise Old Man group, a collection-log top 20 and a combat
  achievements top 20 (with tier badges) built from member uploads,
  per-boss kill counts fetched lazily from Wise Old Man group hiscores,
  and a "GP This Week" board summarizing the last 7 days of logged drops
  — all as collapsible sections alongside the PB leaderboards.

## Setup

1. Install from the RuneLite Plugin Hub
2. (Optional) Adjust drop logging / screenshots / PB upload / stats upload
   in plugin settings — drop logging, PB upload, and stats upload are on
   by default for verified members; screenshots remain off by default
3. (Optional) Set a Discord webhook URL for drop notifications

## Configuration

| Setting | Description | Default |
|---|---|---|
| Enable Drop Logging | Log valuable drops to clan database | On |
| Drop Threshold (GP) | Minimum GP value for a drop to be logged/screenshotted (1,000,000 minimum) | 1,000,000 |
| Screenshot Drops | Upload a full client screenshot for drops above the threshold | Off |
| Enable LFG | Enable Looking For Group feature | On |
| LFG Timeout | Minutes before your LFG status expires and is removed (10–720) | 60 |
| Discord Webhook URL | Discord webhook for drop notifications | Empty |
| Upload personal bests | Send your boss PB times (RSN, boss, time) to the clan leaderboard | On |
| Upload collection log & CA | Send your collection log count and combat achievement points to the clan dashboard | On |

## Data & Security

This plugin communicates with a Supabase database. The Supabase **anon
key** is embedded in the source code — this is intentional and safe. All
data access is controlled by **Row Level Security (RLS)** policies and,
for personal bests, a server-side function:

| Table / Bucket | INSERT | SELECT | UPDATE | DELETE |
|---|---|---|---|---|
| `drops` | Allowed | Allowed | Denied | Denied |
| `lfg_entries` | Allowed | Allowed | Allowed | Allowed |
| `drop-screenshots` (storage) | Allowed | Public read | Denied | Denied |
| `notable_items` | Denied | Allowed | Denied | Denied |
| `welcome_message` | Denied | Allowed | Denied | Denied |
| `announcements` | Denied | Allowed | Denied | Denied |
| `personal_bests` | Denied* | Denied* | Denied | Denied |
| `member_stats` | Denied* | Denied* | Denied | Denied |

\* Personal bests have **no direct table access at all**. Writes go
through a `submit_pbs()` database function that only ever *improves* a
member's stored time (a worse or equal submission is a silent no-op), and
reads come from two read-only views (`pb_leaderboard`, top 3 per boss;
`recent_clan_bests`, the latest live records). No client can post a fake
slow time over someone's record, edit another member's rows, or delete
anything.

\* Member stats (collection log count, combat achievement points) also
have **no direct table access**. Writes go through a `submit_stats()`
database function that only ever *improves* a member's stored counters
(a lower resubmission is a silent no-op), and reads come from two
read-only views (`cl_leaderboard`, `ca_leaderboard`, top 20 each).

- **Drop logging, PB upload, and stats upload are on by default for
  verified clan members and can be disabled individually** — screenshots
  remain off by default and must be turned on explicitly
- **Drops are append-only** — no one can modify or delete drop records
  via the API
- **Screenshots are immutable** — uploaded once, never modifiable via the
  anon key; the bucket is public-read so drop log entries can link to
  their screenshot
- **LFG entries are fully managed** — players can set, update, and remove
  their own status; the optional free-text note is capped at 60
  characters both client-side and by a database CHECK constraint
- **PB uploads skip non-standard worlds** — Leagues, Deadman, tournament,
  beta, and speedrun worlds never feed the leaderboard
- `notable_items`, `welcome_message`, and `announcements` are read-only
  clan-curated content, written solely by a clan-staff sync job outside
  this plugin
- A scheduled job runs every minute and deletes LFG entries whose
  configured timeout has elapsed since `updated_at`. Each entry's TTL is
  the lister's "LFG Timeout" setting (10–720 minutes, default 60, bounded
  by a database CHECK constraint) and resets whenever the player sets
  their status again.

The optional Discord webhook URL is stored locally in your RuneLite
config and never sent to the database.

## Building

```bash
./gradlew build
```

## License

BSD 2-Clause — see [LICENSE](LICENSE)
