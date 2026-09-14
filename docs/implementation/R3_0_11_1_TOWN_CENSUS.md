# Hometown 0.11.1 — Shared Town Census

## Purpose

0.11.1 stabilizes town population facts across the Ledger, facility screens, Administration, and destructive town operations such as Daily Meal.

The rule is now:

> World scans produce observations. Hometown systems consume the last trusted complete Town Census.

A partial loaded-chunk observation never replaces a previously trusted complete census.

## Census cadence and chunk policy

- Hometown attempts a loaded-only census for each town every **6000 server ticks** (about five real-time minutes at 20 TPS).
- Work is smoothed to at most one town census attempt per server tick.
- A town without a scheduled attempt is eligible immediately after server startup.
- Hometown never requests or force-loads chunks for a census.
- The existing settlement resident scanner remains the sole resident observation owner.
- Ledger collection may opportunistically commit a complete resident observation it already paid for; it does not launch a second census scan.

## Trusted census

A census is trusted only when the resident observation reports `COMPLETE`, meaning all settlement chunks considered by the resident scan are currently loaded.

The durable census contains:

- population;
- beds;
- employed resident count;
- profession diversity;
- bounded resident presentation summaries;
- observed server game time.

If a later observation is partial or unavailable, Hometown retains the previous trusted complete census instead of lowering the town population to a partial count.

## Freshness

- **Fresh:** up to 12000 ticks / about 10 minutes old.
- **Stale but operationally usable:** more than 12000 and up to 36000 ticks / about 30 minutes old.
- **Expired for destructive operations:** older than 36000 ticks.

Expired census data may still be shown to the player as stale last-known information, but Daily Meal must wait for a newer complete census before consuming inventory.

## Daily Meal integration

Daily Meal no longer performs its own population scan at sunset.

- It reads the trusted Town Census.
- If no usable census exists, the meal remains due and reports **Waiting for census**.
- Waiting for census consumes no food and is not committed as that day's completed meal.
- Once a usable census becomes available later the same day, the due meal may proceed.
- A same-day `POPULATION_UNAVAILABLE` state written by 0.11.0 is explicitly retryable because that state consumed no inventory.
- All existing Daily Meal source, unresolved-loot, loaded-only, deterministic-roll, and at-most-once real-consumption rules remain in force.

## UI behavior

- The Town Ledger's resident observation is reconciled through the same trusted census, so a partial live pass cannot replace a known complete population.
- The Storage facility detail screen shows Census population and Census freshness/status alongside Daily Meal operations.
- Town Administration uses the census-aware Daily Meal status and reports `Waiting for census` when a due meal lacks trusted population data.

## Persistence

Town Census state is stored independently in `hometown_town_census` SavedData version 1. Civic SavedData remains version 3 and Daily Meal SavedData remains version 1.

This separation keeps transient/operational census evolution independent from permanent civic progression and settlement identity.
