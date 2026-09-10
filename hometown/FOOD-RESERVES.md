# Hometown v0.4a — Food Reserves and Food Security

Version `0.4.0-alpha.1`; Minecraft 1.21.1, NeoForge 21.1.250, Java 21. Install the same version on client and server (Ledger network protocol 5). No SavedData migration is needed.

## Use

Open Development > Food. The left page shows Food Security, reserve days, a reserve bar, residents, daily need, and stored nutrition. The right shows food-containing inventories, qualifying stacks, unique item types, and nutrition. Close and reopen after changing food or population. Existing explicit resident-page requests also regenerate the snapshot; navigating Development subpages does not trigger another scan.

The server config now includes `nutritionPerResidentPerDay`, default 20, allowed range 1–1,000,000. This is a planning estimate per Minecraft day, not actual villager consumption. `/hometown debug food <uuid>` exposes the same snapshot for operators; use debug list to find the UUID. Existing room and Housing debug commands remain available.

## Storage and food rules

The block tag `hometown:food_storage` defaults to chest, trapped_chest, and barrel. Extend `data/hometown/tags/block/food_storage.json` with `replace: false` in a datapack. Only tagged, already-loaded block entities exposing Minecraft's standard `Container` interface are read. This supports standard modded Container implementations through tags without item/material lists. Capability-only, virtual, or otherwise unsupported tagged storage returns incomplete data; arbitrary machines are not scanned by default.

The empty item tag `hometown:food_excluded` is supplied at `data/hometown/tags/item/food_excluded.json`. Tagged items are excluded even when edible. Other stacks qualify through their current `DataComponents.FOOD` component and positive nutrition. Zero-nutrition items contribute neither nutrition nor stack/type/store counts. Saturation is ignored. Standard component-bearing modded foods need no compatibility list.

Nutrition is `nutrition per item * count`, accumulated with checked long arithmetic. Food stacks count slots, not items. Unique types use item identity, so different component variants of one item remain one type. Food containers count recognized inventories containing at least one qualifying stack, not empty or non-food-only stores.

Vanilla double chests are read as two local physical inventories, never through the combined wrapper. Each local slot is read once. Matching in-town halves share a logical inventory key for the container count, including when only one half contains food. A pair straddling the town boundary contributes only its inside half; no outside inventory is read. Repeated references to the same Container object are also deduplicated.

The scanner never opens inventories, removes items, follows nested backpacks/shulkers, or reads player, villager, item-entity, or minecart inventories. Untagged furnaces, smokers, blast furnaces, hoppers, and machines are ignored. Ungenerated loot tables are not unpacked: such tagged stores make the snapshot incomplete until their inventory becomes available through normal gameplay.

## Calculations

Food reuses the existing SettlementStats population (living vanilla villagers, adults and children). Its snapshot is independent of Housing and is not persisted.

`daily requirement = population * nutritionPerResidentPerDay`

`reserve days = total nutrition / daily requirement` using floating-point division.

| State | Reserves |
| --- | --- |
| NO_RESIDENTS | Population is zero; days and bar unavailable |
| EMPTY | Zero days |
| CRITICAL | Greater than zero and less than 1 day |
| LOW | At least 1 and less than 3 days |
| STABLE | At least 3 and less than 7 days |
| STOCKED | At least 7 days |
| SCAN_INCOMPLETE | Required area/inventory data unavailable or a safety limit/error |

FoodRules isolates the ordered thresholds for future tuning without changing scanner or renderer structure. The daily requirement is configurable now; thresholds retain 1/3/7-day defaults. The server calculates the visual bar against the rules' stocked threshold and caps it at 100%. Text shows actual days to one decimal without a seven-day cap. Example: 1,060 nutrition / 200 daily need = 5.3 days. Twelve days still displays 12.0 Days with a full bar.

## Read-only behavior and limits

The scanner visits block-entity positions in the same settlement chunk rectangle and filters by the existing horizontal/vertical bounds. It does not sweep all block positions, create block entities, load chunks, or request tickets. Pending tagged block entities without available inventory data are incomplete. The existing population availability flag is respected, and every relevant chunk is also checked through getChunkNow.

Per-request caps are 32,768 inspected block-entity positions, 4,096 tagged stores, and 131,072 inventory slots. Missing/removed/unsupported stores, unresolved loot, exceptions, or arithmetic overflow return SCAN_INCOMPLETE. Incomplete snapshots have no reserve duration or bar; the UI hides numerical diagnostics so internal unavailable placeholders cannot appear as EMPTY or zero stores. Long totals and daily requirements avoid ordinary int overflow.

Food scans only on explicit Ledger snapshot requests or the operator command. No food consumption, production prediction, crop/farm scanning, variety score, gameplay consequences, background tasks, or persistence were added. Housing/Privacy aggregation, room detection, founding, persistence, population/bed scanning, and Housing rendering are unchanged from v0.3c.

## Verification

All 105 tests pass: the 86 existing tests plus 19 Food tests. Coverage includes exact stack math, add/remove/move refreshes, non-food and untagged storage, positive standard food components, exclusions, default tag resources, unique types, empty stores, chest/barrel/trapped chest, double-chest local reads and boundary behavior, duplicate inventory identity, unloaded and pending data, unresolved loot, slot limits, exceptions, large long totals/overflow, thresholds, population changes, zero residents, custom daily need, and packet round trips preserving Housing.

Existing recording-screen tests now exercise Food at GUI-scale-equivalent sizes 2, 3, and 4 and the minimum viewport. They verify the Food spread, remaining placeholders, 5.3/12.0-day text, full-bar clamping, unavailable bars, page bounds, subnavigation, and preserved Housing/Overview/Residents/History behavior. The renderer retains integer positioning and the existing blur/pose behavior.

39 main Java files compile without errors; only the two pre-existing client annotation deprecation warnings remain. Standard offline Gradle was attempted and again failed before mod compilation in Minecraft artifact extraction with the environment's Windows AccessDeniedException. The JAR uses the same direct JavaCompiler and resource-packaging route as prior working versions. Packaging validates metadata, translations, JSON, archive integrity, and dependency exclusions.

Tests use real Minecraft food components, item stacks, and vanilla container block entities backed by mocked loaded-chunk storage. Tag membership is simulated in scanner fixtures and tag defaults are checked separately. The generic food-component test is synthetic, not an installed external food-mod test. Live v0.4a Minecraft, datapack reload, and installed-mod verification remain pending; see ACCEPTANCE.md. The user confirmed earlier Housing/Privacy behavior in-game.
