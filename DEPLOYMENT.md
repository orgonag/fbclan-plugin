# Final Boss v3: test and deployment guide

This branch replaces the broken write/state paths while retaining the existing feature set, configuration keys, artwork and activity IDs. It requires the v3 SQL API. The Java rewrite and SQL must be tested together.

Automatic WOM verification remains the only membership step. There is no Discord login. The optional Discord webhook still sends drop notifications. Player identity and achievement claims remain client-reported; the database enforces consistency, not proof of account ownership.

## 1. Prepare an isolated test database

Create a separate Supabase project. Open **SQL Editor → New query**.

- **Empty test project:** paste and run all of [`supabase/install_fresh.sql`](supabase/install_fresh.sql). This includes every exported public table, view, constraint, index and row trigger, followed by the v3 migration, in one transaction. It contains no player records or credentials.
- **Test copy of the existing database:** run [`supabase/preflight.sql`](supabase/preflight.sql), then [`supabase/migrations/001_v3.sql`](supabase/migrations/001_v3.sql). Do not run the empty-project installer over existing tables.
- Run [`supabase/002_storage_and_cleanup.sql`](supabase/002_storage_and_cleanup.sql) to configure the screenshot bucket and five-minute cleanup job. Enable `pg_cron` under Database → Extensions if needed. Inspect the policy/job output: remove an older LFG expiry job only if it conflicts with the new TTL. Keep the sheet/WOM sync jobs.

The exports did not contain storage bucket configuration, cron schedules, external sheet/WOM job source, or actual player rows. Those external integrations must be checked in the dashboard. This code does not replace them. The original `announcements`, `welcome_message`, `notable_items` and `wom_cache` table contracts remain available to privileged sync jobs.

SQL Editor normally runs as the database owner. A public/anon API key cannot execute DDL or reveal the complete private schema. You do **not** need to share the database password or service-role key with plugin users.

For a disposable test project only, run `supabase/tests/behavior.sql` after installation. It inserts synthetic players, PBs, parties and drops; do not run it against production. The legacy-data fixture is for automated testing, not installation.

## 2. Run the plugin against the test project

Install JDK 17. From a checkout of `rewrite/fbclan-v3`:

```sh
./gradlew test jar
./gradlew run -Dfinalboss.apiUrl=https://YOUR-TEST-PROJECT.supabase.co -Dfinalboss.anonKey=YOUR-TEST-ANON-KEY
```

On Windows use `gradlew.bat`. Use the project's legacy **anon** key, not a service-role key. Both overrides must be supplied together. They are developer JVM properties, not new member setup requirements. Gradle forwards them to the RuneLite launcher. The built JAR is under `build/libs`; it is a plugin artifact, not a standalone RuneLite executable.

The checked-in default endpoint/key still targets the clan's existing project. Always pass the test overrides while testing. A v3 client cannot write through the old v2 API.

The build pins RuneLite `1.12.38`, Java release 11 bytecode, and Gradle 8.10. Keep Java 17 for the developer launcher. Updating RuneLite requires recompilation plus the fixtures and in-game checks below; do not silently switch the reproducible build back to `latest.release`.

## 3. In-game acceptance checks

Use two verified clan accounts where a second participant is needed. These checks require a running RuneLite client and are not replaced by unit tests.

