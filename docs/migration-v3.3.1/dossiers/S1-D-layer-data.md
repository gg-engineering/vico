# S1-D — Line layer, interpolation, and the data/model pipeline

Cluster dossier for the v3.3.1 migration. Refs per `BASELINE.md`: FORK_POINT = `e67e49520`,
OURS = `HEAD` (`MOB-2953-week-range-spans-eight-days`), TARGET = `v3.3.1` (not examined here —
another agent owns that side).

All library paths below are relative to
`vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/`.
All meApp paths are relative to `/Users/selvakumar/Projects/meApp-bug/Android/app/src/`.

Line numbers are **at HEAD** unless stated otherwise.

---

## Summary table

| id | name | files | conflict risk | undocumented | meApp-visible |
|---|---|---|---|---|---|
| **L1** | Monotone (Fritsch–Carlson) interpolator | `cartesian/layer/MonotoneInterpolator.kt` (NEW), `LineCartesianLayer.kt` L467–477 | **low** | n (FEATURE-3) | **y** |
| **L2** | Interpolated-Y sampling API (`getYAtXFromEntries`, `InterpolationType`) | `MonotoneInterpolator.kt` L100–159, `cartesian/InterpolationType.kt` (NEW) | **low** | n (FEATURE-8) | **y** |
| **L3** | Render-time `yTransform` + fade state machine | `LineCartesianLayer.kt` L114–143, 646, 827–921, 931–940, 1000–1006, 1156–1181, 1238–1242 | **high** | **y** | **y** |
| **L4** | Transformed layer withheld from the shared y range | `LineCartesianLayer.kt` L1057–1075 | **medium** | **y** | y (indirect) |
| **L5** | `alwaysUseLiveRange` + drawing-model bypass | `LineCartesianLayer.kt` L574–576, 612–620 | **medium** | **y** | **y** |
| **L6** | `markerTargetsEnabled` | `LineCartesianLayer.kt` L571–572, 595, 605, 703 | **low** | **y** | **y** |
| **L7** | `internalRangeProvider` / `internalVerticalAxisPosition` accessors | `LineCartesianLayer.kt` L121–122 | **low** | n (FEATURE-1) | n |
| **L8** | Keep points inside the plot (clip + skip) | `LineCartesianLayer.kt` L718–784 | **medium** | **y** (MOB-2953) | **y** |
| **L9** | Point size no longer pads the plot | `LineCartesianLayer.kt` L1047–1053 | **medium** | n (FEATURE-9) | **y** |
| **L10** | `getVisibleXRange()` made public | `cartesian/CartesianDrawingContext.kt` L55–56 | **low** | **y** | n |
| **L11** | `isReady: StateFlow<Boolean>` on the producer | `cartesian/data/CartesianChartModelProducer.kt` L39–50, 81–84 | **low** | **y** | **y** |
| **L12** | `animate` on `runTransaction` (instant-apply path) | `CartesianChartModelProducer.kt` L52–72, 124–127, 167–200, 212–240 | **medium** | **y** | **y** |
| **L13** | `InlineUpdateContext` (ex-`PreviewContext`), public, used by `runTransaction` | `CartesianChartModelProducer.kt` L170–172, 234–253; `cartesian/data/CartesianChartModel.kt` L236 | **low** | **y** | **y** |
| **L14** | `Defaults` / `Animation` constants | `common/Defaults.kt` L25–26, 32 | **low** | partial | **y** (visual) |
| **L15** | Whole-file reformat of `LineCartesianLayer.kt` | `LineCartesianLayer.kt` throughout | **high** (noise) | **y** | n |
| **L16** | `pointSpacingProvider` — added then **withdrawn** | none at HEAD | **none** | doc stale | n |

"meApp-visible" = the symbol or the behaviour is reachable from meApp source today.

---

## L1 — Monotone (Fritsch–Carlson) interpolator

### What it does
Adds a third line interpolator alongside upstream's `cubic` and `catmullRom`. It computes
monotone cubic tangents by the Fritsch–Carlson rule and emits cubic Béziers, so the drawn curve
never overshoots the data's own y bounds. It is a stateless `object`, exposed through one factory
on `LineCartesianLayer.Interpolator.Companion`.

### Why we needed it
Every chart in meApp plots health readings — weight, blood pressure, pulse, baby growth. Upstream's
Catmull-Rom overshoots at local extrema, which draws a weight the user never recorded and makes the
Android chart disagree with the iOS one (SwiftUI `.monotone`). `getYRange()` can also be returned
exactly (data min/max) instead of solving Bézier extrema, which keeps the axis honest.

### Commits
- `93cba5e1e` Feature-3: Add MonotoneInterpolator (Fritsch-Carlson, no overshoot)
- `57ce985ea` perf: Eliminate per-frame allocations and dead code *(removed the `List<Pair<…>>` `getYAtX` overload)*

### Files and hunks
- `cartesian/layer/MonotoneInterpolator.kt` — NEW, 173 lines. Draw path L46–92; `getYRange` L98;
  `computeMonotoneTangent` L167–172.
- `LineCartesianLayer.kt` L467–477 — one factory inside `Interpolator.Companion`:
  ```kotlin
  public fun monotone(): Interpolator = MonotoneInterpolator
  ```

### Public API added or changed
```kotlin
// LineCartesianLayer.Interpolator.Companion
public fun monotone(): LineCartesianLayer.Interpolator

// MonotoneInterpolator itself is `internal object` — NOT public.
internal object MonotoneInterpolator : LineCartesianLayer.Interpolator {
  override fun interpolate(
    context: CartesianDrawingContext,
    path: Path,
    points: List<Offset>,
    visibleIndexRange: IntRange,
  )
  override fun getYRange(y: List<Double>): ClosedRange<Double>
}
```

### Coupling to upstream internals
Minimal and all at the public seam: it implements the public `LineCartesianLayer.Interpolator`
interface (`interpolate` + `getYRange`) and uses `CartesianDrawingContext` only as an unread
parameter. It assumes `points` is ordered by x and that `visibleIndexRange` indexes into `points`
— both are upstream's own contract for `interpolate`.

The one non-obvious coupling: `interpolate` computes secants/tangents over **all** `n` points, not
just the visible range, because a monotone tangent at the first visible point depends on its
off-screen neighbour. Two `FloatArray(n)` allocations per series per frame. Upstream's interpolators
allocate nothing.

### Could it be an extension point, or live in meApp instead?
**Yes — this is the cleanest candidate for de-forking in the whole cluster.** `Interpolator` is a
public interface with a public implementing seam; nothing in `MonotoneInterpolator` touches
internal state. It can move into meApp verbatim as
`com.dmdbrands…chart.MonotoneInterpolator : LineCartesianLayer.Interpolator`, with call sites
changing from `LineCartesianLayer.Interpolator.monotone()` to `MonotoneInterpolator`. Blockers:
(a) `visibleIndexRange: IntRange` and `getYRange` must remain part of the public `Interpolator`
contract at v3.3.1 — unverified here; (b) L2 below (`getYAtXFromEntries`) is called from two
*library* files, so moving the object out of the library means that half has to move too or be
duplicated. Worth sequencing L1+L2 as one de-fork.

### Conflict risk
**Low.** One added factory in a companion object plus a brand-new file. If upstream reshuffled the
`Interpolator.Companion` the fix is to re-add one line.

---

## L2 — Interpolated-Y sampling API (`getYAtXFromEntries`, `InterpolationType`)

### What it does
Exposes the monotone curve as a *function* — given an arbitrary x, return the curve's y — so that
callers outside the draw pass can ask "what is the line's value here?". Adds a two-value
`InterpolationType` enum (`MONOTONE`, `LINEAR`) so a caller can pick the sampling rule.

### Why we needed it
Scrubbing. meApp's marker follows the finger continuously, not point-to-point: `CartesianChart`
synthesizes a marker target at an arbitrary x by sampling the curve
(`CartesianChart.kt:582`), and `VicoScrollState.getInterpolatedYValues` supplies fallback readings
for the chart header when the scrubbed x has no real entry
(`GraphView.kt:413–415`). Both must sample the *same* curve the line draws, or the marker dot sits
off the line.

### Commits
- `7baf04e8b` Feature-8: Add interpolated marker targets + scroll helper APIs *(adds both)*
- `25dc072a9` fix(interpolation): Return null outside data range instead of clamping
- `57ce985ea` perf: Eliminate per-frame allocations and dead code *(deletes the `List<Pair<Double,Double>>` overload, keeping only the `Entry`-based one)*

### Files and hunks
- `cartesian/InterpolationType.kt` — NEW, 26 lines, whole file is the enum.
- `MonotoneInterpolator.kt` L100–152 — `getYAtXFromEntries`: binary-searches the bracketing
  segment, computes only the 2–3 secants that segment's tangents need, then evaluates the Hermite
  basis. O(log n), zero allocation.
- `MonotoneInterpolator.kt` L108–109 — the `25dc072a9` behaviour change:
  ```kotlin
  if (x < series.first().x) return null
  if (x > series.last().x) return null
  ```
  (was: clamp to the first/last y). Out-of-range now yields *no* marker rather than a flat one.

