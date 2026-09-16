# R3 M2 — 0.13.0 Notice Board Foundation

## Purpose

0.13.0 begins Revision 3 Milestone 2 by turning the already-unlocked Notice Board progression node into a real civic facility. It deliberately stops before Civic Project posting, contribution, completion, or rewards; those systems build on this foundation in the next M2 slice.

## Facility identity

The Notice Board is the board/sign itself rather than an enclosed-room facility. Registration uses a vanilla sign face with this narrow grammar:

- line 1: `[Hometown]`
- line 2: `Notice Board`
- lines 3–4: freeform player text

Matching is case-insensitive and collapses surrounding/repeated whitespace. The interacted sign face is the registered face.

## Registration contract

Registration is server-authoritative and uses the linked Town Ledger on the sign. The player must be alive, non-spectating, within 8 blocks, and the sign must be inside the Ledger's town and dimension. Town colors must already be configured and the permanent `NOTICE_BOARD` progression unlock must already have been earned through Town Hall establishment.

Only one Notice Board marker is current for a town. A valid existing board blocks replacement. An unloaded/unavailable existing board also blocks replacement because Hometown cannot safely prove it invalid. A missing, changed, or otherwise invalid existing board may be replaced.

Successful registration recolors the interacted sign face using the town identity colors, following the existing civic-facility feedback pattern.

## Validity and persistence

The generic `FacilityMarker` persists the Notice Board owner, type, dimension, block position, and sign face. No SavedData version bump is required because version 3 already persists generic facility markers by enum name.

Current validity is never persisted. A Notice Board is active only when its registered chunk is loaded, the marker still resolves to a sign, the registered face still exists, and its first two lines still match the Notice Board grammar. Hometown does not force-load chunks to validate it.

0.13.0 does not add a new History event. Project/history semantics for M2 remain separate from simple board-marker persistence so this slice does not invent an event policy before Civic Projects exist.

## Player-facing behavior

Normal right-click on the registered sign opens the existing Facility Detail UI. The board reports active/unavailable state, its registered sign position, its civic role, and explicitly states that Civic Project posting/contributions are not part of 0.13.0.

Town Administration distinguishes three Notice Board states: locked, unlocked but not established, and established active/unavailable. Civic Projects remain displayed as unlocked but not yet implemented.

## Compatibility and safety

- Existing Town Hall, Storage, Animal Farm, Daily Meal, census, and livestock behavior is unchanged.
- `NOTICE_BOARD` is appended to `FacilityType` so existing network enum ordinals retain their prior values.
- Registration and persistence mutation remain server-thread/server-authoritative.
- No world scan or chunk ticket is introduced.
- No project inventory mutation exists in this version.

## 0.13.0 acceptance gates

1. `[Hometown] / Notice Board` grammar accepts case/whitespace variants and rejects unrelated facilities.
2. A town without the Town Hall-granted Notice Board unlock cannot persist a board.
3. First registration persists exactly one Notice Board marker and survives save/load.
4. Repeating the same marker is idempotent; a replacement marker replaces the prior marker without duplicating facility state.
5. Live registration requires the linked Ledger, range, town containment, configured colors, unlock, and matching sign face.
6. A valid existing board blocks replacement; an unavailable existing board fails closed; an invalid existing board may be replaced.
7. Registration gives visible town-color sign feedback.
8. Normal use opens Facility Detail and reports Notice Board active/unavailable state.
9. Town Administration reports unlock, establishment, and active state separately.
10. Full automated test/build and owner live validation pass before 0.13.0 is called accepted.
