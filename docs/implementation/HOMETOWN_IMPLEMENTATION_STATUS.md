# Hometown implementation status

## Current authoritative checkpoint — 2026-09-12

**Specification Revision:** 2 — `docs/specs/Hometown_Implementation_Specification_Revision_2.docx`  
**Current Milestone:** R2 M6 — Integration and delivery  
**Status:** **PARTIAL — 0.9.0 automated/server gates green; owner live/UI/performance validation pending**  
**Current playtest version:** `0.9.0`  
**Versioned implementation head:** `ff5d5fbd79999d1a2e3272c76db112b31d600dbd`  
**M6 evidence:** `docs/evidence/r2-m6-2026-09-12/README.md`, `ACCEPTANCE_MATRIX.md`, `LIVE_VALIDATION.md`

The repository intentionally keeps this tracker at `docs/implementation/HOMETOWN_IMPLEMENTATION_STATUS.md`; this is the organized-repository equivalent of the R2 specification's historical `docs/HOMETOWN_IMPLEMENTATION_STATUS.md` path. Do not create a competing second status file.

### Milestones

| Milestone | State | Current evidence |
| --- | --- | --- |
| R2 M0 | COMPLETE | Baseline preserved/mapped; later owner and regression validation supersedes the original pending notes retained below |
| R2 M1 | COMPLETE | Shared observation/Safety integrated; subsequent milestone regressions and owner testing retained |
| R2 M2 | COMPLETE | Comfort implementation and owner validation complete |
| R2 M3 | COMPLETE | Food Variety/Growing implementation and owner validation complete |
| R2 M4 | COMPLETE | Commerce implementation and owner validation complete |
| R2 M5 | COMPLETE | Prosperity/History 0.8.1 complete; owner evidence in `docs/evidence/r2-m5-2026-09-12/README.md` |
| R2 M6 | PARTIAL | 0.9.0 automated build/test + dedicated server/reload PASS; final M6 LIVE/PROFILE gates remain |

R3 remains **NOT_STARTED**. M6 does not authorize Revision 3 and R3 must not begin until every required R2 M6 acceptance item is PASS with evidence.

### Completed requirements / current M6 evidence

- All 96 R2 acceptance IDs are mapped in `docs/evidence/r2-m6-2026-09-12/ACCEPTANCE_MATRIX.md` to automated, prior-live, or remaining live/server/profile evidence.
- Exact versioned `0.9.0` commit `ff5d5fbd79999d1a2e3272c76db112b31d600dbd` passed GitHub Actions run **34726245824**: normal Gradle `test` PASS and normal Gradle `build` PASS under Java 21.
- Diagnostic-only cached performance profiling is implemented and tested. `/hometown debug performance [uuid]` reads a cached normal Ledger observation and performs no world scan or History mutation.
- Isolated dedicated-server/RCON workflow run **34726154745**, job **103640502723**, PASS: Hometown loaded server-side, Minecraft reached `Done`, actual RCON `reload` completed, recipes/advancements reloaded, and actual RCON `stop` shut the server down without a client-dist crash.
- R2 configuration/tag/rule surfaces are documented in `docs/CONFIGURATION_AND_DATAPACKS.md`.
- Prior M5 owner evidence proves real old-world v1→v2 migration, founding preservation, Prosperity calculation, 200-tick Population confirmation, durable History across restart/no duplicate, Housing shortage start/resolution, and population-change behavior.

### Changed files in M6 so far

- `src/main/java/dev/conner/hometown/settlement/TownLedgerService.java` — capture fresh observation elapsed time and expose cached diagnostic profile without rescanning.
- `src/main/java/dev/conner/hometown/settlement/LedgerPerformanceProfile.java` — immutable memory-only O05 timing/work-counter view.
- `src/main/java/dev/conner/hometown/command/M6DebugCommands.java` — operator cached-performance report.
- `src/main/java/dev/conner/hometown/Hometown.java` — register M6 diagnostic command.
- `src/test/java/dev/conner/hometown/settlement/LedgerObservationTest.java` and `src/test/java/dev/conner/hometown/command/M6DebugCommandsTest.java` — diagnostic lifecycle/format coverage while preserving existing request-call-count assertions.
- `docs/CONFIGURATION_AND_DATAPACKS.md`, `docs/README.md`, and `docs/evidence/r2-m6-2026-09-12/*` — delivery documentation, acceptance matrix and owner procedure.
- `gradle.properties` — M6 owner build version `0.9.0`.

### Build command and result

GitHub Actions run **34726245824** on the exact versioned 0.9.0 implementation head:

```text
./gradlew test
./gradlew build
```

Result: **PASS / PASS**. The normal owner artifact is `build/libs/hometown-0.9.0.jar` after the same local build.

### Automated/server tests run

- Complete normal test suite: **PASS** on exact 0.9.0 code head.
- Normal mod build: **PASS** on exact 0.9.0 code head.
- Dedicated server startup/client-dist smoke: **PASS**.
- Actual server resource reload through RCON: **PASS**.
- Actual server shutdown through RCON: **PASS**.
- M6 performance-diagnostic integration/lifecycle tests: **PASS**.

### Manual/live tests still required

Follow `docs/evidence/r2-m6-2026-09-12/LIVE_VALIDATION.md`. Required remaining evidence includes:

- O02 final integrated Ledger/UI sweep across practical GUI scales.
- Reversible Housing/Privacy/Safety/Comfort/Food/Commerce/Prosperity cross-system spot checks.
- B08 real unresolved-loot non-mutation fixture.
- Q01/Q06 real loaded-fringe/no-force-loading fixture.
- Final 0.9.0 save/exit/reload plus in-world `/reload` sanity.
- O05 representative Oured performance samples using `/hometown debug performance`.
- O05 deliberately dense temporary town performance sample and bounded-ceiling behavior.

A required live/profile item remains NOT RUN until the owner reports an actual observed result. M6 therefore remains PARTIAL.

### Known issues / blockers

No current automated compile/test/server blocker is known. The blocking dependency is the required owner/live environment for UI rendering, real world/loading/loot fixtures, final lifecycle sanity and measured performance. Any functional code/data fix after the 0.9.0 owner pass begins must use version `0.9.1` and receive targeted retest.

### Current owner mapping

