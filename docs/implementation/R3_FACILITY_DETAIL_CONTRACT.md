# R3 Facility Detail Interaction Contract

## Information hierarchy

Hometown uses three deliberate levels of information:

1. **Town Ledger — portable at-a-glance health.** Town-wide headline facts, trends, warnings, and short summaries.
2. **Town Administration — strategic town overview.** Progression, registered facilities, civic systems, and town-wide operations.
3. **Registered facility sign — building-level detail.** Current qualification, diagnostics, job/role, production or consumption, and later facility-specific controls.

New systems should place information at the narrowest useful level instead of continually expanding the portable Ledger.

## Interaction

- Present the linked Town Ledger to a civic sign to register/revalidate that facility, preserving the existing gesture.
- Normally use an already registered civic sign face to open its read-only facility detail screen.
- Registration truth and facility detail snapshots are server-authoritative.
- An unregistered sign remains a normal vanilla sign.
- Only the exact persisted sign face (`FRONT` or `BACK`) is the registered civic marker face.
- A damaged facility remains permanently established but the detail screen reports current `Unavailable` state and diagnostics.

## 0.10.10 — facility-detail foundation

The generic facility-detail payload and screen were introduced with Storage as the first detailed implementation.

Storage reports:

- established/current active state;
- persisted sign position;
- recognized Storage block count;
- usable floor positions;
- dark floor positions;
- current qualification failure when unavailable;
- Daily Meal as an explicitly pending operation;
- its role as the primary future town food reserve.

## 0.10.11 — Animal Farm detail and long-screen behavior

Animal Farm now reports its full current structural qualification:

- farm-building qualification;
- recognized storage and loom requirements;
- paddock building connections;
- connected fence/gate count;
- enclosure state;
- exact current diagnostic when unavailable;
- livestock production and animal tracking as explicitly pending operations;
- its role as livestock production and animal-based town supply.

Facility detail remains observational. It does not mutate inventories, progression, or facility state.

Facility-detail content is scrollable when the player's GUI scale leaves too little vertical room. No server data is dropped merely because a facility has more detail than fits on one screen. This also ensures Storage can expose its Daily Meal placeholder and role instead of replacing lower rows with a generic clipping message.
