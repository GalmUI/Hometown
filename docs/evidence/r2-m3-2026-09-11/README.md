# R2 M3 — Food Variety and Growing automated checkpoint

Date: 2026-09-11 (owner local date)

Status: **AUTOMATED GATE PASS — OWNER / INTERACTIVE VALIDATION PENDING**

Playtest candidate: **0.6.0**

## Authority

Revision 2 remains authoritative for this milestone. R2 M3 is Food Variety and Growing. The implementation preserves the protected Food Reserves behavior while extending Food with Variety and Growing observations.

## Implemented scope

- Food Variety is derived from copied stack facts produced by the existing protected Food inventory pass; no second inventory pass is introduced.
- Five fixed groups are implemented: Grains, Vegetables, Fruit, Protein, and Prepared Meals.
- Group priority, thresholds, disabled-group behavior, unclassified nutrition, unique food identity, partial-data semantics, and coverage/bands are implemented.
- Exact core food-group resources are present.
- Growing is a separate loaded-only crop observation using `getChunkNow`, chunk-section palette filtering, exact crop predicates, deterministic bounded work, and the existing shared block-inspection budget.
- Exact vanilla crop rules are present for wheat, carrots, potatoes, and beetroots.
- Custom crop definitions support exact anchor predicates and optional maturity predicates. Omitted maturity remains explicitly unassessed rather than becoming an invented zero.
- Invalid crop reloads retain the last valid definitions atomically.
- Food Ledger navigation provides Reserves, Variety, and Growing views. Subpage changes and Growing family pagination reuse the already-open observation and do not request a new scan.
- M3 companion networking carries Variety and Growing observations beside the protected existing Ledger snapshot.
- Operator diagnostics exist for Food Variety and Growing.

## Automated acceptance coverage

The M3 test additions cover the required Variety and Growing behavior, including:

- threshold equality and coverage bands;
- classification priority and collision handling;
- disabled winning groups not rerouting nutrition to lower-priority groups;
- unclassified food and unique registry identity;
- zero-population and reduced enabled-group denominator cases;
- partial storage and incomplete-population UNKNOWN/N/A semantics;
- exact food-group resource memberships and exclusions;
- exact vanilla crop maturity ages and unsupported crop blocks;
- custom crop anchors and omitted maturity;
- invalid crop reload retention and duplicate-block rejection;
- Growing fixtures for 8 growing / 3 mature / 2 families and 56 growing / 22 mature / 3 families;
- loaded-only chunk access and bounded scan-limit behavior;
- companion payload round trip;
- Ledger Reserves / Variety / Growing navigation and snapshot reuse;
- preservation of the protected Reserves snapshot through the new one-pass Food observation API.

## Verification

GitHub Actions run `34665319464` on implementation head `e11f501332733cf5e1fb2ea53dc8697a8be052c7` completed successfully:

- `test`: PASS
- `build`: PASS

The full suite contained 164 tests at the immediately preceding assertion-failure checkpoint; the final green run changed only fixture/assertion corrections and did not add or remove tests.

The owner independently ran both:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

and reported both successful before the `0.6.0` versioning/doc-only commits.

## Versioning policy adopted at this checkpoint

Hometown now uses milestone-oriented pre-1.0 versioning:

- new milestone: increment minor and reset patch (`0.5.x` → `0.6.0`);
- changed build within a milestone: increment patch (`0.6.0` → `0.6.1`);
- never reuse a version for materially different code or data.

`gradle.properties` remains the single source of truth for artifact and NeoForge metadata versioning.

## Remaining owner validation

`0.6.0` must still be tested interactively in Minecraft before R2 M3 is considered fully owner-validated. The interactive pass should cover real Food storage, Variety classification/threshold presentation, real crop discovery/maturity, Reserves/Variety/Growing navigation, Growing family pagination where practical, and regressions in existing Food/Housing/Safety/Comfort behavior.
