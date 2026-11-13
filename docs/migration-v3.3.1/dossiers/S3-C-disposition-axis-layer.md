# S3-C — Disposition: axes, item placers, line layer, interpolation, model pipeline, chart composition

Stage 3 decision dossier. Inputs: `BASELINE.md`, `CONTRADICTIONS.md`, `S1-C-axis.md` (A1–A12),
`S1-D-layer-data.md` (L1–L16), `S1-E-host-build.md` (C-1..C-5, H-7), `S2-A/B/C/D`.
Every verdict below is checked against the **v3.3.1 source**, not against the Phase-1 narrative.
Read-only throughout (`git show` / `git grep` / `git diff`); no working tree was touched.

Library paths are relative to `vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/`.
meApp paths are relative to `/Users/selvakumar/Projects/meApp-bug/Android/app/src/main/java/com/dmdbrands/gurus/weight/`.

---

## Three Phase-1 premises this dossier corrects

Recorded first, because three verdicts rest on them.

**P1 — `LineCartesianLayer` does NOT pre-double to compensate for the margin halving.**
S1-C A12 and the Stage-3 brief both state it does. At `HEAD`,
`cartesian/layer/LineCartesianLayer.kt:1098-1108` computes `verticalMargin = max(stroke, point).half`
and passes it straight through. `14542561e` added the `* 2`; `a2a9a7690` reverted it and never
restored it (S1-D L9 records the revert; S1-C A12 did not). **Consequence: with the halving in
place, our fork currently reserves a quarter of the widest stroke/point, not half.** That is a live
under-reservation today, and it removes the last argument for keeping the halving.

**P2 — `HorizontalAxis.updateLayerDimensions` gained no new responsibility at v3.3.1, and upstream
added a supported switch to turn it off.** Body at `cartesian/axis/HorizontalAxis.kt:568-621`; the
only additions in `e67e49520..v3.3.1` are `val label = label ?: return` (:572) and
`if (itemPlacer.getShiftExtremeLabels(context)) return` (:573). Its callers are still exactly
`CartesianChart.kt:322-325`. See (b) below.

**P3 — `CartesianChart.draw()` is byte-identical between `e67e49520` and `v3.3.1`.** Verified by
diffing the extracted method. (The file as a whole is 228+/18−, all of it outside `draw`.)
C-3, C-4 and C-5 therefore graft mechanically.

---

## Verdict table

