# S2-D — Extension points at v3.3.1, and what of the fork they could absorb

Target ref: `v3.3.1` = `3d675ccc7`. Fork ref: `HEAD` (`MOB-2953-week-range-spans-eight-days`).
All paths below are relative to the repo root. The common-source prefix is abbreviated throughout as:

```
$C = vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose
```

Everything quoted here was read with `git show v3.3.1:<path>`; fork behaviour was read with
`git diff e67e49520..HEAD -- <path>`. Where I could not tell from the diff alone what a patch is
*for*, I say so rather than guessing.

Method note, since it changes how to read Section 2: I worked outward from v3.3.1's own surface
first and only then asked which fork patch sites it reaches. That ordering produced two findings
that an inside-out read would have missed — a fork patch that is **self-inflicted** (the fork
created the problem its own patch solves, and v3.3.1 never had it) and an upstream **bug** the
fork fixed that upstream still carries.

---

## 1. The extension surface at v3.3.1

### 1.1 Ranges and the model

**`CartesianLayerRangeProvider`** — `$C/cartesian/data/CartesianLayerRangeProvider.kt`

```kotlin
@Immutable
public interface CartesianLayerRangeProvider {
  public fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = minX
  public fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = maxX
  public fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = minY.coerceAtMost(0.0)
  public fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
    if (minY == 0.0 && maxY == 0.0) 1.0 else maxY.coerceAtLeast(0.0)
  public companion object {
    public val Intrinsic: CartesianLayerRangeProvider
    public fun auto(): CartesianLayerRangeProvider
    public fun fixed(minX: Double? = null, maxX: Double? = null,
                     minY: Double? = null, maxY: Double? = null): CartesianLayerRangeProvider
  }
}
```

This is the "AxisValueOverrider equivalent" and it is fully public and fully implementable by a
consumer. It is passed to `LineCartesianLayer` as `rangeProvider` (a `public constructor`
parameter, and a `rememberLineCartesianLayer` parameter).

**What it does not reach — and this is the single most consequential gap in the whole audit:**
the provider is consulted *only* from `CartesianChart.updateRanges(...)`, which is
`internal fun`, and `updateRanges` is called from exactly two places:
`CartesianChartModelProducer.collectAsState`'s local `updateRanges(model)`
(`$C/cartesian/data/CartesianChartModel.kt`, invoked from `registerForUpdates` / `handleUpdate`,
i.e. **once per data push**) and the model-overload of `CartesianChartHost`
(`remember(chart, model) { ranges.reset(); chart.updateRanges(ranges, model) }`). The provider is
therefore a pure function of *the model*, asked once when the model changes. A provider whose
answer depends on anything else — scroll position, a settled window, an animation frame — will
never be re-asked. There is no `invalidateRanges()`, no `Flow<CartesianChartRanges>` input, and
`ranges` is not a parameter of the producer-based `CartesianChartHost`.

There is one narrow lever: the *model* overload
`CartesianChartHost(chart, model, …)` keys its range computation on `remember(chart, model)`, and
`CartesianChart.equals` compares `layers` (and `LineCartesianLayer.equals` compares
`rangeProvider`). So handing that overload a `chart.copy(...)` carrying a differently-equal
provider *does* recompute ranges. It costs the producer's diff animation and a chart
re-instantiation per animation frame. Viable; ugly. I would not build a 60 fps y-range animation
on it.

**`CartesianChartRanges`** — `$C/cartesian/data/CartesianChartRanges.kt`

```kotlin
public interface CartesianChartRanges {
  public val minX: Double
  public val maxX: Double
  public val xStep: Double
  public fun getYRange(axisPosition: Axis.Position.Vertical?): YRange
  public val xLength: Double get() = maxX - minX
  public interface YRange { public val minY: Double; public val maxY: Double; public val length: Double }
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public object Empty : CartesianChartRanges
}
```

Public and implementable — but there is nowhere to *supply* an implementation. `CartesianChartHost`
takes no `ranges` parameter; the instance the chart draws with comes from
`MutableCartesianChartRanges.toImmutable()`, and `toImmutable()` is `internal`.

**`MutableCartesianChartRanges`** — `$C/cartesian/data/MutableCartesianChartRanges.kt`

```kotlin
public class MutableCartesianChartRanges : CartesianChartRanges {
  internal var yRanges: MutableMap<Axis.Position.Vertical?, MutableYRange>
  override var xStep: Double = 1.0
  override fun getYRange(axisPosition: Axis.Position.Vertical?): CartesianChartRanges.YRange =
    yRanges[axisPosition] ?: yRanges.getValue(null)
  public fun tryUpdate(minX: Double, maxX: Double, minY: Double, maxY: Double,
                       axisPosition: Axis.Position.Vertical?)
  public fun reset()
  public class MutableYRange(override var minY: Double, override var maxY: Double) : …
}
```

`tryUpdate` is the only mutator, and it is x-and-y together. There is no x-only widening. Note
also `yRanges.getValue(null)` — a hard `NoSuchElementException` if no layer ever offered a y range.
A consumer reaches `MutableCartesianChartRanges` only by implementing `CartesianLayer` itself
(`updateChartRanges(ranges, model)` is on the public `CartesianLayer` interface).

**`CartesianChartModelProducer`** — `$C/cartesian/data/CartesianChartModelProducer.kt`

```kotlin
public class CartesianChartModelProducer {
  public suspend fun runTransaction(block: Transaction.() -> Unit)
  public inner class Transaction internal constructor() {
    public fun add(partial: CartesianLayerModel.Partial)
    public fun extras(block: (MutableExtraStore) -> Unit)
  }
}
```

`runTransaction` is `suspend` and returns only once the update is complete — that is the whole
transaction API. `extras` is the sanctioned channel for shipping arbitrary consumer data *with*
the model (keyed by `ExtraStore.Key<T>`, both public), and it is what makes
`ScrollAwareRangeProvider.Window` legitimate as a concept.

**What it does not reach:** no per-transaction animation control (animation is chosen host-side by
`animationSpec` / `initialAnimationSpec`); no readiness signal; `PreviewContext` and
`PreviewContextKey` are `internal`, so a test cannot force inline dispatch.

**Drawing-model interpolation** — `$C/cartesian/data/CartesianLayerDrawingModelInterpolator.kt`,
`BaseCartesianLayerDrawingModelInterpolator.kt`, `LineCartesianLayerDrawingModelInterpolator.kt`,
`DefaultCartesianLayerDrawingModelInterpolator.kt`. `CartesianLayerDrawingModelInterpolator<T, M>`
is a public interface with `transform(drawingModel: M?, fraction: Float): M?`, supplied via
`LineCartesianLayer(drawingModelInterpolator = …)`. A consumer can fully control how the diff
animation interpolates. It does **not** control *when* the interpolation runs or what the y range
is while it runs.

### 1.2 Scroll

**`VicoScrollState`** — `$C/cartesian/VicoScrollState.kt`

Public surface at v3.3.1:

```kotlin
public class VicoScrollState {
  public constructor(
    scrollEnabled: Boolean = true,
    initialScroll: Scroll.Absolute = Scroll.Absolute.Start,
    autoScroll: Scroll = initialScroll,
    autoScrollCondition: AutoScrollCondition = AutoScrollCondition.Never,
    autoScrollAnimationSpec: AnimationSpec<Float> = spring(),
    xSnapStep: Double? = null,
    snapAnimationSpec: AnimationSpec<Float> = spring(),
  )
  public var value: Float      // get public, set private — backed by MutableFloatState
  public var maxValue: Float   // get public, set internal
  public suspend fun scroll(scroll: Scroll)
  public suspend fun animateScroll(scroll: Scroll, animationSpec: AnimationSpec<Float> = spring())
}
@Composable public fun rememberVicoScrollState(…same params…): VicoScrollState
```

So: **scroll position *is* observable from outside** — `value` and `maxValue` are snapshot state
and read inside a composable will recompose. That is new relative to the fork point in usefulness,
and the fork's `isScrolling` can be had from `scrollableState.isScrollInProgress`… except
`scrollableState` is `internal`. There is no public `isScrollInProgress`.

`xSnapStep` + `snapAnimationSpec` + the internal `SnapFlingBehavior`
(`$C/cartesian/Modifier.kt`, bottom) + `$C/cartesian/SnapScroll.kt` are **new upstream
functionality at v3.3.1** that did not exist at the fork point. `getSnapTarget`/`getSnapDelta` are
`internal`, and the snap target is always a multiple of `xSnapStep` from `startPadding`, clamped,
with `0f` and `maxValue` as alternatives.

Also new at v3.3.1, and directly relevant: `VicoScrollState.update(...)` now carries a
`previousScrollLayout` and *re-anchors the viewport* when `ranges.minX` or `ranges.xStep` change,
using a `preClampValue` captured before `maxValue` is written. That is upstream solving, properly,
the class of problem the fork's `prevMaxValue != maxValue → re-apply initialScroll` hack attacks.

