# R3 Daily Meal — 0.11.0

## Purpose

0.11.0 is Hometown's first town operation that mutates real player-managed resources. It preserves the established information hierarchy: Ledger for at-a-glance town health, Town Administration for strategic status, and registered facility signs for building-level detail.

## Schedule and requirement

- Daily Meal becomes due once per Minecraft day at sunset (`dayTime % 24000 >= 12000`).
- A town/day nutrition roll is deterministic from the town UUID and Minecraft day, so reloads cannot reroll the meal.
- The roll is 16–24 nutrition per resident.
- Daily requirement = complete observed population × that day's roll.
- If population observation is partial/unavailable, the meal fails closed and no food is consumed.
- The latest processed day is persisted separately in `hometown_daily_meals`, preventing a second meal for the same day after save/reload.

## Food source policy

- Before Storage is established, Daily Meal may draw only from recognized food-storage blocks inside the registered, currently valid Town Hall room.
- After Storage is established, Daily Meal draws only from the registered Storage facility.
- Damaged/unavailable established Storage does **not** fall back to the Town Hall.
- Animal Farms do not create food in 0.11.0.

## Inventory safety

- Facility and room validation are loaded-only; no chunks are requested or force-loaded.
- Only blocks using the existing `hometown:food_storage` semantic are considered.
- Edible items use vanilla food nutrition and respect `hometown:food_excluded`.
- Unresolved vanilla loot containers are never opened, generated, or mutated; they are skipped and reported.
- Candidate removals are planned first and every selected slot is revalidated before any inventory mutation occurs.
- Shortage consumes the readable food that is available and records the unmet target.
- Scan/container/slot work is bounded.

## Persisted result

The latest result records:

- Minecraft day and processing game time;
- source (`TOWN_HALL`, `STORAGE`, or `NONE`);
- outcome (`FED`, `SHORTAGE`, `NO_RESIDENTS`, `POPULATION_UNAVAILABLE`, `NO_TOWN_HALL`, `SOURCE_UNAVAILABLE`);
- population and deterministic per-resident roll;
- required and consumed nutrition;
- known remaining nutrition after the meal when available;
- protected unresolved-loot and unavailable-container counts.

## Presentation

- Town Administration Overview shows a concise last Daily Meal result.
- Storage's registered-sign detail screen shows Daily Meal state, next meal timing, last target/source, known reserve after the last meal, and protected unresolved-loot diagnostics.
- Facility detail scrolling now changes its hint at the bottom instead of continuing to say `Scroll for more` when no lower content remains.