### Public API added or changed
```kotlin
public enum class InterpolationType { MONOTONE, LINEAR }

// on the internal object MonotoneInterpolator — `public` modifier on an internal object,
// so effectively module-internal:
public fun getYAtXFromEntries(x: Double, series: List<LineCartesianLayerModel.Entry>): Double?
```
Note the visibility oddity: `getYAtXFromEntries` is declared `public` but lives on an `internal
object`, so it is **not** reachable from meApp. meApp reaches the same capability only through
`VicoScrollState.getInterpolatedYValues(...)` (S1 cluster for scroll owns that signature).

### Coupling to upstream internals
- Depends on the shape of `LineCartesianLayerModel.Entry` (`x`, `y` as `Double`) and on series
  being sorted ascending by x — upstream's invariant, not ours.
- Two **library** call sites outside this cluster depend on it:
  `cartesian/CartesianChart.kt:582` (`synthesizeInterpolatedTargets`) and
  `cartesian/VicoScrollState.kt:164`. Both are themselves fork additions.
- `25dc072a9`'s null-outside-range contract is consumed with `?: continue` in `CartesianChart.kt:582`
  — a target is simply not produced. Changing it back to clamping silently resurrects flat
  phantom markers.

### Could it be an extension point, or live in meApp instead?
Partly. The *algorithm* can live in meApp (see L1). The *consumers* cannot: `CartesianChart`'s
synthesized marker targets are built inside the library's draw pass. The clean de-fork is to make
upstream's `Interpolator` interface the sampling seam — i.e. add `getYAt(x, series): Double?` as an
optional method on our fork's `Interpolator` and have `CartesianChart` call *that*, so meApp's
own `MonotoneInterpolator` supplies it. That turns a copied algorithm into one extra interface
method. `InterpolationType` itself is pure meApp policy (two of our own names) and should move out
of the library regardless — only `VicoScrollState.getInterpolatedYValues` reads it.

### Conflict risk
**Low** for the new files. **Low–medium** for the call sites, which belong to other clusters
(`CartesianChart.kt`, `VicoScrollState.kt`) and are themselves hot.

---

## L3 — Render-time `yTransform` + fade state machine

> The single hardest item in this cluster. Full chronology in **"yTransform: the full story"** below;
> this section states the final shape.

### What it does
Lets the caller supply a lambda that rewrites a series' y values **at draw time**, after the model
and the drawing model have been built. The layer caches the lambda's `DoubleArray` result and
re-invokes it only on four triggers (first draw, series change, animation-target change, window
settle), cross-fading between the old and new arrays so the correction is not a snap. Both y-mapping
paths in the layer (`collectPointsAndVisibleIndexRange` and `forEachPointInBounds`) read the cached
array instead of `entry.y`.

### Why we needed it
The weight dashboard overlays a **second metric in a different unit** on the primary metric's axis
(e.g. pulse over weight, or a secondary reading over the main one). There is one y axis. The
overlay has to be squeezed into the primary's axis box, scaled against *the values currently in
view* — meApp's `GraphUtil.normalizeYValues` maps a `MetricRange` into an inset band of the
primary's `[minY, maxY]` (`features/common/helper/graph/GraphUtil.kt:258–292`). That scaling depends
on the live window and the live animated axis, neither of which exists until draw time, so no
amount of pre-processing in the view model can produce it.

### Commits (chronological)
- `037db5da5` feat(yTransform): Add render-time Y transform to LineCartesianLayer
- `cbfc2a365` feat: yTransform screen-fraction animation with per-frame caching
- `a77154f85` feat: alwaysUseLiveRange + buildCache on model change + animate on metric switch
- `0a35ee586` feat: yTransform target-based caching + fade correction (v4.0.26)
- `0fc9c7bf3` MOB-3494: recompute a y transform when its window moves
- `f436d60cd` MOB-3494: keep a transformed layer out of the shared y range *(→ L4)*
- `e6c766cb8` MOB-3494: keep the axis and a transformed line in step

### Files and hunks
`LineCartesianLayer.kt` only.

- **L110–119** — the protected constructor parameter:
  ```kotlin
  protected val yTransform: ((
    series: List<LineCartesianLayerModel.Entry>,
    yRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = null,
  ```
  It is inserted **after** `drawingModelKey`, i.e. at the end of the protected constructor's
  parameter list.
- **L124–143** — ten mutable instance fields holding the cache and fade state:
  `transformCacheResult: DoubleArray?`, `transformIndexMap: Map<Double, Int>?`,
  `transformLastSeriesKey: Long`, `transformLastTargetKey: Long`, `transformLastWindowKey: Long?`,
  `transformFadePhase: Int`, `transformFadeStart: kotlin.time.TimeMark?`,
  `transformFadeOpacity: Float` (`internal var … private set`),
  `transformPendingResult`, `transformPendingIndexMap`, plus
  `fadeOutDuration = 100.milliseconds`, `fadeInDuration = 150.milliseconds`.
- **L646** — the fade is applied by multiplying into upstream's layer opacity:
  ```kotlin
  saveLayer(opacity = (drawingModel?.opacity ?: 1f) * transformFadeOpacity)
  ```
- **L827–921** — the whole decision block, inside `collectPointsAndVisibleIndexRange`. Shape:
  ```kotlin
  val transformedY: DoubleArray? = if (yTransform != null) {
    val seriesKey = series.hashCode().toLong()
    val seriesChanged = transformLastSeriesKey != 0L && transformLastSeriesKey != seriesKey
    val targetYRange = ranges.getTargetYRange(verticalAxisPosition)
    val targetKey = targetYRange.minY.toBits() xor (targetYRange.maxY.toBits() * 31)
    val windowKey = (rangeProvider as? WindowSettleSignal)?.settleEpoch?.toLong() ?: 0L
    val windowChanged = transformLastWindowKey != null && transformLastWindowKey != windowKey
    if (transformCacheResult == null || seriesChanged) { /* snap, no fade */ }
    if ((targetKey != transformLastTargetKey || windowChanged) && transformFadePhase == 0) {
      val pending = yTransform.invoke(series, targetYRange, getVisibleXRange())
      …
      if (pending != null && !pending.contentEquals(transformCacheResult)) { /* start fade */ }
    }
    when (transformFadePhase) { 1 -> /* fade out */; 2 -> /* fade in */; else -> … }
    transformCacheResult
  } else null
  ```
  Note the transform is always invoked with **`targetYRange`**, never the animating `yRange` — see
  the chronology; computing against the range the chart is *leaving* strands the line off-plot.
- **L931–940** — `getDrawY` inside `collectPointsAndVisibleIndexRange`, rewritten to prefer the
  drawing-model fraction and otherwise substitute the transformed value:
  ```kotlin
  val idx = transformIndexMap?.get(entry.x)
  val rawY = if (idx != null && transformedY != null) transformedY[idx] else entry.y
  ((rawY - yRange.minY) / yRange.length).toFloat() * layerBounds.height
  ```
  This hunk also hoists `val yRange = ranges.getYRange(verticalAxisPosition)` out of `getDrawY` to
  L830 (a per-point call became a per-draw one).
- **L1000–1006** — the same substitution in `forEachPointInBounds.getDrawY`, but reading
  `transformCacheResult` directly (not the local `transformedY`), so points and line agree.
- **L1156–1181** — `copy()` gained a `yTransform` parameter and now has a body that hand-copies
  seven cache fields onto the new instance so layer recreation does not snap the line.
- **L1238–1242** — `rememberLineCartesianLayer` gained `yTransform`, `markerTargetsEnabled`,
  `alwaysUseLiveRange`.

### Public API added or changed
```kotlin
// protected constructor — new trailing parameter
protected constructor(
  …,
  protected val drawingModelKey: ExtraStore.Key<LineCartesianLayerDrawingModel>,
  protected val yTransform: ((
    series: List<LineCartesianLayerModel.Entry>,
    yRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = null,
)

// public constructor — two new trailing parameters
public constructor(
  lineProvider: LineProvider,
  pointSpacing: Dp = Defaults.POINT_SPACING.dp,
  rangeProvider: CartesianLayerRangeProvider = CartesianLayerRangeProvider.auto(),
  verticalAxisPosition: Axis.Position.Vertical? = null,
  drawingModelInterpolator: CartesianLayerDrawingModelInterpolator<
    LineCartesianLayerDrawingModel.Entry, LineCartesianLayerDrawingModel> =
      CartesianLayerDrawingModelInterpolator.default(),
  yTransform: ((
    series: List<LineCartesianLayerModel.Entry>,
    yRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = null,
  markerTargetsEnabled: Boolean = true,
)

// copy() — new trailing parameter, and the return type is no longer an expression body
public fun copy(
  lineProvider: LineProvider = this.lineProvider,
  pointSpacing: Dp = this.pointSpacing,
  rangeProvider: CartesianLayerRangeProvider = this.rangeProvider,
  verticalAxisPosition: Axis.Position.Vertical? = this.verticalAxisPosition,
  drawingModelInterpolator: CartesianLayerDrawingModelInterpolator<
    LineCartesianLayerDrawingModel.Entry, LineCartesianLayerDrawingModel> =
      this.drawingModelInterpolator,
  yTransform: ((
    series: List<LineCartesianLayerModel.Entry>,
    yRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = this.yTransform,
): LineCartesianLayer

// @Composable factory — three new trailing parameters
@Composable
public fun rememberLineCartesianLayer(
  lineProvider: LineCartesianLayer.LineProvider = …,
  pointSpacing: Dp = Defaults.POINT_SPACING.dp,
  rangeProvider: CartesianLayerRangeProvider = remember { CartesianLayerRangeProvider.auto() },
  verticalAxisPosition: Axis.Position.Vertical? = null,
  drawingModelInterpolator: … = remember { CartesianLayerDrawingModelInterpolator.default() },
  yTransform: ((
    series: List<LineCartesianLayerModel.Entry>,
    yRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = null,
  markerTargetsEnabled: Boolean = true,
  alwaysUseLiveRange: Boolean = false,
): LineCartesianLayer

// internal, read by nothing outside the class today
internal var transformFadeOpacity: Float   // private set
```