| Contract owner | Current implementation owner |
| --- | --- |
| Founding/identity/access | `interaction/BellInteractionHandler`, `settlement/SettlementManager`, `SettlementValidator`, `Settlement` |
| Settlement persistence | `settlement/HometownSavedData` |
| Ledger orchestration/cache/generation | `settlement/TownLedgerService` |
| Residents / Commerce source facts | `settlement/SettlementQueries`, `SettlementScanner`, `SettlementStats`; `commerce/CommerceEvaluator` |
| Housing / Privacy | `housing/HousingScanner`, `HousingSnapshot`, existing room owners |
| Safety | `safety/SafetyCollector`, `SafetyEvaluator`, `SafetySnapshot` |
| Comfort | `comfort/ComfortCollector`, `ComfortEvaluator`, `ComfortSnapshot`, `ComfortRules` |
| Food Reserves / Variety / Growing | existing Food scanner/rules/snapshot plus `FoodVarietyEvaluator`, `FoodGrowingCollector`, `FoodGrowingEvaluator`, `CropRules` |
| Prosperity | `prosperity/ProsperityEvaluator`, `ProsperitySnapshot` |
| History | `history/HistoryTracker`, typed `HistoryEvent`; durable fields remain in `HometownSavedData` |
| Config | `config/HometownServerConfig` |
| Network | `network/HometownNetworking` and bounded payloads |
| Client Ledger | `client/TownLedgerScreen` plus module presentation helpers |
| Debug / M6 profile | existing debug owners plus `command/M5DebugCommands`, `M6DebugCommands` |

### Next milestone / resume action

**Next milestone:** none yet; R2 M6 remains the current incomplete milestone.  
**Resume action:** owner pulls/builds 0.9.0 and executes `LIVE_VALIDATION.md`, returning any UI failures plus exact representative/dense `/hometown debug performance` output. Close or patch every remaining M6 LIVE/PROFILE item before marking R2 COMPLETE or beginning R3.

---

## Historical retained status

The material below is retained as historical milestone evidence. Where it conflicts with the current checkpoint above (for example old milestone states or absent future owners), the current checkpoint supersedes it; do not reinterpret the older text as current project state.

Updated 2026-09-10. Current milestone: **R2 M2 — Comfort**, **PARTIAL (implementation in progress)**. M0 and M1 remain OWNER_VALIDATION_PENDING. The sequential owner amendment below supersedes historical one-run stop instructions in this record.

## Selection and authority

The owner's explicit instruction to complete the earliest incomplete R2 prerequisite first governs this invocation. R2 M0 has no prior completion record. This takes precedence over R3 section 19's discovery-first ordering. R3 M0 is NOT_STARTED; this run does not claim two milestones. No gameplay implementation or preparatory refactor was performed.

Read Revision 3 in full and R2 sections 1–5, protected Food rules, section 11 owner contracts, sections 14–16, and B01–B08. Original supplied DOCX files are retained unchanged under docs/references; extracted text was used to inspect their contents. No competing Markdown reference was supplied. Revision 1 is not the implementation authority.

Reference SHA-256:

- R3 Hometown_Progression_Town_Operations_Revision_3.docx: C6803F94D3D0497417345B473A2C8FE072C432E06BB89357D21C600E5C52F26C.
- R2 Hometown_Implementation_Specification_Revision_2.docx: B200EF1F547FD27E80A0529C023501E14987FCC721BFD4DFE4164E5F132FA35A.

## Source baseline and preservation

Source root: C:/Users/solow/Documents/Codex/2026-09-09/files-pasted-by-the-user-hometown/outputs/hometown.

No AGENTS.md was found in the source tree or checked ancestor locations. Read the repository README, ACCEPTANCE, VALIDATION, TEST-RESULTS and Food scan correction documentation as baseline guidance/evidence. No previous HOMETOWN_IMPLEMENTATION_STATUS.md exists. Git status fails because this is a source export without .git; commit ID, tracked diff and untracked classification are unavailable. Do not describe it as a clean Git checkout.

Alternative provenance check: compared all 87 files in hometown-0.4a-food-scan-fix-source.zip to the source export using SHA-256; zero missing or changed files. Existing 43 production Java files, 15 test classes, and historical validation documents exist. Retained history claims 119 passing tests; a fresh independent run now reproduces that result. Source manifest is evidence/r2-m0-2026-09-10/source-manifest.sha256 (manifest SHA-256 9F85C8D03F9A008F1CE25723E219B0B74977B5B17CDFEDAD1EF502609CB9F3EE). No pre-existing source files were modified.

Pinned platform: Minecraft 1.21.1, NeoForge 21.1.250, Java toolchain 21, ModDevGradle 2.0.146, Gradle wrapper 9.2.1, JUnit BOM 5.11.4, Mockito 5.15.2. Build group dev.conner.hometown; version 0.4.0-alpha.2. Existing NeoForge default mapping setup unchanged. Runtime used C:/Program Files/Java/jdk-21.0.12/bin/java.exe. build.gradle SHA-256: 406E18BD63025EFAD4EAA2E17968365C9D520AA1A791379BDEF83DF8FA259EDD.

## Milestones

| Milestone | State | Evidence / missing gate |
| --- | --- | --- |
| R2 M0 | OWNER_VALIDATION_PENDING | Normal Gradle test PASS (119/119), build PASS; only interactive B01–B08 pending; see continuation results below |
| R2 M1 | OWNER_VALIDATION_PENDING | 133/133 normal tests and normal build PASS; implementation complete; interactive Safety/loading/GUI and B02–B08 checks below pending |
| R2 M2 | PARTIAL | In progress: read-only detector geometry adapter; next implement exact room-local rules and shared copied-block budget, then C01–C10 and regressions |
| R2 M3 | NOT_STARTED | Variety/Growing absent; Reserves already present |
| R2 M4 | NOT_STARTED | Raw employment/diversity exist; required Commerce panel/evaluator absent |
| R2 M5 | NOT_STARTED | Prosperity placeholder; only founding display, no derived History system |
| R2 M6 | NOT_STARTED | Integration depends on above |
| R3 M0 | NOT_STARTED | Unified discovery follows prerequisite work under owner's instruction |
| R3 M1–M13 | NOT_STARTED | No civic implementation; R2 integration gate incomplete |

## Existing owner map

