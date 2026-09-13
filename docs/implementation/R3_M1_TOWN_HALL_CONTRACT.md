# Revision 3 M1 — Town Hall foundation contract

Status: **READY FOR IMPLEMENTATION**  
Branch: `r3-m1`  
Authority: Revision 3 plus the owner-approved M1 refinements recorded here.  
Baseline: accepted Revision 2 / `0.9.1`.

## 1. Purpose

R3 M1 establishes the first persistent civic-progression layer without replacing the accepted R2 observation architecture.

The milestone must deliver one complete vertical slice:

> A player gives a Hometown its civic colors, physically builds a qualifying Town Hall, labels it with a vanilla Hometown sign, presents that town's Ledger to register it, receives visible confirmation, permanently establishes the Town Hall progression step, and can open a physical Town Administration interface from the Hall's lectern. The state survives save/restart/reload. If the Hall later becomes invalid, Administration becomes unavailable until the Hall is restored or re-registered, but earned progression is never revoked.

R3 M1 is a foundation milestone. It does **not** implement Storage, Animal Farms, Notice Board gameplay, civic-project gameplay, daily meals, work production, travelers, recruitment, markets, specialization, offline catch-up, or autonomous town simulation.

## 2. Owner-approved refinements to the Revision 3 design

These decisions refine the M1 presentation/qualification layer while preserving the broader R3 progression order:

1. **Vanilla signs are the M1 civic marker.** Any earlier Civic Plaque concept is deferred. The persistent backend must use a generic facility designation/marker model so a future custom plaque/block can create the same facility state without rewriting progression.
2. **Town Hall size is room-area based, not a two-room/building-identity rule.** M1 requires one qualifying enclosed civic room with a provisional minimum of **20 usable interior floor positions**. This avoids inventing building topology before it is needed.
3. **Town identity gains two vanilla dye colors.** Each town has one primary and one secondary color selected from the 16 vanilla dye colors; the two colors must differ.
4. **Successful civic registration visibly changes the sign.** M1 must provide immediate old-school server-sign style feedback that Hometown recognized the marker. The minimum acceptance requirement is a visible change from the ordinary/default sign appearance using the town's color identity. Prefer primary/secondary line styling when the vanilla sign renderer/API supports it cleanly; do not add a custom sign renderer solely to force per-line coloring.
5. **Town Hall unlocks survival/civic foundations before commerce.** First establishment unlocks the progression nodes `NOTICE_BOARD`, `CIVIC_PROJECTS`, `STORAGE`, and `ANIMAL_FARMS`. In M1 these downstream nodes are visible as unlocked but may be non-interactive/not-yet-implemented. `STORAGE` is the progression concept; later design may present the physical facility as a Storehouse without creating a competing duplicate system.

## 3. Architectural invariants carried forward from R2

M1 must preserve all accepted R2 behavior unless this contract explicitly authorizes a new mutation:

- server-authoritative town truth;
- one save-wide `HometownSavedData` owner;
- loaded-only world access; no Hometown chunk tickets or force loading;
- reuse established room geometry and observation owners instead of parallel scans;
- explicit incomplete/unavailable results; unknown data can never qualify a facility;
- R2 Ledger observations remain read-only and cached navigation must not rescan;
- no background facility watcher or continuous polling;
- no offline catch-up;
- no gameplay effects from R2 Prosperity/Safety/Comfort/Food metrics beyond R3 behavior explicitly defined later;
- History is append-only structural reporting and must not be spammed by transient invalid/valid oscillation.

## 4. Town colors

### 4.1 Allowed palette

Use exactly the 16 vanilla `DyeColor` values:

`WHITE`, `ORANGE`, `MAGENTA`, `LIGHT_BLUE`, `YELLOW`, `LIME`, `PINK`, `GRAY`, `LIGHT_GRAY`, `CYAN`, `PURPLE`, `BLUE`, `BROWN`, `GREEN`, `RED`, `BLACK`.

Rules:

- primary and secondary are both required once configured;
- primary and secondary must differ;
- color choice is cosmetic/identity metadata only and never changes progression arithmetic or gameplay strength;
- M1 does not provide arbitrary RGB colors;
- M1 does not require recoloring after initial configuration.

### 4.2 New towns

The founding flow must collect name + primary color + secondary color before final settlement creation. The server validates all three before committing the new town.

