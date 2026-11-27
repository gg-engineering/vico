# S1-A — Scroll, scroll state, and dynamic Y/X range

Cluster dossier for the v3.3.1 migration. Describes **what we built and why**, from the code at
`HEAD` (`MOB-2953-week-range-spans-eight-days`) against `FORK_POINT = e67e49520`.
It deliberately says nothing about what v3.3.1 contains — another agent owns that.

All library paths below are relative to
`vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/` unless stated.
All meApp paths are under `/Users/selvakumar/Projects/meApp-bug/Android/app/src/`.

Commits in this cluster: 30 (`e0b886359` … `e6c766cb8`). Six carry the undocumented
`MOB-2953:` / `MOB-3494:` prefixes and are reverse-engineered below from the diff and the
commit bodies.

---

## Summary table

| id | name | files | conflict risk | undocumented | meApp-visible |
|---|---|---|---|---|---|
| S1A-01 | Scroll-aware Y range provider (core) | `cartesian/data/ScrollAwareRangeProvider.kt` (NEW) | low (new file) / **high** via host | partly | **yes** |
| S1A-02 | Window travels inside the model (`Window` + `WindowKey` in `ExtraStore`) | `ScrollAwareRangeProvider.kt` 56–96, 189–208, 340–363 | low (new file) | **yes** (MOB-2953) | **yes** |
| S1A-03 | Provider derives its own opening range (`seedFromWindow`, cache/window guards) | `ScrollAwareRangeProvider.kt` 123–179, 210–244 | **high** (host call order) | **yes** (MOB-3494) | **yes** (indirect) |
| S1A-04 | `WindowSettleSignal` / `settleEpoch` | `cartesian/data/WindowSettleSignal.kt` (NEW), `ScrollAwareRangeProvider.kt` 145–146 | low (new file) / medium via host+layer | **yes** (MOB-3494) | **yes** |
| S1A-05 | Snap fling behaviour | `cartesian/SnapFlingBehavior.kt` (NEW) | low (new file) / medium via `Modifier.kt` | partly (MOB-2953 trimmed it) | **yes** |
| S1A-06 | Scroll ⇄ X geometry helpers + `visibleXRange` | `cartesian/VicoScrollState.kt` 68–111 | **high** | partly | **yes** |
| S1A-07 | Scrub freeze: `isScrollFrozen`, `isScrolling`, `isUserScrolling` | `VicoScrollState.kt` 54–65, 195–221 | **high** | no | **yes** |
| S1A-08 | Initial-scroll re-apply + `key` on `rememberVicoScrollState` | `VicoScrollState.kt` 50, 298–312, 380–413 | **high** | no | **yes** |
| S1A-09 | Axis-label and interpolated-Y read-out (`drawingContext`, `getVisibleAxisLabels`, `getInterpolatedYValues`) | `VicoScrollState.kt` 52, 113–193 | **high** | no | **yes** |
| S1A-10 | `Scroll.Absolute.xBehindStartInset` | `cartesian/Scroll.kt` 58–77 | low (upstream untouched) | **yes** (MOB-2953) | **yes** |
| S1A-11 | Fading edges sized from the layer's own start/end padding | `cartesian/FadingEdges.kt` 82–87, 157–167 | low (upstream untouched) | **yes** (MOB-2953) | **yes** |
| S1A-12 | `CartesianChartRanges.getTargetYRange` | `cartesian/data/CartesianChartRanges.kt` 40–45 | low (upstream untouched) | no | no (library-internal contract) |
| S1A-13 | `MutableCartesianChartRanges.tryUpdateX` + `NeutralYRange` | `cartesian/data/MutableCartesianChartRanges.kt` 40–55, 104–119, 125–129; `commonTest/.../MutableCartesianChartRangesTest.kt` (NEW) | low (upstream untouched) | **yes** (MOB-3494) | no (library-internal) |

"meApp-visible" = at least one meApp file names the symbol directly.

---

## S1A-01 — Scroll-aware Y range provider (core)

**What it does.** A `CartesianLayerRangeProvider` that answers "what Y range should the chart draw
in?" from the data points currently on screen, instead of from the whole dataset. It keeps the
entries sorted per series, finds the visible slice by binary search, hands that slice to a caller
callback, and the callback returns both the range to draw and the tick values to letter the axis
with. The result is animated into place by the host, so scrolling to a flatter stretch of data
zooms the Y axis in rather than leaving the line as a hairline near the bottom.

**Why we needed it.** The product charts (weight, blood pressure, baby growth) show a scrolling
window over a long history. With a fixed full-dataset Y range, a week of readings that varies by
2 kg is drawn as a flat line inside a 60 kg axis. iOS already scaled the axis to the visible
window; this is the Android equivalent. It also lets meApp own the "nice numbers" rounding of the
axis, which is product-specific and does not belong in a chart library.

**Commits.**
- `e0b886359` Feature-1: Add ScrollAwareRangeProvider for dynamic Y-axis range on scroll
- `bc6a1a922` Feature-1: Refactor ScrollAwareRangeProvider — binary search + entry-based callback
- `01ad34f5d` Feature-4: Add SnapFlingBehavior + fix ScrollAwareRangeProvider X range
- `6e9849068` feat(scroll-range): Add X range, modelStructureKey, replay to ScrollAwareRangeProvider
- `1d951af36`, `7917f5b69` fix(scroll-range): Merge all series in buildCache for multi-line Y range
- `4e5f7fb1b` feat(scroll-range): Multi-series buildCache + per-series visible entries callback
- `6ef5fdcc7` fix(scroll-range): Deduplicate providers + use RANGE_ANIM_DURATION
- `eaefd2bdf` feat(marker-decoration): Fix outside-range positioning + remove target range
- `2ddae34f4` feat: markerTargetsEnabled flag + scrollable always-on for scrub parent blocking
- `0a35ee586` feat: yTransform target-based caching + fade correction (v4.0.26)
- `5a8f1567d` MA-3490: Rename initialMinY/initialMaxY to seedMinY/seedMaxY

**Files and hunks.** `cartesian/data/ScrollAwareRangeProvider.kt`, new, 363 lines.
- 42–48 class declaration, implements `CartesianLayerRangeProvider, WindowSettleSignal`.
- 102–143 mutable state: `allSeriesEntries`, `sortedXValues`, `currentMinY/MaxY`,
  `targetMinY/MaxY`, `xRangeMin/Max`, `seedMinY/MaxY`, `isCacheReady`, `cachedSeries`,
  `cachedWindow`, `currentTicks`.
- 181–187 `scrollUpdates` — `MutableSharedFlow<ScrollInfo>(replay = 1, extraBufferCapacity = 10)`,
  the channel the Canvas pushes the measured window into.
- 189–208 the four range getters (see S1A-02 for the precedence rules).
- 210–244 `buildCache(series)` — per-series sort by X plus a merged sorted unique X array.
- 246–286 `computeVisibleEntries(info)` — binary search both window bounds, pad by
  `paddingEntries` on each side, short-circuit when the padded index pair is unchanged.
- 288–306 `computeDisplayRange` — calls the consumer callback inside `try/catch`, and rejects a
  zero-length, NaN or infinite range by returning `null`.
- 308–314 `internal data class ScrollInfo`.
- 321–329 file-private `DoubleArray.binarySearchInsertionPoint`.
- 340–363 `rememberScrollAwareRangeProvider`, which holds the callback through
  `rememberUpdatedState` so a recomposed lambda is still seen by a remembered provider.

