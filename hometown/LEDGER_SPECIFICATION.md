You are working on an existing NeoForge 1.21.1 mod named **Hometown**.

The mod is already loading correctly in Minecraft and the following functionality is already working:

- A player can Shift + Right Click a vanilla Bell while holding a vanilla Book.
- The mod validates the settlement before founding.
- A naming prompt appears.
- The player can found a named Hometown.
- The Hometown persists correctly across world reloads.
- A custom Town Ledger item is created and linked to the Hometown.
- If the player's inventory is full, the Ledger drops into the world safely.
- Breaking the founding Bell does not delete the Hometown.
- The Ledger tells the player to replace the Bell at its original location if the Bell is missing.
- The same Bell cannot be used to create a second Hometown.
- Founding requires at least 2 villagers and 2 beds.
- Invalid founding attempts correctly fail without consuming the Book.

Do not rewrite or replace working founding/persistence logic unless a change is strictly necessary to support this milestone.

The goal of this task is to implement the first proper **Town Ledger UI** and basic live settlement statistics.

# Goal

When a player right-clicks a valid Town Ledger, instead of printing the Hometown information into chat, open a custom client-side screen styled like an open Minecraft book.

The UI should feel like a handwritten town record or ledger, not like a modern management dashboard.

It must display live data retrieved from the server for the Hometown associated with that Ledger.

This milestone is read-only.

Do not implement town management, progression, economy, comfort, prosperity, projects, permissions, or editing tools.

# UI Direction

The Town Ledger should look like an open parchment/book interface.

The interface should support tabs, conceptually like bookmarks inside the book.

Use four tabs:

1. Overview
2. Residents
3. Development
4. History

The active tab should be visually distinct.

The UI should remain readable at normal Minecraft GUI scales.

Do not use a modern flat-dashboard aesthetic.

Use vanilla-style or parchment/book-inspired visuals where practical.

# Tab 1 — Overview

This tab should be fully functional.

Display:

- Hometown name
- Founder name
- Founded day
- Dimension
- Bell coordinates
- Population
- Beds
- Employed villagers
- Profession diversity

Example:

Dured

Founded by GalrUI
Founded Day 18

Dimension: Overworld
Bell: 115, 68, 7

Population: 3
Beds: 4
Employed: 2
Professions: 2

All values must come from authoritative server data.

Do not trust cached item lore or client-side guesses.

# Live Settlement Statistics

Add a basic scanner for the following metrics.

Use the existing Hometown settlement radius and vertical range already stored/configured by the mod.

## Population

Population = all living vanilla Villager entities inside the settlement bounds.

Include:

- adult villagers
- baby villagers

Exclude:

- Wandering Traders
- Zombie Villagers
- non-villager humanoid entities

## Beds

Beds = valid vanilla HOME POIs within the Hometown bounds.

Do not require the bed to currently be claimed.

Use the same style of bed/POI detection already used by founding validation if possible.

Avoid duplicating logic unnecessarily.

## Employed

Employed = all living villagers in the settlement whose profession is neither:

- NONE
- NITWIT

Baby villagers should not count as employed.

## Profession Diversity

Profession Diversity = count of unique professions among employed adult villagers.

Example:

3 employed villagers:
- Farmer
- Farmer
- Librarian

Profession Diversity = 2

Do not count NONE or NITWIT.

# Tab 2 — Residents

For this milestone, implement a basic resident listing.

For every living villager inside the Hometown bounds, show:

- display/name if available
- profession
- adult or child

Example:

Gerald — Farmer — Adult
Mira — Librarian — Adult
Villager — Unemployed — Child

If a villager has a custom name, use it.

If no name exists, use an appropriate fallback.

If the existing modpack later supplies villager naming through another mod, this UI should naturally display the custom entity name if Minecraft exposes it normally.

Do not add compatibility code for external villager mods yet.

If there are too many residents to fit on one page, provide basic scrolling or paging.

Keep the implementation simple.

# Tab 3 — Development

This tab is a placeholder only.

Display the following future categories:

Housing
Food
Safety
Comfort
Commerce
Prosperity

Each should currently display something like:

Not yet tracked

Do not calculate these values.

Do not implement any mechanics for them.

# Tab 4 — History

For this milestone, show only the founding event.

Example:

Day 18
Dured was founded by GalrUI.

If founded day is already stored directly, use it.

If only founded game time is stored, calculate the appropriate Minecraft day consistently.

Do not build a full history/event system yet.

Do not add new persistent history storage unless it is genuinely required for this simple founding entry.

# Networking and Server Authority

The Town Ledger screen must display fresh server-derived information.

Preferred flow:

Player right-clicks Town Ledger

→ client requests Hometown data from server

→ server resolves the Ledger's settlement UUID

→ server validates that the settlement exists

→ server scans live settlement state

→ server sends a read-only snapshot to the client

→ client opens/renders the Ledger UI

Create a clean data-transfer object/snapshot rather than exposing mutable Settlement objects directly to the client.

