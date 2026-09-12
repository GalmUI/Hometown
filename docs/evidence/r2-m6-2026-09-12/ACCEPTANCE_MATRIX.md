# R2 M6 acceptance matrix — initial audit

Date: 2026-09-12

Milestone: **Revision 2 M6 — Integration and delivery**

Starting branch head: `1894eb213fa392f031fdfe6aa1cad83fadbc7292` (`0.8.1`, final M5 head)

This matrix separates requirements that are already represented by targeted automated coverage from requirements that still need a real integrated Minecraft/server fixture. Nothing in this file marks M6 complete. Every automated item must pass again on the final M6 head, and every item marked M6 LIVE / SERVER / PROFILE must receive observed evidence before M6 can be COMPLETE.

Legend:

- **AUTO** — targeted automated coverage exists; rerun on exact M6 head.
- **PRIOR LIVE** — owner already observed the behavior during an earlier milestone; useful evidence, but M6 may still require an integration spot-check.
- **M6 LIVE** — final integrated Minecraft fixture still required.
- **SERVER** — dedicated-server/lifecycle execution required.
- **PROFILE** — measured performance evidence required.

## Protected baseline — B01–B08

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| B01 | `SettlementPersistenceTest`, `HistoryPersistenceTest`; M5 owner validated v1→v2 migration, founding preservation, confirmed History persistence and no duplicate event | AUTO + PRIOR LIVE; repeat save/restart as part of O04 |
| B02 | `HousingIntegrationTest` | AUTO + M6 LIVE spot-check with final integrated build |
| B03 | `PrivacyIntegrationTest` | AUTO + M6 LIVE spot-check with final integrated build |
| B04 | `RoomDetectorTest`, `HousingIntegrationTest`, Comfort/Housing regressions | AUTO + M6 LIVE door/window regression |
| B05 | `FoodScannerTest` | AUTO + PRIOR LIVE; final Food sanity in M6 UI pass |
| B06 | `FoodScannerTest`, `FoodRobustnessTest` | AUTO |
| B07 | `FoodScannerTest`, `FoodRobustnessTest` | AUTO + M6 LIVE storage sanity |
| B08 | `FoodScannerTest`, `FoodRobustnessTest` | AUTO + M6 LIVE unresolved-loot non-mutation fixture |

## Completeness/loading — Q01–Q06

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| Q01 | loaded-only/partial-scope tests across Food/Safety/Growing | AUTO + M6 LIVE fringe-unloaded-chunk fixture |
| Q02 | Food denominator-quality regressions plus Prosperity strict-input tests | AUTO |
| Q03 | Safety/Comfort/Growing budget-limit fixtures | AUTO |
| Q04 | module-isolation fixtures across Food/Comfort/Prosperity | AUTO + integrated M6 spot-check |
| Q05 | Housing/Food/Growing/Safety bounds fixtures, including negative/out-of-bounds cases | AUTO |
| Q06 | loaded-only `getChunkNow` paths and request-cache tests | AUTO + M6 LIVE loaded-chunk/ticket observation |

## Safety — S01–S12

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| S01 | `SafetyCollectorTest` threat/protector counting and UUID deduplication | AUTO + M6 LIVE simple zombie/creeper/golem scene |
| S02 | `SafetyCollectorTest` bounds/alive/removed rejection | AUTO |
| S03 | `SafetyCollectorTest` neutral behavior plus shipped entity-tag resources | AUTO |
| S04 | `SafetyCollectorTest` inclusion/exclusion precedence and player exclusion | AUTO |
| S05 | `SafetyCollectorTest` exact lighting percentage arithmetic | AUTO + M6 LIVE lighting spot-check |
| S06 | `SafetyCollectorTest` block-light-only semantics | AUTO |
| S07 | `SafetyCollectorTest` invalid sample handling | AUTO |
| S08 | `SafetyCollectorTest` no-bed applicability | AUTO |
| S09 | Safety/Housing independence tests and integrated Ledger tests | AUTO + M6 LIVE cross-system spot-check |
| S10 | Safety partial/unloaded-scope tests | AUTO |
| S11 | raw-vs-displayed lighting authority tests | AUTO |
| S12 | module/shared budget tests and debug isolation | AUTO |