| id | Adaptation | Verdict | Risk if wrong | Blocked by / blocks |
|---|---|---|---|---|
| **A1** | `HorizontalAxis.Separators` | **MOVE** → meApp `Decoration.drawUnderLayers` | **silent** (rules at wrong x, or absent) | blocks nothing; frees the largest additive block in `HorizontalAxis.kt` |
| **A2a** | `LabelVerticalMode` | **RE-APPLY** (default → `Outside`, not `Center`) | silent (labels in the wrong band) | — |
| **A2b** | `labelHorizontalPosition` | **RE-DERIVE** as `Position.Horizontal?` over upstream's new per-index `when` | silent (extreme labels blank — see A3c) | blocks A3c |
| **A2c** | `getHeight` Bottom-only `lineThickness` | **RE-APPLY** (decide on its own merits) | silent (axis band 1 px off) | — |
| **A3a** | `updateLayerDimensions` → `Unit = Unit` | **REPLACE** → `ItemPlacer.aligned(shiftExtremeLabels = true)` in meApp | **silent** (plot inset by half a label) | blocked by A4a (same meApp placer) |
| **A3b** | `clipPath` → `clipRect` on item-placer margins | **DELETE** | silent (clip too wide/narrow) | blocks A4b |
| **A3c** | drop per-label `maxWidth` | **RE-DERIVE** with A2b | **silent** (first/last label blanks) | blocked by A2b |
| **A4a** | half-open window in the item placers | **MOVE** → meApp `HorizontalItemPlacer` | **silent** (the 8-day bug returns) | blocked by nothing; pairs with A3a |
| **A4a′** | the `SegmentedHorizontalAxisItemPlacer` half of it | **DELETE** | none (dead for the only consumer) | — |
| **A4b** | label clip clamped to `layerBounds` | **RE-DERIVE** onto upstream's `lineLeft`/`lineRight` | silent (label without a line under it) | blocked by A3b |
| **A5** | `isChartEdge` guideline suppression | **RE-APPLY** | silent (doubled line on the start axis) | blocked by C-2 |
| **A6** | `VerticalAxis.MarkerDecoration` (goal pill) | **REPLACE** → meApp `Decoration` + one statement moved in `draw()` | **silent** (pill mispositioned or sliced) | blocked by C-4/C-5; deletes `AxisManager.drawMarkerDecorations` |
| **A6′** | `getMarkerDecorationWidth` gutter widening | **DELETE** | none (inert: gutter is `Fixed`/`Scroll(50.dp)`) | — |
| **A7a** | `Size.Scroll` case + unconditional guidelines + line/tick clip | **RE-APPLY** (narrowed) | silent (gridlines stop short of the plot edge) | — |
| **A7b** | `isLabelsScrollable`, `scrollValue`/`maxScroll`, `updateScrollState`, `effectiveScroll` | **DELETE** | none (unreachable for the only consumer) | **unblocks H-7's first prepare pass — delete that too** |
| **A8** | `chartEdgeX()` + `drawLineOnly` + `drawStartAxisLine` | **RE-APPLY** as one unit with A5 + C-2 + C-3 | **silent** (start/end line in the wrong place) | blocked by C-2; see UNRESOLVED U1 |
| **A9** | vertical-axis labels centred in the gutter | **RE-DERIVE** as a parameter, not a hard-code | silent (labels hug one side) | — |
| **A10** | NaN guards on a zero-length y range | **RE-APPLY** | **loud** (NaN canvas y / blank draw) | — |
| **A11** | `ListItemPlacer` | **MOVE** → meApp | silent (ticks disagree with labels) | — |
| **A12** | `ensureValuesAtLeast` halving + `ensureVerticalValuesAtLeast` | **DELETE** (both) | **silent, 6 call sites** | **blocks the marker cluster** |
| **L1** | `MonotoneInterpolator` | **MOVE** → meApp, **declaring `visiblePadding = 1`** | **silent** (edge segments change shape on scroll) | blocked by L2b |
| **L2a** | `InterpolationType` enum | **MOVE** → meApp | none | scroll cluster owns `getInterpolatedYValues` |
| **L2b** | `getYAtXFromEntries` | **REPLACE** → one defaulted `getYAt` on `Interpolator` | silent (phantom flat markers) | blocks L1 |
| **L3** | `yTransform` + fade state machine | **RE-DERIVE** as a one-method seam (full section below) | **silent** (overlay in raw units / off-plot) | blocks L4, L7; blocked by L5 |
| **L4** | transformed layer withheld from the shared y range | **RE-DERIVE** as an explicit flag | **silent** (secondary metric decides the primary's axis) | blocked by `MutableCartesianChartRanges` (scroll cluster) |
| **L5** | `alwaysUseLiveRange` + drawing-model bypass | **RE-DERIVE** (upstream now reads the drawing model for the reveal sweep) | **silent** (line and axis disagree; or the sweep breaks) | blocks L3 |
| **L6** | `markerTargetsEnabled` | **RE-APPLY** as a constructor `val` in `equals`/`hashCode` | silent (scrub lands on a percentile curve) | must land with `CartesianChart.kt:576` |
| **L7** | `internalRangeProvider` / `internalVerticalAxisPosition` | **RE-APPLY** | loud (compile) | may become DELETE after L3 |
| **L8** | keep points inside the plot | **RE-DERIVE** (upstream rewrote `forEachPointInBounds` to a visible slice) | silent (dot outside the plot, or a dropped dot) | — |
| **L9** | point size no longer pads the plot | **RE-APPLY** | silent (plot inset by half a dot) | — |
| **L10** | `getVisibleXRange()` made public | **DELETE** (revert to `internal`) | none | **free win — removes a whole file from the conflict surface** |
| **L11** | `isReady: StateFlow<Boolean>` | **RE-APPLY** verbatim | loud (skeleton never dismisses) | — |
| **L12a** | `animate` on `runTransaction` | **RE-APPLY** | silent (points slide between unrelated date ranges) | — |
| **L12b** | `registerForUpdates(animate = false)` | **DELETE** | silent, **global** | see (f) |
| **L13** | `InlineUpdateContext` rename | **REPLACE** (keep upstream's `PreviewContext`; add a second element) | none | **free win — removes `CartesianChartModel.kt` from the conflict surface** |
| **L14a** | `AXIS_LABEL_VERTICAL_PADDING: 4 → 2` | **MOVE** → `rememberAxisLabelComponent(padding = …)` in meApp | silent (axis band 2 dp taller) | — |
| **L14b** | `Animation.RANGE_ANIM_DURATION` | **MOVE** → `ScrollAwareRangeProvider` | none | **removes `Defaults.kt` from the fork** |
| **L15** | whole-file reformat of `LineCartesianLayer.kt` | **DELETE** (never port) | none, but it buries everything else | **do this first** |
| **L16** | `pointSpacingProvider` | **DELETE** (no code exists) | none | — |
| **C-1** | `visibleLabelsCount` + label-spacing scale | **RE-APPLY**, re-sited into v3.3.1's `measure`/`prepare` split | silent (wrong number of periods per window) | — |
| **C-2** | `startInsetDp` | **MOVE** → `layerPadding = { CartesianLayerPadding(unscalableStart = …) }` in meApp (C3 resolved) | silent (gap collapses; A5/A8/C-3 all misplace) | blocks A5, A8, C-3, C-1 |
| **C-3** | start-strip clip + `drawStartAxisLine` | **RE-APPLY** (clean graft, P3) | silent (data painted in the reserved strip) | blocked by C-2 |
| **C-4** | draw order: data on top of what describes it | **RE-APPLY** (clean graft, P3) | silent (axis over the point it describes) | blocks A6 |
| **C-5** | decorations drawn past the fading-edge layer | **RE-DERIVE** — move the *decorations-over* pass there, not a bespoke axis pass | silent (pill sliced by the fade layer) | enables A6 |

**Tally: 10 DELETE · 7 MOVE · 4 REPLACE · 13 RE-APPLY · 9 RE-DERIVE.**
Files that stop being patched altogether: `cartesian/layer/CartesianLayerMargins.kt` (A12),
`cartesian/CartesianDrawingContext.kt` (L10), `cartesian/data/CartesianChartModel.kt` (L13),
`common/Defaults.kt` (L14), `cartesian/axis/AxisManager.kt` (A6 + A8 leaves only `drawStartAxisLine`
— one method, down from two), `cartesian/axis/ListItemPlacer.kt` (A11, deleted outright),
`cartesian/layer/MonotoneInterpolator.kt` + `cartesian/InterpolationType.kt` (L1/L2a, deleted outright).

---

# The six items the brief called out

## (b) `HorizontalAxis.updateLayerDimensions` — what it does at v3.3.1, and the free way out

**What it does.** `cartesian/axis/HorizontalAxis.kt:568-621`. It measures the first and last label
values (`itemPlacer.getFirstLabelValue` / `getLastLabelValue`), formats each, takes **half** its
padded width, subtracts the distance to `ranges.minX`/`maxX` when `!context.zoomEnabled`, and calls
`layerDimensions.ensureValuesAtLeast(unscalableStartPadding = …)` (:600) and
`(unscalableEndPadding = …)` (:620). That is its whole job. It shifts the plot right by half the
first label's width — exactly what FEATURE-9 removed.

**Did anything new come to depend on it? No.** Callers at v3.3.1 are
`CartesianChart.kt:322, 323, 324, 325` — the same four as at the fork point, inside `measure()`.
`VerticalAxis.updateLayerDimensions` is an empty body (`VerticalAxis.kt:383-386`). The only changes
to the method in `e67e49520..v3.3.1` are two early returns:

```kotlin
572    val label = label ?: return
573    if (itemPlacer.getShiftExtremeLabels(context)) return
```

**Does `ItemPlacer.aligned(shiftExtremeLabels = true)` change the picture? Yes — decisively.**
`getShiftExtremeLabels` is a new defaulted member (`HorizontalAxis.kt:888`, `= false`), settable
through the public factory `aligned(spacing, offset, shiftExtremeLines, addExtremeLabelPadding,
shiftExtremeLabels)` (:919-932). It has **exactly two** behavioural uses in the file
(`git grep -n getShiftExtremeLabels` → :194 and :573):

1. `:573` — the early return above. **This is A3a, supplied by upstream, from the consumer, with
   zero library lines.**
2. `:194-218` — the label loop: with it on, index 0 draws `Position.Horizontal.End`, the last index
   `Position.Horizontal.Start`, the rest `Center`, and `maxWidth` becomes one-sided
   (`ceil((nextX - x) / xStep * xSpacing)` for the first, `(x - previousX)` for the last) instead of
   `min(x - previousX, nextX - x)`. **That is upstream's own fix for the bug A3c was removing
   `maxWidth` to avoid**: at a boundary value `previousX == x`, so the old `min(...)` was 0 and the
   label blanked.

**Verdict.** `updateLayerDimensions` must **not** be no-op'd again. meApp changes
`HorizontalItemPlacer.kt:40`'s `HorizontalAxis.ItemPlacer.aligned()` to
`aligned(shiftExtremeLabels = true)` — the wrapper is `by defaultPlacer`, so the new member forwards
automatically. A3a → **REPLACE**. A3c → **RE-DERIVE**, not re-apply, because our A2b forces
`Position.Horizontal.End` on *every* label, which re-breaks the **last** label's `maxWidth`
(`nextX - x == 0` at the final value). The re-derived form: when `labelHorizontalPosition` is
non-null, bypass upstream's `maxWidth` `when` and pass no width constraint; when it is null, keep
upstream's block untouched so `shiftExtremeLabels` behaves as upstream intends.

**Risk if wrong: silent.** A re-applied `: Unit = Unit` compiles, and the chart simply loses
whatever upstream later puts in that method.

## (c) `CartesianLayerMargins.ensureValuesAtLeast` halving — confirmed DELETE, with the blast radius

**v3.3.1 stores as given.** `cartesian/layer/CartesianLayerMargins.kt:55-66`:
`this.top = this.top.coerceAtLeast(top)` — no `.div(2)` anywhere in the file. `ensureVerticalValuesAtLeast`
does not exist upstream (`git grep` at v3.3.1: zero hits). The file, plus
`HorizontalCartesianLayerMargins.kt` and `CartesianLayerMarginUpdater.kt`, are **byte-identical**
between `e67e49520` and `v3.3.1`.

**Every v3.3.1 caller that sets `top`/`bottom`** — six call expressions, seven quantities, in four
files. For each, what is passed and whether halving is right:

| v3.3.1 call site | quantity passed | halving? |
|---|---|---|
| `axis/HorizontalAxis.kt:653` `top = height` | the **full** measured top-axis height, drawn wholly outside the plot | **wrong** |
| `axis/HorizontalAxis.kt:654` `bottom = height` | the full bottom-axis height, wholly outside the plot | **wrong** |
| `axis/VerticalAxis.kt:507` `top = getTopLayerMargin(…) + titleHeight` | label overhang **already halved by the item placer** (`StepVerticalAxisItemPlacer.kt:67-75`), plus a full `End`-title height | **wrong** (halves a half, and halves a full title) |
| `axis/VerticalAxis.kt:507` `bottom = getBottomLayerMargin(…)` | same, already placer-reduced | **wrong** |
| `layer/LineCartesianLayer.kt:947` `top`/`bottom = verticalMargin` | `max(stroke, point)`**`.half`** — the `.half` is already at the call site | **wrong** (yields a quarter) |
| `marker/DefaultCartesianMarker.kt:244` `top = label.getHeight(context) + tickSize.pixels` | full label height + full tick, wholly outside the plot (the `AroundPoint` branch is skipped *because* it is inside) | **wrong** — this is the MOB-2953 overlap |
| `marker/DefaultCartesianMarker.kt:248` `bottom = …` | same | **wrong** |

Not in the blast radius: `HorizontalAxis.kt:651` `ensureValuesAtLeast(startMargin, endMargin)`,
which resolves to the 2-arg `HorizontalCartesianLayerMargins` override and never reaches top/bottom;
`VerticalAxis.kt:481-482` (start/end on the horizontal view); and the five
`MutableCartesianLayerDimensions.ensureValuesAtLeast` sites, a different class.

**Of six top/bottom sites, zero want the halving.** Every one either applies its own `.half` where
halving is geometrically right, or passes a quantity that sits wholly outside the plot and wants all
of it. Combined with P1 — nothing compensates — the halving is uncompensated, wrong at every site,
and self-inflicted.

**Verdict: DELETE both methods' changes.** `ensureVerticalValuesAtLeast` goes with it: once
`ensureValuesAtLeast` stores as given, the marker's two calls become plain `ensureValuesAtLeast`
(which is what upstream already writes at `DefaultCartesianMarker.kt:244,248`).
`cartesian/layer/CartesianLayerMargins.kt` returns to stock and leaves the fork.

**Note the second-order effect, because it is not just two numbers.** `CartesianChart.measure()`
(:327) runs `updateLayerMargins` first; `prepare()` then derives
`freeHeight = canvasSize.height - layerMargins.vertical - legendHeight` and calls
`setLayerBounds(..., layerMargins.top, ..., canvasSize.height - layerMargins.bottom - legendHeight)`.
Removing the halving therefore changes the plot's height **and** the vertical axis's measured width
(pass 2 takes `freeHeight`). Expect a visible layout delta and measure it, rather than assuming
either way — v3.3.1 also changed what the margins are subtracted *from* (`chartAreaHeight`,
S2-A 2.1), so the two changes must be evaluated together on the parity harness.

**Coordinate note for the marker cluster:** this is the answer they are waiting on.
`ensureVerticalValuesAtLeast` **does not survive**. Every fork call site on
`DefaultCartesianMarker.kt:485-493` reverts to `ensureValuesAtLeast`, and their `labelGap` /
text-independent-measurement work rides on top of the unhalved value.

## (d) `MonotoneInterpolator.visiblePadding` — the value is **1**, and here is the derivation

**The contract at v3.3.1.** `cartesian/layer/LineCartesianLayer.kt:342-349`:

```kotlin
public interface Interpolator {
  /** …the number of extra points required on either side of the visible range, beyond the
      standard single extra point already included in `visibleIndexRange`. `null` ⇒ full series. */
  public val visiblePadding: Int? get() = 0
```

It is consumed at `LineCartesianLayer.kt:757` — `val visiblePointPadding = interpolator.visiblePadding?.plus(1)`
— and spent at `:776-777` via `List<Entry>.getSliceIndices(start, end, visiblePointPadding)`
(`cartesian/data/CartesianLayerModel.kt:114-141`), which moves **both** ends by the same count. So
`visiblePadding = k` means *k points exist in `points` beyond each end of `visibleIndexRange`*
(clamped at the real series ends). `CatmullRomInterpolator.kt:26` declares `1`; `CubicInterpolator`
declares nothing (inherits 0); `Sharp` likewise.

**Why Catmull-Rom needs 1.** Its segment at `index` reads `points[index - 2]` and `points[index + 1]`
(`CatmullRomInterpolator.kt:43-46`), and the loop runs `visibleIndexRange.first + 1 .. .last`, so the
reach is one point beyond `visibleIndexRange` on each side.

**Why ours needs the same.** `cartesian/layer/MonotoneInterpolator.kt:62-91`:
secants `d_i` span `points[i] → points[i+1]` (:62-64); tangents are
`tangents[i] = computeMonotoneTangent(secants[i-1], secants[i])` (:72), so **`tangents[i]` reaches
back to `points[i-1]`**; the drawing loop (`:77-91`) runs `index ∈ [first+1, last]` and consumes
`tangents[index-1]` and `tangents[index]` — i.e. `tangents[first .. last]`.
Therefore the left-most tangent consumed, `tangents[first]`, needs `secants[first-1]`, hence
**`points[first - 1]`**; the right-most, `tangents[last]`, needs `secants[last]`, hence
**`points[last + 1]`**. One point each side.

`computeMonotoneTangent` (`:167-172`) is strictly a two-secant, three-point local rule — sign test,
harmonic mean, and a `3 · min(|m1|, |m2|)` clamp. There is **no** global monotonicity sweep and **no**
Fritsch–Carlson α/β pass that re-touches an already-computed neighbouring tangent, so nothing reaches
further. `2` would waste a point per side; `0` is one short.

```kotlin
override val visiblePadding: Int = 1
```

**Why it will not fail loudly.** With padding 0 the slice ends coincide with `visibleIndexRange`'s
ends, so the first and last drawn segments silently fall back to the one-sided endpoint rules
`tangents[0] = secants[0]` / `tangents[n-1] = secants[n-2]` (`:69-70`). Every index stays inside the
shorter slice: no exception, no log, no overshoot (a one-sided tangent equal to the secant trivially
satisfies the FC bound). The curve still passes through every point. The defect is shape-only and
confined to the two edge segments — a small kink that **changes** as the chart scrolls one slot.
On a static chart that fits on screen the slice is the whole series and there is **no difference at
all**, so no screenshot test or non-scrollable preview will catch it.

**Signature changes to absorb: none.** `interpolate(context, path, points, visibleIndexRange)` is
identical at v3.3.1 (`:360-365`), and `getYRange(y): ClosedRange<Double>` is still there (`:372`).
`visiblePadding` is the only addition.

**Caveat (UNRESOLVED U3).** For `LineStroke.Dashed`, `drawFullLineLength = true`
(`LineCartesianLayer.kt:581`) forces `visibleStart = 0`, so the left padding point lands *inside*
`visibleIndexRange` and the left-most segment uses the one-sided tangent regardless of
`visiblePadding`. `CatmullRomInterpolator` is affected identically, so this is upstream behaviour,
not ours to fix. meApp's percentile band is the only candidate for a dashed line — confirm whether
it uses one.

## (e) Two free wins — both confirmed

**L13 — `InlineUpdateContext` is a rename of upstream's `PreviewContext`, and it is our only change
to `CartesianChartModel.kt`.** At v3.3.1, `PreviewContextKey` and `PreviewContext` are still
`internal` at `cartesian/data/CartesianChartModelProducer.kt:214, 216`; `getDispatcher()` (:208-211)
keys on `PreviewContextKey`; and the single consumer is
`cartesian/data/CartesianChartModel.kt:239-240`:

```kotlin
private fun getCoroutineContext(isPreview: Boolean): CoroutineContext =
  if (isPreview) Dispatchers.Unconfined + PreviewContext else EmptyCoroutineContext
```

That one line is our entire 1+/1− diff on that file, and upstream churned the file **43+/39−**.
**Verdict: keep upstream's name.** Add `public object InlineUpdateContext :
AbstractCoroutineContextElement(PreviewContextKey)` as a *second* element on the *same* key
(so `getDispatcher()`'s lookup at :210 finds either), and make `runTransaction` use
`withContext(getDispatcher())` instead of its hard-coded `withContext(Dispatchers.Default)` (:154).
Net: a two-file rename becomes a one-file addition, and `CartesianChartModel.kt` leaves the modified
list. The test behaviour meApp depends on
(`features/dashboard/viewmodel/base/BaseDashboardViewModel.kt:25, 256, 265` and
`testutil/ViewModelTestExtensions.kt:10, 53`) is preserved exactly — only the type name they import
changes, and it changes to the name they already use.

**L10 — `getVisibleXRange()` was made public and nothing outside the library calls it.**
At v3.3.1 it is `internal fun CartesianDrawingContext.getVisibleXRange()` at
`cartesian/CartesianDrawingContext.kt:55-56`, and **`git diff e67e49520..v3.3.1` on that file is
empty** — upstream never touched it. meApp does not call it (grep over `app/src`). All four library
call sites are internal. **Verdict: revert to `internal`.** `CartesianDrawingContext.kt` then grafts
with zero changes and leaves the modified list. Note the dependency: the only reason it was widened
is that `yTransform`'s lambda type hands a `ClosedFloatingPointRange<Double>` to meApp — which L3's
re-derivation keeps inside the library's own seam, so the widening is not needed either way.

## (f) `registerForUpdates(animate = false)` — a global change hiding in a feature flag

**The fact.** v3.3.1's `registerForUpdates` animates, unconditionally:
`CartesianChartModelProducer.kt:109` calls `receiver.handleUpdate(lastPartials,
lastTransactionExtraStore, restoredModel)`, whose last statement (:204) is
`startAnimation { key, fraction -> transform(key, fraction, model, ranges) }`. There is no instant
path and no flag. Our `HEAD` passes `animate = false` at the same line. **The producer file is
byte-identical between `e67e49520` and `v3.3.1`** (same blob SHA), so this is purely our change.

**Was it intended? No.** The commit is `70b7372c5` *"feat(animate): Add animate param to
runTransaction"* — the subject names `runTransaction`, not registration. No FEATURE doc, commit body
or code comment covers the registration half. S1-D L12 flags it in exactly these terms. Every meApp
call site that wanted the behaviour passes `animate = false` to `runTransaction` explicitly
(`WeightDashboardViewModel.kt:241`, `BabyDashboardViewModel.kt:218`,
`BaseDashboardViewModel.kt:240, 268`, `DashboardSnapshotViewModel.kt:345, 414, 479`) — none of them
is a registration.

**What it changes for everyone.** Any chart that *re-registers* — configuration change, process-death
restore, recomposition into a new host — now applies its restored model instantly instead of
animating in. That is a behaviour change for every consumer of the published artifact, delivered
without a parameter.

**Verdict: DELETE.** Restore upstream's `receiver.handleUpdate(lastPartials,
lastTransactionExtraStore, restoredModel)`. **If** the instant restore turns out to be wanted,
express it through v3.3.1's own new, supported parameter instead:
`CartesianChartHost(initialAnimationSpec = null)` (S2-C V7), which is host-scoped, one-shot, and
exactly the knob for "do not animate the first model in". That is the REPLACE fallback; do not
reinstate the producer-side default.

**Risk if wrong: silent.** Nothing fails; a chart just animates (or does not) on rotation.
**Exact check before deleting:** rotate the weight dashboard on the current build and on a build with
the line reverted, and watch whether the line sweeps in from the previous window. If it does and that
is unacceptable, take the `initialAnimationSpec = null` route.

---

# yTransform — the structural call

## What is actually there

~130 lines of wall-clock state (`LineCartesianLayer.kt:124-145, 646, 827-921, 931-940, 1000-1006,
1156-1181`) inside `collectPointsAndVisibleIndexRange` and `forEachPointInBounds` — the two hottest
functions of the hottest file in the fork. Zero test coverage: the only test that ever covered it,
`VisibleEntryKeyTest.kt`, was deleted by `e6c766cb8` together with the mechanism it tested. One
feature — the secondary-metric overlay on the weight dashboard — from one call site,
`features/common/components/chart/ProductChart.kt:324`.

Supporting pieces in other files: `CartesianChartRanges.getTargetYRange` (6 defaulted lines, no
upstream analogue), `WindowSettleSignal` (new file), `getVisibleXRange` visibility (L10).

**Per-frame cost in steady state.** Allocation: zero. But `series.hashCode()` — **O(n) over the
entire series, every frame, per series** (`LineCartesianLayer.kt:838`) — exists only to detect a
data change the host already knows about. On a TOTAL-segment chart that is every reading the user has
ever recorded, hashed 60 times a second. `getDrawY` additionally does one `Map<Double, Int>` lookup
per visible point with a boxed `Double` key. Replacing the hash with a version counter makes the whole
block O(1); that alone is worth doing whichever option wins.

## The two options, costed

### Option A — keep the behaviour, shrink the library to a seam

**Library.** One public interface and two call sites:

```kotlin
public interface CartesianLayerYMapper {
  public fun map(
    series: List<LineCartesianLayerModel.Entry>,
    targetYRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ): DoubleArray?
  public fun opacity(): Float = 1f
}
```

plus a nullable `protected val yMapper` on `LineCartesianLayer`, the substitution in both `getDrawY`
paths, and `saveLayer(opacity = (drawingModel?.opacity ?: 1f) * (yMapper?.opacity() ?: 1f))`.
**≈15 library lines across one new small file and three one-line edits**, down from ~130 lines of
stateful draw-pass logic.

**meApp.** A ~120-line class, moved almost verbatim from the layer: the `DoubleArray` cache, the
index map, the three keys, the fade state machine, the `TimeSource` timing. meApp already owns
every input it needs — `MetricBasis.version` (`ProductChart.kt:206-230`) is exactly the settle
epoch, and `SettledAxis` (`:233-250`) already records the settled `(minY, maxY, ticks)`.

**What is lost.** Nothing. Pixel-identical output.

**What is gained beyond size.** `series.hashCode()` disappears — meApp keys on `MetricBasis.version`,
which it already maintains, so the per-frame cost drops from O(n) to O(1). `WindowSettleSignal`'s
type-test in the layer disappears (meApp's mapper owns its own epoch), which hands the scroll cluster
the option of deleting that interface. And the seam is **re-sitable**: v3.3.1 rewrote
`collectPointsAndVisibleIndexRange` substantially (visible slice, new `interpolator` and `seriesKey`
parameters), and a single call inside `getDrawY` survives that rewrite where a 130-line state machine
does not.

**Risk if it goes wrong: silent.** The overlay draws in its raw unit — a pulse of 60 on a weight axis
of 150–200 — so a flat line pinned to the bottom of the plot, or off-plot entirely. No crash.

### Option B — delete it: normalise in meApp and push pre-normalised values

S1-D's option (b). **Its stated blocker is false, and that matters.** S1-D said "the normalisation
needs the live animated axis, which meApp cannot see". meApp already sees it: its own
`onVisibleEntries` lambda (`ProductChart.kt:156-193`) *computes* `scale.min`/`scale.max`, calls
`onYRangeSettled(scale.min, scale.max)`, records them in `SettledAxis`, and returns them to the
library. The transform is invoked with `getTargetYRange` — which is that same value, handed back to
meApp one round trip later. So the round trip through the draw pass buys nothing meApp could not
compute at the moment the range settles.

**meApp would do.** In the settle lambda, additionally compute
`GraphUtil.normalizeYValues(secondarySeries, scale.min, scale.max, metricRange)` and push a
transaction. It already has `normalizeYValues` (`features/common/helper/graph/GraphUtil.kt:258-292`)
as a pure function of exactly those inputs.

**Library.** L3 and L4 delete outright. `getTargetYRange` and the layer's `WindowSettleSignal`
type-test delete. `LineCartesianLayer.kt` goes from "the hardest file in the port" to L6 + L8 + L9.

**What is lost, concretely.**
1. **The 250 ms cross-fade** (100 out, 150 in). A model push with `animate = false` — which every
   meApp call site uses — makes the overlay *snap* to the new scale. Pushing that one transaction
   with `animate = true` instead would give a position tween, which is arguably better, but it is a
   different look and needs design sign-off.
2. **A settle's worth of latency.** `runTransaction` is `suspend` and goes through the producer; the
   overlay re-scales one or more frames after the axis starts moving. The 300 ms range animation
   masks most of it, but not provably all of it.
3. **S1-D's claim that option (b) also deletes L5 is wrong.** `alwaysUseLiveRange` exists because the
   drawing model bakes y as a *fraction of the range at model-build time* and
   `ScrollAwareRangeProvider` makes the range live. That is true of the overlay layer whether its
   values are raw or pre-normalised. L5 survives option B unchanged.

**New risk it introduces.** A model push on every window settle is a new feedback path into
`updateChartRanges` → the range provider → the scroll state, on a chart whose defect history
(MOB-3494, the range jump) is precisely about that loop. `ScrollAwareRangeProvider` returns its last
settled value so it should be stable, but "should be" is the whole of the evidence: there is no test
and no parity harness result. **If it misbehaves it looks exactly like the bug MOB-3494 was filed
for**, on a surface with no coverage.

## Recommendation: **Option A. RE-DERIVE the seam; do not delete the feature during this migration.**

Three reasons, in order of weight.

**1. Option A buys ~90% of option B's fork reduction at a fraction of the risk.** The library goes
from ~130 stateful lines in the hottest file to ~15 declarative ones. That is the whole of the
migration problem solved — the reason yTransform is "the hardest file in the port" is the state
machine's entanglement with upstream's rewritten `getDrawY`, and option A removes that entanglement
completely. Option B's extra saving over option A is L4 and `getTargetYRange` — about 25 lines.

**2. Option B is a product-behaviour change bundled into a migration that already carries the
largest file rewrite in the port, on a feature with zero tests.** If the overlay looks wrong after
the port, with option A there is exactly one new thing to suspect; with option B there are two, and
the second one is invisible (did the settle-triggered push land late? did it perturb the range?).
C1 in `CONTRADICTIONS.md` records that a confident deletion has already silently removed shipped
behaviour once in this project. Do not stack a second.

**3. Option A does not close option B.** It is a strict step toward it: once the cache, the keying
and the fade all live in meApp, moving them from "called at draw time" to "called at settle time" is
a change entirely within meApp, testable in meApp's own suite, releasable on its own schedule, and
revertible without touching the library. Taking option B now is irreversible in one direction and
untestable in the other.

**Sequencing.** Option A depends on L5 landing first (the mapper is pointless if the drawing model
still supplies `pointInfoMap`), and L4 should be re-derived as an explicit
`contributesYRange: Boolean` property rather than being inferred from `yTransform != null` — that
decoupling is right regardless of which option eventually wins, and it is what lets option B delete
L3 later without also deleting L4.

**Invariants to carry across verbatim** (each was a bug once; none is covered by a test):
- Always compute against `getTargetYRange`, never the animating `getYRange`. Computing against the
  range the chart is *leaving* strands the line off-plot as the axis animates out from under it.
- Increment the settle epoch **before** any no-movement early return.
- Skip the fade when the new result `contentEquals` the old, or every in-scale scroll flickers.
- The window key must be nullable; `0` is a real key (an empty series, or a window holding no entries).
- The cache must survive `copy()`, or every layer recreation snaps the line.

**One optimisation to take with the move:** replace `series.hashCode()` with meApp's existing
`MetricBasis.version`. O(n) per frame → O(1).

---

# Per-adaptation evidence

Only what is not already stated above.

## A1 — `Separators` → **MOVE**
Every term of the x→canvas mapping is public at v3.3.1: `layerBounds` (`CartesianDrawingContext.kt:30`),
`layerDimensions` (:33) with public `startPadding`/`xSpacing` (`CartesianLayerDimensions.kt:52-53, 25`),
`scroll` (:36), `ranges.minX`/`xStep` (`CartesianChartRanges.kt:26, 32`), `layoutDirectionMultiplier`
and `isLtr` (`common/MeasuringContext.kt:71, 64`), and `LineComponent.drawVertical`
(`common/component/LineComponent.kt:67`). The fork's `baseCanvasX` uses the bottom axis's
`bounds.getStart(isLtr)`, and that is **provably equal** to `layerBounds.getStart(isLtr)`:
`AxisManager.setBottomAxisBounds` (:110-115) and `CartesianChart.prepare` (:356-361) set the two from
the same `layerMargins` terms. So no axis reference is needed at all. `Rect.getStart` is `internal`
(`common/Rect.kt:57`) — inline `if (isLtr) layerBounds.left else layerBounds.right`.
Drawn from `Decoration.drawUnderLayers`, which runs before the layer bitmap composite under both
upstream's order (`CartesianChart.kt:380` vs :393) and our reordered one (HEAD :370 vs :399).
**The one accepted change:** separators land *under* the guidelines instead of over them — upstream
offers no slot between `axisManager.drawUnderLayers` (:381) and the composite. This is invisible in
practice: meApp's separator and guideline components are both
`thickness = MeTheme.strokeWidth.s, fill = Fill(MeTheme.colorScheme.textDisabled)`
(`features/common/components/chart/axis/Horizontalxis.kt:61-64, 76-79`) — same thickness, same colour
token — and for YEAR/MONTH they are drawn at the same x values anyway.
meApp passes no `decorations` today (`ProductChart.kt:87-105`), so adding them is free.
*If the z-order ever matters*, the right fix is a third defaulted `Decoration` pass invoked between
`CartesianChart.kt:381` and `:382` — 3 lines in `CartesianChart` plus one defaulted method, and a
good upstream PR. Do not re-open `HorizontalAxis.kt` for it.
**Risk: silent.** **Sequencing:** any time; independent.

## A2a/A2c — **RE-APPLY**
v3.3.1 still computes `textY` as `bounds.bottom/top ∓ lineThickness ∓ outwardTickLength`
(`HorizontalAxis.kt:156-168`), so the `tickOffset` substitution applies unchanged.
S2-B §6(c) proposed expressing `LabelVerticalMode` through upstream's `TickPosition` instead — reject
it: `TickPosition.Inside` gives the same `textY` but also moves the **tick**, and meApp's segmented
bottom axis draws a deliberate 16 dp `SeparatorTickLength` tick into the label band
(`Horizontalxis.kt:70, 87-89`). `TickPosition.Inside` would draw it into the plot instead. Not a
substitute.
**Change the default while re-applying:** upstream's constant is the full `outwardTickLength`, so our
`Center` default (`outwardTickLength / 2`) silently alters every untouched caller. Default to
`Outside`. meApp always passes `Inside`, so nothing moves for the only consumer.
A2c (`lineThickness` added to `Size.Auto` height only for a Bottom axis) is independent of the two
parameters; re-apply it, and record it in the parity harness — it changes the measured height of
every Top axis.
**Risk: silent.** **Sequencing:** A2b must land with A3c.

## A2b/A3c — **RE-DERIVE together**
v3.3.1 rewrote the line our A2b patch replaced: `horizontalPosition` is now a
`when { shiftExtremeLabels && index == 0 -> End; … -> Center }` (`HorizontalAxis.kt:203-208`), and
`maxWidth` is a `when (horizontalPosition)` over three one-sided formulas (:209-217). Make
`labelHorizontalPosition` a `Position.Horizontal?` defaulting to `null`; when null, leave upstream's
two blocks untouched; when non-null, use it and pass no `maxWidth`. That keeps
`shiftExtremeLabels = true` (which A3a depends on) behaving as upstream intends for anyone who does
not override, and gives meApp its uniform `End`.
**Risk: silent** — with A2b applied and upstream's `maxWidth` left in place, the **last** label
computes `maxWidth = 0` and blanks.

## A3b — **DELETE**
Our stated reason for abandoning `getLineLeft`/`getLineRight` was that they tie the horizontal axis's
clip to the vertical axes' line positions and break on a zero-width vertical axis. At v3.3.1 the
default branch is already the quantity our patch substituted:
`defaultLineLeft = bounds.left - itemPlacer.getStartLayerMargin(this, layerDimensions, tickThickness,
maxLabelWidth)` (`HorizontalAxis.kt:473-475`), symmetrically at :496-498. The axis `lineBounds` is
consulted only when non-empty (`getLineStart`/`getLineEnd`, :437-459) and then only via
`minOf`/`maxOf` — it can **widen** the clip, never narrow it. So the failure mode we patched around
does not occur, and upstream's version is strictly more informed than ours. Dropping this hunk also
keeps `clipPath`, `getLineLeft/Right`, `getLineStart/End` and `kotlin.math.ceil` alive, which removes
~70 lines of fork-created dead code and the `Path`/`Rect` import churn from `HorizontalAxis.kt`.
The `maxOf(…, maxLabelWidth.half)` widening also goes: with `shiftExtremeLabels = true` (A3a) the
extreme labels are anchored inward and no longer need it.
**Risk: silent.** **Sequencing:** must precede A4b, which re-derives onto upstream's expression.

## A4a — **MOVE**; A4a′ — **DELETE**
meApp already owns a full `HorizontalAxis.ItemPlacer by defaultPlacer` wrapper
(`features/common/components/chart/HorizontalItemPlacer.kt:40-97`) overriding **both**
`getLabelValues` and `getLineValues`, with `visibleXRange` and `context.ranges.xStep` in scope. Apply
`closesTheWindow` in the `else ->` branches of both. meApp never uses
`HorizontalAxis.ItemPlacer.segmented()` (it uses `aligned()`, `:40`), so the
`SegmentedHorizontalAxisItemPlacer.getLineValues` hunk is dead for the only consumer — delete it.
v3.3.1's private `getLabelValues` loop (`HorizontalAxisItemPlacers.kt:40-67`) is recognisably the same
shape, so the rule ports mechanically.
**This is the MOB-2953 headline fix. Risk if wrong: silent — the eight-day week returns,** and the
eighth day's entry is counted into the week average again. **Put a test on it in meApp while moving
it**; there is none today, in either repo.
*Pre-existing defect worth fixing while there:* `HorizontalItemPlacer.kt:92` — `getLineValues`'s
`else ->` branch calls `defaultPlacer.getLabelValues`, not `getLineValues`.
**Sequencing:** same meApp file and same edit session as A3a.

## A4b — **RE-DERIVE**
The label clip must still be clamped to the plot, or a boundary label at the edge draws with no
guideline beneath it. On upstream's expression that is
`maxOf(lineLeft, layerBounds.left)` / `minOf(lineRight, layerBounds.right)` inside the existing
`clipPath.addRect` (`HorizontalAxis.kt:176-187`) — two terms, not a rewrite.
**Risk: silent.** **Sequencing:** after A3b.

## A5 — **RE-APPLY**
v3.3.1's `drawGuidelines` still suppresses only on `x.isBoundOf(fullXRange)`; `isChartEdge` is still
needed, and the `ranges.xStep / 1000` tolerance is still load-bearing (a label value is rebuilt by
rounding to the nearest step, so an exact `==` passes over it). It exists **only** because the start
inset moved `fullXRange.start`; both candidate mechanisms in C3 (`startInsetDp` or
`CartesianLayerPadding(unscalableStart = …)`) extend `startPadding` and therefore `getFullXRange`
identically, so A5 is needed either way. **Risk: silent** (a second line painted on the start axis).
**Sequencing:** with C-2, A8 and C-3 (Phase 4).

## A6 — **REPLACE**; A6′ — **DELETE**
The three hard requirements of the pill (outside any clip, after the fading-edge `canvas.restore()`,
before the legend) are all met by `decorations.forEach { it.drawOverLayers(context) }` at
**upstream's** `CartesianChart.kt:399` — restore is :396, legend is :401, and `draw()` installs no
clip of its own (every clip in v3.3.1 is local and restored before its component returns). The y
mapping is `ranges.getYRange(position)`, public (`CartesianMeasuringContext.kt:44`,
`CartesianChartRanges.kt:38`) and literally the same object `VerticalAxis` reads at
`VerticalAxis.kt:168, 211` — so the pill stays in lockstep with the axis under our animated-range
machinery for free. The gutter rect is `axis.bounds`, public via
`Bounded` (`common/Bounded.kt:24`, `BaseAxis.kt:65`), set by `AxisManager.setAxesBounds` from
`CartesianChart.prepare`, which the host runs immediately before `draw` every frame
(`CartesianChartHost.kt:340` then :370). meApp constructs the end axis itself
(`ProductChart.kt:93-97`), so holding the reference is a one-line change.

**The one blocker is our own C-4 reorder, not v3.3.1.** In our `draw()` the decoration passes run at
HEAD :370/:376, *before* the composite and *before* the fade restore (:402) — which is exactly why
`drawMarkerDecorations` had to be invented. **So the library change is C-5, re-derived: move the
`decorations.drawOverLayers` statement to the post-fade-restore position where
`axisManager.drawMarkerDecorations(context)` sits today, and delete `drawMarkerDecorations`.**
One statement moved, one deleted. In exchange, `VerticalAxis.kt` sheds the `MarkerDecoration` data
class, `drawMarkerDecoration`, `getMarkerDecorationWidth`, the constructor property, the
`equals`/`hashCode`/`copy` entries and both `remember*` parameters — the single largest block of our
211 added lines on a file upstream churned 175 — and `AxisManager.kt` sheds one of its two added
methods. meApp writes a `GoalPill : Decoration` from `rememberGoalMarker`'s existing inputs
(`features/common/components/chart/GraphDecoration.kt:28-67`).
Accepted order changes: the pill now draws **before** the legend rather than after (meApp passes no
legend — `ProductChart.kt:87-105`) and before the persistent/active marker over-passes (different
screen region: gutter vs plot).
A6′: `getMarkerDecorationWidth` is inert for the only consumer — meApp pins `Size.Scroll(YGutterWidth
= 50.dp)` (`axis/VerticalAxis.kt:92`), a fixed width, so the `max()` never fires. Delete it; if a pill
ever outgrows 50 dp the fix is to widen `YGutterWidth`, a one-line consumer change.
**Risk: silent** (pill at the wrong y, or sliced by the fade layer — the exact defect `1bf0a6d9a`
fixed, with a 63 px pill at y=822 on an 825 px canvas losing its bottom half).
**Sequencing: after C-4 and C-5 land.** Verify on the parity harness before deleting the axis code.

## A7a — **RE-APPLY (narrowed)**; A7b — **DELETE**
v3.3.1 has no `Scroll` variant and is moving *away* from extra `Size` cases (`Fraction` was
deprecated at v3.3.0 in favour of `chartAreaHeight`). Adding a sealed subclass stays mechanically safe:
there are exactly **two** `when (size)` sites in the whole tree at v3.3.1 —
`HorizontalAxis.kt:673` and `VerticalAxis.kt:535` — both in files we already patch.
What meApp actually consumes from `Size.Scroll(50.dp)` is (i) guidelines drawn unconditionally across
the layer rather than being suppressed where they meet an axis band, and (ii) the explicit
line/tick clip. Keep those.
**A7b is dead code for the only consumer, with the fork's own commentary against it.**
`scrollValue`/`maxScroll` are read at exactly one place — `VerticalAxis.kt:491-492`, inside
`if (scrollSize.isLabelsScrollable)` — and no meApp caller sets `isLabelsScrollable = true`
(`axis/VerticalAxis.kt:92` is `Size.Scroll(YGutterWidth)`, the default `false`). meApp's `startAxis`
KDoc records the mechanism as the defect they moved away from: *"this used to be Size.Scroll, whose
margin shrank as the user scrolled and so moved the plot's own edge… Everything measured off that
followed, and the first window laid its labels out differently from every other one."*
`effectiveScroll` only moves ticks, and meApp's end axis has `tickLength = 0.dp`.
**Deleting A7b deletes `updateScrollState`, the four host call sites
(`CartesianChartHost.kt:469, 470, 478, 479`) and — the real prize — H-7's entire throwaway first
`prepare` pass**, which S1-E rated "very high" conflict risk and which collides head-on with
v3.3.1's new `measure` / `getVerticalExtras` pre-measurement split. Tell the host cluster.
**Risk: A7a silent (gridlines stop short); A7b none.**

## A8 — **RE-APPLY as one unit with A5, C-2 and C-3**
Unchanged reasoning from S1-C: A8 + A5 + the start inset + the `hidesStrip` clip are one feature in
three files. Re-applying the pieces independently yields a chart that compiles and is subtly wrong.
**New at v3.3.1 and not in S1-C:** `VerticalAxis.updateAxisDimensions` publishes
`axisDimensions.lineBounds` derived from `bounds` (`VerticalAxis.kt:150-158`), and
`HorizontalAxis.getLineStart`/`getLineEnd` (:437-459) consume it. A8 moves the *drawn* line to
`chartEdgeX()` while `lineBounds` still describes the gutter, so the two disagree. `lineBounds` cannot
be fixed to match: it is set in the measuring phase, and `chartEdgeX()` needs `scroll` and
`layerDimensions` from draw time. See **UNRESOLVED U1**.
**Risk: silent.**

## A9 — **RE-DERIVE as a parameter**
v3.3.1 still computes `labelX = if (areLabelsOutsideAtStartOrInsideAtEnd == isLtr) tickLeftX else
tickRightX` (`VerticalAxis.kt:205`) and still passes `horizontalPosition = textHorizontalPosition`
in `drawLabel` (:437) — `drawLabel` is still `protected open` with the same signature (:388-393).
The two-line hard-code works, but it silently makes `VerticalAxis.HorizontalLabelPosition` inert for
label placement while leaving it in `equals`/`hashCode` and still influencing the default
`tickPosition` — a public-API behaviour change nobody can see. Add
`labelHorizontalPosition: Position.Horizontal?` mirroring A2b, defaulting to `null` = upstream's rule.
Same shape in both axes, and nothing is silently disabled.
A meApp subclass is *not* the cheaper route here: `labelX` is computed in `drawOverLayers`, not in
`drawLabel`, so a subclass would have to copy ~30 lines of upstream method body.
**Risk: silent** (labels hard against one side of the 50 dp gutter).

## A10 — **RE-APPLY**
Upstream did **not** fix it. v3.3.1 still divides by `yRange.length` unguarded at
`VerticalAxis.kt:177` (`drawUnderLayers`) and `:212` (`drawOverLayers`). Keep the exact `== 0.0` test
— a range whose min and max are the same value produces exactly `0.0`. Keep the deliberate
asymmetry in the pill's inline test (a goal on an empty chart should still park at an edge).
**Risk: loud** — NaN propagates into a canvas y; a crash or a blank draw on an empty chart, on a
chart whose every entry has the same y, and on frame zero.

## A11 — **MOVE**
`VerticalAxis.ItemPlacer` at v3.3.1 is a fully public interface with exactly the shape
`ListItemPlacer` implements: `getShiftTopLines` (defaulted), `getLabelValues`,
`getWidthMeasurementLabelValues`, `getHeightMeasurementLabelValues`, `getLineValues` (defaulted),
`getTopLayerMargin`, `getBottomLayerMargin`. **The silent-drift check S1-C asked for passes:**
our `getTopLayerMargin`/`getBottomLayerMargin` (`ListItemPlacer.kt:91-117`) are character-identical
to `StepVerticalAxisItemPlacer.kt:61-88` at v3.3.1, modulo line wrapping. Nothing internal is touched.
meApp is the only consumer (`axis/VerticalAxis.kt:17, 89`). Move the file; delete it from the library.
Keep the one-entry identity cache (meApp's `ticksProvider` is `remember`ed, so it holds) and the
`EPSILON = 0.01` absolute tolerance, noting in meApp that it is unit-dependent.
**Risk: silent** (gridlines in one set of places, labels from another).

## L1 / L2a / L2b
**L1 → MOVE.** `Interpolator` is fully public and implementable at v3.3.1
(`LineCartesianLayer.kt:342-413`), `getYRange` is part of the contract, and
`LineCartesianLayer.updateChartRanges` folds it into the chart range — so our non-overshooting spline
declares its honest range with no library change. Move `MonotoneInterpolator.kt` to meApp, delete the
`Interpolator.Companion.monotone()` factory, and change meApp's two call sites
(`chart/layer/DataLine.kt:47`, `chart/layer/PercentileBand.kt:40`) from
`LineCartesianLayer.Interpolator.monotone()` to the meApp object. **Declare `visiblePadding = 1`** —
see (d). Also drop the dead `import …Interpolator.Companion.catmullRom` at
`LineCartesianLayer.kt:51`.
**L2a → MOVE.** `InterpolationType` is two of our own names; pure meApp policy. Only
`VicoScrollState.getInterpolatedYValues` reads it — **scroll cluster**, flag to them.
**L2b → REPLACE.** `getYAtXFromEntries` has two *library* call sites
(`CartesianChart.kt:582` in `synthesizeInterpolatedTargets`, `VicoScrollState.kt:164`), so the
algorithm cannot simply leave. Add one defaulted member to our fork's `Interpolator`:
`public fun getYAt(x: Double, series: List<LineCartesianLayerModel.Entry>): Double? = null`, and have
`CartesianChart` call the interpolator the layer actually uses. A copied algorithm becomes one
defaulted interface method (~6 lines), and meApp's own `MonotoneInterpolator` supplies it.
**Preserve `25dc072a9`'s contract:** out-of-range returns `null`, consumed with `?: continue` at
`CartesianChart.kt:582`. Reverting to clamping silently resurrects flat phantom markers.
**Risk: L1 silent (edge-segment shape); L2b silent (phantom markers).**
**Sequencing: L2b before L1** — the seam must exist before the implementation leaves.
*Stale comment to fix while there:* `CartesianChart.kt:548` still refers to a
`MonotoneInterpolator.getYAtX` overload deleted by `57ce985ea`.

## L4 — **RE-DERIVE as a flag**
Express as `contributesYRange: Boolean = true` on the layer, not as `if (yTransform != null)`. The
general statement — "this layer contributes x but not y" — is upstream-shaped, and decoupling it is
what lets L3 be deleted later (option B) without also deleting L4.
Depends on `MutableCartesianChartRanges` gaining `tryUpdateX` and a neutral-y fallback — **the scroll
cluster's file.** At v3.3.1 `MutableCartesianChartRanges.getYRange` is still
`yRanges[axisPosition] ?: yRanges.getValue(null)` (`:40-41`) and `tryUpdate` (:47) still requires all
four of minX/maxX/minY/maxY, so there is no x-only path and `getValue(null)` still throws
`NoSuchElementException` when nothing contributed a y range. Note for them: **the neutral fallback is
an independent upstream bug fix**, worth keeping whatever happens to L3/L4.
**Risk: silent** — a pulse of 60 unioned against a weight of 150 opens the axis at zero, and because a
range union only widens, the first frame of a tab keeps it until an animation replaces it: a visible
jump on every tab entry.

## L5 — **RE-DERIVE**
Cannot be re-applied. v3.3.1's `drawInternal` reads the drawing model for the **reveal sweep**
(`sweepFraction`, guarded by a single-evaluation `isSweepInProgress` boolean working around ART JIT
bug b/553511933 — S2-A 2.5, commits `5cbc0b24c`, `632d413f0`). Blanket-nulling `drawingModel` would
disable the sweep and may disturb that guard. The re-derived shape: null only the *y-fraction* read
(`pointInfoMap`), leave the sweep's read intact. Express the condition as
`CartesianLayerRangeProvider.isStable: Boolean get() = true` — one defaulted line on a public
interface — so the layer does `if (!rangeProvider.isStable) null else …` instead of type-testing a
fork-only class, and meApp's delegating `rememberFollowing` wrapper (`ProductChart.kt:276-290`)
answers `false` naturally. That removes both the fork-only type test and the mutable public `var`.
**Risk: silent** (line and axis disagree continuously while scrolling).
**Sequencing: before L3** — the mapper is pointless while `pointInfoMap` still supplies y.

## L6 — **RE-APPLY (as a `val`)**
One guard line in a `protected open` method, plus parameter plumbing. Make it a constructor `val` and
put it in `equals`/`hashCode`; today it is a mutable `var` excluded from both, and `copy()` drops it
(`LineCartesianLayer.kt:1162-1170`), which only works because `rememberLineCartesianLayer` re-assigns
it afterwards (:1272-1273). A direct `copy()` caller gets the default back silently.
Must migrate **with** the second enforcement point, `CartesianChart.kt:576`
(`if (!layer.markerTargetsEnabled) continue` in `synthesizeInterpolatedTargets`) — meApp relies on
the invariant explicitly (`features/common/components/chart/GraphMarker.kt:119`).
At v3.3.1 `updateMarkerTargets` gained a `seriesKey` parameter (S2-A 1.5); the guard line itself is
unaffected.
**Risk: silent** (scrubbing lands on the 75th-percentile curve instead of the baby's measurement).

## L7 — **RE-APPLY**, possibly DELETE later
Still needed: at v3.3.1 `rangeProvider` and `verticalAxisPosition` are still `protected val`
(S2-D §1.5), and the consumers are library code. Two lines. Revisit after L3/L5 land — if the
mapper and `isStable` remove the host's need to find the provider, these go too.
**Risk: loud** (compile error).

## L8 — **RE-DERIVE**
v3.3.1 rewrote `forEachPointInBounds` and `collectPointsAndVisibleIndexRange` to draw from a
**visible-range slice** with interpolator-declared padding (`LineCartesianLayer.kt:746-803`,
`CartesianLayerModel.kt:114-141`). Our skip depends on the old behaviour "runs the action for one
point past the edge"; the slice changes which point that is. Re-derive against the slice, and check
first whether upstream's own per-frame `saveLayer` + sweep clip (S2-A 2.5) already covers it — a
nested clip would be harmless but redundant.
The honest long-term answer is unchanged: "a point drawn outside the plot" is an upstream bug, not a
meApp preference. **Send it upstream.**
**Risk: silent** (a filled dot outside the plot with no line under it — or, if over-tightened, a
dropped dot at the edge).

## L9 — **RE-APPLY**
Two arguments in `updateDimensions`; `dimensions.ensureValuesAtLeast(...)` is still at
`LineCartesianLayer.kt:897` at v3.3.1 with the same named parameters. `CartesianLayerPadding` cannot
go negative, so there is no consumer-side substitute. L8 is what makes it safe — without the clip,
removing the padding is exactly what let a dot spill outside; re-apply them together.
`14542561e`'s `updateLayerMargins` change was reverted by `a2a9a7690` and must **not** be revived
(see P1). **Risk: silent** (a week's seven points sit visibly inset).

## L11 — **RE-APPLY verbatim**
`CartesianChartModelProducer.kt` is **byte-identical** between `e67e49520` and `v3.3.1` (same blob
SHA), so the hunks replay with zero upstream drift. Only the import block
(three `kotlinx.coroutines.flow` imports added inside upstream's existing block, out of alphabetical
order) will collide; fix the ordering while re-applying.
Keep the latch in `getModel`'s `.also` (v3.3.1 :64-67) — that is the only place `cachedModel` is
assigned, and it runs on both branches. A latch placed at `startAnimation` instead would miss the
restored-model path, because `handleUpdate`'s `?: return` at :198 bypasses `getModel` entirely.
**Preserve the empty-transaction behaviour deliberately:** `getModel` returns `null` for an empty
partials list, so `pushEmptyProducer` (`BaseDashboardViewModel.kt:264-270`) never sets `isReady`, and
meApp dismisses that skeleton via `isEmptyGraph`. Do not "fix" it during the port.
MOVE was considered and rejected: `cachedModel` is private and `getCachedData` is a best-effort
`tryLock` pull that returns `null` on contention, so a consumer cannot derive the latch.
v3.3.1 has no model flow on the Cartesian producer (the *Pie* producer has exactly this pattern at
`pie/data/PieChartModelProducer.kt:28-30`, but it is `internal`). meApp consumers:
`features/dashboard/components/chart/GraphPagerView.kt:162`,
`features/dashboard/DashboardScreen.kt:398`.
**Risk: loud-ish** (the shimmer skeleton never dismisses, or dismisses too early).

## L12a — **RE-APPLY**
Producer byte-identical, so the plumbing replays. Reject the two alternatives on evidence:
a consumer `CartesianLayerDrawingModelInterpolator` that always returns the target suppresses only
the drawing-model interpolation — **the host's animation job still runs for the full
`Animation.DIFF_DURATION` (500 ms)** and the ranges are not interpolated by that path at all
(`CartesianChartModelProducer.kt:202-204`, `CartesianChartModel.kt:156-181`); and `animationSpec =
null` is host-wide, not per-transaction. meApp wants per-transaction.
Hoist the duplicated `prepareForTransformation(model, hostExtraStore, ranges)` out of both branches
while re-applying. Note the signature is **binary-incompatible** (`animate` is a leading parameter
before the trailing lambda) — fine, meApp recompiles against the artifact.
Also note the interface's package moved: `common.data` → `cartesian.data`
(`cartesian/data/CartesianLayerDrawingModelInterpolator.kt` is new at that path; the old path is a
deprecated `typealias`), and `.default()` is deprecated in favour of `.line()`, which changes the
default interpolator's identity for line layers (S2-A 2.6).
**Risk: silent** (points slide across the plot between two unrelated date ranges).

## L14a / L14b — **MOVE both; `Defaults.kt` leaves the fork**
`AXIS_LABEL_VERTICAL_PADDING` is still `4` at v3.3.1 (`common/Defaults.kt:30`) and is read in exactly
one place tree-wide: `cartesian/axis/AxisComponents.kt:39-40`, as the default of a **public**
`padding: Insets` parameter on `rememberAxisLabelComponent`. meApp already calls that factory
(`axis/Horizontalxis.kt:67-69`) and can pass `padding` explicitly. (meApp's *vertical* axis label uses
`rememberTextComponent`, whose padding defaults to `Insets.Zero`, so only the bottom axis is affected
today.) `AxisComponents.kt` has zero upstream churn in the range.
`RANGE_ANIM_DURATION` has exactly one reader, `ScrollAwareRangeProvider.kt:45` — move it there as a
constructor default or file constant. Its KDoc claims the `yTransform` fade shares it; the fade uses
its own hard-coded `100.milliseconds` / `150.milliseconds` (`LineCartesianLayer.kt:144-145`). **Trust
the code**; fix or drop the comment on the way.
With both moved, `common/Defaults.kt` is unmodified and leaves the fork.
**Also flag:** `CartesianChartHost.kt:40` still imports `Defaults.CHART_HEIGHT`, which v3.3.1 removed
(renamed to `CARTESIAN_CHART_AREA_HEIGHT` / `PIE_CHART_AREA_HEIGHT`, S2-A 1.9). Dangling import —
**loud**, will not resolve. Host cluster owns the file; flag it to them.

## L15 — **DELETE, and do it first**
`a2a9a7690` expanded four star imports into ~30 explicit ones and changed continuation indent from 4
to 2 throughout `LineCartesianLayer.kt`. Roughly half our 320 added lines on that file are
whitespace and imports, and every one will conflict against upstream's 130+/35−.
**Migration tactic:** before attempting anything on this file, re-apply upstream's formatting to our
HEAD version (or diff with `--ignore-all-space` plus star-import normalisation) so the eight real
adaptations are visible. S2-A 3.2 notes ktfmt moved 0.59 → 0.64 and touched 14 files; normalise
formatting across the whole port, not just here.

## C-1 — **RE-APPLY, re-sited**
`prepare` gained a `canvasSize: Size` parameter and its first half moved into a new
`measure(context, layerDimensions): Float`, with `getVerticalExtras` added for a pre-height pass
(S2-A 1.3). The insertion point survives — both `setLayerBounds` and the lines after it are unchanged
context — but the hunk must be placed in the right half of the split.
Reject `Zoom.x(N)` as a substitute (S2-B §7, own confidence "low"): it divides by the full
`bounds.width` where we divide by `availableWidth - startInsetPx`, and it applies at a different point
in the pipeline, interacting with `VicoZoomState`, `Zoom.Content` and the zoom→scroll compensation.
Not a swap to make during a migration.
**Fix the latent defect while re-applying** (S1-E): `visibleLabelsCount` is in neither `equals` nor
`hashCode`; `startInsetDp` is in `equals` but not `hashCode`. That breaks the contract on a `@Stable`
Compose type.
**Risk: silent** (the wrong number of periods per window — the thing the whole feature exists for).

## C-2 — **MOVE** (C3 resolved in favour of S2-B)
`CONTRADICTIONS.md` C3 is now settled against v3.3.1 source: `CartesianLayerPadding(unscalableStart = …)`
writes **the same field by the same mutator** our `CartesianChart.kt:299` patch does, and
`CartesianLayerPadding.kt`, `CartesianLayerDimensions.kt` and `MutableCartesianLayerDimensions.kt` are
byte-identical across fork point, HEAD and v3.3.1. meApp passes no `layerPadding` today, so the slot
is free. **Delete `startInsetDp` from `CartesianChart`; meApp passes
`layerPadding = { CartesianLayerPadding(unscalableStart = ChartStartGap) }`** (`ProductChart.kt:104`
becomes a `layerPadding` lambda instead of a `startInsetDp` argument;
`SnapshotChartStartGap` likewise on the snapshot card).

**What that does and does not change for my cluster.**
- **A5, A8 and C-3 are unaffected.** All three read `layerDimensions.startPadding` at draw time and
  nothing else, and both mechanisms land on that same field. They survive verbatim.
- **C-1 needs one re-derivation.** `calculateLabelSpacingScale`'s `- startInsetPx` term reads
  `startInsetDp` directly; with the property gone it must read
  `layerDimensions.unscalableStartPadding` instead. Same number, different source.
- Two caveats recorded by S3-A that touch my files: upstream **adds** `layerPadding.unscalableStart`
  to `maxPointSize.half` at `LineCartesianLayer.kt:901` where our patch **maxes** — but L9 removes the
  `maxPointSize.half` term entirely, so after L9 the two agree. Confirm that ordering holds when L9
  lands.
- `startInsetDp` also leaves `copy()` and `rememberCartesianChart`, which removes half of C-1's
  `equals`/`hashCode` defect for free.

## C-3 / C-4 — **RE-APPLY, clean grafts**
`CartesianChart.draw()` is byte-identical fork → v3.3.1 (P3), so both are mechanical.
C-3's only semantic risk is that `measure`/`prepare` now run at different times, so
`layerDimensions.startPadding` at draw time must be re-verified.
C-4's reorder is behaviourally visible to any `Decoration`, `CartesianMarker` or `Axis` that assumed
the old z-order — which is precisely why A1 and A6 have to be evaluated against *our* order, not
upstream's.

## C-5 — **RE-DERIVE** (see A6)
Instead of a bespoke `axisManager.drawMarkerDecorations(context)` call after the fading-edge restore,
move the generic `decorations.forEach { it.drawOverLayers(context) }` pass to that position. Same
one-statement library change, but it serves any consumer decoration rather than one axis concept, and
it is what lets A6 and (optionally) a future goal-line decoration leave the library.
Accepted consequence: a decoration's over-pass now draws **over** the composited data rather than
under it — which is upstream's own behaviour (`HorizontalLine` and `HorizontalBox` both override
`drawOverLayers`) and affects nothing today, since meApp passes no decorations
(`ProductChart.kt:87-105`).

---

# UNRESOLVED

**U1 — A8 vs v3.3.1's `AxisDimensions.lineBounds`.**
`VerticalAxis.updateAxisDimensions` publishes `lineBounds` from the axis's gutter `bounds`
(`VerticalAxis.kt:150-158`); A8 draws the line at `chartEdgeX()` instead. `HorizontalAxis.getLineStart`
/`getLineEnd` (:437-459) consume `lineBounds` to widen the horizontal clip. `lineBounds` cannot be
made to agree: it is set in the measuring phase and `chartEdgeX()` needs draw-time `scroll` and
`layerDimensions`. Probably benign — `getLineLeft`/`getLineRight` take `minOf`/`maxOf`, so a stale
`lineBounds` can only widen the clip, and A4b's clamp to `layerBounds` bounds it back — but
"probably" is not evidence.
**Exact check:** with A8 + A4b applied, log `lineLeft`/`lineRight` and the final clip rect for the
bottom axis at scroll 0, mid-scroll and max-scroll on the WEEK segment, and confirm the clip equals
`layerBounds` horizontally in all three.

**U2 — does deleting the margin halving (A12) change the chart's measured height, and by how much?**
It must: `layerMargins.vertical` feeds `freeHeight`, which feeds `setLayerBounds` and the vertical
axis's pass-2 width. v3.3.1 *also* changed what margins are subtracted from (`chartAreaHeight`,
S2-A 2.1). The two interact and neither can be reasoned about from source alone.
**Exact check:** run the S4-A parity harness on the weight dashboard at a fixed 199 dp chart height
for WEEK / MONTH / YEAR / TOTAL, before and after, and diff the plot rect. meApp pins a fixed height
(`GraphView.kt:98`, `SnapshotLineChart.kt:158`), so `constraints.hasFixedHeight` is true and the
extras come out of that height rather than adding to it — which is the case most likely to shift.

**U3 — dashed lines and `visiblePadding`.** For `LineStroke.Dashed`, `drawFullLineLength = true`
pins `visibleStart = 0` and the left-most segment uses the one-sided tangent regardless of
`visiblePadding`. Upstream's `CatmullRomInterpolator` is affected identically, so this is not ours to
fix — but it is ours to know about.
**Exact check:** `grep -rn "LineStroke.Dashed" app/src` in meApp. If `PercentileBand.kt` uses one,
render it in a horizontally scrollable chart and compare the left-edge segment's shape before and
after scrolling it to mid-screen.

**U4 — is the yTransform fade ever left stalled?** The state machine reads `elapsedNow()` and has no
invalidation of its own; it advances only while something else is redrawing. In practice the 300 ms
range animation is. A settle that produced *no* range movement would leave the fade to whatever else
happens to invalidate. S1-D could not establish whether such a case exists, and neither can I from
source.
**Exact check:** instrument `transformFadePhase` and log it on every draw; scroll the weight
dashboard between two windows that round to the same axis (which is exactly the case `e6c766cb8`
added `settleEpoch` for) and confirm the phase returns to 0.

**U5 — does `labelHorizontalPosition = End` on every label still read correctly once
`shiftExtremeLabels = true` is on?** A3a and A2b interact: upstream anchors the extremes inward,
A2b overrides that with a uniform `End`. The combination has never been rendered.
**Exact check:** screenshot the WEEK and YEAR bottom axes at scroll 0, mid-scroll and max-scroll, and
confirm the first and last date labels are neither clipped nor overlapping their neighbours.

---

# Order of work for this cluster

Ordered so that each step's prerequisites are already done and nothing has to be redone.

**Phase 0 — make the diff readable (before touching anything).**
1. **L15** — normalise formatting on `LineCartesianLayer.kt` (and tree-wide for the ktfmt 0.59 → 0.64
   drift, S2-A 3.2). Everything after this is roughly twice as fast and far less error-prone.
2. **L16** — confirm `pointSpacingProvider` has no code. Nothing to do.

**Phase 1 — the free deletions (no dependencies, shrink the conflict surface immediately).**
3. **L10** — revert `getVisibleXRange` to `internal`. `CartesianDrawingContext.kt` leaves the fork.
4. **L13** — keep `PreviewContext`; add `InlineUpdateContext` on the same key; point `runTransaction`
   at `getDispatcher()`. `CartesianChartModel.kt` leaves the fork.
5. **L14a + L14b** — move both constants to their consumers. `Defaults.kt` leaves the fork.
   Report the dangling `Defaults.CHART_HEIGHT` import to the host cluster.
6. **A12** — delete the halving and `ensureVerticalValuesAtLeast`. `CartesianLayerMargins.kt` leaves
   the fork. **Tell the marker cluster immediately — they are blocked on this.** Open U2.
7. **A7b** — delete `isLabelsScrollable`, `scrollValue`/`maxScroll`, `updateScrollState`,
   `effectiveScroll` and the four host call sites. **Tell the host cluster that H-7's first prepare
   pass dies with it.**
8. **A4a′** — delete the `SegmentedHorizontalAxisItemPlacer` hunk.
9. **A6′**, **A3b** — delete the gutter widening and the clip rewrite.
10. **L12b** — restore upstream's animating `registerForUpdates`. Run the (f) check first.

**Phase 2 — move what belongs to meApp (library-side deletions, meApp-side additions).**
11. **A11** — `ListItemPlacer` → meApp.
12. **L2b** then **L1** then **L2a** — add `getYAt` to the `Interpolator` seam, then move
    `MonotoneInterpolator` with `visiblePadding = 1`, then `InterpolationType`.
    Flag `getInterpolatedYValues` to the scroll cluster.
13. **A3a + A4a** — one meApp edit session on `HorizontalItemPlacer.kt`: switch to
    `aligned(shiftExtremeLabels = true)` and add the half-open filter to both overrides.
    **Write a test for the half-open rule in meApp** — there is none anywhere today, and this is the
    MOB-2953 headline fix.
14. **A1** — `Separators` → a meApp `Decoration`. Largest additive block leaves `HorizontalAxis.kt`.

**Phase 3 — the horizontal axis, onto a much smaller surface.**
15. **A2a + A2c** — re-apply, with the `LabelVerticalMode` default changed to `Outside`.
16. **A2b + A3c** — re-derive together as a nullable `labelHorizontalPosition`. Open U5.
17. **A4b** — clamp upstream's `lineLeft`/`lineRight` to `layerBounds`.
18. **A7a** — add the `Size.Scroll` case and the two behaviours meApp consumes; both `when (size)`
    sites are already in files we touch.

**Phase 4 — the start-of-chart feature, as one unit.**
19. **C-2** — delete `startInsetDp`; meApp passes `layerPadding = { CartesianLayerPadding(
    unscalableStart = ChartStartGap) }`. (C3 is resolved; no longer blocked.) Land this **after L9**,
    so the `maxPointSize.half` term is already gone and upstream's *add* and our former *max* agree.
20. **C-3 → A8 → A5**, in that order, landed and verified together with C-2. Open U1.
    Re-applying any of these alone produces a chart that compiles and is subtly wrong.
21. **C-1** — re-site into v3.3.1's `measure`/`prepare` split; re-derive the inset term to read
    `layerDimensions.unscalableStartPadding`; fix the `equals`/`hashCode` defect on
    `visibleLabelsCount`.

**Phase 5 — draw order, then the goal pill.**
22. **C-4** — re-apply the reorder (clean graft).
23. **C-5** — re-derive: move the `decorations.drawOverLayers` pass past the fading-edge restore;
    delete `AxisManager.drawMarkerDecorations`.
24. **A6** — move the pill to a meApp `Decoration`. Verify on the parity harness **before** deleting
    the `VerticalAxis` code, not after.
25. **A9**, **A10** — the two small `VerticalAxis` items, once the file has stopped moving.

**Phase 6 — the line layer, hardest last.** *(L9 must precede Phase 4's step 19 — see C-2. If the two
phases are worked by one person, do step 26 before step 19.)*
26. **L9**, **L8** — re-apply the padding removal and re-derive the point clip against v3.3.1's
    visible slice. Land them together; L8 is what makes L9 safe.
27. **L6**, **L7** — `markerTargetsEnabled` as a `val` (with `CartesianChart.kt:576`), and the two
    internal accessors.
28. **L5** — re-derive the drawing-model bypass as `CartesianLayerRangeProvider.isStable`, preserving
    v3.3.1's reveal-sweep read.
29. **L4** — re-derive as `contributesYRange`. Needs `MutableCartesianChartRanges.tryUpdateX` and the
    neutral-y fallback from the scroll cluster.
30. **L3** — re-derive the yTransform as a `CartesianLayerYMapper` seam; move the cache, the keys and
    the fade to meApp; replace `series.hashCode()` with `MetricBasis.version`. Open U4.

**Phase 7 — the producer (independent of everything above; can run in parallel from Phase 1).**
31. **L11**, **L12a** — re-apply verbatim onto a byte-identical file.

**Cross-cluster messages to send, and when:**
- *Immediately:* marker cluster — `ensureVerticalValuesAtLeast` does not survive (step 6).
- *Immediately:* host cluster — H-7's first prepare pass dies with A7b (step 7); and
  `CartesianChartHost.kt:40` has a dangling `Defaults.CHART_HEIGHT` import (step 5).
- *Phase 2:* scroll cluster — `InterpolationType` is moving; `getInterpolatedYValues` is theirs.
- *Phase 6:* scroll cluster — L4 needs `tryUpdateX` + the neutral-y fallback, and the latter is an
  independent upstream bug fix worth keeping regardless.
- *Resolved while this dossier was being written:* C3 settled in favour of `CartesianLayerPadding`.
  C-2 is now a MOVE, not a blocker, and Phase 4 can start as soon as L9 has landed.
