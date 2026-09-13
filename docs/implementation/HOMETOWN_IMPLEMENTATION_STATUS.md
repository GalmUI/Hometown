# Hometown implementation status

## Current authoritative checkpoint — 2026-09-12

**Specification Revision:** Revision 2 complete; Revision 3 is next authority  
**Current Milestone:** R2 M6 — Integration and delivery  
**Status:** **COMPLETE**  
**Current playtest version:** `0.9.1`  
**Final R2 implementation/test head:** `f013e6a5731e9566cbe7d83e1aca03fe4ecaee93`  
**Final R2 evidence:** `docs/evidence/r2-m6-2026-09-12/README.md`, `ACCEPTANCE_MATRIX.md`, `LIVE_VALIDATION.md`

This file is the single current implementation-status register. Detailed historical milestone records remain available in Git history and the retained `docs/evidence/` directories; stale historical checkpoint prose is intentionally not duplicated here.

## Milestones

| Milestone | State | Acceptance summary |
| --- | --- | --- |
| R2 M0 | COMPLETE | Baseline/import/persistence/build foundation accepted |
| R2 M1 | COMPLETE | Shared observation and Safety accepted |
| R2 M2 | COMPLETE | Comfort accepted and owner validated |
| R2 M3 | COMPLETE | Food Variety/Growing accepted and owner validated |
| R2 M4 | COMPLETE | Commerce accepted and owner validated |
| R2 M5 | COMPLETE | Prosperity/History accepted and owner validated; final patch `0.8.1` |
| R2 M6 | COMPLETE | Integration/delivery, dedicated server, live loading/loot/lifecycle/UI/performance accepted; final R2 version `0.9.1` |
| R3 | NOT_STARTED | Eligible to begin from the accepted R2 checkpoint after normal branch integration |

## Final R2 automated gate

GitHub Actions run **34733522239** on exact `0.9.1` implementation/test head `f013e6a5731e9566cbe7d83e1aca03fe4ecaee93`:

- Gradle `test`: **PASS — 206/206**
- Gradle `build`: **PASS**
- Java: 21
- Minecraft: 1.21.1
- NeoForge: 21.1.250

The final post-playtest changes after that head are documentation/evidence-only and do not alter the built artifact.

## Final R2 server/lifecycle gate

- Dedicated NeoForge server startup: **PASS**
- No client-dist crash loading Hometown server-side: **PASS**
- Actual server resource `/reload`: **PASS**
- Actual server shutdown: **PASS**
- Singleplayer save/full-exit/reload: **PASS**
- Singleplayer in-world `/reload`: **PASS**
- Town identity/retained History after restart: **PASS**

Dedicated-server/RCON evidence is retained under the M6 evidence record.

## Final R2 live integration gate

Owner completed the integrated Ledger sweep across Overview, Residents, Housing, Food Reserves, Variety, Growing, Safety, Comfort, Commerce, Prosperity and History.

Key live acceptance outcomes:

- local Ledger subnavigation reused one opened observation rather than rescanning;
- unresolved loot storage remained unresolved after Hometown observation and correctly produced partial Food authority;
- unloaded fringe chunks produced partial/uncertain results rather than forced loading;
- Housing, Safety, Comfort, Food, Commerce and Prosperity remained observationally isolated according to their contracts;
- save/restart and resource reload preserved usable town state;
- final `0.9.1` UI polish labels were visually confirmed without the four known fixed-label clipping defects.

See `docs/evidence/r2-m6-2026-09-12/README.md` for exact fixtures and observations.

## Final R2 performance gate

Owner-reported test environment:

- CPU: **AMD Ryzen 9 3900X**
- Minecraft/Java memory allocation: **approximately 6 GB**

Representative Oured fresh Ledger observations:

- samples: 34.559 ms, 8.588 ms, 11.282 ms, 7.205 ms
- average: **15.409 ms**
- worst: **34.559 ms**

Dense Osea fresh Ledger observations:

- samples: 32.401 ms, 9.679 ms, 7.401 ms
- average: **16.494 ms**
- worst: **32.401 ms**

Both representative and dense fixtures remained below the R2 50 ms investigation threshold, with bounded work counters and no forced chunk loading.

## Current implementation ownership

| Contract | Current owner |
| --- | --- |
| Founding/identity/access | `interaction/BellInteractionHandler`, `settlement/SettlementManager`, `SettlementValidator`, `Settlement` |
| Settlement persistence | `settlement/HometownSavedData` |
| Ledger orchestration/cache/generation | `settlement/TownLedgerService` |
| Residents / Commerce facts | `settlement/SettlementQueries`, `SettlementScanner`, `SettlementStats`, `commerce/CommerceEvaluator` |
| Housing / Privacy | `housing/HousingScanner`, `HousingSnapshot`, established room owners |
| Safety | `safety/SafetyCollector`, `SafetyEvaluator`, `SafetySnapshot` |
| Comfort | `comfort/ComfortCollector`, `ComfortEvaluator`, `ComfortSnapshot`, `ComfortRules` |
| Food Reserves / Variety / Growing | Food scanner/rules/snapshot plus `FoodVarietyEvaluator`, `FoodGrowingCollector`, `FoodGrowingEvaluator`, `CropRules` |
| Prosperity | `prosperity/ProsperityEvaluator`, `ProsperitySnapshot` |
| History | `history/HistoryTracker`, typed `HistoryEvent`; durable state in `HometownSavedData` |
| Config | `config/HometownServerConfig` |
| Network | `network/HometownNetworking` and bounded payloads |
| Client Ledger | `client/TownLedgerScreen` plus module presentation helpers |
| Debug/performance | existing debug owners plus `command/M5DebugCommands`, `M6DebugCommands` |

## Accepted architectural boundary carried into Revision 3

Revision 3 must build on, not bypass, the accepted Revision 2 contracts:

- server-authoritative town truth;
- loaded-only observation with no Hometown force-loading or chunk tickets;
- shared established collectors rather than parallel competing scans;
- explicit partial/unavailable semantics instead of invented zero values;
- no hidden world scan from cached Ledger navigation/debug views;
- Prosperity remains derived from its defined authoritative inputs;
- History advances only from qualifying fresh normal observations;
- R2 player-facing systems remain observational unless Revision 3 explicitly authorizes a gameplay effect.

## Next action

**Revision 2 is complete.**

The next implementation work is **Revision 3 — Progression and Town Operations**. Start it on a new scoped branch from the accepted/merged R2 checkpoint, first auditing the Revision 3 milestone/order and preserving all protected R2 behavior.