The protected founding gesture remains the existing Book + shift-right-click Bell flow; M1 may extend its server-authoritative prompt/UI sequence but must not replace the founding gesture.

### 4.3 Existing R2 towns

Migration must not invent colors for existing towns.

A migrated R2 town begins with `colorsConfigured = false`. Existing R2 Ledger/observation functionality remains usable. Before the first R3 civic facility can be registered, a valid linked Town Ledger must complete a one-time town-color selection flow. This flow is available before a Town Hall exists, avoiding a Town-Hall/Administration chicken-and-egg dependency.

Possession of a valid Ledger linked to the town is the M1 authorization token; M1 does not invent a new founder-only permission model.

## 5. Generic civic facility designation model

The persistence/API must model a **facility**, not a `TownHallSign`.

Minimum conceptual state:

```text
FacilityMarker
  settlementId
  type
  dimension
  markerPos
  markerFace/side (front or back where applicable)
```

M1 implements only `FacilityType.TOWN_HALL`, but the model must permit later facility types without changing the meaning of the persisted Town Hall.

Exactly one registered marker is allowed for each unique facility type unless a later specification explicitly permits multiples.

The marker position is an anchor for validation, not the source of truth for permanent progression.

## 6. Town Hall sign grammar and registration

### 6.1 Accepted sign text

On the interacted sign face:

```text
[Hometown]
Town Hall
<player-owned>
<player-owned>
```

Parsing rules:

- trim outer whitespace;
- compare lines 1 and 2 case-insensitively;
- accept normal whitespace between `Town` and `Hall` after normalization;
- lines 3 and 4 are ignored by the parser and preserved exactly as player content;
- sign text alone never creates a facility.

Use vanilla `SignBlockEntity` support so ordinary standing/wall signs and hanging signs may qualify when the interacted face contains the grammar.

### 6.2 Registration gesture

A player right-clicks the correctly written sign with a Town Ledger linked to the containing town.

The server must validate, in order sufficient to avoid unintended world reads/mutations:

1. held item is a valid Town Ledger with a known settlement UUID;
2. sign is in the same dimension and within that settlement;
3. town colors are configured;
4. interacted sign face matches the Town Hall grammar;
5. the marker and all required Hall geometry are in loaded, authoritative scope;
6. the marker resolves to one complete enclosed room using the existing room geometry owner;
7. Town Hall qualification rules pass;
8. no conflicting currently valid Town Hall marker is already registered.

If validation fails, the sign is not registered, progression is not changed, History is not appended, and the player receives a specific reason.

### 6.3 Successful registration

On success the server atomically:

- stores/replaces the `TOWN_HALL` facility marker;
- visibly marks/recolors the registered sign face using town color identity while preserving the player's text content;
- if this is the town's first successful Town Hall establishment, permanently unlocks `TOWN_HALL`, `NOTICE_BOARD`, `CIVIC_PROJECTS`, `STORAGE`, and `ANIMAL_FARMS`;
- appends exactly one first-establishment History event;
- marks SavedData dirty;
- reports success to the player.

Re-registering/restoring/relocating a town whose Town Hall was established previously must never duplicate the first-establishment History event or relock/re-award progression.

## 7. Town Hall qualification

A Town Hall qualifies only when every required observation is complete and authoritative.

### 7.1 Enclosed civic room

- The sign must resolve to one room produced by the accepted existing room detector/`RoomGeometry` path.
- The room must be complete/enclosed.
- M1 must not implement a second room detector.

### 7.2 Minimum usable floor area

Initial balance value: **20 usable interior floor positions**.

This is intentionally floor area rather than raw room volume or fixed width/length. Irregular buildings are valid.

A usable floor position is a canonical interior standing location derived from the existing room geometry: it has a usable supporting floor and player/hostile-sized standing clearance within the enclosed room. Count unique usable positions, not vertical air cells.

`20` is a playtest balance value, not an architectural constant. Put it behind one M1 rule/config owner so it can be tuned without changing the algorithm.

### 7.3 Bookshelves

Require **at least 4 recognized bookshelves** belonging to the room.

Create/reuse a dedicated Hometown block tag for this semantic rule. The default M1 tag includes `minecraft:bookshelf`. Do not inspect bookshelf inventories or require arbitrary book items.

