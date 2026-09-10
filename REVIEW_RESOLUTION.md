# Review resolution and test scope

Baseline: main `400acbe058700be032c0778c62cb6704651afb31`. See `REVIEW.md` for the independently inspected baseline and P0–P3 classifications; `IMPLEMENTATION_PLAN.md` records the plan prepared before this implementation. This document records implementation status rather than reclassifying untested game behavior as proven.

| Findings | Implementation | Verification / remaining scope |
|---|---|---|
| F01 identity spoofing | Accepted small-clan trust model. No Discord sign-in or account linking. RPC input/integrity checks and restricted table mutation. | Spoofing remains possible by design; never claim WOM proves API caller identity. |
| F02, F04, F08 session/profile races | Immutable generation-stamped session, canceled delayed verification, guarded completion, profile capture before PB I/O, reset on logout/profile switch/disable. | JUnit delayed-verification test and profile-isolated outbox test; live account-switch acceptance remains. |
| F03, F13 PB correlation/grammar | Pure same-tick correlator consumes both halves; team variants in either order; current color tokens; numeric/time bounds and locale-stable formatting. Failed uploads trigger backfill retry. | Fixtures cover order, orphan expiry, consumption, reset, tokens, bounds and locale. Real raid/Sepulchre message samples remain in-game release checks. |
| F05–F07 LFG integrity/races | Single board RPC snapshot, serialized transitions, stable operation receipts, party versions, atomic formation and validated switching; refresh single-flight and lifecycle epoch. | SQL behavior tests; Java late-refresh test; real PostgreSQL concurrency test in CI. |
| F09–F10 enable switch/outcomes | Gate polling/actions by settings and captured session; immutable party IDs for disband; typed RPC outcomes distinguish conflict from HTTP success. | JUnit stale-session and HTTP-200-domain-conflict tests. Already-delivered requests cannot be recalled. |
| F11 role mismatches | Mode-local wildcards, explicit freeze assignment, rejected overflow and invalid host/composition, accepted-composition edit protection. | Java role fixtures and SQL role/accepted-membership tests. |
| F12 draft loss | Preserve application component across ordinary refresh; retain failed host/application forms and host-added draft; commit spinner text. | Manual UI acceptance required. A changed composition intentionally rebuilds application role choices. |
| F14–F16 drop delivery/coverage | Persistent UUID outbox, retry independent of screenshot, isolated webhook failure, world classification, long GP values, cross-channel multiplicity-aware deduplication. | Outbox restart/profile test, pure qualification/routing fixtures, deduplication tests, SQL idempotence and >32-bit GP tests. New game loot sources and pet naming still need live validation. |
| F17–F18 markup/failures | Disable Swing HTML on untrusted plain labels, preserve failed drop fetches, explicit refresh errors, bounded response parsing and safer numeric/WOM parsing. | Build verified; full visual acceptance required. Cached content is explicitly shown as possibly stale on refresh failure. |
| F19–F20 badges/pagination | Dedicated unranked badge directory, paged full-list reads, explicit stable ordering on ranked results. | SQL test with more than 20 badge holders. HTTP-fake tests cover server-imposed page caps smaller than the requested page and explicit user limits. |
| F21–F22 versioning/tests | Complete public-schema fixture, migration, clean installer, storage/cleanup SQL, JUnit suite and PostgreSQL 17 CI. | Portable SQL engine local execution passes; hosted Storage/cron and in-game checks remain deployment gates. |
| F23 statistics | Per-generation full-payload acknowledgement, one in-flight submit, periodic retry; equal-count totals/tier changes accepted server-side. | SQL metadata-change test and build. |
| F24 layout/access | Tooltip for complete drop values; wrapped refresh errors; preserved existing compact layout. | Wider visual/layout redesign is deferred pending RuneLite panel testing. |
| F25 hiscore cache | Access-order LRU; avoid accumulating callbacks on repeated in-flight lookups. | Full per-player response sharing across different activities is a future optimization; current cache remains bounded. |

## Explicit test boundary

Automated tests do not launch or log into OSRS. They cannot establish that every game chest emits the assumed event, inspect a rendered RuneLite panel, verify the user's actual cron jobs, or deploy/test Supabase Storage. Those checks are listed concretely in `DEPLOYMENT.md`. They are the reason this is an integration-test PR rather than a production rollout.

Optional screenshots and webhooks are not durable delivery guarantees. Core drops are disk queued (up to 1,000 pending events); permanent server rejections and corrupt local records are retained for inspection. LFG receipts are retained indefinitely until a future protocol explicitly defines safe request expiry. Full database backup remains required before production normalization.
