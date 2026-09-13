# R2 M6 integration evidence — 2026-09-12

Milestone: **Revision 2 M6 — Integration and delivery**

Current status: **COMPLETE — all required automated, server, live-world, UI, lifecycle, loading, unresolved-loot and performance gates are PASS.**

Final playtest version: **0.9.1**

Final implementation/test head before evidence-only closure: `f013e6a5731e9566cbe7d83e1aca03fe4ecaee93`

## Acceptance audit

`ACCEPTANCE_MATRIX.md` maps all 96 R2 acceptance IDs (B/Q/S/C/F/E/P/H/O) to targeted automated evidence, prior owner evidence, or M6 live/server/profile fixtures.

The audit found that most classification, arithmetic, state-machine, serialization, protocol, configuration, bounds and isolation cases already had targeted automated coverage. M6 closed the remaining operational gaps with an integrated Ledger/UI pass, real unresolved-loot and unloaded-fringe fixtures, save/restart/reload, dedicated-server startup/reload, and measured representative/dense performance.

`LIVE_VALIDATION.md` remains the reproducible owner procedure used for the final live pass.

## Final 0.9.1 automated gate — PASS

GitHub Actions run **34733522239** on exact `0.9.1` implementation/test head `f013e6a5731e9566cbe7d83e1aca03fe4ecaee93` completed successfully.

- Gradle `test`: **PASS — 206/206**
- Gradle normal `build`: **PASS**
- Java: 21
- Minecraft: 1.21.1
- NeoForge: 21.1.250

The first 0.9.1 run correctly caught two screen regressions whose assertions still named pre-polish localization strings. Those tests were updated to the intended final labels; no implementation behavior changed. The exact implementation/test head above is green.

## O02 integrated Ledger/UI owner pass — PASS

Owner performed a complete live Ledger tour in Oured/Osea across:

- Overview
- Residents
- Development / Housing
- Food / Reserves
- Food / Variety
- Food / Growing
- Safety
- Comfort
- Commerce
- Prosperity
- History

Navigation remained on the same observation while moving through pages: visible observation age increased continuously instead of resetting on local tab/subpage changes. This is live evidence that Ledger navigation remained cached/local rather than silently rescanning.

Displayed values were internally consistent across Housing, Food, Variety, Growing, Commerce and Prosperity. Prosperity's point-contribution presentation and wrapped authority message remained correct.

The 0.9.0 integrated sweep found four non-functional long labels. 0.9.1 shortened only those English presentation strings. Owner then visually confirmed the exact final 0.9.1 labels render cleanly at the practical test GUI scale:

- Food Variety: `Food Groups`
- Food Growing: `Current crop counts only.`
- Safety: `Block-light check only.`
- Commerce: `Profession counts only.`

Result: **PASS.** No clipping/overlap remains in the four targeted final labels.

Some long dynamic Food-group rows can still ellipsize by design when the actual data text exceeds a book column; that is normal bounded presentation rather than the fixed explanatory-label defect addressed by 0.9.1.

## B08 unresolved-loot non-mutation — PASS

Owner created a controlled unresolved chest at block `112 -60 206` using vanilla village plains-house loot table `minecraft:chests/village/village_plains_house` and did not open the chest.

Before the Ledger observation, `/data get block 112 -60 206 LootTable` showed the unresolved loot table. Hometown's Food view then reported:

- `PARTIAL DATA`
- `Unopened loot storage`
- known stores/nutrition only

After the Ledger observation, the same `/data get` command still returned `minecraft:chests/village/village_plains_house`.

Result: **PASS.** Hometown observed the unresolved container without rolling/materializing its loot and correctly degraded Food authority instead of treating unknown contents as empty.

## Q01/Q06 loaded-only / no-force-loading — PASS

Owner lowered client render distance and observed Osea from fringe loaded scope. The Ledger visibly changed from the fully loaded dense-town state to a partial state and explicitly displayed `Partial counts: only loaded chunks are included.`

A fresh cached profile from that partial observation reported:

- generation 10
- fresh elapsed: **2.913 ms**
- population / beds: **8 / 0**
- loaded / required town chunks: **45 / 81**
- Comfort rooms: **0 assessed / 0 attempted**
- entity work: **96 / 4096**
- shared new-block work: **14,554 / 262,144**
- Growing: 4 sections, 474 palette inspections, 14,080 positions

The owner repeated the loaded-fringe observation with the same partial-loading result. Opening Hometown did not expand town coverage to 81/81 or restore unloaded beds/rooms as if those chunks had been forced in.

Result: **PASS.** Unloaded scope produces explicit uncertainty/partial values rather than forced chunk loading or invented complete data.