**Public API added or changed.**
```kotlin
public class ScrollAwareRangeProvider(
  private val paddingEntries: Int = 1,
  internal val debounceMs: Long = 100L,
  internal val animDurationMs: Int = Animation.RANGE_ANIM_DURATION,
  private val windowKey: String? = null,
  private val onVisibleEntries: (
    visibleEntries: List<List<Pair<Double, Double>>>,
    visibleXRange: ClosedRange<Double>,
  ) -> Pair<ClosedRange<Double>, List<Double>>,
) : CartesianLayerRangeProvider, WindowSettleSignal

public var ScrollAwareRangeProvider.targetMinY: Double   // internal set
public var ScrollAwareRangeProvider.targetMaxY: Double   // internal set
public var ScrollAwareRangeProvider.xRangeMin: Double
public var ScrollAwareRangeProvider.xRangeMax: Double
public var ScrollAwareRangeProvider.seedMinY: Double
public var ScrollAwareRangeProvider.seedMaxY: Double
public var ScrollAwareRangeProvider.currentTicks: List<Double>   // internal set
public override var settleEpoch: Int                              // internal set

public override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double
public override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double
public override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double
public override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double

@Composable
public fun rememberScrollAwareRangeProvider(
  paddingEntries: Int = 1,
  debounceMs: Long = 100L,
  animDurationMs: Int = 300,
  minX: Double = Double.NaN,
  maxX: Double = Double.NaN,
  seedMinY: Double = Double.NaN,
  seedMaxY: Double = Double.NaN,
  windowKey: String? = null,
  onVisibleEntries: (
    visibleEntries: List<List<Pair<Double, Double>>>,
    visibleXRange: ClosedRange<Double>,
  ) -> Pair<ClosedRange<Double>, List<Double>>,
): ScrollAwareRangeProvider
```
Internal surface the host reaches for (not public, but it is still contract): `scrollUpdates`,
`buildCache`, `computeVisibleEntries`, `computeDisplayRange`, `seedFromWindow`, `openingWindow`,
`isCacheReady`, `cachedSeries`, `cachedWindow`, `currentMinY`, `currentMaxY`, `debounceMs`,
`animDurationMs`, `ScrollInfo`.

**Coupling to upstream internals.**
1. The provider cannot measure the window itself — only the draw pass knows `xSpacing`,
   `layerBounds` and `scroll`. `CartesianChartHostImpl` therefore reaches into the provider from
   inside the `Canvas` block (`CartesianChartHost.kt:491–512`) and calls
   `provider.scrollUpdates.tryEmit(...)` with a `ScrollInfo` built from
   `measuringContext.getVisibleXRange(layerDimensions, chart.layerBounds, scrollState.value)`.
   That emission is deduplicated against three remembered scalars
   (`lastEmittedScroll/XSpacing/ChartWidth`) that are also ours.
2. The providers are discovered by **downcasting upstream layers**:
   `(layer as? LineCartesianLayer)?.internalRangeProvider as? ScrollAwareRangeProvider`, in three
   separate places (`CartesianChartHost.kt:105–107`, `146–150`, `308–312`, `573–581`).
   `internalRangeProvider` is an accessor we added to `LineCartesianLayer` (line 121) purely to
   make the private `rangeProvider` readable from the host.
3. The animated result is injected by wrapping the ranges, not by the normal
   `updateRanges` path: `AnimatedYCartesianChartRanges` (`CartesianChartHost.kt:696–722`)
   decorates the immutable ranges and overrides `getYRange`/`getTargetYRange`.
4. `LineCartesianLayer` must skip its own cached drawing model when this provider is in use
   (`LineCartesianLayer.kt:612–614`: `if (rangeProvider is ScrollAwareRangeProvider || …) null`),
   otherwise the cached Y positions fight the per-frame range.
5. `ScrollAwareRangeEffect` (`CartesianChartHost.kt:562–690`) drives the whole animation and
   writes `provider.currentMinY/currentMaxY/currentTicks/settleEpoch` directly. The provider is
   mutable shared state between a composable effect and a draw-time getter; nothing enforces the
   order beyond the code as written.

**Could it be an extension point, or live in meApp instead?** Partly, and the split is clean:

- The *policy* is already in meApp — the `onVisibleEntries` callback does the nice-number scaling
  (`features/common/components/chart/ProductChart.kt:164–192`). Nothing needs to move.
- The *mechanism* is one need: "tell me the visible X window after the scroll settles, and let me
  answer with a Y range and tick list before the next draw". If upstream exposes the visible
  window as observable state on `VicoScrollState` (we already compute it — S1A-06), the entry
  cache, binary search, `computeVisibleEntries`/`computeDisplayRange` and the callback could all
  move to meApp as a plain `CartesianLayerRangeProvider` implementation. What cannot move is the
  animation-injection in point 3 and the `drawingModel` skip in point 4.
- Concretely, the minimum fork surface would be: (a) a public, observable `visibleXRange` on
  `VicoScrollState`, (b) a public `rangeProvider` getter on `LineCartesianLayer` (replaces
  `internalRangeProvider`), (c) something that makes a layer recompute Y positions per frame
  instead of caching (replaces the `ScrollAwareRangeProvider` type check with a flag). Everything
  else becomes meApp code. That is the single highest-leverage shrink available in this cluster.

**Conflict risk.** The file itself is new and grafts clean. The risk is entirely in its two
anchors: `CartesianChartHost.kt` (ours 454+/8-, theirs 196+/94-) and `LineCartesianLayer.kt`
(ours 320+/54-, theirs 130+/35-). Rated **high** because the behaviour is spread across both.

---

## S1A-02 — Window travels inside the model (`Window` + `WindowKey`)

**What it does.** Lets the caller post the chart's X bounds (and a Y seed) *inside the model
transaction*, in the `ExtraStore`, instead of setting them on the provider through composition.
The provider looks its own entry up by a string key, so one model producer can feed several charts
that share data and differ only in the span they show.

**Why we needed it.** The bounds are derived from the same data that triggers the push, so they
arrived one frame *after* the model did. The chart composed against the previous window, drew its
data far off-canvas for a frame, then corrected itself. Putting the window in the transaction makes
the two inseparable, so there is no late frame to correct.

**Commits.** `ccefdc7a9` MOB-2953: let a window travel inside the model it describes (+45/−8).
Earlier related: `5a8f1567d` MA-3490 (the `seedMinY`/`seedMaxY` rename),
`82cda206e` MA-3490 (seed fallback).

**Files and hunks.** `ScrollAwareRangeProvider.kt`
- 56–75 `data class Window(minX, maxX, seedMinY, seedMaxY, openStartX, openEndX)`, every field
  defaulting to `Double.NaN` meaning "no opinion".
- 77–90 `companion object { public val WindowKey: ExtraStore.Key<Map<String, Window>> }`.
- 92–96 `windowFromExtras(extraStore)` and `internal fun openingWindow(extraStore)`.
- 189–195 — the X getters prefer the extras over the field:
  ```kotlin
  override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double =
    (windowFromExtras(extraStore)?.minX ?: xRangeMin).takeIf { !it.isNaN() } ?: minX
  ```
- 197–208 — the Y seed getters deliberately **reverse** that precedence (field first, extras
  second), because the field has a second writer: the caller refreshing it when a scroll settles,
  with no model in flight.
- 349, 353 — `windowKey` is a `remember` key on the provider, so changing it rebuilds it.

**Public API added or changed.**
```kotlin
public data class ScrollAwareRangeProvider.Window(
  val minX: Double = Double.NaN,
  val maxX: Double = Double.NaN,
  val seedMinY: Double = Double.NaN,
  val seedMaxY: Double = Double.NaN,
  val openStartX: Double = Double.NaN,
  val openEndX: Double = Double.NaN,
)

public val ScrollAwareRangeProvider.Companion.WindowKey: ExtraStore.Key<Map<String, Window>>
// plus the `windowKey: String? = null` parameter on the constructor and on
// rememberScrollAwareRangeProvider (see S1A-01).
```