Supporting public API this depends on, added in **other** files (other clusters own them, but the
transform cannot work without them):
```kotlin
// cartesian/data/CartesianChartRanges.kt:44 — new interface method WITH a default
public fun getTargetYRange(axisPosition: Axis.Position.Vertical?): YRange = getYRange(axisPosition)

// cartesian/data/WindowSettleSignal.kt — NEW public interface
public interface WindowSettleSignal { public val settleEpoch: Int }

// cartesian/CartesianDrawingContext.kt:55 — visibility widened (see L10)
public fun CartesianDrawingContext.getVisibleXRange(): ClosedFloatingPointRange<Double>
```

### Coupling to upstream internals
This is the deepest reach in the cluster. Specifically:

1. **The draw pass, twice.** Both `collectPointsAndVisibleIndexRange` (L820) and
   `forEachPointInBounds` (L976) are upstream functions whose local `getDrawY` we rewrote. Any
   upstream change to how a point's canvas y is derived lands exactly on our hunks.
2. **Upstream's drawing-model/animation pipeline is bypassed, not extended.** Upstream animates y
   by interpolating `LineCartesianLayerDrawingModel.Entry.y` *fractions* in `extraStore`.
   `yTransform` ignores that and re-derives y from the live `ranges`. The two coexist only because
   `getDrawY` prefers `pointInfoMap` when it is non-null — and L5 (`alwaysUseLiveRange` /
   `ScrollAwareRangeProvider`) forces it null for the layers that use `yTransform`.
3. **`saveLayer(opacity = …)`** — our fade multiplies into upstream's drawing-model opacity at
   L646. If upstream changes where layer opacity is applied, the fade silently stops working.
4. **`ranges.getTargetYRange`** — a method we added to a public upstream interface, implemented for
   real only by our own `AnimatedYCartesianChartRanges` inside `CartesianChartHost.kt:721`. The
   default implementation returns `getYRange`, so on any other ranges implementation the transform
   degrades to "recompute whenever the animated range moves" — i.e. per frame.
5. **`rangeProvider as? WindowSettleSignal`** — the layer type-tests its own `rangeProvider`.
   `ScrollAwareRangeProvider` implements it (`ScrollAwareRangeProvider.kt:48,145`) and
   `CartesianChartHost.kt:654` does `provider.settleEpoch++`. meApp *also* implements it inline
   (`ProductChart.kt` `rememberFollowing`, `override val settleEpoch get() = basis?.version ?: 0`).
6. **Object identity across recomposition.** The cache lives in instance fields, so `copy()` must
   hand-carry it (L1171–1179). This depends on upstream keeping the
   `lineCartesianLayerWrapper?.copy(...) ?: LineCartesianLayer(...)` pattern in
   `rememberLineCartesianLayer`.
7. **Wall-clock timing inside the draw pass.** `TimeSource.Monotonic` + `elapsedNow()` drive the
   fade. There is no invalidation: the fade only advances if something else is already redrawing
   the chart every frame (the range animation is). If a frame is skipped the fade stalls at
   whatever opacity it reached.

### Known rough edges found while reading (not fixed here)
- **`yTransform` is not a `remember` key.** `rememberLineCartesianLayer`'s key list (L1247–1253) is
  `lineProvider, pointSpacing, rangeProvider, verticalAxisPosition, drawingModelInterpolator`.
  `yTransform`, `markerTargetsEnabled` and `alwaysUseLiveRange` are passed into the block but are
  not keys, so a *changed* lambda is only picked up if one of the five keys also changed. In
  `ProductChart.kt` the secondary layer's lambda captures `config` (and therefore
  `secondaryMetricKey.naturalSpan`); I could not establish from reading alone whether switching the
  secondary metric always moves one of the five keys. **Flagging as suspected stale-capture; verify
  before porting.**
- **`copy()` drops `markerTargetsEnabled` and `alwaysUseLiveRange`** (L1162–1170 construct a new
  layer without them). `rememberLineCartesianLayer` re-assigns them afterwards (L1272–1273), so the
  composable path is fine; a direct `copy()` caller is not.
- **`equals`/`hashCode` (L1183–1196) ignore `yTransform`, `markerTargetsEnabled`,
  `alwaysUseLiveRange`.** Two layers differing only in a transform compare equal.
- **`transformIndexMap` is keyed on `Double` x values** and rebuilt (`associate`) whenever the
  transform is recomputed — one `HashMap` of `series.size` boxed `Double` keys per recompute.
