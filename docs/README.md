# Hometown documentation

This directory separates current specification authority, implementation tracking, supported configuration/datapack surfaces, historical technical notes, and retained validation evidence.

## Specification authority

- `specs/Hometown_Implementation_Specification_Revision_2.docx` — current Revision 2 implementation specification and prerequisite milestone authority.
- `specs/Hometown_Progression_Town_Operations_Revision_3.docx` — Revision 3 progression and town-operations specification, to follow the required Revision 2 prerequisite work.
- `specs/archive/Hometown_Implementation_Specification.docx` — older pre-Revision-2 specification retained for history only.

Revision 1 / older material must not override Revision 2 or Revision 3 requirements.

## Configuration and datapacks

- `CONFIGURATION_AND_DATAPACKS.md` — supported R2 server configuration, Comfort/Food/Safety tags, version-1 Comfort predicates and crop definitions, reload behavior, compatibility boundaries, and M6 performance diagnostics.
- `examples/comfort/README.md` — documentation-only optional furniture compatibility example.

## Implementation tracking

- `implementation/HOMETOWN_IMPLEMENTATION_STATUS.md` — milestone status, prior evidence, implementation ownership, and validation notes.

## Historical technical reference

`reference/` contains earlier milestone documentation, diagnostics, validation notes, and the original foundation/Ledger specifications. These files are useful for compatibility and design history but are not higher authority than the current Revision 2 and Revision 3 specification documents.

## Evidence

`evidence/` contains retained build/test logs and milestone evidence. Do not rewrite historical evidence when doing new work; add a new milestone/checkpoint directory instead.

## Development rule

Keep gameplay changes, repository-organization changes, and evidence/checkpoint updates in clearly scoped commits or branches. A milestone should not be called complete merely because it compiles: automated tests, full build, specification conformance, and any required owner/play validation remain separate gates.