**Coupling to upstream internals.** Low, and this is the cleanest piece in the cluster. It uses
only `ExtraStore.Key` / `ExtraStore.getOrNull`, which are public upstream API, and the `extraStore`
argument that `CartesianLayerRangeProvider` already hands every getter. It does, however, depend on
the host recomputing ranges when the chart changes (`CartesianChartHost.kt:92–136`) — without that,
a window arriving after the push still never reaches the chart.

**Could it be an extension point, or live in meApp instead?** It is already effectively a plugin —
`ExtraStore` is the upstream-sanctioned way to carry data with a model. If S1A-01's mechanism moves
to meApp, this moves with it at no cost. Keep the shape (keyed map, NaN = no opinion) wherever it
lands; meApp's `ChartWindows` typealias and `putWindows` extension
(`features/dashboard/viewmodel/base/BaseDashboardViewModel.kt:40–51`) are written against it.

**Conflict risk.** **Low.** New file, public upstream API only. The dependency on host range
recomputation is where it can silently regress.

**meApp usage.**
- `features/dashboard/viewmodel/base/BaseDashboardViewModel.kt:28, 41, 51, 60–72, 170` —
  `ChartWindows` typealias, `Transaction.putWindows`, `SegmentPlot.toChartWindow`.
- `features/dashboard/viewmodel/baby/BabyDashboardViewModel.kt:28`.
- `features/common/components/chart/ProductChart.kt:156–163` — passes `windowKey = segment.name`.

---

## S1A-03 — Provider derives its own opening range (`seedFromWindow`)

**What it does.** Before the first frame is drawn, the provider computes the opening Y range and
tick list itself, from the opening window the data arrived with, by running a synthetic
`ScrollInfo` through the *same* `computeVisibleEntries` → `computeDisplayRange` path that answers
every later scroll. It also splits the "rebuild the entry cache" decision from the "recompute the
opening range" decision, so neither triggers the other.

**Why we needed it.** The Y range had two producers: meApp computed a seed for the first frame,
and the chart measured the real range once it had drawn. Same arithmetic over slightly different
entries, so the axis **re-lettered itself** a moment after the data appeared. Separately, re-seeding
on any model push dragged the axis back to the opening window from wherever the user had scrolled —
two tick sets per push, visible as a blink whenever a second metric was changed.

**Commits.**
- `a98cf86de` MOB-3494: let the chart derive its own opening range, and 4.0.44 (+68/−1 here)
- `e6c766cb8` MOB-3494: keep the axis and a transformed line in step (the tick-clearing half)

**Files and hunks.** `ScrollAwareRangeProvider.kt`
- 123–139 `seedMinY`/`seedMaxY` doc, and `cachedSeries` / `cachedWindow` — deliberately two
  fields, because a model copied for a new `ExtraStore` carries the same series instance: the
  cache can stand while the window moves, and the window can stand while the data changes.
- 148–179 `internal fun seedFromWindow(extraStore: ExtraStore)`:
  ```kotlin
  val info = ScrollInfo(scrollPixels = 0f, xSpacing = 1f, chartWidth = 0f,
                        visibleXStart = start, visibleXEnd = end)
  val entries = computeVisibleEntries(info) ?: return
  val (range, ticks) = computeDisplayRange(entries, start..end) ?: return
  currentMinY = range.start; currentMaxY = range.endInclusive; currentTicks = ticks
  ```
  Note `xSpacing = 1f` is a dummy that only has to be `> 0f` to pass the guard at line 252;
  `chartWidth` and `scrollPixels` are unused on this path.
- 216–232 `buildCache` — `currentTicks` is cleared **only** on the empty-series path (219–224) and
  explicitly *not* on a push that carries data (226–232). That comment block is the fix for the
  blank-axis frame.

**Public API added or changed.** None. `seedFromWindow` and `openingWindow` are `internal`;
`cachedSeries`/`cachedWindow`/`isCacheReady` are `internal var`. `Window.openStartX`/`openEndX`
(S1A-02) are the public half of this.

**Coupling to upstream internals.** The highest in the cluster, and it is *call-order* coupling,
not just symbol coupling:
- It must run **during composition**, inside the `remember(chart, model)` that computes
  `initialRanges` (`CartesianChartHost.kt:93–136`), *before* `chart.updateRanges(hostRanges, model)`
  at line 133. A frame later leaves `updateRanges` with no opinion to ask for and the chart opens
  on the whole dataset's extent.
- Layers and models are paired **by position** (`chart.layers.forEachIndexed { index, layer -> …
  model.models.getOrNull(index) }`, lines 105–109). The commit body records that `indexOf` matched
  on `equals`, so two layers configured alike both resolved to `models[0]` and the second drew
  against the first's range. Any upstream change to how layers and layer models are associated
  breaks this silently, not loudly.
- The host also re-does the whole range computation itself (`hostRanges` at line 92, `reset()` +
  `updateRanges` + `toImmutable()` at 132–134), deliberately ignoring the producer's own ranges
  (line 81). That is a direct fork of upstream's range lifecycle.

**Could it be an extension point, or live in meApp instead?** Not as it stands — this exists
*because* splitting the computation between meApp and the library is what caused the bug. The
honest options are: (a) keep it, accepting the coupling, or (b) if upstream ever exposes a hook
that runs after a model is accepted and before ranges are finalised, this becomes a plugin
verbatim. Moving it back to meApp would reinstate the two-producer defect. Do not move it.

**Conflict risk.** **High.** It lives inside the hottest file on both sides and depends on an
ordering upstream never promised.

---

## S1A-04 — `WindowSettleSignal` / `settleEpoch`

**What it does.** A one-method interface that lets a range provider announce "the visible window
has come to rest" as a monotonically increasing counter. A layer whose `yTransform` rescales its
series against the values in view uses that counter as a cache key, so it recomputes once per
settled window — and not on every frame of a scroll.

**Why we needed it.** A `yTransform` recomputed only when the shared Y range changed. Two different
windows can hold different values and still round to the same axis, so scrolling between them left
the second line scaled for the window the user had left — a visible kink that nothing later
invalidated. Keying on the window itself was worse: the window moves every frame, so the line
danced under the finger.

**Commits.** `e6c766cb8` MOB-3494: keep the axis and a transformed line in step.
(Supersedes the deleted `VisibleEntryKeyTest.kt` added in `0fc9c7bf3`.)

**Files and hunks.**
- `cartesian/data/WindowSettleSignal.kt`, new, 35 lines — 32–35 is the whole interface; 19–31 is
  the rationale, which is the most load-bearing comment in the cluster.
- `ScrollAwareRangeProvider.kt:145–146` — `override var settleEpoch: Int = 0; internal set`.

**Public API added or changed.**
```kotlin
public interface WindowSettleSignal {
  /** Incremented once per settled window. Layers use it as a cache key, never for its value. */
  public val settleEpoch: Int
}
```

**Coupling to upstream internals.**
- Written by `ScrollAwareRangeEffect` at `CartesianChartHost.kt:654` (`provider.settleEpoch++`),
  deliberately placed *before* the "range did not move" early return at 656–657 — that ordering
  is the entire point and is easy to lose in a merge.
- Read by `LineCartesianLayer.kt:856`:
  `val windowKey = (rangeProvider as? WindowSettleSignal)?.settleEpoch?.toLong() ?: 0L`,
  combined at 876 with the target-range key from S1A-12. The transform is invoked against
  `ranges.getTargetYRange(verticalAxisPosition)` (line 843), never the range being left.