- **No test covers any of this at HEAD.** `VisibleEntryKeyTest.kt` was added by `0fc9c7bf3` and
  deleted again by `e6c766cb8`. The only surviving tests touching the area are
  `MutableCartesianChartRangesTest.kt` (L4's x-only path).

### Could it be an extension point, or live in meApp instead?
Not as written, but the goal is reachable and this is where the "minimise the fork" decision should
bite hardest. Three framings, cheapest first:

- **(a) Keep it, but shrink it to a seam.** Replace the cache + fade machinery with a single
  upstream hook — e.g. a `CartesianLayerYMapper` interface the layer consults in `getDrawY` — and
  move *all* caching, keying and fading into meApp's implementation of it. The library change
  collapses from ~130 lines of stateful draw-pass logic to one nullable field and two one-line call
  sites. meApp already owns the state that decides when to recompute (`MetricBasis.version`), so it
  is the natural home for the cache. This is the recommendation.
- **(b) Move it out entirely — a second chart.** The overlay is a second metric on a borrowed axis.
  If meApp instead pushed *pre-normalised* y values into the model producer and recomputed them
  when the window settles, no library change is needed at all. The blocker is that the normalisation
  needs the live animated axis, which meApp cannot see — unless `ScrollAwareRangeProvider` exposes
  its settled `[minY, maxY]` to meApp (it already computes it) and meApp accepts that the overlay
  re-normalises one frame late. Given the fade already masks a one-settle correction, "one frame
  late" may be indistinguishable. **Worth costing properly — it would delete L3, L4 and L5.**
- **(c) Leave as-is.** Lowest port effort, highest ongoing cost: it is the piece most likely to
  break on every future upstream bump, and it is untested.

### Conflict risk
**High.** `LineCartesianLayer.kt` is hot on both sides (ours 320+/54-, upstream 130+/35-), the
hunks sit inside the two functions most likely to have been touched upstream (`getDrawY` paths),
and the whole file was reformatted by `a2a9a7690` (L15), so even unchanged regions will not apply
cleanly.

---

## L4 — Transformed layer withheld from the shared y range

### What it does
A layer that has a `yTransform` no longer unions its own y values into the chart's y range. It
contributes **only** its x range, via a new `tryUpdateX`. Its drawn values come from the transform,
which maps into another layer's range, so its raw values describe nothing the axis should cover.

### Why we needed it
A pulse of 60 unioned against a weight of 150 opened the axis at zero, so the *secondary* metric
decided the *primary* metric's gridlines. Because a range union only ever widens, the first frame of
a tab kept that range until an animation replaced it — a visible jump on every tab entry.

### Commits
- `f436d60cd` MOB-3494: keep a transformed layer out of the shared y range

### Files and hunks
`LineCartesianLayer.kt` L1057–1075, at the head of `updateChartRanges`:
```kotlin
if (yTransform != null) {
  chartRanges.tryUpdateX(
    rangeProvider.getMinX(model.minX, model.maxX, model.extraStore),
    rangeProvider.getMaxX(model.minX, model.maxX, model.extraStore),
  )
  return
}
```
The same commit added `tryUpdateX` and a `NeutralYRange` fallback to
`cartesian/data/MutableCartesianChartRanges.kt` (L49, L41, L114, L128) — that file is listed in
BASELINE as **upstream-untouched**, so it grafts clean, but it is not in my cluster.

### Public API added or changed
None on my files. The companion change on `MutableCartesianChartRanges` (other cluster):
```kotlin
public fun tryUpdateX(minX: Double, maxX: Double)
```
plus `getYRange` now falls back to a neutral `0..1` range instead of failing a map lookup, because
"a chart whose every layer is transformed contributes no y range at all" is now reachable.

### Coupling to upstream internals
- Overrides `updateChartRanges`, an upstream open method, and short-circuits it.
- Depends on `MutableCartesianChartRanges` growing an x-only update and a neutral y fallback.
  If upstream's v3.3.1 `MutableCartesianChartRanges` changed shape, this breaks with it.
- Implicitly depends on `rangeProvider.getMinX/getMaxX` still being the x contract.

### Could it be an extension point, or live in meApp instead?
This one *is* close to an extension point already. The general statement — "this layer contributes
x but not y" — is a reasonable upstream concept and could be a plain boolean/`CartesianLayer`
property (`contributesYRange`) rather than being inferred from `yTransform != null`. Decoupling it
from `yTransform` would also let option (b) under L3 keep this behaviour while deleting the
transform. Recommend expressing it as a flag regardless of what happens to L3.

### Conflict risk
**Medium.** The hunk is small and sits at the top of a method, but it is in the hot file and it
depends on a sibling file's new API landing first.

---

## L5 — `alwaysUseLiveRange` + drawing-model bypass

### What it does
Two related switches that make a layer ignore upstream's cached, animation-interpolated drawing
model and compute every point's y from the live `ranges` each frame. It triggers automatically when
`rangeProvider is ScrollAwareRangeProvider`, and manually via a public `alwaysUseLiveRange` flag for
layers that merely *follow* someone else's scroll-aware range.

### Why we needed it
`ScrollAwareRangeProvider` animates the y range as the user scrolls. Upstream's drawing model bakes
each point's y into a **fraction of the range at model-build time**, so with a live range the line
would be positioned against a range it no longer has — the line and the axis would disagree
continuously. The overlay layers (`secondaryLayer`, and originally the percentile band) share the
primary's provider through a delegating wrapper, which is not *itself* a `ScrollAwareRangeProvider`,
hence the explicit flag.

### Commits
- `e0b886359` Feature-1: Add ScrollAwareRangeProvider … *(the `rangeProvider is ScrollAwareRangeProvider` branch)*
- `a77154f85` feat: alwaysUseLiveRange + buildCache on model change + animate on metric switch *(the flag)*

### Files and hunks
- `LineCartesianLayer.kt` L574–576:
  ```kotlin
  /** When true, skip the drawing model cache and always compute line positions from the live
   *  animated Y range. Use for layers that share another layer's scroll-aware range. */
  public var alwaysUseLiveRange: Boolean = false
  ```
- `LineCartesianLayer.kt` L612–620, in `drawInternal`:
  ```kotlin
  val drawingModel = if (rangeProvider is ScrollAwareRangeProvider || alwaysUseLiveRange) {
    null
  } else {
    extraStore.getOrNull(drawingModelKey)
  }
  ```
- `rememberLineCartesianLayer` L1244, L1273.

### Public API added or changed
```kotlin
public var LineCartesianLayer.alwaysUseLiveRange: Boolean   // mutable property, default false
// and the rememberLineCartesianLayer parameter  alwaysUseLiveRange: Boolean = false
```

### Coupling to upstream internals
- Reads `extraStore.getOrNull(drawingModelKey)` — upstream's private-by-convention animation cache —
  and conditionally discards it. The *entire* diff-animation feature is switched off for these
  layers; `prepareForTransformation`/`transform` still run and still write the key, the draw just
  ignores it.
- Type-tests `rangeProvider is ScrollAwareRangeProvider`, a fork-only class
  (`cartesian/data/ScrollAwareRangeProvider.kt`).
- It is a **mutable public `var` on a layer that is otherwise immutable and value-compared**;
  `equals`/`hashCode` ignore it (see L3 rough edges).

### Could it be an extension point, or live in meApp instead?
The condition is upstream-shaped — "this layer's range is not stable between model builds, so don't
cache fractions" — and would be better as a property of the **range provider** than of the layer:
`CartesianLayerRangeProvider.isStable` (or `isLive`), with the layer doing
`if (!rangeProvider.isStable) null else extraStore.getOrNull(drawingModelKey)`. That removes both
the fork-only type test and the mutable flag, and meApp's delegating wrapper would answer `false`
naturally. Concrete, small, and it turns two patches into one upstream-shaped predicate.

### Conflict risk
**Medium.** `drawInternal`'s first statements are prime territory for upstream churn, and the hunk
is three lines expanded from one.

---

## L6 — `markerTargetsEnabled`

### What it does
A per-layer flag that stops a layer from contributing marker targets. The layer still draws; it
just never appears under the scrub marker.

### Why we needed it
The baby growth chart draws seven faded CDC percentile curves behind the data line
(`features/common/components/chart/layer/PercentileBand.kt`). Those are context, not readings —
scrubbing must land on the baby's own measurement, never on the 75th-percentile curve.

### Commits
- `2ddae34f4` feat: markerTargetsEnabled flag + scrollable always-on for scrub parent blocking

### Files and hunks
- `LineCartesianLayer.kt` L571–572 — the property.
- `LineCartesianLayer.kt` L595, L603–606 — public constructor gained
  `markerTargetsEnabled: Boolean = true` and a body that assigns it.
- `LineCartesianLayer.kt` L703 — the guard, first line of `updateMarkerTargets`:
  ```kotlin
  if (!markerTargetsEnabled) return
  ```
- `LineCartesianLayer.kt` L1243, L1270, L1272 — composable factory.

### Public API added or changed
```kotlin
public var LineCartesianLayer.markerTargetsEnabled: Boolean   // mutable property, default true
// public constructor parameter      markerTargetsEnabled: Boolean = true   (trailing)
// rememberLineCartesianLayer param  markerTargetsEnabled: Boolean = true
```

### Coupling to upstream internals
- Overrides the behaviour of `protected open fun CartesianDrawingContext.updateMarkerTargets(...)`
  by early-returning — an upstream method, but an `open` one, so the coupling is shallow.
- A **second** enforcement point lives outside this cluster:
  `cartesian/CartesianChart.kt:576` (`if (!layer.markerTargetsEnabled) continue`) in
  `synthesizeInterpolatedTargets`. Both must be migrated together or scrubbing an interpolated x
  will produce percentile-band targets. meApp relies on this invariant explicitly —
  `features/common/components/chart/GraphMarker.kt:119` comments that flattening targets is safe
  *because* disabled layers were already excluded.
- Again a mutable `var` excluded from `equals`/`hashCode`.

### Could it be an extension point, or live in meApp instead?
Yes, cheaply. It does not need to be a layer property at all — meApp could filter targets in its own
`CartesianMarker` implementation, which it already has. The only thing lost is the small cost of
building targets nobody uses (7 series × visible points per frame). Alternatively, upstream almost
certainly has a `CartesianMarker.Target` filtering seam at v3.3.1 worth checking. If kept, it should
be a constructor `val`, not a `var`, and should participate in `equals`.

### Conflict risk
**Low.** One guard line in an `open` method, plus parameter plumbing.

---

## L7 — `internalRangeProvider` / `internalVerticalAxisPosition` accessors

### What it does
Two `internal` getters that re-expose the layer's `protected val rangeProvider` and
`protected val verticalAxisPosition` to other classes in the module.

### Why we needed it
`CartesianChartHost` has to find the chart's `ScrollAwareRangeProvider` to drive the y-range
animation, and `CartesianChart` has to know which vertical axis a layer draws against when
synthesizing an interpolated marker target. Both are *module* siblings but not subclasses, so
`protected` is not enough.

### Commits
- `e0b886359` Feature-1: Add ScrollAwareRangeProvider for dynamic Y-axis range on scroll

### Files and hunks
`LineCartesianLayer.kt` L120–122:
```kotlin
// Internal accessors for CartesianChartHost scroll-aware range support.
internal val internalRangeProvider: CartesianLayerRangeProvider get() = rangeProvider
internal val internalVerticalAxisPosition: Axis.Position.Vertical? get() = verticalAxisPosition
```

### Public API added or changed
None — `internal`, so absent from the published ABI.

### Coupling to upstream internals
Consumers, all fork-added code in other clusters:
`CartesianChartHost.kt:106, 148, 310, 576` and `CartesianChart.kt:578`.
The accessors themselves only assume the two `protected val`s keep their names and types.

### Could it be an extension point, or live in meApp instead?
Cannot live in meApp — the consumers are library code. It could disappear entirely if upstream
v3.3.1 already exposes `rangeProvider`/`verticalAxisPosition` more widely, which is worth checking
before porting; otherwise it is the smallest possible patch for the need and should stay.

### Conflict risk
**Low.** Two lines inserted immediately after the constructor's closing paren. The insertion point
is right next to L3's field block, which is why they will arrive in the same conflict hunk.

---

## L8 — Keep points inside the plot (clip + skip)

### What it does
Wraps the point/data-label loop in `canvas.save()` / `clipRect(layerBounds)` / `canvas.restore()`,
and additionally skips drawing a point whose centre is outside the plot horizontally.

### Why we needed it
`forEachPointInBounds` deliberately runs its action for the first point *past* the edge, so the
line carries on to the boundary instead of stopping short. Nothing clipped the dot drawn on top of
it, so a filled circle sat outside the plot with no line under it. Clipping alone was not enough
either: a half-clipped dot reads as a rendering fault, so a dot whose centre has left the plot is
omitted while the clip still guards everything else (labels, the line's own overdraw).

### Commits
- `d39964206` Feature-9: Add Size.Scroll for scrollable vertical axis + point clip fix *(first version)*
- `82f8567f7` MOB-2953: Keep points inside the plot *(final form, with the centre-outside skip)*

### Files and hunks
`LineCartesianLayer.kt` L718–784, in `drawPointsAndDataLabels`:
```kotlin
canvas.save()
canvas.clipRect(layerBounds)
forEachPointInBounds(series = series, drawingStart = drawingStart, pointInfoMap = pointInfoMap) {
  chartEntry, x, y, previousX, nextX ->
  val point = line.pointProvider?.getPoint(chartEntry, seriesIndex, model.extraStore)
  if (x >= layerBounds.left && x <= layerBounds.right) point?.draw(this, x, y)
  …
}
canvas.restore()
```
(Upstream was an unguarded `point?.draw(this, x, y)`.) L668 has an unrelated pre-existing
`canvas.restore()`.

### Public API added or changed
None.

### Coupling to upstream internals
- Reaches the `Canvas` directly through `CartesianDrawingContext.canvas` and manipulates the clip
  stack around an upstream loop. Any upstream clip of its own around the same region would nest
  (harmless) or conflict textually (likely).
- Depends on the documented-by-us behaviour of `forEachPointInBounds` — that it runs the action for
  one point past the edge. If upstream tightens that at v3.3.1, the skip becomes a dropped point.
- Depends on `layerBounds` being the plot rect in canvas coordinates at this moment in the draw.

### Could it be an extension point, or live in meApp instead?
No — it is a correctness fix inside upstream's own draw loop, with no seam to hang it on. The honest
de-fork is to **send it upstream**: "point drawn outside the plot" is an upstream bug, not a meApp
preference. Short of that, keep it; it is small and self-contained.

### Conflict risk
**Medium.** Small hunks, but they bracket a loop body that upstream also edits, and the comment
block is 10 lines of our prose immediately before the first changed line.

---

## L9 — Point size no longer pads the plot

### What it does
Removes `maxPointSize.half` from the layer's unscalable start and end padding, so the plot no longer
reserves half a dot's width at each edge.

### Why we needed it
Part of the v3-parity layout work: the plot had to start and end exactly at the axis, matching the
iOS chart and the Figma frames. With the padding in, a week's seven points sat visibly inset. L8's
clipping is what makes this safe — without the clip, removing the padding is exactly what let a dot
spill outside.

### Commits
- `d39964206` Feature-9: Add Size.Scroll for scrollable vertical axis + point clip fix

### Files and hunks
`LineCartesianLayer.kt` L1046–1053, in `updateDimensions`:
```kotlin
val xSpacing = maxPointSize + pointSpacing.pixels
dimensions.ensureValuesAtLeast(
  xSpacing = xSpacing,
  scalableStartPadding = layerPadding.scalableStart.pixels,
  scalableEndPadding = layerPadding.scalableEnd.pixels,
  unscalableStartPadding = layerPadding.unscalableStart.pixels,   // was  maxPointSize.half + …
  unscalableEndPadding = layerPadding.unscalableEnd.pixels,       // was  maxPointSize.half + …
)
```
`maxPointSize` is still computed and still feeds `xSpacing`, so the variable is not dead.

**Related but reverted — do not port:** `14542561e` changed `updateLayerMargins` to
`ensureValuesAtLeast(start = maxMargin, top = maxMargin * 2, end = maxMargin, bottom = maxMargin * 2)`;
`a2a9a7690` reverted it. `updateLayerMargins` at HEAD (L1091–1110) is **identical to FORK_POINT**.

### Public API added or changed
None.

### Coupling to upstream internals
Overrides `updateDimensions` and changes two arguments to upstream's
`MutableCartesianLayerDimensions.ensureValuesAtLeast`. Entirely dependent on that function keeping
its five named parameters. This is also the place `pointSpacingProvider` used to hook (L16).

### Could it be an extension point, or live in meApp instead?
Already is one, almost: `layerPadding` is upstream's caller-supplied padding, and the only thing we
removed is a library-added constant. If v3.3.1 lets a caller set padding to a *negative* value, or
exposes a "don't reserve point size" option, this patch disappears. Otherwise it is a one-concept,
two-line patch and is the cheapest thing in the cluster to re-apply by hand.

### Conflict risk
**Medium.** Two lines, but inside `updateDimensions`, which upstream is likely to have touched
(layer dimensions were reworked in the 3.2/3.3 line per the BASELINE churn numbers). Easy to
re-apply once located.

---

## L10 — `getVisibleXRange()` made public

### What it does
Widens one extension function's visibility from `internal` to `public`, and gives it an explicit
return type (required for a public declaration).

### Why we needed it
`yTransform` receives the visible x window, and the layer obtains it by calling
`getVisibleXRange()`. The call site is inside the library, so `internal` would have sufficed — the
widening exists because the *signature* `(series, yRange, visibleXRange) -> DoubleArray?` hands a
`ClosedFloatingPointRange<Double>` to meApp, and `037db5da5`'s author widened the producer too.

### Commits
- `037db5da5` feat(yTransform): Add render-time Y transform to LineCartesianLayer

### Files and hunks
`cartesian/CartesianDrawingContext.kt` L55–56:
```kotlin
public fun CartesianDrawingContext.getVisibleXRange(): ClosedFloatingPointRange<Double> =
  getVisibleXRange(layerDimensions, layerBounds, scroll)
```
(was `internal fun … =`, inferred return type).

### Public API added or changed
```kotlin
public fun CartesianDrawingContext.getVisibleXRange(): ClosedFloatingPointRange<Double>
```
It is now part of the published ABI of `com.dmdbrands.lib:compose-android`. meApp does **not** call
it (verified by grep over `app/src`).

### Coupling to upstream internals
It delegates to `internal fun CartesianMeasuringContext.getVisibleXRange(layerDimensions,
layerBounds, scroll)` (`cartesian/CartesianMeasuringContext.kt:118`), which stays internal — so the
public function exposes an internal computation but not an internal type.

### Could it be an extension point, or live in meApp instead?
**Revert it to `internal`.** Nothing outside the library calls it, the four library call sites
(`LineCartesianLayer`, `ColumnCartesianLayer:146`, `CandlestickCartesianLayer:142`,
`HorizontalAxis:204,336`) are all internal, and `CartesianDrawingContext.kt` is listed in BASELINE
as **upstream-untouched** — so keeping it `internal` makes the file graft with zero changes. This is
free fork-surface reduction.

### Conflict risk
**Low** — and reducible to zero by reverting, since upstream never touched the file.

---

## L11 — `isReady: StateFlow<Boolean>` on the model producer

### What it does
Adds a latch to `CartesianChartModelProducer` that flips `false → true` the first time a transaction
produces a non-null model, and never flips back.

### Why we needed it
MOB-3494's loading behaviour. The dashboard shows a shimmer skeleton over the chart card until the
chart genuinely has data. Nothing else in the pipeline knows that moment: the view model knows when
it *pushed*, not when the chart has a model, and the chart composable renders regardless.
`producer.isReady` is the only honest signal.

### Commits
- `58b7a5647` MA-3797: Add isReady: StateFlow<Boolean> to CartesianChartModelProducer (v4.0.34)
- `462e698dc` MOB-3494: say that isReady is a latch *(documentation only — states the never-resets contract)*

### Files and hunks
`cartesian/data/CartesianChartModelProducer.kt`:
- L25–27 — three `kotlinx.coroutines.flow` imports added *inside* upstream's existing
  `kotlinx.coroutines.*` / `sync` import block, i.e. out of alphabetical order. Expect a trivial
  import conflict.
- L39 — `private val _isReady = MutableStateFlow(false)`
- L41–50 — KDoc stating the latch contract explicitly.
- L50 — `public val isReady: StateFlow<Boolean> = _isReady.asStateFlow()`
- L81–84 — the only write, inside `getModel`'s `.also { model -> … }`:
  ```kotlin
  if (model != null && !_isReady.value) _isReady.value = true
  ```

### Public API added or changed
```kotlin
public val CartesianChartModelProducer.isReady: StateFlow<Boolean>
```

### Coupling to upstream internals
- The write sits inside `private fun getModel(partials, extraStore)`, hooked onto upstream's
  existing `.also { … }` that populates `cachedModel`/`cachedModelPartialHashCode`. If upstream
  restructures model caching, the hook moves.
- **Behavioural edge worth recording:** `getModel` is only called when
  `partials.hashCode() != cachedModelPartialHashCode` (L219–224), and it returns `null` for an
  empty partials list. So an **empty** transaction never sets `isReady`. meApp's
  `pushEmptyProducer` (`BaseDashboardViewModel.kt:264–270`) issues exactly such a transaction, which
  means an empty chart's skeleton is not dismissed by `isReady`; meApp dismisses it separately via
  `isEmptyGraph`. I believe this is intentional but it is not written down anywhere — treat as
  **uncertain**, and preserve the behaviour rather than "fixing" it during the port.

### Could it be an extension point, or live in meApp instead?
It genuinely needs producer-internal knowledge, so it cannot move to meApp as-is — but it is one
field and one line, with no coupling to the draw pass. Check whether v3.3.1 already exposes a model
flow (upstream was moving `CartesianChartModelProducer` towards flows); if it does, `isReady`
becomes a derived flow in meApp and the patch disappears.

meApp consumers: `features/dashboard/components/chart/GraphPagerView.kt:162` and
`features/dashboard/DashboardScreen.kt:398`, both `producer.isReady.collectAsStateWithLifecycle()`.

### Conflict risk
**Low.** Upstream churn on this file is small (13+/6-), and the hunks are additive. Only the import
block is likely to collide.

---

## L12 — `animate` on `runTransaction` (instant-apply path)

### What it does
Adds an `animate: Boolean = true` parameter threaded from `runTransaction` through `Transaction`,
`update`, and `UpdateReceiver.handleUpdate`. When `false`, the receiver skips
`startAnimation(...)` and instead applies the model immediately: `prepareForTransformation`,
`transform(hostExtraStore, 1f)`, `onUpdate(...)`. It also makes the *registration* path
non-animating (`animate = false` at L127), which upstream animated.

### Why we needed it
meApp never wants vico's diff animation. Every chart push is a whole new window of data (segment
change, metric switch, pull-to-refresh), and interpolating from the previous window's points draws a
line that was never real — points sliding across the plot between two unrelated date ranges. Every
single meApp call site passes `animate = false`.

### Commits
- `70b7372c5` feat(animate): Add animate param to runTransaction

### Files and hunks
`cartesian/data/CartesianChartModelProducer.kt`:
- L52–56 — `private suspend fun update(..., animate: Boolean = true)`
- L66–68 — forwards `animate = animate` to each receiver's `handleUpdate`.
- L124–127 — `registerForUpdates` now calls `receiver.handleUpdate(..., animate = false)`.
- L167–172 — the public signature change:
  ```kotlin
  public suspend fun runTransaction(animate: Boolean = true, block: Transaction.() -> Unit) {
    withContext(getDispatcher()) { Transaction(animate).also(block).commit() }
  }
  ```
  Note this hunk *also* swaps `Dispatchers.Default` for `getDispatcher()` — that half is L13.
- L176 — `public inner class Transaction internal constructor(private val animate: Boolean = true)`
- L196–198 — `commit()` forwards `animate`.
- L212–236 — `handleUpdate(..., animate: Boolean = true)` and the branch:
  ```kotlin
  if (animate) {
    prepareForTransformation(model, hostExtraStore, ranges)
    startAnimation { key, fraction -> transform(key, fraction, model, ranges) }
  } else {
    prepareForTransformation(model, hostExtraStore, ranges)
    transform(hostExtraStore, 1f)
    onUpdate(model, ranges, hostExtraStore.copy())
  }
  ```

### Public API added or changed
```kotlin
// signature changed — `animate` is a LEADING parameter, before the trailing lambda
public suspend fun runTransaction(animate: Boolean = true, block: Transaction.() -> Unit)

// inner class constructor (internal, but its arity changed)
public inner class Transaction internal constructor(private val animate: Boolean = true)
```
Source-compatible for `runTransaction { … }` callers; **binary-incompatible**.

### Coupling to upstream internals
- Reaches into `UpdateReceiver`, upstream's private orchestration class, and replicates by hand what
  the animation path does at `fraction = 1f`. The non-animated branch calls the receiver's
  `transform` and `onUpdate` callbacks directly — which are the host's
  (`CartesianChartHost`) lambdas. If upstream changes the contract of those callbacks (e.g. requires
  `startAnimation` to have been called, or expects `onUpdate` only from the frame clock), this
  breaks silently.
- The `registerForUpdates` change alters behaviour for *all* consumers, not just opted-in ones: a
  chart re-registering (configuration change, recomposition into a new host) no longer animates.
- Note the duplicated `prepareForTransformation(model, hostExtraStore, ranges)` in both branches —
  it could be hoisted; it is written twice today.

### Could it be an extension point, or live in meApp instead?
There is a cheaper framing. Upstream already has a `CartesianLayerDrawingModelInterpolator`
abstraction, and meApp could pass an interpolator that always returns the target. That would remove
the `animate` plumbing entirely at the cost of per-layer configuration instead of per-transaction.
Whether it also suppresses the host-side animation loop (and the wasted frames) is unverified —
**flagging as a promising but unconfirmed de-fork.** The `registerForUpdates` half should be ported
on its own merits and called out explicitly, since it is a global behaviour change hiding inside a
feature flag.

meApp consumers (all `animate = false`): `WeightDashboardViewModel.kt:241`,
`BabyDashboardViewModel.kt:218`, `BaseDashboardViewModel.kt:240` and `:268`,
`DashboardSnapshotViewModel.kt:345, 414, 479`.

### Conflict risk
**Medium.** Upstream churn on the file is small, but our change threads one parameter through five
call sites including a private inner class, and it is entangled in the same hunk as L13.

---

## L13 — `InlineUpdateContext` (ex-`PreviewContext`), public, used by `runTransaction`

### What it does
Renames upstream's `PreviewContext` / `PreviewContextKey` to `InlineUpdateContext` /
`InlineUpdateContextKey`, makes the element `public`, and — the behavioural part — makes
`runTransaction` use `getDispatcher()` instead of a hard-coded `Dispatchers.Default`, so a caller
that puts the element in its context gets the update committed on the calling coroutine.

### Why we needed it
Test determinism. `Dispatchers.Default` is a real dispatcher that a `kotlinx-coroutines-test`
virtual-time scheduler cannot drain, so `advanceUntilIdle()` returned while a chart update was still
in flight and whether the assertion saw the new model depended on machine speed. Per the commit
message, ~20 dashboard view-model tests sit behind this, one of which had been quarantined as flaky
for weeks. Previews get the same benefit (they have no frame clock to await) — that was upstream's
original, narrower use.

### Commits
- `17b134500` MOB-3494: let a data update run on the caller, and say why it would

### Files and hunks
- `cartesian/data/CartesianChartModelProducer.kt` L170–172 — `withContext(getDispatcher())`
  replacing `withContext(Dispatchers.Default)` inside `runTransaction`.
- `cartesian/data/CartesianChartModelProducer.kt` L234–240 — `getDispatcher()` now keys on
  `InlineUpdateContextKey`.
- `cartesian/data/CartesianChartModelProducer.kt` L243 —
  `internal object InlineUpdateContextKey : CoroutineContext.Key<InlineUpdateContext>`
- `cartesian/data/CartesianChartModelProducer.kt` L245–253 — KDoc + the now-public element.
- `cartesian/data/CartesianChartModel.kt` L236 — the single rename call site:
  ```kotlin
  if (isPreview) Dispatchers.Unconfined + InlineUpdateContext else EmptyCoroutineContext
  ```
  This is the **entire** diff on `CartesianChartModel.kt` (1+/1-).

### Public API added or changed
```kotlin
public object InlineUpdateContext : AbstractCoroutineContextElement(InlineUpdateContextKey)
internal object InlineUpdateContextKey : CoroutineContext.Key<InlineUpdateContext>
// (upstream names PreviewContext / PreviewContextKey are gone)
```

### Coupling to upstream internals
- Renames two upstream `internal` declarations. The rename itself is free; the *promotion to public*
  is the fork surface.
- Changes the dispatcher used by the public `runTransaction` entry point.
- Depends on `getDispatcher()` existing and on the preview path in `CartesianChartModel.kt` still
  building its coroutine context the same way.

### Could it be an extension point, or live in meApp instead?
The need — "commit on my coroutine so my test scheduler can drain it" — is generic and a reasonable
upstream request. Until then, the minimum fork is: keep upstream's `PreviewContext` name (so
`CartesianChartModel.kt` reverts to a clean graft and `CartesianChartModel.kt` leaves our modified
file list entirely), add `public object InlineUpdateContext` as a *second* element, and make
`runTransaction` call `getDispatcher()`. That turns a 2-file rename into a 1-file addition.
**This is a concrete, cheap fork reduction** — note that `CartesianChartModel.kt` is hot upstream
(43+/39-) and our only change to it is this rename, so reverting the rename deletes a whole file
from the conflict surface.

