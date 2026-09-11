# R2 M2 Comfort automated checkpoint — 2026-09-11

Status: **AUTOMATED GATE PASS — OWNER / INTERACTIVE VALIDATION PENDING**

This checkpoint completes the automated implementation pass for Revision 2 Milestone 2 (Comfort) without claiming the outstanding owner/play validation gates.

## Specification source

The authoritative contract is `docs/specs/Hometown_Implementation_Specification_Revision_2.docx`. The M2 audit used the exact Comfort section and C01–C10 acceptance contract from that document rather than reconstructing requirements from historical status notes.

## Existing implementation retained

The pre-existing M2 work was preserved where it already matched R2:

- Housing-derived `RoomGeometry` adapter and canonical room keys.
- request-local `BlockObservationCache` shared with Safety.
- `ComfortCategory`, `ComfortSettings`, `ComfortRules`, `ComfortCollector`, `ComfortEvaluator`, `ComfortSnapshot`, and `ComfortDebugReport`.
- server/network/debug integration already present in `TownLedgerService`, `TownLedgerSnapshot`, and `TownLedgerSnapshotPayload`.
- exact core Comfort tags and the three required vanilla state predicates.

No competing room detector or second Comfort scan architecture was introduced.

## Completion work in this checkpoint

- Development > Comfort now renders the authoritative server snapshot instead of the generic placeholder.
- Aggregate view shows Residential Comfort or Observed room comfort, N/A/percent, authoritative band, progress bar, enclosed-bed count, rooms assessed, category coverage, and incomplete reason where applicable.
- Room Details reuses the already-delivered snapshot and provides room paging, bed count, room score/N/A, band, enabled-category presence, reasons, and Back navigation.
- Development/Comfort subnavigation sends no new Ledger request and therefore cannot trigger a new Comfort world scan.
- Added localization for Comfort states, categories, bands, reasons, and room details.
- Added standalone optional furniture-compatibility documentation using `required: false` tag entries and optional version-1 state predicates; no runtime furniture dependency was added.

## Automated acceptance coverage

New tests cover the R2 Comfort contract including:

- C01 bare room = 0 / 75 and BARE.
- C02 per-category presence capping, Storage + Books = 26.666…% / displayed 27 / BASIC, duplicate Books do not increase score, disabled Seating does not contribute.
- C03 six standalone core categories = 100%; Storage + Lighting = 33.333…% / BASIC.
- C04 enabling empty Seating/Tables retains their denominator weight; all eight present can reach 100%.
- C05 raw bed-weighted town aggregation, proven room scope only, no behind-boundary leakage, shared wall may qualify independently for both rooms while its physical read is cached.
- C06 exact vanilla state predicates for redstone lamps and campfires, plus unconditional chiseled bookshelf qualification.
- C07 shipped core tag membership checks and exclusion precedence.
- C08 partial/incomplete/N/A distinctions, UNKNOWN absence, no-enclosed-bed and no-enabled-weight states, and Comfort snapshot codec round-trip.
- C09 invalid custom-rule reload retains the last valid definitions; optional compatibility is documented without a required dependency.
- C10 aggregate Comfort UI, all eight enabled category rows, room-detail pagination, Back navigation, and no network request from Comfort subnavigation.
- Q03/Q05/Q06-oriented tests for room/shared scan limits, preserved known positive facts, zero-work unavailable state, out-of-bounds rejection, Housing independence, and loaded-only chunk access.

Existing Ledger/Housing/Food/Safety tests remain in the same suite and continue to run as regressions.

## Configuration audit

The shipped config matches R2 M2:

- `comfort.enabled=true`
- category enabled/weight defaults from `ComfortCategory`, weights range 0–100
- `comfort.maxRooms=512`, range 1–4096
- `comfort.maxCellsPerRoom=65536`, range 64–262144
- shared `scan.maxNewBlockInspections=262144`, range 1024–1048576

Default enabled Comfort weight remains 75 (Storage 10 + Lighting 15 + Decor 10 + Books 10 + Plants 10 + Amenities 20). Seating 15 and Tables 10 remain disabled by default.

## Scan and orchestration audit

`TownLedgerService` performs one synchronous observation and gives Safety and Comfort the same request-local `BlockObservationCache`. Physical block reads are therefore shared against the same request budget. Resident-page and Development-subpage navigation reuse the session snapshot/generation rather than performing another collection pass. Comfort does not mutate Housing and does not persist its observations.

The block cache checks bounds and budget before reading and uses loaded-only `getChunkNow`; Comfort does not create chunk tickets or request a forced chunk load.

## CI evidence

GitHub Actions workflow: **Build and Test**

Successful head commit: `db514a6fa96d286cd663ab082bed6bd72055ea82`

Workflow run: `34636333439`

Result:

- Gradle test task: **PASS**
- Gradle build task: **PASS**
- workflow conclusion: **success**

The immediately preceding run executed 148 tests and identified one test-expectation error in C02. That test input was corrected without changing Comfort production scoring; the successful run used the same test suite plus that corrected expectation.

## Remaining validation before merge / milestone close

- owner local `gradlew test` and `gradlew build` on `r2-m2-comfort`.
- interactive in-game review of Development > Comfort using real rooms, including aggregate/category presentation and Room Details navigation.
- interactive confirmation that room/window changes refresh only on a fresh Ledger observation and do not change Housing semantics.
- any still-pending global B01–B08 owner gates remain pending; this checkpoint does not override them.

Do not mark final v1.0 validation complete from this automated checkpoint alone.
