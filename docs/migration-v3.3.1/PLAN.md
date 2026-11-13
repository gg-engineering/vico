# PLAN — vico fork → upstream v3.3.1, published as 5.0.0

**Status: authoritative.** This document replaces `dossiers/S5-B-plan.md` (written 10:51), which was
produced before `dossiers/S5-A-adversarial.md` (10:57) and `dossiers/S3-E-host-orphans.md` (11:09)
landed. Where this file and S5-B disagree, this file wins; S5-B is kept only as the record of how the
sequencing was first derived.

**What this document does.** It sequences and reconciles verdicts. It does not re-decide them. A
verdict is overturned here only where two dossiers conflict and one had to be chosen; every such case
is marked **[PLAN call]** with the reason and the loser. Assumptions are marked **[ASSUMPTION]**.
Facts re-checked against source for this document are marked **[verified]** with the command.

**Locked inputs** (`BASELINE.md`, `SYNTHESIS.md`, `CONTRADICTIONS.md` C1–C8 — not re-litigated):
target `v3.3.1` = `3d675ccc7`; **minimise the fork**; new version `5.0.0` with `UPSTREAM_BASE = "3.3.1"`;
fork point `e67e49520`; consumer is meApp only.

**Hard constraints, in force throughout.**
1. **T0 goldens captured from the current 4.0.46 gate all migration work.** No library edit before P1 is green.
2. **Never rebase or cherry-pick the 105 commits.** Port as hand-applied `git diff e67e49520..HEAD -- <file>`, per file. (Four of our files round-tripped through upstream's transient `vico/shared` tree.)
3. **Formatting normalised before any merge** (P3), not `diff -w`.
4. **Reductions after parity**, except where the code sits in a file that must otherwise be hand-merged against upstream churn — then move it early and golden it on landing.
5. **Develop in a second clone** (`~/Projects/vico-migration`), never a worktree. `/Users/selvakumar/Projects/vico` stays read-only.
6. **Every phase ends with the build green and the chart rendering**, so work can stop or be reviewed at a boundary.

**What changed from the draft, in one list.**
- `A4a` + `A5` are one feature and move together into P11 (S5-A F1). `A4a`'s MOVE is now conditional.
- CF-1 is closed: `A7b` DELETE stands, `H-7` is DELETE, `S1A-08c` flips RE-APPLY → **DROP** (S3-E §3, S5-A F5). BL-5 is gone.
- BL-4 is closed: the `flingBehavior` seam survives as Shape A; snap leaves the library and `SnapFlingBehavior.kt` is **deleted**, not renamed (S3-E §4). BL-4 is gone.
- Eight host orphans have verdicts (`C-7`, `H-4`…`H-10`). `C-7` is RE-APPLY, not an open question — P13 is unblocked.
- `H-10` is the project's **only loud failure**: `Defaults.CHART_HEIGHT` does not exist at v3.3.1. **[verified]** `git show v3.3.1:…/common/Defaults.kt | grep CHART_HEIGHT` → nothing; only `CARTESIAN_CHART_AREA_HEIGHT: Float = 192f`. The dangling import is at `HEAD …/CartesianChartHost.kt:40`.
- `L1` has **three** meApp call sites. **[verified]** `grep -rn "Interpolator.monotone()" app/src` → `PercentileBand.kt:39`, `DataLine.kt:47`, `PrimaryLayer.kt:50`.
- `A11` touches `internal inline val Float.half` **[verified]** (`v3.3.1 …/common/Math.kt`); `B-7`'s internal-helper list is **7**, including `Rect.getEnd` **[verified]** (`v3.3.1 …/common/Rect.kt`).
- The `decorations` instability risk (S5-A F3) is now an owned precondition on `A1` + `A6` + `H-3` + `C-2` jointly, with an instrumentation gate.
- `C-1` is blocked by `A3a` as well as `C-2` (S5-A F4).
- `L5` is amended: `rememberFollowing` has two call sites with **opposite** `isStable` requirements.
- `contributesYRange` is **non-defaulted** on the constructor.
- The consumer-API coupling surface is **40 rows**, not 22.
- C2's "WEEK on `xSnapStep`" split is **rejected** (S3-E §4): two snap mechanisms with different animation feel on two tabs of one screen.

---

# §1 Coverage audit

Every adaptation in the five catalogues — S1-A (13), S1-B (12), S1-C (12), S1-D (16), S1-E host (22)
and S1-E build (12) = **87 catalogued adaptations**, dispositioned as **105 rows** because eleven
catalogue items were split by the Stage-3 dossiers (`A2→a/b/c`, `A3→a/b/c`, `A4→a/a′/b`, `A6→6/6′`,
`A7→a/b`, `B-3→a/b`, `B-4→a/b/c`, `L2→a/b`, `L12→a/b`, `L14→a/b`, `S1A-05→a/b`, `S1A-08→a/b/c`,
`S1A-09→a/b`).

**[ASSUMPTION / correction to the brief]** The brief says "S1-E 21 host". The catalogue's §1.0 table
actually carries **22** rows: `H-1`…`H-10` (10), `C-1`…`C-8` (8), `M-1`…`M-4` (4). All 22 are
audited below. If the intended 21 excluded `C-8` (because S3-B re-dispositions it as `D-2`), the row
is here under both names and nothing is lost either way.

**Unassigned: none.** Every row has a verdict and a phase.

## 1.1 Totals by verdict

| verdict | count | share |
|---|---|---|
| **RE-APPLY** | 40 | 38% |
| **DELETE** | 20 | 19% |
| **MOVE** | 17 | 16% |
| **RE-DERIVE** | 17 | 16% |
| **DROP** | 6 | 6% |
| **REPLACE** | 5 | 5% |
| **Total** | **105** | |

Three further rows are carried in the plan but are **not** catalogue adaptations (`D-1` and `D-2`,
both defects, both DELETE; `E-3`, undecided). They are listed at the end of §1.2 so nothing in the
plan is unaccounted for; they are excluded from the 105.

By catalogue: S1-A 17 rows · S1-B 15 · S1-C 20 · S1-D 19 · S1-E host 22 · S1-E build 12.

**The DROP count has changed and it matters.** SYNTHESIS §3 says "**0 DROP**". That was true before
S3-E. `S1A-08c` is now the first library DROP in the project (the `initialScrollHandled` widening has
no consumer once `H-7` goes). The other five are `L16` (a parameter added and withdrawn — no code
exists at HEAD) and the four "do not reinstate" build items (`bld-B5/6/7/9`). The *spirit* of "0 DROP" survives intact: **no shipped library behaviour is
dropped on the grounds that upstream now does it.** All five DROPs are dead or abandoned code.

Because the port is a forward hand-apply, **DELETE and DROP are "do not apply this hunk"** — 26 of
the 105 rows (20 + 6) cost bookkeeping, not code. The hazard is never *doing* a DELETE; it is doing **half** of
a coupled pair (§2).

## 1.2 The audit table

Legend — **Phase**: the phase in §3 where the row is executed. "P4 (bk)" = recorded as bookkeeping in
P4, physically realised by not applying a hunk when that file is merged in the named later phase.
**Source**: the dossier the verdict comes from; `→` means a later dossier amended an earlier one and
the later verdict is the one taken.

### S1-A — scroll, scroll state, dynamic Y/X range (13 catalogue items → 17 rows)

| id | short name | verdict | phase | source |
|---|---|---|---|---|
| S1A-01 | `ScrollAwareRangeProvider` core | RE-APPLY (members widened public) | P7 | S3-A |
| S1A-02 | `Window` / `WindowKey` in `ExtraStore` | RE-APPLY | P7 | S3-A |
| S1A-03 | `seedFromWindow` + cache/window guards | RE-APPLY | P7 | S3-A |
| S1A-04 | `WindowSettleSignal` / `settleEpoch` | RE-APPLY | P5 | S3-A |
| S1A-05a | `SnapBehaviorConfig` + `ChartSnapFlingBehavior` + remember fn | **MOVE** to meApp; library file **deleted** | P16 | S3-A → S3-E §4 (deletion, not rename) |
| S1A-05b | `isSnapping` flag | DELETE | P4 (bk) | S3-A, C5 |
| S1A-06 | scroll⇄X helpers + `visibleXRange` | RE-APPLY, 3 members `internal`→`public` | P6 | S3-A |
| S1A-07 | scrub freeze (`isScrollFrozen` etc.) | RE-APPLY | P6 | S3-A |
| S1A-08a | re-apply `initialScroll` on `maxValue` change | DELETE | P6 (bk) | C-resolved-by-convergence |
| S1A-08b | `key` on `rememberVicoScrollState` | MOVE to meApp (`key(…) { … }`) | P16 | S3-A (gated on P1's A→B→A baseline) |
| S1A-08c | `initialScrollHandled` `private`→`internal` | **DROP** (was RE-APPLY) | P6 (nothing to do) | **S3-E §3 / S5-A F5 over S3-A** |
| S1A-09a | `getVisibleAxisLabels` + retained `drawingContext` | RE-APPLY (scoped) | P7 | S3-A |
| S1A-09b | `getInterpolatedYValues` | MOVE to meApp | P8 | S3-A (pulled forward, see CF-3) |
| S1A-10 | `Scroll.Absolute.xBehindStartInset` | MOVE to meApp | P16 | S3-A |
| S1A-11 | fading edges sized from the layer's padding | RE-APPLY | P5 | S3-A |
| S1A-12 | `CartesianChartRanges.getTargetYRange` | RE-APPLY | P5 | S3-A |
| S1A-13 | `tryUpdateX` + `NeutralYRange` (both sites) | RE-APPLY | P5 | S3-A |

### S1-B — markers, scrubbing, marker label (12 catalogue items → 15 rows)

| id | short name | verdict | phase | source |
|---|---|---|---|---|
| B-1 | `ScrubMarkerController` class | RE-APPLY; **DELETE** its unread `scrollState` param | P14 | S3-B |
| B-2 | `interaction` 3rd param on the callback | RE-APPLY | P14 | S3-B |
| B-3a | `acceptsGestures` + its `Modifier.kt` guard | DELETE (superseded) | P14 | S3-B §3 (confirmed S5-A) |
| B-3b | `rememberNonInteractive` / `NonInteractiveMarkerController` | MOVE to meApp (`acceptsLongPress = false`) | P14 | S3-B |
| B-4a | `CartesianMarker.drawResting` + dispatch | RE-APPLY (patch) | P13 | S3-B |
| B-4b | resting-label body (`restingLabel`, formatter, anchor, `labelGap`) | MOVE to a meApp subclass | P13 | S3-B |
| B-4c | `ensureVerticalValuesAtLeast` | DELETE; use upstream's `ensureValuesAtLeast` | P4 (with A12) | S3-B (confirmed S5-A) |
| B-5 | `CartesianMarker.onScrollSettled` | RE-APPLY (patch) | P13 | S3-B (re-opened at P16, CF-4) |
| B-6 | nullable `label: TextComponent?` | REPLACE (reverts to RE-APPLY if the subclass dies) | P13 | S3-B + S3-E §5.2 |
| B-7 | horizontal crosshair + label | MOVE to the subclass (**7** internal helpers) | P13 | S3-B → S5-A F8 |
| B-8 | `yLabelCallback` | MOVE to the subclass | P13 | S3-B (gated on U-1, measured in P1) |
| B-9 | `contentPadding` | DELETE (no meApp caller) | P4 (bk) | S3-B (confirmed S5-A) |
| B-10 | `isInterpolated` on `…MarkerTarget.Point` | RE-APPLY | P5 | S3-B |
| B-11 | `TextComponent` `append(measuredText)` fix | RE-APPLY — **first and alone** + upstream PR | P4 | S3-B |
| B-12 | caller-owned clearing (`initialMarkerX` half) | RE-DERIVE | P14 | S3-B (host file; see H-5) |

### S1-C — axes and layer margins (12 catalogue items → 20 rows)

| id | short name | verdict | phase | source |
|---|---|---|---|---|
| A1 | `HorizontalAxis.Separators` | MOVE → meApp `Decoration.drawUnderLayers` | P8 | S3-C (confirmed S5-A; **F3 precondition**) |
| A2a | `LabelVerticalMode` | RE-APPLY (default `Outside`) | P9 | S3-C |
| A2b | `labelHorizontalPosition` | RE-DERIVE as `Position.Horizontal?` | P9 | S3-C |
| A2c | `getHeight` Bottom-only `lineThickness` | RE-APPLY | P9 | S3-C |
| A3a | `updateLayerDimensions` gutting | REPLACE → `aligned(shiftExtremeLabels = true)` | **P11** (was P8) | S3-C; re-phased by S5-A F4 (C-1 needs it) |
| A3b | `clipPath` → `clipRect` rewrite | DELETE | P4 (bk) | S3-C (confirmed S5-A) |
| A3c | drop per-label `maxWidth` | RE-DERIVE with A2b | P9 | S3-C |
| A4a | half-open window in the item placer | **MOVE, conditional** — meApp's `else ->` must build its own list, else RE-APPLY | **P11** (was P8) | S3-C → **S5-A F1** |
| A4a′ | the `SegmentedHorizontalAxisItemPlacer` half | DELETE (dead for meApp) | P4 (bk) | S3-C (confirmed S5-A) |
| A4b | label clip clamped to `layerBounds` | RE-DERIVE onto `lineLeft`/`lineRight` | P9 | S3-C |
| A5 | `isChartEdge` guideline suppression | RE-APPLY | P11 | S3-C; **bound to A4a** by S5-A F1 |
| A6 | `VerticalAxis.MarkerDecoration` (goal pill) | REPLACE → meApp `Decoration` | P12 | S3-C (**F3 precondition**) |
| A6′ | `getMarkerDecorationWidth` gutter widening | DELETE — **conditional on the P1 font-scale measurement** | P12 | S3-C → **S5-A F2** (risk is not "none") |
| A7a | `Size.Scroll` case + guidelines + line/tick clip | RE-APPLY (narrowed) | P9 | S3-C |
| A7b | `isLabelsScrollable`, `scrollValue`/`maxScroll`, `updateScrollState`, `effectiveScroll` | DELETE | P7 (host half) / P9 (axis half) | S3-C, confirmed S3-E §3 + S5-A F5 |
| A8 | `chartEdgeX()` + `drawLineOnly` + `drawStartAxisLine` | RE-APPLY as one unit | P11 | S3-C |
| A9 | vertical-axis labels centred in the gutter | RE-DERIVE as a parameter | P9 | S3-C |
| A10 | NaN guards on a zero-length y range | RE-APPLY | P9 | S3-C |
| A11 | `ListItemPlacer` | MOVE → meApp (**replace `Float.half`**) | P8 | S3-C → S5-A F6 |
| A12 | margin halving + `ensureVerticalValuesAtLeast` | DELETE (both) | P4 (with B-4c) | S3-C (confirmed S5-A) |

### S1-D — line layer, interpolation, data pipeline (16 catalogue items → 19 rows)

| id | short name | verdict | phase | source |
|---|---|---|---|---|
| L1 | `MonotoneInterpolator` | MOVE → meApp, **`visiblePadding = 1`**, **3** call sites | P8 | S3-C → S5-A F7 |
| L2a | `InterpolationType` enum | MOVE → meApp | P8 | S3-C |
| L2b | `getYAtXFromEntries` | REPLACE → defaulted `Interpolator.getYAt` | P8 | S3-C |
| L3 | `yTransform` + fade state machine | RE-DERIVE as a one-method seam (**Option A**) | P10 | S3-C |
| L4 | transformed layer withheld from the shared range | RE-DERIVE as `contributesYRange` — **non-defaulted on the ctor** | P10 | S3-C → **S3-E §5.3** |
| L5 | `alwaysUseLiveRange` + drawing-model bypass | RE-DERIVE as `CartesianLayerRangeProvider.isStable` — **`rememberFollowing` must split** | P10 | S3-C → **S3-E §5.3** |
| L6 | `markerTargetsEnabled` | RE-APPLY as a ctor `val` (+ `CartesianChart.kt:576`) | P10 | S3-C |
| L7 | `internalRangeProvider` / `internalVerticalAxisPosition` | RE-APPLY (still needed — `verticalAxisPosition` is `protected` at v3.3.1) | P5 | S3-C, confirmed S3-E §2 C-7 |
| L8 | keep points inside the plot | RE-DERIVE onto the visible-slice rewrite | P10 | S3-C |
| L9 | point size no longer pads the plot | RE-APPLY | P10 | S3-C |
| L10 | `getVisibleXRange()` made public | DELETE (revert to `internal`) | P4 (bk) | S3-C (confirmed S5-A) |
| L11 | `isReady: StateFlow<Boolean>` | RE-APPLY verbatim | P5 | S3-C |
| L12a | `animate` on `runTransaction` | RE-APPLY | P5 | S3-C |
| L12b | `registerForUpdates(animate = false)` | DELETE (gated on P1's rotation check) | P4 (bk) | S3-C (confirmed S5-A) |
| L13 | `InlineUpdateContext` rename | REPLACE (second element on upstream's `PreviewContextKey`) | P4 | S3-C |
| L14a | `AXIS_LABEL_VERTICAL_PADDING 4→2` | MOVE → `rememberAxisLabelComponent(padding = …)` | P8 | S3-C |
| L14b | `Animation.RANGE_ANIM_DURATION` | MOVE → `ScrollAwareRangeProvider` | P8 | S3-C |
| L15 | whole-file reformat of `LineCartesianLayer.kt` | DELETE (never port) | P3 | S3-C |
| L16 | `pointSpacingProvider` | DROP (no code exists at HEAD) | P4 (bk) | S3-C |

### S1-E Part 1 — host, composition, gesture (22 rows)

| id | short name | verdict | phase | source |
|---|---|---|---|---|
| H-1 | host-owned range recomputation + opening range | RE-DERIVE | P7 | S3-A |
| H-2 | animated Y-range overlay + seed fallback | RE-DERIVE | P7 | S3-A |
| H-3 | viewport-window broadcast from the draw pass | MOVE → meApp `Decoration` | P16 | S3-A (**F3 precondition**) |
| H-4 | marker resting text formatted on scroll settle | RE-APPLY (re-sited; pin `@OptIn(FlowPreview)` on the host impl) | P13 | **S3-E §2** |
| H-5 | caller-owned marker via `initialMarkerX` | RE-DERIVE (non-deprecated overloads only) | P14 | **S3-E §2** |
| H-6 | scrub via `onInteraction`; exempt from `onViewportChange` | RE-DERIVE (use upstream's `narrowMarkerTargets`; return **`true`** on a null marker) | P14 | **S3-E §2** |
| H-7 | two-pass prepare + `VerticalAxis.updateScrollState` | **DELETE** | P7 | **S3-E §3** over S3-A's implied keep |
| H-8 | external `flingBehavior` | RE-DERIVE — **Shape A**, seam narrowed | P14 | **S3-E §4** |
| H-9 | `scrollState.drawingContext = drawingContext` | RE-APPLY (one line; do **not** null it out) | P7 | **S3-E §2** |
| H-10 | chart-area height parity | **DELETE — do not apply**; remove 3 imports (one is unresolved) | P4 | **S3-E §2** |
| C-1 | `visibleLabelsCount` + label-spacing scale | RE-APPLY, re-sited; inset term reads `layerDimensions.unscalableStartPadding` | P11 | S3-C → **S5-A F4** (now blocked by A3a too) |
| C-2 | `startInsetDp` | MOVE → `layerPadding = { … }` in meApp | P11 | S3-C, C3 (**F3 precondition: non-capturing lambda**) |
| C-3 | start-strip clip + `drawStartAxisLine` | RE-APPLY (clean graft) | P11 | S3-C |
| C-4 | draw order: data on top of what describes it | RE-APPLY (clean graft; `draw()` byte-identical) | P12 | S3-C (confirmed S5-A P3) |
| C-5 | decorations drawn past the fading-edge layer | RE-DERIVE (move the generic over-pass) | P12 | S3-C (confirmed S5-A) |
| C-6 | marker resting pass dispatch | RE-APPLY (folded into B-4a) | P13 | S3-B/S3-C |
| C-7 | interpolated (synthesised) marker targets | **RE-APPLY (amended)** — 6 named amendments | P13 | **S3-E §2** (was: no verdict anywhere) |
| C-8 | marker-target bookkeeping (= `D-2`) | DELETE both halves; restore upstream's sort | P4 | S3-B `D-2` |
| M-1 | scrub gesture state machine | RE-DERIVE in a new file `ScrubGesture.kt` | P14 | S3-B |
| M-2 | `scrollable(enabled = true)` + freeze | RE-APPLY (2 lines) | P14 | S3-B |
| M-3 | external `flingBehavior` on `Modifier.pointerInput` | RE-DERIVE as the override parameter | P14 | S3-B → **S3-E §4** (C2's open half closed) |
| M-4 | `acceptsGestures` gate on the tap detector | DELETE (with B-3a) | P14 | S3-B |

### S1-E Part 2 — build and publishing (12 rows)

| id | short name | verdict | phase | source |
|---|---|---|---|---|
| bld-B1 | `group = "com.dmdbrands.lib"` | RE-APPLY (one line onto upstream's file) | P2 | S3-D |
| bld-B2 | fork version line | RE-APPLY with a new value: `VICO = "5.0.0"` + `UPSTREAM_BASE` | P2 | S3-D |
| bld-B3 | GPR repo + `publishing-convention` → `maven-publish` | RE-APPLY by hand | P2 | S3-D |
| bld-B4 | `gpr-publishing-convention.gradle.kts` | DELETE (dead — applied by nothing) | P2 | S3-D |
| bld-B5 | POM transitive-dep stripping | DROP (never in effect; do not reinstate) | P2 | S3-D |
| bld-B6 | `.module` metadata suppression | DROP (never in effect) | P2 | S3-D |
| bld-B7 | "publish the AAR directly like v3" | DROP (never in effect) | P2 | S3-D |
| bld-B8 | publish org `dmdbrands` → `gg-engineering` | RE-APPLY (re-point `origin`) | P2 | S3-D |
| bld-B9 | Kotlin pinned to 2.1.21 | DROP (take 2.4.10 + `alias(libs.plugins.composeMultiplatform)`) | P2 | S3-D |
| bld-B10 | `gradle-local.properties` ignored | RE-APPLY (keep the `.gitignore` line) | P2 | S3-D |
| bld-B11 | `DmdBrandsTestChart.kt` + 4 sample edits | DELETE (both sample modules gone at v3.3.1) | P2 | S3-D |
| bld-B12 | `dokka(project(":vico:views"))` | DELETE by taking upstream's `vico/build.gradle.kts` whole | P2 | S3-D |

### Non-catalogue rows carried in the plan

| id | what | verdict | phase | source |
|---|---|---|---|---|
| D-1 | `marker.drawOverLayers` called twice per frame | DELETE the second call; replace with an explicit hook | decision P0, executed P13 | S3-B (defect, not an adaptation) |
| D-2 | size-keyed `allMarkerTargetXValues` + deleted sort | DELETE both; restore the sort; invalidate on repopulation | P4 | S3-B (= `C-8`) |
| E-3 | `3b9146ca8`'s "alpha hiding" removal | **undecided — check, then RE-APPLY or DROP** | P4 | S3-E §6 |

`E-3` is the one row in this document whose verdict is genuinely open. It is **not** unassigned — it
has a phase (P4) and a one-read check — but do not fold it into `H-10`'s deletion on the strength of
adjacency, which is what the import cleanup will tempt you to do.

## 1.3 Conflicting verdicts, resolved

| # | Row | Positions | Taken, and why |
|---|---|---|---|
| 1 | `S1A-08c` | S3-A: RE-APPLY, risk "loud (compile)". S3-E §3 + S5-A F5: DROP | **DROP.** The stated risk is false: `initialScrollHandled`'s only outside reader is the `H-7` guard this plan deletes. Two agents derived it independently from source. **[PLAN call]** — S3-A loses. |
| 2 | `A4a` | S3-C: MOVE, risk "silent". S5-A F1: the MOVE describes half the adaptation | **Conditional MOVE.** MOVE only if meApp's `else ->` branch builds its own value list (as it already does for YEAR/MONTH). If it delegates and filters, the deleted `fullXRange` guards cannot be restored and `A4a` is **RE-APPLY**. Settled by the P1 logging check (BL-8). |
| 3 | `A6′` | S3-C: DELETE, risk "none". S5-A F2: UNPROVEN, leaning WRONG | **DELETE, gated.** The premise ("fixed width, so `max()` never fires") is logically invalid; a 4-character signed goal at fontScale 1.3 is ≈54 dp against a 50 dp gutter. Measure in P1 (BL-9); if it exceeds, widen `YGutterWidth` in the same change or keep the contribution in the meApp `Decoration`. |
| 4 | `H-7` | S3-A: implied keep (via `S1A-08c`). S3-C `A7b`: delete it. S3-E §3: DELETE | **DELETE.** `updateScrollState`'s values have exactly one reader, gated on `isLabelsScrollable`, which is never true in meApp. |
| 5 | `M-3` / `H-8` | S3-B: "RE-DERIVE pending C2". S3-E §4: RE-DERIVE as an override parameter (Shape A) | **Shape A.** Costed both ways: +3 parameters, −1 file; Shape B kills `S1A-05a`, patches `VicoScrollState.kt` again, and cannot reconstruct the `isDrag` split. |
| 6 | `L5` | S3-C: meApp's wrapper "answers `false` naturally". S3-E §5.3: two call sites, opposite requirements | **Amended.** `rememberFollowing` gains an `isStable` parameter or splits in two; the percentile band needs `true` where the secondary needs `false`. |
| 7 | `L4` | S3-C: an explicit flag. S3-E §5.3: make it non-defaulted | **Non-defaulted on the ctor** (the remember-function may still default it). It converts a structurally-enforced invariant into a remembered one; omission compiles clean and is silently wrong. |
| 8 | `C-7` | No verdict in S3-A…S3-D. S3-E §2: RE-APPLY (amended) | **RE-APPLY.** The x→canvasX formulas are term-for-term identical at v3.3.1, so this is a graft plus one formula verification, not a re-derivation against a moved target. |
| 9 | CF-2 — reductions early or late | S3-A: after parity. S3-C: seven MOVEs first | **The dividing rule stands** (from the draft, retained): move early iff the code sits in a file you must otherwise hand-merge against upstream churn; move late iff the file grafts clean. Every early MOVE is goldened at the moment it lands. |
| 10 | CF-6 — formatting | S3-C: normalise. S3-B: `diff -w` | **Normalise** (P3). Constraint 3, and `-w` does not address `L15`'s star-import expansion. |
| 11 | C2 — WEEK on `xSnapStep` | C2: "WEEK can plausibly move". S3-E §4: recommend against | **One mechanism, one seam.** Two snap paths on two tabs of one screen would have different animation feel (`spring()` vs `tween(500, LinearOutSlowInEasing)`). The experiment becomes informational. |

---

# §2 Dependency graph and the must-land-together sets

## 2.1 Gates over everything

```
BL-1 Paparazzi spike green ──→ P1 T0 goldens ──→ every library edit below
BL-3 Kotlin context params  ──→ P2 first green build
BL-2 composite substitution ──→ every meApp-side MOVE (i.e. 17 rows)
P3 formatting normalisation ──→ any hand-merge of a hot file
P2 migration clone + build rebase ──→ any library edit
```

## 2.2 Blocking edges, by cluster

Read `X → Y` as "X must land before Y". **†** = cross-cluster. **‡** = stated by no dossier, found
while merging. **✚** = added or corrected by S5-A / S3-E after the draft.

### Scroll / range
```
S1A-06 (public conversions) → S1A-05a, S1A-09a, H-3
S1A-01 (+ widen to public)  → S1A-02 → H-1 → S1A-03
S1A-01, S1A-06              → H-3
S1A-12, H-1                 → H-2
H-8/M-3 seam (Shape A)      → S1A-05a                                   †✚
E-2 (are the two conversions publishable?) → S1A-05a                    ✚
L1 out of the library       → S1A-09b                                   †
H-9                         → S1A-09a                                   †
A7b + H-7 deleted           → S1A-08c is a no-op (DROP)                 †✚
```
The draft's edge `S1A-08a ‖ S1A-08c (one edit, opposite verdicts)` is **deleted**: both are now "do
not apply", so there is no coupling and no edit.

### Marker / gesture
```
B-11 → everything that measures a label, incl. B-4b
D-2  → all gesture work (M-1, B-1, H-6)
D-1 decision → B-4b, B-7, B-8
B-4a → B-4b, B-6
B-4c ‖ A12                                                              †
B-3a + B-3b + M-4 → M-1 → B-1 + B-2
B-4b + B-7 + B-8 land as MOVE → B-6 REPLACE (else B-6 reverts to RE-APPLY)
H-4 → B-5                                                               †
L2b → C-7 ;  L6 → C-7 ;  B-10 → C-7 ;  L7 → C-7                         †✚
D-2 (amended) → H-6 ; C-7 → H-6 (for parity)                            †✚
B-3a/M-4's "unconsumed tap" policy ‖ H-6's `true`-on-null-marker policy  †✚
B-7's seven internal helpers re-written → the subclass compiles         ✚
```

### Axis / layer
```
L15 formatting → everything on LineCartesianLayer.kt
A3b → A4b
A2b ‖ A3c
L2b → L1 → L2a ;  L1 → S1A-09b                                          †
L5 → L3 ;  L3 → L4 (the flag's inference source disappears with L3)     ‡✚
L4 ← S1A-13 (tryUpdateX + NeutralYRange)                                †
L8 ‖ L9
L9 → C-2 → C-3 → A8 → A5 ;  C-2 → C-1
A3a → C-1  (upstream's updateLayerDimensions otherwise adds half a label width)  †✚
A4a ‖ A5   (A5 exists because A4a deleted the fullXRange guards)         ✚
A3a ‖ A4a  (one meApp file, one edit session)
C-4 → C-5 → A6 ;  A6′ and drawMarkerDecorations die with A6
H-2 → A6   (the pill's "lockstep with the axis" claim is only true under the animated ranges)  ‡
L6 ‖ CartesianChart.kt:576
A7b → H-7  (host side) ;  A7b → the three static greps in S3-E §3        †
```

### Build / consumer
```
bld-B9 ‖ alias(libs.plugins.composeMultiplatform)    (one change)
bld-B1 ‖ bld-B12                                     (one file, taken whole)
clone → build rebase → first green :vico:compose:assemble → port
port → publish 5.0.0 → meApp version bump → T5 re-render
every MOVE/DELETE of a fork API → its meApp side, same change   (40 rows, not 22)  †✚
every early MOVE → its golden, at the moment it lands
```

## 2.3 The must-land-together sets

The draft listed 14. **Verified: 12 of the 14 stand, 1 dissolves, 3 are extended, and 6 are new. The
list is now 19.** Each set states what breaks if it is split and whether that break is **loud** (the
build or a test fails) or **silent** (it ships).

| # | Set | What breaks if split | loud / silent | Δ vs draft |
|---|---|---|---|---|
| 1 | `A12` + `B-4c` + the marker's two `ensureValuesAtLeast` call sites | Either half alone is a layout bug in the opposite direction: halving without the compensator under-reserves; the compensator without the halving double-reserves | **silent** | unchanged |
| 2 | ~~`S1A-08a` + `S1A-08c`~~ | **Dissolved.** Both are now "do not apply" (`S1A-08c` is DROP). There is no edit and no coupling | n/a | **removed** |
| 3 | `C-2` + `C-3` + `A8` + `A5` + `A4a` + `A3a` + `C-1` — the start-of-chart / chart-edge unit | Two interlocking features. Without `C-2` the gap collapses and `A5`/`A8`/`C-3` all misplace. Without `A3a`, `C-1`'s inset term silently grows by half a label width — "the thing the whole feature exists for". Without `A4a`'s guard deletion, `A5` suppresses a guideline for a label that upstream never emitted | **silent** | **extended** (+`A4a`, +`A3a`) — S5-A F1, F4 |
| 4 | `B-1` + `B-2` + `H-5` + `H-6` + `B-12` + `B-3a`/`B-3b`/`M-4` | One contract read from both ends. The controller, the callback's third argument, the host's marker state and the **tap-consumption policy** are one decision: `ScrubMarkerController` returns `true` on every accepted interaction *including the clear*; meApp's moved `NonInteractiveMarkerController` returns `false` so the snapshot card still opens | **silent** (a tap that navigates when it should not, or does not when it should) | **extended** (+`B-3a`/`B-3b`/`M-4`) — S3-E §2 H-6 |
| 5 | `L8` + `L9` | Removing the point padding without the clip is what let a dot spill outside the plot | **silent** | unchanged |
| 6 | `L6` + `CartesianChart.kt:576` | Two enforcement points for one invariant; one alone lets the scrub land on a percentile curve | **silent** | unchanged |
| 7 | `S1A-13`'s `getValue(null)` substitution at **both** `:41` and `:101` | The existing test pins only the first site; the `toImmutable()` copy is uncovered | **silent** | unchanged |
| 8 | `A2b` + `A3c` | `A2b` re-breaks the last label's `maxWidth` unless `A3c` is re-derived with it | **silent** | unchanged |
| 9 | ~~`A3a` + `A4a`~~ | **Absorbed into set 3.** They are still one meApp edit session, but the session now also carries `A5` and `C-1`'s term | — | **merged** |
| 10 | `B-4a` + `B-5` + `B-10` + `drawLabels` (per D-1) | One coherent interface change and one upstream PR ("explicit draw phases plus an idle phase"). Shipping them piecemeal means three PRs upstream will reject as incoherent | loud locally, **silent** for the subclass's line placement | unchanged |
| 11 | `C-5` + `A6` + deleting `AxisManager.drawMarkerDecorations` | The moved decorations-over pass is what lets the pill leave the axis; delete the bespoke pass first and the pill vanishes, move the pill first and it is sliced by the fade layer | **silent** | unchanged |
| 12 | `S1A-01` + widening its members to `public` | `H-3` and `H-2`'s effect move are blocked without it; a later widening means re-publishing | loud (compile) | unchanged |
| 13 | `H-3` + the three-scalar dedup + the `xSpacing > 0f` guard | Emitting unconditionally re-triggers the settle chain — the defect `6ef5fdcc7`/`57ce985ea` fixed | **silent** | unchanged |
| 14 | Every MOVE/DELETE of a fork API + its meApp side | A MOVE that lands alone is a broken consumer build. The surface is **40 rows**, not 22 | loud (compile), except the three *removals* which are silent | **extended** — S3-E §5 |
| 15 | `L3` + `L4` | **New.** `contributesYRange` is inferred today from `yTransform != null`. Remove the seam without landing the explicit flag and the overlay layer resumes unioning its raw values into the shared range — a pulse of 60 against a weight of 150 opens the axis at zero | **silent** | **new** ‡ |
| 16 | `C-7` + `L2b` + `L6` + `B-10` + `L7` | **New.** Three verdicts exist only to serve `C-7` and have no other consumer; `L7`'s `internal` accessor is still required because `verticalAxisPosition` is `protected` at v3.3.1. Land `C-7` without them and it does not compile; drop `C-7` and all four are stranded | loud (compile) | **new** — S3-E §2 |
| 17 | `A7b` + `H-7` (+ `S1A-08c` left un-applied) | **New.** `A7b` deletes the only writer of the values `H-7`'s first pass exists to feed; `H-7` deletes the only outside reader of `initialScrollHandled`. Apply any one of the three and you ship either dead code or a dangling `internal` | silent (dead code) — but the **three static greps** in S3-E §3 make it checkable in a minute | **new** |
| 18 | `L1` + `L2a` + `L2b` + `S1A-09b` — the interpolator unit | **New.** Move the enum out while the library still holds its reader and the library does not compile; move the interpolator without `visiblePadding = 1` and the two edge segments silently change shape *as you scroll* — invisible to any static golden | loud for the first half, **silent** for the second | **new** (CF-3 was an ordering note, not a set) |
| 19 | `A1` + `A6` + `H-3` + `C-2`'s `layerPadding` lambda — the **`decorations` stability precondition** | **New, and the one nobody owned.** All four become `rememberCartesianChart` keys *and* `CartesianChart.equals` terms, and the host's range-seeding `remember(chart, model) { ranges.reset(); chart.updateRanges(…) }` is keyed on that equality. An unstable decoration list, or a capturing `layerPadding` lambda, re-seeds the range on every recomposition — **the MOB-3494 defect shape**. meApp passes no `decorations` today, so this surface is created *by* the minimise-the-fork strategy | **silent** | **new** — S5-A F3 |

**Set 19 is not a "land together" set in the ordinary sense** — the four land in four different phases
(P8, P12, P16, P11). It is a shared precondition with a single owner and a single gate: the
`remember(chart, model)` block is instrumented when the **first** of the four lands (P8), and the log
is re-read when each subsequent one lands. It must fire once per genuine model/config change and never
per frame or per recomposition. The specific landmines: the decoration list must be a stable
instance (hoisted or `remember`ed, not rebuilt in the composable body), and
`layerPadding = { CartesianLayerPadding(unscalableStart = ChartStartGap) }` must stay **non-capturing**
— the moment anyone writes `{ … if (isSnapshot) a else b }` it allocates per composition and the chart
is unequal every recomposition.

## 2.4 File contention — who edits what, and how often

| File | Upstream churn in range | Items contending | Phases |
|---|---|---|---|
| `CartesianChart.kt` | +228/−18 (ours +203/−12) | `D-2`, `C-1`…`C-7`, `L6`(:576), `L2b`(:582), `B-4a` dispatch, `D-1`'s `drawLabels` | P4, P10, P11, P12, P13 — **five passes** |
| `CartesianChartHost.kt` | +196/−94 (ours +454/−8) | `H-1`…`H-10`, `A7b`'s call sites, `B-12`, `H-10`'s dead imports | P4, P7, P13, P14 — **four passes** |
| `LineCartesianLayer.kt` | +130/−35 (ours +320/−54) | `L1`, `L3`…`L9`, `L15` | P3, P8, P10 |
| `HorizontalAxis.kt` | +277/−77 | `A1`, `A2a-c`, `A3a-c`, `A4b`, `A5`, `A7a` | P4, P8, P9, P11 |
| `VerticalAxis.kt` | +175/−29 | `A6`, `A6′`, `A7a`, `A7b`, `A8`, `A9`, `A10`, `A11` | P4, P9, P11, P12 |
| `VicoScrollState.kt` | +182/−41 | `S1A-06`, `-07`, `-08a/b/c`, `-09a/b`, `L2b`(:164), `H-9`'s target | P6, P8, P16 |
| `DefaultCartesianMarker.kt` | +39/−25 (ours +288/−19) | `B-4b`, `B-6`, `B-7`, `B-8`, `B-9`, `B-4c` | P13 |
| `Modifier.kt` | +121/−25 (ours +234/−27) | `M-1`…`M-4`, `B-3a`, `H-8`'s Shape-A override | P14 |

**[PLAN call]** One owner per *file* for the duration, not per cluster. `CartesianChart.kt` is edited
by three dossiers' items across five phases; its `draw()` tail is re-read end to end once after P13
rather than trusted hunk by hunk (CF-5).

---

# §3 The phases

Eighteen phases, P0–P17, plus P18 (deferred, explicitly not this migration). Every phase ends with
**`:vico:compose:assemble` green, meApp building against the composite, and the dashboard chart
rendering** — so any phase boundary is a place to stop, hand over, or review.

**The standing caveat about "green".** P4–P6 leave a library that builds and draws but whose flagship
behaviour (scroll-aware Y ranging) is dead, because the host half is not in yet. That is why the host
core is pulled forward to **P7** rather than left where S3-A's cluster-local order implies. From P7
onward every golden is meaningful and a regression is attributable to the phase that introduced it.

---

## P0 — Settle the blocking questions. No library code. — **2–3d, moderate**

**Lands:** three spikes and four decisions, written into this file as answers with the command output
pasted beside them.

1. **BL-1 Paparazzi spike** — one test, three pass criteria; criterion 3 (`verifyPaparazziDebug` green on a clean CI runner) is the one that usually fails. Note: **coroutines do not run under preview rendering** — the model must be pushed synchronously.
2. **BL-2 composite-build substitution at v3.3.1's layout** — `dependencyInsight` in the composite, across AGP 9.3.2 (lib) vs 9.5.1 (app). Three ordered fallbacks are recorded in S3-D app. 7.
3. **BL-3 Kotlin context parameters at 2.4.10** — one compile of stock v3.3.1. Failure → add `-Xcontext-parameters`, do not downgrade Kotlin.
4. **BL-6 `D-1` decision** — add `CartesianMarker.drawLabels`, or collapse to upstream's single late call? The subclass's method split *is* the fix, so this cannot be decided after the subclass is written.
5. **BL-7 the start-gap arithmetic** — `12.dp` vs `12.dp + maxPointSize/2`. A **product** decision, not a spike; it needs a human and therefore needs asking now.
6. **Record `L3` Option A** so P10 is not re-opened.
7. **S3-D app. 4** — one grep: does the fork's `yTransform` path touch the now-throwing `CartesianLayerDrawingModel.transform`? It prevents an `UnsupportedOperationException` in P10 reading as a port bug.

**Why here and not later:** items 1–3 can each invalidate the plan's *shape*, not just its duration.
**Why not earlier:** nothing.
**Proof:** every question has a written answer; 1–3 each have a pasted command output.
**Not here any more:** the draft's BL-4 (`flingBehavior` seam) and BL-5 (`A7b` vs `S1A-08c`) — both
resolved by S3-E from source. The frame-1-vs-frame-2 screenshot that CF-1 proposed for P0 moves to
**P15**, where it must capture the **first drawn frame**, not a settled one.

---

## P1 — T0: the golden baseline, and the six checks that can only be run on 4.0.46. — **5–8d, genuinely uncertain**

**Lands:** a meApp PR against the current `vico = "4.0.46"`, resolved from GPR with `vico.local` unset.

- `ChartFixtures.kt` (fixed timestamps, locale, time zone — no `Calendar.getInstance()`), `ProductChartGoldenTest.kt`, `ChartEdgeStateGoldenTest.kt`.
- **The 36-image set**: 3 configs × 4 segments × 2 themes, plus 12 edge/state cases, each pinned to a named silent-change risk (S3-D §3.4).
- **A first-drawn-frame capture mode.** `H-7`'s deletion is silent and *"a golden screenshot taken at frame ≥ 2 would not catch it"*. At least the chart-open and tab-entry goldens must be capturable at frame 1. This is new work the draft did not budget.
- **The six 4.0.46-only checks**, unanswerable once the branch tip moves:
  1. `acceptsGestures` smoke gate — tap the snapshot card (full graph opens); press-and-hold leaves the caption put.
  2. `./gradlew :app:lintDebug` with both `@SuppressLint("RestrictedApi")` removed (C7).
  3. The rotation check for `L12b` — does the restored model sweep in?
  4. `yLabelCallback` instrumentation — is the `fallbackValues` path ever the value used? (gates `B-8` MOVE vs DELETE.)
  5. Segment A → B → A — today's behaviour, as the comparison for `S1A-08b`.
  6. **New (BL-9):** render the weight dashboard at `fontScale = 1.3` with the longest signed weightless goal label and measure the pill's `getWidth` against `YGutterWidth = 50.dp`. Settles whether `A6′` is a free DELETE.
- **New (BL-8):** on a WEEK and a TOTAL chart, log `ranges.maxX`, `fullXRange.endInclusive` and the placer's returned label list at scroll positions where `maxX` sits strictly inside the visible window. Settles whether `A4a` can be a MOVE.

**Why here and not later:** once 4.0.46 is off the branch tip the baseline is gone, and a golden
captured after any migration work is a record of a half-migrated state. Checks 1–6 and BL-8 need a
running, un-migrated app.
**Why not earlier:** BL-1 must be green first.
**Proof:** 36 committed PNGs; two consecutive `recordPaparazziDebug` runs byte-identical;
`verifyPaparazziDebug` green on CI; eight written check results.
**Uncertain because:** it rests entirely on BL-1. If Paparazzi cannot render vico deterministically,
the fallback is AGP Compose Screenshot Testing (different source set, less ergonomic) or instrumented
capture (which removes the "runs in CI, no device" premise). That is a **different plan, not a longer
one.**

---

## P2 — The migration clone and the build rebase. — **1–3d, predictable**

**Lands:** `~/Projects/vico-migration`, branched from `v3.3.1`; S3-D §1.2 steps 1–8 — upstream's
`settings.gradle.kts`, catalogue, wrapper, `gradle.properties`, `buildSrc`, `sample/**` taken verbatim;
`group = com.dmdbrands.lib` (bld-B1); `VICO = "5.0.0"` + `UPSTREAM_BASE = "3.3.1"` (bld-B2);
`maven-publish` + the GPR block (bld-B3); delete `gpr-publishing-convention.gradle.kts` (bld-B4);
drop the Kotlin pin with `alias(libs.plugins.composeMultiplatform)` (bld-B9); keep the `.gitignore`
line (bld-B10); re-point `origin` to `gg-engineering` (bld-B8); delete `DmdBrandsTestChart.kt` and the
four sample edits (bld-B11); `vico/build.gradle.kts` taken whole, which is how bld-B12 dies. bld-B5/6/7
stay un-reinstated. Plus the guarded, property-switched `includeBuild` in meApp's `settings.gradle.kts`.

**Why here:** nothing can be ported until there is somewhere to port it to, and the shared checkout is
read-only. Doing it before any adaptation means the first compile failure is a *build* failure, not an
ambiguous one. **Why not later:** BL-3 is settled by this build.
**Proof:** `./gradlew :vico:compose:assemble` green on **stock** v3.3.1 + fork build config;
`dependencyInsight` reports `project :vico:compose`; the publish task set recorded.

---

## P3 — Formatting normalisation. — **1–2d, predictable**

**Lands:** upstream's ktfmt 0.64 formatting applied to our HEAD side of every file we will hand-merge
(14 files in `vico/compose`); `L15`'s star-import expansion on `LineCartesianLayer.kt` reverted;
`L16` confirmed as nothing.

**Why here:** roughly half our added lines on the two hottest files are whitespace and imports, and
every one of them will conflict. Everything after this is roughly twice as fast. **Why not later:** a
merge attempted first has to be redone.
**Proof:** `git diff e67e49520..HEAD -- <file>` with and without `-w` produces the same hunk count on
`HorizontalAxis.kt` and `LineCartesianLayer.kt`.

---

## P4 — The no-ops, the one-liners, the two defects, and the one loud failure. — **2–4d, predictable**

**Lands — mostly by *not applying* hunks** (the bookkeeping pass, 18 rows):
`A12` + `B-4c` **as one decision**; `A3b`; `A4a′`; `A7b`'s own hunks (host side realised at P7, axis
side at P9); `L10`; `L12b` (gated on P1 check 3); `L16`; `B-9`; `S1A-05b`; `S1A-08a`; `S1A-08c`
(nothing to apply); `H-7` (host side, realised at P7); `H-10`.

**Applied, in this order:**
1. **`B-11`** — the `TextComponent` `append(text)` → `append(measuredText)` fix, **first and alone, in its own commit**, with the upstream PR opened the same day.
2. **`D-2`** — restore upstream's three sort lines verbatim; replace the size-keyed `allMarkerTargetXValues` cache with explicit invalidation where `_markerTargets.clear()` runs. Keep the `internal val` — `H-6` is its only consumer.
3. **`L13`** — `InlineUpdateContext` as a second element on upstream's `PreviewContextKey`.
4. **`H-10`'s import cleanup** — remove all three imports from `CartesianChartHost.kt`, including `Defaults.CHART_HEIGHT`, which **is an unresolved reference at v3.3.1** and the only loud failure in the project. **[verified]**
5. **`E-3`** — read v3.3.1's `collectAsState(chart, animationSpec, initialAnimationSpec, mutableRanges)` reveal path end to end against the comment at `HEAD CartesianChartHost.kt:186-187`, and decide whether `3b9146ca8`'s alpha-hiding removal is an independent behaviour we carry. **Do not fold it into `H-10` on the strength of adjacency.**
6. Doc deletions that travel with the code: FEATURE-4's suppression paragraph, FEATURE-8's `contentPadding` section, the stale `TreeMap` comment, the stale `MonotoneInterpolator.getYAtX` comment at `CartesianChart.kt:548`.

**Why here:** these shrink the conflict surface before any hot-file merge. `B-11` is the most losable
change in the project — a take-theirs resolution reverts it silently — so it goes in alone where it
can be verified by hand. `D-2` goes before any gesture work, or you will debug the wrong layer.
**Why not later:** every later phase that measures a label is wrong until `B-11` lands, and nothing
will tell you.
**Files that stop being patched here:** `CartesianLayerMargins.kt`, `CartesianDrawingContext.kt`,
`CartesianChartModel.kt`.
**Proof:** build green (the `CHART_HEIGHT` import is how you know the cleanup landed);
`git diff v3.3.1..<branch>` shows those three files absent; the `pad` branch of `TextComponent`
re-read by hand after the merge; a two-layer chart's marker returns both series.

---

## P5 — The clean-graft contracts. — **1–2d, predictable**

**Lands:** `S1A-12`, `S1A-13` (**both** `:41` and `:101`), `S1A-04`, `S1A-11`, `B-10`, `L11`,
`L12a`, `L7`.

**Why here:** every one sits in BASELINE's "upstream never touched" list or on a byte-identical file;
three fail to compile if dropped. They are the data-layer contracts the rest of the plan reads, and
landing them first buys an early green build on v3.3.1.
**Why not later:** `L4` needs `tryUpdateX` + `NeutralYRange`; `L3` needs `S1A-04` and `S1A-12`;
`C-7` needs `B-10` **and `L7`** (set 16).
**Proof:** build green; `MutableCartesianChartRangesTest` green **plus a new case covering the
`toImmutable()` copy** — the existing test pins only one of the two substitution sites.

---

## P6 — `VicoScrollState`. — **1–3d, predictable**

**Lands:** `S1A-06` RE-APPLY with `xToScrollValue`, `xToScrollValueBehindStartInset` and
`scrollValueToX` widened `internal` → `public`; `S1A-07` (the scrub-freeze re-wrap). `S1A-08a` and
`S1A-08c` are both **not applied** — v3.3.1's `private var initialScrollHandled` is left exactly as
upstream wrote it.

**Why here:** `S1A-06` unblocks `S1A-05a`, `S1A-09a`, `S1A-09b` and `H-3`; it is the file everything
else reads. **Why not earlier:** it is a hot file (+182/−41 upstream) and wants P3's normalised diff.
**Smaller than the draft had it:** CF-1's resolution removes the `S1A-08c` edit *and* the coupled-edit
hazard from this phase.
**Watch:** `scrollEnabled` became `mutableStateOf` at v3.3.1 (`1fe9daef9`), so the `!scrollEnabled`
freeze arm is now reactive where it previously could not change.
**Also settle here or in P14:** **E-2** — are the two conversions' signatures ones the fork is willing
to make public? That is what decides whether `S1A-05a` can be a MOVE in P16.
**Proof:** build green; the three conversions resolve from meApp through the composite build.

---

## P7 — The host core. The fork's flagship goes live. — **6–12d, genuinely uncertain**

**Lands:** `S1A-01` (**with its members widened to `public` in the same commit**), `S1A-02`, `H-1`
(**alone**), `S1A-03` (immediately after `H-1`), `H-2` (wrapper + effect, ported in place), `H-9`
(one line, between the `CartesianDrawingContext` construction and `chart.draw`), `S1A-09a`.
**`H-7` is realised here by not applying it** — the two-pass prepare block never arrives on the
v3.3.1 host, and with it goes the early `scrollState.update(…)`.

**Why here — a departure from both S3-A's and S3-C's cluster-local orders. [PLAN call]** Until
`H-1`/`H-2` are in, the scroll-aware Y range is dead and every golden taken between P4 and P7 is a
golden of a chart missing its headline behaviour. Landing the host here makes every subsequent phase
individually goldenable. **Why not later:** `A6`'s lockstep claim (P12) and `H-3`'s move (P16) both
depend on this machinery existing. The cost is that `CartesianChartHost.kt` is hand-merged here and
touched again in P13 and P14; the regions are disjoint (range machinery / marker plumbing / gesture),
but the file wants one owner.

**The `H-7` port note, which looks wrong at a glance and is not:** deleting the block moves
`scrollState.update(…)` to its normal position *after* `prepare` on frame 1. That is safe because the
update still runs **before** the `CartesianDrawingContext` is constructed, so the frame-1 *draw*
already uses the post-`initialScroll` value. Only *measurement* sees `scroll = 0`, and measurement is
scroll-independent by design — upstream's own `measureExtras` runs from `Modifier.layout`, strictly
before any scroll is known. **Write that invariant into the axis file as a comment**, not into a
dossier nobody will read again.

**Carry verbatim, each a previously-shipped silent defect:** the push-time domain (`25f0fb793`), the
frame-late opening range (`a98cf86de`), layer↔model pairing by position not `indexOf` (`e6c766cb8`),
animatables initialised *after* cache build, dedup by provider identity, `NaN` as the uninitialised
sentinel, and `provider.settleEpoch++` **before** the "range did not move" early return.
**Observation to make here (E-1):** upstream's layout-pass `measure()` runs with a height meApp's
fixed-height hosts discard, and it mutates chart state (`_markerTargets.clear()`, `layerMargins.clear()`,
`updatePersistentMarkers`). Log `_markerTargets.size` and `drawingContext != null` at the top of
`measure()` and `draw()` for one frame. Cost: one read.
**Proof:** the full golden set re-rendered; the Y axis follows the visible window on a WEEK scroll;
the three named regression cases exercised by hand; the three static greps from S3-E §3 for the
`H-7`/`A7b` half that lives here.
**Uncertain because:** `H-1` is the hardest single item in the project and `H-2` is ~250 lines, against
a host where `prepare` is re-signatured, the `Canvas` moved into `ChartHostBox`, and `animateIn`
became `initialAnimationSpec`. A RE-DERIVE cannot be estimated from a diff: the diff says what *was*.

---

## P8 — Reduce the hot files before merging them. — **3–6d, moderate**

**Lands, each with its meApp side in the same change:**
- `A1` — `Separators` → a meApp `Decoration.drawUnderLayers`. Takes the largest additive block out of `HorizontalAxis.kt` *before* P9 has to merge that file against +277/−77 of upstream churn. `Rect.getStart` is internal and must be inlined.
- `A11` — `ListItemPlacer` → meApp, file deleted from the library. **`internal inline val Float.half` must be replaced** with `/ 2f` or a local private val **[verified]**; S3-C's "nothing internal is touched" was wrong, and it is the sentence that would stop someone looking.
- **The interpolator unit (set 18), in this order:** `L2b` (one defaulted `getYAt` on `Interpolator`) → `L1` (`MonotoneInterpolator` → meApp, **declaring `visiblePadding = 1`**, **three** call sites: `PercentileBand.kt:39`, `DataLine.kt:47`, `PrimaryLayer.kt:50` **[verified]**) → `S1A-09b` (`getInterpolatedYValues` → meApp, **carrying `25dc072a9`'s contract: outside the data range return `null`, do not clamp**) → `L2a` (`InterpolationType` → meApp).
- `L14a` + `L14b` — `Defaults.kt` leaves the fork.
- **Set 19's instrumentation**, installed with `A1` because `A1` is the first `decorations` entry: log every entry into the host's `remember(chart, model)` block and exercise scroll, segment switch, goal edit, unit toggle, rotation.

**Why here, and the methodological conflict it settles.** S3-A says reductions come after parity;
S3-C puts seven MOVEs before anything is proven. **[PLAN call] the dividing rule: move early iff the
code sits in a file you must otherwise hand-merge against upstream churn; move late iff the file grafts
clean.** `A1`, `A11`, `L1`, `L2a` qualify. `S1A-10` (`Scroll.kt`, untouched upstream) and `S1A-05a`
(our own new file) do not — they cost nothing to port and then move, so they wait for P16. The price
of the rule is that **every early MOVE is goldened at the moment it lands**; that golden is the item's
parity proof. **Validate the rule on `A11` first** (lowest stakes, character-identical margin methods):
if its golden is pixel-clean, the rule holds for `A1` and `L1`; if not, fall back to S3-A's position
and accept the larger merges.
**Smaller than the draft had it:** `A3a` and `A4a` leave this phase for P11 (S5-A F1, F4), and the new
meApp half-open-window test goes with them.
**Behaviour changes to golden explicitly:** `visiblePadding = 1` changes the two edge segments' shape
**on a scrolling chart and is invisible on a static one** — the golden must scroll; `A1` puts
separators *under* the guidelines instead of over.
**Pre-existing defect to fix while there:** `HorizontalItemPlacer.kt:92`'s `getLineValues` `else ->`
branch calls `defaultPlacer.getLabelValues`.
**Proof:** goldens unchanged except where predicted; `A11`'s golden pixel-clean; the
`remember(chart, model)` log fires once per genuine change and never per frame.

---

## P9 — The axes. — **3–5d, moderate**

**Lands:** `A2a` + `A2c` (with `LabelVerticalMode`'s default `Outside`, not `Center`) · `A2b` + `A3c`
re-derived together as a nullable `labelHorizontalPosition` over upstream's new per-index `when` ·
`A4b` (clamp upstream's `lineLeft`/`lineRight` to `layerBounds`) · `A7a` (narrowed to the `Size.Scroll`
case plus the two behaviours meApp consumes) · `A9` (as a parameter, not a hard-code) · `A10` (NaN
guards) · **`A7b`'s axis half** — `isLabelsScrollable`, `scrollValue`/`maxScroll`, `updateScrollState`
and `effectiveScroll` are simply not applied.

**Why here:** onto a surface `A1`, `A3b`, `A4a′`, `A6′` and `A11` have already shrunk, and after the
host is live so an axis regression is visibly an axis regression.
**Why not later:** `A5`/`A8` (P11) and `A6` (P12) land on these files; merging them after those
reductions would mean hand-merging code you are about to change again.
**Open here:** `U5` — `labelHorizontalPosition = End` on every label combined with
`shiftExtremeLabels = true` has never been rendered. `A3a` lands in P11, so this is checked again there.
**Proof:** the three static greps from S3-E §3 return nothing (`scrollValue|maxScroll` in
`cartesian/axis/`; `initialScrollHandled` outside `VicoScrollState.kt`; `isLabelsScrollable` in either
tree); WEEK and YEAR bottom axes screenshotted at scroll 0 / mid / max — first and last labels neither
clipped nor overlapping; `A10`'s guards exercised on an empty chart and on a chart whose every entry
has the same y.

---

## P10 — The line layer. — **5–10d, genuinely uncertain**

**Lands:** `L9` + `L8` (together, set 5) · `L6` as a constructor `val` in `equals`/`hashCode`, **with
`CartesianChart.kt:576`** (set 6) · `L5` re-derived as `CartesianLayerRangeProvider.isStable`,
preserving v3.3.1's reveal-sweep read of the drawing model · `L3` **Option A** (a `CartesianLayerYMapper`
seam, ~15 library lines; the `DoubleArray` cache, the three keys, the fade state machine and the
`TimeSource` move to meApp as ~120 lines; `series.hashCode()` replaced by `MetricBasis.version`) ·
`L4` re-derived as `contributesYRange: Boolean`, **non-defaulted on the constructor** (sets 15).

**Why here:** `L9` must precede `C-2` (P11) so upstream's *add* to `maxPointSize.half` and our former
*max* agree. `L5` must precede `L3`; `L3` must land with `L4`. The file is the hardest in the port and
wants P3's normalised diff, the interpolator already gone (P8) and the host live (P7) so the overlay
can be seen working. **Why not later:** P11 is blocked on `L9`.

**The `L5` amendment — this is not the one-line change S3-C describes.** `rememberFollowing` has two
call sites with **opposite** requirements: `ProductChart.kt:323` (→ `secondaryLayer`, needs
`isStable = false`) and `ProductChart.kt:303` (→ `rememberPercentileBand`, needs **`true`** — meApp's
own comment at `:302` records why: *"alwaysUseLiveRange stays off — it bypasses the provider and
flashes the Y axis on frame 0"*). The library guard is a disjunction, so three providers must answer
three ways. `rememberFollowing` gains an `isStable` parameter or splits in two. If both wrappers
answer `false`, the percentile band regresses to a frame-0 Y-axis flash. **Silent.**

**The `L4` amendment.** `contributesYRange` is defaulted-true *as a concept*, and today the flag
cannot be forgotten because it is inferred from `yTransform != null`. Making it explicit converts a
structurally-enforced invariant into a remembered one. **Non-defaulted on the ctor** so omission is a
compile error; the remember-function may still default it. Failing that, P15's parity check for the
weight-with-secondary chart must assert the Y-axis **minimum on the first frame of a tab entry**.

**Invariants to carry verbatim** (each was a bug once, none covered by a test): compute against
`getTargetYRange`, never the animating `getYRange`; increment the settle epoch before any no-movement
early return; skip the fade when the new result `contentEquals` the old; the window key must be
nullable (`0` is a real key); the cache must survive `copy()`.
**meApp side:** `yTransform` and `alwaysUseLiveRange` both leave the public API, `contributesYRange`
arrives; `PrimaryLayer.kt` and `ProductChart.kt` change shape.
**Proof:** the secondary-overlay golden (#31) and the baby percentile goldens (#27/#28) unchanged;
`U4` instrumented — the fade phase returns to 0 across two windows that round to the same axis;
the percentile band shows no frame-0 flash after the `rememberFollowing` split.
**Uncertain because:** same reason as P7 — RE-DERIVE against functions upstream rewrote (visible
slice, reveal sweep), with zero existing test coverage on `yTransform`.

---

## P11 — The start-of-chart and chart-edge unit. — **3–6d, moderate**

**Lands as one unit (set 3), in this order:** `C-2` (MOVE — `startInsetDp` deleted; meApp passes
`layerPadding = { CartesianLayerPadding(unscalableStart = ChartStartGap) }`, **non-capturing**) →
`C-3` → `A8` → **`A3a` + `A4a` + `A5`** (one meApp edit session on `HorizontalItemPlacer.kt`:
`aligned(shiftExtremeLabels = true)` plus the half-open filter on **both** overrides) → `C-1`
(re-sited into v3.3.1's `measure`/`prepare` split; the inset term re-derived to read
`layerDimensions.unscalableStartPadding`; the `equals`/`hashCode` defect on `visibleLabelsCount` fixed).

**Why `A3a`/`A4a`/`A5` are here and not in P8 — the draft's largest sequencing error.**
- **S5-A F1:** `A4a` is *two* changes, not one — the `closesTheWindow` epsilon filter **and the deletion of upstream's `fullXRange.start`/`endInclusive` guards**. A delegating wrapper can only subtract from upstream's list; it cannot add back a value upstream's `break` suppressed. With meApp's post-migration end padding at zero (`L9` + `A3a` + no `unscalableEnd`), `fullXRange.endInclusive == ranges.maxX`, so upstream's guard drops the **last data x's label**. And `A5` exists *because* those guards were removed — edge values only reach `drawGuidelines` once the placer stops suppressing them. **`A4a` and `A5` are one feature.** `A5` is blocked by `C-2`, which is here; therefore `A4a` is here.
- **S5-A F4:** `C-1`'s re-derived inset term equals `ChartStartGap` only under (`L9` ∧ `A3a` ∧ `C-2`). At v3.3.1 `HorizontalAxis.updateLayerDimensions` contributes half the first label's padded width unless `getShiftExtremeLabels` returns true — i.e. unless `A3a` has landed in meApp. Drop `A3a` and `C-1`'s scale factor silently grows. **`A3a` is a blocker of `C-1`, not an independent P8 item.**

**Why not split:** re-applying the pieces independently yields a chart that compiles and is subtly
wrong. **Why not earlier:** `C-2` needs `L9` (P10).
**The form of `A4a` is decided here, by BL-8's P1 result.** If the last date label is lost under
delegate-and-filter, meApp's `else ->` branch must build its own value list (as it already does for
YEAR and MONTH); if that is refused, `A4a` reverts to **RE-APPLY** and the guard deletion stays in the
library.
**New test, here not P8:** there is no test for the half-open window rule in either repo today.
**Open:** `U1` — `A8` draws the line at `chartEdgeX()` while `AxisDimensions.lineBounds` still
describes the gutter. Log `lineLeft`/`lineRight` and the final bottom-axis clip at scroll 0 / mid /
max on WEEK and confirm the clip equals `layerBounds` horizontally. Re-check `U5` with `A3a` now on.
**Proof:** golden #36 (scrolled away from the start edge) and the start-edge goldens; no second line
painted on the start axis; the correct number of periods per window; **the eight-day week does not
return, and the last date label is still drawn**; at runtime,
`layerDimensions.unscalableStartPadding == ChartStartGap.pixels` exactly, on WEEK/MONTH/YEAR/TOTAL.

---

## P12 — Draw order, the decorations pass, and the goal pill. — **1–3d, predictable**

**Lands:** `C-4` (the reorder — clean graft; `draw()` is byte-identical fork → v3.3.1 **[S5-A P3]**) ·
`C-5` re-derived (**move the generic `decorations.forEach { it.drawOverLayers(context) }` pass** to
where `axisManager.drawMarkerDecorations` sits today, past the fading-edge restore; delete
`drawMarkerDecorations`) · `A6` (the pill → a meApp `Decoration`), **verified on the harness before
the `VerticalAxis` code is deleted, not after** · `A6′` (delete the gutter widening) **only if P1's
BL-9 measurement cleared it**; if it did not, either keep the width contribution in the meApp
`Decoration` or widen `YGutterWidth` in this same change.

**Why here:** `C-4` → `C-5` → `A6` (set 11), and `A6`'s "lockstep with the axis" claim is only true
once `H-2` is in (P7) ‡. **Why not later:** `A6` takes the largest single block out of `VerticalAxis.kt`.
**Why not earlier:** the dependency chain forbids it — this is a reduction *after* its file's merge
(P9), accepted and stated rather than hidden.
**Set 19 re-check:** `A6` is the second `decorations` entry; re-read the `remember(chart, model)` log.
**Accepted order changes:** the pill now draws before the legend (meApp passes none) and before the
marker over-passes; a decoration's over-pass now draws over the composited data, which is upstream's
own behaviour.
**Proof:** golden #32 (goal pill) at a y near the canvas bottom — the `1bf0a6d9a` defect was a 63 px
pill at y=822 on an 825 px canvas losing its bottom half; and at `fontScale = 1.3` with the longest
signed goal label, the pill is not clipped at the end axis.

---

## P13 — The marker. — **5–9d, uncertain**

**Lands, in this order:**
1. **The interface set as one additive, defaulted change and one upstream PR** ("explicit draw phases and an idle phase for `CartesianMarker`"): `B-4a` (`drawResting`), `B-5` (`onScrollSettled`), `B-10` already in from P5, and **`drawLabels`** per P0's `D-1` decision · `C-6` (the dispatch).
2. **`C-7`** (synthesised targets) — RE-APPLY with its six amendments: route `:582` through the `L2b` seam preserving the `?: continue` no-clamp contract; `:576` lands with `L6` as a `val`; `:590` lands with `B-10`; `:578` lands with `L7`; the retained `drawingContext` re-sites into v3.3.1's `draw()` (set at `:378`, nulled at `:403`); fix the stale comment at `:548`. Align the one-pixel bounds-test asymmetry with the layer's while you are there. **Record, do not fix:** during a difference animation the drawn line sits at the animated y and the synthesised point at the final y — they separate for the animation's duration, on layers whose `drawingModel` is not nulled. Pre-existing; note it so P15 does not read it as a port defect.
3. **`H-4`** (the settle feed), re-sited into v3.3.1's `LaunchedEffect` block, with `MARKER_SETTLE_MS` kept **equal to `ScrollAwareRangeProvider.debounceMs`** so the caption and the axis it describes come to rest together, `replay = 1` and `model` kept in the key, and **`@OptIn(FlowPreview::class)` put on the host impl explicitly** rather than relying on `debounce`'s warning level surviving Kotlin 2.4.10 and whatever `-Werror` the rebased build ends up with.
4. **The meApp `DefaultCartesianMarker` subclass**: `B-4b`, `B-7`, `B-8`, then `B-6`'s REPLACE.

**Why here:** the subclass cannot override methods that do not exist, and it sits downstream of `B-11`
(P4), `A12` (P4), `D-1` (P0) and `D-2` (P4). Under P8's rule the subclass moves early relative to its
file's merge — ~290 of our lines never get hand-applied to `DefaultCartesianMarker.kt` at all.
**Why not later:** P14's host plumbing is the subclass's counterparty.
**Carry as a checklist, not a diff:** the crosshair's three `ab3fe3bfc` refinements (start at
`chartStartX()` not `layerBounds.left`; label after the axis line; label in a *later pass* than the
dots); `yLabelCallback`'s empty-list-for-non-line-targets contract; **seven** `internal` helpers to
re-write — `Rect.getStart`, **`Rect.getEnd`**, `Float.half`, `Position.Vertical.inBounds`, both
`unaryMinus` operators, `Dp?.orZero` **[verified]**; the two `protected`-but-not-`open` method bodies
(`drawLabel`, `drawGuideline`) to re-implement.
**Conditional verdicts:** if the subclass is abandoned, `B-6` reverts to RE-APPLY and the seven
null-guards become a checklist. If P1's check 4 showed the `fallbackValues` path never runs, `B-8` is
a DELETE rather than a MOVE.
**Proof:** goldens #29/#30 (BP marker at rest and mid-scrub); the date-range caption appears at rest on
both charts and is replaced by the marker label while marked; the guideline's opacity matches the
design (the only visible trace `D-1` leaves); `CartesianChart.draw()` re-read end to end once at the
end of this phase (CF-5).

---

## P14 — The gesture path and the marker's host plumbing. — **4–8d, uncertain**

**Lands:**
- `B-3a` + `B-3b` + `M-4` — delete `acceptsGestures` and its guard; move `rememberNonInteractive` to meApp with `acceptsLongPress = false`. **Run P1's smoke-gate result before committing.**
- `M-1` RE-DERIVE **in a new file `ScrubGesture.kt`**, reducing `Modifier.kt` to a one-line delegation.
- `M-2` (`enabled = true` + freeze in `ScrollableState`).
- `H-5` + `H-6` + `B-12` + `B-1` + `B-2` as one contract (set 4), onto v3.3.1's changed overload set (`modifier` inserted at position 3, `chartAreaHeight` at 7; **`initialMarkerX` goes only on the non-deprecated overloads**). `H-6` uses upstream's `narrowMarkerTargets` + `MARKER_POSITION_TOLERANCE = 0.5f` and **deletes our exact-equality copy** (which is also S2-C's ULP bug). Keep both halves of `H-5`'s asymmetry: the clear is forced, the restore is conditional, and the effect is keyed on `initialMarkerX`, **not `Unit`**.
- **The `Boolean` return policy, stated once and applied in both directions (set 4):** `onInteraction` is now `(Interaction) -> Boolean` and drives consumption. The scrub branch returns **`true`** when the consumer callback returns `null` — the interaction was accepted, only the marker was declined; returning `false` would leave a clear-tap unconsumed and navigate. Symmetrically, meApp's moved `NonInteractiveMarkerController` returns `false` so the snapshot card still opens the full graph. These are the same mechanism read from opposite ends.
- `H-8` + `M-3` — **Shape A**: re-add `flingBehavior: FlingBehavior? = null` to the public producer overload, to `CartesianChartHostImpl` and to `Modifier.pointerInput`; inside `pointerInput`, prefer the override and fall back to upstream's own `xSnapStep` block. **Net fork surface: +3 parameters, −1 file.** DROP `ScrollAwareRangeEffect`'s unused `flingBehavior` parameter either way.

**Why here:** `D-2` (P4) first so scrub misbehaviour cannot be blamed on a stale target list;
`B-3a`/`M-4` before `M-1` so there is one fewer condition in the `.then` chain to carry through the
rewrite; the host's marker plumbing is the controller's counterparty and the subclass (P13) is its
other end. **Why not later:** P15 cannot adjudicate a chart you cannot scrub.
**Why `M-1` in a new file:** it turns a 234-line conflict surface on the hottest file into about five
lines, permanently — it pays again at the next upgrade.
**Smaller than the draft had it:** `H-7` is gone from this phase entirely (P7), and `H-8` is no longer
an open question.
**Settle here: E-2** — if `scrollValueToX` and `xToScrollValueBehindStartInset` cannot be public,
Shape A still keeps snapping working (the seam is unaffected) but `S1A-05a` reverts MOVE → RE-APPLY
and the fork keeps `SnapFlingBehavior.kt`, **renamed `ChartSnapFlingBehavior.kt`** per C4 — otherwise
`import …cartesian.SnapFlingBehavior` silently resolves to upstream's class.
**Preserve verbatim:** `M-1`'s `Release` exception (keep the explicit state check; discard the returned
`Boolean` on that branch); the `500f` drag/fling velocity threshold and the `4.5f` decay divisor.
**Resist:** using v3.3.1's mutable `scrollEnabled` in place of `isScrollFrozen` — `enabled = false`
detaches the nested-scroll node, the exact defect `3a284be42` fixed.
**Proof:** a 200 ms hold starts a scrub and the parent `LazyColumn` does not move during it; a
horizontal drag on the TOTAL chart does not scroll the dashboard; the snapshot card opens the full
graph on tap; a clear-tap on the dashboard chart does **not** navigate; YEAR still comes to rest on a
month boundary.

---

## P15 — PARITY. T3/T4. — **3–6d, genuinely uncertain**

**Lands:** no code. The full golden set re-rendered against the migrated fork through the composite
build, every diff adjudicated as intended / upstream-silent-change / regression, and the adjudication
written down **per image**.

**Why here:** this is the checkpoint the whole ordering is built around. Nothing below it is required
for the migration, and every item below makes a port defect and a redesign defect look identical.
**The diffs to expect, each already named:** `S-1` the 64 dp auto-height cap on all five surfaces
(Tablet ~133→64, Phone ~92→64, Snapshot ~76→64) · `S-2` `drawOutline` vs `drawPath` antialiasing on
every data point and the pill · `S-3` one offscreen layer for seven 40%-alpha percentile strokes —
the baby chart specifically · `S-7` marker narrowing on the three coincident BP series · `U2` the plot
rect moves because `A12` removed the halving *and* v3.3.1 changed what margins are subtracted from.
**Two checks that must be here and nowhere else:**
- **The chart-open golden must capture the first drawn frame** — `H-7`'s deletion is silent and a settled-frame golden cannot see a one-frame jump.
- **The weight-with-secondary golden must assert the Y-axis minimum on the first frame of a tab entry** — that is the only frame on which a forgotten `contributesYRange` is visible.
**Proof:** every non-empty diff has a written verdict; the device pass (3 products × 4 windows ×
light/dark) is clean.
**Uncertain because:** the cost *is* the count of non-empty diffs, which cannot be known before P1
exists, and `A12`'s margin change alone can cascade through all 36 images.

---

## P16 — Post-parity reductions. — **3–5d, moderate**

**Lands, cheapest and loudest first:** `S1A-10` (`xBehindStartInset` → meApp; `Scroll.kt` stops being
patched) · `S1A-05a` (**gated on E-2**; if it lands, `$C/cartesian/SnapFlingBehavior.kt` is **deleted
outright** and C4's file-name hazard disappears with it — no rename needed) · `S1A-08b` (the `key`
MOVE, **gated on P1's A→B→A result**) · `H-3` (the window-probe `Decoration`) — **last**.

**Why here:** each of these files grafts clean, so porting then moving costs nothing extra, and each
introduces a *new* defect class after parity is already proven. `H-3` is the single largest reduction
in the port — it deletes the `visibleXRanges` flow, the three-scalar dedup and the per-frame emit from
the most-churned file — and the largest silent risk: if the decoration is not installed, or is
installed on a chart that `equals` an older one, the whole scroll-aware feature goes dead with the
chart still drawing a plausible full-range axis. **Set 19's final re-check lands here.**
**Acknowledged exception to P8's rule, stated not hidden:** `H-3` would qualify as "move early" — it
is the largest deletion from the most-churned file — but its dependency chain (`S1A-01` public,
`S1A-06`, `H-1`/`H-2` proven) forbids it. The cost is ~90 lines of `CartesianChartHost.kt` ported and
then deleted.
**CF-4, the decision to take explicitly here and record:** with `H-3` landed, `H-4`'s feed disappears
by the same route, so `B-5` + `H-4` (~40 lines) become deletable. Budget the rework or accept the
patch — **state which; do not let it drift into "we meant to"**.
**Proof:** goldens unchanged; the window probe emits on exactly the frames the host used to; the fade
still appears with `startWidth = 0.dp`.

---

## P17 — Publish 5.0.0 and cut meApp over. T5. — **2–4d, predictable**

**Lands:** publish `5.0.0` from the migration clone; verify the POM/`.module` publication set matches
4.0.46's shape (six publications minus `compose-iosx64`, all six runtime deps declared, `.module`
present); `vico.local=false`; `vico = "5.0.0"` in meApp's catalogue; the four surviving upstream
compile breaks (`animateIn = false` ×2 → `initialAnimationSpec = null`; one-arg `getXStep` ×2 →
three-arg, at `ProductChart.kt:99` and `SnapshotLineChart.kt:149`); delete the two
`@SuppressLint("RestrictedApi")`; rename the four `lineSeries` imports opportunistically; re-render
the goldens against the **published** artifact.

**Why a separate phase:** a composite build bypasses the POM, the `.module` metadata and the AAR
packaging — the three things that took six failed attempts to get right once already.
**Proof:** T5's diff against T3's goldens is **empty**; `:app:assembleDebug` and `:app:lintDebug`
green against the published artifact.

---

## P18 — Deferred. Explicitly not this migration.

The `ranges` decorator parameter on `CartesianChartHost` (~6 lines of theirs would replace ~250 of
ours; needs the `U1` snapshot-invalidation spike) · hoisting `markerX`/`onMarkerXChange` onto the host
(replaces `H-5`, `H-6` and most of `B-1` — **the single best candidate in the host cluster**) ·
`CartesianLayer.getInterpolatedTarget(context, x)` so `C-7`'s maths sits beside the code that draws
the line (also fixes the animation divergence for free) · consolidating the two retained drawing
contexts (`VicoScrollState.drawingContext` + `CartesianChart.drawingContext`) into one published
geometry snapshot · moving `H-2`'s `ScrollAwareRangeEffect` to meApp · `L3` Option B · the upstream PR
list in descending value-per-line: `B-11`'s one-word fix (send first, no design argument attached),
the `CartesianMarker` draw-phase set, `getVisibleXRange`/`getFullXRange` public, the `ranges` seam,
`flingBehavior`, fading edges over unscalable padding, `drawLabel`/`drawGuideline` `open`,
`Position.Vertical.inBounds` + the two `unaryMinus` operators public, `label: TextComponent?`,
`L8`'s point clip.

---

# §4 Ticket shape

The board sizes comparable work at **1d–3d per ticket**, so the phases above are not tickets: P7
alone is 6–12 days. Below, the eighteen phases become **37 tickets in four chunks**. Each chunk is a
coherent thing to merge; the chunks are listed in the order they must be worked.

**Shippable** in the table below means: this ticket could merge and release on its own, with the
library still correct and meApp still green, without waiting for the rest of the chunk.

## Chunk A — Foundations (meApp-side only; nothing touches the published library) — **separately shippable as a whole, and ticket by ticket**

| # | Title | Covers | Est. | Depends on | Acceptance criterion |
|---|---|---|---|---|---|
| A1 | Spike: is vico Compose rendering Paparazzi-clean at 4.0.46? | P0 / BL-1 | 1–2d | — | One test renders `rememberProductChart`; all three pass criteria met, including `verifyPaparazziDebug` green on a clean CI runner. A failure is a documented recommendation for the AGP fallback, not a blocked ticket |
| A2 | Spike: composite-build substitution and toolchain at v3.3.1 | P0 / BL-2, BL-3 | 1d | — | `dependencyInsight` resolves `com.dmdbrands.lib:compose-android` to `project :vico:compose` across AGP 9.3.2/9.5.1; stock v3.3.1 compiles at Kotlin 2.4.10 (or the `-Xcontext-parameters` flag is recorded) |
| A3 | Migration decisions: `D-1`, the start gap, `L3` Option A | P0 / BL-6, BL-7 | 1d | — | Four answers written into `PLAN.md`, each with its reason; the start gap has a product sign-off |
| A4 | Golden harness: fixtures and the two test classes | P1 | 2–3d | A1 | `ChartFixtures.kt` with fixed timestamps/locale/TZ; two consecutive `recordPaparazziDebug` runs byte-identical |
| A5 | The 36 T0 goldens, incl. first-drawn-frame capture | P1 | 2–3d | A4 | 36 PNGs committed; `verifyPaparazziDebug` green on CI; chart-open and tab-entry cases capturable at frame 1 |
| A6 | The six 4.0.46-only checks + BL-8's placer logging | P1 | 1–2d | A4 | Eight written results: smoke gate, lint without the suppressions, `L12b` rotation, `yLabelCallback` fallback, A→B→A, pill width at fontScale 1.3, and the `maxX`/`fullXRange` label list on WEEK and TOTAL |

## Chunk B — The library port to parity — **only makes sense merged as a whole; do not release a partial B**

| # | Title | Covers | Est. | Depends on | Acceptance criterion |
|---|---|---|---|---|---|
| B1 | Migration clone and build rebase onto v3.3.1 | P2 (bld-B1…B12) | 1–3d | A2 | `:vico:compose:assemble` green on stock v3.3.1 + fork build config; publish task set recorded |
| B2 | Formatting normalisation (ktfmt 0.64, `L15`) | P3 | 1–2d | B1 | `git diff` with and without `-w` gives the same hunk count on the two hottest files |
| B3 | The bookkeeping pass: 18 do-not-apply rows + doc deletions | P4 | 1–2d | B2 | A written per-row record; `CartesianLayerMargins.kt`, `CartesianDrawingContext.kt`, `CartesianChartModel.kt` absent from `git diff v3.3.1..HEAD` |
| B4 | `B-11`: `TextComponent` measures `measuredText` (+ upstream PR) | P4 | 0.5–1d | B2 | Own commit, nothing else in it; upstream PR opened; the `pad` branch re-read by hand after the merge |
| B5 | `D-2`: restore the marker-target sort, invalidate the cache | P4 | 1d | B3 | A two-layer chart's marker returns both series; the stale `TreeMap` comment is gone |
| B6 | `L13`, `H-10`'s import cleanup, and the `E-3` read | P4 | 1d | B3 | Build green — which is itself the proof, since `Defaults.CHART_HEIGHT` does not resolve at v3.3.1; `E-3` has a written verdict |
| B7 | The clean-graft contracts (`S1A-04/11/12/13`, `B-10`, `L7`, `L11`, `L12a`) | P5 | 1–2d | B3 | Build green; `MutableCartesianChartRangesTest` green plus a new case for the `toImmutable()` copy |
| B8 | `VicoScrollState`: `S1A-06` + `S1A-07` | P6 | 1–3d | B2 | The three widened conversions resolve from meApp through the composite; `initialScrollHandled` untouched |
| B9 | `ScrollAwareRangeProvider` + `Window`/`WindowKey` re-apply | P7 (`S1A-01`, `S1A-02`) | 2–3d | B7, B8 | Provider compiles with public members; build green; no behaviour yet |
| B10 | `H-1`: host-owned range recomputation and opening range | P7 (`H-1`, `S1A-03`) | 3–5d | B9 | The Y axis follows the visible window on a WEEK scroll; the three named regressions re-proved by hand |
| B11 | `H-2` animated overlay + `H-9` + `S1A-09a` + the `E-1` observation | P7 | 2–4d | B10 | Full golden set re-rendered and adjudicated; `getVisibleAxisLabels` returns the drawn labels, not the even walk |
| B12 | `A1`: separators leave for a meApp `Decoration` (+ set-19 instrumentation) | P8 | 1–2d | B11 | Separator golden unchanged; the `remember(chart, model)` log fires once per genuine change, never per frame |
| B13 | `A11`: `ListItemPlacer` moves to meApp | P8 | 1d | B11 | Golden pixel-clean (this ticket validates P8's move-early rule); no `Float.half` import remains |
| B14 | The interpolator unit: `L2b` → `L1` → `S1A-09b` → `L2a` | P8 (set 18) | 2–3d | B13 | Three meApp call sites updated; `visiblePadding = 1` declared; a **scrolling** golden shows the edge segments unchanged |
| B15 | `L14a` + `L14b`: `Defaults.kt` leaves the fork | P8 | 1d | B11 | `common/Defaults.kt` absent from the fork diff; axis band height unchanged |
| B16 | Axes I: `A2a`, `A2c`, `A9`, `A10`, `A7a` | P9 | 2–3d | B12–B15 | `A10` exercised on an empty chart and an all-same-y chart; gutter labels centred |
| B17 | Axes II: `A2b` + `A3c` re-derived, `A4b`, `A7b`'s axis half | P9 | 2–3d | B16 | The three static greps return nothing; WEEK/YEAR labels at scroll 0/mid/max neither clipped nor overlapping |
| B18 | Line layer I: `L9` + `L8`, `L6` (+ `CartesianChart.kt:576`) | P10 | 1–2d | B14, B17 | No dot outside the plot, none dropped; the scrub does not land on a percentile curve |
| B19 | Line layer II: `L5` as `isStable`, with the `rememberFollowing` split | P10 | 2–3d | B18 | Three providers answer three ways; the percentile band shows **no** frame-0 Y-axis flash |
| B20 | Line layer III: `L3` seam + the meApp `CartesianLayerYMapper` | P10 | 3–5d | B19 | Golden #31 unchanged; `U4` instrumented — the fade returns to 0 across two windows that round to the same axis |
| B21 | Line layer IV: `L4` as a non-defaulted `contributesYRange` | P10 | 1d | B20 | Omitting it is a **compile error**; the first frame of a weight-with-secondary tab opens at the right Y minimum |
| B22 | Start of chart: `C-2` + `C-3` + `A8` | P11 | 2–3d | B21 | Golden #36 and the start-edge goldens; no second line on the start axis; the `layerPadding` lambda is non-capturing |
| B23 | Chart edge: `A3a` + `A4a` + `A5` + `C-1` | P11 | 2–3d | B22 | Eight-day week does not return **and the last date label is still drawn**; a new meApp test for the half-open rule; `unscalableStartPadding == ChartStartGap.pixels` on all four segments |
| B24 | Draw order and the goal pill: `C-4`, `C-5`, `A6`, `A6′` | P12 | 2–3d | B23 | Golden #32 at a y near the canvas bottom; pill not clipped at fontScale 1.3; `drawMarkerDecorations` deleted |
| B25 | Marker interface set: `B-4a`, `B-5`, `drawLabels`, `C-6` (+ upstream PR) | P13 | 1–2d | B24 | One additive defaulted change; the guideline draws at single alpha; one upstream PR opened |
| B26 | `C-7` synthesised targets + `H-4` settle feed | P13 | 2–3d | B25 | A marker lands on an x between two points; the caption appears at rest on both charts and does not re-format per frame |
| B27 | The meApp marker subclass: `B-4b`, `B-7`, `B-8`, `B-6` | P13 | 3–4d | B26 | Goldens #29/#30; seven internal helpers re-written; `DefaultCartesianMarker.kt` carries none of our ~290 lines |
| B28 | Gesture rewrite: `M-1` in `ScrubGesture.kt`, `M-2`, `B-3a`/`B-3b`/`M-4` | P14 | 3–4d | B27 | 200 ms hold scrubs without the `LazyColumn` moving; the snapshot card opens the full graph; `Modifier.kt` is a one-line delegation |
| B29 | Host marker plumbing: `H-5`, `H-6`, `B-1`, `B-2`, `B-12` | P14 | 2–3d | B28 | A clear-tap does not navigate; a release after a scrub does not clear the marker; a window with no entries still marks |
| B30 | `H-8`/`M-3` Shape A seam + the `E-2` read | P14 | 1–2d | B29 | YEAR comes to rest on a month boundary; `+3` parameters is the whole library cost; `E-2` answered |

## Chunk C — Parity and release — **ships as one; C2 is the release**

| # | Title | Covers | Est. | Depends on | Acceptance criterion |
|---|---|---|---|---|---|
| C1 | Parity adjudication of the full golden set | P15 | 3–6d | B30 | Every non-empty diff has a written verdict; the first-drawn-frame and first-frame-of-tab assertions are both in the set; device pass clean |
| C2 | Publish 5.0.0 and cut meApp over | P17 | 2–4d | C1 | T5's diff against T3 is empty; `:app:assembleDebug` and `:app:lintDebug` green against the published artifact |

## Chunk D — Post-parity reductions — **separately shippable, as 5.1.0, after Chunk C**

| # | Title | Covers | Est. | Depends on | Acceptance criterion |
|---|---|---|---|---|---|
| D1 | Cheap moves: `S1A-10`, `S1A-08b`, `S1A-05a` | P16 | 2–3d | C1 | `Scroll.kt` unpatched; `SnapFlingBehavior.kt` deleted (or renamed if `E-2` said no); A→B→A matches P1's baseline |
| D2 | `H-3`: the window probe becomes a `Decoration` (+ the CF-4 decision) | P16 | 2–3d | D1 | ~90 lines leave `CartesianChartHost.kt`; the probe emits on exactly the frames the host used to; CF-4 recorded either way |

## Reconciling the ticket numbers with §6

The 37 tickets sum to **~66–106 days**; §6's phases sum to **51–99**. The gap is at the *bottom* end
and it is structural, not a disagreement: a board ticket does not go below ~1 day, so thirty tickets
carry thirty roundings-up, and the chunk boundaries add handover. **§6's range is the honest estimate
of the work; the ticket range is what a board will actually commit to.** Quote §6 for planning and
the tickets for the sprint; do not add them together or average them.

## How to split this if the board wants chunks rather than one epic

- **Chunk A merges into meApp on its own** and is worth having regardless of the migration: it is the first visual regression testing the app has ever had. If the migration were cancelled tomorrow, A1–A6 would still be the right work.
- **Chunk B cannot be released partially.** Every phase boundary inside it is green and renderable, so it can be *reviewed* in 30 slices and merged to a long-lived migration branch — but a 5.0.0 cut anywhere inside B ships a chart that is missing a feature it had at 4.0.46.
- **Chunk C is the release.**
- **Chunk D is a follow-up release.** Deferring it leaves ~90 host lines and the three moved files in the fork. BASELINE decision 2 says take it; it is separable if the calendar demands.
- `B4` (the `TextComponent` fix) is the one ticket inside Chunk B that is **independently shippable upstream** — send the PR the day it lands, not at the end.

---

# §5 Blockers and open questions

**The count moved from 7 to 5.** The draft listed BL-1…BL-7 as blocking. **BL-4** (the `flingBehavior`
seam) and **BL-5** (`A7b` vs `S1A-08c`/`H-7`) are resolved from source by S3-E §3 and §4, and both
removed work rather than adding it. Nothing found by S5-A or S3-E was promoted *into* the pre-start
set: every new question S5-A raised (`A4a`'s form, the pill width, the `decorations` stability) has a
check that runs inside P1 or P8, both of which are already gated.

## 5.1 Must settle before work starts — 5

| # | Question | The exact check | Who or what answers it |
|---|---|---|---|
| **BL-1** | Is vico's Compose rendering Paparazzi-clean at 4.0.46? | The one-test spike, **all three** pass criteria; criterion 3 is `verifyPaparazziDebug` green on a clean CI runner. Upstream ships 11 committed Layoutlib PNGs — strong evidence, not proof for us. Remember coroutines do not run under preview rendering, so the model must be pushed synchronously | A developer, one day. If (1) fails, try AGP Compose Screenshot Testing mirroring upstream's config **before** concluding Layoutlib cannot render vico |
| **BL-2** | Does `substitute(module("com.dmdbrands.lib:compose-android")).using(project(":vico:compose"))` resolve at v3.3.1's module layout, across AGP 9.3.2 (library) and 9.5.1 (app)? | `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency com.dmdbrands.lib:compose-android` in the composite | A developer, hours. Three ordered fallbacks are already written down (S3-D app. 7) |
| **BL-3** | Are Kotlin context parameters on by default at 2.4.10? Upstream relies on them with no opt-in flag | `./gradlew :vico:compose:compileKotlinAndroid` in the migration clone, on **stock** v3.3.1 | The compiler. Failure → add `-Xcontext-parameters`; **do not downgrade Kotlin** |
| **BL-6** | `D-1`: add `CartesianMarker.drawLabels`, or collapse to upstream's single late call? | A decision, not a spike. The meApp subclass's method split **is** the fix, so it cannot be taken after the subclass is written. If the extra member is judged too much patch surface, state the cost explicitly: indicator dots paint over the layer bitmap | The fork owner. Also shapes the upstream PR in B25 |
| **BL-7** | The start-gap arithmetic: `12.dp` or `12.dp + maxPointSize/2`? | Look at both on a device. A **product** question about a few dp, not a code question | Product / design. Start asking at P0 — it gates P11 (B22/B23) and `S1A-11`'s visual check, and a human in a loop is the long-pole risk |

## 5.2 Settle during, named phase — 8

| # | Question | Settles in | The exact check | Gates |
|---|---|---|---|---|
| **BL-8** | Can `A4a` be a MOVE, or must the guard deletion stay in the library? | checked **P1**, decided **P11** | On WEEK and TOTAL, log `ranges.maxX`, `fullXRange.endInclusive` and the placer's label list at scroll positions where `maxX` sits strictly inside the visible window. If `maxX` is in the list today and not after a delegate-and-filter implementation, the last date label is being lost | `A4a`'s verdict; B23's shape |
| **BL-9** | Is `A6′` a free DELETE, or does the goal pill exceed the 50 dp gutter? | measured **P1**, executed **P12** | Render the weight dashboard at `fontScale = 1.3` with the longest signed weightless goal label; measure the pill's `getWidth` against `YGutterWidth = 50.dp`. A 3-char label at 1.3× is ≈46 dp; a 4-char signed one ≈54 dp | `A6′`; whether `YGutterWidth` widens in B24 |
| **BL-10** | Is the `decorations` list (and the `layerPadding` lambda) stable enough not to re-seed the range? | instrumented **P8**, re-read **P12** and **P16** | Log every entry into the host's `remember(chart, model) { ranges.reset(); … }`; exercise scroll, segment switch, goal edit, unit toggle, rotation. It must fire once per genuine model/config change and **never** per frame or per recomposition | `A1`, `A6`, `H-3`, `C-2` jointly (set 19). **Assign the owner at P8; it is the one risk no cluster owned** |
| **E-1** | Does upstream's layout-pass `measure()` interact badly with the fork's additions (`C-7`'s retained context, `allMarkerTargetXValues`)? | **P7** | On the first green host build, log `_markerTargets.size` and `drawingContext != null` at the top of `measure()` and of `draw()` for one frame. Expect `measure()` twice per frame and `drawingContext` null in both | Nothing — an observation. Cost: one read |
| **E-2** | Can `scrollValueToX` and `xToScrollValueBehindStartInset` be public? | **P14**, before `S1A-05a` is scheduled | Read the two signatures against what the fork is willing to publish | `S1A-05a` (D1). If no, snap stays and `SnapFlingBehavior.kt` is **renamed**, not deleted |
| **E-3** | Is `3b9146ca8`'s "alpha hiding" removal obsoleted by v3.3.1's `initialAnimationSpec` reveal? | **P4** | Read v3.3.1's `collectAsState(chart, animationSpec, initialAnimationSpec, mutableRanges)` path end to end against the comment at `HEAD CartesianChartHost.kt:186-187`. **Do not fold it into `H-10`** | Whether a behaviour is silently dropped in B6 |
| **U-1** | Does the `yLabelCallback` fallback path ever run? | **P1**, applied **P13** | Instrument `GraphMarker.rememberDefaultMarker`'s `yLabelCallback`; scrub a WEEK view with sparse readings. One session's use is enough | `B-8` MOVE vs DELETE |
| **CF-4** | With `H-3` landed, do `B-5` + `H-4` (~40 lines) get deleted or kept? | **P16** | Nothing to check — a budget decision. Take it explicitly and write down which way | D2's scope |

**Informing, not blocking** (settle inside the phase that touches them, no decision needed first):
`U1` (`A8` vs `lineBounds` — verified inside P11) · `U2`/`U5` (`A12`'s measured height; `End` labels
with `shiftExtremeLabels` — answered by P15's diff and a P11 screenshot) · `U3` (dashed lines and
`visiblePadding` — one grep) · `U4` (is the fade ever stalled — instrumented in P10) · `U-2`
(`markerTargets` iteration order — gates only the sort *optimisation*; restoring the sort is safe
regardless) · S3-D app. 2 (publish task name — P2), app. 3 (does the 64 dp cap bind — a P15 diff to
adjudicate, not a decision), app. 8 (GPR version ordering — informational), app. 9 (which `getXStep`
overload binds — a compile, and moot either way).

**One consolidation worth acting on, unchanged from the draft and now with a sixth member:** six of
these can **only** be answered against a running, un-migrated 4.0.46 build — the `acceptsGestures`
smoke gate, the lint run, the `L12b` rotation check, the `yLabelCallback` instrumentation, the A→B→A
baseline, and **BL-9's pill measurement** — plus **BL-8's placer logging**. They sit in five different
dossiers and none of them says so. All of them are scheduled into **P1**, beside the goldens, for the
same reason the goldens are there.

---

# §6 Estimate

One senior Android developer, working alone, excluding the upstream PRs (P18).

| Phase | Days | vs draft | Confidence | What dominates the number |
|---|---|---|---|---|
| P0 Settle blockers | **2–3** | ↓ from 2–4 | moderate | Two of the seven blockers were resolved from source; what is left is the Paparazzi spike, two compiles and three decisions |
| P1 T0 goldens + the 4.0.46-only checks | **5–8** | ↑ from 4–7 | **genuinely uncertain** | `ChartFixtures.kt` is the cost, not the 36 images. **+1d** for first-drawn-frame capture (needed by `H-7`) and two new measurements (BL-8, BL-9) |
| P2 Clone + build rebase | **1–3** | = | predictable | Enumerated to the file; the risk is three AGP versions in one composite |
| P3 Formatting normalisation | **1–2** | = | predictable | Mechanical over 14 files; fiddly (ktfmt pin, star imports) |
| P4 No-ops + `B-11` + `D-2` + `L13` + `H-10` | **2–4** | = | predictable | 18 "do not apply" rows cost bookkeeping; four real applies; `D-2`'s invalidation needs thought |
| P5 Clean-graft contracts | **1–2** | = | predictable | 8 items, <300 lines, on untouched or byte-identical files |
| P6 `VicoScrollState` | **1–3** | ↓ from 2–3 | predictable | Hot file, but one hunk and one coupled-edit hazard were removed by CF-1's resolution |
| P7 Host core | **6–12** | = | **genuinely uncertain** | `H-1` is the hardest single item; `H-2` ~250 lines; `prepare` re-signatured, `Canvas` moved, `animateIn` → `initialAnimationSpec`. `H-7`/`H-9` are small and now certain, which does not shrink the hard part |
| P8 Hot-file reductions | **3–6** | ↓ from 4–7 | moderate | Loses `A3a`/`A4a` and their new test to P11; keeps four MOVEs, a new `Decoration` and the set-19 instrumentation |
| P9 Axes | **3–5** | = | moderate | `A2b`+`A3c` is real design work against upstream's per-index `when`; the rest lands on a surface P8 shrank |
| P10 Line layer | **5–10** | ↑ from 5–9 | **genuinely uncertain** | The hardest file. `L3` Option A is a new seam plus ~120 meApp lines; **+`L5`'s `rememberFollowing` split**, which S3-C had as a one-liner. Zero existing coverage on `yTransform` |
| P11 Start-of-chart + chart-edge | **3–6** | ↑ from 2–4 | moderate | Four coupled items became **seven** (`A3a`, `A4a`, `A5` arrive), plus the new half-open-window test and `A4a`'s conditional form |
| P12 Draw order + pill | **1–3** | = | predictable | Two clean grafts and one statement moved; `A6′` may add a gutter change |
| P13 Marker | **5–9** | = | uncertain | The meApp subclass is the largest single piece; seven internal helpers, two non-`open` bodies. `C-7` is now a verified graft rather than an open question, which offsets `H-4`'s arrival |
| P14 Gesture + host plumbing | **4–8** | ↓ from 5–9 | uncertain | `M-1` is still a 180-line re-derivation against a `Boolean` contract — but `H-7` left this phase and `H-8` is now a costed shape, not an open question |
| P15 Parity (T3/T4) | **3–6** | = | **genuinely uncertain** | The cost is the count of non-empty diffs, unknowable before P1 exists |
| P16 Post-parity reductions | **3–5** | = | moderate | Four cheap moves plus `H-3`, the largest reduction and the largest silent risk |
| P17 Publish + cutover | **2–4** | = | predictable | Mechanical, but the publication configuration took six failed attempts once before |
| | **51–99** | draft: 52–98 | | ≈ **10–20 working weeks**, midpoint ~15 weeks |

## Did the corrections move the estimate?

**No — not materially, and that is the honest answer.** 51–99 against the draft's 52–98 is inside the
noise of a range this wide. What the corrections moved is the **shape**:

- **Two blocking unknowns were closed from source** (BL-4, BL-5), and both closed in the cheap direction: `H-7` is deleted rather than re-derived onto upstream's `measure`/`getVerticalExtras` split, and the `flingBehavior` seam is a costed +3 parameters / −1 file rather than an open design question. That is where P6, P14 and P0 each lost a little.
- **Work was converted from unknown to known, not removed.** `C-7` went from "the largest orphan, needs a verdict before P13" to a graft with six written amendments. `H-4`, `H-5`, `H-6`, `H-8`, `H-9`, `H-10` went from "no verdict anywhere" to verdicts with line numbers. None of that is free; it is just no longer a risk of discovering the work mid-phase.
- **Three corrections added real work**: `L5`'s `rememberFollowing` split (P10), `A4a`+`A5`+`A3a` becoming one unit with its own test (P11), and first-drawn-frame golden capture (P1). Those are the ↑s.
- **The meApp side grew from 22 API rows to 40** without growing the estimate, because most of the 18 new rows document MOVEs that were already budgeted inside their phases. The three *removals* (`yTransform`, `alwaysUseLiveRange`, and `contributesYRange`'s arrival) are the ones that were genuinely unbudgeted, and they sit in P10's ↑.

**Predictable phases: P2, P3, P4, P5, P6, P12, P17.** The work is enumerated down to line numbers and
the files are untouched upstream, byte-identical, or already shrunk. Their risk is clerical — applying
half of a coupled pair — which is exactly what §2.3's nineteen sets exist to prevent.

**Genuinely uncertain: P1, P7, P10, P15** (and P13/P14 are "uncertain" one notch below).
- **P1** rests entirely on BL-1. A failed Paparazzi spike is a *different plan*, not a longer one.
- **P7 and P10** are RE-DERIVE work: the intent survives, the code cannot be ported, and the target functions were rewritten upstream. You cannot estimate a re-derivation by counting the diff — the diff tells you what *was*, not what the new shape costs. Both carry defects that are silent by construction, so "done" means "goldens agree", not "it compiles".
- **P13 and P14** are RE-DERIVE *plus* the largest meApp-side builds in the plan, against contracts that changed shape (`(Interaction) -> Boolean`, a private tap detector, a changed overload set).
- **P15** cannot be estimated at all until P1 exists.

## The one compression lever, stated honestly

Deferring Chunk D (P16) and the P13 subclass to a follow-up release cuts roughly **10–18 days** and
leaves ~290 marker lines plus ~90 host lines in the fork. BASELINE decision 2 — *minimise the fork; a
smaller fork surface is worth a slower port* — says not to take it. It is recorded so the trade is
visible, not recommended. Chunk D alone (5 days) is the separable half: it is a clean follow-up
release and does not touch the parity proof.

**What would most change these numbers, in order:** BL-1 (a failed spike re-shapes P1 and P15),
the count of non-empty diffs at P15, and BL-7 (a product decision with a human in the loop is the only
dependency in this plan that a developer cannot unblock alone).