- Depends on `yTransform` on `LineCartesianLayer`, which is itself ours (another cluster).

**Could it be an extension point, or live in meApp instead?** The interface is already a plugin —
it has no upstream dependencies at all, and meApp implements it directly
(`features/common/components/chart/ProductChart.kt:282–283`, an anonymous
`CartesianLayerRangeProvider, WindowSettleSignal` whose `settleEpoch` delegates to meApp's own
`MetricBasis.version`). The *file* could move to meApp verbatim if `yTransform` also moved; it
cannot while the consumer of the signal is inside `LineCartesianLayer`.

**Conflict risk.** **Low** for the file, **medium** overall — two write/read sites inside hot
upstream files, and the pre-early-return placement is a correctness detail a merge can quietly
reorder.

**meApp usage.** `features/common/components/chart/ProductChart.kt:40, 277–296`.

---

## S1A-05 — Snap fling behaviour

**What it does.** A `FlingBehavior` for the chart that, after a drag or a fling, animates the
scroll to an X position the caller chooses. It distinguishes a slow release from a fling by
velocity, and for a fling it first projects where decay physics would land, so the caller can snap
to the nearest window boundary from there rather than from where the finger left.

**Why we needed it.** The product charts page through fixed windows — a week, a month, a year. A
free-scrolling chart leaves the user mid-week with a half-labelled axis. Snapping to boundaries is
what makes the chart read as a pager.

**Commits.**
- `01ad34f5d` Feature-4: Add SnapFlingBehavior + fix ScrollAwareRangeProvider X range
- `5050aff7b` Feature-4: Add scrollPaddingXStep to SnapBehaviorConfig *(later removed)*
- `66679fbbf` Feature-5: Add test chart usage + fix KMP String.format
- `57ce985ea` perf: Eliminate per-frame allocations and dead code
- `130fde18f` fix(snap): Fix continuous scroll stuck during snap animation
- `b323662e6` MOB-2953: One source for the space before a chart's first entry *(removed
  `scrollPaddingXStep`)*

**Files and hunks.** `cartesian/SnapFlingBehavior.kt`, new, 131 lines.
- 43–52 `SnapBehaviorConfig` + nested `SnapAnimation`.
- 65–119 `internal class ChartSnapFlingBehavior : FlingBehavior`.
  - 77–87 velocity split at 500 px/s and the decay projection
    `initialVelocity / (decayFrictionMultiplier * 4.5f)` — the `4.5f` is an unexplained magic
    constant; treat it as tuned, not derived.
  - 90 `scrollState.xToScrollValueBehindStartInset(targetDataX)` (S1A-10/S1A-11 single-source rule).
  - 99–116 the animation runs `AnimationState(0f).animateTo(delta)` and calls `scrollBy(step)`
    inside `ScrollScope` — the comment at 97–98 records that this is the only way to scroll from
    inside `performFling` without fighting the `scrollableState` lock. `isSnapping` is reset in a
    `finally` so a cancelling gesture cannot leave it stuck true.
- 126–131 `rememberChartSnapFlingBehavior`.

**Public API added or changed.**
```kotlin
public data class SnapBehaviorConfig(
  val snapToLabel: ((
    currentXLabel: Double?, projectedXLabel: Double?, isDrag: Boolean, isForward: Boolean,
  ) -> Double)? = null,
  val animation: SnapAnimation = SnapAnimation(),
) {
  public data class SnapAnimation(
    val decayFrictionMultiplier: Float = 2f,
    val snapDurationMillis: Int = 800,
    val snapEasing: androidx.compose.animation.core.Easing = LinearOutSlowInEasing,
  )
}

@Composable
public fun rememberChartSnapFlingBehavior(
  scrollState: VicoScrollState,
  config: SnapBehaviorConfig,
): FlingBehavior
```
`ChartSnapFlingBehavior` and its `isSnapping` are `internal`.

**Coupling to upstream internals.**
- Needs three `internal` members of `VicoScrollState`: `scrollValueToX()`,
  `xToScrollValueBehindStartInset()`, and `maxValue`/`value`. Two of the three are ours (S1A-06).
- Needs the `flingBehavior` parameter threaded through `CartesianChartHost` →
  `CartesianChartHostImpl` → `Modifier.pointerInput(... flingBehavior = flingBehavior)`
  (`CartesianChartHost.kt:457`) and on into `scrollable()` inside `Modifier.kt`. Upstream's
  `CartesianChartHost` does not take a `FlingBehavior` at all — that parameter is ours.
- `FEATURE-4` claims the gesture detector in DECIDING mode must not consume events, so
  `scrollable` can cancel an in-flight snap. That lives in `Modifier.kt` (another cluster) and is
  a real behavioural dependency, not a comment.

**Could it be an extension point, or live in meApp instead?** Mostly yes, and this is the second
best shrink available. `FlingBehavior` is a **Compose Foundation** interface, not a Vico one. If
upstream `CartesianChartHost` accepted a `FlingBehavior` (or if we keep only that one-parameter
patch), the whole of `SnapFlingBehavior.kt` could live in meApp, provided
`VicoScrollState` exposes publicly: `value`, `maxValue`, a data-X→scroll-pixels conversion, and a
scroll-pixels→data-X conversion. Three public conversions in exchange for deleting a file: take it.

**Conflict risk.** **Low** for the file. **Medium** overall, carried by the `flingBehavior`
parameter in `CartesianChartHost` and `Modifier.kt`.

**Known defect / doc disagreement.** `isSnapping` (line 71) is **written but never read**. Nothing
in the repo reads it — `FEATURE-4-SNAP-FLING-BEHAVIOR.md` ("Range Provider Integration") says the
range provider's debounce collector skips updates while it is true, and that is simply no longer
the case; the only emission guard at `CartesianChartHost.kt:491–512` is the three-scalar dedup.
Decide at migration time whether to restore the suppression or delete the flag. Do not carry a
stated-but-absent behaviour forward silently.

**meApp usage.** `features/dashboard/components/chart/GraphView.kt:41, 47, 203, 210–225`
(`GraphSnapHelper.getSnappedPositionOnDrag` / `getSnapPositionOnFling`,
`SnapAnimation(snapDurationMillis = 500)`).

---

## S1A-06 — Scroll ⇄ X geometry helpers and `visibleXRange`

**What it does.** Adds conversions between a data X value and a scroll pixel offset, in both
directions, plus a property that reports the X data range currently on screen. All are computed on
demand from state the scroll state already holds, and return `null` before the chart has been
measured.

**Why we needed it.** Everything else in this cluster needs the answer to "what is on screen?" —
the snap behaviour (to pick a target), the range provider (via the host), the chart header in
meApp (to name the window and fill in estimated values). Upstream had no public way to ask.

**Commits.**
- `01ad34f5d` Feature-4, `6f3cb9f33` Feature-8 (`visibleXRange` + `xToScrollValueWithPadding`)
- `7baf04e8b` Feature-8: interpolated marker targets + scroll helper APIs
- `57ce985ea` perf: Eliminate per-frame allocations and dead code
- `b323662e6` MOB-2953 (renamed `xToScrollValueWithPadding` → `xToScrollValueBehindStartInset`)

**Files and hunks.** `cartesian/VicoScrollState.kt`
- 68–74 `xToScrollValue(x)` — includes `dims.startPadding`.
- 77–87 `visibleXRange` — guards on `xSpacing == 0f`; `start` subtracts `startPadding`,
  `end = start + b.width / xSpacing * xStep`.
