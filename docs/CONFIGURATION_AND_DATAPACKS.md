# Hometown R2 configuration and datapacks

This document describes the supported Revision 2 server configuration and datapack extension surfaces. Hometown remains standalone: no furniture, farming, economy, or NPC mod is required.

## Server configuration

Hometown registers a NeoForge **SERVER** config. Values affect observations only; Revision 2 does not add gameplay rewards, penalties, town-radius growth, automatic immigration, food consumption, trade modifiers, or AI changes.

### Founding and protected baseline

| Key | Default | Range / meaning |
| --- | ---: | --- |
| `settlementRadius` | 64 | 16–256; stored on newly founded settlements only |
| `verticalScanRadius` | 32 | 8–128 |
| `minimumVillagers` | 2 | 0–100 |
| `minimumBeds` | 2 | 0–100 |
| `preventSettlementOverlap` | true | Founding validation |
| `consumeFoundingBook` | true | Founding behavior |
| `nutritionPerResidentPerDay` | 20 | 1–1,000,000 diagnostic nutrition points; scanning never consumes food |

### Food

`food.variety.enabled=true`

`food.variety.nutritionPerResidentPerGroup=2` (1–100)

`food.variety.groupPriority=[prepared_meals, protein, vegetables, fruit, grains]`; the configured list must remain a permutation of all five IDs.

Each `food.variety.groups.<group>.enabled` defaults to `true` for `prepared_meals`, `protein`, `vegetables`, `fruit`, and `grains`.

`food.growing.enabled=true`.

### Safety and shared scan ceilings

| Key | Default | Range |
| --- | ---: | ---: |
| `safety.enabled` | true | boolean |
| `safety.minimumResidentialBlockLight` | 1 | 0–15 |
| `safety.maxEntityInspections` | 4096 | 128–65536 |
| `scan.maxNewEntityInspections` | 4096 | 128–65536 |
| `scan.maxNewBlockInspections` | 262144 | 1024–1048576 |
| `ledger.requestCooldownTicks` | 40 | 1–1200 |

The shared ceilings apply only to the newer R2 observation work. They do not retroactively redefine the protected Housing/Food budgets. Hometown never force-loads chunks to finish an observation.

### Comfort

`comfort.enabled=true`, `comfort.maxRooms=512` (1–4096), and `comfort.maxCellsPerRoom=65536` (64–262144).

Category defaults:

| Category | Enabled | Weight |
| --- | --- | ---: |
| storage | true | 10 |
| lighting | true | 15 |
| decor | true | 10 |
| books | true | 10 |
| plants | true | 10 |
| amenities | true | 20 |
| seating | false | 15 |
| tables | false | 10 |

Each category uses `comfort.categories.<category>.enabled` and `.weight` (0–100). An enabled category with an empty compatibility tag still participates in the denominator; Hometown does not silently disable it.

### Commerce

`commerce.enabled=true`. Revision 2 intentionally has no configurable workstation, trade, wage, or profession-band simulation.

### Prosperity

`prosperity.enabled=true`.

Default component weights are all 20:

- `prosperity.weights.housingSupply`
- `prosperity.weights.foodReserves`
- `prosperity.weights.residentialLighting`
- `prosperity.weights.residentialComfort`
- `prosperity.weights.employment`

Each weight accepts 0–100. A zero weight explicitly excludes that component; Hometown does not silently reweight because a positive-weight source is missing.

Default band thresholds are `prosperity.bands.developing=25`, `established=50`, and `flourishing=75`. Effective settings must satisfy `0 < developing < established < flourishing <= 100`.

### History

| Key | Default | Range / meaning |
| --- | ---: | --- |
| `history.enabled` | true | controls derived events; founding remains protected |
| `history.maxEventsPerTown` | 256 | 8–4096 |
| `history.confirmationTicks` | 200 | 200–24000 |
| `history.coalesceTicks` | 1200 | 0–24000; 0 disables coalescing |

History candidates are memory-only. Durable confirmed events/baselines persist; debug and cached page navigation do not advance History.

## Datapack tags

Hometown uses the Minecraft 1.21.1 singular registry tag directories.

### Comfort block tags

Add explicit compatibility blocks to:

`data/hometown/tags/block/comfort/<category>.json`

where `<category>` is one of the eight Comfort IDs above. A documentation-only optional furniture example is in `docs/examples/comfort/README.md`.

### Food group item tags

Food Variety uses:

`data/hometown/tags/item/food_groups/<group>.json`

for the five exact group IDs `prepared_meals`, `protein`, `vegetables`, `fruit`, and `grains`. A food can still contribute to protected Reserves without belonging to a Variety group. If an item matches multiple group tags, Hometown applies the configured priority and counts that nutrition toward only one group.

### Safety entity-type tags

Safety uses:

- `data/hometown/tags/entity_type/safety/threats.json`
- `data/hometown/tags/entity_type/safety/threats_excluded.json`
- `data/hometown/tags/entity_type/safety/protectors.json`
- `data/hometown/tags/entity_type/safety/protectors_excluded.json`

Classification is explicit tag membership; Hometown does not infer hostility from anger/target state or from a mod/entity name.

## Small version-1 rule resources

### Comfort state predicates

A tagged Comfort block may additionally require an exact block-state predicate under:

`data/<namespace>/hometown/comfort_rules/<name>.json`

Example:

```json
{
  "schemaVersion": 1,
  "block": "example:furniture_chair",
  "category": "seating",
  "properties": {
    "occupied": "false"
  }
}
```

Tag membership remains required. Properties are exact string equalities validated against the referenced block state. Ambiguous duplicate block/category rules are invalid. Invalid custom rule reloads do not replace the last valid installed custom set.

### Custom crops

Crop definitions live under:

`data/<namespace>/hometown/crops/<name>.json`

Core wheat is equivalent to:

```json
{
  "schemaVersion": 1,
  "block": "minecraft:wheat",
  "family": "hometown:wheat",
  "anchorProperties": {},
  "matureProperties": { "age": "7" }
}
```

`anchorProperties` defaults to an empty conjunction for a one-block crop. It can identify the one counted anchor of a supported multiblock crop. If `matureProperties` is omitted, the crop can still count as growing but maturity is explicitly unassessed rather than assumed immature. Duplicate effective definitions, invalid registry IDs/properties/values, and unsupported schema versions are rejected.

## Reload behavior and compatibility boundary

Comfort predicates and crop rules participate in server resource reload. A successful `/reload` installs a validated new set atomically. Invalid custom definitions retain the last valid custom set instead of mixing revisions.

Compatibility is explicit through tags and the two small rule formats above. Hometown intentionally has no fuzzy block-name matching, mod-name branches, scripts, generic integration engine, or required external mod dependency in Revision 2.

## Performance/debug validation

M6 adds the operator command `/hometown debug performance [uuid]`. It reads the player's most recent normal Ledger observation; the command itself performs **no world scan**. Open the Ledger normally, close it, then run the command within the cached session window to report the fresh observation's elapsed time and existing work counters. This diagnostic data is memory-only and is never persisted as town truth.
