# S2-B — Which fork features v3.3.1 already provides

Scope: for each of the fork's 13 custom features, does upstream **v3.3.1** (`3d675ccc7`) provide it
natively, or provide enough of it that ours becomes unnecessary or much smaller?

Method: `git grep`/`git show` against `v3.3.1` and the fork point `e67e49520`; `git log
e67e49520..v3.3.1`; upstream GitHub release notes for v3.2.0–v3.3.1 (`gh release view`). Read-only
throughout — no checkout, no working-tree writes.

Path prefix used below (abbreviated `…/`):
`vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/`

A note on "upstream" vs "new": several extension points named here (`LineCartesianLayer.Interpolator`,
`CartesianMarkerController`, `CartesianLayerPadding`, `Zoom.x`, `CartesianChart.getMarkerTargets`)
already existed at the **fork point**. They are still reported, because the question asked is "does
v3.3.1 provide this", not "did upstream add this since". Where that matters it is called out — a
pre-existing extension point means our patch may have been avoidable all along.

---

## Verdict table

| # | Fork feature | Verdict | One-line reason |
|---|---|---|---|
| 1 | ScrollAwareRangeProvider (+ host-side ranges, travelling window) | **NOT UPSTREAM** | `CartesianLayerRangeProvider` is still `(minY, maxY, extraStore)`, evaluated per data update; it never sees scroll or the visible window |
| 2 | ScrubMarkerController | **PARTIALLY UPSTREAM** | Controller interface, `Interaction` model, `consumeMoveEvents` and the tap-consumption fix are upstream; the hold-then-drag state machine and scroll freezing on a **scrollable** chart are not |
| 3 | MonotoneInterpolator + `InterpolationType` | **NOT UPSTREAM** | `Interpolator` is a public extension point (and gained `visiblePadding`), but there is no monotone/no-overshoot implementation and no `InterpolationType` |
| 4 | SnapFlingBehavior + `scrollPaddingXStep` | **PARTIALLY UPSTREAM** | `xSnapStep` + `snapAnimationSpec` + an internal `SnapFlingBehavior` give fixed-step snap-after-fling; there is no consumer callback, no drag-vs-fling distinction and no public `flingBehavior` parameter. `scrollPaddingXStep` no longer exists in the fork |
| 5 | Padding / inset work | **PARTIALLY UPSTREAM** | "Start inset as a length" is fully served by the pre-existing `CartesianLayerPadding.unscalableStart`; `Scroll.Absolute.xBehindStartInset` and the FadingEdges-from-`startPadding` derivation are not upstream |
| 6 | Axis enhancements (MarkerDecoration, Separators, label positioning) | **PARTIALLY UPSTREAM** | No `MarkerDecoration`, no `Separators`. `labelVerticalMode`'s three offsets coincide with upstream's pre-existing `tickPosition` offsets; `labelHorizontalPosition` has only the extremes-only `shiftExtremeLabels` nearby |
| 7 | pointSpacingProvider → `visibleLabelsCount` | **PARTIALLY UPSTREAM** | `pointSpacingProvider` is **gone from the fork** (replaced by `visibleLabelsCount`). The nearest upstream equivalent is the pre-existing `Zoom.x(x: Double)`, which fits N x-units to the width but ignores the reserved start inset |
| 8 | Interpolated marker targets (+ `visibleXRange`, `xToScrollValueWithPadding`) | **PARTIALLY UPSTREAM** | No synthesis of off-data-point targets and no `isInterpolated`; but `CartesianChart.getMarkerTargets` is a `public open` override point (signature changed at v3.3.1) and `getVisibleXRange` exists — internal only. `xToScrollValueWithPadding` no longer exists in the fork |
| 9 | `Size.Scroll` / scrollable vertical axis | **PARTIALLY UPSTREAM** | `BaseAxis.Size` has no `Scroll` variant. Of the v3-parity sub-items, only extreme-label clipping has a new upstream answer (`ItemPlacer.aligned(shiftExtremeLabels = true)`) |
| 10 | Chart layout parity | **PARTIALLY UPSTREAM** | The 200 dp height cap is **gone upstream**, replaced by `ChartHostBox` + `chartAreaHeight` — our fix is obsolete. The `CartesianLayerMargins` top/bottom halving is **not** upstream |
| 11 | `yTransform` on LineCartesianLayer | **NOT UPSTREAM** | No render-time Y transform hook anywhere in v3.3.1 |
| 12 | `isReady: StateFlow<Boolean>` + `runTransaction(animate)` | **NOT UPSTREAM** | `CartesianChartModelProducer` has neither. `initialAnimationSpec = null` covers only the first reveal animation, host-wide |
| 13 | Marker `contentPadding`, crosshair, resting label | **NOT UPSTREAM** | `DefaultCartesianMarker` has no `contentPadding`, no crosshair, no `restingLabel`/`yLabelCallback`; `CartesianMarker` has no `drawResting`/`onScrollSettled` |

No feature is **FULLY UPSTREAM**. Nothing here says "delete the behaviour"; the two findings that
most shrink the fork are #10's height cap (obsolete) and #5's start inset (already an upstream
parameter). Both still need a parity check before anything is dropped.

---

## 1. ScrollAwareRangeProvider — NOT UPSTREAM

**Upstream API:** none.

