# Vico fork migration to v3.3.1 — Phase 1 synthesis

Research complete, 2026-11-13. No code written, no branch created, nothing committed.
Sixteen agents across four stages. Everything below is evidenced in `dossiers/`; where a claim was
later overturned, `CONTRADICTIONS.md` records it.

---

## 1. The answer in brief

**The migration is structurally easy and semantically medium-hard, and the cost sits in four files.**
`vico/compose` never moved, no file we modified was deleted upstream, eleven of our twenty-six modified
files were not touched upstream at all, and meApp already clears every toolchain floor with headroom.

What is hard is that upstream spent 236 commits rewriting **exactly the surfaces our fork exists to
modify** — `CartesianChartHost`'s parameter list and sizing model, `VicoScrollState`'s update path,
`Modifier.kt`'s gesture pipeline, and `CartesianChart`'s measure/prepare split.

**Estimate: 52–98 days, one developer** (~11–20 weeks), excluding the upstream PRs. Four phases carry
nearly all the uncertainty: the host core, the line layer, the gesture plumbing, and adjudicating the
golden-image diffs.

---

## 2. Decisions locked

| | |
|---|---|
| **Target** | upstream `v3.3.1` = `3d675ccc7`, 2026-08-28 — the last stable tag, not master |
| **Strategy** | **minimise the fork** — prefer an upstream extension point over patching their file; move behaviour into meApp where it can live as consumer code |
| **Fork point** | `e67e49520`, 2026-03-26 = upstream `v3.1.0-alpha.3` + 8 |
| **New version** | `5.0.0`, with `UPSTREAM_BASE = "3.3.1"` stamped into the POM |

`3.3.1-gg.1` was considered and rejected: Gradle orders it **below** 4.0.46, so any future dynamic
range would never offer it, and it reads as "equals upstream 3.3.1", which it does not.

---

## 3. Disposition — ~89 adaptations

**87 catalogued adaptations, 105 disposition rows** (eleven split into sub-rows at Stage 3):
40 RE-APPLY · 20 DELETE · 17 MOVE · 17 RE-DERIVE · 6 DROP · 5 REPLACE. **No row is unassigned.**

The six DROPs are `S1A-08c` plus `L16` and four never-reinstated build items — all "do not apply this
hunk", none of them shipped behaviour. **Nothing is dropped on the grounds that upstream now does it**,
which is the shape C1 forced: a confident DROP on exactly those grounds would have removed the flagship
feature. Every real reduction is a **MOVE** (relocated to meApp, behaviour unchanged) or a **DELETE**
(dead, inert or wrong).

Final plan, superseding `dossiers/S5-B-plan.md`: **`PLAN.md`**.

Because the port is a **forward hand-apply**, DROP and DELETE are "do not apply this hunk" — they cost
bookkeeping, not code. **The danger is a partial apply of a coupled pair; there are 14 must-land-together
sets**, headed by the margin halving and the marker's compensating helper.

**Files that stop being patched entirely:** `CartesianLayerMargins.kt`, `CartesianDrawingContext.kt`,
`CartesianChartModel.kt`, `Defaults.kt`, `Scroll.kt`, `HorizontalAxisItemPlacers.kt`*,
`vico/compose/build.gradle.kts`. Deleted outright: `ListItemPlacer.kt`, `MonotoneInterpolator.kt`,
`InterpolationType.kt`, `SnapFlingBehavior.kt`. `AxisManager.kt` drops to one added method.
*(`HorizontalAxisItemPlacers.kt` is qualified — see S5-A F1.)*

**The two biggest conflict collapses:**
- the ~180-line scrub state machine leaves `Modifier.kt` for its own file — a 234-line conflict becomes ~5
- `yTransform` goes from ~130 lines inside the two hottest functions to ~15, via a one-method seam

**The fork does not shrink. The patch surface does.** Our inventions — scroll-aware Y ranging and the
scrub marker — have no upstream analogue and will not get smaller. They move out of the library instead,
where they cost nothing at the next upgrade.

---

## 4. Defects found in code shipping today

Five, none of them the thing we were looking for:

1. **`marker.drawOverLayers` runs twice per frame.** Upstream calls it once; the marker cannot tell which
   pass it is in. Doubles translucent guideline alpha and runs `yLabelCallback` twice every frame.
2. **The marker-target sort was deleted on a false premise.** We believed the map was a sorted `TreeMap`;
   `10fa978bb` had already made it `mutableMapOf`, and upstream still sorts every frame at v3.3.1. The
   early-break nearest scan is wrong on any chart whose layers contribute different x sets — **the baby
   chart**. The adversarial pass confirmed that scan is live, called per gesture event.
3. **Margin reservation is a quarter of the stroke width, not half.** `14542561e` added the compensating
   double; `a2a9a7690` reverted it and never restored it.
4. **`series.hashCode()` runs every frame, O(n) over the whole series** — for TOTAL, every reading ever
   recorded. Replaceable with meApp's existing `MetricBasis.version` for O(1).
5. **`allMarkerTargetXValues` is size-keyed**, so it is stale for most of a scroll — targets come from the
   visible points, which slide while the count stays constant.

**Three documented behaviours do not exist in the code:** `isSnapping` suppression (written, never read —
and restoring it would now be *wrong*, adding a 500 ms axis lag), `ScrubMarkerController(scrollState)`
(accepted, never read), and FEATURE-4/7/8's stale tables. Eight further doc/code disagreements are listed
per cluster.

---

## 5. Risk register — the silent ones

Almost every failure mode in this migration is silent. Ranked:

| Risk | Why it is silent |
|---|---|
| **`decorations` instability** (S5-A F3) | Three MOVEs land as `decorations` entries and C-2 adds a `layerPadding` lambda. Both are `rememberCartesianChart` keys **and** `CartesianChart.equals` terms, and the host's range-seeding `remember` is keyed on that equality. An unstable list or capturing lambda re-seeds the range every recomposition — **the MOB-3494 defect shape**. Created *by* the minimise-the-fork strategy. No cluster owned it. |
| **`MonotoneInterpolator.visiblePadding`** | Must be `1` (derived twice, independently). At `0` the two edge segments fall back to one-sided tangents: no exception, a small kink that *changes as you scroll* — **invisible on any static chart, so no golden catches it.** |
| **`contributesYRange`** | A defaulted parameter replacing an invariant currently enforced structurally. Omit it and meApp compiles clean and is silently wrong — a pulse of 60 against a weight of 150 opens the axis at zero. **Make it non-defaulted.** |
| **The 64 dp axis cap** | Upstream changed `canvasSize.height / 3` to a flat `64.dp`. It tightens on every meApp surface (Tablet ~133→64, Phone ~92→64, Snapshot ~76→64). Our layout shifts even if we adopt nothing. |
| **Single-layer compositing** | All series now composite onto one offscreen layer. Hits the **baby chart** specifically: 7 percentile lines at 40% alpha behind the data line. |
| **`A4a`'s second half** | The fork deletes upstream's `fullXRange` guards as well as adding an epsilon filter. A delegating wrapper can only subtract, never restore. |
| **`L5`'s two call sites** | `rememberFollowing` has two callers with *opposite* `isStable` requirements; treating them as one wrapper silently regresses the percentile band. |

**One loud failure, the only one:** `CartesianChartHost.kt:40` imports `Defaults.CHART_HEIGHT`, which does
not exist at v3.3.1. Unresolved reference, not a warning.

---

## 6. The plan — 18 phases

Hard constraints, in force throughout:

- **T0 goldens, captured from the current 4.0.46, gate all migration work.** meApp has no visual
  regression testing at all; the chart is a pure composable, so ~36 goldens are cheap.
- **Never rebase or cherry-pick the 105 commits.** Upstream round-tripped 26 files through a transient
  `vico/shared` tree; four of ours went with them, so git sees delete-then-add. Port as a hand-applied
  `git diff e67e49520..HEAD -- <file>`, per file.
- **Normalise formatting before any merge.** Roughly half our added lines on the two hottest files are
  ktfmt churn; upstream moved 0.59 → 0.64 and reformatted 14 files.
