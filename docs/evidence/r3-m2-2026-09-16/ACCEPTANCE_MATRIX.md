# R3 M2 / 0.13.0 Notice Board Acceptance Matrix

Automated checkpoint: GitHub Actions run **35056996223** on implementation commit `9670327d47ecd267a9ccf6c715e45124ac6ab0eb` completed successfully: Gradle `test` **PASS**, Gradle `build` **PASS**.

| ID | Requirement | Automated | Owner live | Status |
| --- | --- | --- | --- | --- |
| NB-01 | Notice Board sign grammar is narrow, case-insensitive, and whitespace-tolerant | `NoticeBoardFoundationTest` | — | AUTOMATED PASS |
| NB-02 | Board persistence is gated by the Town Hall-granted Notice Board unlock | `NoticeBoardFoundationTest` | — | AUTOMATED PASS |
| NB-03 | Board marker persists through save/load and replacement stays singular/idempotent | `NoticeBoardFoundationTest` | Reopen world and inspect board | AUTOMATED PASS / LIVE PENDING |
| NB-04 | Linked Ledger + range + town containment + colors + unlock gate registration | service path compiled in full build | Register in test town | BUILD PASS / LIVE PENDING |
| NB-05 | Valid current board blocks replacement; unavailable board fails closed; invalid board can be replaced | service path compiled in full build | Break/change/unload board and attempt replacement | BUILD PASS / LIVE PENDING |
| NB-06 | Successful registration visibly recolors the interacted sign face | service path compiled in full build | Observe registration sign color | BUILD PASS / LIVE PENDING |
| NB-07 | Normal right-click opens Notice Board Facility Detail with active/unavailable semantics | snapshot/service path compiled in full build | Open board normally | BUILD PASS / LIVE PENDING |
| NB-08 | Administration distinguishes board unlock, establishment, and active validity | `NoticeBoardFoundationTest` | Open Administration progression page | AUTOMATED PASS / LIVE PENDING |
| NB-09 | Existing R3 M1 facilities/operations regressions remain green | full Gradle `test` | Representative M1 smoke test | AUTOMATED PASS / LIVE PENDING |
| NB-10 | Dedicated build/package succeeds at version 0.13.0 | GitHub Actions `build` | Load produced jar | BUILD PASS / LIVE PENDING |

`0.13.0` is not accepted until the owner live checks above have been exercised in a real world and any discovered defects are resolved.