Paths below are relative to src/main/java/dev/conner/hometown/ unless stated otherwise. Future owners are mapped, not created.

| Contract owner | Existing owner / required future owner |
| --- | --- |
| Founding/identity/access | interaction/BellInteractionHandler.java; settlement/SettlementManager.java, SettlementValidator.java, Settlement.java, TownNames.java |
| Settlement persistence | settlement/HometownSavedData.java; keep this sole save-wide store |
| Ledger request/orchestration | settlement/TownLedgerService.java; network/RequestTownLedgerPayload.java, TownLedgerSnapshotPayload.java |
| Resident collector | settlement/SettlementQueries.java and SettlementScanner.java; SettlementStats.java; reuse existing pass for Commerce |
| Housing collector/evaluator | housing/HousingScanner.java, HousingSnapshot.java, RoomDensity.java |
| Room world/identity | room/RoomDetector.java, LoadedRoomWorld.java, RoomDetectionResult.java |
| Food collector/evaluator | food/FoodScanner.java, FoodRules.java, FoodSnapshot.java, FoodScanDiagnostics.java, FoodDebugReport.java |
| Ledger immutable view | network/data/TownLedgerSnapshot.java and ResidentSummary.java |
| Existing screen/navigation | client/TownLedgerScreen.java and DevelopmentSection.java |
| Config owner | config/HometownServerConfig.java; preserve seven existing flat keys |
| Network owner | network/HometownNetworking.java; protocol 6; common code uses client callbacks |
| Debug owner | command/HometownDebugCommands.java and RoomDebugCommand.java; permission level 2 |
| SafetyCollector, SafetyEvaluator, SafetySnapshot | Absent; future safety package using shared resident/bed observations |
| ComfortCollector, ComfortEvaluator, ComfortSnapshot | Absent; future comfort package, narrow access to existing proven room cells required |
| FoodVarietyEvaluator, FoodVarietySnapshot | Absent; future food package; expose copied stack facts in original Food pass |
| FoodGrowingCollector, FoodGrowingEvaluator, FoodGrowingSnapshot | Absent; future food package |
| CommerceEvaluator, CommerceSnapshot | Absent; future commerce package; no second resident collector |
| ProsperityEvaluator, ProsperitySnapshot | Absent; future prosperity package; pure five-input evaluation |
| HistoryEvaluator, HistoryEvent, HistoryEventType | Absent; future history package; durable fields remain owned by HometownSavedData |

All R3 named civic/project/operations/recovery/traveler/recruitment owners are absent. No parallel systems were created. R3 owner mapping remains for R3 M0.

Existing config mapping: settlementRadius=64 (16–256), verticalScanRadius=32 (8–128), minimumVillagers=2 and minimumBeds=2 (0–100), preventSettlementOverlap=true, consumeFoundingBook=true, nutritionPerResidentPerDay=20 (1–1000000). Food thresholds are FoodRules.DEFAULT/current 1/3/7, not config keys. Other R2 section 14 keys are absent; introduce only when their selected milestones require them. Ledger cooldown is currently hardcoded 5 ticks; R2 requires 40 by default with compatible snapshot reuse. Native tags use existing singular tags/block and tags/item directories and matching registry TagKeys; future entity tags use tags/entity_type. No custom rule loader currently exists.

## Baseline characterization and integration dependencies

- Founding uses held Book, Bell interaction, server validation and nonce; identity and duplicate rules remain intact. Ledger possession is the existing access path; do not infer a new owner-only Ledger permission.
- Resident query uses alive vanilla Villager entities inside the existing AABB with loaded position checks. Population includes babies/nitwits. Employment excludes babies/NONE/NITWIT. SettlementScanner sorts the existing queried list by UUID. This is not proof of the new bounded Safety entity budget; do not add a second Commerce scan.
- World scan bounds use SettlementQueries.bounds, an inflated box; Settlement.contains/overlap use horizontal circles. Preserve these distinct established purposes rather than silently unifying geometry.
- RoomDetector uses bounded bed seeds, canonical minimum BlockPos representative and scan-local enclosed-cell/bed caches. Limits: 4096 room volume, horizontal 32, vertical 16, 65536 inspections. LoadedRoomWorld uses getChunkNow, treats doors/trapdoors as boundaries regardless of open state, and guards collision-shape neighbor reads. Window removal permits escape. RoomDetectionResult exposes beds and volume but not interior cells: Comfort needs a narrow read-only geometry accessor, never detector replacement.
- Housing yields one capacity per enclosed bed; privacy is bed-weighted 100/80/60/40. Capacity is absent for zero residents or incomplete scans; NO_RESIDENTS and SCAN_INCOMPLETE stay distinct. Privacy is present for complete nonempty enclosed-bed data even at zero population. Incomplete rooms retain unknown counts; do not interpret raw zero unhoused/spare helper values as authoritative without state checks.
- Food reads loaded block entities/local inventory slots, uses FOOD components and exclusions, checks unresolved loot before slot reads, and counts double-chest local halves without accessing outside contents. Nutrition uses checked long arithmetic. Security defaults and zero-population semantics match protected rules. Food continues after local failures and never extracts items.
- Confirmed R2 Q02 gap: FoodRules.snapshot computes reserveDays from incomplete population, FoodSnapshot requires it for positive population/non-unavailable status, and TownLedgerScreen selects at_least_days for every partial scan. Diagnostics already carry populationComplete. R2 M1 explicitly authorizes the narrow denominator guard; leave it untouched in M0. Existing incompletePopulationNoLongerPreventsStorageScan test is not proof of Q02 compliance.
- Ledger service currently validates held item, then scans residents, Housing, Food in order. Each resident page request repeats these scans. Development subnavigation is local. No shared revision-aware generation/coalescing/cache exists. R2 M1 must adapt this existing owner while preserving reopen refresh and the original collection order.
- Founding History is a presentation derived from settlement metadata. No derived event baseline/candidate/retention system exists. SavedData DataVersion=1, file overworld data/hometown_settlements.dat, list Settlements; each record stores Id, Name, Dimension, BellPosition, Radius, FounderUuid, FounderName, FoundedGameTime. Unsupported/missing version throws; fields are explicitly reconstructed, so arbitrary unknown fields are not preserved today. Later migration must explicitly retain legacy identity and handle future schemas; no migration version changed this run.
- No new world mutations, loading calls, chunk tickets, background scans, dependencies, test framework, or gameplay features were introduced. Source inspection and mocks support loaded-only behavior; live chunk-count validation remains pending.