Blocks on the room's direct face-adjacent boundary may count, because solid bookshelves used as walls must not be excluded merely for forming part of the enclosure.

### 7.4 Lectern

Require **at least 1 vanilla lectern** belonging to the room. A book on the lectern is not required.

The lectern is the physical Administration access point after registration.

### 7.5 Recognized storage

Require **at least 1 recognized storage block** belonging to the room.

Reuse the established Food storage semantic owner/tag (`hometown:food_storage`) rather than inventing a second incompatible definition. Hall qualification checks structural tag membership only; it must not open inventories, generate loot, count food, or mutate contents.

### 7.6 Interior lighting

Every spawn-relevant usable interior floor position must have **block light >= 1**. This is intentionally above the modern hostile-mob block-light spawn threshold of 0 and matches Hometown's existing safe-light convention without depending on a configurable Safety score.

Rules:

- use block light, not time of day or sky light;
- check the interior spawn/standing sample positions, not decorative unreachable ceiling pockets;
- any unknown/unloaded/failed light sample makes Town Hall qualification fail as incomplete;
- do not force-load missing chunks.

## 8. Facility validity versus permanent progression

M1 must distinguish these concepts:

### Permanent progression

`TOWN_HALL` means the town has **ever successfully established** a qualifying Town Hall. Once earned, it is never automatically removed by structural damage, sign edits, unloading, or relocation.

The first Hall establishment permanently unlocks:

- `NOTICE_BOARD`
- `CIVIC_PROJECTS`
- `STORAGE`
- `ANIMAL_FARMS`

These unlocks likewise do not disappear when the current Hall becomes unusable.

### Current facility availability

The registered Town Hall is currently usable only when explicit revalidation confirms the marker and room still satisfy the M1 contract.

Examples that suspend current Administration without deleting progression:

- registered sign removed or grammar edited;
- room opened/broken;
- lectern removed;
- bookshelf count falls below 4;
- storage removed;
- floor area no longer meets the minimum;
- required light sample becomes 0;
- required scope is unloaded/unknown.

No background watcher is required. Current validity is checked on explicit Town Hall operations such as Administration open and new/replacement registration.

## 9. Existing Hall conflict and relocation behavior

A town may have only one currently registered Town Hall marker.

- If the old registered Hall is loaded and still valid, a new Town Hall registration attempt fails with an `already registered` result and identifies the existing marker position where appropriate.
- If the old marker is loaded and demonstrably invalid/missing, a new qualifying marker may replace it. Permanent progression is retained and no first-establishment event is duplicated.
- If the old marker's required scope is unloaded/unknown, M1 must not assume it is invalid merely to permit replacement. The attempt fails as unavailable/incomplete until the old marker can be authoritatively checked.

A later milestone may add an explicit relocation workflow; M1 does not need a separate relocation GUI.

## 10. Town Administration interface

### 10.1 Access gesture

With the linked Town Ledger in hand, the player right-clicks a lectern belonging to the **currently valid registered Town Hall**.

The server validates:

- Ledger/town identity;
- same dimension and reasonable interaction distance;
- clicked lectern belongs to the registered Town Hall room;
- registered marker grammar still matches;
- current Town Hall qualification is complete and valid.

Failure does not open a stale Administration screen.

### 10.2 M1 screen contents

The first Town Administration GUI is intentionally small.

Required information:

- town name;
- primary/secondary town identity accents where readable;
- Town Hall state: established + currently active/available, or unavailable with reasons;
- Progression view.

Required progression nodes after first establishment:

- Town Hall — **Established**
- Notice Board — **Unlocked / not yet implemented**
- Civic Projects — **Unlocked / not yet implemented**
- Storage — **Unlocked / not yet implemented**
- Animal Farms — **Unlocked / not yet implemented**

Downstream commerce/traveler/recruitment/specialization systems remain locked according to later R3 progression.

### 10.3 Session behavior

Opening Administration is an explicit server-authoritative validation request. Once the snapshot is delivered, local page/tab navigation must reuse that snapshot and must not trigger hidden rescans.

M1 Administration contains no generic client-authoritative mutation API. Future action buttons must use explicit server requests with fresh authorization/revalidation.

## 11. Persistence plan

### 11.1 SavedData version

R3 M1 introduces a new persisted civic/progression schema. Bump Hometown SavedData from version 2 to **version 3** when implementation lands.

