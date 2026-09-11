# Town Ledger in-game acceptance

The existing foundation was reported working by the user. The following checks are for the new read-only Ledger milestone and remain pending in Minecraft.

Use Minecraft 1.21.1, NeoForge 21.1.250, Java 21, and the updated Hometown JAR on both client and server.

## Overview live updates

- [ ] Open a Ledger for a town with two villagers, two beds, one adult Farmer, and one unemployed villager. Expect Population 2, Beds 2, Employed 1, Professions 1.
- [ ] Add a Librarian and one bed. Close and reopen. Expect 3, 3, 2, 2.
- [ ] Remove the Farmer's workstation and wait until Minecraft actually changes its profession. Close and reopen. Expect 3, 3, 1, 1.
- [ ] Add a baby: population rises; employed count and profession diversity do not.
- [ ] Confirm town name, original founder/day, dimension, and Bell coordinates match the saved record.

## Residents and book

- [ ] Four bookmark tabs work and clearly indicate the active tab.
- [ ] Residents shows each loaded living villager, a custom name or Villager fallback, profession, and Adult/Child.
- [ ] With more than four residents, use arrows to reach every resident. Reopen after a profession/name change to see fresh information.
- [ ] Hover long truncated lines to read their supplied full text.
- [ ] Development shows all six specified categories as Not yet tracked; History contains only the founding event.
- [ ] Check normal GUI scales, mouse clicks, keyboard tab focus, and Escape. Gameplay continues while the book is open.
- [ ] Close immediately after requesting data; a delayed reply does not reopen the book.

## Bell, unavailable data, invalid links

- [ ] Break the founding Bell. The Ledger still opens and gives the saved replacement coordinates.
- [ ] Replace the Bell at those coordinates and reopen. The missing warning disappears.
- [ ] Open from far away or another dimension with settlement chunks unloaded. Data is marked partial/unavailable and no chunks are forced to load.
- [ ] Read a Ledger with an unknown UUID. A clean error is shown without a crash.
- [ ] Retry a briefly rate-limited request; the book recovers normally.

## Existing-world regression

- [ ] Close Minecraft completely, restart, open the same world, and use an existing Ledger. Identity and live statistics work without migration.
- [ ] Recheck Book + crouch + Bell founding, failed-validation Book preservation, duplicate Bell rejection, and full-inventory Ledger delivery.
- [ ] Confirm idle server behavior has no background settlement scans or log spam.

The automated suite covers the corresponding server logic and drawing code, but these real Minecraft checks are still required before certifying the milestone complete in-game.

## v0.2.1 sharpness — pending in-game verification

At Minecraft GUI scales 2, 3, and 4, open each of Overview, Residents, Development, and History. Confirm sharp text and parchment edges, unchanged bookmark styling and behavior, and no page content outside the ledger. Include resident paging and hover tooltips. Automated drawing-order and bounds checks pass, but actual GPU/font appearance must be confirmed in Minecraft.

## Rendering sharpness

Pending in-game: inspect all four pages, parchment, tabs, paging and tooltips at GUI scales 2, 3, and 4. Confirm crisp edges and no overflow. See RENDERING-FIX.md for automated checks.

## v0.3a room prototype

Run the structural scenarios and manual performance checks listed in ROOM-DETECTION.md in a live Minecraft instance. These playtests remain pending; the automated fixtures and command tests pass.

## v0.3b manual acceptance (pending)

Reopen Development after adding/removing an enclosed bed, breaking/repairing a window, opening/closing a door, adding an outdoor bed, or changing adult/child population. Check 10 residents / 9 enclosed beds = 90%, then 10/10 = 100%. Check shared versus private rooms, surplus capacity, zero residents (N/A), and unavailable town chunks (incomplete, no displayed zero counts). Repeat at GUI scales 2, 3, and 4. Confirm Residents, History, founding, persistence, and room debugging still work. Automated fixtures pass; these are the remaining in-game checks.

## v0.3c live acceptance (pending)

In a real Hometown village, verify the six Development bookmarks and active underline at GUI scales 2, 3, and 4. Check separate Supply and Privacy bars and all five placeholder spreads. Add/remove beds through 1, 2, 3, 4, and 5 beds per room, reopening the Ledger each time. Check 6 private beds plus 2 shared beds = 95% Privacy; 10 beds in one room = 100% capacity / 40% Privacy for 10 residents; 8 private beds for 10 residents = 80% capacity / 100% Privacy / SHORTAGE. Check broken/repaired windows, N/A cases, unloaded chunks, both debug commands, Residents pagination, History, Overview, founding, and persistence. Automated fixtures pass; no live v0.3c village playtest was performed in this environment.

## v0.4a live acceptance (pending)

Reopen Food after adding/removing food from a chest, trapped chest, and barrel. Check a double chest counts nutrition once, repeated foods do not inflate unique types, and non-tagged inventories do not count. Check the exclusion tag and a standard-component food from any installed food mod. Verify 1,060 nutrition / 10 residents / 20 daily nutrition = 5.3 days; change population and confirm duration updates. Check empty, zero-resident, incomplete, and 12-day/full-bar cases. Review all Ledger tabs and scales 2/3/4, debug food/housing/room, and inventory contents after repeated scans. Automated fixtures pass; live Minecraft and installed-mod playtests remain pending.

## Food scan robustness live acceptance (pending)

Install the scan-fix JAR on both sides. In the photographed village, run `/hometown debug food` and inspect the exact reasons/counters. With edge chunks unavailable, reopen Food and confirm known nutrition/stores remain visible as PARTIAL DATA with qualified reserves and no EMPTY label. Load the area normally and confirm complete scans retain existing Food behavior. Check unopened loot and unavailable stores have specific reasons without generating loot. Recheck Housing/Privacy and navigation at scales 2/3/4. Automated tests pass; this live village test remains pending.