## Initial discovery commands and results (historical)

Evidence prefix: docs/evidence/r2-m0-2026-09-10/. All runs were 2026-09-10. Full logs and exact compiler/runtime argument files retained there; output paths in argument files deliberately point to this invocation's work directory rather than overwriting old build evidence.

Gradle commands used the source root as working directory and these PowerShell assignments:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.12'
$env:GRADLE_USER_HOME = 'C:/Users/solow/Documents/Codex/2026-09-09/files-pasted-by-the-user-hometown/work/gradle-cache'
.\gradlew.bat test build --offline
.\gradlew.bat test build
.\gradlew.bat tasks --all --offline
```

| Command/check | Result | Evidence |
| --- | --- | --- |
| git -C source-root status --short | NOT AVAILABLE, exit 128: not a Git repository | Source archive comparison substitutes only for export provenance, not Git history |
| SHA-256 archive comparison | PASS: 87/87 exact matches | archive-comparison.txt; source-manifest.sha256 |
| test build --offline, sandbox | FAIL exit 1 before compilation: createMinecraftArtifacts/extractServer AccessDeniedException on archive metadata close | r2-m0-gradle.log |
| test build --offline, elevated | FAIL exit 1: production compile/jar succeeded; JUnit/Mockito missing offline | r2-m0-gradle-elevated.log |
| test build, elevated with network | FAIL exit 1: dependencies resolved and 15 test classes compiled; test runner ClassNotFoundException for test classes | r2-m0-gradle-online.log; gradle-test-failure.xml; build/reports/tests/test/index.html |
| tasks --all --offline, elevated | PASS exit 0; build/test/runClient/runServer exist; no GameTest task | r2-m0-tasks.log |
| Existing JavaCompiler harness, fresh production outputs | PASS exit 0, 43 sources; two existing EventBusSubscriber deprecation warnings | r2-m0-compile.log; javac-main.args |
| Existing JavaCompiler harness, fresh test outputs | PASS exit 0, 15 sources | r2-m0-test-compile.log; javac-test.args |
| Existing JUnit console harness, all retained classes | PASS exit 0: 119 started, 119 successful, 0 failed/skipped | r2-m0-tests.log; java-test.args; test-reports/ |

Exact harness invocation (working directory for main/test compilation: previous task root, C:/Users/solow/Documents/Codex/2026-09-09/files-pasted-by-the-user-hometown):

```powershell
& 'C:/Program Files/Java/jdk-21.0.12/bin/java.exe' 'C:/Users/solow/Documents/Codex/2026-09-09/files-pasted-by-the-user-hometown/work/Compile.java' '@C:/Users/solow/Documents/Codex/2026-09-10/you-are-implementing-the-hometown-mod/work/javac-main.args'
& 'C:/Program Files/Java/jdk-21.0.12/bin/java.exe' 'C:/Users/solow/Documents/Codex/2026-09-09/files-pasted-by-the-user-hometown/work/Compile.java' '@C:/Users/solow/Documents/Codex/2026-09-10/you-are-implementing-the-hometown-mod/work/javac-test.args'
& 'C:/Program Files/Java/jdk-21.0.12/bin/java.exe' '@C:/Users/solow/Documents/Codex/2026-09-10/you-are-implementing-the-hometown-mod/work/java-test.args'
```

The retained compiler harness was inspected. Main/test destinations were changed only in new argument copies. Fresh classes plus unchanged resources were zipped using System.IO.Compression.ZipFile.CreateFromDirectory into work/baseline-check.jar and work/baseline-tests.jar for this harness. This is an independent diagnostic build, not a substitute PASS for Gradle.

## B01–B08 acceptance evidence

All automated tests below are part of the 119-test PASS. They are mocked/pure/serialization checks, not actual Minecraft world acceptance. Full B01–B08 remain NOT RUN in-game.

| ID | Automated characterization PASS | Required live fixture / expected outcome |
| --- | --- | --- |
| R2 B01 | SettlementPersistenceTest, SettlementManagerTest, LedgerAndPayloadTest | Copy an existing named-town world; open Ledger, save, fully restart, reopen. Same UUID/name/dimension/bounds/residents/founding record, no reset or duplicate |
| R2 B02 | HousingIntegrationTest.baselineAddRemoveAndPopulationChanges and zeroPopulationAndSurplusAndRounding | Ten residents/eight enclosed beds: 80%, two unhoused, zero spare; add two: 100%; furniture cannot change capacity |
| R2 B03 | PrivacyIntegrationTest.sixPrivateAndTwoSharedProduceNinetyFive, highDensityNeverReducesCapacity, shortageCanHavePerfectPrivacy, emptyAndZeroPopulationAndIncompleteHaveConsistentAvailability | Six private plus two shared beds: 95%; ten in one room: 40% with ten capacity; eight private/ten residents: 100% privacy and 80% supply; check zero residents |
| R2 B04 | HousingIntegrationTest.windowBreakRepairAndDoorStateRefresh; RoomDetectorTest; PrivacyIntegrationTest.brokenWindowRemovesBedsFromPrivacyAndRepairRestoresThem | Open/close door keeps validity; remove window unseals; replace restores |
| R2 B05 | FoodScannerTest.thresholdsPopulationAndVisualCap, zeroPopulationAndCustomDailyNeed, stackMathAndAddRemoveFoodNeverConsumeItems | Ten residents, 1000 nutrition, daily 20: need 200, five days; twenty residents: 2.5 days; zero: N/A |
| R2 B06 | FoodScannerTest.thresholdsPopulationAndVisualCap; FoodRobustnessTest.noKnownFoodPartialNeverReportsEmpty | Test 0, 1, 3, 7 days and immediately below; twelve days keeps text/full bar; partial zero never EMPTY |
| R2 B07 | FoodScannerTest chest/doubleChest/boundary/exclusion/standardFoodComponent/resource tests | Chest/trapped chest/barrel, double chest, excluded edible/nonfood, standard-component mod food, untagged storage; count local slots once and leave contents unchanged |
| R2 B08 | FoodScannerTest.ungeneratedLootIsNeverUnpacked | Compare unresolved loot metadata/contents before and after Ledger/debug; unchanged; open normally then rescan and include food |

Reason live checks are NOT RUN: no running Minecraft validation session or existing-world fixture was supplied; repository has mock JUnit fixtures but no GameTests or world fixture. Actual font/GPU/input, normal gameplay loot resolution and full game restart cannot be inferred from those tests. Owner action: use a copied existing world and matching client/server mod version, execute these fixtures through runClient, record actual observations/build hash and GUI scales 2/3/4. Do not overwrite the original world.

Command mapping for subsequent validation, verified from pinned tasks:

```powershell
.\gradlew.bat test
.\gradlew.bat test --tests 'dev.conner.hometown.room.HousingIntegrationTest' --tests 'dev.conner.hometown.room.PrivacyIntegrationTest' --tests 'dev.conner.hometown.food.FoodScannerTest' --tests 'dev.conner.hometown.food.FoodRobustnessTest' --tests 'dev.conner.hometown.settlement.SettlementPersistenceTest'
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Dedicated-server startup: NOT RUN this discovery invocation; command exists, configured with --nogui. It is not a substitute for B01 world restart. Owner action at integration gate: start isolated development server, review/accept Minecraft EULA personally where prompted, connect matching client, record successful startup and shutdown/save. GameTests: NOT RUN, no configured run or source fixture; there is no valid existing runGameTestServer command to claim. Use the explicit manual B fixtures until a selected milestone adds relevant supported fixtures.