`…/cartesian/data/CartesianLayerRangeProvider.kt` at v3.3.1:

```kotlin
public interface CartesianLayerRangeProvider {
  public fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = minX
  public fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = maxX
  public fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = minY.coerceAtMost(0.0)
  public fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = …
}
```

**What it covers:** nothing of ours. The provider is handed the layer's whole-data extremes and an
`ExtraStore`. It has no scroll value, no bounds, no layer dimensions, no visible x-range.

**What it does not cover:** everything — recomputing Y from the entries currently in view, the
debounce, the animation of the range, the tick list, the initial-frame hide.

**Evidence:**
- The interface above is byte-identical in substance to the fork-point version; `git log
  e67e49520..v3.3.1 -- …/CartesianLayerRangeProvider.kt` shows no behavioural change.
- `git grep -n 'visibleXRange' v3.3.1 -- vico/compose/src/commonMain` → `getVisibleXRange` exists
  only as `internal fun` in `…/cartesian/CartesianMeasuringContext.kt:119` and
  `…/cartesian/CartesianDrawingContext.kt:55`, used by `HorizontalAxis` and by `CartesianChartHost`
  for marker-x validity. It is not public and is not routed to range providers.
- Ranges are still computed once per data update, in `collectAsState`
  (`…/cartesian/data/CartesianChartModel.kt:127-130`: `ranges.reset(); chartState.value.updateRanges(ranges, model); ranges.toImmutable()`)
  — unchanged from the fork point. So the "recompute in the host" evolution has no upstream analogue
  either.
- Nothing in the 3.2.x/3.3.x release notes mentions dynamic or viewport-dependent y-ranges.
- Our `ListItemPlacer` (consumer-supplied tick list) also has no upstream counterpart:
  `VerticalAxis.ItemPlacer` offers only `step()` and `count()`
  (`…/cartesian/axis/VerticalAxis.kt:751,762`).

**Adjacent, do not confuse:** v3.3.0's "visible-x-range preservation across data updates" (commit
`a1e3d0296`) keeps the same **x** on screen when `minX`/`xStep` shift. It is about x, not y, and it
is a scroll-value correction, not a range provider.

**Confidence:** high.

---

## 2. ScrubMarkerController — PARTIALLY UPSTREAM

**Upstream API:** `…/cartesian/marker/CartesianMarkerController.kt` at v3.3.1

```kotlin
public fun interface CartesianMarkerController {
  public val acceptsLongPress: Boolean get() = true
  public val lock: Lock get() = Lock.X              // Lock.X | Lock.Position
  public val consumeMoveEvents: Boolean get() = false
  public fun shouldAcceptInteraction(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean = true
  public fun shouldShowMarker(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean
  public companion object {
    @Composable public fun rememberShowOnPress(consumeMoveEvents: Boolean = false): CartesianMarkerController
    @Composable public fun rememberShowOnHover(): CartesianMarkerController
    @Composable public fun rememberToggleOnTap(): CartesianMarkerController
  }
}
```

Wired in `…/cartesian/CartesianChart.kt:81` (`markerController` property) and consumed in
`…/cartesian/CartesianChartHost.kt:270-290` and `…/cartesian/Modifier.kt:57-150`.

**What it covers**
- The controller extension point itself, `Interaction` (Press/Move/Release/Tap/LongPress/Enter/Exit/Zoom),
  and `Lock.Position` (marker holds its screen position while the viewport moves) — all present, all
  already present at the fork point.
- **Press-and-drag scrubbing on a NON-scrollable chart.** `…/cartesian/Modifier.kt:108-111`:
  ```kotlin
  event.type == PointerEventType.Move -> {
    onInteraction(Interaction.Move(pointerPosition))
    consumeMoveEvents && !scrollState.scrollEnabled
  }
  ```
  With `rememberShowOnPress(consumeMoveEvents = true)` on a chart whose `scrollEnabled` is false,
  moves are consumed, so a parent `LazyColumn` is blocked and the marker tracks the finger. That is
  the fork's `TOTAL`/single-window case.
- **Our `acceptsGestures` / `rememberNonInteractive` addition is now redundant.** v3.2.0 fixed
  "charts with markers could needlessly consume taps and long presses, blocking upstream detection":
  `…/cartesian/Modifier.kt:156-174` only consumes when the callback returns `true`, and that return
  value is `shouldAcceptInteraction`. A controller returning `false` therefore lets the tap through
  to the surrounding card with no library patch at all — the `acceptsGestures` property and the
  patch to `CartesianMarkerController.kt` can be replaced by a ~6-line local implementation of the
  public interface.

**What it does not cover**
- **Scrubbing on a SCROLLABLE chart.** `consumeMoveEvents` is gated on `!scrollState.scrollEnabled`,
  so with scroll on, a drag scrolls the chart instead of scrubbing. There is no upstream equivalent
  of `VicoScrollState.isScrollFrozen` (claim the delta so `nestedScroll` blocks the parent, but do
  not move the position).
- The hold-timer state machine (`NONE → DECIDING → SCROLLING | MARKER_SELECTION → MARKER_SCRUBBING`),
  `TAP_SLOP`/`MOVEMENT_THRESHOLD`, and "marker rests where the gesture ended" — none of this exists;
  upstream's `ShowOnPress` hides the marker on `Release`.