- 89–102 `xToScrollValueBehindStartInset(x)` — the same arithmetic **without** `startPadding`.
- 104–111 `scrollValueToX(scrollPixels: Float? = null)`.

**Public API added or changed.**
```kotlin
public val VicoScrollState.visibleXRange: ClosedFloatingPointRange<Double>?
```
`xToScrollValue`, `xToScrollValueBehindStartInset` and `scrollValueToX` are **`internal`** — note
the asymmetry: meApp reads `visibleXRange` but cannot convert in the other direction, which is why
`SnapFlingBehavior` has to live inside the library today (see S1A-05).

**Coupling to upstream internals.** All four read the three private fields `context`,
`layerDimensions`, `bounds` that upstream's `update()` populates, and through them
`context.ranges.minX`, `context.ranges.xStep`, `layerDimensions.xSpacing` and
`layerDimensions.startPadding`. They reproduce, by hand, the arithmetic upstream uses in
`Scroll.Absolute.x` and `CartesianMeasuringContext.getVisibleXRange`. If upstream changes how
`startPadding` relates to scroll origin, these drift silently — there is no test pinning them.

**Could it be an extension point, or live in meApp instead?** They cannot move to meApp (the
fields are private upstream state), but they are exactly the shape of an upstream feature request:
four pure functions over already-stored state. Make these public and `SnapFlingBehavior` leaves the
fork. Worth proposing upstream rather than re-patching.

**Conflict risk.** **High** — `VicoScrollState` is hot on both sides (ours 168+/9-, theirs
182+/41-), and this is the largest of our additions to it.

**meApp usage.** `features/dashboard/components/chart/GraphView.kt:401, 444, 496`
(`scrollState.visibleXRange`).

---

## S1A-07 — Scrub freeze: `isScrollFrozen`, `isScrolling`, `isUserScrolling`

**What it does.** While the user is dragging a marker along the chart (scrubbing), the scroll state
keeps *claiming* touch deltas but stops moving. Claiming them is what keeps `nestedScroll` active
so a parent `LazyColumn` does not steal the gesture; not moving is what keeps the chart still under
the finger. Two public booleans let a caller tell the two situations apart.

**Why we needed it.** Earlier versions disabled the `scrollable` modifier during a scrub. That
handed the gesture straight to the parent list, so scrubbing a chart inside a scrolling screen
scrolled the screen. Freezing instead of disabling fixes that. `isScrolling` then became ambiguous
(it is true during a scrub too), so `isUserScrolling` was added for the callers that mean "the
chart is actually moving".

**Commits.**
- `3a284be42` fix(scrub): Freeze scroll during scrubbing instead of disabling scrollable
- `196bb54af` feat(marker): Add yLabelCallback + isScrolling for chart header updates
- `2ddae34f4` feat: markerTargetsEnabled flag + scrollable always-on for scrub parent blocking
- `41613334a` MA-3773: Stricter scrub clamp + isUserScrolling getter (v4.0.30)

**Files and hunks.** `cartesian/VicoScrollState.kt`
- 54–55 `isScrolling`; 57–65 `isUserScrolling`.
- 199–201 `internal var isScrollFrozen: Boolean = false`.
- 203–221 the `ScrollableState` lambda is **restructured**, not merely extended:
  ```kotlin
  internal val scrollableState = ScrollableState { delta ->
    if (isScrollFrozen || !scrollEnabled) {
      delta                        // claim it, do not move
    } else {
      /* upstream's original body, now in the else branch */
    }
  }
  ```
  Note the `!scrollEnabled` arm: a non-scrollable chart (TOTAL / single-window segments) keeps the
  `scrollable` modifier attached so the same parent-blocking works there.

**Public API added or changed.**
```kotlin
public val VicoScrollState.isScrolling: Boolean      // = scrollableState.isScrollInProgress
public val VicoScrollState.isUserScrolling: Boolean  // = isScrolling && !isScrollFrozen
```
`isScrollFrozen` is `internal var`.

**Coupling to upstream internals.**
- It rewrites the body of upstream's `scrollableState` lambda. This is a **structural** edit to a
  seven-line upstream expression — the kind that produces a merge conflict with no clean side.
- `isScrollFrozen` is written from four places in `Modifier.kt` (lines 109, 136, 146, 206), which
  is our gesture-handling patch in another cluster. The freeze/unfreeze pairing is invariant-by-
  convention; nothing enforces it.
- Depends on upstream keeping `scrollEnabled` as a field the lambda can read.

**Could it be an extension point, or live in meApp instead?** No, neither. The freeze has to sit
inside the delta-consumption decision, which is upstream's. The realistic shrink is to make the
*intent* upstream-shaped: a single `internal var scrollFrozen` (or a `ScrollLock` enum covering
frozen/disabled/free) with the whole behaviour in one small override point, so the merge is a
three-line patch rather than a restructured lambda. Keep `isScrolling`/`isUserScrolling` public —
meApp genuinely needs the distinction.

**Conflict risk.** **High.** Structural rewrite of a hot upstream expression.

**meApp usage.** `features/dashboard/components/chart/GraphView.kt:334`
(`if (scrollState.isUserScrolling) return@LaunchedEffect` — the marker-cleared-by-scroll path).

---

## S1A-08 — Initial-scroll re-apply and `key` on `rememberVicoScrollState`

**What it does.** Two changes. (1) `update()` re-applies `initialScroll` when the maximum scroll
distance changes, not only on the very first measurement — but never while a scroll is in
progress. (2) `rememberVicoScrollState` takes an extra `key` so the caller can force a fresh
scroll state (and therefore a fresh initial scroll) on its own terms.

**Why we needed it.** The screen shows a placeholder/stale model first and the real data moments
later. On the real model, `maxValue` changes but `initialScrollHandled` was already true, so the
chart stayed scrolled to wherever the stale data had put it — commonly an empty region. meApp's
`key` is `Triple(segment, isEmptyGraph, endTimestamp)`, which covers the empty→data transition
(MOB-598) and a new/deleted entry moving the rolling window (MOB-1537).

**Commits.**
- `3b9146ca8` fix(scroll): Re-apply initialScroll on maxValue change + key param + no alpha hiding
- `5af99a161` fix(scroll): Skip initial scroll re-apply during active scroll
- `82cda206e` MA-3490: Seed Y-range fallback + two-pass prepare for frame-0 correctness

**Files and hunks.** `cartesian/VicoScrollState.kt`
- 50 `private var initialScrollHandled` → `internal var` (so the Canvas can read it for the
  two-pass prepare).
- 298–312 `update()`:
  ```kotlin
  val prevMaxValue = maxValue
  maxValue = context.getMaxScrollDistance(bounds.width, layerDimensions)
  if (!initialScrollHandled ||
      (prevMaxValue != maxValue && prevMaxValue > 0f && !scrollableState.isScrollInProgress)) {
    value = initialScroll.getValue(context, layerDimensions, bounds, maxValue)
    initialScrollHandled = true
  }
  ```
- 386, 389, 396 `key: Any? = null` added as the **first** `rememberSaveable` input and as a
  `remember` key for the `Saver`.

**Public API added or changed.**
```kotlin
@Composable
public fun rememberVicoScrollState(
  scrollEnabled: Boolean = true,
  initialScroll: Scroll.Absolute = Scroll.Absolute.Start,
  autoScroll: Scroll = initialScroll,
  autoScrollCondition: AutoScrollCondition = AutoScrollCondition.Never,
  autoScrollAnimationSpec: AnimationSpec<Float> = spring(),
  key: Any? = null,                       // ← added, trailing, source-compatible
): VicoScrollState
```
Source-compatible for existing callers; **not** binary-compatible (signature change).