**What `VicoScrollState` does not reach:** `context` (`CartesianMeasuringContext`),
`layerDimensions` and `bounds` are all `private`. There is therefore **no public way to convert a
scroll value to an x value, or to ask what x range is visible.** `scrollableState`,
`consumedXDeltas`, `unconsumedXDeltas`, `scrollEnabled`, `getSnapDelta`, `performSnap`,
`clearUpdated` and `update` are all `internal`.

**`Scroll`** — `$C/cartesian/Scroll.kt`

```kotlin
public sealed interface Scroll {
  public fun interface Absolute : Scroll {
    public fun getValue(context: CartesianMeasuringContext,
                        layerDimensions: CartesianLayerDimensions,
                        bounds: Rect, maxValue: Float): Float
    public companion object {
      public val Start: Absolute
      public val End: Absolute
      public fun pixels(pixels: Float): Absolute
      public fun x(x: Double, bias: Float = 0f): Absolute
    }
  }
  public fun interface Relative : Scroll {
    public fun getDelta(context: CartesianMeasuringContext,
                        layerDimensions: CartesianLayerDimensions,
                        bounds: Rect, maxValue: Float): Float
    public companion object { public fun pixels(…); public fun x(…) }
  }
}
```

This is a **genuinely open extension point and the best one in the scroll area**: both members are
`fun interface`s, every parameter type is public (`CartesianMeasuringContext.ranges` is public,
`CartesianLayerDimensions.startPadding`/`xSpacing`/`endPadding` are public), so a consumer can
write any absolute or relative scroll target it likes, including one that reads the full chart
geometry. `Scroll.getDelta(…, value)` (the dispatch extension) is `internal`, but no consumer
needs it.

**Fling behaviour: closed.** `CartesianChartHost` has no `flingBehavior` parameter. The modifier
chain that installs `scrollable(...)` is `internal fun Modifier.pointerInput(...)` in
`$C/cartesian/Modifier.kt`, and the only fling choice is upstream's own `SnapFlingBehavior`,
selected iff `scrollState.xSnapStep != null`. A consumer cannot supply a `FlingBehavior`.

### 1.3 Markers

**`CartesianMarker`** — `$C/cartesian/marker/CartesianMarker.kt`

```kotlin
@Immutable
public interface CartesianMarker : CartesianLayerMarginUpdater<CartesianChartModel> {
  public fun drawUnderLayers(context: CartesianDrawingContext, targets: List<Target>) {}
  public fun drawOverLayers(context: CartesianDrawingContext, targets: List<Target>) {}
  public interface Target { public val x: Double; public val canvasX: Float }
}
```

Fully implementable. Because it extends `CartesianLayerMarginUpdater`, a consumer marker can also
reserve chart margins (`updateLayerMargins` / `updateHorizontalLayerMargins`, both public with
default bodies). **What it does not reach:** both draw methods are only called when
`markerTargets.isNotEmpty()` — there is no "draw while nothing is marked" callback, and no
scroll/settle callback of any kind.

**`CartesianMarkerController`** — `$C/cartesian/marker/CartesianMarkerController.kt`

```kotlin
public fun interface CartesianMarkerController {
  public val acceptsLongPress: Boolean get() = true
  public val lock: Lock get() = Lock.X
  public val consumeMoveEvents: Boolean get() = false
  public fun shouldAcceptInteraction(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean = true
  public fun shouldShowMarker(interaction: Interaction, targets: List<CartesianMarker.Target>): Boolean
  public enum class Lock { X, Position }
  public companion object {
    @Composable public fun rememberShowOnPress(consumeMoveEvents: Boolean = false): CartesianMarkerController
    @Composable public fun rememberShowOnHover(): CartesianMarkerController
    @Composable public fun rememberToggleOnTap(): CartesianMarkerController
  }
}
```

**`Interaction`** — `$C/cartesian/marker/Interaction.kt` — `public sealed class Interaction` with
`point: Point` and subclasses `Press`, `Tap`, `LongPress`, `Move`, `Release`, `Zoom`, `Enter`,
`Exit(point, isInBounds)`. Its `Saver` is `internal`.

This is a strong extension point: a consumer controller sees every pointer interaction with chart
coordinates, decides whether to respond, and decides visibility. **What it does not reach:** the
controller is a *predicate*, not a selector. It cannot choose *which* x is marked — that is fixed
in `CartesianChartHostImpl`:

```kotlin
if (shouldShow && narrowedTargets.isNotEmpty()) { markerX = narrowedTargets.first().x; … }
```

where `narrowMarkerTargets` (internal, same file) picks the target closest to the pointer. It also
cannot veto *gesture attachment*: `Modifier.pointerInput` installs `detectTapGestures` whenever
`onInteraction != null`, which it is whenever `chart.marker != null`, so a chart with a marker
consumes taps regardless of what the controller says.

**`CartesianMarkerVisibilityListener`** — `$C/cartesian/marker/CartesianMarkerVisibilityListener.kt` —
public interface, `onShown` / `onUpdated` / `onHidden`, supplied via `rememberCartesianChart`.
This is the sanctioned out-channel for "a marker is now at these targets", and it carries
`List<CartesianMarker.Target>`, i.e. the entries and canvas coordinates.

**`DefaultCartesianMarker`** — `$C/cartesian/marker/DefaultCartesianMarker.kt`

```kotlin
public open class DefaultCartesianMarker(
  protected val label: TextComponent,
  protected val valueFormatter: ValueFormatter = ValueFormatter.default(),
  protected val labelPosition: LabelPosition = LabelPosition.Top,
  protected val indicator: ((Color) -> Component)? = null,
  protected val indicatorSize: Dp = Defaults.MARKER_INDICATOR_SIZE.dp,
  protected val guideline: LineComponent? = null,
) : CartesianMarker {
  override fun drawOverLayers(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>)
  protected open fun CartesianDrawingContext.drawIndicator(x: Float, y: Float, color: Color, halfIndicatorSize: Float)
  protected fun drawLabel(context: …, targets: …)
  protected fun overrideXPositionToFit(…)
  protected fun CartesianDrawingContext.drawGuideline(targets: List<CartesianMarker.Target>)
  override fun updateLayerMargins(…)
  public fun interface ValueFormatter { public fun format(context: CartesianDrawingContext, targets: List<CartesianMarker.Target>): CharSequence }
  protected companion object { public val keyNamespace: CacheStore.KeyNamespace }
}
```

Open class, public constructor, `protected` members, `drawOverLayers` overridable, and
`markerCornerBasedShape` / `tickSize` derived (`protected`). A consumer can subclass this freely.
`ValueFormatter` is a `fun interface` — arbitrary label text with full target access.

**Marker target production.** `LineCartesianLayerMarkerTarget` is a public interface
(`points: List<Point>`, `Point(entry, canvasY, color)` a public `data class`), but
`MutableLineCartesianLayerMarkerTarget` is `internal`, and targets are produced by
`LineCartesianLayer`'s `protected open fun CartesianDrawingContext.updateMarkerTargets(...)`
(two overloads). Those *are* `protected open`, so a `LineCartesianLayer` subclass can suppress or
reshape its own targets. It cannot add a field to `Point`.

**Gesture → marker path, end to end** (all internal):
`Modifier.pointerInput` → `onInteraction(Interaction)` → `measuringContext.pointerPositionToX(...)`
(`$C/cartesian/PointerPosition.kt`, `internal`) → `chart.getMarkerTargets(x, visibleXRange)` →
`narrowMarkerTargets` → `markerController.shouldAcceptInteraction/shouldShowMarker` →
`markerX`/`markerSeriesIndex` state → `CartesianMeasuringContext.markerX`.

`CartesianChart.getMarkerTargets(x: Double?, visibleXRange: ClosedFloatingPointRange<Double>)` is
`public open` — a `CartesianChart` subclass *can* override which targets an x resolves to,
including synthesising targets that no layer produced. That is the one open seam in this path, and
it is a real one.

### 1.4 Axes

**`Axis<P>`** — `$C/cartesian/axis/Axis.kt` — a fully public interface
(`position`, `drawUnderLayers(context, axisDimensions)`, `drawOverLayers(...)`,
`setRestrictedBounds(vararg Rect?)`, `updateLayerDimensions(...)`, `updateAxisDimensions(...)`,
plus `Bounded` and `CartesianLayerMarginUpdater`). **A consumer can write a complete custom axis
from scratch and pass it as `startAxis`/`topAxis`/`endAxis`/`bottomAxis` to
`rememberCartesianChart`.** This is the most under-used extension point in the library.