## Comfort — C01–C10

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| C01 | `ComfortEvaluatorTest` bare-room fixture | AUTO + PRIOR LIVE |
| C02 | `ComfortEvaluatorTest` caps/defaults | AUTO |
| C03 | `ComfortEvaluatorTest` standalone 100% fixture | AUTO |
| C04 | `ComfortEvaluatorTest` optional Seating/Tables denominator behavior | AUTO |
| C05 | `ComfortCollectorTest` + evaluator bed-weighted aggregation and room boundaries | AUTO |
| C06 | `ComfortRulesTest` exact lit-state predicates | AUTO |
| C07 | `ComfortRulesTest` exact shipped membership/exclusion behavior | AUTO |
| C08 | Comfort partial/N/A/unknown distinctions | AUTO |
| C09 | invalid-rule reload retention and bounded scope tests | AUTO; real reload also covered under O01 |
| C10 | `ComfortLedgerScreenTest`, Housing regressions, room pagination | AUTO + PRIOR LIVE + M6 LIVE UI/window spot-check |

## Food Variety/Growing — F01–F10

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| F01 | `FoodVarietyEvaluatorTest` threshold equality/bands | AUTO + PRIOR LIVE arithmetic |
| F02 | `FoodVarietyEvaluatorTest` priority/collision/disabled winner | AUTO |
| F03 | `FoodVarietyEvaluatorTest` unclassified/excluded/unique registry identity | AUTO |
| F04 | zero-population/zero-enabled-group fixtures | AUTO |
| F05 | partial storage/incomplete-population semantics; one-pass Food facts | AUTO |
| F06 | `FoodGrowingCollectorTest` exact count fixtures | AUTO + PRIOR LIVE real crops |
| F07 | exact vanilla maturity predicates | AUTO + PRIOR LIVE |
| F08 | bounds/custom anchor/unassessed maturity | AUTO |
| F09 | Growing/Reserves/Variety independence | AUTO + PRIOR LIVE |
| F10 | `FoodM3RulesTest`, `FoodM3LedgerScreenTest`, reload/budget/navigation tests | AUTO + M6 LIVE UI/resource-reload spot-check |

## Commerce — E01–E09

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| E01 | `CommerceEvaluatorTest` mixed-resident fixture | AUTO |
| E02 | profession counts/diversity fixtures | AUTO |
| E03 | profession-change/diversity fixtures | AUTO |
| E04 | exact raw employment boundaries | AUTO |
| E05 | baby population/eligibility separation | AUTO |
| E06 | nitwit and modded-profession eligibility | AUTO |
| E07 | no-eligible vs authoritative zero employment | AUTO |
| E08 | partial resident scope/observed employment | AUTO |
| E09 | `SettlementCommerceObservationTest`, `CommerceLedgerScreenTest`, debug tests; one-pass/no-trade reads | AUTO + M6 LIVE profession/UI sanity |

## Prosperity — P01–P16

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| P01 | `ProsperityEvaluatorTest` default five-input formula | AUTO + PRIOR LIVE (78% Oured) |
| P02 | raw band-boundary tests | AUTO |
| P03 | fractional Housing source precision | AUTO |
| P04 | Food normalization against STOCKED threshold | AUTO |
| P05 | weight-zero exclusion | AUTO |
| P06 | missing/disabled source strict authority | AUTO |
| P07 | no-residents behavior | AUTO |
| P08 | Housing over-100 cap/source behavior | AUTO |
| P09 | raw source precision/display separation | AUTO |
| P10 | Variety/Growing independence | AUTO |
| P11 | threat-count independence | AUTO |
| P12 | profession-diversity independence | AUTO |
| P13 | scoring-rule revision/rebaseline behavior | AUTO |
| P14 | no gameplay effects | AUTO design/regression evidence + M6 LIVE integrated sanity |
| P15 | generation/revision/non-finite provenance rejection and zero-world-access evaluator | AUTO |
| P16 | config validation plus Prosperity details/presentation tests | AUTO + PRIOR LIVE + M6 LIVE final UI pass |

