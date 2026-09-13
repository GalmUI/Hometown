# Hometown implementation status

## Current authoritative checkpoint — 2026-09-12

**Specification Revision:** Revision 3 active; Revision 2 complete baseline  
**Current Milestone:** R3 M1 — Town Hall foundation  
**Status:** **READY FOR IMPLEMENTATION — contract, persistence plan, and acceptance matrix defined; gameplay code not started**  
**Current playable baseline:** `0.9.1` (accepted R2)  
**Development branch:** `r3-m1`  
**M1 contract:** `docs/implementation/R3_M1_TOWN_HALL_CONTRACT.md`  
**M1 acceptance matrix:** `docs/evidence/r3-m1-2026-09-12/ACCEPTANCE_MATRIX.md`

This file is the single current implementation-status register. Detailed historical milestone records remain available in Git history and retained `docs/evidence/` directories.

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
| R3 M1 | READY_FOR_IMPLEMENTATION | Town colors, sign-based Town Hall designation, Hall qualification, persistent progression, Administration GUI foundation |
| R3 later milestones | NOT_STARTED | Storage/Storehouse behavior, Animal Farms, Notice Board/projects, operations/meals, travelers, markets/recruitment, specialization remain downstream |

## Accepted R2 baseline

Final R2 implementation/test head: `f013e6a5731e9566cbe7d83e1aca03fe4ecaee93`.

GitHub Actions run **34733522239** on exact `0.9.1` implementation/test head:

- Gradle `test`: **PASS — 206/206**
- Gradle `build`: **PASS**
- Java: 21
- Minecraft: 1.21.1
- NeoForge: 21.1.250

The final R2 branch closure and documentation-only commits also passed the normal Build and Test workflow before R3 branching.

Owner live acceptance retained under `docs/evidence/r2-m6-2026-09-12/` includes dedicated-server startup/reload, unresolved-loot non-mutation, loaded-fringe/no-force-loading, save/full-restart/reload, complete Ledger UI sweep, and representative/dense performance on an AMD Ryzen 9 3900X with approximately 6 GB allocated to Minecraft/Java.

## R3 M1 owner-approved design checkpoint

The M1 implementation contract records the current decisions:

- vanilla signs are the first civic facility marker; custom plaque/block art is deferred but the backend must remain marker-agnostic;
- each town gains primary + secondary identity colors chosen from the 16 vanilla dye colors, with different colors required;
- new towns choose colors during founding; migrated R2 towns choose them once before registering their first R3 facility;
- sign grammar is `[Hometown]` / `Town Hall`; player lines 3–4 remain freeform;
- the linked Town Ledger is presented to the sign to request server-authoritative registration;
- successful registration visibly changes the sign using town color identity;
- the Town Hall is one complete existing room, not a new building detector or two-room topology system;
- provisional minimum size is 20 usable interior floor positions;
- qualification requires at least 4 recognized bookshelves, 1 lectern, 1 recognized `hometown:food_storage` block, and block light >=1 at every spawn-relevant usable floor position;
- unknown/unloaded room or light data can never qualify and Hometown must not force-load it;
- first successful establishment permanently unlocks Town Hall plus Notice Board, Civic Projects, Storage, and Animal Farms progression nodes;
- downstream nodes are visible as unlocked but may remain explicitly not implemented during M1;
- current Hall validity is separate from permanent progression: damage/unloading suspends Administration but never revokes earned unlocks;
- Administration opens physically by using the linked Ledger on a lectern belonging to the currently valid registered Town Hall;
- the first Administration GUI contains Hall status + Progression and uses town identity colors as readable accents;
- M1 adds one first-establishment History event and must not spam events for temporary invalidation/restoration.

## R3 M1 persistence plan

M1 will introduce Hometown SavedData **version 3** when code lands.

Preferred architecture:

- keep the accepted immutable `Settlement` identity record stable;
- add one per-town `TownCivicState` / `TownProgressionState` map inside existing `HometownSavedData`, keyed by settlement UUID;
- persist optional/unconfigured colors, progression unlock IDs, and generic facility markers;
- derive current Town Hall validity from loaded world state rather than persisting it as authoritative truth.

