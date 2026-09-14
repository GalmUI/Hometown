# Hometown 0.12.0 — Animal Farm Livestock Production

## Purpose

0.12.0 turns the registered Animal Farm from a structural facility into the first producer feeding the town economy. The player still owns the physical animals, building and inventories; Hometown observes the registered paddock, applies explicit herd-management policy, and converts only qualifying surplus adult livestock into deterministic farm output.

## Player-facing loop

1. Establish and keep a registered Animal Farm operational.
2. Keep cows and/or pigs inside its attached enclosed paddock.
3. Configure each species from the registered Animal Farm sign:
   - **Breeding pairs** — protected adult reserve; one pair means two adults.
   - **Cull above** — adult herd size above which surplus becomes eligible for production.
4. Breed animals normally.
5. Late in the Minecraft workday, Hometown runs one bounded livestock cycle.
6. Output is placed in recognized storage inside the Animal Farm building.
7. The player may manually move it, hopper it, or later automate it into registered Town Storage for the Daily Meal.

## Default policy

For both cows and pigs:

- breeding pairs: **1**
- protected adult reserve: **2**
- cull above: **4 adults**

`cullAbove` may never be configured below `breedingPairs * 2`.

Changing breeding pairs upward automatically raises the cull threshold when necessary. Policy is persisted per Hometown and per species.

## Supported livestock in 0.12.0

- Cow
- Pig

Babies are counted separately and are never culled.

Named adult animals are counted in the herd but are never selected for culling. They can therefore keep a herd above its configured threshold if all otherwise-surplus adults are named.

## Culling semantics

For a species:

`raw surplus = max(0, adults - cullAbove)`

Only unnamed adults are cull candidates. A work cycle processes at most **4 animals per species**. Culling therefore moves the herd toward the configured threshold without collapsing it to the breeding reserve, and an extremely oversized herd may require multiple Minecraft days to return to target.

The breeding-pair setting is a hard policy invariant: `cullAbove` cannot fall below the protected reserve.

## Production timing

The Animal Farm work cycle becomes due at Minecraft day time **10000** (late workday), before the Daily Meal at 12000.

Terminal outcomes are once per Minecraft day:

- `PROCESSED`
- `NO_SURPLUS`

Retryable, non-mutating outcomes may retry later the same day on a bounded 200-tick cadence:

- `FACILITY_UNAVAILABLE`
- `LIVESTOCK_UNAVAILABLE`
- `OUTPUT_STORAGE_FULL`

Clock rewinds may replace a retryable result because those results guarantee zero culling and zero output mutation. A terminal cycle at an equal/newer day still blocks reprocessing.

## Deterministic outputs

0.12.0 intentionally does not invoke vanilla death loot tables. Hometown uses fixed first-pass yields:

- one culled cow: **2 raw beef + 1 leather**
- one culled pig: **2 raw porkchops**

The animal is removed with no vanilla random drops after Hometown has successfully committed the deterministic output.

## Inventory safety

Production uses only recognized storage blocks inside the currently validated Animal Farm room.

Before any animal is removed, Hometown:

1. collects loaded recognized storage containers;
2. skips unresolved loot containers without opening/generating them;
3. plans the complete output insertion in memory;
4. verifies that every output item fits;
5. revalidates every inventory slot that will change;
6. commits all planned output;
7. only then removes the selected animals.

If the full output does not fit, outcome is `OUTPUT_STORAGE_FULL` and **no animal or inventory is mutated**.

## Paddock observation and performance

The existing Animal Farm qualifier remains authoritative for facility validity. Livestock occupancy derives an enclosed paddock footprint from the same room attachment rules, fence/gate semantic and hard limits.

Observation remains loaded-only:

- no chunk tickets;
- no forced chunk loading;
- paddock geometry fails closed if required chunks are unavailable;
- at most 512 recognized livestock entities are accepted in one pass;
- existing paddock barrier and enclosure limits remain in force;
- scheduled work is smoothed to at most one town attempt per server tick.

## Facility UI

Normal right-click of the registered Animal Farm sign now shows:

- current cow and pig adult/young counts;
- breeding-pair reserve;
- cull threshold;
- currently cullable surplus;
- named protected adults when present;
- previous production-cycle result;
- next-cycle timing;
- produced items for the latest successful cycle.

The bottom of the Animal Farm detail screen exposes `- / +` controls for each species' breeding pairs and cull threshold. Client packets only request bounded +/-1 changes. The server revalidates the registered sign, town, dimension and 8-block player distance before mutating persisted policy, then returns a fresh authoritative facility snapshot.

## Persistence

Animal Farm operations use separate SavedData:

`hometown_animal_farm_operations`

This keeps existing Hometown civic SavedData version 3 untouched. Persisted state includes:

- cow policy;
- pig policy;
- latest scheduled livestock-cycle result.

Current livestock counts and current facility validity remain derived from loaded world state and are never persisted as authoritative truth.

## Initial live acceptance fixture

The existing playtest Animal Farm with **2 cows + 2 pigs** should initially show both species as breeding stock with default policy `1 pair / cull above 4` and produce nothing.

Suggested live progression:

1. verify 2 cows + 2 pigs are detected;
2. verify default controls and persistence across save/reload;
3. breed one species and confirm babies never count as cullable surplus;
4. allow adults to exceed 4;
5. cross day time 10000 and confirm only surplus adults are removed;
6. verify fixed output appears in farm storage;
7. verify full storage causes no culling;
8. verify a named surplus adult is protected;
9. verify save/reload does not double-process the same terminal workday cycle.
