# Revision 3 M1 acceptance matrix — Town Hall foundation

Status: **PLANNED / NOT RUN**  
Implementation contract: `docs/implementation/R3_M1_TOWN_HALL_CONTRACT.md`

No row in this file is a PASS merely because it is specified. Implementation must add targeted automated evidence and owner/live evidence where required.

## Gate legend

- **AUTO** — unit/integration/serialization/network test required.
- **LIVE** — real Minecraft owner validation required.
- **BOTH** — automated proof plus owner validation required.
- **SERVER** — dedicated-server/common-code smoke required.

## I — Town identity colors and founding

| ID | Gate | Acceptance requirement | Status |
| --- | --- | --- | --- |
| I01 | AUTO | The allowed palette is exactly the 16 vanilla `DyeColor` values. | NOT RUN |
| I02 | AUTO | Primary and secondary colors must both be present once configured and must differ. Same-color selection is rejected server-side. | NOT RUN |
| I03 | BOTH | New-town founding still begins with the protected Book + shift-right-click Bell gesture and now commits name + primary + secondary only after all are valid. | NOT RUN |
| I04 | AUTO | Invalid name/color input cannot create a partial settlement or orphan civic state. | NOT RUN |
| I05 | AUTO | v2 migration creates no invented colors; existing towns load with colors unconfigured. | NOT RUN |
| I06 | BOTH | An existing R2 town remains usable for R2 Ledger observation before colors are configured. | NOT RUN |
| I07 | BOTH | Existing town can complete one-time color selection using a valid linked Ledger before a Town Hall exists. | NOT RUN |
| I08 | AUTO | Wrong/unknown Ledger cannot configure another town's colors. | NOT RUN |
| I09 | AUTO | M1 provides no arbitrary RGB path and no gameplay arithmetic depends on town colors. | NOT RUN |
| I10 | LIVE | Chosen colors survive save/full restart and remain associated with the same town UUID. | NOT RUN |

## D — Sign designation and marker semantics

| ID | Gate | Acceptance requirement | Status |
| --- | --- | --- | --- |
| D01 | AUTO | `[Hometown]` + `Town Hall` parses case-insensitively with trimmed/normalized whitespace. | NOT RUN |
| D02 | AUTO | Misspelled/unknown header or facility line is rejected; fuzzy matching is not used. | NOT RUN |
| D03 | BOTH | Lines 3–4 do not affect parsing and player text is preserved after registration. | NOT RUN |
| D04 | AUTO | Writing the sign alone creates no facility, unlock, History event, or SavedData mutation. | NOT RUN |
| D05 | BOTH | Right-clicking the valid sign face with the correct linked Ledger initiates the server validation path. | NOT RUN |
| D06 | AUTO | The interacted sign side/front-back identity is retained so editing the opposite side does not silently redefine the marker. | NOT RUN |
| D07 | LIVE | Ordinary standing/wall signs work; hanging signs work if represented by the same supported vanilla sign block-entity contract. | NOT RUN |
| D08 | AUTO | Sign outside the Ledger town, wrong dimension, unknown town, or wrong Ledger is rejected before facility mutation. | NOT RUN |
| D09 | BOTH | Successful registration visibly changes the registered sign from its default appearance using town color identity without replacing player text. | NOT RUN |
| D10 | AUTO | Registration persistence stores generic facility marker semantics (`TOWN_HALL`), not a sign-specific progression type. | NOT RUN |
| D11 | AUTO | Only one registered marker exists for unique facility type `TOWN_HALL`. | NOT RUN |
| D12 | BOTH | A second valid Hall is rejected while the existing Hall is loaded and valid. | NOT RUN |
| D13 | BOTH | If the old marker is loaded and demonstrably invalid/missing, a new valid Hall may replace it without resetting progression. | NOT RUN |
| D14 | BOTH | If the old marker's required scope is unloaded/unknown, replacement is rejected as incomplete rather than assuming the old Hall is gone. | NOT RUN |

## T — Town Hall qualification

