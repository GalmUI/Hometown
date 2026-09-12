# R2 M4 Commerce automated checkpoint — 2026-09-12

Status: **AUTOMATED GATE PASS — OWNER / INTERACTIVE VALIDATION PENDING**

## Scope implemented

R2 M4 Commerce is implemented as a read-only observation derived from the existing resident pass. `SettlementScanner.observe(...)` produces the protected `SettlementStats` plus copied immutable Commerce resident facts. `CommerceEvaluator` consumes those facts without any second villager query or world access.

Implemented behavior:

- babies remain normal town Population but are excluded from Commerce eligibility;
- adult nitwits remain normal town Population but are excluded from Commerce eligibility;
- adult `minecraft:none` villagers are eligible and unemployed;
- every other adult profession registry ID is employed, including registered modded profession IDs, with no workstation requirement;
- employment uses `100 * employedAdults / eligibleAdults` with raw precision;
- no eligible adults yields N/A / `NO_ELIGIBLE_ADULTS`, not 0%;
- authoritative states are `UNEMPLOYED`, `LIMITED`, `ACTIVE`, `STRONG`, and `FULLY_EMPLOYED` at the R2 boundaries;
- incomplete resident scope retains known counts and optional Observed Employment while withholding authoritative employment/state input;
- profession diversity/counts include employed eligible adults only;
- Commerce UI uses the existing Ledger observation, six profession rows per page, and existing arrows without rescans;
- `/hometown debug commerce` is read-only and reports registry-ID profession counts, denominator/exclusion counts, raw authority, and observation metadata;
- `commerce.enabled=true` is the only Commerce-specific configuration;
- networking protocol is `10` for the added Commerce companion payload.

## Architectural evidence

- The normal Ledger request calls `SettlementScanner.observe(...)` once. Housing/Food continue to receive the resulting protected `SettlementStats`, while Commerce receives copied resident facts from that same pass.
- `SettlementCommerceObservationTest` supplies a duplicated villager candidate and verifies UUID deduplication, exactly one `getEntitiesOfClass(Villager.class, ...)` resident query, and no trade-offer access.
- `CommerceEvaluatorTest` is pure arithmetic/classification and performs no world discovery.
- Commerce page selection and profession pagination are client-local over the received snapshot; `CommerceLedgerScreenTest` verifies no additional `RequestTownLedgerPayload` is sent.
- No workstation, brain, trade, restock, inventory, XP/level, schedule, wealth, market, currency, or AI-state collector was added.

## Automated acceptance coverage

The Commerce acceptance family is covered by targeted tests and existing regressions:

- **E01 / E02:** mixed-resident denominator, 13 total / 10 eligible / 7 employed = 70% ACTIVE, and exact profession diversity/counts.
- **E03–E05:** raw employment band boundaries are exercised at 0%, 25%, 50%, 75%, 80%, 90%, and 100%.
- **E06:** adult nitwit exclusion plus a non-vanilla profession ID qualifying without workstation logic.
- **E07:** babies/nitwits-only population gives N/A `NO_ELIGIBLE_ADULTS`; eligible-but-unemployed adults give authoritative 0% in evaluator boundary coverage.
- **E08:** partial resident coverage preserves known 6/8 = Observed 75%, with no authoritative percentage or band; one-pass resident reuse is separately asserted.
- **E09:** functional Ledger Commerce rendering, six-row pagination and snapshot reuse are automated; the one-pass fixture verifies no trade-offer reads. Full live no-effect observation remains part of owner/in-game validation.

Protected Ledger coalescing/cooldown, Housing/Food/Safety/Comfort rendering and prior Development navigation tests remain in the full suite.

## CI evidence

Known-green implementation head before this documentation commit:

- commit: `1f870827d0426724d9a2c1cffe432bc446101391`
- GitHub Actions workflow: `Build and Test`
- run: `34674941460`
- result: **SUCCESS**
- `./gradlew test --no-daemon`: **BUILD SUCCESSFUL**
- `./gradlew build --no-daemon`: **BUILD SUCCESSFUL**

Previous failures during implementation were limited to expected integration/test-seam changes (old `SettlementScanner.scan(...)` static mocks, Commerce placeholder expectations, and one Java compound-`var` declaration). Those were corrected without weakening the Commerce contract.

## Remaining owner validation

Before M4 is considered owner-validated, test the versioned M4 JAR in a real town and confirm:

1. Commerce counts match actual adult profession state while Population still includes babies and nitwits.
2. Adult unemployed villagers affect the eligible denominator; babies and nitwits do not.
3. Changing a villager profession through normal gameplay changes Commerce only on a fresh Ledger observation.
4. Profession names/counts and pagination are readable in the real GUI.
5. `/hometown debug commerce` agrees with the Ledger.
6. Ledger/debug observation does not change professions, trades, workstations, restocks, inventory, or villager behavior.

The first M4 owner-playtest artifact is version `0.7.0` after the versioned branch head passes CI.