**Coupling to upstream internals.** It changes the meaning of upstream's one-shot
`initialScrollHandled` latch. `initialScrollHandled` is also read from the Canvas
(`CartesianChartHost.kt:467`) to decide whether to run the extra "two-pass prepare" so
`VerticalAxis.updateHorizontalLayerMargins` sees a post-initial-scroll scroll value — so the latch
now has two consumers with different expectations. That second consumer is the part most likely to
be lost in a merge, because the condition reads as an optimisation.

**Could it be an extension point, or live in meApp instead?** The `key` parameter cannot move —
`rememberSaveable` keys must be passed at the call site of the `remember`. The re-apply condition
is arguably an upstream bug fix and should be offered upstream as one: "re-apply `initialScroll`
when `maxScrollDistance` changes and no scroll is in progress" is a defensible general behaviour,
not a meApp quirk. Propose it; keep the patch meanwhile.

**Conflict risk.** **High.** Both hunks are inside functions upstream reworked heavily, and the
re-apply condition is a one-line edit whose absence is invisible until QA finds a chart opening on
an empty region.

**meApp usage.**
- `features/dashboard/components/chart/GraphView.kt:197–202` — `key = Triple(segment,
  segmentState.isEmptyGraph, segmentState.endTimestamp)`.
- `features/dashboard/snapshot/components/SnapshotLineChart.kt:167–170` — `scrollEnabled = false`.

---

## S1A-09 — Axis-label and interpolated-Y read-out

**What it does.** Lets a caller ask the scroll state two questions that otherwise need the draw
pass: "which X axis labels are currently visible?" and "what Y value does each series have at these
X positions?" (interpolating where there is no actual data point). To answer the first with a
custom item placer, the scroll state holds a reference to the last `CartesianDrawingContext`.

**Why we needed it.** meApp's chart header shows a value per visible label — including estimated
values for windows with no reading of their own, drawn from the same monotone curve the line uses.
Without this, meApp would need a second implementation of label placement and curve interpolation,
which would disagree with what the chart actually drew.

**Commits.**
- `7baf04e8b` Feature-8: Add interpolated marker targets + scroll helper APIs
- `58a017417` feat(axis): Add drawingContext to VicoScrollState for itemPlacer label values
- `25dc072a9` fix(interpolation): Return null outside data range instead of clamping

**Files and hunks.** `cartesian/VicoScrollState.kt`
- 52 `internal var drawingContext: CartesianDrawingContext? = null`, assigned once per draw at
  `CartesianChartHost.kt:531`.
- 113–144 `getVisibleAxisLabels` — three strategies in priority order: caller `labelProvider`,
  then `itemPlacer.getLabelValues(drawingContext!!, range, fullRange, 0f)`, then an even walk at
  `xStep` intervals from `ranges.minX`. The `drawingContext!!` is guarded by a null check on the
  preceding line.
- 146–176 `getInterpolatedYValues` — iterates `ctx.model.models`, filters to
  `LineCartesianLayerModel`, and flattens **all series of all line layers** into one output list.
- 178–193 `linearInterpolateFromEntries` — returns `null` outside the data range (deliberately
  not clamped, per `25dc072a9`); the monotone path delegates to `MonotoneInterpolator
  .getYAtXFromEntries`, which is ours (another cluster).