## O05 performance diagnostic — PASS

M6 added `/hometown debug performance [uuid]`, a diagnostic-only view of the most recent cached normal Ledger observation. The command performs no world scan and does not advance History.

Owner-reported test machine:

- CPU: **AMD Ryzen 9 3900X**
- Minecraft/Java memory allocation: **approximately 6 GB**
- No different repository Gradle JVM setting was reported for the measured client playtests.

### Representative village — Oured

Representative fixture characteristics across samples:

- radius 64
- 3 residents / 7 enclosed beds
- 81 / 81 town chunks loaded
- 4 Food containers, 7 storage positions scanned
- 7 Comfort rooms assessed/attempted
- entity work roughly 42–56 / 4096
- shared new-block work roughly 28,836–28,840 / 262,144
- Growing: 7 sections, about 2,640 palette inspections, 25,600 positions

Four distinct fresh observations were recorded:

| Sample | Fresh elapsed |
| --- | ---: |
| 1 | 34.559 ms |
| 2 | 8.588 ms |
| 3 | 11.282 ms |
| 4 | 7.205 ms |

Average: **15.409 ms**  
Worst observed: **34.559 ms**

The R2 representative target is a typical fresh request below one 50 ms server tick. Oured therefore **PASS** with significant headroom, including the slowest observed sample.

### Dense fixture — Osea

Owner expanded temporary town Osea into a materially denser live fixture. The measured dense state included:

- 38–39 residents
- 15 beds
- 14 scanned storage positions, with 1 qualifying Food container in the captured samples
- 9 Comfort rooms assessed/attempted
- entity work about 150–177 / 4096
- shared new-block work about 15,736–15,760 / 262,144
- Growing: 4 sections, about 345–369 palette inspections, 14,080 positions
- 81 / 81 town chunks loaded

Three distinct dense fresh observations were recorded:

| Sample | Fresh elapsed |
| --- | ---: |
| 1 | 32.401 ms |
| 2 | 9.679 ms |
| 3 | 7.401 ms |

Average: **16.494 ms**  
Worst observed: **32.401 ms**

Result: **PASS.** Work remained far below configured ceilings and all three dense observations also stayed below 50 ms. The higher resident/entity load did not produce an unbounded scan or hang.

## O01/O04 dedicated-server and resource-reload smoke — PASS

Dedicated-server validation was performed on isolated branch `r2-m6-server-smoke`; that test harness is not part of production R2.

RCON-based smoke workflow run **34726154745**, job **103640502723**, completed **PASS**:

- Minecraft 1.21.1 dedicated NeoForge server started under Java 21.
- Hometown loaded server-side with no client-class crash.
- Server reached `Done`.
- Actual Minecraft `reload` was issued through local RCON and completed.
- Recipes/advancements reloaded successfully.
- Actual `stop` command shut the server down cleanly.

The later 0.9.1 change is localization/test-only and does not alter server runtime code.

## Final singleplayer save/restart/reload lifecycle — PASS

Owner confirmed the tested Hometown world and Ledger state survived:

1. normal save and full client/world exit;
2. reloading the saved world;
3. reopening the town Ledger;
4. in-world `/reload`;
5. reopening/using the Ledger after reload.

Result: **PASS.** No town identity loss, required reset, crash or reload failure was observed. This complements the earlier M5 Oured evidence for v1→v2 migration, durable History, founding preservation and no duplicate confirmed events.

## Configuration/tag delivery documentation

`docs/CONFIGURATION_AND_DATAPACKS.md` documents the supported R2 server configuration, Comfort/Food/Safety tag paths, version-1 Comfort state-predicate and custom-crop formats, reload behavior, compatibility boundary and cached M6 performance command.

## Prior milestone evidence retained

M5 owner validation supplied real-world evidence for the highest-risk persistence/History path on Oured: v1→v2 save migration, founding preservation, Prosperity arithmetic, 200-tick Population confirmation, confirmed event persistence without duplication, Housing-shortage start/resolution and normal population changes.

Earlier owner testing also exercised real Comfort, Food Variety/Growing and Commerce observations. M6's final integrated pass confirmed those systems coexist in the same Ledger and one observation generation.

## Final conclusion

**R2 M6 COMPLETE. Revision 2 COMPLETE.**

Every required R2 M6 acceptance gate represented in the acceptance matrix is backed by automated, dedicated-server, prior-live or final owner-live evidence. No required item remains FAIL or NOT RUN.

The final R2 playtest artifact is **Hometown 0.9.1**. Revision 3 may now begin on a new branch from the accepted R2 head after the normal milestone integration/merge step.