## Initial discovery artifact and changed files (historical)

Gradle produced build/libs/hometown-0.4.0-alpha.2.jar (SHA-256 F9408929992C2ACA8F810BEB4CF206880CE4F4A35B02AFBE4E1E942F5B1BB618) and the matching sources JAR at the existing output location. This is an unchanged gameplay baseline artifact, not a v1.0 release and not evidence of a successful overall build. Older release JARs/ZIPs were not replaced.

Added files: docs/HOMETOWN_IMPLEMENTATION_STATUS.md (this record), docs/evidence/r2-m0-2026-09-10/ (command logs, argument files, manifests, fresh JUnit XML, Gradle failure XML), docs/references/ (unchanged R2/R3 source documents). Generated files: build/ and .gradle/ plus the existing task's Gradle cache; this invocation's work/ contains isolated diagnostic classes/JARs/logs. No existing Java, resource, build or test source edits. A copy of this status is provided in the current invocation's outputs folder for handoff convenience; this repository copy remains authoritative.

## Initial discovery failures and next action (superseded below)

Pre-existing and reproduced: sandbox archive-close metadata access failure; current UI partial Food denominator violation; 5-tick/no-coalescing request behavior; later Development/History placeholders. Newly observed on unchanged baseline: normal Gradle test worker cannot load test classes even though all 15 compiled classes exist in build/classes/java/test. Root cause is not yet proven; do not call it a source compilation failure. Offline missing test dependencies were resolved by downloading the pinned dependencies. Two production deprecation warnings remain. No regression was introduced by this documentation-only milestone.

Completed M0 requirements: source/archive inventory, pinned platform and command mapping, section 11 owner map, save schema and baseline source characterization, verification that prior artifacts/tests exist, fresh full harness suite. No full live acceptance ID or normal-build PASS is claimed.

**Resume action: continue R2 M0 only, diagnose the normal Gradle test-worker classpath failure without changing pinned dependencies or gameplay, rerun test/build, and obtain recorded live B01–B08 outcomes.** M0 stays PARTIAL until its required gates pass. Then a later owner invocation may select R2 M1; do not start it automatically. R3 implementation remains behind the R2 integration gate. No commits, merges, publication or follow-on tasks were made.

## R2 M0 continuation checkpoints (historical)

Owner amendment received 2026-09-10: OWNER_VALIDATION_PENDING permits a later implementation milestone only when implementation, compilation, automated and agent-runnable checks pass and only unavailable/interactive owner checks remain. It does not satisfy the final v1.0 COMPLETE gate. A failed owner check returns the affected milestone to PARTIAL and makes the earliest affected milestone the next target.

This invocation continues R2 M0 only. Root-cause candidate: missing neoForge.unitTest.testedMod means test output is not associated with Hometown's loaded module. Added testedMod = mods.hometown without changing pinned dependencies. Running gradlew.bat test --info; next inspect results, then normal test/build. Prior evidence retained. State remains PARTIAL until validation is finished.
Checkpoint: testedMod mapping fixed class loading; all 119 tests execute. The first normal run exposed a persistence fixture race (NoSuchFileException), confirmed against pinned NeoForge 21.1.250 SavedData and IOUtilities source: save schedules I/O and clears dirty immediately. The test now waits via IOUtilities.waitUntilIOWorkerComplete after both save call sites, retaining all assertions. Normal gradlew.bat test is running; next run gradlew.bat build and inspect XML totals. State remains PARTIAL until all required agent validation passes. No R2 M1 work.

## Current continuation result — owner protocol amendment

**R2 M0: OWNER_VALIDATION_PENDING.** This result supersedes the initial PARTIAL state and checkpoint next actions above. Implementation/discovery work is complete, all required compilation and automated checks pass, and no known defect blocks M0. Only interactive owner B01–B08 validation remains. The known Food denominator/request-rule gaps are explicitly assigned R2 M1 requirements, not uncompleted M0 implementation. They remain recorded and were not changed in this invocation.

Owner amendment (2026-09-10) is authoritative:

- OWNER_VALIDATION_PENDING requires completed implementation, passing required compilation, automated and agent-runnable validation, and no known implementation defect blocking that milestone.
- Only required checks needing owner gameplay, GUI inspection, physical client input or another unavailable environment may remain pending.
- A later invocation may begin the next implementation milestone from this state. It does not authorize automatic continuation.
- OWNER_VALIDATION_PENDING is not COMPLETE for the v1.0 release gate. Every pending owner item must eventually receive recorded PASS.
- A manual FAIL returns the affected milestone to PARTIAL; the earliest affected milestone becomes the next implementation target.

### Diagnosis and minimal correction

