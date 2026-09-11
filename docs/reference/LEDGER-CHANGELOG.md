# Town Ledger UI milestone — 0.2.0

Right-clicking a Town Ledger now opens a read-only parchment book with four bookmark tabs.

- **Overview:** server-derived identity, founded day, dimension, Bell coordinates, population, beds, employment, and profession diversity.
- **Residents:** custom names or a Villager fallback, profession, and adult/child status. Four residents per page; every resident in the loaded area can be reached through paging.
- **Development:** Housing, Food, Safety, Comfort, Commerce, and Prosperity are labeled **Not yet tracked**. Nothing is calculated for these categories.
- **History:** one founding entry derived from the existing settlement record.

The client requests a held hand and page, never a town UUID. The server resolves the held item's existing UUID component, validates the item and settlement, and sends an immutable snapshot. Responses carry a request identifier so delayed replies cannot overwrite a newer request or reopen a closed screen.

Scans occur only on open, explicit retry, or resident page navigation. The server uses the settlement's stored horizontal radius and the existing vertical scan setting. Population and beds share the founding query implementation. Partial loaded areas are labeled; unavailable areas show unavailable statistics rather than misleading zeros. No chunk tickets, background scanners, or persistent statistics/history were added.

The Bell may be absent and the book still opens, with the original replacement coordinates. Invalid ledgers show a clear error. Long displayed lines have hover text, tabs are keyboard-focusable, Escape closes the screen, and gameplay is not paused.

The `Settlement`, `SettlementManager`, and `HometownSavedData` files are unchanged from the working foundation. Ledger creation and delivery methods are unchanged. The existing villager/bed query code was extracted into a shared helper without changing its validation rules.

Both client and server must use this version: the network protocol is now version 2. Existing settlement files and Ledger UUID components need no migration.