- **Reductions come after parity is proven**, except where the code sits in a file that must otherwise be
  hand-merged against upstream churn — then move it early and golden it on landing.
- **Develop in a second clone** (`~/Projects/vico-migration`), never a worktree, with meApp's composite
  build switched by a gitignored property. The shared checkout stays the read-only reference.

Phases, with the four uncertain ones marked: P0 settle blockers 2–4d · **P1 T0 goldens 4–7d** ·
P2 build rebase 1–3d · P3 formatting 1–2d · P4 no-ops + the three defect fixes 2–4d · P5 clean grafts 1–2d ·
P6 `VicoScrollState` 2–3d · **P7 host core — flagship goes live 6–12d** · P8 hot-file reductions 4–7d ·
P9 axes 3–5d · **P10 line layer 5–9d** · P11 start-of-chart unit 2–4d · P12 draw order 1–3d ·
P13 marker 5–9d · **P14 gesture + host plumbing 5–9d** · **P15 parity adjudication 3–6d** ·
P16 post-parity reductions 3–5d · P17 publish 5.0.0 + meApp cutover 2–4d · P18 upstream PRs (out of scope).

---

## 7. Settle before starting — 7 blockers

Three would stop work on day one:

1. **Is vico Paparazzi-clean at 4.0.46?** Blocks T0, therefore everything. Upstream ships 11 committed
   Layoutlib PNGs, which is strong evidence but not proof for us. One spike, three pass criteria — the
   third (`verifyPaparazziDebug` green on a clean CI runner) is the one that usually fails.
   Note: **coroutines do not run under preview rendering** — the model must be pushed synchronously.
2. **Does the composite-build substitution survive v3.3.1's layout?** Three AGP versions in one composite.
   Blocks the whole iteration loop. Three ordered fallbacks documented.
3. **Kotlin context parameters at 2.4.10.** Upstream relies on them with no opt-in flag. Settle before
   porting, or a toolchain failure reads as a port failure.

Then: the `flingBehavior` seam (resolved — Shape A), CF-1 (resolved — A7b stands, H-7 DELETE,
S1A-08c DROP), the `drawLabels` split (must be decided *before* the marker subclass is written, because
the method split is the fix), and the start-gap arithmetic — which is a **product** decision, not a spike.

Five further checks can only be answered against a **running, un-migrated 4.0.46 build**. They sit in four
different dossiers and none of them says so; all five are scheduled beside the T0 goldens.

---

## 8. What we give back upstream

Four candidates, in order of how easy they are to land:

1. **The `TextComponent` one-token fix.** `append(text)` with a nullable `text` two lines after a non-null
   `measuredText` is computed. `Appendable.append(null)` writes `"null"` — so **every `Size.Auto` axis in
   vico sizes its gutter four characters too wide.** No design argument attached. Send this first.
2. **Three marker draw-phase hooks** — `drawResting`, `onScrollSettled`, `isInterpolated`. All defaulted,
   all additive, one coherent proposal: explicit draw phases plus an idle phase.
3. **Open `getVisibleXRange()` / `getFullXRange()`** — one keyword each, no behaviour change. Every
   drawing extension needs them.
4. **A `ranges` seam on the two public `CartesianChartHost` overloads.** `updateRanges` is `internal` and
   ranges are recomputed only on a data push, which is the root cause of our two largest patches.
   **~6 lines of theirs would replace ~250 of ours.** Needs a spike first.

---

## 9. How to read the dossiers

**Start with `CONTRADICTIONS.md`.** It records eight places this analysis was wrong, four of which
arrived wrapped in correct commit SHAs and precise line numbers. The adversarial pass over every
DELETE/MOVE/REPLACE returned **5 WRONG, 3 UNPROVEN, 21 CONFIRMED**, and lists what it deliberately did
*not* attack so that silence is not read as clearance.

`dossiers/S1-*` catalogue our 105 commits (including the 34 undocumented ones, reverse-engineered).
`S2-*` study the 236 upstream commits. `S3-*` assign the verdicts. `S4-A` the verification gap.
`S5-A` the adversarial pass, `S5-B` the merged plan and estimate.
