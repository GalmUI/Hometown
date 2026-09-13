# Hometown

Hometown is a Minecraft **1.21.1** mod for **NeoForge 21.1.250**, built with **Java 21**.

The mod turns vanilla villages into named settlements centered on a Bell and a Town Ledger. Revision 2 implements founding/identity, resident statistics, Housing, Food Reserves, Safety, Comfort, Food Variety, Growing observation, Commerce, Prosperity, and retained structural History.

## Current development state

- Version: `0.9.1`
- Platform: Minecraft 1.21.1 / NeoForge 21.1.250
- Java toolchain: Java 21
- Gradle wrapper: included in the repository
- Current milestone: **Revision 2 M6 — Integration and delivery — COMPLETE**
- Revision 2 status: **COMPLETE**
- Automated gate: **PASS — 206/206 tests and normal build** on the final `0.9.1` implementation/test head in GitHub Actions run `34733522239`
- Dedicated-server/resource-reload gate: **PASS**
- Owner/play validation: **PASS** — integrated Ledger/UI, persistence/reload, unresolved loot, loaded-only behavior, no force-loading, representative performance, and dense-fixture performance were validated in-game
- Next implementation authority: **Revision 3 — Progression and Town Operations**

Revision 2 completion is backed by `docs/evidence/r2-m6-2026-09-12/README.md` and the full acceptance matrix. A passing compile alone is not treated as milestone completion; automated, integration, server, performance and required owner/play gates are recorded separately.

Representative R2 performance validation was performed on an **AMD Ryzen 9 3900X** with approximately **6 GB** allocated to the Minecraft/Java client. Oured averaged about **15.4 ms** per fresh Ledger observation across four samples, with a **34.6 ms** worst sample. The deliberately denser Osea fixture averaged about **16.5 ms**, with a **32.4 ms** worst sample. Both remained below the R2 representative one-tick investigation threshold of 50 ms.

`0.8.0` introduced Hometown SavedData schema version 2 so per-town History state can be retained. Existing pre-M5 worlds should still be backed up before their first load on modern Hometown builds, but owner testing confirmed migration, durable History, save/restart and resource reload behavior through final R2.

See [`docs/README.md`](docs/README.md) for specification authority, implementation tracking, configuration/datapack surfaces, and retained evidence.

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

The Gradle project lives directly at the repository root. Historical compiled JARs and source ZIP exports are intentionally not kept in the active tree; Git history preserves imported snapshots, and future distributable builds should use GitHub Releases rather than being committed beside source code.

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

- Advancing to a new implementation milestone increments the **minor** version and resets the patch number.
- A changed playtest build within the same milestone increments the **patch** version.
- Version identifiers are never reused for materially different source or data.

Because Hometown remains below `1.0`, the `0.x` version itself identifies the project as pre-1.0 development; separate alpha suffixes are not required for normal milestone playtests.

Revision 3 should choose its first artifact version from the Revision 3 milestone plan rather than treating `0.9.1` as a generic patch line for new systems.

## Development workflow

Development should normally happen on a scoped branch rather than directly on `main`.

Typical flow:

1. Branch from a known-green accepted checkpoint.
2. Make one logically scoped change or milestone.
3. Run `test` and `build` locally.
4. Review the diff.
5. Push the branch and let GitHub Actions verify it independently.
6. Merge only after the branch is green and any required specification/owner validation is accounted for.

Repository organization, gameplay changes and milestone evidence should remain separable whenever practical.

## Specification authority

Current authoritative design documents are retained under `docs/specs/`:

- `Hometown_Implementation_Specification_Revision_2.docx` — completed prerequisite implementation authority
- `Hometown_Progression_Town_Operations_Revision_3.docx` — next progression and town-operations authority

Older Markdown specifications and milestone notes under `docs/reference/` are compatibility/history references and must not override the current specification documents.

Implementation status and validation history are tracked in `docs/implementation/HOMETOWN_IMPLEMENTATION_STATUS.md` and `docs/evidence/`.

## Important architectural constraints

Hometown is intentionally server-authoritative and conservative about world access. Existing systems preserve loaded-only observation behavior, avoid forced chunk loading and chunk tickets, reuse established collectors instead of creating competing scans, and distinguish incomplete/unavailable observations from authoritative zero values.

Prosperity is a pure derived view over existing authoritative observations. History advances only on fresh normal Ledger generations; cached page navigation and debug views do not perform hidden world scans or advance confirmation state.

Revision 3 must build on these accepted Revision 2 contracts rather than replacing or bypassing them.
