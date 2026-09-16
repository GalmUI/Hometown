# Hometown documentation

This directory separates current specification authority, implementation tracking, supported configuration/datapack surfaces, historical technical notes, and retained validation evidence.

## Specification authority

- `specs/Hometown_Implementation_Specification_Revision_2.docx` — accepted Revision 2 implementation specification and completed prerequisite authority.
- `specs/Hometown_Progression_Town_Operations_Revision_3.docx` — current Revision 3 progression and town-operations authority.
- `specs/archive/Hometown_Implementation_Specification.docx` — older pre-Revision-2 specification retained for history only.

Revision 1 / older material must not override Revision 2 or Revision 3 requirements.

Owner-approved milestone refinements are recorded in scoped implementation contracts rather than rewriting the source DOCX. Current Revision 3 authority includes:

- `implementation/R3_M1_TOWN_HALL_CONTRACT.md` plus the subsequent M1 implementation contracts — completed R3 M1 foundation and town-operations work through `0.12.0`.
- `implementation/R3_0_13_0_NOTICE_BOARD_FOUNDATION.md` — active R3 M2 Notice Board foundation contract.
- `evidence/r3-m2-2026-09-16/ACCEPTANCE_MATRIX.md` — active automated/live acceptance matrix for `0.13.0`.

## Configuration and datapacks

- `CONFIGURATION_AND_DATAPACKS.md` — supported server configuration, Comfort/Food/Safety tags, Comfort predicates and crop definitions, reload behavior, compatibility boundaries, and retained performance diagnostics.
- `examples/comfort/README.md` — documentation-only optional furniture compatibility example.

New Revision 3 tag/rule surfaces must be documented when their runtime behavior becomes supported.

## Implementation tracking

- `implementation/HOMETOWN_IMPLEMENTATION_STATUS.md` — single current milestone status, implementation ownership, validation checkpoint, and immediate next sequence.

## Historical technical reference

`reference/` contains earlier milestone documentation, diagnostics, validation notes, and the original foundation/Ledger specifications. These files are useful for compatibility and design history but are not higher authority than the current Revision 2 or Revision 3 documents and owner-approved milestone contracts.

## Evidence

`evidence/` contains retained build/test logs and milestone evidence. Do not rewrite historical evidence when doing new work; add a new milestone/checkpoint directory instead.

## Development rule

Keep gameplay changes, repository-organization changes, and evidence/checkpoint updates in clearly scoped commits or branches. A milestone or version slice should not be called complete merely because it compiles: automated tests, full build, specification conformance, and required owner/live validation remain separate gates.
