# Hometown v0.1 — Founding Foundation

## 1. Purpose

Hometown is a NeoForge 1.21.1 mod built around one core idea:

**A player should be able to deliberately choose an existing Minecraft village, declare it their hometown, and then invest in that same place throughout the rest of the game.**

Version 0.1 does **not** implement town progression.

Its only purpose is to establish the persistent foundation that future systems will depend on.

The successful player experience for v0.1 is:

**Book + Shift-Right-Click Bell → validate village → name hometown → create persistent settlement → receive Town Ledger → settlement survives world restart.**

If that works reliably, v0.1 is successful.

---

# 2. Platform

Minecraft: **1.21.1**

Loader: **NeoForge 21.1.x**

Java: **21**

Mod Name: **Hometown**

Mod ID: `hometown`

Initial package namespace:

`dev.conner.hometown`

This can be changed later if Hometown is eventually published under a dedicated project identity.

---

# 3. Design Philosophy

Hometown should feel like an extension of vanilla Minecraft rather than a colony-management game.

The player physically builds the settlement.

The mod observes and reacts to what exists in the world.

Hometown should never replace building with menus, blueprints, workers, abstract upgrade points, or town-management spreadsheets.

The town should become valuable because of what the player actually builds and maintains.

Version 0.1 establishes only the identity and persistence layer required for those future systems.

---

# 4. Founding Interaction

A hometown is founded through a vanilla Village Bell.

The required interaction is:

**Player holds a vanilla Book in their MAIN HAND.**

**Player crouches.**

**Player right-clicks a vanilla Bell.**

Normal bell interaction should remain unchanged if those conditions are not met.

Only:

`Items.BOOK`

counts as the founding item.

Books and Quills, Written Books, enchanted books, or modded books do not count.

---

# 5. Founding Validation

When the player Shift-Right-Clicks a bell with a Book, the server performs preliminary validation.

Default settlement dimensions:

**Horizontal radius:** 64 blocks

**Vertical scan distance:** 32 blocks above and below the bell

The scan area is therefore approximately:

`128 × 64 × 128`

centered on the bell.

The following requirements must pass.

### Bell

The clicked block must still be a vanilla Bell.

### Residents

At least:

**2 living vanilla Villager entities**

must exist inside the settlement scan area.

Include adult and baby villagers.

Do not count:

- Wandering Traders
- Zombie Villagers
- other humanoid mobs
- villagers outside the scan area

### Housing

At least:

**2 valid HOME POIs**

must exist inside the settlement scan area.

For v0.1, a valid vanilla bed registered as a HOME POI counts.

The bed does not need to be currently claimed by a villager.

We are deliberately keeping housing validation simple during the foundation release.

### Existing Hometown

The bell must not already be registered to an existing Hometown.

### Settlement Overlap

A new Hometown cannot overlap an existing Hometown in the same dimension.

For two settlements:

`distance between anchors >= settlementA.radius + settlementB.radius`

With default 64-block radii, hometown bells therefore need to be approximately 128 horizontal blocks apart.

Vertical distance should not allow settlements to bypass this restriction.

Overlap calculations should primarily use horizontal X/Z distance.

Settlements in different dimensions do not conflict.

---

# 6. Validation Failure Feedback

Founding failures should clearly explain the problem to the player.

Examples:

**Insufficient residents**

> A hometown needs at least 2 residents nearby. Found: 1.

**Insufficient housing**

> A hometown needs at least 2 beds nearby. Found: 1.

**Existing hometown**

> This bell already anchors Oakridge.

**Nearby hometown**

> Another hometown is too close to establish one here.

Do not consume the Book on failure.

Do not create partial settlement data.

---

# 7. Naming Flow

If preliminary validation succeeds, the server tells the client to open the:

**Found a Hometown**

screen.

The screen contains:

Town Name:

`[____________________________]`

Buttons:

**Found Hometown**

**Cancel**

No other town-management functionality belongs on this screen.

### Name rules

Minimum length after trimming:

**1 character**

Maximum length:

**32 characters**

Leading and trailing whitespace must be removed.

Control characters and Minecraft formatting code character `§` are not allowed.

Normal letters, numbers, spaces, punctuation and Unicode characters should otherwise be allowed.

Town names should be case-insensitively unique within the save.

For example:

`Oakridge`

and