meApp consumers: `BaseDashboardViewModel.kt:25, 256, 265` (`chartUpdateContext`, `@VisibleForTesting`)
and `test/.../testutil/ViewModelTestExtensions.kt:10, 53`.

### Conflict risk
**Low** for the producer hunks. **Low** for `CartesianChartModel.kt` *mechanically* (one line) but
it sits in a file upstream rewrote heavily, so the 1-line change still has to be re-found by hand.

---

## L14 — `Defaults` / `Animation` constants

### What it does
Two unrelated constant edits in one file: adds a shared `RANGE_ANIM_DURATION = 300` to
`internal object Animation`, and halves `AXIS_LABEL_VERTICAL_PADDING` from 4 to 2 in
`internal object Defaults`.

### Why we needed it
- `RANGE_ANIM_DURATION` — the scroll-aware y-range animation and the `yTransform` fade have to agree
  on how long a settle takes; one constant stops them drifting apart.
- `AXIS_LABEL_VERTICAL_PADDING: 4 → 2` — v3/Figma parity for axis label height. Pure visual
  calibration against the designs.

### Commits
- `037db5da5` feat(yTransform): Add render-time Y transform … *(adds `RANGE_ANIM_DURATION`)*
- `1a08d48a6` feat: Add label positioning params to HorizontalAxis (v3 parity) *(the 4 → 2)*

