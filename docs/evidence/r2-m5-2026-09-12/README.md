# R2 M5 owner validation — 2026-09-12

Milestone: **Revision 2 M5 — Prosperity + History**

Playtest version: **0.8.0**

Versioned implementation head before this docs-only evidence commit: `1200de1a70dd79f735da5cc606b68653b244b226`

## Automated gate

GitHub Actions run **34699534596** on the exact versioned `0.8.0` head completed successfully. Both the normal Gradle test task and normal mod build passed.

The M5 automated coverage includes the pure five-input Prosperity evaluator, authoritative/incomplete component handling, exact band boundaries, weight and provenance behavior, v1→v2 SavedData migration, typed durable History events, confirmation windows, domain-local candidates, comparison revisions, Population/Food coalescing, Prosperity high-water behavior, retained History paging, networking, and M5 Ledger client behavior.

## Owner / in-game validation

Owner validation used the existing real settlement **Oured** after making a world backup before first launch with `0.8.0`.

### Save migration and History

- Existing v1 Hometown SavedData loaded successfully under the v2 M5 schema.
- Existing settlement identity remained intact.
- The former founding presentation was migrated into a durable History event: `Day 0 — Oured was founded by GalMUI.`
- Migration did not fabricate retrospective Population, Housing, Food, or Prosperity events for pre-M5 observations.
- A confirmed Population event appeared only after the changed value survived the confirmation window and a later fresh Ledger observation: `Day 16 — Population changed from 4 to 3.`
- The confirmed Population event survived save/exit/reload.
- Reopening after restart did not duplicate the confirmed event.
- Breaking enough enclosed beds produced the expected Housing shortage candidate/confirmed History behavior after the same confirmation cycle.
- Restoring the beds produced the corresponding Housing shortage resolved event.
- Killing/spawning villagers produced the same delayed-confirmation Population behavior in normal gameplay.

### Prosperity

The first owner-validated Prosperity snapshot showed approximately:

- Housing Supply: 100%
- Food Reserves: 100%
- Residential Lighting: 100%
- Residential Comfort: ~24%
- Employment: ~67%
- Development Index: **78% — FLOURISHING**

With default equal weights of 20, the displayed Development Index agreed with the five authoritative source values.

After Employment rose to 100%, the same town showed approximately:

- Housing Supply: 100%
- Food Reserves: 100%
- Residential Lighting: 100%
- Residential Comfort: ~24%
- Employment: 100%
- Development Index: **85% — FLOURISHING**

This verified that Commerce changes feed through the pure Prosperity calculation while remaining inside the same Prosperity band when appropriate.

### Snapshot reuse / no hidden rescans

While moving among Overview, Development/Prosperity, and History, the displayed observation age continued to increase rather than resetting. This supports the intended behavior that tab/subpage navigation reuses the opened Ledger generation instead of triggering hidden world scans.

## Owner-validation result

**PASS — R2 M5 functional owner validation complete.**

The following visual issues are recorded as cosmetic follow-up rather than functional blockers:

- The Prosperity explanatory sentence can be clipped/ellipsized at the bottom of the left page.
- Player-facing weighted-contribution notation currently renders values such as `100% × 20 = 2000.0`; the Development Index math is correct, but a clearer presentation would be `100% × 20% = 20.0 pts` (and equivalent values for the other components).

These presentation issues do not affect the underlying Development Index, History confirmation/persistence, save migration, or observation authority behavior.