**Public API added or changed.**
```kotlin
public fun VicoScrollState.getVisibleAxisLabels(
  itemPlacer: HorizontalAxis.ItemPlacer? = null,
  labelProvider: ((
    visibleXRange: ClosedFloatingPointRange<Double>,
    fullXRange: ClosedFloatingPointRange<Double>,
  ) -> List<Double>)? = null,
): List<Double>

public fun VicoScrollState.getInterpolatedYValues(
  xValues: List<Double>,
  interpolationType: InterpolationType = InterpolationType.MONOTONE,
): List<List<Double?>>
```
(`InterpolationType` is a separate fork file, `cartesian/InterpolationType.kt` — not in this
cluster, but it is part of this signature's contract.)

**Coupling to upstream internals.**
- `drawingContext` is a **draw-pass object retained past the draw pass**. It holds the canvas, the
  layer bounds and the scroll value at the moment it was built; reading it later is reading a stale
  snapshot by construction. It is also assigned on every frame from inside `Canvas`
  (`CartesianChartHost.kt:531`), which is the exact line a restructured host would move.
- `getVisibleAxisLabels` calls `HorizontalAxis.ItemPlacer.getLabelValues(context, visibleXRange,
  fullXRange, maxLabelWidth)` with `maxLabelWidth = 0f`. That signature is upstream's and is
  listed in BASELINE as hot on both sides (`HorizontalAxis` theirs 277+/77-).
- `getInterpolatedYValues` walks `context.model.models` and assumes `LineCartesianLayerModel`
  carries `series: List<List<Entry>>` with `x`/`y` fields.

**Could it be an extension point, or live in meApp instead?** `getInterpolatedYValues` **should
move to meApp**: it needs only the model (which meApp produces) and an interpolator. It is a pure
function wearing a method's clothes, and it is in the library only because `MonotoneInterpolator`
is. Expose the interpolator publicly, move the function out.
`getVisibleAxisLabels` is harder — the `itemPlacer` branch genuinely needs a
`CartesianDrawingContext`. Prefer replacing the retained `drawingContext` with the narrower thing
the item placer actually uses; if that is not possible, keep it but scope it tightly, because a
retained draw context is a latent correctness bug as well as a merge hazard.

**Conflict risk.** **High** — size of the addition plus the `drawingContext` assignment inside the
host's Canvas plus the `ItemPlacer` signature dependency.

**meApp usage.** `features/dashboard/components/chart/GraphView.kt:39, 408, 413–416, 497`.

---

## S1A-10 — `Scroll.Absolute.xBehindStartInset`

**What it does.** A scroll factory that puts a given X value at the start of the visible window
*behind* the blank space the chart reserves before its first entry — so the reserved gap is still
on screen, rather than scrolled past.

**Why we needed it.** The charts open on a window whose first label needs breathing room from the
left edge. `Scroll.Absolute.x` puts the value flush against the edge.

**Commits.**
- `a28e874cd` Feature-5: Add xWithPadding scroll + FadingEdges padding from xStep
- `b323662e6` MOB-2953: One source for the space before a chart's first entry

**Files and hunks.** `cartesian/Scroll.kt:58–77`.
```kotlin
public fun xBehindStartInset(x: Double, bias: Float = 0f): Absolute =
  Absolute { context, layerDimensions, bounds, _ ->
    ((x - context.ranges.minX) / context.ranges.xStep).toFloat() *
      layerDimensions.xSpacing - bias * bounds.width
  }
```
It is literally upstream's `Scroll.Absolute.x` (lines 51–56) **minus** `layerDimensions
.startPadding`. The MOB-2953 commit body explains the previous shape: `xWithPadding(x,
paddingXStep, bias)` added `startPadding` and then subtracted a caller-supplied
`paddingXStep * xSpacing`, so the parameter's only possible effect was to disagree with what the
chart had reserved.

**Public API added or changed.**
```kotlin
public fun Scroll.Absolute.Companion.xBehindStartInset(x: Double, bias: Float = 0f): Absolute
```
**Removed in the same commit:** `public fun xWithPadding(x: Double, paddingXStep: Double,
bias: Float = 0f): Absolute`.

**Coupling to upstream internals.** Minimal and well-behaved: `CartesianMeasuringContext.ranges`
and `CartesianLayerDimensions.xSpacing`, both already used by the upstream factory beside it. It
does assume `startPadding` is the only thing between the scroll origin and the first entry — if
upstream changes that relationship, this silently offsets by the wrong amount.

**Could it be an extension point, or live in meApp instead?** `Scroll.Absolute` is a
`fun interface`, so meApp could construct this lambda itself **today** — nothing in it is
non-public. The only reason it lives here is discoverability. **Move it to meApp.** This is the
cheapest fork reduction in the cluster: delete 20 lines of library code, add a one-line extension
in meApp, zero behaviour change.

**Conflict risk.** **Low.** `Scroll.kt` is in BASELINE's "upstream never touched" list and this is
a pure addition inside a companion object.

**meApp usage.**
- `features/dashboard/components/chart/GraphView.kt:188`.
- `features/dashboard/snapshot/components/SnapshotLineChart.kt:94`.

**Residual mess.** Two stacked KDoc blocks (lines 58–62 and 63–72) — the old `xWithPadding` doc
was left above the new one. Cosmetic, but it describes a parameter that no longer exists. Clean up
during the port.

---

## S1A-11 — Fading edges sized from the layer's own start/end padding

**What it does.** The fade overlay at each edge of a scrollable chart is now at least as wide as
the space the layer reserves before its first (and after its last) entry, taken from the layer
itself rather than from a figure the caller passes alongside.

**Why we needed it.** The fade exists to say "there is more content this way". If the reserved
inset is wider than the fade, the line emerges from a hard edge instead of a soft one. Passing the
inset separately made it possible to tell the fade a different number from the one the chart had
reserved — the same class of defect as S1A-10.

**Commits.**
- `a28e874cd` Feature-5: Add xWithPadding scroll + FadingEdges padding from xStep
- `57ce985ea` perf: Eliminate per-frame allocations and dead code
- `b323662e6` MOB-2953: One source for the space before a chart's first entry

**Files and hunks.** `cartesian/FadingEdges.kt`
- 82–87 inside `draw(context)`:
  ```kotlin
  val effectiveStartWidth = maxOf(startWidth.pixels, layerDimensions.startPadding)
  val effectiveEndWidth = maxOf(endWidth.pixels, layerDimensions.endPadding)
  ```
  and the two `if` guards plus the two rect edges at 88, 93, 104 now use them.
- 157–167 `rememberFadingEdges` — `startPaddingXStep` / `endPaddingXStep` parameters **removed**,
  and the `remember` key list shortened to match.
- `PaddedFadingEdges` (an `internal` subclass that existed only to carry the two figures, with a
  duplicated `draw` that re-implemented the gradient) **deleted**, −54 lines.

**Public API added or changed.**
```kotlin
// REMOVED from rememberFadingEdges(startWidth, endWidth, visibilityThreshold, visibilityEasing):
//   startPaddingXStep: Double? = null,
//   endPaddingXStep: Double? = null,
// REMOVED: internal class PaddedFadingEdges
```
The public `FadingEdges` class signature is unchanged; only its *behaviour* changed — a
zero-width `FadingEdges` now draws a fade whenever the layer reserves padding. That is a silent
behaviour change for any caller that passed `0.dp` expecting no fade. meApp relies on it
(`GraphView.kt:181–185` passes `startWidth = 0.dp, endWidth = 0.dp` precisely to get the layer's
own padding).

**Coupling to upstream internals.** Reads `layerDimensions.startPadding` / `endPadding` from the
`CartesianDrawingContext` receiver inside upstream's `draw`. Both are public `CartesianLayerDimensions`
members. The real coupling is semantic: it assumes `startPadding` is the chart's reserved inset,
which at HEAD is set from `CartesianChart.startInsetDp` (`CartesianChart.kt:298–299`,
`ensureValuesAtLeast(unscalableStartPadding = startInsetDp.pixels)`) — and `startInsetDp` is ours.

**Could it be an extension point, or live in meApp instead?** `FadingEdges` is `public open` with
`protected` width fields, so meApp *could* subclass it — which is exactly what `PaddedFadingEdges`
did, and MOB-2953 deleted it as a mistake. Keeping the patch is right: the correct width is the
layer's own, and only code inside `draw` can see it. This is a four-line patch to a file upstream
never touches; leave it. If anything, offer it upstream — "the fade should cover the layer's
reserved padding" is a general improvement.

**Conflict risk.** **Low.** `FadingEdges.kt` is in BASELINE's "upstream never touched" list and the
net diff is +10/−4.

**meApp usage.** `features/dashboard/components/chart/GraphView.kt:48, 181–185`;
`features/dashboard/snapshot/components/SnapshotLineChart.kt` (via the shared chart builder).

---

## S1A-12 — `CartesianChartRanges.getTargetYRange`

**What it does.** Adds a second Y-range question to the ranges interface: not "what range is the
chart drawing in right now" but "what range is it animating towards". While no animation is
running the two are the same.

**Why we needed it.** A layer that maps its series into another layer's range must map against the
range the axis is *heading for*. Mapping against the range it is leaving strands the line outside
the plot while the axis animates out from under it. The default implementation means every
existing `CartesianChartRanges` implementor keeps compiling.

**Commits.** `0a35ee586` feat: yTransform target-based caching + fade correction (v4.0.26).

**Files and hunks.** `cartesian/data/CartesianChartRanges.kt:40–45`.
```kotlin
public fun getTargetYRange(axisPosition: Axis.Position.Vertical?): YRange = getYRange(axisPosition)
```

**Public API added or changed.**
```kotlin
public fun CartesianChartRanges.getTargetYRange(
  axisPosition: Axis.Position.Vertical?,
): CartesianChartRanges.YRange   // default: = getYRange(axisPosition)
```

**Coupling to upstream internals.** It adds a member to a **public upstream interface**. Three
implementors matter: upstream's `MutableCartesianChartRanges` and its `toImmutable()` object (both
take the default), and our `AnimatedYCartesianChartRanges` (`CartesianChartHost.kt:714–721`),
which is the only one that returns something different. The single consumer is
`LineCartesianLayer.kt:843`. Because it is a defaulted interface method, a merge that drops it
produces a *compile* error at the two sites — the good failure mode.

**Could it be an extension point, or live in meApp instead?** It cannot move — meApp has no way to
observe an in-flight library animation. The honest alternative is to stop needing it: if the
`yTransform` recompute were driven entirely by `settleEpoch` (S1A-04) with the transform told the
target range through the signal rather than through the ranges, this interface member would go
away. Worth testing during the port; it would remove a public-interface patch for the cost of a
wider settle signal.

**Conflict risk.** **Low.** `CartesianChartRanges.kt` is upstream-untouched, the change is +6/−0,
and a missed merge fails loudly.

---

## S1A-13 — `MutableCartesianChartRanges.tryUpdateX` and `NeutralYRange`

**What it does.** Two related changes. `tryUpdateX(minX, maxX)` lets a layer widen the chart's X
domain **without** contributing a Y range. And when no layer has offered a Y range for any axis at
all, `getYRange` now returns a shared, immutable neutral `0.0..1.0` instead of throwing out of a
map lookup.

**Why we needed it.** Every layer unions its raw Y values into the range of the axis it draws
against. A layer with a `yTransform` never draws those raw values — the transform maps them into
another layer's range — so unioning them widened the axis to cover readings in a different unit
entirely. A pulse of 60 against a weight axis of 150..190 opened the axis at zero (the auto range
provider coerces a minimum to at most zero, and a union only ever widens), and because a union
only widens, a tab's first frame kept that range until the animation replaced it: the second metric
was deciding the first metric's gridlines. The neutral fallback is the newly reachable degenerate
case — a chart whose *every* layer is transformed now contributes no Y range at all.

**Commits.** `f436d60cd` MOB-3494: keep a transformed layer out of the shared y range.

**Files and hunks.** `cartesian/data/MutableCartesianChartRanges.kt`
- 40–41 `getYRange`: `yRanges[axisPosition] ?: yRanges.getValue(null)` →
  `yRanges[axisPosition] ?: yRanges[null] ?: NeutralYRange`.
- 44–55 `tryUpdateX`:
  ```kotlin
  public fun tryUpdateX(minX: Double, maxX: Double) {
    _minX = _minX?.coerceAtMost(minX) ?: minX
    _maxX = _maxX?.coerceAtLeast(maxX) ?: maxX
  }
  ```
- 104–119 (declaration at 114) `private val NeutralYRange` — an anonymous `CartesianChartRanges.YRange` with
  `minY = 0.0, maxY = 1.0, length = 1.0`. Immutable and shared deliberately: a caller widening it
  would widen it for every chart that reaches the branch.
- 128 the same `getValue(null)` → `?: NeutralYRange` substitution inside `toImmutable()`.

`vico/compose/src/commonTest/kotlin/com/patrykandpatrick/vico/compose/cartesian/data/MutableCartesianChartRangesTest.kt`
— new, 73 lines, four tests, and these are the only tests in this cluster:
`tryUpdateXWidensTheDomainWithoutTouchingTheAxis`,
`tryUpdateWouldHaveWidenedTheAxisToCoverTheOtherMetric` (pins the defect itself, so the difference
stays visible), `anAxisNoLayerClaimedFallsBackToTheChartWideRange`,
`aChartNoLayerOfferedARangeForIsNeutralRatherThanFatal`.

**Public API added or changed.**
```kotlin
public fun MutableCartesianChartRanges.tryUpdateX(minX: Double, maxX: Double)
```
Behaviour change (no signature change): `getYRange` no longer throws `NoSuchElementException`
when `yRanges` has no `null` entry.

**Coupling to upstream internals.** It reaches into the private backing fields `_minX` / `_maxX`
of upstream's `MutableCartesianChartRanges` — the same fields upstream's own `tryUpdate` writes.
The single caller is `LineCartesianLayer.updateChartRanges` (`LineCartesianLayer.kt:1057–1072`),
which early-returns out of upstream's Y-union path when `yTransform != null`. So this is two
coordinated patches: an added method here, and a skipped union there.

**Could it be an extension point, or live in meApp instead?** No. `_minX`/`_maxX` are private and
`updateChartRanges` is upstream's own method; neither half can live outside the library. The
patch is small and well-tested, which is the right trade. Note that "a layer that contributes X
but not Y" is a genuine general concept — this is a reasonable upstream proposal, not a meApp
quirk.

**Conflict risk.** **Low.** Both files are in BASELINE's "upstream never touched" list and the
diff is +29/−2. The one-line `getValue(null)` → `?: NeutralYRange` substitution appears **twice**
and is easy to apply to only one of them during a port; the test pins the first, not the
`toImmutable()` copy.

---

## Where the FEATURE docs disagree with the code

The docs describe the **early** shape of this cluster. Trust the code. The disagreements:

1. **`FEATURE-5` and `FEATURE-4` describe APIs that no longer exist.**
   `Scroll.Absolute.xWithPadding(x, paddingXStep, bias)` is now
   `xBehindStartInset(x, bias)` (S1A-10). `rememberFadingEdges(startPaddingXStep,
   endPaddingXStep)` and `internal class PaddedFadingEdges` are **gone** (S1A-11).
   `SnapBehaviorConfig.scrollPaddingXStep` is **gone**, and
   `VicoScrollState.xToScrollValueWithPadding` is now `xToScrollValueBehindStartInset` (S1A-05).
   All four removed by `b323662e6` (MOB-2953).

2. **`FEATURE-4` "Range Provider Integration" describes behaviour that is not there.** It says
   the range provider's debounce collector skips updates while `isSnapping` is true. `isSnapping`
   is written in `SnapFlingBehavior.kt` and **read nowhere in the repository**. Either the
   suppression was removed deliberately and the flag was left behind, or it was lost. Flagged in
   S1A-05 — this needs a decision, not a carry-forward.

3. **`FEATURE-1`'s callback signature is one generation old.** It documents
   `onVisibleEntries: (List<Pair<Double, Double>>) -> …`; the code takes
   `(List<List<Pair<Double, Double>>>, ClosedRange<Double>)` — per-series lists plus the visible
   X range (`4e5f7fb1b`, and the X range added later).

4. **`FEATURE-1`'s `modelStructureKey` section describes a mechanism that was replaced.** No
   `modelStructureKey` exists anywhere at HEAD. Its job — "do not restart on a renormalisation" —
   is now done by `cachedSeries` identity and `cachedWindow` equality (S1A-03).

5. **`FEATURE-1`'s "Initial Load (Zero Flash)" is obsolete.** The `alpha(0f)` hide is gone
   (`3b9146ca8`); the comment at `CartesianChartHost.kt:186–187` says so explicitly. Frame-0
   correctness is now the `seedFromWindow` path (S1A-03) plus the two-pass prepare (S1A-08).

6. **`FEATURE-1`'s "Upstream Merge Guide" line numbers are all stale** and its claim that
   `ScrollAwareRangeProvider.kt` is "no risk" on merge is only true of the *file*; its two anchors
   in `CartesianChartHost.kt` and `LineCartesianLayer.kt` are the actual risk.

7. **The sample does not compile against HEAD.**
   `sample/charts/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/sample/charts/compose/DmdBrandsTestChart.kt:89`
   calls `Scroll.Absolute.xWithPadding(80.0, startPaddingXStep)` and line 150 passes
   `scrollPaddingXStep = startPaddingXStep` to `SnapBehaviorConfig`. Both were removed by
   `b323662e6`. The sample module was evidently not built after MOB-2953. It is not a shipping
   artifact, but it means the sample is not a usable reference for the current API — and it will
   need fixing or deleting as part of the port.

8. **Naming drift.** MOB-2953's commit body says "`CartesianChart.startInsetXStep` is the only way
   in"; at HEAD the property is `startInsetDp: Dp` (`CartesianChart.kt:70`), renamed later.
   The KDoc in `Scroll.kt` and `VicoScrollState.kt` still says "startInsetXStep".

---

## Stated uncertainties

- `ChartSnapFlingBehavior`'s `4.5f` divisor in the decay projection (`SnapFlingBehavior.kt:85`) has
  no derivation in code or commit message. I could not establish whether it approximates Compose's
  `FlingCalculator` or was tuned by hand. Treat it as a magic constant to preserve verbatim.
- The 500 px/s drag/fling threshold (line 78) is likewise undocumented.
- I did not verify at runtime that `seedFromWindow`'s dummy `xSpacing = 1f` / `chartWidth = 0f` are
  genuinely unused downstream; I traced `computeVisibleEntries` and `computeDisplayRange` and they
  are not read there, but a future change to either would make them live.
- `getVisibleAxisLabels` passes `maxLabelWidth = 0f` to `ItemPlacer.getLabelValues`. Whether an
  upstream item placer treats `0f` as "unconstrained" or as "no room" is unverified; meApp's own
  placer (`features/common/components/chart/HorizontalItemPlacer.kt`) handles it, so the risk is
  confined to anyone passing an upstream placer.
- The `isSnapping` dead-flag finding is from a tree-wide grep of `vico/` and `sample/`. meApp
  cannot see it (it is `internal`), so the grep is conclusive for the library, and the behaviour
  described in `FEATURE-4` is genuinely absent.
