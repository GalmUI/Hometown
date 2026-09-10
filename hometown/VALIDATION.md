# Food scan robustness validation

43 main sources compile without errors; all 119 tests pass. The new 14 robustness tests cover partial/unavailable scans, preserved data, limits, diagnostics, population qualification, packet round trips, and both debug command forms. Existing regressions pass. See [FOOD-SCAN-FIX.md](FOOD-SCAN-FIX.md).

## Previous milestone evidence

# v0.4a validation

39 main Java sources compile without errors; all 105 tests pass. Food adds 19 tests and extends the native-screen regression. All prior tests remain passing. See [FOOD-RESERVES.md](FOOD-RESERVES.md) for current behavior and build limitations. The user confirmed v0.3c Housing/Privacy in-game; live Food verification remains pending.

## Previous milestone evidence

# v0.3c validation

36 main Java sources compile without errors. All 86 tests pass, including the retained 76 regressions, 9 density/privacy tests, and the new Residents pagination/navigation regression. See [V0.3C-VALIDATION.md](V0.3C-VALIDATION.md) for current evidence and limitations.

## Previous milestone evidence

# v0.3b validation

34 main Java sources compiled without errors; 76 automated tests pass, including all 61 previous tests. Housing refresh, aggregation, loaded-only behavior, wire round trips, and native GUI layouts are covered. See [HOUSING-DIAGNOSTICS.md](HOUSING-DIAGNOSTICS.md) for current details and build limitations. The user confirmed v0.3a room detection in-game; v0.3b still needs live playtesting.

## Historical validation

# v0.3a validation

32 main Java sources compiled without errors; 61 automated tests passed, including all 37 prior regressions. See [ROOM-DETECTION.md](ROOM-DETECTION.md) for current scope, tests, and build limitations. Runtime changes are confined to the new room package, room debug command, and one command registration. Ledger, persistence, and existing settlement scans are unchanged from v0.2.1.

## Previous milestone evidence

# Ledger rendering fix validation

## Completed

- **27 main Java source files compiled successfully**, targeting Java 21 against Minecraft 1.21.1 and NeoForge 21.1.250. No compiler errors remain. The existing client event-bus annotation produces two deprecation warnings.
- **37 tests passed**, with no failures, errors, or skipped tests.
- All **27 foundation regression tests** still pass.
- The `Settlement`, `SettlementManager`, and `HometownSavedData` source files are byte-equivalent after newline normalization to the working foundation. Ledger creation and delivery methods are unchanged.

| New test class | Tests | Verified behavior |
| --- | ---: | --- |
| SettlementScannerTest | 4 | Requested 2/2/1/1 → 3/3/2/2 → 3/3/1/1 live sequence; children; names; duplicate professions; nitwits; life state; stored radius; vertical bounds; loaded-only queries |
| TownLedgerServiceTest | 1 | Server-held item and UUID validation; unknown links; cooldown; missing/replaced Bell; missing dimension; identity preservation; no item consumption |
| TownLedgerSnapshotTest | 3 | Every resident accessible through bounded pages; immutable DTOs; request/snapshot/error codec round trips; Unicode and wire limits; unavailable state |
| TownLedgerScreenTest | 2 | Actual screen drawing methods for all tabs at 320×240, 640×360, and 960×540 GUI sizes; book/tab bounds; placeholders; founding event; child/name display; stale-response rejection; invalid-ledger error |

Screen tests use a recording `GuiGraphics` backend and a test font with deterministic character widths. They exercise the actual screen code without OpenGL. They are not an in-game screenshot or a test of Minecraft's real font rasterizer/input loop.

Server-world tests use mocked server/entity objects with real mapped Minecraft data types. Persistence regression tests still exercise the actual Minecraft SavedData compressed file writer and copied-file UUID round trips. See `TEST-RESULTS.txt` for individual results.

## Build method and limitation

The standard `gradlew.bat build --offline` was attempted. Downloads were already cached, but NeoForge's `extractServer` step failed at the same Windows `ZipFileSystem.close` parent-path lookup (`AccessDeniedException`) seen during the foundation task. This occurs before project compilation and is not a Java source error.

The mod was compiled through the existing short-lived JavaCompiler API harness against official Minecraft 1.21.1 classes mapped with NeoForge AutoRenamingTool and NeoForge 21.1.250 libraries. The process releases its input archives on exit, avoiding the environment's failing archive-close metadata operation. JUnit was run directly. The deliverable JAR packages only the newly compiled Hometown classes and project resources.

This is the compilation/packaging route used for the previous foundation JAR that the user confirmed loads and works. A successful standard Gradle build of this update is not claimed.

## Remaining in-game verification

The user confirmed the existing foundation's working behavior. The **new Ledger milestone** still needs a client/server playtest: actual book appearance and keyboard/mouse use, reopen-driven live changes in a real village, Bell recovery warnings, invalid Ledger behavior, and complete Minecraft restart with an existing Ledger. These checks are listed in [ACCEPTANCE.md](ACCEPTANCE.md).