`oakridge`

cannot both exist.

---

# 8. Server Authority

The client must never be trusted to create a town directly.

When the player submits a name, the server performs **all founding checks again**.

This protects against:

- the Bell being broken while the naming screen was open
- villagers leaving
- beds being destroyed
- another player founding a town nearby
- another player registering the same Bell
- the player moving away
- the founding Book disappearing
- malicious client packets

At final submission, verify:

- correct dimension
- clicked Bell still exists
- player is within 8 blocks of the Bell
- player still has a vanilla Book available
- settlement requirements still pass
- town name is valid
- town name is not already used
- settlement does not overlap another Hometown

Only after every check passes should settlement creation occur.

---

# 9. Book Consumption

On successful founding:

Survival players lose exactly:

**1 Book**

Creative-mode players do not lose the Book.

The Book is not consumed when:

- validation fails
- naming is cancelled
- the name is invalid
- another settlement is created before submission
- networking fails
- creation otherwise fails

The settlement record should be successfully created before the item transaction is finalized.

The system should avoid any state where the player loses the Book but does not receive a town.

---

# 10. Settlement Data Model

Create a central `Settlement` object.

Minimum v0.1 data:

```java
UUID id;

String name;

ResourceKey<Level> dimension;

BlockPos bellPosition;

int radius;

UUID founderUuid;

String founderName;

long foundedGameTime;
```

Optional cached/friendly value:

```java
long foundedDay;
```

`foundedDay` may instead be calculated from `foundedGameTime`.

The UUID is the authoritative identity of the settlement.

Town names and Bell locations must never be used as database identifiers.

---

# 11. Settlement Radius

Each settlement stores its radius when it is founded.

Changing the server's default radius later should **not automatically alter existing towns**.

This protects existing saves from suddenly creating overlapping hometowns after a configuration change.

Future versions may add explicit settlement expansion or resizing.

Version 0.1 does not.

---

# 12. Persistent Storage

Create:

`HometownSavedData`

This should contain all Hometowns in the save.

Conceptually:

```java
Map<UUID, Settlement> settlements;
```

Useful lookup methods should include:

```java
getSettlement(UUID id)

findByBell(ResourceKey<Level> dimension, BlockPos pos)

findByName(String name)

findSettlementContaining(ResourceKey<Level> dimension, BlockPos pos)

getSettlementsInDimension(ResourceKey<Level> dimension)

addSettlement(Settlement settlement)

removeSettlement(UUID id)
```

Hometown data should be attached to persistent world SavedData.

Every mutation must mark the SavedData as dirty.

---

# 13. Persistence Requirement

This is the most important acceptance requirement in v0.1.

A player must be able to:

Found **Oakridge**

Quit to title screen.

Reload the world.

Oakridge still exists.

Close Minecraft completely.

Launch Minecraft again.

Load the world.

Oakridge still exists.

Copy the world save.

Load the copied save.

Oakridge still exists.

Settlement UUIDs must remain unchanged during all of these operations.

---

# 14. Bell Destruction

Breaking the founding Bell does **not** automatically delete the Hometown.

This is intentional.

Accidental explosions, player mistakes, raids, mods, or world damage should not permanently erase town data.

If the Bell is missing:

The settlement remains stored.

Its anchor is considered unavailable.

If a vanilla Bell is placed again at the exact stored anchor position:

The Hometown becomes usable again.

Version 0.1 does not allow moving a Hometown anchor.

Version 0.1 does not automatically delete abandoned Hometowns.

---

# 15. Town Ledger

Successful founding creates one:

`hometown:town_ledger`

The Town Ledger should be a custom non-stackable item.

Maximum stack size:

**1**

The Ledger contains a persistent reference to the settlement UUID.

Prefer a dedicated Hometown data component rather than using the item's display name as identity.

Conceptually:

```java
SettlementIdComponent(UUID settlementId)
```

The Ledger should receive a display name based on the Hometown.

Example:

**Oakridge Town Ledger**

Basic lore may contain:

> Founded by Conner  
> Founded Day 18

The Ledger should not yet contain population, prosperity, comfort, housing, economy, or other future statistics.

---

# 16. Ledger Delivery

After successful founding:

Attempt to insert the Town Ledger into the player's inventory.

If the inventory is full:

Drop the Ledger safely at the player's position.