| ID | Gate | Acceptance requirement | Status |
| --- | --- | --- | --- |
| T01 | AUTO | Candidate Hall resolves through the existing `RoomGeometry`/room detector path; no parallel room detector is introduced. | NOT RUN |
| T02 | BOTH | Incomplete/unsealed room cannot qualify. Repairing the enclosure permits revalidation without unrelated state reset. | NOT RUN |
| T03 | AUTO | Floor-area calculation counts unique usable interior standing/floor positions, not raw interior volume. | NOT RUN |
| T04 | BOTH | 19 usable floor positions fails and 20 passes with all other requirements held constant. | NOT RUN |
| T05 | AUTO | Irregular room geometry may satisfy the 20-position rule; no fixed width/length shape is required. | NOT RUN |
| T06 | AUTO | Minimum floor-area value is owned by one M1 rule/config surface and the algorithm is not hardwired to a shape. | NOT RUN |
| T07 | BOTH | Three recognized bookshelves fails; four passes. | NOT RUN |
| T08 | AUTO | Default bookshelf rule includes `minecraft:bookshelf` through a Hometown semantic tag/rule surface; inventory contents are not inspected. | NOT RUN |
| T09 | AUTO | Bookshelves on direct face-adjacent room boundary may count, preventing a solid bookshelf wall from being ignored. | NOT RUN |
| T10 | BOTH | No lectern fails; one vanilla lectern passes. A book on the lectern is not required. | NOT RUN |
| T11 | BOTH | No recognized storage block fails; one `hometown:food_storage` block passes. | NOT RUN |
| T12 | AUTO | Hall storage qualification checks storage tag membership only and never opens inventory, reads food contents, or resolves loot. | NOT RUN |
| T13 | AUTO | Every spawn-relevant usable floor position must have block light >= 1. | NOT RUN |
| T14 | BOTH | A single required spawn-relevant position at block light 0 fails; lighting it to >=1 passes. | NOT RUN |
| T15 | AUTO | Sky light/time of day cannot substitute for the required block-light rule. | NOT RUN |
| T16 | AUTO | Decorative/non-spawnable ceiling air is not incorrectly required as a lighting sample. | NOT RUN |
| T17 | BOTH | Any required unknown/unloaded block/light sample makes Hall qualification incomplete and cannot award progression. | NOT RUN |
| T18 | AUTO | Hall validation is bounded to the candidate room geometry/shared block cache and does not scan the entire town. | NOT RUN |

## P — Progression, persistence and History

| ID | Gate | Acceptance requirement | Status |
| --- | --- | --- | --- |
| P01 | AUTO | SavedData schema advances to version 3 only when implementation lands. | NOT RUN |
| P02 | AUTO | Valid v1 data still migrates through the accepted founding/History path and receives empty R3 civic defaults. | NOT RUN |
| P03 | BOTH | Valid v2 R2 world migrates to v3 with town UUID/name/dimension/bounds/founder/founding History and existing History preserved exactly. | NOT RUN |
| P04 | AUTO | v1/v2 migration fabricates no colors, facility marker, Town Hall establishment, or downstream unlock. | NOT RUN |
| P05 | AUTO | Civic state is owned inside existing `HometownSavedData`, keyed by settlement UUID; no second save-wide database is introduced. | NOT RUN |
| P06 | AUTO | Persisted civic state rejects duplicate/foreign settlement owners and malformed color invariants safely. | NOT RUN |
| P07 | BOTH | First successful Hall registration permanently awards `TOWN_HALL`, `NOTICE_BOARD`, `CIVIC_PROJECTS`, `STORAGE`, and `ANIMAL_FARMS`. | NOT RUN |
| P08 | LIVE | Administration Progression view presents Notice Board, Civic Projects, Storage, and Animal Farms as unlocked but not yet implemented in M1. | NOT RUN |
| P09 | AUTO | No commerce/traveler/recruitment/specialization unlock is awarded by M1 Hall establishment. | NOT RUN |
| P10 | AUTO | Current Hall validity is derived live and is not persisted as authoritative truth. | NOT RUN |
| P11 | BOTH | Breaking/removing a Hall requirement suspends Administration but does not remove permanent unlocks. | NOT RUN |
| P12 | BOTH | Repair/revalidation restores Administration without re-awarding unlocks. | NOT RUN |
| P13 | BOTH | First establishment appends exactly one structural `TOWN_HALL_ESTABLISHED` History event. | NOT RUN |
| P14 | AUTO | Revalidation, temporary invalidation, restoration, or replacement after first establishment does not append duplicate first-establishment events. | NOT RUN |
| P15 | AUTO | Marker + permanent unlocks + first-establishment History are committed in one server-thread transition before success is reported. | NOT RUN |
| P16 | LIVE | Colors, marker, unlocks, and History survive save/full client exit/restart and in-world `/reload`. | NOT RUN |

## A — Town Administration GUI/session

