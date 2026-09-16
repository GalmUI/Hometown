# Hometown implementation status

## Current authoritative checkpoint — 2026-09-16

**Specification Revision:** Revision 3 active; Revision 2 complete baseline  
**Current Milestone:** R3 M2 — Notice Board & Civic Projects  
**Current implementation slice:** `0.13.0` — Notice Board foundation  
**Status:** **IMPLEMENTED / CI GREEN — owner live validation in progress**  
**Current accepted main baseline:** `0.12.0` — completed R3 M1, merged to `main`  
**Development branch:** `r3-m2`  
**0.13.0 contract:** `docs/implementation/R3_0_13_0_NOTICE_BOARD_FOUNDATION.md`  
**M2 acceptance matrix:** `docs/evidence/r3-m2-2026-09-16/ACCEPTANCE_MATRIX.md`

This file is the single current implementation-status register. Detailed historical contracts and evidence remain available in `docs/implementation/`, `docs/evidence/`, and Git history.

## Milestones

| Milestone | State | Acceptance summary |
| --- | --- | --- |
| R2 M0–M6 | COMPLETE | Revision 2 accepted through `0.9.1`; final integration/delivery, dedicated-server, UI, lifecycle and performance validation complete |
| R3 M1 | COMPLETE | Town identity/colors, Town Hall, Administration, Storage, Daily Meal, Census, Animal Farm and livestock production implemented through `0.12.0`, owner live-tested and merged to `main` |
| R3 M2 | IN_PROGRESS | Notice Board foundation implemented in `0.13.0`; Civic Project persistence/contribution/effects remain downstream within M2 |
| R3 later milestones | NOT_STARTED | Travelers, markets/recruitment, specialization and later town-economy systems remain downstream |

## Accepted R2 baseline

Final R2 implementation/test head: `f013e6a5731e9566cbe7d83e1aca03fe4ecaee93`.

GitHub Actions run **34733522239** on the exact `0.9.1` implementation/test head:

- Gradle `test`: **PASS — 206/206**
- Gradle `build`: **PASS**
- Java: 21
- Minecraft: 1.21.1
- NeoForge: 21.1.250

The Revision 2 architecture remains authoritative unless a Revision 3 contract explicitly changes it.

## Completed R3 M1 baseline — `0.12.0`

R3 M1 ultimately grew beyond its original Town Hall-only planning boundary before milestone bookkeeping was reconciled. The completed and merged baseline contains:

- town primary/secondary colors and SavedData v3 civic state;
- sign-based Town Hall registration and live qualification;
- permanent civic progression unlocks with transient facility validity kept separate;
- Town Administration and facility-detail UI foundations;
- registered Storage and its operational role as the primary town food reserve;
- Daily Meal scheduled resource consumption;
- persistent Town Census support for operations;
- Animal Farm building/paddock qualification;
- cow/pig livestock observation, breeding/cull policy, named-adult and young-animal protection;
- scheduled surplus livestock production with transactional output-storage gating;
- persistence/save-reload protection against duplicate terminal work cycles.

Owner live validation of the final `0.12.0` livestock slice confirmed surplus culling, named-animal protection, baby protection, no-storage/no-cull behavior and save/reload continuity. Testing also established that accelerated operation tests should use `/time add` rather than rewinding calendar time with `/time set`.

R3 M1 was merged to `main` at commit `171d0d3928a9bccaca70875f062fb3b158b20cf4`.

## Current R3 M2 plan

M2 gives the already-operational town a player-facing civic-work loop rather than expanding immediately into travelers, markets or specialization.

Planned slices:

1. **`0.13.0` — Notice Board foundation**: register/persist/revalidate a town Notice Board, expose facility detail, and show established/active status in Town Administration.
2. **`0.13.1` — Civic Project state**: durable per-town project identity/state/requirements/progress with migration and save/reload tests.
3. **`0.13.2` — Contributions & completion**: explicit server-authoritative player contributions, atomic inventory mutation, exact completion semantics, and multiplayer-safe persistence.
4. **`0.13.3` — Civic effects**: at least one complete project path from posting through contribution to a durable town-level effect and History result.

The milestone should not be considered complete until one real civic project can be posted, contributed to across multiple interactions/save cycles, completed exactly once, and produce its intended durable town effect.

## `0.13.0` Notice Board implementation checkpoint

Implementation commit: `9670327d47ecd267a9ccf6c715e45124ac6ab0eb`.

GitHub Actions run **35056996223** on that commit:

- Gradle `test`: **PASS**
- Gradle `build`: **PASS**
- version: `0.13.0`

The Notice Board contract currently defines:

- vanilla sign face grammar `[Hometown]` / `Notice Board`; lines 3–4 remain freeform;
- registration by using the town-linked Ledger on the intended sign face;
- required existing `NOTICE_BOARD` progression unlock from Town Hall establishment;
- one current Notice Board marker per town;
- valid existing board blocks replacement; unavailable existing board fails closed; invalid/missing board can be replaced;
- generic `FacilityMarker` persistence with no SavedData version bump;
- live loaded-only sign validation with no force-loading;
- visible town-color registration feedback;
- Facility Detail support and Town Administration unlock/established/active status;
- Civic Projects remain explicitly unimplemented in this slice;
- no Notice Board History event is invented before M2 project/history semantics are defined.

Owner live validation in Osea has now confirmed the happy-path registration, visible sign-color feedback, Facility Detail `Established — Active` state, Town Administration Notice Board state, refusal to register a second board while the original remains valid, successful replacement after invalidating the original, and successful loading/running of the `0.13.0` jar.

Remaining live acceptance before `0.13.0` can be called complete:

- save/full-restart persistence of the replacement Notice Board;
- unloaded/unavailable existing-board fail-closed behavior;
- a representative R3 M1 smoke check on the `0.13.0` build.

## Accepted architectural boundary carried into Revision 3

Revision 3 continues to build on, not bypass, the accepted Revision 2 contracts:

- server-authoritative town truth and gameplay mutation;
- loaded-only observation with no Hometown force-loading or chunk tickets;
- shared established collectors rather than parallel competing scans;
- explicit partial/unavailable semantics instead of invented zero values;
- no hidden world scan from cached Ledger navigation/debug views;
- persistent progression is separate from transient facility availability;
- History advances only from qualifying transitions explicitly authorized by the relevant R3 contract;
- operations that consume or produce items must fail safely without partial mutation.

## Immediate next sequence

1. Finish the remaining `0.13.0` owner-live gates: full restart persistence, unavailable-board fail-closed behavior, and one representative M1 smoke check.
2. Record the final result in the M2 acceptance matrix and patch any defect found.
3. Once `0.13.0` is accepted, design and implement `0.13.1` Civic Project persistence/state on the same `r3-m2` milestone branch.