- Dismiss-on-scroll, and the `onMarkerIndexChanged(clickX, allTargetXValues)` consumer callback that
  lets meApp choose the target x. Upstream picks the nearest target itself
  (`CartesianChart.getMarkerTargets`, see #8) and the controller only gets a yes/no on visibility.
- `CartesianChart.allMarkerTargetXValues` — no upstream accessor for the full target-x list.

**Evidence:** full reads of `…/cartesian/Modifier.kt`, `…/cartesian/marker/CartesianMarkerController.kt`
and `…/cartesian/CartesianChartHost.kt` at v3.3.1; `git diff e67e49520..HEAD --
…/marker/CartesianMarkerController.kt` (the fork adds only `acceptsGestures` + `rememberNonInteractive`);
v3.2.0 release note on tap/long-press consumption.

**Confidence:** high for "the gesture machine is not upstream". Medium-high for the claim that
`acceptsGestures` is now redundant — it rests on reading the consumption path in `Modifier.kt`, not
on a run; worth a 10-minute behavioural check (tap a chart inside a clickable card) before deleting.

---

## 3. MonotoneInterpolator + `InterpolationType` — NOT UPSTREAM

**Upstream API:** `LineCartesianLayer.Interpolator` in `…/cartesian/layer/LineCartesianLayer.kt:342-412`

```kotlin
public interface Interpolator {
  public val visiblePadding: Int? get() = 0                       // NEW since fork point (v3.2.0)
  public fun interpolate(context: CartesianDrawingContext, path: Path,
                         points: List<Offset>, visibleIndexRange: IntRange)
  public fun getYRange(y: List<Double>): ClosedRange<Double> = y.min()..y.max()
  public companion object {
    public val Sharp: Interpolator
    public fun cubic(curvature: Float = 0.5f): Interpolator       // CubicInterpolator.kt
    public fun catmullRom(alpha: Float = 0f): Interpolator        // CatmullRomInterpolator.kt
  }
}
```

**What it covers:** the extension point only, and it already existed at the fork point — our
`MonotoneInterpolator` is a new file plus **one line** in the `Interpolator.Companion`
(`public fun monotone(): Interpolator = MonotoneInterpolator`). The one genuinely new thing is
`visiblePadding`, which our implementation must now declare (Fritsch–Carlson needs neighbours, so
`visiblePadding = 1`, as `CatmullRomInterpolator` does — `…/cartesian/layer/CatmullRomInterpolator.kt:29`).

**What it does not cover:** no monotone/no-overshoot interpolator ships at v3.3.1, and there is no
`InterpolationType` selector. The fork's `MonotoneInterpolator.getYAtXFromEntries` (used by FEATURE-8
and by `VicoScrollState.getInterpolatedYValues`) has no upstream analogue either.

**Do not mistake v3.2.3 for this.** Its note reads: the Catmull–Rom `Interpolator` "now clamps each
segment's Bézier control points to the segment's _x_-range, keeping the spline **monotonic in _x_**
for unevenly spaced points". That is monotonicity in x (fixing an inverted area fill), not the
absence of **y** overshoot. A `catmullRom` line still overshoots the data's y bounds.

**Evidence:** `git grep -ni monotone v3.3.1 -- vico/` returns only the v3.2.3 x-monotonicity comments
in `CatmullRom.kt`/`CatmullRomInterpolator.kt`; side-by-side read of the `Interpolator` interface at
`e67e49520` and `v3.3.1`.

**Confidence:** high.

---

## 4. SnapFlingBehavior + `scrollPaddingXStep` — PARTIALLY UPSTREAM

