# Independent review: orgonag/fbclan-plugin

Reviewed 9 September 2026. Main commit: **400acbe058700be032c0778c62cb6704651afb31** (merge of PR #24).

**Recommendation: request changes before treating the shared records or LFG ownership as trustworthy.** The package decomposition is sensible, but identity, session lifetime, multi-request mutations, and PB event correlation have correctness gaps. Another architectural rewrite is unnecessary; fix those boundaries first.

This is an independent inspection of the checked-out main branch, not a review of a previous review or PR description. No repository or production database changes were made.

## Scope and evidence

- Inspected all 48 tracked files: all Java sources, configuration, documentation, launch scripts, data, and binary resources. The file-by-file inventory below records the coverage. Binary checks covered PNG decoding/dimensions and wrapper archive integrity; they are not a decompilation audit of Gradle.
- Parsed the entire 718,722-byte drop dataset: 672 sources, 24,966 rows, 3,799 rows with multiple rolls. All denominators were positive. This validates structure, not every game probability.
- Attempted `./gradlew build --no-daemon`. It failed **before compilation** because the Gradle 8.10 distribution download could not reach the network. This is an environment limitation, not evidence that the project fails to compile.
- Executed focused Java 17 checks using extracted, unchanged PB pairing method bodies and parser expressions. Lombok data classes were replaced with equivalent constructors/accessors and external team-size reads with a fixed fixture. Separate checks used the actual Role enum with a minimal Activity stub, and real Swing JLabel rendering. These are focused reproductions, not a full plugin integration suite.
- Cross-checked relevant current RuneLite source: ConfigManager, ChatCommandsPlugin, LootManager, LootTrackerPlugin, and NpcLootReceived. Current upstream master is a compatibility reference, not proof of the exact artifact resolved by `latest.release`.
- No SQL migrations, RPC bodies, RLS policies, storage policies, cleanup schedules, or sync-job sources are tracked. The deployed backend was not inspected or probed. Statements about its actual permissions and constraints remain conditional.

Severity: **P0** emergency/widespread critical impact; **P1** urgent security or serious integrity defect; **P2** substantive correctness, reliability, or maintainability problem; **P3** improvement/polish. “Confirmed” means demonstrated by the code or a focused reproduction, not necessarily observed in a live client. “Risk” identifies the conditions needed for impact.

**No P0 finding is established.** There are seven P1 findings, sixteen P2 findings, and two P3 findings below.

## Ranked findings

### F01 — P1: Backend requests have no authenticated player identity

**Confirmed design defect; deployed exploitability not tested.** `core/Supabase.java:147–152`, `lfg/PartyApi.java:78–161`, `pbs/PersonalBests.java:221–243`, `stats/MemberStats.java:64–99`, README security section.

Every database request carries the same public anon bearer token. The RSN in a filter or RPC argument is caller-supplied. WOM verification occurs only in the client. The server receives no authenticated binding between the caller and that RSN.

Consequently, any operation that works for the shipped client can be reproduced with the same credentials and a different claimed name. Database checks can restrict shape, capacity, or monotonicity, but cannot distinguish the legitimate owner from another caller sending identical credentials. If the documented permissions are deployed, this permits cross-player LFG manipulation and fabricated drop/PB/stat submissions. “Improve-only” prevents slower PB replacement but allows falsely fast records; monotonic stats allow falsely high counters. The README's assurance that another member's records cannot be changed is not supported by this protocol.

**Fix:** authenticate members, bind authenticated user IDs to verified/approved RSNs, derive the actor server-side, and enforce ownership and membership for each operation. A clan Discord/OAuth login plus a server-controlled RSN association is one practical approach; a client simply claiming an RSN is insufficient. Keep the anon key public but restrict anonymous mutations. Use owner IDs in tables and narrowly scoped RPCs. Validate values and rate-limit writes/uploads. Preserve moderation/reversal capability and provenance for client-reported achievements: authenticating the submitter does not prove the achievement.

Do not replace the anon key with a service-role secret in the plugin. Supabase distinguishes application API keys from user authentication; row ownership needs an authenticated identity. [API keys](https://supabase.com/docs/guides/getting-started/api-keys), [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security).

### F02 — P1: Old verification can authorize a new session or restart a stopped plugin

**Confirmed race by code inspection.** `core/Clan.java:107–154`; `FinalBossPlugin.java:128–166,222–237`.

`verify()` captures a name, performs network work, then unconditionally writes `womAnswer` and `verified` and invokes the listener. `reset()` neither invalidates the task nor checks its completion generation. Reproduction schedule: verification for member A starts; logout resets state; B logs in; A's response arrives and publishes MEMBER or caches A's answer for B. A response after shutdown can also call `startPolling()` again.

The separate volatile fields do not provide an atomic identity/authorization snapshot. Capturing `rsn()` before calling `canUpload()` is not sufficient when a session switches between those reads.

**Fix:** maintain an immutable `SessionContext` with generation, RSN, RS profile, verification state, and enabled state. Own transitions on the client thread. Capture the generation for every verification and publish only if it is still current. Increment it on logout, profile change, and shutdown; cancel tracked verification/delay tasks and invalidate the listener. Serialize polling start/stop. Every queued write must carry and revalidate the appropriate context before execution.

**Regression:** hold A's response behind a latch, log in B, release A, and assert that B remains unverified and no old listener restarts polling.

### F03 — P1: PB correlation can submit another encounter's time

**Confirmed by focused Java reproduction.** `pbs/PersonalBests.java:126–202`.

Three independent defects compound:

1. Pending duration (`lastPbSeconds`) has no tick/time expiry. A duration at tick 10 can attach to a KC at tick 100, even after unrelated messages.
2. Old KC state is cleared **after** `handle()`, so a later-tick duration first consumes stale identity.
3. A successful KC-first pairing does not clear `lastBossKey`; another duration can reuse it.

Focused outputs included `zulrah=5.0/live` for both an orphan duration followed much later by a Zulrah KC and a stale Zulrah KC followed much later by a duration. A second same-tick duration consumed the already-used KC again.

An incorrectly short PB is particularly damaging because the improve-only backend cannot repair it through a normal later submission.

**Fix:** expire both sides before processing; store tick/generation for both; consume and clear both exactly once; share one emission path for KC-first and duration-first order. Enforce the verified event-correlation window with fixtures, initially same-tick as this implementation documents. Reset on logout/profile/world-context changes. Retain an administrative correction path for already-poisoned records.

### F04 — P1: PB backfill can read one account's profile under another account's RSN

**Confirmed unsafe sequencing; requires an account switch during queued work.** `pbs/PersonalBests.java:97–121`.

The method captures RSN on the client thread, then obtains the profile later on an executor. Worse, it enumerates keys for `profile` but reads values using the overload that resolves the **current** profile. A switch before execution or during iteration can attribute B's values to A.

**Fix:** capture RSN, profile, generation, and allowed-world state together. Read every value through the explicit-profile overload:

```java
Double value = configManager.getConfiguration(
    "personalbest", capturedProfile, key, double.class);
```

Validate that the context remains current before dispatching. A small immutable seed snapshot made on the owning thread is another option. Add a profile-switch fixture with different PB values for the same boss. The explicit/current-profile distinction was checked against [RuneLite ConfigManager](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/config/ConfigManager.java).

### F05 — P1: Party formation is not atomic or safely retryable

**Confirmed client transaction defect; duplicate behavior depends on unseen database constraints.** `lfg/PartyApi.java:153–156`; `lfg/PartyBoard.java:169–208`; `lfg/FormedParty.java:70–102`.

Formation inserts a client-built snapshot and then deletes the live party in separate requests. If insertion commits but deletion fails, the system retains both. Retrying can create duplicate snapshots; if a unique `party_id` constraint exists, an insert conflict can instead prevent the delete forever. Meanwhile, a member can leave or the host can edit after the snapshot was fetched, making the archived roster stale before deletion.

**Fix:** replace the pair with `form_party(party_id, expected_version, operation_id)`. In one database transaction, authenticate the host, lock the party, read the current accepted roster, recheck capacity/roles, create one snapshot with unique source-party ID, and close/delete the live rows. Repeating a committed operation must return its prior result. Ideally acceptance of the final seat and formation occur in the same transaction.

### F06 — P1: Concurrent refreshes can publish old state and execute duplicate formation

**Confirmed unsynchronized entry points; impact requires overlapping calls.** `lfg/PartyBoard.java:120–155,169–228`; `ui/PartiesTab.java:140–142,625–630`.

Refresh runs from the scheduled poll, tab selection, post-action refresh, and add-member completion on a shared executor. Only notification bookkeeping is synchronized. Two refreshes can fetch the same full party and both form it. A slower earlier response can overwrite a newer board. An in-flight refresh can repopulate state after `stop()` clears it. `poll` and `lastHeartbeat` also cross threads without consistent ownership.

**Fix:** one serialized LFG coordinator or a single-flight refresh mechanism; coalesce refresh requests; reject obsolete response generations; publish one immutable board snapshot containing parties and formed parties. Serialize start/stop and heartbeat state. This client fix reduces races but does not replace F05's server transaction, which must handle multiple clients.

### F07 — P1: Failed apply/host operations can discard an existing accepted place

**Confirmed failure path.** `lfg/PartyApi.java:78–80,97–111,119–121`.

`apply()` first deletes **all** applications for the RSN, including accepted membership, then inserts the new application. `save()` does the same before upserting a hosted party. If the second request fails because the target disappeared, a constraint changed, or the network failed, the prior seat/application is already gone. The UI permits applying elsewhere while a member of another host's party.

**Fix:** use transactional `apply_to_party` and `create_party` RPCs that validate the target before changing membership and roll back on any failure. Make switching from an accepted party an explicit UI operation; separate pending withdrawal from accepted departure. Test a target deleted between selection and confirmation and an insert failure after a successful withdrawal.

### F08 — P2: Session reset and upload checkpoints are incomplete

**Confirmed.** `FinalBossPlugin.java:96–101,222–237`; `pbs/PersonalBests.java:66–72,97–121`; `stats/MemberStats.java:32–33,46–99`; `clan/ClanContent.java:75–78,144–168`.

PB reset runs at plugin startup, not logout. After A seeds, B cannot seed until the plugin restarts. PB pairing survives logout too. Stats checkpoints are never reset or keyed by account: after A uploads 1,000 collection entries and 2,000 CA points, B with lower values is suppressed. Welcome state also persists after a successfully displayed welcome, contrary to a per-login interpretation.

`seeded=true` is set before a usable profile or successful RPC exists, so a transient failure prevents retry for that plugin session. If verification occurs on an excluded world, hopping to a standard world does not call `maybeSeed()` while the member remains verified.

**Fix:** key checkpoints by authenticated account/profile and reset transient pairing on every identity transition. Track backfill state as NOT_STARTED/IN_FLIGHT/SUCCEEDED with per-batch acknowledgements and bounded retry. Trigger eligibility reconciliation on profile changes, standard-world entry, and upload-setting changes. Define whether “session” means login or plugin lifetime and make documentation consistent.

### F09 — P2: LFG's enable switch does not disable the feature

**Confirmed.** `lfg/PartyBoard.java:120–136,169–228`; `ui/Sidebar.java:88–92`; `ui/PartiesTab.java:140–184,573–630`; `FinalBossPlugin.java:128–143,168–200`.

`enableLfg()` is checked when starting polling and for the command, but not in panel refresh/mutations. The LFG tab can host/apply with it disabled. Turning it off after polling starts leaves the poll running because no ConfigChanged handler stops it. Shutdown also skips cleanup if the setting was switched off, despite an existing party.

Other scheduling behavior is inconsistent: drop polling starts based on upload configuration once; turning logging off does not stop an existing poll, and turning it on does not create one until another verification/start cycle.

**Fix:** reconcile feature state on `ConfigChanged`; disable panel mutations and check the flag plus current authorization in the coordinator immediately before each operation. Cleanup should be driven by actual owned state, not the current enable flag. Keep viewing preferences separate from uploading preferences.

### F10 — P2: HTTP success is treated as proof that the intended row changed

**Confirmed API handling limitation.** `core/Supabase.java:105–116,155–175`; `lfg/PartyApi.java:91–129`; `ui/PartiesTab.java:500–505`.

PATCH/DELETE with no matching rows can succeed at the transport level. A stale acceptance can therefore appear successful although the applicant no longer exists. Disband is scoped by host RSN rather than the party the user saw; it can delete a newer party for the same host. Its lambda reads `clan.rsn()` at execution time instead of capturing the intended identity, adding an account-switch hazard.

**Fix:** commands use immutable party/applicant IDs, actor context, expected version, and explicit outcomes. Return affected rows or a typed RPC result. Distinguish already-absent idempotent deletes from stale acceptance/edit conflicts. Preserve the intended party ID in shutdown cleanup so an old cleanup task cannot remove a newly created party.

### F11 — P2: Role handling can silently claim incompatible seats are filled

**Confirmed model behavior; backend enforcement unknown.** `lfg/Role.java:59–70,156–214`; `ui/HostForm.java:138–162,251–279`; `ui/PartiesTab.java:541–550,591–606`.

`canFill()` lets any wildcard match any activity/mode. Focused check: `TOB_FILL.canFill(COX_CM_VENG)` is true, contradicting the class's mode-isolation contract. `open()` removes an arbitrary last seat when no compatible seat exists. A CoX party configured for two Mages with a Melee host is rendered as needing only one Mage, although the Melee host filled no requested role.

CoX role counts exceeding capacity are silently truncated in enum order. Host controls let the host choose a role outside that composition. Acceptance checks only total capacity, and add-member falls back to any playable role when advertised roles are exhausted. Editing an activity/mode leaves existing applicants to be interpreted under the new layout unless the unseen backend rejects it.

**Fix:** separate role preference from assigned seat. Validate activity/mode, exact composition totals, host seat, and accepted assignments in both client and server. Resolve wildcard applicants to a concrete seat at acceptance; use a matching algorithm that reserves exact roles before flexible roles. Reject incompatible edits or make roster reconfiguration explicit. Never remove an unmatched seat as a fallback.

### F12 — P2: Background refresh erases application drafts

**Confirmed.** `ui/PartiesTab.java:136,190–255,356–458`; `lfg/PartyBoard.java:169–208,235–252`.

`render()` removes the whole list and recreates `applyRow()`, which constructs fresh role, learner, and KC controls. A 30-second poll, its client-state callback, or a hiscore completion resets in-progress input. Only the add-member name is retained; even its selected role is rebuilt. Buttons are not disabled while a mutation is pending, enabling repeated or conflicting submissions.

**Fix:** store an `ApplyDraft` per party ID, including role, learner, KC value, provenance, and dirty state. Async KC prefill must not overwrite user-edited values. Preserve focus/scroll position, update rows by identity, and use an in-flight flag per command. Retain host form state on error; hide it only after successful save.

### F13 — P2: PB grammar has drifted from current RuneLite

**Confirmed mismatch to current upstream; deployed-client impact depends on the release.** `pbs/PbParser.java:20–25`; `pbs/PersonalBests.java:75–82,155–202`.

The parser's “copied verbatim” claim is no longer true. Current ChatCommands accepts `@...@` color tokens in KC and duration expressions; this parser accepts only `<col=...>`. The focused fixture `Fight duration: @red@0:05</col> (new personal best)` was rejected. The core also accepts TRADE events and supports adventure-log PB extraction, which this live pipeline does not mirror.

Team-specific output is also asymmetric: duration-first can emit a team key; KC-first emits only the generic key. Treat that as a coverage gap until supported raid message-order fixtures establish the actual order. Guard against a zero-player team label when varbits are not ready.

**Fix:** maintain fixtures from a pinned RuneLite version covering both color encodings, both message orders, raid modes/team sizes, Sepulchre, and relevant event types. Consider consuming validated per-profile `personalbest` config changes for core-discovered records, with explicit provenance, instead of duplicating every discovery path. Preserve minimal third-party notices for copied code. [Current ChatCommands source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java).

### F14 — P2: Drop delivery is best-effort and can silently disappear

**Confirmed failure paths.** `drops/DropLogger.java:179–216,257–284`; `core/Supabase.java:80–93`.

A failed drop insert is ignored; Discord is attempted regardless. There is no event ID, durable outbox, retry, or user-visible failed-delivery state. With screenshots enabled, the database insert waits for the next rendered frame and screenshot upload. If a frame never arrives before shutdown, the drop is never submitted. Upload success followed by insert failure leaves an orphan public object. A malformed webhook request can throw before its IOException catch and abort the remaining items in the batch.

**Fix:** assign an event UUID and capture event time at detection, then persist/submit the drop independently of screenshot capture. Add bounded, acknowledged outbox retries with server uniqueness on `(event_id,item_id)` or an appropriate item-entry ID. Attach a screenshot later through an authenticated, restricted update/RPC. Time out screenshot capture, use UUID storage paths, and isolate Discord failures per item. Honor Retry-After for 429 responses. Report pending/failed upload status.

### F15 — P2: Special-world drops contaminate an undifferentiated GP board

**Confirmed behavior; whether it is unwanted is a product decision.** `drops/DropLogger.java:102–107,144–166,195–216`; `core/Clan.java:92–103`.

PB/stat uploads reject non-standard worlds; drop/pet uploads do not, and store no world classification. Leagues/beta/speedrun-derived records can therefore enter the same drop log and feed its GP aggregates. The displayed rarity also assumes normal-world probabilities.

**Fix:** either apply the standard-world gate to both drop entry points, or persist world type and explicitly separate aggregates, rarity labeling, and filters. Add standard/seasonal fixtures. This is not a claim that the README currently promises a drop-world filter.

### F16 — P2: Loot coverage and deduplication depend on fragile source routing

**Risk, not a confirmed live duplicate/missed drop.** `FinalBossPlugin.java:251–267`; `drops/DropRules.java:25–32`; `drops/DropLogger.java:102–137`.

The plugin combines ground-loot `NpcLootReceived` with EVENT loot and a six-name NPC allowlist. Current core also emits ServerNpcLoot and builds LootReceived from it. Whether both routes overlap or one is needed depends on the boss and core version. There is no occurrence-level deduplication. Simply accepting every NPC LootReceived would risk logging ordinary drops twice.

Items are evaluated as supplied, without first aggregating multiple stacks of the same canonical item. A split stack can miss a total-value threshold or mismatch the multi-roll quantity table. The bundled table contains no exact CoX/ToB/ToA source entries, so raid rarity remains unknown; valuable/notable rules still work. This is a coverage limitation, not proof raid drops never log.

**Fix:** select one canonical occurrence source per supported release, aggregate canonical item IDs, and correlate/deduplicate with a short-lived event identity. Add real event-stream fixtures for ordinary NPCs, Gauntlet, Whisperer, Araxxor, Titans, and each raid. Do not suppress all identical item drops in a coarse time window. Record “unknown” rarity distinctly from zero and document source-specific probability assumptions. [LootManager](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/game/LootManager.java), [LootTracker](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java).

### F17 — P2: Some remote “plain labels” can render HTML

**Confirmed rendering behavior; attack impact depends on backend input validation.** `ui/Ui.java:137–144`; `ui/DropLogTab.java:80–85`; `ui/LeaderboardsTab.java:270`.

Swing JLabel interprets a value starting with `<html>` as HTML. The drop item label is built directly from database text; `Ui.small()` does not disable HTML. A focused Java/Swing check confirmed HTML view activation. This permits unexpected formatting and potentially HTML image requests when a writable text field permits markup. It is not browser JavaScript execution or demonstrated arbitrary code execution.

**Fix:** set `label.putClientProperty("html.disable", Boolean.TRUE)` for plain labels before assigning remote text, or escape text inside intentionally controlled HTML. Enforce backend text limits/allowed RSN format too. Keep the existing plain JTextArea handling and screenshot URL allowlist; those are useful defenses.

### F18 — P2: Failure states are hidden, and malformed data can stop refreshes

**Confirmed.** `core/Supabase.java:51–76,179–208`; `ui/Ui.java:65–71`; `ui/DropLogTab.java:44–60`; `stats/Dashboard.java:103–186`.

The drop feed maps network failure to an empty array and displays “No drops logged yet,” clearing real data. Other caches preserve old values but do not expose staleness. JSON property helpers can throw on wrong types; parsing happens outside the HTTP client's catch, and `Ui.async()` has no failure callback, so a Future can fail silently without rendering an error. One malformed dashboard source prevents later sources refreshing despite the independence claim.

A successful empty WOM cache does not clear prior XP/EHB data or its timestamp, contrary to the method's documented semantics. Notifications also read live and formed parties separately; if a party vanished while the formed read fails, a member can receive a false disband message before a later formed message.

**Fix:** return a typed result with data, last-success time, and structured error; preserve snapshots on transport failure; clear them on authoritative emptiness. Validate/quarantine rows independently and bound remote content. Make each dashboard source independent. Use an atomic board RPC or defer disappearance classification until both related reads succeed. UI errors should distinguish unavailable, empty, stale, and rejected.

### F19 — P2: CA badges use a ranked subset as the member directory

**Confirmed query selection; missing-badge impact follows if the documented top-20 view is deployed.** `stats/CaBadges.java:67–85`; README dashboard contract.

Badges load from `ca_leaderboard`, documented as the top 20. Eligible members outside those 20 get no badge, and badge eligibility can disappear solely because ranking changes. The plugin also keeps badge state across logout and does not gate badge application on current verification.

**Fix:** expose a separate authorized `member_badges` projection for all eligible members; keep ranking limits in the dashboard view. Clear or explicitly scope caches by session, and define whether badges should operate for unverified local users. Derive tiers on the server from authoritative thresholds instead of trusting arbitrary client tier strings.

### F20 — P2: Full-list reads assume unlimited, correctly ordered responses

**Confirmed query omissions; impact depends on dataset size/view definitions.** `lfg/PartyApi.java:38–74`; `pbs/Leaderboards.java:53–64`; `clan/ClanContent.java:83–121`; `stats/Dashboard.java:103–153`.

LFG/formed/announcement/PB reads lack pagination. A backend row cap can silently hide parties, members' notifications, or later alphabetical PB categories. Formed parties and all embedded applicants are downloaded every 30 seconds whether history is visible or not. Dashboard CL/CA/GP queries have no explicit order, yet the UI assigns rank/podium position by response order. Unless the view contract guarantees order, the displayed ranking is not guaranteed.

**Fix:** stable ordering with unique tiebreakers and explicit ranges/cursors; fetch visible history on demand with a seven-day server filter; fetch notification deltas separately. Order CL by count, CA by points, and GP by value with defined tie rules or return explicit rank. Review indexes for party ID, normalized owner, accepted membership, updated timestamps, and aggregate windows using real query plans when migrations are available.

### F21 — P2: The critical backend is excluded from version control

**Confirmed repository gap.** `.gitignore:9–11`; README security/lifecycle sections; all Supabase callers.

The ignore file explicitly excludes local SQL migration history under `docs/`. The guarantees doing most of the security/integrity work are not reproducible from this repository. A clean deployment, schema rollback, RPC audit, or race-condition test cannot be performed from main alone.

**Fix:** commit `supabase/migrations/`, RLS and RPC tests, view definitions, grants, storage policies, scheduled cleanup, and sync-job source/deployment instructions. Keep secrets out of tracked files. Specifically verify canonical RSN uniqueness, concurrent capacity checks with locking, foreign-key cleanup, snapshot idempotency, RPC search_path/privileges, upload limits, and cron health. Existing triggers are not proof those concurrency invariants are sound until their code and tests are inspected.

### F22 — P2: There is no automated correctness gate

**Confirmed.** `build.gradle:16–28`; `src/test/.../FinalBossPluginTest.java:6–15`; tracked file inventory.

The only test source is a launcher, and the build explicitly has no unit tests. There is no tracked CI workflow. GitHub's main-branch response reported `protected=false` for the reviewed branch. `latest.release` and `mavenLocal()` make reproducing dependency resolution harder; the wrapper distribution has no checksum property.

**Fix:** add JUnit and HTTP-fake tests, backend integration tests, and CI running a clean compile/test plus required status checks. Pin a known RuneLite version for reproducible CI, optionally with a separate latest-version compatibility job if the distribution workflow needs it. Exclude `mavenLocal()` from clean CI resolution. Validate wrapper integrity and set the published distribution SHA-256. Add static analysis after the immediate behavioral tests, not instead of them.

### F23 — P2: Stats deduplication omits totals and tier changes and permits request bursts

**Confirmed.** `stats/MemberStats.java:46–99`; `FinalBossPlugin.java:241–247`.

The change gate compares only obtained collection entries and CA points. If collection-log total or CA threshold/tier changes while those counters stay equal, no update occurs. Only COLLECTION_COUNT and CA_POINTS events trigger submission; COLLECTION_COUNT_MAX and threshold changes do not. An improve-only tier backend would also need a way to handle downward recalculation when thresholds increase.

Until the first request completes, repeated varbit changes can queue duplicate submissions. The volatile `Math.max` read-modify-write is not atomic across completion threads, so out-of-order completions can lower a local checkpoint and trigger unnecessary resubmission.

**Fix:** compare a complete per-profile stats snapshot, reconcile after thresholds/total become ready, coalesce one pending latest snapshot per account, and serialize acknowledgement updates. Compute tier from points plus a versioned threshold table server-side. Store counters and derived display values with different update semantics where needed.

### F24 — P3: UI lacks complete-value access and scalable layout behavior

**Confirmed implementation limitations; visual severity needs a live client review.** `ui/DropLogTab.java:64–106`; `ui/Ui.java:56,163–195`; `ui/LeaderboardsTab.java:249–324`; `ui/HostForm.java:99–102`.

The drop log truncates item/value/detail text without full-value tooltips and fetches quantity without displaying it. Fixed 20/34/38px row heights and 226px wrapping assumptions are sensitive to scaling and font/layout changes. Online status uses color alone, with no distinct unknown/stale state. Collapsible section headers are mouse-only panels. The host form shows current world while an existing party's advertised world changes only on save.

**Fix:** full-value tooltips and quantity display; responsive preferred-size layouts; keyboard-accessible toggle buttons; explicit online/unknown text; distinguish current world from advertised world and offer a deliberate update. Validate at normal/HiDPI scale and narrow sidebar widths. Explain screenshot scope/public visibility in settings. Mark the webhook setting as secret in the supported RuneLite config UI and document RuneLite's own profile-sync behavior rather than promising it is always local-only.

### F25 — P3: Hiscore caching does more work than necessary

**Confirmed.** `lfg/Killcounts.java:63–70,119–181`; `ui/PartiesTab.java:665–693`.

The documented LRU is a default LinkedHashMap and therefore insertion-ordered, not access-ordered. Cache keys include activity although each lookup retrieves a full player hiscore result, causing repeated requests when browsing the same player across activities. Repeated renders attach callbacks to the same in-flight future; many applicants can cause many complete UI rebuilds. LOCAL values are shown without a source suffix even though they are self-asserted.

**Fix:** use access-order LRU, cache a player's complete response by `(normalizedRSN, endpoint)`, derive per-activity values, and coalesce render notifications. Show “client-reported” distinctly from independently fetched hiscores. Capture profile context for local KC reads as well.

## Architecture and implementation direction

The existing packages are appropriately sized for this plugin. `FinalBossPlugin` mostly delegates, network operations generally run off the client/EDT threads, Swing refresh callbacks usually hop to the EDT, responses are closed with try-with-resources, GP multiplication widens before multiplying, and several read caches correctly preserve data on outages. Plain announcement text and the screenshot link prefix check are useful defensive choices.

The weak boundary is that feature state, identity, transport outcomes, and remote writes do not share one lifecycle contract. The fix should have four focused pieces:

1. **SessionContext / lifecycle coordinator:** atomic identity/profile/generation, client-thread ownership, cancellation and opt-out reconciliation. Ensure injected Swing views are constructed/mutated on the EDT; current constructors rely on their Guice construction context without enforcing it.
2. **Typed repositories and domain commands:** replace boolean success with success/conflict/rejected/unavailable outcomes. Move authorization and transactional LFG transitions into RPCs.
3. **Deterministic feature state:** one PB correlator; one serialized party coordinator; immutable snapshots and independent UI draft models. Defensive-copy model lists instead of relying on Lombok `@Value` to make mutable lists immutable.
4. **Acknowledged delivery:** bounded queues and event identities for writes, separate optional screenshots/Discord, explicit freshness and errors for reads.

Do not hold synchronized locks during blocking HTTP calls. Do not shut down RuneLite's injected shared executor. If a dedicated executor is introduced, bound its queue and own its lifecycle. Prefer completion-driven/fixed-delay polling with backoff over piling overlapping work onto a fixed-rate poll. Add explicit call timeouts/cancellation using a derived HTTP client rather than changing global client settings.

### Example PB correction structure

Illustrative Java 11-compatible shape, not a drop-in patch:

```java
List<Submission> process(String message, int tick, long generation) {
    if (generation != pairingGeneration || tick != pairingTick) {
        clearPair(); // clears both boss and duration
        pairingGeneration = generation;
        pairingTick = tick;
    }
    rememberValidatedBossOrDuration(message);
    if (boss == null || duration == null) {
        return Collections.emptyList();
    }
    List<Submission> result = emitValidatedPair(boss, duration);
    clearPair();
    return result;
}
```

The expiry window must be justified by captured message streams. Do not increase it arbitrarily to hide missing events. Persist enough event provenance to diagnose false records without collecting unrelated chat.

### Example LFG transaction contract

`accept_applicant(party_id, applicant_id, expected_version, operation_id)` should authenticate the host, lock the current party, verify applicant status and concrete seat availability, accept once, increment version, and form the party atomically if full. Return the updated version/snapshot and a typed outcome. A unique operation ID makes response-loss retries safe. `apply_to_party` should validate the destination before withdrawing any existing membership inside that same transaction.

Names should be presentation attributes backed by stable member IDs. Normalize them consistently server-side and client-side; Java's case-insensitive display comparison does not establish database uniqueness.

## Regression tests and release gates

| Area | Required cases |
|---|---|
| Identity | A→B login during delayed WOM response; shutdown during verification; stale callback; profile switch during backfill; uploads toggled off before queued work executes |
| PBs | KC-first and duration-first; orphan duration; stale KC; double duration; same-tick two encounters; account/world reset; both color syntaxes; raid team sizes; Sepulchre; unavailable profile; batch retry |
| LFG transactions | Two hosts/clients accept the final seat concurrently; accept vs leave; edit vs form; response lost after snapshot commit; repeat same operation; failed switch preserves previous membership |
| Roles | Host outside requested composition; excessive counts; duplicate roles; exact roles before wildcard; mode change with accepted roster; BA wildcard assignment |
| UI | Poll and hiscore callback preserve draft/focus; double click creates one command; disabled LFG cannot mutate; stale error/empty states; untrusted HTML remains plain |
| Drops | Normal NPC/chest/raid event traces; duplicate routes; noted/variant items; split quantities; valuable/rare/notable exclusions; pets; special worlds; no screenshot frame; failed upload/insert/webhook |
| Stats | Lower-stat second account; total-only change; threshold-only change; out-of-order responses; burst coalescing; missing varbits |
| Backend | Anonymous/member/other-member/admin allow-deny matrix; RPC privileges; numeric bounds; ownership; upload quotas; unique operation IDs; concurrent capacity and membership constraints |

Minimum sequence: fix F01–F04 (identity/PBs), F05–F07 (LFG transactions/coordinator), then add automated regression gates and fix settings/drafts/recovery. Audit or quarantine suspicious existing PB/stat rows before tightening improve-only writes; otherwise incorrect records remain permanently dominant.

Useful future upgrades, after correctness: authorized live subscriptions or delta polling for LFG, a diagnostics panel with freshness/pending deliveries, an admin moderation/audit interface, explicit data-retention controls, versioned drop-table generation with source hashes and probability fixtures, and a compatibility job against new RuneLite releases. None requires replacing Swing or the entire codebase.

## File-by-file inventory

Paths below are repository-relative. “No specific defect” means no additional finding from this inspection, not a guarantee of correctness. Source links are pinned to the reviewed commit.

| File | Inspection result |
|---|---|
| [.gitignore](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/.gitignore) | Reviewed ignore rules; SQL/history exclusion is F21. |
| [LICENSE](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/LICENSE) | Reviewed BSD-2 text. Keep third-party code/data notices separate; no claim of legal clearance. |
| [README.md](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/README.md) | Reviewed all feature/security/build claims against source. F01, F08, F19, F21–F22; backend claims cannot be verified from this tree. |
| [build.gradle](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/build.gradle) | Reviewed repositories, Java 11 target, Lombok, dependencies and launcher task. F22. |
| [gradle/wrapper/gradle-wrapper.jar](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/gradle/wrapper/gradle-wrapper.jar) | ZIP/CRC integrity checked (33 entries), manifest inspected; SHA-256 2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046. Not decompiled or matched to a published checksum. |
| [gradle/wrapper/gradle-wrapper.properties](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/gradle/wrapper/gradle-wrapper.properties) | Reviewed distribution URL/options; no distribution checksum. F22. |
| [gradlew](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/gradlew) | Read full POSIX launcher; no additional nonstandard behavior identified. |
| [gradlew.bat](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/gradlew.bat) | Read full Windows launcher; no additional nonstandard behavior identified. |
| [icon.png](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/icon.png) | Decoded and verified PNG: 16×16. Root/resource copies both checked. |
| [runelite-plugin.properties](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/runelite-plugin.properties) | Reviewed plugin entry point, metadata and standard build declaration; no additional defect. |
| [settings.gradle](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/settings.gradle) | Reviewed project-name declaration; no additional defect. |
| [src/main/java/com/github/orgonag/fbclan/FinalBossConfig.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/FinalBossConfig.java) | Reviewed every setting, default and range. F09, F24; opt-out defaults accurately described. |
| [src/main/java/com/github/orgonag/fbclan/FinalBossPlugin.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/FinalBossPlugin.java) | Reviewed full lifecycle and every event route. F02, F06, F08–F09, F16, F23. |
| [src/main/java/com/github/orgonag/fbclan/clan/ClanContent.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/clan/ClanContent.java) | Reviewed all reads, welcome sanitation/scheduling and caches. F08, F18, F20. |
| [src/main/java/com/github/orgonag/fbclan/core/Clan.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/core/Clan.java) | Reviewed WOM lookup, world gate and shared state. F02; membership is a client gate only (F01). |
| [src/main/java/com/github/orgonag/fbclan/core/Names.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/core/Names.java) | Reviewed normalization and sanitation. Locale.ROOT is appropriate; align server normalization and reject invalid identity input. |
| [src/main/java/com/github/orgonag/fbclan/core/Supabase.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/core/Supabase.java) | Reviewed reads/writes/RPC/storage, auth headers, encoding and JSON helpers. F01, F10, F18, F20. |
| [src/main/java/com/github/orgonag/fbclan/drops/DropLogger.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/drops/DropLogger.java) | Reviewed event qualification, pets, screenshot/Discord/upload pipelines and feed. F14–F17. |
| [src/main/java/com/github/orgonag/fbclan/drops/DropRates.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/drops/DropRates.java) | Reviewed loading, variation/noted matching, quantity ranges and binomial expansion. F16; index by source/item if profiling warrants; variable quantity ranges need probability-semantics fixtures. |
| [src/main/java/com/github/orgonag/fbclan/drops/DropRules.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/drops/DropRules.java) | Reviewed thresholds, exclusions, source aliases and pet messages. Explicit notable override is intentional. F15–F16. |
| [src/main/java/com/github/orgonag/fbclan/lfg/Activity.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/Activity.java) | Reviewed all 25 activity entries and mappings. Stable enum keys useful; upstream aliases/item IDs require compatibility fixtures (F16/F22). |
| [src/main/java/com/github/orgonag/fbclan/lfg/FormedParty.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/FormedParty.java) | Reviewed JSON and snapshot roster methods. F05/F18; defensively copy members. |
| [src/main/java/com/github/orgonag/fbclan/lfg/Killcounts.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/Killcounts.java) | Reviewed profile reads, async timeout/failure caching and extraction. F04-related profile context, F25. |
| [src/main/java/com/github/orgonag/fbclan/lfg/LfgCommand.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/LfgCommand.java) | Reviewed local-author checks, parsing and summaries. Read-only as documented; cap/paginate long summaries and generation-check delayed replies. |
| [src/main/java/com/github/orgonag/fbclan/lfg/LootRule.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/LootRule.java) | Reviewed enum parsing/fallback. No additional specific defect. |
| [src/main/java/com/github/orgonag/fbclan/lfg/Party.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/Party.java) | Reviewed entire row parser, JSON payload and derived membership/role state. F10–F11/F18; copy builder lists defensively. |
| [src/main/java/com/github/orgonag/fbclan/lfg/PartyApi.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/PartyApi.java) | Reviewed every query and mutation. F01, F05, F07, F10, F20. |
| [src/main/java/com/github/orgonag/fbclan/lfg/PartyBoard.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/PartyBoard.java) | Reviewed polling, heartbeat, formation, mutations and every diff branch. F06, F09, F18; lastHeartbeat should advance on success and reset appropriately. |
| [src/main/java/com/github/orgonag/fbclan/lfg/Role.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/lfg/Role.java) | Reviewed all enum values, composition/matching and serialization. F11; focused matching checks executed. |
| [src/main/java/com/github/orgonag/fbclan/pbs/Leaderboards.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/pbs/Leaderboards.java) | Reviewed caching/parsing and query ordering. F18/F20; validate finite positive seconds. |
| [src/main/java/com/github/orgonag/fbclan/pbs/PbParser.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/pbs/PbParser.java) | Reviewed every regex, canonicalization, parsing and display helper. F03/F13; formatting should use a stable locale and validate finite values. |
| [src/main/java/com/github/orgonag/fbclan/pbs/PersonalBests.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/pbs/PersonalBests.java) | Reviewed gating, seeding, pairing and batching. F03–F04/F08/F13; focused pairing checks executed. |
| [src/main/java/com/github/orgonag/fbclan/stats/CaBadges.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/stats/CaBadges.java) | Reviewed icon registration, tier fetch and chat name mutation. F19; membership/session behavior should be explicit. |
| [src/main/java/com/github/orgonag/fbclan/stats/Dashboard.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/stats/Dashboard.java) | Reviewed each read, payload parser and number formatter. F18/F20; total/top queries can describe different database snapshots. |
| [src/main/java/com/github/orgonag/fbclan/stats/MemberStats.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/stats/MemberStats.java) | Reviewed counters, payload omissions and live thresholds. F08/F23. |
| [src/main/java/com/github/orgonag/fbclan/ui/AnnouncementsTab.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/ui/AnnouncementsTab.java) | Reviewed complete rendering/fetch path. Plain text areas avoid markup; F18/F20/F24 for failure, volume and layout. |
| [src/main/java/com/github/orgonag/fbclan/ui/DropLogTab.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/ui/DropLogTab.java) | Reviewed complete feed render and link handling. F17/F18/F24; screenshot prefix check is present. |
| [src/main/java/com/github/orgonag/fbclan/ui/HostForm.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/ui/HostForm.java) | Reviewed rebuild/populate/build and all widget listeners. F11/F24; explicitly commit/validate spinner editor text before submit. |
| [src/main/java/com/github/orgonag/fbclan/ui/LeaderboardsTab.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/ui/LeaderboardsTab.java) | Reviewed all sections, row rendering, sorting, custom podium/medal painting. F17–F20/F24; use boss key, not display string, as unique identity. |
| [src/main/java/com/github/orgonag/fbclan/ui/PartiesTab.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/ui/PartiesTab.java) | Reviewed all controls/cards/forms/actions and async callbacks. F09–F12/F25. |
| [src/main/java/com/github/orgonag/fbclan/ui/Sidebar.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/ui/Sidebar.java) | Reviewed verification card/tab behavior and retry action. F09; logged-out state currently says verifying without an active check. |
| [src/main/java/com/github/orgonag/fbclan/ui/Ui.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/java/com/github/orgonag/fbclan/ui/Ui.java) | Reviewed all layout, text, image, input and async helpers. F17/F18/F24. |
| [src/main/resources/com/github/orgonag/fbclan/ca_elite.png](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/resources/com/github/orgonag/fbclan/ca_elite.png) | Decoded and verified PNG: 11×11; used by CaBadges. |
| [src/main/resources/com/github/orgonag/fbclan/ca_grandmaster.png](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/resources/com/github/orgonag/fbclan/ca_grandmaster.png) | Decoded and verified PNG: 11×11; used by CaBadges. |
| [src/main/resources/com/github/orgonag/fbclan/ca_master.png](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/resources/com/github/orgonag/fbclan/ca_master.png) | Decoded and verified PNG: 11×11; used by CaBadges. |
| [src/main/resources/com/github/orgonag/fbclan/icon.png](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/resources/com/github/orgonag/fbclan/icon.png) | Decoded and verified PNG: 16×16. Root/resource copies both checked. |
| [src/main/resources/com/github/orgonag/fbclan/npc_drops.json](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/main/resources/com/github/orgonag/fbclan/npc_drops.json) | Entire file parsed/structurally scanned: 672 sources, 24,966 rows. Data semantics/provenance refresh needed; not independently verified against every Wiki drop table. |
| [src/test/java/com/github/orgonag/fbclan/FinalBossPluginTest.java](https://github.com/orgonag/fbclan-plugin/blob/400acbe058700be032c0778c62cb6704651afb31/src/test/java/com/github/orgonag/fbclan/FinalBossPluginTest.java) | Reviewed launcher in full; it contains no unit tests. F22. |

## Focused check results

The Java compiler module was invoked directly because the environment exposes a Java runtime but no `javac` executable. All checks below ran locally; none wrote to Supabase.

| Check | Observed result | Interpretation |
|---|---|---|
| Duration at tick 10; unrelated text at tick 11; Zulrah KC at tick 100 | `[zulrah=5.0/live]` | Orphan duration was misattributed: F03 |
| Zulrah KC at tick 10; duration at tick 100 | `[zulrah=5.0/live]` | Stale KC consumed before expiry: F03 |
| KC + duration at tick 10; another duration at tick 10 | `[zulrah=5.0/live]` again | Pair not consumed exactly once: F03 |
| `Fight duration: @red@0:05</col> (new personal best)` | Parser returned no duration | Current-upstream grammar mismatch: F13 |
| JLabel text `<html><b>remote item</b></html>` | HTML view present | Plain-label assumption is false: F17 |
| `TOB_FILL.canFill(COX_CM_VENG)` | `true` | Mode/activity wildcard leakage: F11 |
| Required CoX roles Mage/Mage; host Melee | One Mage seat reported open | Unmatched host consumes a requested seat: F11 |

These results validate specific method behavior. They do not establish how often the triggering message sequences occur in live gameplay, nor validate the full RuneLite integration. The backend security finding is established at the request identity/design level; production grants and possible exploitation were not tested.

## Remaining evidence needed

A clean dependency-resolved build, real RuneLite client smoke test, captured loot/PB event fixtures, and the deployed backend schema/security definitions are required to close the review's runtime and backend uncertainties. No successful build, live gameplay validation, production exploit, or full RLS audit is claimed here.