**`BaseAxis<P>`** — `$C/cartesian/axis/BaseAxis.kt` — `public abstract class` with a public
constructor and `protected` `line`/`label`/`labelRotationDegrees`/`valueFormatter`/`tick`/
`tickLength`/`guideline`/`size`/`titleComponent`/`title`, plus public `tickPosition` and
`lineDrawingOrder`. Also `protected fun isNotInRestrictedBounds(...)` and the `protected`
`MeasuringContext.lineThickness` / `tickThickness` / `guidelineThickness` / `tickLength`
extensions. `Size` is a `public sealed class` with `Auto(min,max)`, `Fixed(value)`,
`Fraction(fraction)` (deprecated) and `Text(text)` — **sealed, so a consumer cannot add a case.**

**`HorizontalAxis<P>`** — `$C/cartesian/axis/HorizontalAxis.kt`

```kotlin
public open class HorizontalAxis<P : Axis.Position.Horizontal>
protected constructor(
  override val position: P, line: LineComponent?, label: TextComponent?,
  labelRotationDegrees: Float, valueFormatter: CartesianValueFormatter,
  tick: LineComponent?, tickLength: Dp, guideline: LineComponent?,
  public val itemPlacer: ItemPlacer, size: Size,
  titleComponent: TextComponent?, title: (ExtraStore) -> CharSequence?,
  tickPosition: TickPosition, lineDrawingOrder: LineDrawingOrder,
  public val titlePosition: TitlePosition,
) : BaseAxis<P>(…)
```

`open class` with a **`protected constructor`** — a consumer subclass can call it. Overridable:
`drawUnderLayers`, `drawOverLayers`, `updateLayerDimensions`, `updateLayerMargins`
(all `override`s of public interface members), plus
`protected open fun drawGuidelines(context, baseCanvasX, fullXRange, labelValues, lineValues)` and
`protected open fun getHeight(context, layerDimensions, maxLabelWidth)`. Also available to a
subclass: `protected fun CartesianDrawingContext.getLinesCorrectionX(...)`,
`protected fun CartesianMeasuringContext.getMaxLabelWidth(...)` / `getMaxLabelHeight(...)`.
Not available: `clipPath`, `getLineLeft`, `getLineRight`, `getLineStart`, `getLineEnd`,
`drawLineAndTicks`, `TextComponent.drawTitle`/`getTitleBounds` (all `private`), and
`internalGetFullXRange` / `getVisibleXRange()` (`internal`).

**`HorizontalAxis.ItemPlacer`** — a public interface with ten members
(`getShiftExtremeLines`, `getFirstLabelValue`, `getLastLabelValue`, `getLabelValues`,
`getWidthMeasurementLabelValues`, `getHeightMeasurementLabelValues`, `getLineValues`,
`getShiftExtremeLabels`, `getStartLayerMargin`, `getEndLayerMargin`) and factories
`aligned(spacing, offset, shiftExtremeLines, addExtremeLabelPadding, shiftExtremeLabels)`,
`segmented(shiftExtremeLines)`, `extremes(shiftExtremeLines)`.
**A consumer can implement this outright and decide every label, tick and guideline x, plus the
layer margins the axis demands.** The bundled implementations
(`AlignedHorizontalAxisItemPlacer`, `SegmentedHorizontalAxisItemPlacer`,
`ExtremesHorizontalAxisItemPlacer`, `BaseHorizontalAxisItemPlacer`, and the file-private
`CartesianDrawingContext.getLabelValues(...)` helper) are all `internal`/`private` — a consumer
cannot subclass or reuse them, only replace them.

`shiftExtremeLabels` is **new at v3.3.1**: it end-aligns the first label and start-aligns the last,
"anchoring them to the plot-area edges, preventing clipping without shrinking the plot area."
That is upstream's own answer to extreme-label overhang.

**`VerticalAxis<P>`** — `$C/cartesian/axis/VerticalAxis.kt` — same shape: `public open class`,
`protected constructor`, public `horizontalLabelPosition` / `verticalLabelPosition` / `itemPlacer`
/ `titlePosition`. Overridable: `drawUnderLayers`, `drawOverLayers`, `updateLayerDimensions`,
`updateLayerMargins`, `updateHorizontalLayerMargins`, plus
`protected open fun drawLabel(...)` and `protected open fun getWidth(context, freeHeight)`.
Available to a subclass: `protected var maxLabelWidth` / `maxLabelHeight`,
`protected fun CartesianMeasuringContext.getTickLeftX()`, `getMaxLabelHeight()`,
`getMaxLabelWidth(axisHeight)`, `protected fun CartesianDrawingContext.getLineCanvasYCorrection(...)`.
`drawLineAndTicks`, the title helpers and `getAxisLineCenterX` are `private`.

**`VerticalAxis.ItemPlacer`** — public interface: `getShiftTopLines`, `getLabelValues`,
`getWidthMeasurementLabelValues`, `getHeightMeasurementLabelValues`, `getLineValues`,
`getTopLayerMargin`, `getBottomLayerMargin`; factories `step(step, shiftTopLines)` and
`count(count, shiftTopLines)`. `StepVerticalAxisItemPlacer` and `CountVerticalAxisItemPlacer` are
`internal`. **Fully consumer-implementable** — this is exactly the shape of the fork's
`ListItemPlacer`.

**`AxisManager`** is `internal class` — the draw ordering of axes relative to layers, markers,
decorations and legend is not reachable. The only ordering control a consumer has is
`BaseAxis.LineDrawingOrder.{UnderLayers, OverLayers}` (ticks and axis line only).

**Decorations.** `$C/cartesian/decoration/Decoration.kt`:

```kotlin
@Immutable
public interface Decoration {
  public fun drawUnderLayers(context: CartesianDrawingContext) {}
  public fun drawOverLayers(context: CartesianDrawingContext) {}
}
```

Supplied as `decorations: List<Decoration>` to `rememberCartesianChart`. Both methods receive the
full `CartesianDrawingContext` — canvas, `layerBounds`, `layerDimensions`, `scroll`, `zoom`,
`ranges`, `model`, `extraStore`. Shipped implementations: `HorizontalLine` and `HorizontalBox`
(`$C/cartesian/decoration/`). `HorizontalLine` is a near-exact template for "a labelled line at a
y value", including off-range label clamping via `Position.Vertical.inBounds(...)`.

**This is the general-purpose draw-time seam at v3.3.1, and it is better than it looks.** A
`Decoration` runs on every frame with everything needed to compute the visible x window from
public members only:

```
fullStart = ranges.minX - layerDimensions.startPadding / layerDimensions.xSpacing * ranges.xStep
visStart  = fullStart + layoutDirectionMultiplier * scroll / layerDimensions.xSpacing * ranges.xStep
visEnd    = visStart + layerBounds.width / layerDimensions.xSpacing * ranges.xStep
```

(that is `getFullXRange` + `getVisibleXRange` from `$C/cartesian/CartesianMeasuringContext.kt`,
reproduced from public members; both upstream functions are `internal`). So a consumer-owned
`Decoration` can *observe and publish* the live window without touching library source.

**What a `Decoration` does not reach:** it is not a `CartesianLayerMarginUpdater`.
`CartesianChart.marginUpdaters()` is `this + axisManager.axisCache + marker + persistentMarkerMap.values`
— decorations are excluded. A decoration therefore cannot reserve chart margin for itself.

### 1.5 Layers

**`LineCartesianLayer`** — `$C/cartesian/layer/LineCartesianLayer.kt`

```kotlin
public open class LineCartesianLayer
protected constructor(
  protected val lineProvider: LineProvider,
  protected val pointSpacing: Dp = Defaults.POINT_SPACING.dp,
  protected val rangeProvider: CartesianLayerRangeProvider = CartesianLayerRangeProvider.auto(),
  protected val verticalAxisPosition: Axis.Position.Vertical? = null,
  protected val drawingModelInterpolator: CartesianLayerDrawingModelInterpolator<…> = …,
  protected val drawingModelKey: ExtraStore.Key<LineCartesianLayerDrawingModel>,
) : BaseCartesianLayer<LineCartesianLayerModel>()
```

plus a `public constructor` without `drawingModelKey`. `open` + `protected constructor` ⇒
subclassable.

Customisation that needs no subclass:

- **`LineProvider`** — `public fun interface`, `getLine(seriesKey, seriesIndex, extraStore): Line`.
  Per-series, extras-aware line selection.
- **`Line`** — `public open class` with public `stroke`, `pointProvider`, `interpolator`,
  `dataLabel`, `dataLabelPosition`, `dataLabelValueFormatter`, `dataLabelRotationDegrees`, and
  `protected` `fill`, `areaFill`.
- **`LineFill`** — public interface, `draw(context, path, halfLineThickness, verticalAxisPosition)`;
  factories `single(fill)`, `colorScale(...)`, `double(...)`.
