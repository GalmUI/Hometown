# R3 M2 / 0.13.0 Notice Board Acceptance Matrix

| ID | Requirement | Automated | Owner live | Status |
| --- | --- | --- | --- | --- |
| NB-01 | Notice Board sign grammar is narrow, case-insensitive, and whitespace-tolerant | `NoticeBoardFoundationTest` | — | NOT RUN |
| NB-02 | Board persistence is gated by the Town Hall-granted Notice Board unlock | `NoticeBoardFoundationTest` | — | NOT RUN |
| NB-03 | Board marker persists through save/load and replacement stays singular/idempotent | `NoticeBoardFoundationTest` | Reopen world and inspect board | NOT RUN |
| NB-04 | Linked Ledger + range + town containment + colors + unlock gate registration | service path/build | Register in test town | NOT RUN |
| NB-05 | Valid current board blocks replacement; unavailable board fails closed; invalid board can be replaced | service path/build | Break/change/unload board and attempt replacement | NOT RUN |
| NB-06 | Successful registration visibly recolors the interacted sign face | service path/build | Observe registration sign color | NOT RUN |
| NB-07 | Normal right-click opens Notice Board Facility Detail with active/unavailable semantics | snapshot/build | Open board normally | NOT RUN |
| NB-08 | Administration distinguishes board unlock, establishment, and active validity | `NoticeBoardFoundationTest` | Open Administration progression page | NOT RUN |
| NB-09 | Existing R3 M1 facilities/operations regressions remain green | full `test` | Representative M1 smoke test | NOT RUN |
| NB-10 | Dedicated build/package succeeds at version 0.13.0 | GitHub Actions `build` | Load produced jar | NOT RUN |

0.13.0 is not accepted until automated CI is green and the owner live checks above have been exercised in a real world.