Conceptually:

TownLedgerSnapshot

Fields may include:

- settlement UUID
- town name
- founder name
- founded day
- dimension display name
- bell position
- population
- beds
- employed count
- profession diversity
- resident summaries

Resident summary should contain only what the UI needs, for example:

- display name
- profession display name
- adult/child

Do not send full Villager entities or unnecessary NBT data.

# Error Handling

If the Ledger references a settlement UUID that does not exist:

Display a clean error screen or message:

"This ledger no longer points to a known hometown."

If the founding Bell is missing:

The Ledger UI may still open.

Show a warning near the Bell information such as:

"Founding bell missing. Replace it at 115, 68, 7."

Do not block access to the Ledger merely because the Bell is missing.

If the settlement chunks are not available or live scanning cannot complete safely:

Do not force-load chunks.

Return the best safe state possible or display that the live data is unavailable.

Hometown must not create chunk tickets.

# Performance Requirements

Do not run settlement scans every tick.

For this milestone, scanning on Ledger open is acceptable.

Do not introduce background settlement scanning yet.

Do not force-load chunks.

Do not create idle server overhead.

# Architecture

Keep responsibilities separated.

Suggested additions:

client/
    TownLedgerScreen.java

network/
    RequestTownLedgerPayload.java
    TownLedgerSnapshotPayload.java

settlement/
    SettlementStats.java
    SettlementScanner.java

Optional data-transfer classes:

network/data/
    TownLedgerSnapshot.java
    ResidentSummary.java

Reuse existing Settlement, SettlementManager, HometownSavedData, configuration, and Ledger UUID linkage wherever possible.

Do not duplicate settlement lookup logic.

Do not move persistence responsibility into UI or networking classes.

The client screen is presentation only.

The server remains authoritative.

# Visual Behavior

The screen should:

- use an open-book/parchment visual
- have four clickable tabs
- render the active tab clearly
- display dark readable text
- preserve Minecraft UI scale compatibility
- support Escape to close
- not pause multiplayer gameplay
- not allow editing town values

Tabs may be implemented as small parchment bookmarks along the edge or top of the book.

Keep visuals functional first.

Do not spend excessive time creating elaborate textures before the UI works.

Placeholder vanilla-compatible textures are acceptable if needed.

# Testing Requirements

After implementation, test the following.

## Overview Live Update Test

Start with a Hometown containing:

2 villagers
2 beds
1 employed Farmer
1 unemployed villager

Expected:

Population: 2
Beds: 2
Employed: 1
Professions: 1

Add a Librarian and one bed.

Reopen the Ledger.

Expected:

Population: 3
Beds: 3
Employed: 2
Professions: 2

Remove the Farmer's workstation so the Farmer becomes unemployed.

Reopen the Ledger after Minecraft updates the villager profession.

Expected:

Population: 3
Beds: 3
Employed: 1
Professions: 1

## Resident Test

Verify all residents appear in the Residents tab.

Verify profession changes appear after closing and reopening the Ledger.

Verify baby villagers appear as children.

Verify custom-named villagers display their names.

## Bell Missing Test

Break the Hometown's founding Bell.

Open the Ledger.

The Ledger should still open.

It should show that the Bell is missing and include the stored Bell coordinates.

Replace the Bell at the correct location.

Reopen Ledger.

Warning should disappear.

## Persistence Test

Close Minecraft completely.

Restart.

Open the same world.

Open the Ledger.

Town identity and live statistics should still work.

## Invalid Ledger Test

Create or manipulate a Ledger whose settlement UUID no longer exists.

Opening it should fail gracefully without crashing.

# Explicit Non-Goals

Do not implement:

- Prosperity calculations
- Comfort calculations
- Food calculations
- Safety calculations
- Commerce calculations
- Town upgrades
- Town levels
- Civic projects
- Town renaming
- Settlement deletion UI
- Permissions
- Town ownership mechanics
- Resident assignment
- Resident teleportation
- Workstation assignment
- Housing assignment
- Taxes
- Currency
- Rewards
- Bounties
- Raids integration
- Villager Overhaul compatibility
- Villager Comfort compatibility
- Aether compatibility
- Mekanism compatibility
- Ad Astra compatibility
- Background scanners
- Forced chunk loading
- Custom villager AI
- Village generation changes

If implementation starts moving into any of these areas, stop.

# Definition of Done

This milestone is complete when:

A player can right-click their Town Ledger and open a tabbed book-style interface that displays live, server-derived Hometown information.

The Overview tab correctly reports:

- town identity
- population
- beds
- employment
- profession diversity

The Residents tab correctly lists current villagers.

The Development tab contains placeholders only.

The History tab shows the founding event.

Changing the real village changes the Ledger data the next time it is opened.

The existing founding, persistence, Bell recovery, and Ledger systems must continue working exactly as before.

Build the project and resolve all compiler errors before finishing.

Do not expand the scope beyond this specification.