### Files and hunks
`common/Defaults.kt`:
```kotlin
internal object Animation {
  …
  /** Duration for Y-range and yTransform animations (shared so they stay in sync). */
  const val RANGE_ANIM_DURATION: Int = 300          // L25–26, added
}

internal object Defaults {
  …
  const val AXIS_LABEL_VERTICAL_PADDING: Int = 2    // L32, was 4
}
```

### Public API added or changed
None — both objects are `internal`.

### Coupling to upstream internals
- `RANGE_ANIM_DURATION` has exactly one reader:
  `cartesian/data/ScrollAwareRangeProvider.kt:45` (`animDurationMs`). Despite the KDoc, the
  `yTransform` fade does **not** read it — it uses its own hard-coded `100.milliseconds` /
  `150.milliseconds` (`LineCartesianLayer.kt` L144–145). **The comment and the code disagree;
  trust the code.**
- `AXIS_LABEL_VERTICAL_PADDING` has one reader, `cartesian/axis/AxisComponents.kt:40`. Note
  `vico/views/.../common/Defaults.kt:29` is *already* 2 upstream, so our compose-side change makes
  the two modules agree — but `vico/views` is gone at v3.3.1, so that rationale expires.

### Could it be an extension point, or live in meApp instead?
`AXIS_LABEL_VERTICAL_PADDING` is a default, and defaults exist to be overridden: meApp constructs
its own axis label `TextComponent`s (`startAxis()`, `endAxis()`, `topAxis()` in the chart package)
and can set `padding` explicitly, deleting this patch. **Recommended** — it is a one-line patch in a
file upstream touched (4+/3-) buying something meApp can set itself.
`RANGE_ANIM_DURATION` should move next to its only consumer (`ScrollAwareRangeProvider`) or be a
constructor default there, which removes `Defaults.kt` from the conflict surface entirely.

