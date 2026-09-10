# Hometown — Food Scan Robustness Fix

Minecraft **1.21.1** · NeoForge **21.1.250** · Java **21** · Hometown **0.4.0-alpha.2**

Choose a village with a Book and Bell, then read its living record through the Town Ledger.

## Install

Replace the previous Hometown JAR with `hometown-0.4a-food-scan-fix.jar` in the instance's `mods` folder. Install the same version on both client and server; do not keep both Hometown versions installed together. Existing settlement records and Ledger UUID components remain compatible and require no migration.

The foundation was confirmed working in Minecraft by the user. This update has compiled successfully and passed **119 automated tests**, including screen rendering with a recording graphics backend. The updated UI has not been launched inside Minecraft in this environment. See [VALIDATION.md](VALIDATION.md) for the build details and [ACCEPTANCE.md](ACCEPTANCE.md) for the remaining playtests.

## Food scan robustness

Food now distinguishes COMPLETE, PARTIAL, and UNAVAILABLE scans. Loaded stores remain visible when fringe chunks or other stores cannot be inspected. Partial reserves are labeled as known data and never report EMPTY. Run `/hometown debug food` from inside a town, or retain `/hometown debug food <uuid>`, for exact reasons, counters, and limits. See [FOOD-SCAN-FIX.md](FOOD-SCAN-FIX.md) for the diagnosis, population qualification, and verification.

## Food reserves

Development > Food now reports stored nutrition, daily need, reserve days, Food Security, and store/stack/type counts. It reads tagged chests, trapped chests, and barrels; it never consumes food. Housing Supply and Privacy are unchanged. Safety, Comfort, Commerce, and Prosperity remain placeholders.

Close and reopen the Ledger after inventory or population changes. Configure `nutritionPerResidentPerDay` in the existing server config (default 20). The reserve bar fills at seven days; displayed days remain uncapped. See [FOOD-RESERVES.md](FOOD-RESERVES.md) for tags, semantics, limits, and validation. Install matching client/server versions (network protocol 6).

## The Ledger

Right-click your existing Town Ledger. The server resolves the item in your hand and returns a fresh read-only snapshot.

| Bookmark | Contents |
| --- | --- |
| Overview | Town, founder, founded day, dimension, Bell, population, beds, employed adults, profession diversity |
| Residents | Names, professions, adult/child status; four residents per page |
| Development | Six subpages: Housing Supply/Privacy, Food Reserves, and four placeholder spreads |
| History | The original founding event |

Click a bookmark or use keyboard focus to change tabs. Use the arrows to page through residents. Escape closes the book. The screen does not pause gameplay. Truncated display lines reveal their supplied full text on hover.

Population includes living vanilla adult and baby villagers. Employment excludes babies, NONE, and NITWIT. Profession diversity counts distinct employed adult professions. Beds are intact vanilla beds registered as HOME POIs, whether claimed or unclaimed.

Statistics refresh when the Ledger is reopened or a resident page is requested. Page requests are separately scanned so the server does not retain resident snapshots or track villagers persistently. All loaded residents are accessible through pages; the list is not silently capped.

A missing Bell does not block the Ledger: the book gives the saved replacement coordinates. If some settlement chunks are unloaded, counts are explicitly labeled partial. If scanning is unavailable, statistics show unavailable, not zero. Reading never loads world chunks or creates chunk tickets. Invalid Ledger UUIDs display a clean error.

## Founding remains the same

Hold a plain vanilla Book in the main hand, crouch, and right-click a vanilla Bell. The default requirements are two living villagers and two intact beds nearby. Name the town and confirm. Successful Survival founding consumes one Book and delivers one Ledger; Creative keeps the Book. Full inventories receive a safe dropped Ledger.

Names, overlap checks, server revalidation, per-town radii, founder metadata, persistence, Bell recovery, and operator debug commands retain their foundation behavior. There are no ownership permissions, progression systems, economy mechanics, or editing controls.

## Build

Configure a Java 21 JDK, then run in this directory:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

On macOS/Linux, use `bash ./gradlew build` or `bash ./gradlew runClient`.

A normal Gradle build outputs `build/libs/hometown-0.2.0.jar`. `gradlew.bat test` runs the included JUnit suite. `gradlew.bat runServer` starts a development dedicated server, subject to the normal Minecraft EULA setup.

In this environment the standard NeoForge Gradle extraction task still fails on a Windows parent-path metadata lookup. The supplied JAR was built with the same direct JavaCompiler route as the working foundation artifact, using official mapped Minecraft classes and NeoForge libraries. All source compiler errors are resolved. Minecraft and test/build dependencies are not bundled in the mod JAR.

## Storage and configuration

Settlements still live in the overworld's `data/hometown_settlements.dat` for the whole save. The Ledger milestone adds no persistent fields, resident tracking, or history storage. Normal world saves persist towns; copy the whole world save when transferring a world.

Per-world server settings are in `serverconfig/hometown-server.toml`:

| Setting | Default | Range |
| --- | --- | --- |
| settlementRadius | 64 | 16–256 |
| verticalScanRadius | 32 | 8–128 |
| minimumVillagers | 2 | 0–100 |
| minimumBeds | 2 | 0–100 |
| preventSettlementOverlap | true | boolean |
| consumeFoundingBook | true | boolean |

Existing towns retain their stored horizontal radius. Both founding and Ledger queries use the original box-shaped scan bounds and intact HOME-bed checks. Overlap still uses horizontal circular distance and ignores vertical separation.

## Operator commands

Permission level 2 is required. There is no creation command or deletion UI.

```text
/hometown debug room
/hometown debug housing <uuid>
/hometown debug food <uuid>
/hometown debug list
/hometown debug inspect <uuid>
/hometown debug remove <uuid>
/hometown debug ledger <uuid>
```

## Code map

- `SettlementQueries`: shared loaded-world villager and bed detection.
- `SettlementScanner` / `SettlementStats`: on-demand read-only statistics.
- `TownLedgerService`: validates the server-held Ledger, resolves the town, applies a short request cooldown, and produces a snapshot.
- `RequestTownLedgerPayload` / `TownLedgerSnapshotPayload`: bounded requests, resident pages, responses, and errors.
- `TownLedgerSnapshot` / `ResidentSummary`: immutable presentation data, without entity NBT or resident UUIDs.
- `TownLedgerScreen`: parchment visuals, bookmarks, paging, and errors; no settlement mutations.

See [LEDGER-CHANGELOG.md](LEDGER-CHANGELOG.md) and [LEDGER_SPECIFICATION.md](LEDGER_SPECIFICATION.md) for this milestone. The original founding specification remains in `SPECIFICATION.md`.

The project retains the [official NeoForge MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) build structure and [registered payload API](https://docs.neoforged.net/docs/1.21.1/networking/payload/). The Gradle wrapper license is in `gradle/LICENSE`; the mod metadata remains All Rights Reserved.
