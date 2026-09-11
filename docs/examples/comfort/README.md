# Optional Comfort compatibility sample

Hometown Comfort remains standalone and does not require a furniture mod. Compatibility is explicit: datapacks add blocks to the existing Comfort tags, and may add a small state predicate only when a tagged block should qualify in a specific state.

The examples below are documentation only. They are intentionally not active runtime data, so the fictional `example:furniture` namespace never becomes a required dependency.

## Add an optional chair to Seating

Place a datapack file at `data/hometown/tags/block/comfort/seating.json`:

```json
{
  "replace": false,
  "values": [
    { "id": "example:furniture_chair", "required": false }
  ]
}
```

Then enable the Seating category in Hometown's server configuration. Enabling Seating adds its configured weight to the denominator even when its tag is empty; Hometown never auto-disables an enabled category because a compatibility block is absent.

## Optional state predicate

If a tagged compatibility block should count only in one state, add a separate version-1 rule under `data/<your_namespace>/hometown/comfort_rules/`, for example:

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

Tag membership is still required. Properties are exact string-value equalities and are validated against the block's state definition. At most one effective rule may exist for a block/category pair. An invalid custom-rule reload is rejected atomically and Hometown keeps the last valid rule set.

Do not infer chairs from stairs, tables from slabs/fences, or compatibility from mod names. Add only explicit tag membership and, where necessary, explicit predicates.