- **`AreaFill`** — public interface, same shape; `single`, `colorScale`, `double`.
- **`LineStroke`** — `public sealed interface` with `thickness` and
  `apply(context, paint)`; `Continuous` and `Dashed` data classes. **Sealed — no new cases**, but
  `apply` means a `Continuous`/`Dashed` covers most needs.
- **`PointProvider`** — public interface, `getPoint(entry, seriesIndex, extraStore)`,
  `getPoint(entry, extraStore)`, `getLargestPoint(extraStore)`; factory `single(point)`.
- **`Point`** — `public data class Point(component: Component, size: Dp)`; `component` is
  `private` but `Component` itself is a public interface, so arbitrary point rendering is open.

**Interpolation — the big v3.3.1 addition:**

```kotlin
public interface Interpolator {
  public val visiblePadding: Int? get() = 0
  public fun interpolate(context: CartesianDrawingContext, path: Path,
                         points: List<Offset>, visibleIndexRange: IntRange)
  public fun getYRange(y: List<Double>): ClosedRange<Double> = y.min()..y.max()
  public companion object {
    public val Sharp: Interpolator
    public fun cubic(curvature: Float = 0.5f): Interpolator
    public fun catmullRom(alpha: Float = 0f): Interpolator
  }
}
```

`PointConnector` still exists but is `@Deprecated("Use `Interpolator`.")`, as are
`PointConnector.Sharp` and `PointConnector.cubic`. `Interpolator` is **fully public and
implementable**, it is told how much off-screen context it needs (`visiblePadding`), it is handed
the whole visible slice plus padding, and — importantly for a non-overshooting spline — it declares
its own y range via `getYRange`, which `LineCartesianLayer.updateChartRanges` folds into the chart
range. A consumer-written monotone (Fritsch–Carlson) interpolator drops straight in here with no
library change at all.

Also new/adjacent at v3.3.1: `CatmullRom.kt`, `CatmullRomInterpolator.kt`, `CubicInterpolator.kt`,
`CubicPointConnector.kt`, `PointConnectorAdapter.kt`, `ColorScale.kt`, `ColorScaleShader.kt`,
`ColorScaleScope.kt`, `LineFills.kt`, `AreaFills.kt`.

**Subclass-only seams on `LineCartesianLayer`** (all `protected`, several `open`):
`drawInternal` (`override`), `CartesianDrawingContext.updateMarkerTargets(...)` ×2 (`open`),
`CartesianDrawingContext.drawPointsAndDataLabels(...)` (`open`),
`CartesianDrawingContext.forEachPointInBounds(...)` (`open`),
`CartesianDrawingContext.collectPointsAndVisibleIndexRange(...)`, `connectPoints(...)`,
`getMaxDataLabelWidth(...)`, `resetTempData()`, `linePath`, `cacheKeyNamespace`.
`updateDimensions`, `updateChartRanges`, `prepareForTransformation`, `transform` are
`override`s of the public `CartesianLayer` interface.

**What the layer does not reach:** there is no draw-time y transform hook. `getDrawY` is a local
function inside `collectPointsAndVisibleIndexRange` and inside `forEachPointInBounds`, both
hardcoded to `pointInfoMap?.get(entry.x)?.y ?: ((entry.y - yRange.minY) / yRange.length)`. The
cheapest *supported* way to move a point vertically is to change the model, not the draw.
`LineCartesianLayerModel.Partial`/`series` is public, so "transform the y values before pushing" is
available — but it loses the ability to transform *against the currently visible window*.

**`CartesianLayer<T>`** itself (`$C/cartesian/layer/CartesianLayer.kt`) is a public interface and
`BaseCartesianLayer` a public abstract class — a consumer can write an entirely new layer type.
It will not be *drawn*, though: `CartesianChart.forEachWithLayer` dispatches with an exhaustive
`when (layer) { is ColumnCartesianLayer / LineCartesianLayer / CandlestickCartesianLayer -> …
else -> throw IllegalArgumentException("Unexpected `CartesianLayer` implementation.") }`.
It is `protected open`, so a `CartesianChart` subclass can replace the dispatch — but a plain
consumer cannot. A *subclass* of `LineCartesianLayer` is fine (the `is` check still matches).

**`CartesianLayerPadding`** (`$C/cartesian/layer/CartesianLayerPadding.kt`) is supplied via
`rememberCartesianChart(layerPadding = { extraStore -> … })` — scalable and unscalable start/end
padding, extras-aware. **This is the supported way to reserve space before the first entry.**
`MutableCartesianLayerDimensions.ensureValuesAtLeast(...)` is also public (reachable from a custom
`Axis` or `CartesianLayer`). `MutableCartesianLayerDimensions.scale(factor)` and `copyScaled` are
`internal`.

### 1.6 Chart composition

**`CartesianChart`** — `$C/cartesian/CartesianChart.kt`

```kotlin
@Stable
public open class CartesianChart internal constructor(…) : CartesianLayerMarginUpdater<CartesianChartModel> {
  protected constructor(
    vararg layers: CartesianLayer<*>,
    startAxis: …, topAxis: …, endAxis: …, bottomAxis: …,
    marker: CartesianMarker? = null,
    markerVisibilityListener: CartesianMarkerVisibilityListener? = null,
    layerPadding: ((ExtraStore) -> CartesianLayerPadding) = { CartesianLayerPadding() },
    legend: Legend<…>? = null,
    fadingEdges: FadingEdges? = null,
    decorations: List<Decoration> = emptyList(),
    persistentMarkers: (PersistentMarkerScope.(ExtraStore) -> Unit)? = null,
    getXStep: ((CartesianChartModel, Double, Double) -> Double) = { model, minX, _ -> getDefaultXStep(model, minX) },
    markerController: CartesianMarkerController = CartesianMarkerController.showOnPress(),
  )
  public val layers: List<CartesianLayer<*>>
  public val startAxis / topAxis / endAxis / bottomAxis
  public val markerController: CartesianMarkerController
  protected val markerTargets: Map<Double, List<CartesianMarker.Target>>
  public open fun getMarkerTargets(x: Double?, visibleXRange: ClosedFloatingPointRange<Double>): List<CartesianMarker.Target>
  protected open fun CartesianChartModel.forEachWithLayer(consumer: ModelAndLayerConsumer)
  protected interface ModelAndLayerConsumer
  protected inline fun <reified T : CartesianLayerModel> MutableList<CartesianLayerModel>.consume(…)
  public fun copy(…): CartesianChart   // two overloads
  public fun interface PersistentMarkerScope { /* CartesianMarker.at(x) */ }
  protected companion object { public val cacheKeyNamespace: CacheStore.KeyNamespace }
}
```

Subclassable (`protected constructor`), with exactly **two** open members: `getMarkerTargets` and
`forEachWithLayer`. `getXStep` is a lambda parameter and at v3.3.1 now receives
`(model, finalMinX, finalMaxX)` — the old `(model) -> Double` overload is deprecated.

**What it does not reach:** `draw(context)` is `internal`. The entire draw order — decorations
under, axes under, layers into an offscreen bitmap, persistent markers under, active marker under,
bitmap composite, fading edges, axes over, decorations over, persistent markers over, legend,
active marker over — is fixed and unreachable. `prepare`, `getVerticalExtras`, `updateRanges`,
`prepareForTransformation`, `transform`, `layerBounds`, `marker`, `layerPadding` and `id` are all
`internal`.

**`CartesianChartHost`** — `$C/cartesian/CartesianChartHost.kt`

```kotlin
@Composable public fun CartesianChartHost(
  chart: CartesianChart,
  modelProducer: CartesianChartModelProducer,
  modifier: Modifier = Modifier,
  scrollState: VicoScrollState = rememberVicoScrollState(),
  zoomState: VicoZoomState = rememberDefaultVicoZoomState(scrollState.scrollEnabled),
  animationSpec: AnimationSpec<Float>? = defaultCartesianDiffAnimationSpec,
  initialAnimationSpec: AnimationSpec<Float>? = animationSpec,
  chartAreaHeight: Dp = Defaults.CARTESIAN_CHART_AREA_HEIGHT.dp,
  placeholder: @Composable BoxScope.() -> Unit = {},
)
@Composable public fun CartesianChartHost(
  chart: CartesianChart, model: CartesianChartModel, modifier: Modifier = Modifier,
  scrollState: …, zoomState: …, chartAreaHeight: Dp = …,
)
```

New at v3.3.1 and relevant: `chartAreaHeight` and `initialAnimationSpec` (the old `animateIn:
Boolean` overload is deprecated), and `ChartHostBox` (`$C/common/ChartHostBox.kt`,
`internal`) whose `Modifier.chartAreaHeight` honours a *fixed* height constraint
(`if (constraints.hasFixedHeight) constraints.maxHeight else chartAreaHeight + extras`). So the
caller's own `Modifier.height(...)` now wins; there is no `heightIn(max = …)` cap any more.