1. build.gradle enabled NeoForge unit testing without assigning testedMod. The FML ModuleClassLoader therefore could not resolve test classes despite their compiled output being present. Added `testedMod = mods.hometown` inside the existing unitTest block, with a short explanatory comment. This is the supported [ModDevGradle unit-test configuration](https://github.com/neoforged/ModDevGradle#unit-testing-with-junit); execution on pinned 2.0.146 confirms support. No dependencies, loader, mappings, game, Java or plugin versions changed.
2. Once loaded correctly, all 119 tests ran and the actual patched NeoForge runtime exposed one persistence-fixture race. SavedData.save(File, Provider) in the pinned generated sources JAR copies the tag, schedules IOUtilities.withIOWorker, then clears dirty before disk completion. The old direct harness used an unpatched mapped Minecraft save path and did not expose this race. The fixture immediately read the not-yet-written file and failed with NoSuchFileException.
3. SettlementPersistenceTest now calls IOUtilities.waitUntilIOWorkerComplete after its initial save and each save in the two-reload loop. The pinned IOUtilities method joins queued writes. No sleeps, retries, skipped tests, mocks replacing disk I/O or relaxed assertions were added. The same real compressed-file reload and copied-world identity assertions remain. Production Hometown persistence was not changed. This correction does not claim R3 cross-file durability or implement any R3 recovery system.

### Commands and validation

Working directory for all Gradle commands: C:/Users/solow/Documents/Codex/2026-09-09/files-pasted-by-the-user-hometown/outputs/hometown. Same Java and Gradle cache assignments as above. Commands ran with approved elevated filesystem access to avoid the previously characterized Windows archive metadata sandbox failure. No init script or external compiler substitutes were used for these final Gradle validations.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.12'
$env:GRADLE_USER_HOME = 'C:/Users/solow/Documents/Codex/2026-09-09/files-pasted-by-the-user-hometown/work/gradle-cache'
.\gradlew.bat test --info
.\gradlew.bat test
.\gradlew.bat build
```

Evidence root: docs/evidence/r2-m0-gradle-fix-2026-09-10/.

| Actual command | Result | Evidence |
| --- | --- | --- |
| test --info, after testedMod correction only | FAIL exit 1, 119 executed/1 failed: persistence fixture async-write race, subsequently fixed | m0-fix-test.log |
| test, after both corrections | PASS exit 0, BUILD SUCCESSFUL in 24s; 119 tests, 0 failures, 0 errors, 0 skipped; changed test source compiled | m0-normal-test.log; test-results/TEST-*.xml |
| build, after passing test | PASS exit 0, BUILD SUCCESSFUL in 6s; normal assemble/check/build succeed using current up-to-date compilation/test outputs | m0-normal-build.log |
| Source/resource preservation against original manifest | PASS: only SettlementPersistenceTest.java changed under src; all production sources and resources unchanged | Original source-manifest.sha256 plus retained changed test file |
| Existing 119-test behavior | PASS through normal patched NeoForge Gradle runner; no class/test excluded | 15 XML test-suite files, total 119 |

All B01–B08 automated characterization mappings in the table above now also pass through the normal Gradle runner. Existing deprecated EventBusSubscriber usage and JVM class-sharing warning are non-failing baseline warnings. There is no unresolved Gradle or automated-test defect. Dedicated-server/GameTest requirements remain assigned to their later integration milestones as documented above; M0 required their command mapping, which is complete. No new agent-runnable M0 check remains outstanding.

### Outstanding owner checks

Each row is **NOT RUN — OWNER VALIDATION PENDING**, not PASS. The detailed B01–B08 fixture table above remains binding. Use a copied existing world, pinned Minecraft 1.21.1 / NeoForge 21.1.250, and Hometown 0.4.0-alpha.2 with artifact SHA-256 below. Log owner/date/build hash, exact fixture values and observed outcome for every ID. Reopen the Ledger after each physical change, and inspect relevant screens at supported GUI scales 2/3/4.

| ID | Required interactive action and PASS criterion |
| --- | --- |
| R2 B01 | Open an existing named town; record UUID/name/dimension/bounds/residents/founding History; save, exit fully, restart and reopen. All remain available, with no duplicate founding or reset. |
| R2 B02 | Ten residents/eight enclosed beds gives 80% capacity, two unhoused, zero spare. Add two valid beds: 100%. Add/remove furniture: capacity unchanged. |
| R2 B03 | Six private beds plus one two-bed room gives 95% Privacy; ten beds in one room gives ten capacity/40%; eight private beds for ten residents gives 80% Supply/100% Privacy. Complete zero residents shows N/A capacity; complete nonempty enclosed beds retain existing Privacy. |
| R2 B04 | Open/close a supported door without changing validity; remove a tested window to unseal the room; restore the window to restore validity. |
| R2 B05 | Ten residents with 1000 stored nutrition and per-resident default 20 gives daily need 200/five days; twenty residents gives 2.5 days; complete zero residents gives N/A days. |
| R2 B06 | Verify zero nutrition EMPTY; positive below one day CRITICAL; exactly one LOW; exactly three STABLE; exactly seven STOCKED, including nearest achievable values below each threshold. Twelve days retains 12-day text/full bar. Partial zero never displays EMPTY. |
| R2 B07 | Inspect chest/trapped chest/barrel/double chest, excluded edible, non-food, a modded standard-food item and untagged storage. Count qualifying local contents once; excluded/untagged contents absent; repeated observations do not modify items. Record any optional food provider used. |
| R2 B08 | Record unopened loot container metadata/contents, run Ledger and debug scans, confirm no resolution/mutation. Open normally, rescan and verify food inclusion and removal of the resolved-loot reason if no other incomplete scope remains. |

Unavailable environment: this agent has no interactive Minecraft client/gameplay session or owner world fixture for these tests. Mocked world checks and disk tests do not establish real GUI/input or gameplay outcomes. Owner supplies actual PASS/FAIL observations; do not infer results from the build. A failed row returns M0 to PARTIAL until addressed.

### Final files, artifact and next action

Changed existing files: build.gradle (testedMod association); src/test/java/dev/conner/hometown/settlement/SettlementPersistenceTest.java (wait for pinned asynchronous save completion); docs/HOMETOWN_IMPLEMENTATION_STATUS.md (protocol amendment and current evidence). Added docs/evidence/r2-m0-gradle-fix-2026-09-10/ with logs, XML and before/after build/test evidence. Original evidence/reference files remain. There is still no .git; no Git diff/commit ID is claimed.

Normal output: build/libs/hometown-0.4.0-alpha.2.jar, SHA-256 F9408929992C2ACA8F810BEB4CF206880CE4F4A35B02AFBE4E1E942F5B1BB618. The artifact is byte-identical to the gameplay baseline, as expected for build/test-only corrections. The matching sources JAR remains at build/libs. This invocation does not create a v1.0 release or overwrite older release files.

Next implementation target eligible in a later owner invocation: **R2 M1**, unless a failed owner validation makes M0 PARTIAL first. Owner action in parallel with later authorized work: execute and record B01–B08. R2 M1 was not started; no production feature changes, commits, publication, merge, automation or follow-on work were performed. Stop here.

## Sequential checkpoint protocol and R2 M1 work in progress

Owner amendment supersedes one-milestone stop boundaries: finish, validate and record each checkpoint, then continue sequentially while safe. Stop on unresolved validation failures, protected conflicts, material ambiguity, unsafe implementation, execution-window limit or reached target. OWNER_VALIDATION_PENDING permits progression but not final v1.0 completion. Version checkpoints: full R2=0.5.0-alpha.1; Hall/projects/Storehouse=0.6.0-alpha.1; operations/meals/five sites=0.7.0-alpha.1; Inn/travelers=0.8.0-alpha.1; Market/recruitment/six foundations=0.9.0-alpha.1; all release gates including owner validation=1.0.0. Do not bump merely for starting work.

Historical in-progress entry: R2 M1 began with denominator guard, Housing adapter, Safety collection/UI/debug and shared request work. Final checkpoint evidence follows.
## R2 M1 checkpoint — 2026-09-10

**State: OWNER_VALIDATION_PENDING.** Implementation and agent-runnable validation pass; remaining checks require interactive Minecraft gameplay/client inspection. No known implementation defect blocks this checkpoint. No v1.0 or full R2 completion claim. Current project version remains 0.4.0-alpha.2; the 0.5.0-alpha.1 checkpoint is not reached.

### Requirements implemented

R2 sections 3–5, 8 (Safety), 11 (existing owners), 14–15 (M1 config/budgets), M1 and acceptance S01–S12, Q01–Q03/Q05–Q06: immutable shared provenance; server-thread authorized Ledger requests; same-tick generation coalescing; compatible per-player/per-town cooldown reuse preserving time/generation; fresh reopen outside cooldown; existing resident paging without scans; bounded expiring sessions; invalidation for effective config/tags/bounds; lifecycle release; separate Safety entity and lighting observations. Canonical enclosed bed heads come from the existing Housing pass. No replacement room detector or second resident pass. The existing Housing scan result remains authoritative for completeness.

Safety recognizes the exact 33 default threat types and iron-golem-only protector tag, exclusion precedence and overlap diagnostics. Bounded loaded-entity traversal counts attempted/rejected/duplicate records. Lighting samples only air one block above known enclosed heads using BLOCK light, default threshold 1 (zero permitted and disclosed). Known partial observations never establish authoritative coverage. Complete empty residential scope is N/A. Entity incompleteness does not blank complete lighting. No combined Safety score, effects or History mutations.

Food Q02 narrow correction: incomplete population withholds reserve days/bar while preserving known nutrition and observed population. Reliable population retains existing lower-bound Reserves. All other protected calculations and storage semantics remain.

Ordering adaptation: the pinned loaded entity API supports abortable native spatial traversal, not a pre-bounded UUID list. Safety aborts that traversal before exceeding the lower cap instead of constructing an unbounded sortable list. Complete results are order independent; partial results honestly describe native traversal scope. Block samples use x/y/z order. This records the shared ordering exception anticipated by section 15 without changing the protected resident scan.

### Changed files and preservation

Exact source/resource inventory: evidence/r2-m1-2026-09-10/source-changes-from-m0.txt and source-manifest.sha256. The comparison includes the previously approved M0 SettlementPersistenceTest change; that file was NOT changed again by M1.

Production changes: client/TownLedgerScreen.java (Safety spread, local reasons, age hint and denominator display); command/HometownDebugCommands.java (permission-2 Safety command); config/HometownServerConfig.java (six specified keys); food/FoodRules.java, FoodSnapshot.java and FoodDebugReport.java (denominator guard/presentation); Hometown.java (session cleanup); housing/HousingScanner.java (read-only canonical-bed adapter); network/data/TownLedgerSnapshot.java, network/HometownNetworking.java, RequestTownLedgerPayload.java, TownLedgerSnapshotPayload.java (snapshot provenance and generation-safe protocol 7); settlement/TownLedgerService.java (existing request owner extended); new observation/ObservationMetadata.java and safety/SafetyCollector.java, SafetyEvaluator.java, SafetySnapshot.java, SafetyDebugReport.java; en_us.json and four exact Safety entity tags.

Tests: client/TownLedgerScreenTest.java, food/FoodRobustnessTest.java, settlement/TownLedgerServiceTest.java extended; new TestServerConfig.java, settlement/LedgerObservationTest.java, safety/SafetyCollectorTest.java and SafetyConfigTest.java. Build dependencies, M0 async-save fix, RoomDetector, LoadedRoomWorld, persistence implementation, founding, FoodScanner and existing resident collector remain unchanged. No Git metadata is available.

### Commands and results

Execution directory is the source root above. Every Gradle command used:
JAVA_HOME=C:/Program Files/Java/jdk-21.0.12
GRADLE_USER_HOME=C:/Users/solow/Documents/Codex/2026-09-09/files-pasted-by-the-user-hometown/work/gradle-cache
Elevated sandbox execution is required for the pinned cache's ZIP filesystem access. No dependency versions changed.

| Exact command | Result | Evidence |
| --- | --- | --- |
| ./gradlew.bat test --tests '*SafetyCollectorTest' | PASS, 12 Safety collector tests | m1-safety-test.log |
| ./gradlew.bat test --tests '*LedgerObservationTest' --tests '*TownLedgerServiceTest' | PASS, 2 request tests, 21 seconds | m1-request-test.log |
| ./gradlew.bat test | PASS, 133 tests, 0 failures/errors/skipped, 38 seconds | m1-full-validation.log |
| ./gradlew.bat test (after age display and actual tag-binding reload test) | PASS, 133 tests, 0 failures/errors/skipped, 37 seconds | m1-final-test.log; test-results/TEST-*.xml (18 suites) |
| ./gradlew.bat build | PASS, exit 0, 8 seconds; assemble/check/build succeeded; tests correctly up-to-date from prior normal run | m1-build.log |
| Get-FileHash build/libs/hometown-0.4.0-alpha.2.jar -Algorithm SHA256 | PASS, artifact hash below | this record |
| Source inspection of new world access plus mocked no-load assertions | PASS; getChunkNow only, abortable loaded-entity query, BLOCK light; no ticket, inventory mutation, save or background collector calls | SafetyCollector.java, SafetyCollectorTest.java, source manifest |

All log paths in this section are under docs/evidence/r2-m1-2026-09-10/. Early compilation/fixture failures are retained, not hidden: comparator type inference; missing loaded config; unsupported config close method; sealed-interface mocking; final carrier mocking. These were diagnosed and corrected. Final test fixture uses the pinned loader's actual in-memory configuration carrier; no production workaround or dependency downgrade. Non-failing warnings: pre-existing EventBusSubscriber removal warnings, JVM class-sharing warning; the tag reload test calls the pinned deprecated bindTags API. No unresolved automated failures.

### Acceptance evidence and outstanding owner actions

Automated PASS means the named executable fixture passed, not that real gameplay was observed. Every owner row below is **NOT RUN — OWNER VALIDATION PENDING**. Use a copied world, matching pinned client/server and the M1 artifact hash below. Record owner/date/hash/coordinates/loaded chunks/actual results. Reopen outside the 40-tick cooldown after physical changes.

| IDs | Agent evidence PASS | Remaining owner fixture and expected PASS |
| --- | --- | --- |
| S01–S04 | SafetyCollectorTest: exact membership, 2 threats/1 protector, UUID dedup, alive/removed/bounds/player filtering, exclusion precedence; no AI reads | Place zombie, creeper, iron golem: 2/1; move/kill/remove across every boundary; anger all six excluded neutral defaults: unchanged; reload overlap/exclusion tags and verify threat→protector→neither, with conflict diagnostics. |
| S05–S06 | BLOCK-only threshold math, 100/75/0%, threshold 0 | Four enclosed beds: four lit=100, three lit=75, none=0; change only sky/day: unchanged; remove local light and reopen: updated. Threshold zero must explicitly count zero light as lit. |
| S07–S08 | Solid sample and no alternate search; complete no beds versus unknown Housing | Solid block above one of four beds: 3 assessed/1 unassessed, partial observed ratio, no authoritative ratio; no enclosed beds with complete scope: N/A, NO_ENCLOSED_BEDS. Unloaded/unknown Housing must not claim no beds. |
| S09–S10 | Independent panels, unloaded chunks, known complete Housing lighting, no-loading checks | Add/remove torch: Supply/Privacy unchanged. Unload a required entity chunk while all residential samples remain known: lighting remains usable; partial zero threats says only no threats observed in inspected area. Unload a sample: record reason and absent authority. |
| S11–S12 | Raw 8/9 retained, bounded codec/debug; lower module/shared ceilings, zero-work unavailable, useful partial retained | Eight lit of nine displays 89%; cap entity/block work on a dense fixture and compare exact attempts/limits/reasons in debug; known values survive; repeated debug does not create History. |
| Q01–Q02 | FoodRobustnessTest/FoodScannerTest: fringe retained nutrition, incomplete denominator; actual screen no lower-bound days/bar for unreliable population | Food in a loaded chunk plus unloaded fringe retains known nutrition with reason. Incomplete resident scope must show observed population/known nutrition with N/A days, never “At least X days.” |
| Q03/Q05/Q06 | Safety cap/bounds/loaded access tests; existing Food cross-boundary storage checks; source audit | Exercise lower caps and horizontal/vertical/negative-coordinate boundaries. Before/after repeated Ledger/debug requests record loaded chunks/tickets: no Hometown tickets or increased coverage; cross-boundary chest contributes no outside slots. |
| B02–B04 | Existing HousingIntegrationTest, PrivacyIntegrationTest, RoomDetectorTest all PASS | Execute unchanged M0 fixture table: 10 residents/8→10 beds, 95% mixed Privacy and 40% dense room, supported doors/window repair; adding light never changes these results. |
| B05–B08 | Existing Food suites all PASS except explicitly corrected Q02 expectation; no test removed | Execute unchanged M0 Food fixture table: 1000/200=5 days then 2.5, thresholds 0/1/3/7, standard/excluded/modded/double storage, unresolved loot unchanged until normal opening. |
| Shared request/UI | LedgerObservationTest: two-client generation coalescing, t+39 reuse/t+40 fresh, same-generation paging, stale/closed/session rejection, config and actual tag reload invalidation, read-only debug. TownLedgerScreenTest exercises real drawing at seven GUI sizes, N/A/disabled/partial/raw-rounded display and no subcategory request | Two clients open same town in same tick, inspect original age/time and unchanged generation on cached responses; page/reopen/config-reload/disconnect/dimension switch with no stale content. Inspect all Safety reason pages, long/localized labels and actual GPU/input at GUI scales 2/3/4; verify permission-2 debug access. |

GameTests: NOT RUN; repository provides no configured GameTest task or game-world fixture. Section 17's explicit manual fallback applies. Interactive runClient fixtures are unavailable to this agent (no active physical Minecraft client/owner world), so the owner amendment permits this checkpoint state. Dedicated server startup/save/reload/performance gate remains R2 M6, not claimed by M1's mocked checks.

### Preserved checkpoint and continuation

M1 artifact preserved at:
C:/Users/solow/Documents/Codex/2026-09-10/you-are-implementing-the-hometown-mod/outputs/r2-m1/hometown-0.4.0-alpha.2.jar
SHA-256 **67B4989BF3CBC9D4FF919ED97B096C3F4B7691A7D71DDB7B8F1146956F25AD05**.

This is an implementation checkpoint with pending owner validation, not the unchanged M0 JAR or a new release version. Protocol 7 requires matching client/server artifacts. Next eligible milestone: **R2 M2 Comfort**. Resume action: read exact Comfort geometry/category/predicate/reload requirements, implement through existing Housing room results without altering detector semantics, validate C01–C10 and required regressions, and record the checkpoint before any M3 implementation.