The loader must support valid v1, v2, and v3 data:

- v1: preserve the already-supported identity/founding migration, create the required History state, then add empty/default R3 civic state;
- v2: preserve every R2 settlement and History field exactly, add empty/default R3 civic state;
- v3: read and validate R2 state plus R3 civic state.

Migration must never fabricate Town Hall establishment, facility markers, colors, or downstream unlocks.

### 11.2 Keep immutable R2 identity stable

Do not require rewriting the protected `Settlement` identity record merely to add R3 mutable civic state.

Preferred owner: one new per-town `TownCivicState`/`TownProgressionState` map inside the existing `HometownSavedData`, keyed by settlement UUID.

Conceptual durable state:

```text
TownCivicState
  colorsConfigured
  primaryColor?
  secondaryColor?
  progressionUnlocks
  facilityMarkers
```

Invariants:

- both colors present or both absent;
- configured colors are distinct;
- unlock IDs are known/bounded enum values;
- facility type appears at most once for unique facility types;
- every civic state owner must correspond to an existing settlement;
- malformed duplicate/foreign owners fail safely rather than attaching state to the wrong town.

Current Town Hall validity is **derived live** and must not be persisted as authoritative truth.

### 11.3 Atomic first establishment

The first successful Town Hall transaction must persist the marker, permanent unlock set, and first-establishment History event as one server-thread operation before reporting success. A save/restart may not produce `History says Hall established` while progression is absent, or vice versa.

## 12. History contract

Add one structural event for the first successful Hall establishment, e.g. `TOWN_HALL_ESTABLISHED`.

The event should retain at minimum:

- settlement UUID (existing History ownership);
- game time/day;
- marker position;
- dimension;
- town name or other stable display arguments as appropriate.

Only the transition from `never established` to `established` creates this event.

Do **not** append History merely because:

- the Hall becomes temporarily invalid;
- its chunks unload;
- the Hall becomes valid again;
- the same established Hall is revalidated;
- an established town replaces an invalid marker.

## 13. Networking/security contract

All registration, color selection, and Administration-open requests are server-authoritative.

Server checks must reject:

- spoofed/unknown town UUIDs;
- a Ledger that is not linked to the requested town;
- wrong dimension;
- marker outside town bounds;
- malformed facility type;
- stale marker/lectern positions;
- registration from incomplete world scope;
- client attempts to directly award progression/unlocks;
- duplicate/racing first-establishment requests.

Concurrent valid attempts are serialized on the server thread. Exactly one may perform the first-establishment transition and append its History event.

Payloads remain bounded and version-compatible with the Hometown network owner. No client class may be referenced from dedicated-server common code.

## 14. Performance/world-access contract

Town Hall registration and Administration open are explicit, infrequent player actions. They may evaluate the one candidate Hall room, but must stay bounded by established room geometry/work budgets.

M1 must not:

- scan every block in the town to find a Hall;
- search unloaded chunks;
- create chunk tickets;
- repeatedly scan from GUI navigation;
- inspect unrelated inventories;
- start a background Hall monitor.

Prefer the existing shared `RoomGeometry` + bounded block-observation cache pattern already used by Comfort/Safety.

## 15. Deliberately deferred decisions/features

Not required for M1 completion:

- custom Hometown plaque/block textures;
- custom sign renderer;
- arbitrary RGB town colors;
- town-color editing after initial configuration;
- two-room/building identity detection;
- Storage facility behavior;
- Animal Farm facility behavior;
- Notice Board behavior;
- Civic Project behavior;
- Daily Meal/workday operations;
- travelers/Inn;
- Market/recruitment;
- specialization;
- explicit Hall relocation GUI;
- offline simulation/catch-up.

## 16. M1 completion rule

R3 M1 may be marked COMPLETE only when:

1. the automated acceptance matrix is green;
2. normal Gradle `test` and `build` pass on the exact versioned implementation head;
3. v2-to-v3 migration is verified without R2 data loss or fabricated R3 state;
4. dedicated-server/common-code sanity remains green;
5. owner live validation proves the full physical Town Hall loop, visible sign registration, Administration access, persistence, invalidation/restoration, and no forced loading;
6. no required M1 acceptance item remains FAIL or NOT RUN.

Until then, downstream R3 systems may be planned but must not use an incomplete M1 foundation as if it were accepted.