`CartesianChartHostImpl` is `internal`. **There is no gesture-interception seam and no
`FlingBehavior` parameter.** The `Modifier` the caller passes is applied to the outer `Box`, so a
consumer *can* wrap the chart in its own `pointerInput`/`nestedScroll` — but only *outside*
upstream's `scrollable(...)` and tap detector, which is the wrong side for arbitrating
scroll-vs-scrub.

**`FadingEdges`** — `$C/cartesian/FadingEdges.kt` — `public open class` with `protected`
`startWidth`/`endWidth`/`visibilityThreshold`/`visibilityEasing`, but
`internal fun draw(context: CartesianDrawingContext)` — **not open, not public.** A subclass cannot
change what is drawn, only the four constructor values.

**Composition locals.** Exactly one, and it is `private`:
`$C/common/VicoTheme.kt:93` — `private val LocalVicoTheme = staticCompositionLocalOf<VicoTheme?> { null }`,
exposed through `public val vicoTheme: VicoTheme @Composable get()` and
`@Composable public fun ProvideVicoTheme(theme: VicoTheme, content: …)`. There is no
composition-local seam for scroll, ranges, marker state or anything else.

**`ExtraStore`** (`$C/common/data/ExtraStore.kt`) and `MutableExtraStore` are public with public
`Key<T>` — the general-purpose channel for consumer data through the model and into providers,
formatters, item placers, decorations and `getXStep`.

---

## 2. Patch-to-plugin map

Verdict legend: **EP** = reachable through an existing v3.3.1 extension point ·
**CONSUMER** = belongs in meApp, not the library · **PATCH** = genuinely needs library source ·
**DROP** = the need disappears at v3.3.1 · **UNCLEAR** = I could not tell from the diff.

