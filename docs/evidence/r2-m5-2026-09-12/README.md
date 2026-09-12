# R2 M5 owner validation — 2026-09-12

Milestone: **Revision 2 M5 — Prosperity + History**

Owner-validation playtest version: **0.8.0**

Final M5 polish version: **0.8.1**

Versioned implementation head before the original owner-evidence commit: `1200de1a70dd79f735da5cc606b68653b244b226`

## Automated gate

GitHub Actions run **34699534596** on the exact versioned `0.8.0` owner-playtest head completed successfully. Both the normal Gradle test task and normal mod build passed.

After owner validation, the two recorded Prosperity presentation findings were corrected in `0.8.1`. GitHub Actions run **34723647758** on exact code head `ee51c523dd3bd5c80e118734e42fed17fffbfd48` passed both the normal Gradle test task and normal mod build. Focused regression coverage verifies default and custom effective-weight/contribution formatting.

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

## Post-validation Prosperity UI polish

The two cosmetic findings from `0.8.0` were resolved in `0.8.1` without changing Prosperity math, observation authority, History state, or persistence:

- The left-page authoritative-input explanation now wraps instead of being forcibly ellipsized.
- The right-page contribution rows now present the component's effective share of enabled Prosperity weight and its actual contribution to the final 0–100 Development Index. With default weights this renders in the player-facing form `100% × 20% = 20.0 pts` rather than the internal weighted numerator `100% × 20 = 2000.0`.
- Custom weights are normalized against total enabled weight for display, matching the evaluator's actual formula rather than assuming the default five equal weights.

M5 is therefore complete with its recorded functional validation intact and its known Prosperity presentation findings closed before R2 M6 begins.