### Conflict risk
**Low**, but note this file is the cheapest thing in the cluster to remove from the fork — see above.

---

## L15 — Whole-file reformat of `LineCartesianLayer.kt`

### What it does
Not a feature. `a2a9a7690` ran a different formatter over the file: it expanded four star imports
(`androidx.compose.ui.graphics.*`, `…compose.cartesian.data.*`, `…compose.common.*`,
`kotlin.math.*`) into ~30 explicit imports, and changed continuation indent from 4 spaces to 2
throughout — function signatures, generic parameter lists, `equals` chains, trailing commas.

### Why we needed it
It did not serve a product behaviour; it is formatter drift. It is listed here because it is a
first-class *migration* hazard: roughly half of our 320+/54- on this file is whitespace and imports,
and every one of those lines will conflict against upstream's 130+/35-.

### Commits
- `a2a9a7690` fix(axis): Size.Scroll works without scrollEnabled gate *(the reformat rode along with
  an axis fix; it also reverted `14542561e`'s layer-margin change — see L9)*

### Files and hunks
Throughout `LineCartesianLayer.kt`. Representative:
- L22–30 imports; L38–49 imports; L51–70 imports.
- L51 — `import …LineCartesianLayer.Interpolator.Companion.catmullRom` is **unused** (the only
  `catmullRom` references at L467 and L473 are a declaration and a KDoc link). Dead import; drop it.
- Continuation-indent changes at L106–110, L585–590, L795–810, L1013–1020, L1148–1153, L1188–1193,
  L1225–1235 and others.

### Public API added or changed
None.

### Coupling to upstream internals
None semantically. Total coupling textually.

### Could it be an extension point, or live in meApp instead?
N/A. **The migration tactic matters more than the item:** before attempting any merge on this file,
re-apply upstream's formatting to our HEAD version (or diff with `-w` / `--ignore-all-space` and a
star-import normalisation) so the real 8 adaptations are visible. Doing the port against the raw
diff will bury L3–L9 in whitespace.

### Conflict risk
**High (noise).** It will not break anything; it will make everything else harder to see.

---

## L16 — `pointSpacingProvider` — added, then withdrawn

### What it does at HEAD
**Nothing. It does not exist.** `grep -rn pointSpacingProvider vico` returns no matches at HEAD.

### Chronology
- `f9b44435f` Feature-7 added `pointSpacingProvider: ((availableWidth: Float) -> Float)?` to the
  protected constructor, the public constructor, `copy()`, `rememberLineCartesianLayer` (including
  its `remember` key list), and branched `updateDimensions`:
  ```kotlin
  val xSpacing = if (pointSpacingProvider != null) {
    maxPointSize + pointSpacingProvider.invoke(canvasSize.width)
  } else {
    maxPointSize + pointSpacing.pixels
  }
  ```
- `57274083d` fix: NaN crash guard, remove pointSpacingProvider, scrub LazyColumn fix — removed all
  of it. `updateDimensions` returned to upstream's `val xSpacing = maxPointSize + pointSpacing.pixels`.

### Replacement
`visibleLabelsCount: Double` on `CartesianChart` — a chart-level number evaluated once in
`prepare()`, which scales all layer dimensions. That lives in `cartesian/CartesianChart.kt`,
**not in this cluster**; S1's chart/host dossier owns it. meApp sets it per segment
(WEEK 7.0, MONTH 5.0, YEAR 12.0, TOTAL 0.0 = auto).

### Doc accuracy
`docs/FEATURE-7-POINT-SPACING-PROVIDER.md` is correct about the outcome — its title and first
section already say "visibleLabelsCount (replaced pointSpacingProvider)". Nothing to correct;
**nothing to migrate from this cluster.**

### Conflict risk
**None.** There is no code.

---

## Where the docs disagree with the code

1. `docs/FEATURE-3-MONOTONE-INTERPOLATOR.md` says the interpolator is "Singleton (`object`)" and
   describes only `interpolate`/`getYRange`. It does not mention `getYAtXFromEntries` or
   `InterpolationType`, which arrived later (`7baf04e8b`) and have two library call sites. The doc's
   "Upstream Merge Guide — only 1 line added to `LineCartesianLayer.kt`" is **true for L1 and
   misleading as a summary of the file**, which now carries L3–L9 as well.
2. `docs/FEATURE-3` claims `O(n), 2 FloatArrays` — correct, but it does not say those two arrays are
   allocated **per series per frame** over the full series, not the visible slice.
3. `docs/FEATURE-7-POINT-SPACING-PROVIDER.md` is accurate (see L16).
4. In the code itself: `Defaults.kt`'s KDoc says `RANGE_ANIM_DURATION` is shared by "Y-range and
   yTransform animations". The `yTransform` fade uses its own hard-coded 100 ms / 150 ms. Trust the
   code.
5. `LineCartesianLayer.kt` L124–127's comment says the transform is "Not during scroll. Not during
   yRange animation frames." That is true of the *recompute*; the surrounding block (hashing the
   series, building the keys, running the fade state machine) does run every frame.
6. `LineCartesianLayer.kt` L833–834 still carries the stale comment "yTransform triggered ONLY on
   yRange change … fractions are stable until range updates" from the `cbfc2a365` era, before window
   settles were a trigger and before the fractions approach was replaced. Dead comment.
7. `CartesianChart.kt:548` refers to a "`MonotoneInterpolator.getYAtX` overload" — that overload was
   deleted by `57ce985ea`; the live function is `getYAtXFromEntries`.

---

# yTransform: the full story

## Commit by commit

### 1. `037db5da5` — feat(yTransform): Add render-time Y transform to LineCartesianLayer
*(3 files, +60/-6)*

**Problem:** the weight dashboard needed a second metric, in a different unit, plotted inside the
primary metric's y axis. The scale depends on the live window and the live axis, so it cannot be
computed before the draw.

**Shape:** a nullable lambda on the protected constructor,
`(series, yRange, visibleXRange) -> DoubleArray?`. Cached by `series.hashCode()` **xor the
`yRange` bits**; a `transformIndexMap: Map<Double, Int>` gives `entry.x → index` so `getDrawY` can
look the transformed value up. Both `getDrawY` paths changed. `getVisibleXRange()` widened to public
(L10). `Animation.RANGE_ANIM_DURATION` added (L14).

**Cost:** the transform re-ran whenever the animated `yRange` moved — i.e. every frame of the 300 ms
range animation — and each re-run allocated a fresh `DoubleArray` *and* a fresh `HashMap`.

### 2. `cbfc2a365` — feat: yTransform screen-fraction animation with per-frame caching
*(1 file, +127/-19)*

**Problem:** the secondary line *snapped* to its new scale while the primary line animated, because
the transform produced a fully-corrected array instantly while the axis was still moving.

**Shape:** re-key the cache on `series + visibleXRange` (not `yRange`), so the 300 ms animation does
not re-invoke the transform. Detect the change in **screen space** — the insight being that `yRange`
cancels out of `normalizeYValues → getDrawY`, so the screen fraction is the stable quantity — hold
"current" and "target" fraction arrays, and tween between them with `FastOutSlowIn` to match the
primary's `tween()` default. Both `getDrawY` paths read the same `transformCurrentFractions`.

**Cost:** this is the version that allocated most — per-frame fraction arrays, interpolated every
draw. It is the one the commit title calls "per-frame caching", which refers to caching the
*transform call*, not the arrays.

### 3. `a77154f85` — feat: alwaysUseLiveRange + buildCache on model change + animate on metric switch
*(3 files, +24/-26)*

