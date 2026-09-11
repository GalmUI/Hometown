# Food scan robustness and diagnostics

Version `0.4.0-alpha.2`, Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21. Use matching client/server JARs; the diagnostics payload requires protocol 6. Town data remains compatible.

## Diagnosis

The previous scanner returned an unavailable snapshot immediately when SettlementStats was not COMPLETE. It also returned unavailable on the first unloaded chunk, missing/pending/unsupported storage entity, ungenerated loot table, or exceeded limit. These exits discarded otherwise readable Food data and shared one generic error label. They explain why a compact village's surrounding radius could suppress its Food page, but the supplied screenshot cannot identify which exit occurred in that specific run.

The fix exposes evidence instead of guessing: dedicated status, reasons, and counters travel with every Food snapshot. Unopened loot has its own LOOT_NOT_GENERATED reason. Storage exceptions and overflow use INTERNAL_ERROR; unavailable storage, unavailable chunks, population incompleteness, and safety limits remain distinct. When several conditions occur, all are retained; the UI shows the primary reason and the operator report shows every reason.

## What changed

- COMPLETE means all required Food and population observations were available. Only complete snapshots carry a Food Security classification: NO_RESIDENTS, EMPTY, CRITICAL, LOW, STABLE, or STOCKED.
- PARTIAL means useful observations were collected but some data remains unknown. The scanner retains known stores, stacks, unique types, and nutrition, including successful stack reads before a later error or slot limit. Partial snapshots never carry EMPTY or another complete security classification.
- UNAVAILABLE means no meaningful Food observation could be obtained. Examples include all chunks unavailable, all discovered stores inaccessible, or a limit before any useful observation. A safely inspected area with no recognized storage can support a partial zero-food observation when other chunks are unloaded.

Loaded chunks are inspected even if the shared settlement/population scan is partial. Missing chunks are counted and skipped without loading them. Collection-limit exits still allow collected stores to be read. Individual unavailable stores and inventory exceptions do not erase earlier values or prevent other safe inventories from being inspected.

The existing food tags, positive-nutrition component math, exclusions, local double-chest slot reads, inventory identity deduplication, and no-loot-generation behavior remain intact. Two physical chest halves still count once as a logical food-containing inventory, with each local slot read once. Duplicate inventories skipped counts repeated Container object references; reading the second distinct physical chest half is not a duplicate slot scan.

Housing, Privacy, room detection, founding, persistence, population/bed scanners, Development navigation, and non-Food rendering were compared against the previous source archive and preserved.

## Known reserve duration and population

Partial Food uses known nutrition divided by the daily requirement for the reported population. The UI says “At least X.X Days,” with partial duration rounded down to avoid overstating the minimum at display precision. The right page is explicitly labeled Known Town Stores.

If population itself is incomplete, the row says “For X known residents,” and the debug report states that the lower bound applies only to that reported population. More residents may exist outside loaded areas, so this is not a guaranteed lower bound for an unknown total town population. This qualification is necessary to avoid a misleading promise while retaining the existing population scanner. With zero known residents, no reserve duration or bar is calculated.

The UI uses compact status/reason lines. It retains known counts during partial scans and hides numerical reserve claims when unavailable. Bars remain native integer GUI fills; there is no fractional GUI/font scaling or new background scan.

## Operator command

Use `/hometown debug food` while standing inside a Hometown. `/hometown debug food <uuid>` remains supported, including from the console. Permission level 2 is still required.

The report includes town name, scan status, primary/all reasons, considered/loaded/unavailable chunk counts, inspected block entities, found and successfully scanned storage entities, duplicate inventory skips, unavailable storage, ungenerated loot count, slot usage, exact default/current limits, population completeness, known food-containing stores, stack/type/nutrition totals, daily requirement, and known reserve days.

Limits remain 32,768 block-entity inspections, 4,096 collected storage entities, and 131,072 inspected slots. Found-storage count includes a discovered store that could not be queued because the storage limit was already full; successfully scanned means every slot in that inventory was read. A partially read inventory can contribute known food while not incrementing the successfully-scanned counter. Diagnostics expose that distinction.

## Verification and remaining limits

All 119 tests pass. Fourteen new robustness tests cover fully loaded and partially loaded areas, food in loaded/unloaded chunks, empty complete versus empty partial data, all chunks unavailable, block-entity/slot/storage limits before and after meaningful data, failed containers after successful reads, incomplete population, diagnostic serialization, operator permissions, and both command forms. Existing Food math, tags, loot safety, double-chest, Housing/Privacy, and other system regressions pass.

Recording-screen tests include COMPLETE, PARTIAL, and UNAVAILABLE Food at GUI-scale-equivalent sizes 2, 3, and 4 plus the minimum viewport. They verify retained known stores, qualified reserve text, no partial EMPTY label, compact unavailable presentation, page bounds, and existing navigation/render ordering.

43 main Java sources compile without errors, with only the two existing client annotation deprecation warnings. Standard offline Gradle was attempted and again failed during Minecraft artifact extraction with the environment's Windows AccessDeniedException, before mod compilation. The delivered JAR uses the same direct JavaCompiler/resource-packaging route as previous working versions. Packaging validates metadata, JSON/translations, Java 21 classes, archive integrity, and exclusion of Minecraft/test dependencies.

The scanner and UI tests use real Minecraft data types with mocked loaded storage and recording graphics. The photographed village has not been run here with this fix. Its original exact failure remains unconfirmed until the enhanced debug command is run there. See ACCEPTANCE.md for the focused live check.