Migration requirements:

- continue to accept valid v1 and v2 data;
- preserve all R2 town identity and History exactly;
- add empty/default R3 civic state only;
- fabricate no colors, Hall marker, Town Hall establishment, or downstream unlock;
- new towns commit name/colors/civic state atomically with founding;
- first Hall establishment commits marker + unlocks + first-establishment History in one server-thread transition.

## Current implementation ownership

| Contract | Current / M1 owner |
| --- | --- |
| Founding/identity access | existing `interaction/BellInteractionHandler`, `settlement/SettlementManager`, `SettlementValidator`; extend flow for colors without replacing founding gesture |
| Immutable town identity | existing `settlement/Settlement` — preserve R2 record shape if practical |
| Save-wide persistence | existing `settlement/HometownSavedData`; add v3 civic-state map here |
| Civic progression/facilities | new narrowly scoped R3 owners, persisted through `HometownSavedData`; no second database |
| Room geometry | existing `housing/HousingScanner` + `room/RoomGeometry`; no second detector |
| Hall block/light observations | reuse bounded `observation/BlockObservationCache` pattern |
| Recognized storage | existing `food/FoodScanner.FOOD_STORAGE` semantic tag; do not inspect contents for Hall qualification |
| Existing Ledger | `settlement/TownLedgerService`, network payload owners, `client/TownLedgerScreen`; preserve R2 observational behavior |
| Town Administration | new M1 server snapshot/session + client screen, opened only through a valid Hall lectern |
| History | existing `history` owners + durable state in `HometownSavedData`; add one Hall-establishment event type |
| Config/rules | existing `config/HometownServerConfig` plus one M1 Hall rule owner for tuneable minimum floor area if required |
| Network/security | existing `network/HometownNetworking`; all new mutations server-authoritative and bounded |

## M1 acceptance state

The formal matrix currently contains only **NOT RUN** requirements. That is intentional: design completion is not implementation completion.

Major required gates include:

- founding/color selection and v2→v3 migration;
- sign parsing, visible registration, unique marker/replacement rules;
- exact Hall room/area/bookshelf/lectern/storage/light qualification;
- permanent unlock versus current validity behavior;
- Town Administration access/session/UI behavior;
- History exactly-once semantics;
- loaded-only/no-force-loading and concurrent-registration security;
- dedicated-server startup/reload;
- complete R2 regression suite;
- owner live save/restart/invalidation/restoration validation.

See `docs/evidence/r3-m1-2026-09-12/ACCEPTANCE_MATRIX.md` for the individual acceptance IDs.

## Accepted architectural boundary carried into Revision 3

Revision 3 must build on, not bypass, the accepted Revision 2 contracts:

- server-authoritative town truth;
- loaded-only observation with no Hometown force-loading or chunk tickets;
- shared established collectors rather than parallel competing scans;
- explicit partial/unavailable semantics instead of invented zero values;
- no hidden world scan from cached Ledger navigation/debug views;
- Prosperity remains derived from its defined authoritative inputs;
- History advances only from qualifying transitions/observations explicitly authorized by the relevant R3 contract;
- R2 player-facing systems remain observational unless Revision 3 explicitly authorizes a gameplay mutation.

## Next implementation sequence

1. Add pure civic-state/progression/facility data types and SavedData v3 migration tests first.
2. Extend founding/existing-town setup for the two dye colors without changing the protected founding gesture.
3. Implement bounded Town Hall facility qualification over existing room geometry.
4. Implement sign parsing/registration, visible town-color confirmation, unique-marker/replacement rules, and exactly-once History transition.
5. Implement Town Administration server snapshot/session and minimal Hall Status + Progression GUI.
6. Add targeted automated tests, run the full normal suite/build, then execute the M1 owner/live matrix before calling the milestone complete.

Do not begin downstream Storage, Animal Farm, Notice Board/project, daily-meal, traveler, market/recruitment, or specialization behavior until the M1 foundation is accepted or a later owner amendment explicitly changes the order.