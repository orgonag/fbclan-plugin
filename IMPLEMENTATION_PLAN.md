# Final Boss plugin — implementation and migration plan

10 September 2026 • Baseline: `orgonag/fbclan-plugin` main, `400acbe058700be032c0778c62cb6704651afb31`

## Outcome and agreed scope

Deliver a complete, maintainable Java implementation of the existing Final Boss feature set, a complete Supabase SQL package, automated tests, and an integration branch for RuneLite testing. Preserve automatic Wise Old Man membership verification. No Discord login, new member passwords, or account-linking workflow.

This remains a small-clan, client-reported system. The public API cannot prove RSN ownership or that an achievement happened. That risk is accepted. The backend will enforce valid inputs, consistent state transitions, duplicate protection, and read-only staff content. Those protections do not constitute authenticated ownership and will not be described as such.

Full rewrite means replacing the defective lifecycle, transport, state coordination, parsing and delivery paths and updating all dependent UI/features. Existing assets, sensible enum values, configuration keys and verified utility behavior may be retained. There is no benefit in gratuitously replacing a correct image or renaming a stable activity key.

This document records the plan-first deliverable as written before implementation. See REVIEW_RESOLUTION.md and DEPLOYMENT.md for the subsequent implementation and validation status.

## Evidence now available

Both supplied exports have been inspected. PostgreSQL 17.6 metadata includes 11 public tables, six views, 100 columns, 48 constraints, 16 indexes, five attached row triggers, 16 public/storage policies, and one event-trigger binding. The previous export contains seven public function definitions.

The main commit remained unchanged when rechecked. No branch named `merge` or `integration/rewrite` was returned by the current remote branch check. Proposed names are `rewrite/fbclan-v3` for development and `integration/rewrite` for the test merge destination; these are proposed new names, not claims that they already exist.

### Findings that directly shape implementation

| Existing behavior | Planned correction |
|---|---|
| `drops.ge_value` is `integer` while Java computes `long` | Migrate to `bigint`; rebuild dependent GP views with intentional result types and grants. Test totals above 2,147,483,647 GP. |
| `lfg_check_applicant` is attached but counts accepted rows without serializing competing operations | Centralize all LFG writes in transactional RPCs, including cleanup. |
| Applicant uniqueness is normalized, host uniqueness is case-sensitive | Apply one normalization rule to both and check membership across hosting and applications. |
| No unique source-party key on formed snapshots | Add an enforced unique source-party identity after detecting legacy duplicates. |
| Host cannot apply to its own party, but there is no cross-table rule against hosting one party and joining another | Validate this invariant in the centralized transaction path. |
| Role constraints validate string shape, not activity/seat compatibility | Validate the actual composition and concrete assigned seats. |
| `submit_stats` updates totals/tier only when obtained entries/points increase | Update complete snapshots with separate semantics for counters and derived metadata. |
| PB numeric conversion can abort the batch | Validate entries before conversion; return per-entry accepted/ignored/rejected outcomes. |
| `ca_leaderboard` has `LIMIT 20` and is also used by badges | Keep the leaderboard capped; add a separate all-member badge projection. |
| CL/CA/GP views already contain ordering | Retain their ranking intent and add stable tiebreakers/explicit read ordering; do not claim they currently have no ordering. |
| Timestamps are capped against the future, but a past client timestamp remains possible | New mutation RPCs own heartbeat/update timestamps. |
| Curated content has anon SELECT policies, live LFG tables permit anon CRUD | Keep curated content read-only; route LFG writes through the transaction API at cutover. |
| Old `lfg_entries` still exists | Preserve it as legacy data; do not delete it merely because current main no longer uses it. |

The exports do not include cron job schedules/commands, storage bucket settings, external sheet/WOM sync-job code, or player data. Those omissions do not block the rewrite plan or isolated implementation. Deployment instructions will include targeted checks for jobs and bucket configuration; no claim is made that they were inspected. Existing integrations will be preserved by keeping their current table contracts where possible.

## Feature contract

Retain announcements, welcome message, configurable valuable/rare/notable/pet logging, optional screenshots, optional Discord drop webhook, all current LFG activities, role layouts, learner/teacher flags, host-added buddies, client/hiscore/manual KC sources, formed history, local `!lfg` summaries, PB leaderboards and recent records, collection-log/CA dashboards, WOM weekly results, GP results and CA chat badges.

Keep existing setting names and defaults unless a documented behavior correction requires a change. Preserve the valuable threshold floor, the explicit notable-item override, and unknown-rarity behavior. Keep screenshots opt-in. Classify newly logged drops by world type and exclude non-standard-world records from the normal GP board; legacy records have unknown provenance and will not be relabeled as known standard-world drops.

## Implementation sequence

### 1. Establish a reproducible baseline