| # | File | What the fork's patch does | Verdict | Reasoning |
|---|---|---|---|---|
| 1 | `vico/build.gradle.kts` | `group = "com.dmdbrands.lib"` | **PATCH** | Fork infrastructure. 1 line. Irreducible and harmless. |
| 2 | `vico/compose/build.gradle.kts` | swaps `publishing-convention` for `maven-publish` + a GPR repo block reading `gradle-local.properties` | **PATCH** | Fork infrastructure (~30 lines). Could be reduced by putting it in a convention plugin under `buildSrc/` (already a fork-added file, `gpr-publishing-convention.gradle.kts`) and leaving this file untouched — the module file would then need only the plugin id. Worth doing: it removes a 30-line conflict surface from a file upstream edits. |
| 3 | `cartesian/CartesianChart.kt` | (a) `visibleLabelsCount` + `calculateLabelSpacingScale` → `layerDimensions.scale(f)`; (b) `startInsetDp` → `ensureValuesAtLeast(unscalableStartPadding)`; (c) start-strip clip around axis draws; (d) full draw-order rewrite (layers composited last, marker over); (e) `drawResting` dispatch; (f) `synthesizeInterpolatedTargets`; (g) `allMarkerTargetXValues` | **PATCH** (mostly), with 2 extractions | (b) is **EP**: `rememberCartesianChart(layerPadding = { CartesianLayerPadding(unscalableStart = …) })` reserves exactly that space with no patch. (f) is **EP**: `getMarkerTargets(x, visibleXRange)` is `public open`, so a `CartesianChart` subclass can synthesise interpolated targets — *except* it cannot set `isInterpolated` (row 24) and needs `MutableLineCartesianLayerMarkerTarget` (internal); a consumer would write its own `LineCartesianLayerMarkerTarget` implementation, which is public. (a), (c), (d), (e), (g) all live inside `internal fun draw` / `internal fun prepare` and need `layerDimensions.scale` (internal) — **must stay**. This is the fork's hardest file and upstream churned it 228+/18−. |
| 4 | `cartesian/CartesianChartHost.kt` | (a) recompute ranges host-side keyed on `chart`; (b) `AnimatedYCartesianChartRanges` wrapper; (c) `ScrollAwareRangeEffect` (cache build, seed, debounce, `Animatable`); (d) per-frame `ScrollInfo` emit to providers; (e) `visibleXRanges` feed → `marker.onScrollSettled`; (f) scrub wiring + `initialMarkerX`; (g) `flingBehavior` param; (h) two-pass `prepare` for `Size.Scroll`; (i) drop `heightIn(max = CHART_HEIGHT.dp)` | **PATCH** (core), with large extractions | (i) is **DROP** — v3.3.1's `ChartHostBox`/`chartAreaHeight` already honours a fixed height. (c)+(d)+(e) are **CONSUMER**: a consumer-owned `Decoration` sees `layerBounds`, `layerDimensions`, `scroll`, `ranges` every frame and can compute the visible x window from public members alone (§1.4), then publish it to the provider and to the marker. That moves ~200 lines out of the library. What it leaves behind is (a)+(b) — the fact that **`ranges` is recomputed only on a data push** — which no extension point reaches. That is the irreducible core. (f)+(g) depend on rows 7/22. (h) depends on row 11/14. |
| 5 | `cartesian/CartesianDrawingContext.kt` | `internal fun CartesianDrawingContext.getVisibleXRange()` → `public fun … : ClosedFloatingPointRange<Double>` | **PATCH**, and the cheapest upstream PR in the set | One line, one visibility keyword, no behaviour change, no API risk. Until upstream takes it, a consumer can inline the three lines of arithmetic from public members — so this patch is *avoidable* at the cost of a copied formula. See §3. |
| 6 | `cartesian/FadingEdges.kt` | widen the fade to `max(startWidth.pixels, layerDimensions.startPadding)` (and the end likewise) | **PATCH** | `FadingEdges.draw` is `internal fun`, not `open`. A subclass can change the four constructor values but not the drawing. The patch is 10 lines and upstream has not touched the file (per BASELINE), so it grafts clean — but it is only needed because of the `startInsetDp` reservation in row 3. If row 3(b) moves to `layerPadding`, this patch still applies (the fade should still cover reserved padding), so I would keep it *and* propose it upstream: "fading edges should cover unscalable layer padding" is a correct general behaviour, not a meApp quirk. |
| 7 | `cartesian/Modifier.kt` | (a) a 180-line scrub state machine (`NONE/DECIDING/MARKER_SELECTION/MARKER_SCRUBBING/SCROLLING`) branching on `markerController is ScrubMarkerController`; (b) `scrollable(enabled = true)` always, with freezing moved into `ScrollableState`; (c) `flingBehavior` passthrough; (d) suppress `detectTapGestures` when `acceptsGestures == false` | **PATCH** | The whole file is `internal`, there is no gesture-interception seam, and the outer `Modifier` the caller supplies sits on the *wrong side* of `scrollable`. (d) is a 4-line guard that upstream would plausibly take (§3). (a) is the single largest irreducible patch in the fork and is coupled to rows 9 and 22. Upstream churned this file 121+/25− (it rewrote `detectTapGestures`, added `Interaction` consumption return values and `SnapFlingBehavior`) — expect a hard three-way merge here. |
| 8 | `cartesian/Scroll.kt` | adds `Scroll.Absolute.xBehindStartInset(x, bias)` — `Scroll.Absolute.x` minus `startPadding` | **CONSUMER** | `Scroll.Absolute` is a `public fun interface` and every parameter type is public. The fork's body is four lines of arithmetic over `context.ranges` and `layerDimensions.xSpacing`. meApp can declare `val xBehindStartInset = Scroll.Absolute { ctx, dims, _, _ -> … }` verbatim. **This patch should simply disappear.** |
| 9 | `cartesian/VicoScrollState.kt` | (a) `isScrolling` / `isUserScrolling`; (b) `visibleXRange`; (c) `xToScrollValue` / `scrollValueToX` / `xToScrollValueBehindStartInset`; (d) `getVisibleAxisLabels(itemPlacer, labelProvider)`; (e) `getInterpolatedYValues(xValues, InterpolationType)`; (f) `isScrollFrozen` + the rewritten `ScrollableState` lambda; (g) `key` param on `rememberVicoScrollState`; (h) re-apply `initialScroll` when `maxValue` changes | **Split** | (e) is **CONSUMER** outright — it reads `ctx.model` and interpolates; meApp holds the same series it pushed and can do this without the chart. (d) is **CONSUMER** for the `labelProvider` branch and **EP** for the `itemPlacer` branch (a consumer-written `HorizontalAxis.ItemPlacer` already decides these values). (h) is **DROP**: v3.3.1's `update()` now re-anchors the viewport across `minX`/`xStep` changes via `previousScrollLayout` and a `preClampValue` — upstream solved this properly and the fork's heuristic should be deleted and re-tested, not ported. (g) is a 2-line **PATCH** (or **CONSUMER** — wrap `rememberVicoScrollState` in a `key(…) { }`, which is exactly equivalent). (a), (b), (c), (f) are **PATCH**: they need `context`/`layerDimensions`/`bounds`/`scrollableState`, all `private`/`internal`. (a)+(b)+(c) are the strongest upstream-PR candidates in the file (§3). |
| 10 | `axis/AxisManager.kt` | adds `drawStartAxisLine(context)` and `drawMarkerDecorations(context)` | **PATCH**, but **consequential, not primary** | `AxisManager` is `internal class`. Both methods exist only to serve the start-strip clip (row 3c) and the goal-pill placement (row 14). If rows 3 and 14 are reduced, this file stops being patched at all. Treat it as a dependent, not a target. |
| 11 | `axis/BaseAxis.kt` | adds `Size.Scroll(value, isLabelsScrollable)` to the `sealed class Size` | **PATCH** | `Size` is `sealed`; a consumer cannot add a case. 13 lines. Whether the *behaviour* it enables is needed at all is a question for the scroll-axis dossier — upstream's `Size.Auto(min, max)` and `Size.Fixed` plus a `VerticalAxis` subclass overriding `getWidth`/`updateHorizontalLayerMargins` may cover it without a new `Size`. **UNCLEAR** from the diff alone; worth one focused experiment. |
| 12 | `axis/HorizontalAxis.kt` | (a) `Separators(values, line)` + `drawSeparators`; (b) `LabelVerticalMode` {Inside,Center,Outside}; (c) `labelHorizontalPosition`; (d) clip by item-placer margins instead of `clipPath`+`getLineLeft/Right`; (e) drop per-label `maxWidth`; (f) suppress guidelines at `isChartEdge`; (g) `updateLayerDimensions` → no-op; (h) height excludes `lineThickness` for Top; (i) `Size.Scroll` case | **EP via subclass** (a–f), **PATCH** (g, i) | `HorizontalAxis` is `open` with a `protected constructor`, and `drawUnderLayers`/`drawOverLayers`/`drawGuidelines`/`getHeight` are all overridable. A consumer `DmdHorizontalAxis : HorizontalAxis<Bottom>` can own (a)–(f) and (h) entirely. The cost is honest and should be stated: overriding `drawUnderLayers` means **copying ~90 lines of upstream drawing code** into meApp, because `internalGetFullXRange`, `getVisibleXRange()`, `getLineLeft/Right` and `clipPath` are not reachable. That is still strictly better than a patch — a consumer copy fails to *compile* on an upgrade rather than silently mis-merging, and it never needs re-applying. (g) disabling `updateLayerDimensions` cannot be done from a subclass in the way the fork does it *and* keep upstream's extreme-label padding — but it may be unnecessary now: v3.3.1's new `aligned(shiftExtremeLabels = true)` anchors the first/last labels inward "without shrinking the plot area", which is what (g) was reaching for. **Try `shiftExtremeLabels` before porting (g).** |
| 13 | `axis/HorizontalAxisItemPlacers.kt` | makes the visible window half-open at the end (`endEpsilon`), in the file-private `getLabelValues` helper and in `SegmentedHorizontalAxisItemPlacer.getLineValues`; also relaxes the `fullXRange` bound tests | **EP / CONSUMER** | `HorizontalAxis.ItemPlacer` is a fully public interface; the three bundled placers are `internal`, so this cannot be done by subclassing — but it can be done by **replacing**. The fork already owns `ListItemPlacer` for the vertical axis; the same move applies here. A consumer `WeekWindowItemPlacer : HorizontalAxis.ItemPlacer` implements the ten members with the half-open rule built in. ~120 lines of consumer code replacing a patch to a file upstream will keep editing. **Strong conversion.** |
| 14 | `axis/VerticalAxis.kt` | (a) `MarkerDecoration(y, markerComponent, label, …)` + `drawMarkerDecoration` + gutter widening in `getWidth`; (b) `Size.Scroll` handling + `updateScrollState` + `effectiveScroll` + clip; (c) labels centred on `bounds.center.x` instead of `textHorizontalPosition`; (d) `chartEdgeX()` + `drawLineOnly`; (e) `yRange.length == 0.0` guards | **EP via subclass** (a, c, e), **PATCH** (b, d) | (a) is the strongest single conversion available and has an upstream template: `HorizontalLine` (`$C/cartesian/decoration/HorizontalLine.kt`) is already "a labelled line at a y value with off-range clamping". But a `Decoration` cannot widen the axis gutter (§1.4), so the right home is a **`VerticalAxis` subclass** overriding `protected open fun getWidth(context, freeHeight)` (to `max(markerWidth, super.getWidth(...))`) and `drawOverLayers` (to add the pill). Both are open; `markerDecoration` becomes a constructor property of the subclass. (c) and (e) likewise fit a subclass (`drawLabel` is `protected open`). (b) needs `Size.Scroll` (row 11) and host cooperation (row 4h) — **PATCH**. (d) exists to serve the start-strip clip (row 3c). |
| 15 | `data/CartesianChartModel.kt` | renames `PreviewContext` → `InlineUpdateContext` in one line | **DROP or PATCH (1 line)** | Pure consequence of row 16. If `InlineUpdateContext` stays, this one-liner stays. If the test-dispatch need is met another way (see row 16), delete it. |
| 16 | `data/CartesianChartModelProducer.kt` | (a) `isReady: StateFlow<Boolean>` latch; (b) `runTransaction(animate, block)` + `Transaction(animate)` + non-animated `handleUpdate` path; (c) `PreviewContext` → `public object InlineUpdateContext` for tests | **CONSUMER** (a), **PATCH** (b), **PATCH-or-test-redesign** (c) | (a) is plainly consumer code: `runTransaction` is `suspend` and returns when the update is complete, so meApp can set its own `MutableStateFlow(true)` on the first successful return. Delete this from the library. (b) has a *partial* consumer equivalent — `CartesianChartHost(animationSpec = if (shouldAnimate) spec else null)` and `initialAnimationSpec = null` — but that is host-scoped and racy against the transaction, so a per-transaction flag is not reachable. This is a reasonable upstream PR (§3). (c) exists because a virtual-time test scheduler cannot drain `Dispatchers.Default`; the fork's own `MutableCartesianChartRangesTest` is the consumer. If the fork's tests move to the consumer app or use a real-time `runTest` with awaits, this disappears. **UNCLEAR** without reading the fork's test harness. |
| 17 | `data/CartesianChartRanges.kt` | adds `getTargetYRange(axisPosition): YRange = getYRange(axisPosition)` (defaulted) | **PATCH** | Six lines on a public interface with a default body — source-compatible, binary-compatible for implementors. It exists solely so a draw-time y transform can map against the range the animation is *heading for* rather than the one it is leaving. It is meaningless without rows 4(b) and 20(yTransform). Good upstream PR *if* the transform need survives; otherwise it dies with them. |
| 18 | `data/MutableCartesianChartRanges.kt` | (a) `tryUpdateX(minX, maxX)`; (b) `yRanges[null] ?: NeutralYRange` instead of `getValue(null)` | **PATCH**, and (b) is a real upstream bug fix | (b) is correct independently of the fork: `yRanges.getValue(null)` throws `NoSuchElementException` if no layer ever contributed a y range, and at v3.3.1 that is still the code in both `getYRange` and `toImmutable()`. (a) exists only to serve the `yTransform` layer (row 20). ~30 lines; upstream never touched the file, so it grafts clean. Propose (b) upstream on its own. |
| 19 | `layer/CartesianLayerMargins.kt` | (a) makes `ensureValuesAtLeast` **halve** `top`/`bottom` ("v3 parity"); (b) adds `ensureVerticalValuesAtLeast` to get the un-halved behaviour back | **DROP — entirely self-inflicted** | At v3.3.1 `ensureValuesAtLeast` stores `top`/`bottom` **as given**: `this.top = this.top.coerceAtLeast(top)`. The halving is the fork's own change; `ensureVerticalValuesAtLeast` exists only to undo it. Delete both halves, keep upstream's method, and audit the ~3 call sites that were compensating (`DefaultCartesianMarker.updateLayerMargins` passes `reserved` through the vertical variant — it should just call `ensureValuesAtLeast`). **This is the clearest "stop patching" in the set and it costs nothing.** |
| 20 | `layer/LineCartesianLayer.kt` | (a) `yTransform` + a ~90-line fade/caching state machine keyed on series/target-range/settle-epoch; (b) `markerTargetsEnabled`; (c) `alwaysUseLiveRange` (bypass the drawing-model cache when the provider is scroll-aware); (d) `Interpolator.monotone()`; (e) clip points to `layerBounds` + skip a point whose centre is outside; (f) `internalRangeProvider` / `internalVerticalAxisPosition` accessors; (g) drop `maxPointSize.half` from unscalable padding; (h) `tryUpdateX` when `yTransform != null` | **Split** | (d) is **CONSUMER** and the single cleanest win: v3.3.1's `public interface Interpolator` takes `interpolate(context, path, points, visibleIndexRange)` + `visiblePadding` + `getYRange`, so the fork's `MonotoneInterpolator` becomes a meApp class with **zero** library change. Delete `monotone()` from the companion and let meApp hold the object. (b) and (e) are **EP via subclass**: `updateMarkerTargets` ×2 and `drawPointsAndDataLabels` are all `protected open`. (f) is **CONSUMER** — the consumer constructs the provider and therefore already holds it; the accessor exists only because the patched host reaches *back* into the layer. (a), (c), (h) are the `yTransform` cluster — **PATCH**, and the most expensive thing in the fork after row 7, because the only y hook (`getDrawY`) is a local function in two `protected` methods. A subclass overriding **both** `collectPointsAndVisibleIndexRange` and `forEachPointInBounds` could carry it, at the cost of copying ~80 lines; worth costing against the patch. (g) is a behaviour change to padding that may now conflict with v3.3.1's own point-size handling — **UNCLEAR**, needs a visual check. |
| 21 | `marker/CartesianMarker.kt` | adds `drawResting(context)` and `onScrollSettled(visibleXRange)`, both with empty default bodies | **PATCH** | 21 lines on a public interface, both defaulted — the least invasive patch in the fork, and a clean upstream PR (§3). `drawResting` needs a call site in `CartesianChart.draw` (internal) and `onScrollSettled` needs one in the host (internal), so neither is reachable from a consumer. Note the *alternative*: a consumer `Decoration` can draw resting content and knows the window — what it cannot do is **share the marker's label line**, i.e. know not to draw while a marker is up. A consumer can approximate that with `CartesianMarkerVisibilityListener` (public) flipping a flag the decoration reads. That is a genuine conversion and it is worth testing before keeping the patch. |
| 22 | `marker/CartesianMarkerController.kt` | adds `acceptsGestures: Boolean get() = true` and `rememberNonInteractive()` | **PATCH** (36 lines) | Defaulted property on a public `fun interface` — source- and binary-safe. Needed because `Modifier.pointerInput` attaches `detectTapGestures` whenever `chart.marker != null`, swallowing taps meant for a surrounding card. There is **no** consumer workaround: the marker cannot be null (it draws the resting label) and the modifier is internal. Pair with row 7(d). Strong upstream PR — "a controller that never marks anything should not claim the gesture" is a general bug, not a meApp need. |
| 23 | `marker/DefaultCartesianMarker.kt` | makes `label` nullable; adds `contentPadding`, `yLabelCallback`, `horizontalLabelPosition`, `horizontalLabelFormatter`, `restingLabel`, `restingLabelFormatter`, `labelGap`; `drawHorizontalCrosshair` + its label; `drawResting`/`restingLabelAnchor`; guideline top/bottom from label position; skips indicators for `isInterpolated` points; margin reservation via `ensureVerticalValuesAtLeast` | **CONSUMER (subclass)** — the largest single extraction available | `DefaultCartesianMarker` is `public open class` with a public constructor, `protected` fields, `override`-able `drawOverLayers`/`updateLayerMargins`, `protected open drawIndicator`, and `protected drawLabel`/`drawGuideline`/`overrideXPositionToFit`. A consumer `DmdMarker : DefaultCartesianMarker(...)` can own the crosshair, the gap, the content padding, the y-callback, the guideline extents and the margin reservation outright. Three things escape it: `drawResting` and `onScrollSettled` need row 21; `isInterpolated` needs row 24; `ensureVerticalValuesAtLeast` is **DROP** (row 19 — just call `ensureValuesAtLeast`). Upstream churned this file only 39+/25−, so the patch would graft — but ~250 of the fork's ~290 added lines do not need to be in the library at all. |
| 24 | `marker/LineCartesianLayerMarkerTarget.kt` | adds `isInterpolated: Boolean = false` to `data class Point` | **PATCH (2 lines)** or **CONSUMER** | `LineCartesianLayerMarkerTarget` is a *public interface* with `points: List<Point>`, and `Point` is a public data class — but it is a `data class`, so a consumer cannot extend it. However, a consumer that synthesises its own targets (row 3f) could instead carry the flag **out of band**: emit synthetic targets at x values the consumer already knows are synthetic, and have the consumer's marker test membership of that set. Clunky but patch-free. Otherwise: 2-line defaulted parameter, trivially upstreamable. |
| 25 | `common/Defaults.kt` | `AXIS_LABEL_VERTICAL_PADDING: 4 → 2`; adds `Animation.RANGE_ANIM_DURATION = 300` | **CONSUMER** | Both objects are `internal`, so the patch is the only way to change them *in place* — but neither needs changing. `rememberTextComponent(padding = Insets(...))` is public and the axis takes a `label: TextComponent?`, so meApp sets its own label padding; `RANGE_ANIM_DURATION` is a fork-local constant whose only readers are fork-added files and which belongs wherever the animation is configured. **Delete this patch.** |
| 26 | `common/component/TextComponent.kt` | in `getBounds(..., pad = true)`, `append(text)` → `append(measuredText)` | **PATCH — and it is an upstream bug that still exists at v3.3.1** | At `$C/common/component/TextComponent.kt:287` v3.3.1 still has `append(text)` inside `buildAnnotatedString`, where `text: CharSequence?`. `Appendable.append(null)` writes the literal `"null"`, so measuring with no text reserves four characters of width — and `pad` defaults to `text == null`, i.e. this path is taken by **every `Size.Auto` axis sizing its gutter**. One-character fix, no API change. Send it upstream today; keep the patch until it lands. |

