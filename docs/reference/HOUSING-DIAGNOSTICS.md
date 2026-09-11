# Hometown v0.3b — Housing Diagnostics and Ledger Integration

Version `0.3.0-alpha.2`, Minecraft 1.21.1, NeoForge 21.1.250, Java 21. Install the matching JAR on client and server; the extended Ledger packet uses protocol 3. Existing towns and Ledgers need no data migration.

## Use

Open the Town Ledger's Development tab. Housing now occupies the left page, with its capacity state, native pixel bar, population, total/enclosed/unsealed beds, unique/private/shared rooms, shared beds, unhoused residents, and spare capacity. The five remaining categories stay Not yet tracked on the right. Long text uses the existing truncation and hover details. Zero residents show Capacity: N/A without a bar. Incomplete scans hide numerical Housing diagnostics and explain that world data is unavailable or a safety limit was reached.

Close and reopen to refresh after construction or population changes. Existing explicit resident page requests also regenerate the server snapshot. There is no timed or background refresh. `/hometown debug room` is unchanged. Operators can also use `/hometown debug housing <uuid>`; obtain UUIDs from the existing debug list command. The latter uses the same scanners and formulas as the Ledger.

## Data and formulas

HousingScanner consumes the existing live SettlementStats population, including adults and children. The shared HOME POI query returns intact vanilla bed heads within the same settlement and vertical bounds used by the existing bed count. The query now exposes positions and completeness as well as the original count API; it still rejects stale, broken, and non-vanilla beds.

One unchanged v0.3a RoomDetector session evaluates beds. Successful room results are reused across their members. Only qualifying town POI beds contribute capacity, even if a detected room extends beyond the town. Room privacy classification uses the full detected room bed count; sharedBeds counts the town's qualifying beds in those shared rooms. Unique rooms use the detector's representative position. Aggregation collects all successful results before classifying beds, so a successful later seed can resolve an earlier seed's distance-limit failure.

For a complete scan:

- unsealedBeds = totalBeds - enclosedBeds.
- unhousedResidents = max(0, population - enclosedBeds).
- spareHousingCapacity = max(0, enclosedBeds - population).
- Capacity rounds enclosedBeds / population * 100 to the nearest integer and clamps at 100. It is absent with zero population.
- NO_RESIDENTS means zero population; OVERCROWDED means population exceeds enclosed beds; otherwise SUFFICIENT. Shared rooms alone do not cause overcrowding.

HousingSnapshot is an immutable derived DTO. It is included in the server-produced TownLedgerSnapshot and its bounded packet codec. It never enters SavedData or item components. Capacity is derived from authoritative counts; the client performs no world queries or enclosure scans. It only formats the DTO and draws its bar.

## Incomplete scans and limits

Settlement chunk incompleteness skips Housing detection. Missing bed-half chunks at the edge, unavailable room chunks, exhausted session work, and aggregation limits produce SCAN_INCOMPLETE. Unknown beds are tracked separately from unsealed beds: total = enclosed + unsealed + unknown for the observed set. Partial-set counts are not displayed as complete totals. Capacity is absent, and callers must check scanComplete before using unhoused/spare counts (their incomplete placeholders are zero).

Room volume/distance/no-start/outdoor failures count as unsealed diagnostic beds, as requested; they are not quality judgments. CHUNK_UNAVAILABLE and SCAN_BUDGET_EXCEEDED remain unknown. A generic incomplete message also covers exceptions or mismatched live query data without replacing the stored town identity.

The original detector limits remain 4,096 traversable cells per room, horizontal displacement 32, vertical displacement 16, and 65,536 inspected cells per session. Housing additionally caps new detector attempts at 32 and relevant beds at 4,096. Successful room membership can cover many beds with one attempt. These constants are in HousingScanner; exceeding a cap yields incomplete data instead of guessed capacity. This also bounds repeated failed-region work without changing the tested room detector. The existing Ledger request cooldown remains active.

All world queries are synchronous and loaded-only. No chunk tickets, force loading, persistent snapshots, furniture queries, comfort rules, shared-room penalties, gameplay effects, or other Development mechanics were added. The room detector's documented whole-block collision approximation is unchanged.

## Verification

76 tests pass: all 61 prior regressions plus 15 Housing tests. Covered cases include the 10/9 baseline, bed addition/removal, window break/repair, stable doors, shared/private rooms, outdoor beds, population changes, zero residents, surplus/rounding, unavailable chunks, bed halves crossing unloaded town edges, bounded aggregation, later successful room reuse, town-bound bed counting, real POI integration, and packet round trips.

Existing screen tests now check populated, overcrowded, zero-resident, and incomplete Housing layouts at scale-equivalent GUI sizes 2, 3, and 4 and the minimum supported viewport. They verify page containment, five retained placeholders, no pose transforms, and one background blur before foreground drawing. They use recording graphics, not a real GPU/font screenshot.

34 main sources compile without errors, with only the two existing client annotation warnings. Standard offline Gradle was attempted and again failed in Minecraft artifact extraction with the environment's Windows AccessDeniedException, before mod compilation. The delivered JAR uses the same direct JavaCompiler and resource-packaging route as the previously working versions. Packaging checks version metadata, JSON, translations, archive integrity, and dependency exclusions.

The user confirmed v0.3a enclosure behavior in-game. This milestone's live Minecraft acceptance checks remain pending; automated fixture tests are not a server performance benchmark. See ACCEPTANCE.md.