- Create the development branch from the verified main commit.
- Store sanitized database definitions as test fixtures and version the SQL under `supabase/`; avoid the ignored `docs/` migration-history location.
- Pin a supported RuneLite dependency for repeatable CI, while retaining a separate compatibility check for current releases.
- Establish JUnit, HTTP-fake fixtures and PostgreSQL integration tests. The previous local Gradle download failure must be resolved in a working build environment or CI before claiming a successful build.
- Record all feature acceptance cases before replacing behavior.

**Gate:** an identifiable source/dependency baseline and an executable test harness. A launcher named `Test` is not test coverage.

### 2. Define the shared Java/SQL contract

Introduce versioned RPC names so new client behavior does not silently depend on changing old signatures. Return structured outcomes such as `ok`, `conflict`, `not_found`, `invalid`, and `unavailable`; reserve retries for transient failures. Include a client-generated operation UUID for safely retryable mutations and an expected party version for edits/acceptances.

Planned operations:

| API area | Operations |
|---|---|
| LFG | Read a consistent board snapshot; create/edit; apply/switch; accept/decline; add buddy; leave/kick; disband; heartbeat; remove formed history |
| PBs | Submit validated batches; read ranked records and recent bests |
| Stats | Submit a full eligible snapshot; read dashboards and all-member badges |
| Drops | Submit an occurrence/batch idempotently; attach a screenshot separately; read paginated history |
| Compatibility | Expose the supported protocol version and feature capabilities to the new client |

Actor RSNs remain claims supplied by the trusted client. Checking those against a stored host RSN prevents ordinary client mistakes, not deliberate spoofing.

**Gate:** example requests/responses and fixtures agree across Java and SQL before either side is considered complete.

### 3. Implement transactional LFG

For this clan-sized system, prefer one transaction-scoped advisory lock shared by all LFG mutations over a complicated distributed locking system. Keep the locked work short and entirely inside PostgreSQL; no network calls. All new write paths and cleanup must follow the same convention. At cutover, prevent old direct write paths from bypassing it.

Within each transaction:

- Normalize identity consistently and validate host/applicant exclusivity.
- Validate the current party version, activity, size and role composition.
- Validate a destination before removing an existing membership; failures roll back everything.
- Resolve wildcard applicants to a concrete available seat at acceptance.
- Accept the final seat and create the formed snapshot atomically.
- Use operation receipts and a unique source-party key to make response-loss retries safe.
- Reject incompatible activity/mode changes with accepted members; allow compatible description/world/min-KC edits.
- Set timestamps on the server and honor the defined timeout policy.

A single read operation returns live and formed state from a consistent snapshot for notification diffing. History can be paginated without losing events needed by the current member.

**Gate:** concurrent final-seat acceptance cannot overfill; failed switching preserves the old place; retry cannot create a second snapshot; stale operations cannot affect a replacement party.

### 4. Rewrite lifecycle, API and background coordination

Use an immutable session context containing generation, RSN, profile and verification/world state. Keep game reads and session transitions on the client thread; Swing state on the EDT; HTTP and serialization on controlled background tasks.

Every delayed verification, profile read, upload and UI callback carries the session generation. Reject obsolete results after logout/profile switch/shutdown. Snapshot identity and profile together, and use explicit-profile configuration reads. Track/cancel owned work without shutting down RuneLite's shared executor.

Centralize typed HTTP outcomes, timeouts, bounded retry/backoff and response parsing. Serialize/coalesce party refreshes and stats writes. Store explicit last-success time and errors. Reconcile enable/disable preferences when configuration changes.

**Gate:** delayed A-session responses cannot authorize B, seed B's values for A, restart a stopped feature, or overwrite a new board.

### 5. Rewrite PBs, drops and stats

**PBs:** one deterministic correlator with expiry on both sides, one-time consumption and symmetric order handling. Fixtures cover real supported message formats, both color encodings, raid sizes/modes and Sepulchre. Validate finite positive times. Backfill tracks acknowledged batches per profile and retries eligible failures. Corrections to existing suspect records remain an explicit admin operation; do not silently discard old PBs.

**Drops:** aggregate canonical item stacks, use supported event routing, and add occurrence-level duplicate protection. A bounded local outbox makes API delivery retryable. Screenshot capture has a deadline and cannot prevent drop recording. Screenshot attachment and Discord delivery have separate outcomes. Stable event IDs make retries safe; repeated legitimate identical drops remain separate occurrences. Honor opt-out changes for queued work and expose delivery failures.

**Stats:** compare complete per-profile snapshots, including collection totals and CA tier/threshold context. Coalesce changes while a submission is pending. Keep counters and derived fields logically separate, so a threshold change can update a tier without requiring a point increase. Retain the game's threshold source and document client-reported provenance rather than introducing an unmaintained hard-coded server threshold table.

