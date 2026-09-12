# R2 M6 integration evidence — 2026-09-12

Milestone: **Revision 2 M6 — Integration and delivery**

Current status: **PARTIAL — automated/server gates green; required live UI/world/performance validation remains.**

Playtest version: **0.9.0**

Versioned 0.9.0 implementation head: `ff5d5fbd79999d1a2e3272c76db112b31d600dbd`

## Acceptance audit

`ACCEPTANCE_MATRIX.md` maps all 96 R2 acceptance IDs (B/Q/S/C/F/E/P/H/O) to targeted automated evidence, prior owner evidence, or a genuinely remaining M6 live/server/profile fixture.

The audit found that most classification, arithmetic, state-machine, serialization, protocol, configuration, bounds and isolation cases already have targeted automated coverage. The remaining M6 blockers are operational: final integrated UI/client behavior, selected real world/loading/loot fixtures, final save/restart lifecycle, and measured representative/dense performance.

`LIVE_VALIDATION.md` is the reproducible owner procedure for those remaining gates.

## Normal automated gate

GitHub Actions run **34726245824** on exact versioned `0.9.0` commit `ff5d5fbd79999d1a2e3272c76db112b31d600dbd` completed successfully.

- Gradle `test`: **PASS**
- Gradle normal `build`: **PASS**
- Java: 21
- Minecraft: 1.21.1
- NeoForge: 21.1.250

Earlier M6 integration checkpoints also passed after adding the acceptance matrix, cached performance diagnostics, and configuration/datapack documentation.

## O05 performance diagnostic

M6 added a diagnostic-only cached performance profile for a normal fresh Ledger observation.

Command:

`/hometown debug performance [uuid]`

Behavior:

- Timing begins immediately before the existing normal fresh Ledger collection and ends after History evaluation/final immutable History capture.
- Timing and counters are memory-only and are never persisted as town truth.
- Same-tick/cooldown reuse preserves the original observation timing rather than pretending a new scan occurred.
- The performance command reads the player's cached normal Ledger observation; the command itself performs **no world scan** and does not advance History.
- Player/server release removes the cached diagnostic state.

Reported evidence includes settlement/dimension/bell/radius, generation/observation time/age, elapsed milliseconds, population/enclosed beds, Food containers/storage scanned, Comfort room counts, loaded/required chunks, entity work, shared new-block work and Growing work counters.

Implementation/test checkpoint `7589555f7fc7b47a1e8e0f5f29f07a2794f2b42d` passed the complete normal test/build workflow in Actions run **34725864946**.

## O01/O04 dedicated-server and resource-reload smoke

Dedicated-server validation was intentionally performed on isolated branch `r2-m6-server-smoke`; that branch is test harness only and must not be merged.

Final RCON-based smoke workflow run **34726154745**, job **103640502723**, completed **PASS**.

Observed behavior:

- Minecraft 1.21.1 dedicated NeoForge server started under Java 21.
- Hometown loaded in the server mod list with no accidental client-class crash.
- Server reached `Done (9.644s)! For help, type "help"`.
- Local RCON issued the actual Minecraft `reload` command.
- Server logged `[Rcon: Reloading!]`, then reloaded 1290 recipes and 1399 advancements.
- Local RCON issued `stop`; server accepted shutdown.
- Harness completed without a Hometown/client-dist/resource-reload failure.

The isolated smoke used production-code checkpoint `7589555...`; subsequent `r2-m6` changes through exact 0.9.0 were documentation and the version stamp only. Exact 0.9.0 separately passed the normal complete test/build gate above.

## Configuration/tag delivery documentation

`docs/CONFIGURATION_AND_DATAPACKS.md` now documents the supported R2 server configuration, exact Comfort/Food/Safety tag paths, version-1 Comfort state-predicate and custom-crop formats, reload behavior, compatibility boundary and M6 cached performance command.

The documentation explicitly preserves the R2 boundary: Hometown requires no external furniture/farming/economy/NPC mod and does not provide fuzzy mod-name compatibility or gameplay effects.

## Prior owner evidence reused by M6

M5 owner validation already supplied real-world evidence for the highest-risk persistence/History path on Oured: v1→v2 save migration, founding preservation, Prosperity arithmetic, 200-tick Population confirmation, confirmed event persistence without duplication, Housing-shortage start/resolution and normal population changes.

Earlier milestone owner testing also exercised real Comfort, Food Variety/Growing and Commerce observations. M6 still requires the final integrated spot-checks in `LIVE_VALIDATION.md`; prior evidence is not used to falsely mark an unexecuted final fixture PASS.

## Remaining required gates

M6 and Revision 2 are **not complete yet**. Before completion, owner/live evidence must close the remaining M6 LIVE / PROFILE items in the acceptance matrix, including:

- final integrated Ledger/UI sweep across practical GUI scales and all Development/History navigation;
- reversible cross-system regression spot-checks;
- unresolved-loot non-mutation fixture;
- loaded-fringe/no-force-loading observation fixture;
- final 0.9.0 save/exit/reload and in-world `/reload` sanity;
- representative Oured performance measurements using `/hometown debug performance`;
- deliberately dense test-town performance measurement and bounded-ceiling behavior.

The representative target is a typical fresh Ledger request below one 50 ms server tick on the documented test machine. A measured miss requires investigation; it must not be hidden by raising budgets.

Any functional code/data fix discovered after owner testing starts requires a `0.9.1` patch build and targeted retest.

## Current conclusion

**M6 PARTIAL.** Automated integration, normal build, documentation, dedicated-server startup and real resource reload are green. The branch is ready for the 0.9.0 owner/live validation pass, but R2 must not be declared COMPLETE and R3 must not begin until every required remaining live/profile gate is PASS with evidence.
