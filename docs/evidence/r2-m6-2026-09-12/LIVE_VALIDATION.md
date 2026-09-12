# R2 M6 live validation procedure

Target playtest artifact: **0.9.0** (`build/libs/hometown-0.9.0.jar`)

Do not mark these checks PASS until the result was actually observed in Minecraft. Use a backup/copy of an existing Hometown world for destructive or migration-sensitive checks.

## A. Integrated Ledger and UI — O02

Use the existing Oured settlement as the representative village.

1. Open the Ledger normally and record its observation age.
2. Visit Overview, Residents, Development/Housing, Food/Reserves, Food/Variety, Food/Growing, Safety, Comfort, Commerce, Prosperity, Prosperity component/detail views, and History/pagination.
3. Exercise available page/detail arrows.
4. Repeat the busiest views at each practical GUI scale exposed by the current client/display.
5. Look specifically for clipping, overlap, tiny/blurred text, broken arrows/hitboxes, stale companion data, or a subpage click resetting the observation age.

Expected: all pages remain usable; page/detail navigation reuses the opened observation and does not act like a fresh scan.

## B. Cross-system regression spot-check — B02/B03/B04/Q04/S09/C10/P14

Use reversible changes in Oured:

- add/remove an enclosed bed;
- open/close the tested room door and break/repair a tested window;
- add/remove a local light source;
- add/remove a known Comfort furnishing;
- add/remove stored food;
- change one villager profession or population when convenient.

Expected: only the logically dependent observations move. In particular, furnishings/light do not change Housing Supply or Privacy; crops do not add Food Reserves; Commerce does not redefine overall Population; Prosperity remains purely observational and produces no world/gameplay effects.

## C. Unresolved loot — B08

Use a genuinely unresolved loot-table container in loaded in-bounds scope.

1. Do not open the container.
2. Capture its loot-table/unresolved state if available through the chosen fixture/debug method.
3. Open the Ledger and run the relevant read-only Food debug command.
4. Confirm the container is still unresolved and its contents were not materialized by Hometown.
5. Open the container normally in Minecraft so vanilla resolves it.
6. Reopen the Ledger after the cooldown and confirm its qualifying food can now be observed.

Expected: Hometown never rolls loot; normal vanilla opening can resolve it for a later observation.

## D. Loaded-only / no force-loading — Q01/Q06

At a settlement edge, arrange for at least one in-bounds fringe chunk to be unloaded while the player remains able to open the Ledger from loaded town scope.

1. Record the relevant loaded-scope/debug information before the fresh request.
2. Open the Ledger and exercise the applicable debug views.
3. Repeat the normal request after cooldown.
4. Confirm known loaded observations remain visible with partial/unloaded reasons as applicable.
5. Confirm Hometown did not expand loaded town coverage or create a Hometown chunk ticket merely to finish the observation.

Expected: unavailable fringe scope produces uncertainty, not forced loading or invented emptiness.

## E. Final save/restart/reload lifecycle — B01/H01/H15/O04

1. Confirm Oured identity and retained History before shutdown.
2. Save and fully exit the world/client.
3. Reload the same world and reopen the Ledger.
4. Confirm UUID/name/town identity and retained History remain, with no duplicate founding/confirmed event.
5. Execute `/reload`, wait for completion, then obtain another fresh Ledger observation.

Expected: state survives; rules/resources reload without a crash, mixed revision, duplicate History, or required world reset.

## F. Representative performance — O05

Performance command is operator-only and reads the most recent cached normal Ledger observation. The command itself performs no world scan.

1. In normal Oured, wait until the prior Ledger cooldown has expired.
2. Open the Ledger normally once and close it.
3. Within 60 seconds run:

   `/hometown debug performance`

4. Record the entire output plus the test machine CPU/model (and JVM memory override if different from repository defaults).
5. Repeat for several fresh opens separated by the cooldown so one warm/cold outlier is not mistaken for a typical result.

Record: town coordinates/bell/radius, population, enclosed beds/rooms, loaded/required chunks, Food containers/storage scanned, entity work, shared block work, Growing work and fresh-observation elapsed milliseconds.

Target: a representative/typical fresh request should be below one 50 ms server tick. A result above target is an investigation trigger, not permission to raise budgets.

## G. Deliberately dense performance fixture — O05

Use a separate temporary test town/world rather than damaging Oured. Create a deliberately dense but valid settlement with substantially more residents/rooms/storage/crops/furnishings/entities than the representative village, while keeping its relevant chunks loaded.

Run the same fresh-open → close → `/hometown debug performance` procedure and record the complete output and fixture description/counts.

Expected: work remains bounded by configured ceilings; if a ceiling is reached, affected modules degrade to the specified PARTIAL/UNAVAILABLE behavior rather than forcing chunks or hanging the server. Dense elapsed time is measured and investigated rather than assumed to meet the representative <50 ms target.

## Result handoff

Report each section as PASS / FAIL with screenshots only where useful. For performance, copy the command text output exactly. Any functional/code/data fix after this 0.9.0 playtest requires a 0.9.1 patch version before retest.
