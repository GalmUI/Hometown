# Hometown

Hometown is a Minecraft **1.21.1** mod for **NeoForge 21.1.250**, built with **Java 21**.

The mod turns vanilla villages into named settlements centered on a Bell and a Town Ledger. Existing implemented systems include founding/identity, resident statistics, Housing, Food, Safety, and the in-progress Revision 2 Comfort milestone.

## Current development state

- Version: `0.5.0-alpha.1`
- Platform: Minecraft 1.21.1 / NeoForge 21.1.250
- Java toolchain: Java 21
- Gradle wrapper: included in the repository
- Current milestone: **Revision 2 M2 — Comfort (partial / in progress)**
- Last local baseline verification: **133 automated tests passing and full Gradle build passing** after the Comfort snapshot round-trip repair

M0 and M1 automated implementation gates are complete, with required owner/play validation still tracked separately. Do not treat a passing compile alone as milestone completion.

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

Every intentional playtest build containing changed code or data must receive a new, never-reused version before it is handed off for testing. Iterations within the same development target increment the prerelease number (for example, `0.5.0-alpha.1` → `0.5.0-alpha.2`). This prevents two materially different playtest JARs from carrying the same identifier.

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

The current implementation remains a work in progress toward the full Revision 2 and Revision 3 design.