1. Enable mid-session; verify WOM membership. Switch account/profile while verification is pending, log out during a fetch, and disable/re-enable the plugin. No old account may acquire verified status or receive delayed LFG messages. Retry a temporary WOM error from the panel.
2. Host every role-based mode: CoX/CM, ToB/HMT and BA. Check size limits, composition, host role and typed spinner values. Over-capacity role counts must display an error and preserve the form.
3. Apply, decline, accept, kick, leave, add a buddy and disband. Concurrently accept the final seat from two clients; exactly one formed snapshot should exist, with the right roster. Changing an accepted composition must be rejected. A failed switch must retain the existing application or accepted place.
4. Keep a draft open while polling or a hiscore request finishes. Role, learner and typed KC values must survive ordinary refreshes. A genuinely changed party composition rebuilds the role choices; check before confirming. Failed submissions retain the form.
5. Disable LFG while requests are pending. It must stop polling and accepting new writes. Re-enable and refresh. After a host stops heartbeating, the advertisement expires after 30 minutes; cleanup runs every five minutes, while board reads hide expired ads immediately. Formed history lasts seven days.
6. Test NPC loot and chest/event loot: CoX, ToB, ToA, Gauntlet, Whisperer, Araxxor and Royal Titans. Confirm actual source routing with the core Loot Tracker enabled. Test threshold, rare, notable, pet and duplicate-pet messages. Check item quantities and GP totals. Event availability depends on the current RuneLite/game version.
7. Disconnect the network after a qualifying drop. Reconnect: the queued drop appears once. Restart RuneLite on the same account/profile and retry. Another account must not upload that queue. Turn logging off: no queue sends until re-enabled. Screenshot off, minimized rendering or upload failure must not prevent the core drop record. Discord failure must not affect it.
8. Test PB message order both ways, color-token messages, raid team sizes and Sepulchre. Orphaned messages from an earlier tick must not pair. Change account before backfill and verify the database name/profile pairing. Worse times must not overwrite better times. PB/stat upload switches must suppress queued sends.
9. Verify all eligible badge holders, including members below the CA top 20. Check total/tier changes with unchanged points/counts, empty WOM cache behavior, long labels, and outage messages in each tab.
10. Test standard and special worlds. New special-world drops may appear in the feed but must not contribute to standard GP totals. Legacy drops are retained with unknown world provenance.

## 4. Production cutover after the test branch passes

1. Take a full Supabase database backup and export existing storage policies, bucket settings and cron jobs. Record row counts using `preflight.sql`.
2. Resolve any reported case-equivalent host or cross-table membership conflicts in the test copy first. The migration aborts rather than guessing which active party to delete. The normalized applicant index can also expose legacy name collisions.
3. Arrange a short write maintenance window. Old v2 clients lose write access at migration commit; distribute/approve the v3 plugin at the same cutover. Reads of retained public projections continue.
4. Run `migrations/001_v3.sql` once, then `002_storage_and_cleanup.sql`. Do not rerun the migration: its version guard rejects that. Verify `select public.fb_board();` returns `protocol: 3` and test one LFG flow and one stats/PB upload with the v3 client.
5. Compare pre/post counts. Case-equivalent PBs collapse to the fastest result; independent CL and CA maxima survive normalization. Complete originals remain under the private `fb_private.legacy_*` tables. Duplicate formed history is retained, with only one row keeping the live-party ID. `drops.ge_value` is widened without discarding drop rows.
6. Check grants and storage policies. Anonymous clients may execute the v3 RPCs and read published projections, but may not directly mutate live tables or curated content. Check external sync jobs still run with their existing privileged credentials.

No production SQL has been executed by this rewrite. Creating a PR does not deploy the database or merge the plugin into main.

## Recovery and maintenance

- A failure inside `001_v3.sql` rolls back that migration transaction. Investigate the message, fix the test copy, and rerun only after confirming no v3 version row exists.
- After successful cutover, do not blindly run a destructive down migration. New values may exceed the old integer range, and normalization has changed keys. Stop writes, restore the pre-cutover database backup to a separate project, validate it, then coordinate client/database rollback. The private archives are useful for comparison, not a substitute for a full backup.
- Drop receipts use stable UUIDs. Local pending records are under RuneLite's `finalboss-outbox` directory, partitioned logically by profile and endpoint. Only the currently verified matching profile sends them. There is a 1,000-pending-event limit. Corrupt files are retained as `.invalid`; server-rejected records as `.rejected`. Review plugin logs if either occurs. Delete pending files only if intentionally discarding those uploads.
- Screenshots and Discord notifications are best effort. Screenshots expire if a frame does not arrive within five seconds; failure does not roll back the drop. Screenshot files can remain orphaned after partial failure. Add a privileged retention job if storage growth warrants it.
- LFG operation receipts are retained so ancient retries cannot recreate old mutations. For a small clan this is intentionally simple. If growth becomes material, introduce an explicit request-expiry contract before pruning receipts.
- No network call blocks the client thread or Swing EDT. Running requests may finish after a toggle/logout, but session/board generations prevent stale results from publishing; a request already delivered to the server cannot be recalled.
- CI executes JUnit and real PostgreSQL 17 multi-connection tests. Local SQL validation also uses the PostgreSQL engine compiled to WASM (PGlite); that validates migration semantics but does not prove cross-connection locking or Supabase Storage/cron behavior.