## History — H01–H20

Automated History evidence is distributed across `HistoryTrackerTest`, `HistoryPersistenceTest`, `LedgerObservationTest`, `M5LedgerScreenTest`, History payload tests and debug/report tests. M5 owner playtesting additionally proved the real migration → confirmation → save/restart path for Population and Housing.

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| H01 | migration/persistence tests + M5 real Oured migration | AUTO + PRIOR LIVE; O04 restart recheck |
| H02 | silent-baseline/high-water initialization | AUTO |
| H03 | exact 200-tick confirmation window | AUTO + PRIOR LIVE real Population confirmation |
| H04 | reversal/replacement candidate behavior | AUTO |
| H05 | partial-domain candidate reset | AUTO |
| H06 | Housing shortage started/resolved, no coalescing | AUTO + PRIOR LIVE real bed-removal/restoration |
| H07 | Food Security transition eligibility | AUTO |
| H08 | Prosperity high-water/no-repeat | AUTO |
| H09 | direct jump emits only highest observed milestone | AUTO |
| H10 | relevant fingerprint rebaseline/monotonic revision | AUTO |
| H11 | fixed-window coalescing | AUTO |
| H12 | retention/founding protection/sequence non-reuse + typed persistence | AUTO |
| H13 | debug read-only isolation | AUTO |
| H14 | cached/duplicate/player/pagination generation isolation | AUTO |
| H15 | durable state survives restart; candidates do not | AUTO + PRIOR LIVE confirmed event persistence |
| H16 | observation-only language/exact event vocabulary | AUTO/resource audit |
| H17 | net-zero coalescing | AUTO |
| H18 | dirty-state discipline and same-session confirmation | AUTO |
| H19 | bounded newest-first History paging/session generation behavior | AUTO + M6 LIVE final UI/pagination pass |
| H20 | backward-time/disabled-history/domain edge/typed bounds | AUTO |

## Operational — O01–O05

| ID | Coverage found | M6 disposition |
| --- | --- | --- |
| O01 | Comfort/Crop/config validation tests and atomic last-valid rule behavior | AUTO + M6 LIVE real resource reload |
| O02 | module-specific screen tests and snapshot-reuse tests | **M6 LIVE REQUIRED** across final Ledger and practical GUI scales |
| O03 | `LedgerObservationTest` coalescing/cooldown/stale generation/release; lifecycle hooks in `Hometown` | AUTO + SERVER/LIVE lifecycle sanity |
| O04 | normal CI build/tests and M5 real old-world migration already exist | **SERVER + M6 LIVE REQUIRED**: dedicated-server startup, resource reload, final save/restart |
| O05 | bounded work counters/cooldown tests exist but no representative/dense elapsed-time evidence yet | **PROFILE REQUIRED**; instrumentation/a repeatable measurement path must be provided before owner handoff |

## Initial conclusions

1. No new gameplay system is authorized or needed for M6.
2. The existing test tree already covers the overwhelming majority of pure R2 arithmetic, classification, state-machine, serialization, protocol and configuration behavior.
3. The largest genuine M6 gaps are operational rather than feature gaps: final integrated UI coverage, real unloaded-chunk/loot fixtures, resource reload, dedicated-server lifecycle, and measured request performance.
4. O05 currently lacks a clean owner-facing way to capture the exact normal Ledger request elapsed time together with work counters. M6 should add only the minimum diagnostic instrumentation necessary to make that measurement repeatable; it must not add background monitoring or gameplay effects.
5. The current artifact version is still `0.8.1`. Do not call a build `0.9.0` until the initial M6 automated baseline is green and any required diagnostic-only integration work is defined.