**Problem:** layers that merely *follow* the primary's scroll-aware range (via a delegating wrapper)
were not detected by `rangeProvider is ScrollAwareRangeProvider`, so they kept using the cached
drawing model and fought the live range. Also, switching metric kept stale y values because
`buildCache` only re-ran on structure change.

**Shape:** adds `alwaysUseLiveRange` (→ L5) and widens the drawing-model bypass to
`rangeProvider is ScrollAwareRangeProvider || alwaysUseLiveRange`. The rest of the commit is in
`CartesianChartHost` (another cluster).

### 4. `0a35ee586` — feat: yTransform target-based caching + fade correction (v4.0.26)
*(5 files, +133/-122 — a near-rewrite of the block)*

**Problem:** the screen-fraction tween of step 2 was elaborate, allocated per frame, and still
produced a visible correction when it landed.

**Shape — the current architecture arrives here:**
- Cache keyed on the **animation target** `yRange`, obtained from a new
  `CartesianChartRanges.getTargetYRange(axisPosition)` with a default of `getYRange`. Implemented
  for real by `AnimatedYCartesianChartRanges` in `CartesianChartHost.kt:721`, which now carries
  `targetMinY`/`targetMaxY` alongside the animating values. Result: **zero** transform calls during
  scroll and during the range animation.
- The per-frame fraction arrays are deleted. In their place: a 100 ms fade-out of the old array, a
  swap, then a 150 ms fade-in of the new one, driven by a 3-state machine
  (`transformFadePhase` 0/1/2) and `TimeSource.Monotonic`. The fade multiplies into
  `saveLayer(opacity = …)`.
- `copy()` gains a body that hand-carries the cache fields, so layer recreation does not snap.
- KMP-safe timing (`kotlin.time`, not `System.nanoTime`).

### 5. `0fc9c7bf3` — MOB-3494: recompute a y transform when its window moves
*(2 files, +148/-3, including a new test)*

**Problem:** the transform reads three inputs — series, range, window — and the cache was keyed on
two. A window that moved without changing either returned the previous window's scaling: a kink in
the line that nothing invalidated, which then persisted until some unrelated change happened to move
one of the other keys. That is why it looked intermittent, and why it was reachable by scrolling
alone with no segment switch.

**Shape:** add a third key, `transformLastWindowKey: Long?` (nullable, because `0` is a legitimate
key — an empty series, or a window holding no entries). Key on **which entries the window admits**,
not on the window's bounds: a new `internal fun visibleEntryKey(series, visibleXRange): Long`
binary-searched both ends and returned `firstIndex * 31 + lastIndex`, so a scroll that moves the
window without crossing a point does not rescale. Covered by a new `VisibleEntryKeyTest.kt` (93
lines).

**Defect introduced here, fixed in step 7:** a window change took the *snap* branch
(`if (transformCacheResult == null || seriesChanged || windowChanged)`) and computed against the
**current** `yRange`, not the target.

### 6. `f436d60cd` — MOB-3494: keep a transformed layer out of the shared y range
*(3 files, +115/-2)*

See **L4**. Not about the transform's caching, but about the transform's *consequence*: a layer
whose drawn values come from the transform must not union its raw values into the axis.

### 7. `e6c766cb8` — MOB-3494: keep the axis and a transformed line in step
*(5 files, +109/-153 — net deletion)*

**Three problems, one of them the transform's.** The transform one: recomputing only on a y-range
change meant scrolling between two windows that *round to the same axis* left the line scaled for
the window the user had left. Step 5's entry-index key was the right instinct but the wrong owner —
it made the layer re-derive, every frame, something the range provider already knows.

**Shape — the final form:**
- `visibleEntryKey` and its test are **deleted** (93 lines of test gone, no replacement).
- A new public interface `WindowSettleSignal { val settleEpoch: Int }`. The provider counts settles;
  the layer reads `(rangeProvider as? WindowSettleSignal)?.settleEpoch?.toLong() ?: 0L`.
  `ScrollAwareRangeProvider` implements it; `CartesianChartHost.kt:654` increments it *before* the
  no-movement early return, precisely so a settle that leaves the range unchanged still fires.
- A settle now takes **the same path a target change does** — one `if` covering both
  (`targetKey != transformLastTargetKey || windowChanged`) — computed against `targetYRange`, with
  the same fade, and **skipped entirely when `pending.contentEquals(transformCacheResult)`**, so a
  scroll that stays inside one scale does not flicker.
- The snap branch is narrowed back to `transformCacheResult == null || seriesChanged`, and it too
  now uses `targetYRange`. The commit message states the reason explicitly: computing against the
  range the chart is *leaving* strands the line outside the plot as the axis animates out from
  under it — which step 5's version did.

## Final shape

Per draw of a series on a layer with a non-null `yTransform`:

1. `seriesKey = series.hashCode().toLong()` — **O(n) over the whole series, every frame.**
2. `targetKey` from `ranges.getTargetYRange(verticalAxisPosition)` bits.
3. `windowKey` from `(rangeProvider as? WindowSettleSignal)?.settleEpoch ?: 0`.
4. **Snap** (no fade) if `transformCacheResult == null || seriesChanged`: invoke the transform
   against `targetYRange`, rebuild `transformIndexMap`, reset the fade.
5. **Fade** if `(targetKey changed || windowChanged) && fadePhase == 0`: invoke the transform into
   `transformPendingResult`; start the fade only if the result is non-null and differs by
   `contentEquals`.
6. Run the fade state machine against wall-clock elapsed time; at the end of phase 1 the pending
   array/map become current.
7. `getDrawY` reads `transformIndexMap[entry.x]` and substitutes `transformedY[idx]` for `entry.y`.
   `forEachPointInBounds.getDrawY` does the same against `transformCacheResult` directly.
8. `drawInternal` multiplies `transformFadeOpacity` into `saveLayer`'s opacity.

Invariants worth preserving verbatim in any port:
- **Always compute against `getTargetYRange`, never the animating `getYRange`.**
- **Increment `settleEpoch` before any no-movement early return.**
- **Skip the fade when the new result equals the old** (`contentEquals`), or every in-scale scroll
  flickers.
- **`transformLastWindowKey` must be nullable**; `0` is a real key.
- **`copy()` must carry the cache**, or every layer recreation snaps the line.

## Performance characteristics

| version | transform invocations | per-frame allocation | notes |
|---|---|---|---|
| `037db5da5` | every frame of a 300 ms range animation | `DoubleArray(n)` + `HashMap(n)` per invocation | worst |
| `cbfc2a365` | once per visible-x-range change | fraction arrays interpolated **every frame** | smoothest, heaviest |
| `0a35ee586` | once per animation target | none steady-state | current architecture |
| `0fc9c7bf3` | + once per point crossed while scrolling | binary search per frame (O(log n), no alloc) | key computed per frame |
| `e6c766cb8` (HEAD) | once per target change, once per settle | none steady-state | key is an `Int` read |

**At HEAD, steady state (no settle, no target change, no data change):**
- Allocation: **zero** from the transform block itself.
- Per frame, per series: one `List.hashCode()` — **O(n) over the entire series**, which for a
  TOTAL-segment chart is every reading the user has ever recorded. Plus two `Double.toBits()`, one
  `as?` test, and a `when` over `transformFadePhase`.
- `getDrawY` does one `Map<Double, Int>` lookup per visible point (boxed `Double` key).

**On a recompute** (settle or target change): one `yTransform` invocation (meApp's
`normalizeYValues` is O(n), one `DoubleArray(n)`), plus `series.withIndex().associate { … }` —
one `LinkedHashMap` with `n` boxed `Double` keys and `n` `IndexedValue` wrappers.

**During a fade** (250 ms): the state machine runs per frame, reading `elapsedNow()`. It has **no
invalidation of its own** — it advances only if something else is redrawing. In practice the range
animation is, so this works; a settle that produced no range movement would leave the fade to
whatever else happens to invalidate. I could not confirm from reading alone whether such a case
exists. **Stated as uncertain.**

**The one clear optimisation if the code survives the port:** `series.hashCode()` per frame is the
only O(n) steady-state cost, and it exists to detect a data change the host already knows about.
Replacing it with identity (`System.identityHashCode`, or a model-version counter from the
producer) makes the whole block O(1) per frame.

## What the migration decision needs to weigh

- **Scope:** ~130 lines of stateful, time-driven logic inside the two hottest functions of the
  hottest file in the fork, plus three supporting pieces in other files
  (`getTargetYRange` default method, `WindowSettleSignal`, `getVisibleXRange` visibility).
- **Test coverage:** none. The only test that ever covered it (`VisibleEntryKeyTest.kt`) was deleted
  along with the mechanism it tested. Everything above was reconstructed from commit messages and
  code comments — both unusually good here, and both unverified against running behaviour.
- **Product value:** one feature — the secondary-metric overlay on the weight dashboard — reached
  from exactly one meApp call site (`ProductChart.kt:324`), through
  `PrimaryLayer.kt`'s `secondaryLayer(...)`, computing `GraphUtil.normalizeYValues`.
- **The alternative worth costing first** is option (b) under L3: expose the settled axis to meApp
  and normalise in the view model. If it works it deletes L3, L4 and L5 together, and takes
  `LineCartesianLayer.kt` from "the hardest file in the port" to "L8 + L9, two small hunks".