The Ledger must never silently disappear because the inventory was full.

---

# 17. Ledger Interaction

For v0.1, right-clicking while holding a valid Town Ledger may display a minimal server-derived message:

> Oakridge  
> Founded by Conner  
> Bell: 152, 68, -341  
> Dimension: Overworld

The Ledger must resolve its settlement using the UUID stored on the item.

If that UUID cannot be found:

> This ledger no longer points to a known hometown.

The Ledger does not open a management GUI in v0.1.

---

# 18. Successful Founding Feedback

Successful founding should be noticeable but restrained.

Recommended behavior:

Ring the founding Bell once.

Display:

> Oakridge has been founded.

No giant RPG interface.

No fireworks.

No reward chest.

No emerald reward.

The act of establishing the town is the reward.

An advancement can be added later.

---

# 19. Multiplayer Behavior

Multiple players may create different Hometowns.

Each Hometown records its original founder.

Version 0.1 does **not** implement:

- ownership permissions
- town members
- mayors
- co-owners
- teams
- claims
- grief protection

Founder identity is currently historical metadata only.

Two players attempting to found the same Bell simultaneously must not create duplicate settlements.

The server's final validation and settlement registration must be authoritative.

---

# 20. Configuration

Create a server-side configuration containing at least:

```text
settlementRadius = 64

verticalScanRadius = 32

minimumVillagers = 2

minimumBeds = 2

preventSettlementOverlap = true

consumeFoundingBook = true
```

Reasonable ranges should prevent nonsensical configuration values.

Example:

```text
settlementRadius: 16–256
verticalScanRadius: 8–128
minimumVillagers: 0–100
minimumBeds: 0–100
```

Town name length should remain hardcoded to 32 for v0.1.

---

# 21. Performance Rules

There is no continuous settlement scanner in v0.1.

Do not scan villages every tick.

Founding validation happens only when required.

Ledger lookup should use stored settlement data.

Future resident/infrastructure scanning will use scheduled and event-driven updates.

Version 0.1 should have essentially negligible idle server cost.

---

# 22. Debug/Admin Commands

Player-facing town creation must only use the Bell + Book system.

However, development/admin commands are allowed.

Suggested commands:

```text
/hometown debug list
```

Lists known settlements.

```text
/hometown debug inspect <uuid>
```

Displays serialized settlement information.

```text
/hometown debug remove <uuid>
```

Deletes a settlement.

```text
/hometown debug ledger <uuid>
```

Creates a replacement Ledger for testing/recovery.

These commands should require operator/cheat permission.

There should be **no `/hometown create` command**.

Founding should always exercise the actual player mechanic.

---

# 23. Logging

Log important lifecycle events at appropriate levels.

Examples:

```text
Created Hometown 'Oakridge' [UUID] at minecraft:overworld 152 68 -341

Loaded 3 Hometowns from SavedData

Removed Hometown 'Oakridge' [UUID]
```

Do not log normal scans or interactions every tick.

Do not spam the console.

---

# 24. Internal Architecture

Suggested structure:

```text
dev.conner.hometown
│
├── Hometown.java
│
├── config
│   └── HometownServerConfig.java
│
├── settlement
│   ├── Settlement.java
│   ├── SettlementManager.java
│   ├── SettlementValidator.java
│   └── HometownSavedData.java
│
├── item
│   ├── TownLedgerItem.java
│   └── HometownItems.java
│
├── component
│   ├── SettlementIdComponent.java
│   └── HometownDataComponents.java
│
├── interaction
│   └── BellInteractionHandler.java
│
├── network
│   ├── OpenTownNamingPayload.java
│   ├── SubmitTownNamePayload.java
│   └── HometownNetworking.java
│
├── client
│   └── TownNamingScreen.java
│
└── command
    └── HometownDebugCommands.java
```

Class names may change if NeoForge architecture makes another structure cleaner.

Responsibilities should remain separated.

Do not put all settlement logic in the main mod class.

---

# 25. Important Architectural Rule

`SettlementManager` should be the authority for settlement creation.

The networking code should not directly modify SavedData.

The Bell interaction should not directly create settlements.

The screen should never create settlements.

Flow should conceptually be:

```text
Bell Interaction
      ↓
SettlementValidator
      ↓
Open Naming Screen
      ↓
Submit Name Packet
      ↓
SettlementValidator
      ↓
SettlementManager.createSettlement()
      ↓
HometownSavedData
      ↓
Town Ledger
```

This matters because future systems will need to create, query and modify settlements without duplicating founding logic.

---

# 26. Networking

The naming screen requires client/server networking.

Required conceptual payloads:

### Server → Client

`OpenTownNamingPayload`

Contains enough information for the client to open the naming screen for the selected Bell.

### Client → Server

`SubmitTownNamePayload`

Contains:

```text
bell position
requested town name
```

Do not send settlement UUID from the client because the settlement does not exist yet.

The server creates the UUID.

The server must independently know the player's current dimension.

Never trust a dimension value supplied by the client.

---

# 27. No Chunkloading

Hometown must not force settlement chunks to remain loaded.

Founding scans only loaded world state around the player.

Settlements stored in unloaded chunks remain persisted normally.

No Hometown functionality in v0.1 should create chunk tickets.

---

# 28. Compatibility Philosophy

Version 0.1 depends only on:

Minecraft

NeoForge

Do **not** integrate:

Villager Overhaul

Villager Comfort Continued

Aether

Mekanism

Ad Astra

or any other pack mod yet.

Those integrations come after the standalone Hometown foundation is proven stable.

The mod should run successfully in an otherwise vanilla NeoForge instance.

---

# 29. Explicit Non-Goals for v0.1

Do not implement any of the following:

- Prosperity
- Comfort
- Food security
- Safety score
- Employment score
- Profession diversity
- Economy
- Bounties
- Shops
- Taxes
- Passive emerald generation
- Resident history
- Resident UUID tracking
- Village expansion
- Multiple Bells
- Town relocation
- Town merging
- Town permissions
- Town membership
- Town ownership mechanics
- Mayors
- Elections
- Raids integration
- Civic projects
- Building detection
- Roads
- Farms
- Markets
- Industry
- Mekanism support
- Vehicle support
- Aether support
- Ad Astra support
- Spaceports
- Town-management dashboard
- Quest book
- Custom currency
- Automatic chunkloading
- Custom villagers
- Custom village generation

If implementation begins drifting toward these systems, stop.

They belong to later releases.

---

# 30. Acceptance Tests

Hometown v0.1 is ready only when all of these work.

### Normal founding

Place/find a village containing:

2 villagers

2 beds

1 Bell

Hold Book.

Crouch.

Right-click Bell.

Naming screen opens.

Name town:

`Oakridge`

Confirm.

One Book is consumed.

Town Ledger appears.

Bell rings.

Message appears:

> Oakridge has been founded.

### Cancelled founding

Open naming screen.

Press Cancel.

No town exists.

Book remains.

### Insufficient villagers

Use Bell with only one nearby villager.

Creation is rejected.

Book remains.

### Insufficient beds

Use Bell with fewer than two nearby beds.

Creation is rejected.

Book remains.

### Duplicate Bell

Attempt to establish another town using Oakridge's Bell.

Creation is rejected.

### Overlapping settlement

Attempt to establish another town inside Oakridge's settlement radius.

Creation is rejected.

### Separate settlement

Travel far enough away.

Create second Hometown.

Both persist independently.

### Persistence

Create Oakridge.

Quit world.

Reload.

Oakridge remains.

Close Minecraft.

Restart.

Oakridge remains.

### Broken Bell

Break Oakridge's Bell.

Oakridge remains in SavedData.

Ledger indicates its saved anchor.

Replace Bell at the original coordinates.

Oakridge remains associated with it.

### Full inventory

Fill inventory.

Found town.

Ledger drops safely near player.

### Creative mode

Found town while in Creative.

Book is not consumed.

Town is still created normally.

### Multiplayer race

Two players attempt to found the same Bell.

Only one settlement can be created.

No duplicate UUIDs.

No duplicate towns attached to one Bell.

---

# 31. Definition of Done

Version 0.1 is complete when:

**A player can intentionally establish a named Hometown using a Book and Bell, receive a Ledger referencing that settlement, and the settlement reliably survives saving, restarting, multiplayer use, Bell destruction, and basic edge cases.**

Nothing else is required.

That persistent relationship:

**Player ↔ Town ↔ Bell ↔ Ledger**

is the foundation every future Hometown mechanic will build upon.