First: **`scrollPaddingXStep` no longer exists in the fork.** `git grep -n scrollPaddingXStep HEAD --
vico/` → no hits. It was folded into the chart-level start inset (see #5). Only the snap behaviour
is still live.

**Upstream API** (added in v3.3.0, commit `01c7571ad`, "Add snap scrolling feature to align scroll
position to grid windows (#1372)"):

```kotlin
// …/cartesian/VicoScrollState.kt:128-146 and :333-353
public constructor(
  scrollEnabled: Boolean = true,
  initialScroll: Scroll.Absolute = Scroll.Absolute.Start,
  autoScroll: Scroll = initialScroll,
  autoScrollCondition: AutoScrollCondition = AutoScrollCondition.Never,
  autoScrollAnimationSpec: AnimationSpec<Float> = spring(),
  xSnapStep: Double? = null,
  snapAnimationSpec: AnimationSpec<Float> = spring(),
)

@Composable public fun rememberVicoScrollState(…, xSnapStep: Double? = null,
  snapAnimationSpec: AnimationSpec<Float> = spring()): VicoScrollState
```

plus `internal fun getSnapTarget(...)` / `getSnapDelta(...)` in `…/cartesian/SnapScroll.kt`, and
`internal class SnapFlingBehavior` in `…/cartesian/Modifier.kt:188-210`, selected automatically:

```kotlin
// …/cartesian/Modifier.kt:65-71
if (scrollState.xSnapStep != null) SnapFlingBehavior(scrollState, decayAnimationSpec)
else defaultFlingBehavior
```

**What it covers**
- Physics-based projection then snap: `decayAnimationSpec.calculateTargetValue(value, velocity)` →
  `getSnapDelta(targetValue)` → `animate(...)`. Same two-phase shape as ours.
- Snap targets at `startPadding + k × windowPx` where `windowPx = (xSnapStep / xStep) × xSpacing`,
  with `0f` and `maxValue` also candidates and the nearest winning
  (`…/cartesian/SnapScroll.kt:45-61`). Our "nearest window boundary, clamped" is the same rule.
- Start-padding awareness — upstream's own test is literally
  `"When xSnapStep is set, then snap delta accounts for layer start padding"`
  (`vico/compose/src/androidHostTest/.../VicoScrollStateTest.kt:105`). This is the part
  `scrollPaddingXStep` existed for.
- Cancellation hygiene: our `try/finally` around `isSnapping` has no upstream analogue because
  upstream has no `isSnapping` flag — the flag exists only to suppress our range provider (#1).

**What it does not cover**
- **No consumer callback.** `SnapBehaviorConfig.snapToLabel(currentXLabel, projectedXLabel, isDrag,
  isForward)` has no upstream equivalent. Upstream snaps to arithmetic multiples of one `Double`.
  meApp's `GraphSnapHelper` logic (different rules per WEEK/MONTH/YEAR segment, and different rules
  for drag vs fling) must collapse to a single `xSnapStep` per segment — expressible for uniform
  windows, **not** expressible for irregular boundaries (calendar months are not a constant x-step).
- **No drag-vs-fling distinction.** Upstream runs the same path for both; with near-zero velocity the
  decay target is the current value, so a slow release snaps to nearest. Close to our `isDrag` branch,
  but not configurable.
- **No public `flingBehavior` parameter** on `CartesianChartHost` or `Modifier.pointerInput`; the
  behaviour is chosen internally from `xSnapStep`. Supplying a custom `FlingBehavior` still requires
  a patch.
- Our `VicoScrollState.visibleXRange`, `isScrolling`, `isUserScrolling`, `xToScrollValue`,
  `scrollValueToX`, `getVisibleAxisLabels`, `getInterpolatedYValues`, and the `key` parameter on
  `rememberVicoScrollState` — none exist upstream.
- Our `update()` change (re-apply `initialScroll` when `maxValue` changes) is **replaced, not kept**,
  by upstream's own repositioning logic at `…/cartesian/VicoScrollState.kt:160-200` (commit
  `a1e3d0296`, "Preserve the visible x range across data updates"), which recomputes `value` from a
  remembered `ScrollLayout` so the same x stays on screen. Different mechanism, overlapping purpose —
  treat ours as superseded only after a parity check on the "stale `initializeImmediateData` model"
  case it was written for.

**Confidence:** high on the API facts; high that the callback is not expressible upstream for
irregular (calendar) boundaries.

---

## 5. Padding / inset work — PARTIALLY UPSTREAM

The fork has moved on since FEATURE-5 was written. Current state at `HEAD`:
`xWithPadding` → `Scroll.Absolute.xBehindStartInset(x, bias)`; `startPaddingXStep` on
`rememberFadingEdges` → gone; `startInsetDp` on `CartesianChart`.

**(a) Start inset given as a length — FULLY available upstream, via a pre-existing API.**

```kotlin
// …/cartesian/layer/CartesianLayerPadding.kt:32-37  (identical at the fork point)
public class CartesianLayerPadding(
  public val scalableStart: Dp = 0f.dp,
  public val scalableEnd: Dp = 0f.dp,
  public val unscalableStart: Dp = 0f.dp,
  public val unscalableEnd: Dp = 0f.dp,
)
```
applied by every layer, e.g. `…/cartesian/layer/LineCartesianLayer.kt:901`:
`unscalableStartPadding = maxPointSize.half + layerPadding.unscalableStart.pixels`, and reaching
`CartesianLayerDimensions.startPadding` (`…/cartesian/layer/CartesianLayerDimensions.kt:53`) and
`getFullXRange`. That is exactly what the fork's `startInsetDp` does by calling
`layerDimensions.ensureValuesAtLeast(unscalableStartPadding = startInsetDp.pixels)` in
`CartesianChart.prepare` — one slot, one effect, reached two ways.
*Caveat before dropping:* the fork applies it in `prepare()` after axis measurement, upstream applies
it inside each layer's `updateLayerDimensions`; and the fork's `visibleLabelsCount` maths subtracts
`startInsetPx` from the available width (see #7). Both need re-checking against `layerPadding`.

**(b) `Scroll.Absolute.xBehindStartInset` — NOT upstream.** Upstream has
```kotlin
// …/cartesian/Scroll.kt:51-58
public fun x(x: Double, bias: Float = 0f): Absolute =
  Absolute { context, layerDimensions, bounds, _ ->
    layerDimensions.startPadding +
      ((x - context.ranges.minX) / context.ranges.xStep).toFloat() * layerDimensions.xSpacing -
      bias * bounds.width
  }
```
Ours is the same expression **minus `layerDimensions.startPadding`** — it puts `x` behind the
reserved inset rather than flush at the window edge. A genuine, if small, behavioural difference that
upstream does not offer.

**(c) FadingEdges derived from `startPadding` — NOT upstream.** `…/cartesian/FadingEdges.kt` is
byte-identical between `e67e49520` and `v3.3.1` (`git diff e67e49520..v3.3.1 -- …/FadingEdges.kt`
returns nothing). Upstream uses `startWidth`/`endWidth` as given; the fork uses
`maxOf(startWidth.pixels, layerDimensions.startPadding)`.
Note for the migration (not a verdict): since the inset is a `Dp` the consumer already chooses, the
consumer could pass the same `Dp` as `startWidth` and get the same visual result without the patch —
but only if nothing else contributes to `startPadding`. `LineCartesianLayer` adds `maxPointSize.half`
to it (line 901), so the two are **not** equal in general. Verify before dropping.

**Confidence:** high on (a) and (b); high on (c)'s "not upstream", medium on the suggestion that the
consumer can emulate it.

---

## 6. Axis enhancements — PARTIALLY UPSTREAM

**(a) `VerticalAxis.MarkerDecoration` — NOT upstream.** `git grep -n 'decoration' v3.3.1 --
…/cartesian/axis/` → no hits; `…/cartesian/decoration/` contains only `Decoration.kt`,
`HorizontalLine.kt`, `HorizontalBox.kt`, all pre-existing and all drawn across the **plot area**, not
on the axis, with no out-of-range edge-pinning (`outsideRangeOffset`). `HorizontalLine` is the
nearest primitive; it is not a substitute for a pill label parked on the axis.

**(b) `HorizontalAxis.Separators` — NOT upstream.** No vertical-line decoration exists at all;
`git grep -n 'separator' v3.3.1 -- vico/` → no hits in library source.

**(c) `labelVerticalMode` — PARTIAL, through a pre-existing neighbour.** Upstream
`…/cartesian/axis/HorizontalAxis.kt:157-168` computes the label's y from `tickPosition`:

```kotlin
val outwardTickLength = when (tickPosition) {
  TickPosition.Outside -> this.tickLength
  TickPosition.Inside  -> 0f
  TickPosition.Cross   -> this.tickLength / 2
}
val textY = if (isTop) bounds.bottom - lineThickness - outwardTickLength
            else        bounds.top    + lineThickness + outwardTickLength
```

The three offsets `0 / tickLength÷2 / tickLength` are **exactly** our `Inside / Center / Outside`.
`TickPosition` already existed at the fork point (`…/cartesian/axis/BaseAxis.kt:59,124` at
`e67e49520`). The difference: `tickPosition` moves the **tick** as well as the label, and the fork
subdivides again (`tickOffset = outwardTickLength / 2` for `Center`), so the fork's `Center` on an
`Outside` tick is `tickLength/2` — reachable upstream only as `TickPosition.Cross`, which also moves
the tick. So: expressible upstream **iff** the tick is allowed to move with the label.

**(d) `labelHorizontalPosition` — NOT upstream.** Upstream derives the horizontal alignment per label
and only for the extremes (`…/cartesian/axis/HorizontalAxis.kt:204-208`, driven by
`ItemPlacer.getShiftExtremeLabels`). There is no global "align every label Start/Center/End at its
tick".

**(e) `AXIS_LABEL_VERTICAL_PADDING` 4 → 2 and the `getHeight` `lineThickness` fix** — fork-local v3
parity tweaks, no upstream movement.

**Evidence:** reads of `…/cartesian/axis/HorizontalAxis.kt` and `…/cartesian/axis/VerticalAxis.kt` at
v3.3.1; `BaseAxis.kt` at both refs; v3.3.0 release note (the only axis additions are `titlePosition`
and `shiftExtremeLabels`).

**Confidence:** high.

---

## 7. pointSpacingProvider → `visibleLabelsCount` — PARTIALLY UPSTREAM

**Status in the fork, established first as instructed:** `pointSpacingProvider` is **removed**.
`git grep -n pointSpacingProvider HEAD -- vico/` → no hits. It was replaced by
`CartesianChart.visibleLabelsCount: Double` (`…/cartesian/CartesianChart.kt:69`, applied in
`prepare()` at :284-315 via `calculateLabelSpacingScale` → `layerDimensions.scale(scaleFactor)`).
So the question becomes: does upstream provide `visibleLabelsCount`?

**Upstream API:** `…/cartesian/Zoom.kt:49-51` (pre-existing at the fork point)

```kotlin
/** Ensures the specified number of _x_ units is visible. */
public fun x(x: Double): Zoom = Zoom { context, layerDimensions, bounds ->
  bounds.width * (context.ranges.xStep / x).toFloat() / layerDimensions.xSpacing
}
```

**What it covers:** the core intent — "make exactly N x-units fill the window" — by producing a zoom
factor rather than scaling `layerDimensions` directly. Used as
`VicoZoomState(zoomEnabled = false, initialZoom = Zoom.x(7.0))` it is the same arithmetic:
`bounds.width / N` desired spacing over current `xSpacing`.

**What it does not cover**
- **The start inset.** The fork computes `desiredSpacing = (availableWidth - startInsetPx) / visibleLabelsCount`
  (`…/cartesian/CartesianChart.kt:315`), with a comment recording that leaving the inset out
  "over-spaces the labels by exactly the gap and pushes the last one off the edge". `Zoom.x` divides
  by the full `bounds.width`. With a nonzero start inset the two differ by exactly that gap.
- **Where it applies.** Zoom is a separate multiplier applied downstream and interacts with
  `VicoZoomState`, `Zoom.Content`, zoom gestures and the zoom→scroll compensation path. The fork's
  version mutates `layerDimensions` inside `prepare()`, before any of that. Equivalence of the two
  under scroll + snap is **not** established here.
- `visibleLabelsCount = 0.0` ("auto / fit all") maps to `Zoom.Content`, not to `Zoom.x`.

**Verdict rationale:** PARTIALLY UPSTREAM rather than FULLY, because the inset term is a real
difference and the zoom path is a different place in the pipeline.

**Confidence:** medium-high on the API; **low** on "swap in `Zoom.x` and it behaves the same" — that
needs a visual parity run, not a reading.

---

## 8. Interpolated marker targets — PARTIALLY UPSTREAM

**Upstream API:** `…/cartesian/CartesianChart.kt:479-495`

```kotlin
/** Returns the `CartesianMarker.Target`s for `x`. */
public open fun getMarkerTargets(
  x: Double?,
  visibleXRange: ClosedFloatingPointRange<Double>,
): List<CartesianMarker.Target> =
  if (x == null || x !in visibleXRange || markerTargets.isEmpty()) emptyList()
  else { /* nearest existing target by |key − x| */ }
```

**What it covers**
- The **hook**. This is `public open` and is the single place the host asks for targets
  (`…/cartesian/CartesianChartHost.kt:262-269`). Our `synthesizeInterpolatedTargets` can be an
  override of it in a subclass rather than a patch to the upstream file — a real reduction in fork
  surface. It was already `public open` at the fork point (`e67e49520:…/CartesianChart.kt:371`), but
  its **signature changed** at v3.3.1 (second parameter is now `visibleXRange`, and there is a
  separate private `getMarkerTargets(x, seriesIndex)` that drives visibility listeners), so the
  override must be re-aimed.
- `getVisibleXRange` exists (`…/cartesian/CartesianMeasuringContext.kt:119`) and is now handed to the
  hook — so a subclass gets the visible range for free.

**What it does not cover**
- No synthesis of targets at x values with no data point; no interpolation of y at an arbitrary x.
  `git grep -ni 'synthes\|isInterpolated' v3.3.1 -- vico/` → no hits.
- No `isInterpolated` flag on `LineCartesianLayerMarkerTarget.Point`, and therefore no
  "skip the indicator dot, keep the guideline and label" path in `DefaultCartesianMarker`.
- `forEachPersistentMarker` (`…/cartesian/CartesianChart.kt:470-476`) still looks up
  `markerTargets[x]` and silently draws nothing when the x is not a data point — no fallback.
- `VicoScrollState.visibleXRange` is not public (only the internal context extension is), and
  `getVisibleAxisLabels` / `getInterpolatedYValues` have no upstream analogue.
- `xToScrollValueWithPadding` **no longer exists in the fork** — it is now
  `xToScrollValueBehindStartInset` (internal), and upstream has neither.
- Marker `contentPadding` and `yLabelCallback`, documented at the end of FEATURE-8, are covered under
  #13 and are not upstream.

**Confidence:** high.

---

## 9. `Size.Scroll` / scrollable vertical axis — PARTIALLY UPSTREAM

**(a) The axis size itself — NOT upstream.** `…/cartesian/axis/BaseAxis.kt:172-240` at v3.3.1:

```kotlin
public sealed class Size {
  public class Auto(min: Dp = 0.dp, max: Dp = Dp.Infinity) : Size()
  public class Fixed(value: Dp) : Size()
  public class Fraction(fraction: Float) : Size()   // deprecated at v3.3.0
  public class Text(text: CharSequence) : Size()
}
```

No `Scroll` variant, no `isLabelsScrollable`, and nothing in `VerticalAxis` that offsets the axis
line/ticks by the scroll value. `Size.Fraction` was in fact **deprecated** at v3.3.0 in favour of
`chartAreaHeight`, so upstream is moving away from, not toward, extra `Size` variants.

**(b) The v3-parity sub-items bundled into FEATURE-9** — one has a new upstream answer, the rest do not:

| FEATURE-9 sub-item | v3.3.1 |
|---|---|
| Labels at data min/max excluded by the `fullXRange` check | **Still there**: `…/cartesian/axis/HorizontalAxisItemPlacers.kt:56-57` still reads `… \|\| potentialValue == fullXRange.start) continue` / `== fullXRange.endInclusive) break`. Not upstream. (And the fork has since gone further — it now drops the value that *closes* the window, with an `endEpsilon`, to make `visibleLabelsCount` mean what it says.) |
| First/last label clipped | **New upstream option**: `ItemPlacer.aligned(shiftExtremeLabels = true)` + `ItemPlacer.getShiftExtremeLabels` (`…/cartesian/axis/HorizontalAxis.kt:888, 919-932`) anchor the extreme labels inward (`Position.Horizontal.End` / `.Start`) "without shrinking the plot area, preventing clipping". A **different remedy** from our "extend the clip by `maxLabelWidth.half`" — upstream moves the label, we widen the window. Both solve clipping; they do not produce the same picture. |
| `updateLayerDimensions` made a no-op; label `maxWidth` not passed; v3-style clip; point clip via margins | Not upstream; `…/cartesian/axis/HorizontalAxis.kt:150-232` still computes `maxWidth` per label and clips via `getLineLeft`/`getLineRight`. |

**Confidence:** high on `Size.Scroll`; high on the sub-item table (direct line reads).

---

## 10. Chart layout parity — PARTIALLY UPSTREAM

**(a) The 200 dp height cap — GONE upstream; our fix is obsolete.** This is the clearest
fork-shrinking finding in this dossier.

At the fork point (`e67e49520:…/cartesian/CartesianChartHost.kt:275`):
```kotlin
Box(modifier = modifier.heightIn(max = CHART_HEIGHT.dp).fillMaxWidth(), content = content)  // 200f
```
The fork's FEATURE-10 removed `heightIn(max = …)` from `CartesianChartHostBox`
(`HEAD:…/cartesian/CartesianChartHost.kt:538`).

At v3.3.1 the whole box was replaced (commit `a3e3d6cd0`, "size charts by their content area, not
their total height"), `…/common/ChartHostBox.kt`:
```kotlin
@Composable internal fun ChartHostBox(
  modifier: Modifier, chartAreaHeight: Dp,
  measureExtras: ((widthPx: Int) -> Float)?, content: @Composable BoxScope.() -> Unit,
) { Box(modifier = modifier.fillMaxWidth().chartAreaHeight(chartAreaHeight, measureExtras), content = content) }
```
whose `layout` block reads: `if (constraints.hasFixedHeight) constraints.maxHeight else
(chartAreaHeight.roundToPx() + extras).coerceIn(...)`. A consumer `Modifier.height()` now wins
outright; `chartAreaHeight` (public parameter on `CartesianChartHost`, default
`Defaults.CARTESIAN_CHART_AREA_HEIGHT = 192f`) only sets the **default** coordinate-system height,
with legend/marker/axis extras added on top. There is no cap left to remove.
Release note: "The legend, the marker, the axis margins, and other components now add to this height
rather than being fitted inside a capped total height."

**(b) The `CartesianLayerMargins` top/bottom halving — NOT upstream.** v3.3.1
`…/cartesian/layer/CartesianLayerMargins.kt:46-52`:
```kotlin
public fun ensureValuesAtLeast(start: Float = …, top: Float = …, end: Float = …, bottom: Float = …) {
  this.start = this.start.coerceAtLeast(start)
  this.top = this.top.coerceAtLeast(top)        // no .div(2)
  this.end = this.end.coerceAtLeast(end)
  this.bottom = this.bottom.coerceAtLeast(bottom)
}
```
The fork halves `top`/`bottom` and adds a second method `ensureVerticalValuesAtLeast(top, bottom)`
that stores them as given, for content that sits wholly outside the plot (the marker label). None of
this is upstream.
**Caution:** `chartAreaHeight` changes what the margins are subtracted from (extras are now *added*
to the chart area rather than carved out of a capped total). Whether the halving is still needed for
v3 visual parity is a question for the parity harness — do not assume either way from this dossier.

**Confidence:** high on both facts. The "obsolete" label on (a) is about the cap, not about the
resulting pixel heights, which must still be measured.

---

## 11. `yTransform` — NOT UPSTREAM

**Upstream API:** none.

There is no render-time Y transform on `LineCartesianLayer` or any layer at v3.3.1 — no hook taking
`(series, yRange, visibleXRange)`, no per-frame/per-target caching, nothing keyed on a settle epoch.
The fork's supporting additions are equally absent: `CartesianChartRanges.getTargetYRange`
(fork-only, `…/cartesian/data/CartesianChartRanges.kt:42`), `WindowSettleSignal` (fork-only file),
`MutableCartesianChartRanges.tryUpdateX` (widen x only, fork-only).

**Nearest upstream mechanism, and why it is not a substitute:**
`CartesianLayerDrawingModelInterpolator` — which v3.3.0 made the owner of interpolation policy, with
per-type factories `line()`, `column()`, `candlestick()` and the old `CartesianLayerDrawingModel.transform`
/ `Entry.transform` hooks deprecated (`…/cartesian/data/CartesianLayerDrawingModelInterpolator.kt`,
`…/cartesian/data/LineCartesianLayerDrawingModelInterpolator.kt`). It interpolates drawing models
**between data updates** — it is driven by the diff-animation fraction, not by the frame or the
visible window — so it cannot move a line within the plot while the data is unchanged. Note also
that `LineCartesianLayer` at v3.3.1 reads `drawingModel` on the draw path for the reveal sweep
(`sweepFraction`), which collides with the fork's "skip `drawingModel` when a `ScrollAwareRangeProvider`
is in use" change.

**Evidence:** `git grep -n 'yTransform\|getTargetYRange\|settleEpoch' v3.3.1 -- vico/` → no hits; read
of `…/cartesian/layer/LineCartesianLayer.kt` at v3.3.1; v3.3.0 release note on interpolator factories
and the reveal animation.

**Confidence:** high.

---

## 12. `isReady` + `runTransaction(animate)` — NOT UPSTREAM

**Upstream API:** `…/cartesian/data/CartesianChartModelProducer.kt` at v3.3.1

```kotlin
public class CartesianChartModelProducer {
  public suspend fun runTransaction(block: Transaction.() -> Unit)
  public inner class Transaction internal constructor() {
    public fun add(partial: CartesianLayerModel.Partial)
    public fun extras(block: (MutableExtraStore) -> Unit)
  }
}
```

No `isReady`, no `animate` parameter anywhere — on `runTransaction`, on `Transaction`, or on
`UpdateReceiver.handleUpdate`.

**What is nearby and what it does not do**
- `CartesianChartHost(initialAnimationSpec: AnimationSpec<Float>? = animationSpec)` (v3.3.0, commit
  `ac445d2e8`). Setting it to `null` skips the **initial reveal** animation. It is host-wide and
  one-shot; it cannot suppress the diff animation for one particular later transaction, which is what
  `runTransaction(animate = false)` is for.
- `animationSpec = null` disables diff animations for the host permanently — again not per-transaction.
- For `isReady`: upstream's `placeholder: @Composable BoxScope.() -> Unit` on `CartesianChartHost`
  renders while `model == null` (`…/cartesian/CartesianChartHost.kt:107-117`). That covers
  "show something before the first model" **inside** the chart's own box, but gives the consumer no
  observable signal — meApp's use is a screen-level skeleton outside the chart. Note the fork's own
  KDoc stresses `isReady` is a latch (never returns to false), which `model == null` is not.
- Also fork-only in this file: `PreviewContextKey/PreviewContext` renamed to
  `InlineUpdateContextKey/InlineUpdateContext` and made **public** (for tests with a virtual-time
  scheduler). Upstream still has the internal `PreviewContext`.

**Confidence:** high.

---

## 13. Marker `contentPadding`, crosshair, resting label — NOT UPSTREAM

**Upstream API:** `…/cartesian/marker/DefaultCartesianMarker.kt:59-72` at v3.3.1

```kotlin
public open class DefaultCartesianMarker(
  protected val label: TextComponent,
  protected val valueFormatter: ValueFormatter = ValueFormatter.default(),
  protected val labelPosition: LabelPosition = LabelPosition.Top,
  protected val indicator: ((Color) -> Component)? = null,
  protected val indicatorSize: Dp = Defaults.MARKER_INDICATOR_SIZE.dp,
  protected val guideline: LineComponent? = null,
  …
  protected val markerCornerBasedShape: MarkerCornerBasedShape? = …,
  protected val tickSize: Dp = markerCornerBasedShape?.tickSize.orZero,
)
@Composable public fun rememberDefaultCartesianMarker(
  label: TextComponent, valueFormatter: …, labelPosition: …, indicator: …, indicatorSize: …,
  guideline: LineComponent? = null,
): DefaultCartesianMarker
```

**Not present, all three parts:**
- **`contentPadding: Insets`** — no such parameter; no guideline extension beyond `layerBounds`
  (`…/DefaultCartesianMarker.kt:231`: `guideline?.drawVertical(this, x, layerBounds.top, layerBounds.bottom)`);
  `updateLayerMargins` (:234) still uses the dynamic label height, not a fixed 24 dp.
- **Horizontal crosshair + label** — no second (horizontal) line, no `horizontalLabelPosition` /
  `horizontalLabelFormatter`, no crosshair anchor logic. `git grep -ni crosshair v3.3.1 -- vico/` →
  no hits.
- **Resting marker label formatted on settle** — no `restingLabel`, and the hooks the fork added to
  the `CartesianMarker` interface do not exist upstream:
  `CartesianMarker.drawResting(context)` and `CartesianMarker.onScrollSettled(visibleXRange)` are
  fork-only additions to `…/cartesian/marker/CartesianMarker.kt` (a file BASELINE lists as
  "upstream never touched"). Confirmed by `git grep -n 'drawResting\|onScrollSettled' v3.3.1 -- vico/`
  → no hits.
- `yLabelCallback` (documented under FEATURE-8) — also absent.

The only upstream movement on `DefaultCartesianMarker` since the fork point is the marker-target
narrowing fix (v3.2.0, `narrowMarkerTargets` in `…/cartesian/CartesianChartHost.kt:56-68`) and
`MARKER_POSITION_TOLERANCE`, neither of which touches these three.

**Confidence:** high.

---

## UNDETERMINED items and what would settle them

Nothing was left wholly UNDETERMINED, but three claims above are weaker than the rest and are the
ones most likely to bite:

1. **#7 — is `Zoom.x(N)` a drop-in for `visibleLabelsCount`?** Settle by: building one chart each way
   (fork `visibleLabelsCount = 7.0` vs upstream `VicoZoomState(zoomEnabled = false, initialZoom =
   Zoom.x(7.0))`) with a nonzero start inset, and comparing `layerDimensions.xSpacing`, the resulting
   label count, and the snap positions after a fling. The inset term in
   `calculateLabelSpacingScale` (`CartesianChart.kt:315`) predicts they differ.
2. **#5(c) — can the consumer emulate the FadingEdges change?** Settle by: logging
   `layerDimensions.startPadding` against the configured `unscalableStart` on a `LineCartesianLayer`
   with a nonzero `pointSize`; `LineCartesianLayer.kt:901` adds `maxPointSize.half`, so they should
   differ by exactly that.
3. **#2 — is `acceptsGestures` really redundant?** Settle by: a chart whose controller returns
   `false` from `shouldAcceptInteraction`, placed inside a `clickable` card, and checking the card
   still fires. The code path (`Modifier.kt:156-174`) says yes; this has not been run.

Also worth a single explicit check during the port, because it is cheap and the blast radius is
large: **#10(b)** — whether the margin halving is still needed once `chartAreaHeight` is in play.
That is a parity-harness question, not a code-reading one.
