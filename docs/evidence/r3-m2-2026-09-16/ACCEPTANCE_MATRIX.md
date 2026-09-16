# R3 M2 / 0.13.0 Notice Board Acceptance Matrix

Automated checkpoint: GitHub Actions run **35056996223** on implementation commit `9670327d47ecd267a9ccf6c715e45124ac6ab0eb` completed successfully: Gradle `test` **PASS**, Gradle `build` **PASS**.

Owner live checkpoint in Osea on 2026-09-16 confirmed successful Notice Board registration and color feedback, normal Facility Detail access with `Established — Active`, Town Administration status, refusal of a second board while the registered board remained valid, and successful replacement after the original board was invalidated.

| ID | Requirement | Automated | Owner live | Status |
| --- | --- | --- | --- | --- |
| NB-01 | Notice Board sign grammar is narrow, case-insensitive, and whitespace-tolerant | `NoticeBoardFoundationTest` | — | AUTOMATED PASS |
| NB-02 | Board persistence is gated by the Town Hall-granted Notice Board unlock | `NoticeBoardFoundationTest` | — | AUTOMATED PASS |
| NB-03 | Board marker persists through save/load and replacement stays singular/idempotent | `NoticeBoardFoundationTest` | Reopen world and inspect replacement board | AUTOMATED PASS / RESTART LIVE PENDING |
| NB-04 | Linked Ledger + range + town containment + colors + unlock gate registration | service path compiled in full build | Registered in Osea with linked Ledger | LIVE PASS |
| NB-05 | Valid current board blocks replacement; unavailable board fails closed; invalid board can be replaced | service path compiled in full build | Valid board refused replacement; invalidated board allowed replacement; unloaded/unavailable case not yet exercised | PARTIAL LIVE PASS |
| NB-06 | Successful registration visibly recolors the interacted sign face | service path compiled in full build | Town-color sign feedback observed | LIVE PASS |
| NB-07 | Normal right-click opens Notice Board Facility Detail with active/unavailable semantics | snapshot/service path compiled in full build | Opened `Osea — Notice Board`, reported `Established — Active` | LIVE PASS |
| NB-08 | Administration distinguishes board unlock, establishment, and active validity | `NoticeBoardFoundationTest` | Administration progression page reported Notice Board established/active while Civic Projects remained unimplemented | LIVE PASS |
| NB-09 | Existing R3 M1 facilities/operations regressions remain green | full Gradle `test` | Representative M1 smoke test on 0.13.0 | AUTOMATED PASS / LIVE PENDING |
| NB-10 | Dedicated build/package succeeds at version 0.13.0 | GitHub Actions `build` | 0.13.0 jar loaded and ran in the Osea test world | LIVE PASS |

`0.13.0` is not accepted yet. Remaining owner-live gates are save/full-restart persistence of the replacement Notice Board, the unloaded/unavailable existing-board fail-closed case, and a representative R3 M1 smoke check on the 0.13.0 build.
