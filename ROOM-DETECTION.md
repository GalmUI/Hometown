# v0.3a — Room Detection Prototype

Artifact version: `0.3.0-alpha.1`. Minecraft 1.21.1, NeoForge 21.1.250, Java 21.

## Use

Run `/hometown debug room` with operator permission level 2 or cheats. It inspects the nearest intact bed within six blocks on each axis, using only loaded chunks. The reported bed coordinates identify the selection. This diagnostic also works outside a founded town so test structures can be inspected freely. Console sources must use a player context.

Success reports enclosure, traversable interior volume, complete bed count, a representative interior position, and inspected-cell count. Failure reports a named reason and explicitly labels observed counts as partial. It does not establish that an oversized or unavailable space is physically outdoors; it establishes that enclosure could not be confirmed within the limits.

## Algorithm and boundaries

`RoomDetector` is a scan-local session over a small `WorldView` interface. It normalizes intact bed halves to their head position, selects free space above either half or beside the head, and explores six-direction connected block cells. Beds are traversable cells so furniture does not split an otherwise connected interior. The volume counts all traversable cells, including the cells occupied by bed halves; it is not a measurement of fractional cubic air volume.

Doors and trapdoors are architectural boundaries in both states. Tagged blocks and any block with nonempty collision are boundaries. This supports solid blocks, glass, panes, walls, slabs, stairs, and ordinary colliding modded blocks without material lists or house templates.

**Prototype precision:** collision seals the entire block cell. The detector does not model sub-block gaps, thin slab air spaces, or exact collision-surface connectivity. A pane or stair seals its cell; colliding furniture or foliage can also seal cells. This deliberately conservative architectural approximation should be reviewed before adding Housing mechanics. It does not require full-cube construction, rectangular rooms, furniture, windows, doors, or a particular ceiling height.

Add architectural separators through `data/hometown/tags/block/room_boundaries.json` in a data pack. The bundled tag includes vanilla doors, trapdoors, walls, fences, and fence gates. Use `replace: false` to extend it. Vanilla door/trapdoor classes also have a direct fallback to keep their semantics stable. There is no external-mod-specific compatibility code. Tag resource layout follows [NeoForge's tag-folder change for Minecraft 1.21](https://neoforged.net/news/21.0release/).

`LoadedRoomWorld` reads chunks only through `getChunkNow`. Collision-shape neighbor reads use the same guarded BlockGetter; unavailable neighbors invalidate the scan. It reads existing block entities only, and creates neither chunk tickets nor room records.

An accessible cell above the loaded column's WORLD_SURFACE height is an early proof of outdoor escape. WORLD_SURFACE includes glass and leaves, avoiding glass-roof skylight false positives. This is not the sole test: a roofed exterior or underground tunnel is still flood-filled and must terminate within the volume/distance budgets. Missing roofs/walls and awnings normally reach an outdoor column. Vast roofed terrain can instead return a limit failure.

## Limits and reuse

Tune `RoomDetector.Limits.DEFAULT` or pass explicit limits when constructing a scan:

| Limit | Default |
| --- | ---: |
| Maximum traversable room cells | 4,096 |
| Maximum horizontal displacement on either axis from the seed | 32 |
| Maximum vertical displacement from the seed | 16 |
| Maximum inspected cells across the session, including boundaries | 65,536 |

Every flood fill has visited positions; only traversable cells expand the queue. The session caches world cells and successful room membership. Each successful result is immutable and includes normalized bed heads. Requesting another bed in that room returns the same result without repeating the fill. The representative is the minimum visited interior BlockPos using Minecraft's ordering. Future consumers can use the results directly without rescanning. Failed results are partial and must not be counted as confirmed rooms.

Discard the detector at the end of the synchronous scan. Opening/closing doors or changing blocks between separate commands is rediscovered. There are no persistent room IDs, scheduled scans, per-tick hooks, Ledger requests, scores, capacity rules, bonuses, penalties, assignments, or other Development changes.

## Validation

61 tests pass: all 37 previous regressions plus 20 room tests and 4 command tests. Tests exercise real Minecraft block states/collision shapes with a mocked loaded-chunk world; they are not an in-game server benchmark or screenshot playtest.

Covered: ordinary enclosed house; open/closed doors; roof opening; awning and tree canopy; carved underground cave; glass/panes/walls; two and four shared beds; separate bedrooms with an open door; large open-plan interior; oversized hall; broken wall; open/closed trapdoor roof; slab/stair roofs; unloaded chunks; horizontal/vertical/work budgets; invalid beds/no start; custom boundary tag; fresh-session building changes; and an infinite roofed tunnel without sky escape. Command tests verify permission denial, success text, partial failure text, and missing-bed feedback.

All 32 main sources compile with Java 21; there are only the two existing client annotation deprecation warnings. Standard `gradlew.bat build --offline` was retried and failed before mod compilation in Minecraft artifact extraction with Windows `AccessDeniedException`. The delivered JAR was compiled and packaged through the same direct JavaCompiler route used for the prior working mod. Archive integrity, version metadata, tags, translations, and dependency exclusions are checked during packaging.

Remaining manual checks: repeat the requested structures in a running Minecraft instance, especially mixed slab/stair and modded construction; inspect server responsiveness during the large-hall command. Automated tests verify bounded work, not real server frame times.
