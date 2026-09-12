# R2 M4 Commerce automated checkpoint — 2026-09-12

Status: **AUTOMATED GATE PASS — OWNER / INTERACTIVE VALIDATION PENDING**

## Scope implemented

R2 M4 Commerce is implemented as a read-only observation derived from the existing resident pass. `SettlementScanner.observe(...)` produces the protected `SettlementStats` plus copied immutable Commerce resident facts. `CommerceEvaluator` consumes those facts without any second villager query or world access.

Implemented behavior:

- babies remain normal town Population but are excluded from Commerce eligibility;
- adult nitwits remain normal town Population but are excluded from Commerce eligibility;
- baby classification wins before nitwit classification, so a hypothetical baby nitwit contributes only to `excludedBabies`;
- adult `minecraft:none` villagers are eligible and unemployed;
- every other adult profession registry ID is employed, including registered modded profession IDs, with no workstation requirement;
- employment uses `100 * employedAdults / eligibleAdults` with raw precision;
- no eligible adults yields N/A / `NO_ELIGIBLE_ADULTS`, not 0%;
- authoritative states are `UNEMPLOYED`, `LIMITED`, `ACTIVE`, `STRONG`, and `FULLY_EMPLOYED` at the exact R2 raw boundaries;
- incomplete resident scope retains known counts and optional Observed Employment while withholding authoritative employment/state input;
- profession diversity/counts include employed eligible adults only;
- Commerce UI uses the existing Ledger observation, six profession rows per page, and existing arrows without rescans;
- `/hometown debug commerce [uuid] [page]` is read-only, sorts registry IDs, keeps totals/reasons visible, and bounds profession detail to 32 records per page;
- `commerce.enabled=true` is the only Commerce-specific configuration;
- networking protocol is `10` for the added Commerce companion payload.

## Architectural evidence

- The normal Ledger request calls `SettlementScanner.observe(...)` once. Housing/Food continue to receive the resulting protected `SettlementStats`, while Commerce receives copied resident facts from that same pass.
- `SettlementCommerceObservationTest` supplies a duplicated villager candidate and verifies UUID deduplication, exactly one `getEntitiesOfClass(Villager.class, ...)` resident query, and no trade-offer access.
- `CommerceEvaluatorTest` is pure arithmetic/classification and performs no world discovery.
- Commerce page selection and profession pagination are client-local over the received snapshot; `CommerceLedgerScreenTest` verifies no additional `RequestTownLedgerPayload` is sent.
- `CommerceDebugReportTest` verifies the shared 32-record debug-page limit while totals/counters remain present on every page.
- No workstation, brain, trade, restock, inventory, XP/level, schedule, wealth, market, currency, or AI-state collector was added.

## Exact R2 Commerce acceptance audit

The authoritative R2 DOCX was re-extracted on the isolated `r2-m2-comfort-spec-audit` branch so M4 was checked against the exact Commerce acceptance wording rather than memory. Audit workflow run `34675145649` completed successfully. The audit branch remains isolated and must not be merged into M4 or `main`.

## Automated acceptance coverage

The Commerce acceptance family is covered by targeted tests and existing regressions:

- **E01:** 13 observed residents / 10 eligible adults / 7 employed / 3 unemployed / 2 babies / 1 adult nitwit gives raw 70% and `ACTIVE` without rewriting Population.
- **E02:** 3 farmers, 2 librarians, 1 cleric and 1 toolsmith give diversity 4 and seven employed; `none`/`nitwit` never enter `professionCounts`.
- **E03:** Farmer → Librarian fixtures preserve employment, update counts, and change diversity only when a profession registry ID appears/disappears.
- **E04:** 7/10 → 8/10 produces 70% `ACTIVE` → 80% `STRONG`; raw boundary tests explicitly cover 0, positive-below-1, 49.99, 50, 79.99, 80, 99.99 and 100.
- **E05:** adding a baby changes Population without changing eligibility/employment; a baby nitwit is counted only in the baby exclusion.
- **E06:** adding an adult nitwit changes Population but not eligibility/percentage; a non-vanilla non-none/non-nitwit profession ID qualifies without workstation logic.
- **E07:** babies/nitwits-only population gives N/A `NO_ELIGIBLE_ADULTS`; eligible-but-unemployed adults give authoritative 0% `UNEMPLOYED`.
- **E08:** partial resident coverage preserves known 6/8 = Observed 75%, with no authoritative percentage or band; one-pass resident reuse is separately asserted.
- **E09:** functional Ledger Commerce rendering, six-row profession pagination, deterministic sorting/fallback behavior and snapshot reuse are automated where practical; the one-pass fixture verifies no trade-offer reads. Full live before/after profession/trade/workstation/restock/inventory/AI preservation remains part of owner/in-game validation.

M4 also keeps the required protected population consumers in place: Housing and Food still receive the unchanged `SettlementStats` from the existing resident observation. Existing Q02 denominator-quality and Ledger/coalescing regressions remain in the full suite.

## CI evidence

Known-green implementation head before subsequent spec-audit/debug-boundary refinements:

- commit: `1f870827d0426724d9a2c1cffe432bc446101391`
- GitHub Actions workflow: `Build and Test`
- run: `34674941460`
- result: **SUCCESS**
- `./gradlew test --no-daemon`: **BUILD SUCCESSFUL**
- `./gradlew build --no-daemon`: **BUILD SUCCESSFUL**

The exact versioned `0.7.0` branch head must pass the same workflow before owner handoff.

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