| ID | Gate | Acceptance requirement | Status |
| --- | --- | --- | --- |
| A01 | BOTH | Holding the correct linked Ledger and right-clicking a lectern in the currently valid registered Hall opens Town Administration. | NOT RUN |
| A02 | AUTO | Wrong Ledger, wrong town, wrong dimension, stale lectern, excessive interaction distance, or unrelated lectern cannot open Administration. | NOT RUN |
| A03 | BOTH | Registered sign missing/edited so grammar no longer matches prevents Administration from opening while preserving earned progression. | NOT RUN |
| A04 | BOTH | Hall invalid because of enclosure/area/bookshelf/lectern/storage/light requirement prevents Administration from opening and returns useful reason(s). | NOT RUN |
| A05 | BOTH | Unknown/unloaded Hall scope prevents Administration rather than opening stale authoritative state. | NOT RUN |
| A06 | LIVE | Root Administration view shows town name and readable primary/secondary color identity accents. | NOT RUN |
| A07 | LIVE | Progression page shows Town Hall established and the four M1 downstream unlock nodes with clear not-yet-implemented messaging. | NOT RUN |
| A08 | AUTO | Opening Administration performs one explicit server validation/snapshot request; local tab/page navigation does not rescan the world. | NOT RUN |
| A09 | AUTO | Client cannot award unlocks or directly mutate civic SavedData through Administration payloads. | NOT RUN |
| A10 | LIVE | GUI remains navigable at practical GUI scales with no clipped essential controls/text or broken hitboxes. | NOT RUN |

## Q — Loaded-only, security, concurrency and performance

| ID | Gate | Acceptance requirement | Status |
| --- | --- | --- | --- |
| Q01 | AUTO | M1 world access uses loaded-only APIs and introduces no Hometown chunk ticket/force-load call. | NOT RUN |
| Q02 | LIVE | Unloading part of the candidate/registered Hall produces incomplete/unavailable validation and does not pull chunks back in. | NOT RUN |
| Q03 | AUTO | Registration cannot succeed from partial room geometry or failed block/light samples. | NOT RUN |
| Q04 | AUTO | Facility validation does not inspect unrelated inventories or generate unresolved loot. | NOT RUN |
| Q05 | AUTO | Repeated Administration page navigation performs zero additional Hall scans after the opening snapshot. | NOT RUN |
| Q06 | AUTO | Two concurrent first-registration requests serialize so exactly one performs the first establishment and exactly one History event exists. | NOT RUN |
| Q07 | AUTO | Unknown/malformed facility type or spoofed town UUID is rejected by bounded codec/server validation. | NOT RUN |
| Q08 | SERVER | Dedicated server loads M1 common code with no client-class crash and completes startup. | NOT RUN |
| Q09 | SERVER | Dedicated-server resource `/reload` remains clean after adding M1 tags/rules. | NOT RUN |
| Q10 | BOTH | Candidate-room validation remains bounded and does not exhibit town-wide scan growth; representative owner fixture records elapsed/work evidence if new profiling counters are introduced. | NOT RUN |

## R — R2 regression protection

| ID | Gate | Acceptance requirement | Status |
| --- | --- | --- | --- |
| R01 | AUTO | Existing R2 founding identity and Ledger UUID linking remain valid after M1 changes. | NOT RUN |
| R02 | AUTO | Housing/Privacy room semantics are unchanged by Town Hall qualification. | NOT RUN |
| R03 | AUTO | Food Reserves/Variety/Growing remain observational; Hall storage check does not alter Food inventories or calculations. | NOT RUN |
| R04 | AUTO | Safety/Comfort/Commerce/Prosperity calculations remain unchanged unless explicitly read for display. | NOT RUN |
| R05 | AUTO | Existing R2 Ledger navigation/cache tests continue to pass. | NOT RUN |
| R06 | BOTH | Existing 0.9.1 world remains playable through migration with no forced reset and no duplicate founding/History records. | NOT RUN |
| R07 | AUTO | Normal complete Gradle test suite passes with no existing test removed or disabled to obtain green. | NOT RUN |
| R08 | AUTO | Normal Gradle build succeeds for the exact M1 playtest head. | NOT RUN |

## Completion rule

M1 remains incomplete while any required row is FAIL or NOT RUN. Automated tests may prove logic/serialization/security but do not substitute for the LIVE rows covering actual sign rendering, GUI behavior, real room/light/loading behavior, world migration, and restart persistence.