**Gate:** regression cases from the review pass; blocked or failed optional work cannot corrupt or suppress core records.

### 6. Rebuild UI behavior

Separate view state from domain state. Retain application/add-member/host drafts, selected roles, learner flags and dirty KC values across polls. Do not overwrite edited values with a delayed hiscore prefill. Disable commands while the matching operation is in flight, and preserve the form on rejection.

Display meaningful loading, empty, stale and failed states. Keep remote text plain unless explicitly escaped for HTML. Add complete-value tooltips, quantities and keyboard-accessible controls. Show advertised versus current world clearly. Badge lookup uses the new full-member projection. Cache full hiscore responses per player/endpoint and coalesce UI refreshes.

**Gate:** polling and asynchronous responses do not erase input, move actions to a different party, or issue duplicate requests.

## SQL package and data preservation

Deliver ordered files for: fresh test installation, production preflight, additive schema preparation, data normalization/migration, versioned RPCs and views, grants/policies, cutover, cleanup setup, verification, and recovery. Provide a clear ordered runbook and a consolidated installer where safe; distinguish test reset scripts from production migrations.

Before normalization, identify case/spacing collisions, duplicate formed source-party IDs, impossible counts and invalid roles. Preserve affected rows in a migration archive rather than deleting them. Use reviewed deterministic merge rules for PBs and stats, and report legacy LFG conflicts for resolution. Snapshot history may be duplicated legitimately by prior bugs; preserve the original rows before adding uniqueness.

Widening `drops.ge_value` requires handling dependent GP views deliberately. PostgreSQL's `sum(bigint)` changes the aggregate result type to numeric, so specify the new API result type instead of assuming it remains the old bigint automatically. Recreate views, dependencies and intended privileges within the migration transaction and test the resulting JSON contract.

Keep staff-written announcements/notables/welcome/WOM tables and existing screenshot links intact. No destructive removal of the legacy LFG table is included. Do not change project-wide default privileges or Supabase-managed event triggers merely as part of this plugin migration; apply narrowly scoped grants to plugin-owned objects.

New RPC helpers are not broadly executable merely because the project has permissive function defaults. Set privileges explicitly, use a fixed safe search path, qualify referenced objects, and test access. Public read projections should have only their intended privileges. The absence of added sign-in does not require permitting arbitrary direct table updates.

## Test and integration workflow

A Git branch does not isolate Supabase. Automated tests will use a disposable PostgreSQL database with Supabase role fixtures; live storage/API tests use a separate Supabase test project. The new client needs a documented development-only endpoint override so a dev client cannot accidentally submit its test data to production. Production behavior continues to use the configured clan project.

Required automated scenarios:

- Login/logout/account/profile/world transitions with delayed callbacks.
- PB pairing, malformed batches, retries and profile-specific seeding.
- Two simultaneous acceptances, accept versus leave, edit versus form, cleanup versus heartbeat, and retry after commit/response loss.
- Role counts, incompatible host roles, wildcards, duplicate seats and mode changes.
- Values above the 32-bit GP limit, split stacks, duplicate event routes, screenshot timeout and Discord failure.
- Stats total-only/tier-only changes, account switches and out-of-order acknowledgements.
- Draft preservation, disabled-feature mutations, plain remote text and stale/empty rendering.
- Anonymous access to intended RPCs/reads; denial of direct curated or protected-table writes; migration from exported baseline and fresh installation.

After passing automated checks, merge the rewrite into the proposed integration branch through a reviewable PR. Test a RuneLite dev client against the test backend: host/apply/accept/form with two clients, ordinary boss/chest/raid/pet drops, PB capture/backfill, settings toggles, logout and restart, screenshots, badges and dashboards.

Production cutover is a separate last step. Additive preparation can coexist with the old client, but the transactional write cutover cannot promise full old-client write compatibility. Schedule deployment so active LFG sessions are resolved deliberately, deploy the new client, then disable legacy write paths. Preserve old read projections where practical. Document rollback compatibility and data handling; do not call a lossy restore a rollback.

Existing cron/bucket settings are a deployment verification item, not a reason to ask for more exports before implementation. Provide a metadata-only preflight to identify plugin cleanup jobs, replace only confirmed plugin jobs, and prevent duplicate cleanup schedules. Keep external sync jobs untouched unless their contract requires a documented adjustment.

## Deliverables and completion criteria

1. Full rewritten Java source, preserved assets/config compatibility and automated tests.
2. Complete versioned Supabase SQL for fresh install and migration, plus backend tests.
3. A supported protocol contract and development endpoint instructions.
4. Migration, test, cutover and recovery runbooks.
5. A development branch and reviewable integration PR, with actual build/test results and remaining live-client checks clearly identified.

No further schema export is required to proceed with the planned implementation. Live deployments and merges will only be described as completed after they actually succeed.
