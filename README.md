# Hometown

Hometown is a Minecraft **1.21.1** mod for **NeoForge 21.1.250**, built with **Java 21**.

The mod turns vanilla villages into named settlements centered on a Bell and a Town Ledger. Existing implemented systems include founding/identity, resident statistics, Housing, Food, Safety, Comfort, Food Variety, Growing observation, Commerce, Prosperity, and retained structural History.

## Current development state

- Version: `0.8.1`
- Platform: Minecraft 1.21.1 / NeoForge 21.1.250
- Java toolchain: Java 21
- Gradle wrapper: included in the repository
- Current milestone: **Revision 2 M5 — Prosperity + History — COMPLETE**
- Automated gate: **PASS** — the M5 implementation suite and normal build pass on the exact `0.8.1` UI-polish code head in GitHub Actions run `34723647758`
- Owner/play validation: **PASS** — save migration, Prosperity, confirmed/persistent Population History, and Housing shortage start/resolution were validated in the real Oured settlement
- Next milestone: **Revision 2 M6 — Integration**

M0–M5 implementation work is tracked separately from owner/play validation. A passing compile alone is not milestone completion; milestone acceptance also requires the applicable automated and interactive validation gates.

The owner has validated the core M3 `0.6.0` Food Variety/Growing behavior, M4 `0.7.0` Commerce behavior, and M5 `0.8.0` Prosperity/History behavior in-game. M5 derives Prosperity from the five authoritative Development inputs and adds confirmed, retained History events without introducing background or forced-chunk scans. The follow-up `0.8.1` patch resolves the two Prosperity presentation findings from owner validation by wrapping the authority note and rendering weighted contributions as effective percentages and Development Index points.

**M5 migration note:** `0.8.0` introduced Hometown SavedData schema version 2 so per-town History state can be retained. The migration preserves existing settlement identity and converts the previously authoritative founding record into the structural founding History event. Existing pre-M5 worlds should still be backed up before first opening them with any `0.8.x` build.

See [`docs/README.md`](docs/README.md) for specification authority, implementation tracking, historical references, and retained evidence.

## Repository layout

```text
Hometown/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
├── src/
│   ├── main/
│   └── test/
├── docs/
│   ├── specs/
│   ├── implementation/
│   ├── reference/
│   └── evidence/
└── .github/workflows/
```

The Gradle project lives directly at the repository root. Historical compiled JARs and source ZIP exports are intentionally not kept in the active tree; Git history preserves the imported snapshots, and future distributable builds should use GitHub Releases rather than being committed beside source code.

## Build and test

From the repository root on Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

On macOS/Linux:

```bash
./gradlew test
./gradlew build
```

Useful development runs:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

A successful build writes the mod artifact under `build/libs/` as `hometown-<version>.jar`.

### Playtest artifact versioning

`mod_version` in `gradle.properties` is the single source of truth for both the built JAR filename and Hometown's NeoForge mod metadata.

Every intentional playtest build containing changed code or data must receive a new, never-reused version before it is handed off for testing.

Hometown uses milestone-oriented pre-1.0 versioning:

- Advancing to a new implementation milestone increments the **minor** version and resets the patch number. Example: `0.7.x` → `0.8.0`.
- A changed playtest build within the same milestone increments the **patch** version. Example: `0.8.0` → `0.8.1` → `0.8.2`.
- The next milestone after `0.8.x` therefore begins at `0.9.0`.
- Version identifiers are never reused for materially different source or data.

Because Hometown is still below `1.0`, the `0.x` version itself identifies the project as pre-1.0 development; separate `-alpha.N` suffixes are not required for normal milestone playtests.

## Development workflow

Development should normally happen on a scoped branch rather than directly on `main`.

Typical flow:

1. Branch from a known-green `main`.
2. Make one logically scoped change or milestone.
3. Run `test` and `build` locally.
4. Review the diff.
5. Push the branch and let GitHub Actions verify it independently.
6. Merge only after the branch is green and any required specification/owner validation is accounted for.

Repository-organization work, gameplay changes, and milestone evidence should remain separable whenever practical.

## Specification authority

Current authoritative design documents are retained under `docs/specs/`:

- `Hometown_Implementation_Specification_Revision_2.docx`
- `Hometown_Progression_Town_Operations_Revision_3.docx`

Older Markdown specifications and milestone notes under `docs/reference/` are compatibility/history references and must not override current Revision 2 or Revision 3 requirements.

Implementation status and validation history are tracked in `docs/implementation/HOMETOWN_IMPLEMENTATION_STATUS.md` and `docs/evidence/`.

## Important architectural constraints

Hometown is intentionally server-authoritative and conservative about world access. Existing systems should preserve loaded-only observation behavior, avoid forced chunk loading and chunk tickets, reuse established collectors instead of creating competing scans, and distinguish incomplete/unavailable observations from authoritative zero values.

Prosperity is a pure derived view over existing authoritative observations. History advances only on fresh normal Ledger generations; cached page navigation and debug views do not perform hidden world scans or advance confirmation state.

The current implementation remains a work in progress toward the full Revision 2 and Revision 3 design.
