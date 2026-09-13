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

## First implementation slice — 0.10.10

The generic facility-detail payload and screen are introduced with Storage as the first detailed implementation.

Storage currently reports:

- established/current active state;
- persisted sign position;
- recognized Storage block count;
- usable floor positions;
- dark floor positions;
- current qualification failure when unavailable;
- Daily Meal as an explicitly pending operation;
- its role as the primary future town food reserve.

Town Hall and Animal Farm signs use the same generic screen foundation with current live validity and role notes. Animal Farm receives its full building-specific detail slice next.

Facility screens are observational in this slice. They do not mutate inventories, progression, or facility state.