### Tally

- **Could stop being patches: 9 of 26** — rows 8, 13, 15 (conditional), 16a, 19, 20d (+20b, 20e, 20f), 23 (bulk), 25, and 9h/4i as outright deletions.
  Counting strictly by *file* — a file that stops being modified at all — the honest number is
  **7**: `Scroll.kt` (8), `HorizontalAxisItemPlacers.kt` (13), `CartesianLayerMargins.kt` (19),
  `DefaultCartesianMarker.kt` (23, *if* rows 21 and 24 land upstream), `Defaults.kt` (25),
  `CartesianChartModel.kt` (15, *if* row 16c is resolved), and `vico/compose/build.gradle.kts` (2,
  by moving the block into the existing `buildSrc` convention plugin).
- **Substantially reduced but still patched: 6** — rows 3, 9, 12, 14, 20, 4.
- **Irreducible: 11** — rows 1, 5, 6, 7, 10, 11, 16b, 17, 18, 21, 22, 24, 26 (several of which are
  <40 lines and are better framed as upstream PRs than as patches).

Two patches should be **deleted rather than ported** (row 19 entirely, row 9h) and one should be
**retested before porting** (row 12g, against v3.3.1's new `shiftExtremeLabels`).

### The three strongest conversions

**1. `MonotoneInterpolator` → a meApp class, via `LineCartesianLayer.Interpolator`.**
v3.3.1 introduced a public, fully-specified interpolation interface the fork point did not have:
`interpolate(context, path, points, visibleIndexRange)` + `visiblePadding: Int?` +
`getYRange(y): ClosedRange<Double>`. The last member matters — it is how a spline declares that it
overshoots, and `LineCartesianLayer.updateChartRanges` folds it into the chart range. The fork's
monotone spline neither overshoots nor needs padding beyond its neighbours, so it is a direct fit.
Cost: delete one companion function (`Interpolator.Companion.monotone()`), move one file to meApp.
Library change: **zero**. This is the cleanest win in the audit.

**2. `DefaultCartesianMarker`'s ~290 added lines → a meApp subclass.**
`DefaultCartesianMarker` is `public open class`, its constructor is public, its fields are
`protected`, `drawOverLayers` and `updateLayerMargins` are `override`-able, `drawIndicator` is
`protected open`, and `drawLabel`/`drawGuideline`/`overrideXPositionToFit` are `protected`.
Everything the fork added — the horizontal crosshair and its label, `labelGap`, `contentPadding`,
the y-value callback, the guideline's top/bottom derived from `labelPosition`, the margin
reservation — is reachable from a subclass. Only three hooks escape, and two of them are 21 lines
of defaulted interface members (row 21) and a 2-line defaulted data-class parameter (row 24) that
are themselves good upstream PRs. Cost: a new meApp class; library shrinks by the largest single
block of marker code in the fork.

**3. The scroll-window feed → a meApp `Decoration` (and with it, most of `CartesianChartHost`).**
The fork drives `ScrollAwareRangeProvider` by emitting a `ScrollInfo` from inside the patched
`Canvas` block of `CartesianChartHostImpl`. It does not need to. A `Decoration` is handed the
complete `CartesianDrawingContext` on every frame — `layerBounds`, `layerDimensions` (with public
`xSpacing`/`startPadding`/`endPadding`), `scroll`, `zoom`, `ranges`, `model`, `extraStore` — which
is precisely the input set of the internal `getVisibleXRange()`, and the three-line formula is
reproducible from public members. A consumer `WindowProbe : Decoration` can publish the live window
to the provider and to the marker with **no library change**, taking roughly 200 lines of
`ScrollAwareRangeEffect` + the per-frame emit + the `visibleXRanges` flow out of
`CartesianChartHost.kt` — the file upstream churned 196+/94− and the one most expensive to
re-merge. Be clear about what this does *not* solve: it does not make the chart *re-ask* the range
provider. That is row 4(a)/(b) and it stays a patch (or becomes sealed door **S1** below).

---

## 3. Sealed doors

Things a consumer plainly needs that v3.3.1 keeps shut. Ordered by (value × cheapness).
Each is a candidate upstream PR; several are one line.

**S1 — Ranges are recomputed only on a data push.**
`CartesianChart.updateRanges` is `internal`; the only callers are the producer's `collectAsState`
(per push) and the model-overload's `remember(chart, model)`. A `CartesianLayerRangeProvider` whose
answer depends on scroll, a settled window, or an animation clock can never be re-asked, and
`CartesianChartHost` takes no `ranges` parameter, so a consumer cannot supply its own
`CartesianChartRanges` either.
*Ask upstream for:* either a `ranges: CartesianChartRanges?` override parameter on
`CartesianChartHost`, or an explicit `CartesianChartModelProducer`/host invalidation hook
("recompute ranges without a model push"). **This is the root cause of the fork's two largest
patches** (rows 3 and 4) and the one door whose opening would change the migration's shape.

**S2 — `CartesianDrawingContext.getVisibleXRange()` is `internal`.**
`$C/cartesian/CartesianDrawingContext.kt`, and the underlying
`CartesianMeasuringContext.getVisibleXRange(layerDimensions, layerBounds, scroll)` and
`getFullXRange(layerDimensions)` in `$C/cartesian/CartesianMeasuringContext.kt`. Every consumer
extension that draws — a `Decoration`, a `CartesianMarker`, an `ItemPlacer`, a `LineCartesianLayer`
subclass — needs to know what window it is drawing. The arithmetic is three lines over public
members, so the only thing `internal` buys is a copied formula that drifts.
*Ask upstream for:* make all three `public`. **One keyword each, zero behaviour change.**

**S3 — `TextComponent.getBounds(..., pad = true)` appends `"null"`.**
`$C/common/component/TextComponent.kt:287` — `append(text)` where `text: CharSequence?`, inside
`buildAnnotatedString`, two lines after `var measuredText = text ?: ""`. Since
`pad: Boolean = text == null`, this is the *default* path for measurement-without-text, which is
how every `Size.Auto` axis computes its gutter width. The gutter is four characters too wide.
*Ask upstream for:* `append(measuredText)`. **One token. Send this one first — it is a pure bug fix
with no design argument attached.**

**S4 — `MutableCartesianChartRanges.getYRange` throws when no layer offered a y range.**
Both `getYRange` and the `toImmutable()` anonymous object end in `?: yRanges.getValue(null)`.
Reachable whenever every layer in a chart declines to contribute a y range.
*Ask upstream for:* a neutral fallback (`?: yRanges[null] ?: SomeNeutralRange`) instead of a throw.
**~4 lines, defensible on its own merits.** (The fork's companion `tryUpdateX` is a separate,
weaker ask that only makes sense alongside a draw-time y transform.)

**S5 — A `CartesianMarkerController` cannot decline the gesture.**
`Modifier.pointerInput` attaches `detectTapGestures` iff `onInteraction != null`, which is iff
`chart.marker != null`. A chart that displays a marker but never responds to touch still eats the
tap, breaking a tappable card around it. No consumer workaround exists (the modifier is `internal`,
and the marker cannot be dropped if it draws anything).
*Ask upstream for:* `val acceptsGestures: Boolean get() = true` on `CartesianMarkerController`, and
a 4-line guard in `Modifier.pointerInput`. Defaulted property on a `fun interface` — source- and
binary-compatible.

**S6 — `VicoScrollState` exposes pixels but not meaning.**
`value` and `maxValue` are public and observable; `context`, `layerDimensions` and `bounds` are
`private`, and `scrollableState` is `internal`. So from outside, a consumer can know the chart is
scrolled 437 px and cannot know to *what*, nor whether a scroll is in progress.
*Ask upstream for:* `public val visibleXRange: ClosedFloatingPointRange<Double>?`,
`public val isScrollInProgress: Boolean`, and a pair of `x ↔ scrollValue` conversions. These are
~25 lines of additive public API over state the class already holds, and they are what make
"observe the chart from the ViewModel" possible without a `Decoration` side-channel.

**S7 — No `FlingBehavior` seam.**
`CartesianChartHost` has no `flingBehavior` parameter; `scrollable(...)` is installed inside
`internal fun Modifier.pointerInput`, and the only choice is upstream's `SnapFlingBehavior`, taken
iff `xSnapStep != null`. v3.3.1's `xSnapStep` + `snapAnimationSpec` + `getSnapTarget` cover
*uniform-step* snapping only (`startPadding + round((v - startPadding) / windowPx) * windowPx`,
with `0f` and `maxValue` as candidates). A consumer wanting velocity-aware, window-aware or
non-uniform snapping has no entry.
*Ask upstream for:* `flingBehavior: FlingBehavior? = null` on `CartesianChartHost`, passed through.
**Before filing this, test whether `xSnapStep` alone is enough** — if the fork's windows are
uniform in x, upstream's new snap may retire `SnapFlingBehavior.kt` and part of row 7 outright.
*(I could not determine from the diff whether the fork's windows are uniform. Flagging as the
single highest-value unknown in this dossier.)*

**S8 — `CartesianMarker` has no resting / settle callbacks.**
`drawUnderLayers` and `drawOverLayers` fire only when targets exist. A marker that owns a label
line outside the plot has no way to draw on it while nothing is marked, and nothing tells it when
the scroll has come to rest.
*Ask upstream for:* `drawResting(context)` and `onScrollSettled(visibleXRange)`, both with empty
default bodies (21 lines, fully compatible). Call sites are `CartesianChart.draw` and
`CartesianChartHostImpl` — both internal, so this cannot be done consumer-side.
*Partial workaround that should be tried first:* a consumer `Decoration` + a
`CartesianMarkerVisibilityListener` (both public) can draw resting content and suppress it while a
marker is shown.

**S9 — `FadingEdges.draw` is `internal`, not `protected open`.**
`$C/cartesian/FadingEdges.kt:77`. The class is `public open` and its four fields are `protected`,
so the openness is already half-granted; only the draw is shut.
*Ask upstream for:* `protected open fun draw(...)`, or — better, since the fork's change is
generally correct — make the fade width `max(startWidth.pixels, layerDimensions.startPadding)`
upstream, so a chart that reserves unscalable padding fades over it.

**S10 — `BaseAxis.Size` is `sealed`; `MutableCartesianLayerDimensions.scale` is `internal`.**
Two separate closures with the same consequence: a consumer cannot introduce a new axis sizing
*mode*, and cannot rescale `xSpacing` to fit a requested number of visible labels. Together they
are what force rows 3(a), 11, 12(i) and 14(b).
*Ask upstream for:* lower priority than the above, and I would not lead with it — `Size` being
sealed is a deliberate design choice and `scale` is measurement-phase internals. The honest
framing to upstream is the *use case* ("fit exactly N labels in the viewport"), not the two
keywords.

**S11 — `markerSeriesIndex` is `@RestrictTo(LIBRARY_GROUP)`.**
`$C/cartesian/CartesianMeasuringContext.kt:59`. A consumer extension that needs to know which
series the marker narrowed to — a custom marker, a custom layer — is formally outside the contract
even though the symbol is `public`. Low cost, low urgency; note it so nobody is surprised by a lint
error.

---

### Two things I could not determine

- **Row 16c / row 15 (`InlineUpdateContext`).** Whether the fork's tests genuinely require inline
  dispatch, or whether they could be rewritten to await `runTransaction`'s suspension. Resolving
  this one way removes two patches; I did not read the fork's test harness.
- **Row 20g** (dropping `maxPointSize.half` from unscalable padding) and **row 12h** (excluding
  `lineThickness` from a Top axis's height). Both are visual-parity adjustments whose rationale is
  not in the diff and whose correctness against v3.3.1's changed measurement code I cannot assert
  from reading alone. They need a screenshot comparison, not an argument.
