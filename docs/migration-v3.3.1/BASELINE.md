# Vico fork migration — shared baseline (Phase 1)

Repo: /Users/selvakumar/Projects/vico   (fork of patrykandpatrick/vico, origin = dmdbrands/vico)

## Refs you will need
| Name | Ref | What it is |
|---|---|---|
| FORK_POINT | `e67e49520` | merge-base. upstream v3.1.0-alpha.3 + 8, 2026-03-26 |
| OURS | `HEAD` (branch `MOB-2953-week-range-spans-eight-days`) | our production fork, published as 4.0.46 |
| TARGET | `v3.3.1` = `3d675ccc7`, 2026-08-28 | the upstream release we are migrating TO (decision locked) |

- OURS is FORK_POINT + **105 commits**: 34 library files (8 new, 26 modified), +3,736 / -335.
- TARGET is FORK_POINT + **236 commits**.
- All 26 files we MODIFIED exist at the same paths in v3.3.1. Only our own 8 NEW files are absent.
- **CORRECTED (S2-A):** the big module consolidation (`core`, `multiplatform*` folded into `compose`/`views`)
  happened in `161ccf051`, 2026-01-04 — an ANCESTOR of our fork point, not inside the range. Directories such
  as `vico/core` visible in the working tree are stale untracked leftovers, not tracked modules.
  Inside `e67e495..v3.3.1` the only module changes are: `vico/views` DELETED (`55cdd2b3b`) and
  `vico/compose-glance` ADDED (`f2923084f`). `vico/compose` neither moved nor was renamed, and
  **no file under `vico/compose/src/commonMain` was deleted in the range.**
- **PROCESS HAZARD (S2-A §1.1):** between `d4a7b65b9` and `8be98e01e` upstream moved 26 files out to a transient
  `vico/shared` tree and back. Four of OUR 26 modified files round-tripped through it
  (`CartesianChartRanges`, `MutableCartesianChartRanges`, `CartesianLayerMargins`, `HorizontalAxisItemPlacers`).
  **Never `rebase --onto v3.3.1` or cherry-pick the 105 commits.** Port as a hand-applied
  `git diff e67e49520..HEAD -- <file>` per file.
- Consumer: **meApp only** (`/Users/selvakumar/Projects/meApp-bug/Android`), 23 files, 48 distinct
  vico imports, artifact `com.dmdbrands.lib:compose-android:4.0.46`.

## Upstream churn vs ours, on the files we modified
Hot on both sides (the real work):
  HorizontalAxis (ours 152+/149-, theirs 277+/77-) · CartesianChartHost (454+/8-, 196+/94-)
  CartesianChart (203+/12-, 228+/18-) · VicoScrollState (168+/9-, 182+/41-)
  VerticalAxis (211+/25-, 175+/29-) · LineCartesianLayer (320+/54-, 130+/35-)
  Modifier (234+/27-, 121+/25-) · DefaultCartesianMarker (288+/19-, 39+/25-)
Upstream never touched (graft clean): Scroll.kt · CartesianMarker.kt · CartesianMarkerController.kt ·
  AxisManager.kt · MutableCartesianChartRanges.kt · CartesianLayerMargins.kt · FadingEdges.kt ·
  CartesianChartRanges.kt · LineCartesianLayerMarkerTarget.kt · CartesianDrawingContext.kt

## Documentation state
`docs/FEATURE-1..10-*.md` document the EARLY work only. The **34 most recent commits**
(`MOB-2953:` and `MOB-3494:` prefixes, Aug–Oct 2026) have **NO doc** and must be reverse-engineered
from the diff. Treat that reconstruction as a first-class deliverable, not a footnote.

## Decisions already locked (do not re-litigate)
1. Target is **v3.3.1**, the last stable tag — not master tip.
2. **Minimise the fork.** Prefer an upstream extension point over patching an upstream file; prefer
   moving meApp-specific behaviour out of the library into meApp. A smaller fork surface is worth a
   slower port. Say so whenever our change could become a non-patch.

## HARD RULE FOR EVERY AGENT
This repo is a single shared checkout that other agents are reading concurrently.
**READ ONLY. Never `git checkout`, `switch`, `stash`, `reset`, `rebase`, `cherry-pick`, or write to
the working tree.** Read other refs with `git show <ref>:<path>` and `git diff <refA>..<refB>`.
Write your output ONLY to the dossier path you are given.
