# S2-C — New upstream capabilities worth adopting (FORK_POINT `e67e49520` → `v3.3.1` `3d675ccc7`)

Scope: what upstream *gained* in the 236 commits, ranked by value to **this** consumer — meApp's
weight / blood-pressure / baby-weight line charts with a scrubbable marker, dynamic Y range on
scroll, monotone-curved lines, snap scrolling and percentile bands.

Paths are relative to `vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/`
unless written out in full. All paths/signatures are quoted **at `v3.3.1`**.

Evidence base: `git log e67e49520..v3.3.1`, the diffs cited per entry, and the upstream release
notes for `v3.2.0`, `v3.2.1`, `v3.2.3`, `v3.3.0-next.1`, `v3.3.0-next.2`, `v3.3.0`, `v3.3.1`
(`gh release view <tag> --repo patrykandpatrick/vico`).

What the consumer actually does, established by reading
`meApp-bug/Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/components/chart/`
(`ProductChart.kt`, `PrimaryLayer.kt`, `HorizontalItemPlacer.kt`, `GraphDecoration.kt`, `axis/`)
and `.../features/dashboard/components/chart/` (`GraphView.kt`, `GraphPagerView.kt`):

- Line layers only. `LineFill.single`, `LineStroke.Continuous`, `Interpolator.monotone()` (a **fork**
  interpolator — upstream has only `Sharp`, `cubic`, `catmullRom`), a `ShapeComponent(CircleShape)`
  point per entry. **No `AreaFill` anywhere.** No columns, no candlesticks, no pie.
- `CartesianChartHost(..., modifier.height(chartHeight), animateIn = false, zoomState = zoomEnabled = false)`,
  inside a `HorizontalPager` (`GraphPagerView.kt`), keyed by `modelProducer` identity.
- `scrollEnabled = segment != GraphSegment.TOTAL && segmentState.plot?.isSingleWindow != true`
  (`GraphView.kt:198`) — a value that **flips once data loads**.
- Fork-only library files this dossier can speak to:
  `cartesian/SnapFlingBehavior.kt` (131 lines), `cartesian/axis/ListItemPlacer.kt` (118),
  `cartesian/data/ScrollAwareRangeProvider.kt` (363), `cartesian/data/WindowSettleSignal.kt` (35),
  `cartesian/layer/MonotoneInterpolator.kt` (173), `cartesian/marker/ScrubMarkerController.kt` (158),
  `cartesian/InterpolationType.kt` (26).

---

## Visual / capability wins

### V1. Visible-_x_-range preservation across data updates — **the range-jump defect, fixed upstream**

**What it is.** When a chart's `ranges.minX` or `ranges.xStep` changes between frames (new/earlier
data arrives, a segment's window is re-derived), `VicoScrollState` now recomputes `value` so the
same *x*-coordinates stay under the viewport, instead of the viewport snapping to the start edge.
The correction runs inside `update(...)` during measurement, so the frame that draws the new data
already uses the corrected value. LTR and RTL both handled.

**Upstream API.** No new public surface — behaviour inside
`cartesian/VicoScrollState.kt`, plus `private data class ScrollLayout(minX, xStep, xSpacing, startPadding, layoutDirectionMultiplier)`
at the bottom of that file.

**Evidence.** `a1e3d0296` "Preserve the visible x range across data updates (#1539)", hardened by
`2b389ba95` "Fix two scroll-position regressions in VicoScrollState (#1554)":

```kotlin
    val preClampValue = value          // 2b389ba95
    maxValue = context.getMaxScrollDistance(bounds.width, layerDimensions)
    val ranges = context.ranges
    val previous = previousScrollLayout
    if (!initialScrollHandled) { ... }
    else if (previous != null && previous.xSpacing != 0f && ranges.xStep != 0.0 &&
             (ranges.minX != previous.minX || ranges.xStep != previous.xStep)) {
      val startEdgeX = previous.minX +
        (previous.layoutDirectionMultiplier * preClampValue - previous.startPadding) /
          previous.xSpacing * previous.xStep
      value = context.layoutDirectionMultiplier *
        (layerDimensions.startPadding +
         ((startEdgeX - ranges.minX) / ranges.xStep).toFloat() * layerDimensions.xSpacing)
    }
```

`2b389ba95`'s own comment names the exact failure the naive version had: *"when the content shrinks
on the start side (e.g., the data window was trimmed) while the scroll is pinned to the end
(`value == maxValue`), the clamp shifts `value` back by the trimmed amount."*

`2b389ba95` also drops stale zoom compensation (`if (scrollableState.isScrollInProgress) return` in
`scroll(scroll, maxScroll)`) — relevant only if zoom is ever enabled.

**What it does for these charts.** This is the library-side half of the **"range jump"** loading
defect recorded for MOB-3494. The fork's `VicoScrollState.kt` at HEAD has **no** `previousScrollLayout`
— confirmed by `git show HEAD:...VicoScrollState.kt | grep previousScrollLayout` returning nothing —
so this is genuinely new behaviour for the fork, not something already hand-patched.

**Cost to adopt.** Free with the migration, *provided* the fork's 168+/9- rework of `VicoScrollState`
merges around it. `update(...)` is the busiest method on both sides — expect a real conflict here.

**Confidence.** High that the capability is new and relevant. Medium that it alone closes the app's
range-jump symptom (the app also re-derives its own window; both halves need to agree).

---

### V2. `scrollEnabled` can be toggled without destroying the scroll position

**What it is.** `scrollEnabled` was removed from `rememberVicoScrollState`'s `rememberSaveable` key
list and is now pushed in via `SideEffect`, with `internal var scrollEnabled by mutableStateOf(true)`
on the state object.

**Upstream API.** `cartesian/VicoScrollState.kt`:
```kotlin
  internal var scrollEnabled by mutableStateOf(true)
  ...
  SideEffect { state.scrollEnabled = scrollEnabled }
  return state
```

**Evidence.** `1fe9daef9` "Allow toggling scrollEnabled without losing scroll position" —
*"Remove scrollEnabled from VicoScrollState's rememberSaveable keys so that toggling it no longer
recreates the state. Instead, update it via SideEffect."*

**What it does for these charts.** `GraphView.kt:198` computes
`scrollEnabled = segment != GraphSegment.TOTAL && segmentState.plot?.isSingleWindow != true`.
`isSingleWindow` is unknown until the segment's data resolves, so this expression **flips** during
normal loading. At FORK_POINT that flip recreates `VicoScrollState` and resets the scroll to
`initialScroll` — a visible jump at exactly the moment the first data paints. The fork has **not**
applied this: `git show HEAD:...VicoScrollState.kt` still shows `scrollEnabled` in the
`rememberSaveable(...)` key list at line 390 and as a constructor `val` (line 195), not a
`mutableStateOf`.

**Cost to adopt.** Free with the migration, same conflict zone as V1.

**Confidence.** High on the mechanism. High that the consumer's expression flips. Medium on how
visible the resulting jump is in practice (it may be masked by the skeleton).

---

### V3. Snap scrolling in the library (`xSnapStep` / `snapAnimationSpec`)

**What it is.** The scroll position snaps to multiples of a given *x*-step after a fling or drag,
animated, with the extremes (0 and `maxValue`) as additional candidates.

**Upstream API.** `cartesian/VicoScrollState.kt`:
```kotlin
public fun rememberVicoScrollState(
  scrollEnabled: Boolean = true,
  initialScroll: Scroll.Absolute = Scroll.Absolute.Start,
  autoScroll: Scroll = initialScroll,
  autoScrollCondition: AutoScrollCondition = AutoScrollCondition.Never,
  autoScrollAnimationSpec: AnimationSpec<Float> = spring(),
  xSnapStep: Double? = null,
  snapAnimationSpec: AnimationSpec<Float> = spring(),
): VicoScrollState
```
plus `internal fun getSnapDelta(targetValue: Float = value): Float?` and
`internal suspend fun performSnap(...)` on `VicoScrollState`, and
`cartesian/SnapScroll.kt` (new file) holding `getSnapDelta` / `getSnapTarget`.

**Evidence.** `01c7571ad` "Add snap scrolling feature to align scroll position to grid windows (#1372)"
— 498 insertions across `Modifier.kt`, `VicoScrollState.kt`, new `SnapScroll.kt`, `ScrollHandler.kt`,
`MotionEventHandler.kt`. `v3.3.0` release note: *"when `xSnapStep` is set, the scroll position snaps
to multiples of the given x-step after a fling."* Note `v3.3.0-next.2` / `5272a81a9` gave every
`VicoScrollState` constructor parameter a default, so this is **not** a breaking change at `3.3.1`.

**What it does for these charts.** The app currently carries `cartesian/SnapFlingBehavior.kt` (131
lines: `SnapBehaviorConfig`, `SnapBehaviorConfig.SnapAnimation`, `ChartSnapFlingBehavior`,
`rememberChartSnapFlingBehavior`) *plus* app-side `GraphSnapHelper` (~190 lines of `Calendar` work)
wired in `GraphView.kt:197-223`.

**Honest limit — this is a partial replacement, not a drop-in.** Upstream snaps to *uniform multiples
of `xSnapStep` measured from the layout origin*. `GraphSnapHelper` snaps to **calendar** boundaries:
`snapToDayBoundary` for `WEEK`, `snapToMonthBoundary` for `YEAR`, nothing for `MONTH`, nothing for
`TOTAL`. Months are not a uniform millisecond step, and DST makes days non-uniform too (hence
`GraphSnapHelper`'s `HOUR_OF_DAY == 0 || == 1` tolerance). So:
- `WEEK` (snap to a day) — plausibly expressible as `xSnapStep = 1 day` *if* the model's `xStep` and
  `minX` are day-aligned. Needs verification against `GraphUtil.calculateXStep(segment)`.
- `YEAR` (snap to a month) — **not** expressible. Keep the fork behaviour.
- `MONTH` / `TOTAL` — no snapping; pass `xSnapStep = null`.

**Cost to adopt.** Significant if pursued as a deletion (per-segment behaviour change + regression
testing of the scrub marker, which reads snapped positions). Small if adopted only for `WEEK`.
Free if ignored — `xSnapStep` defaults to `null` and changes nothing.

**Confidence.** High on the API. Medium-low that it deletes meaningful fork code, for the calendar
reason above. Flagged anyway because "minimise the fork" makes even a partial reduction interesting.

---

### V4. `shiftExtremeLabels` — anchor first/last *x* labels inward instead of clipping or padding

**What it is.** A new `HorizontalAxis.ItemPlacer` capability: the first label is drawn
`Position.Horizontal.End`-aligned and the last `Start`-aligned, so neither is clipped at the plot
edge — **and no extreme-label padding is added**, so the plot area is not shrunk to make room.

**Upstream API.** `cartesian/axis/HorizontalAxis.kt:919`:
```kotlin
      public fun aligned(
        spacing: (ExtraStore) -> Int = { 1 },
        offset: (ExtraStore) -> Int = { 0 },
        shiftExtremeLines: Boolean = true,
        addExtremeLabelPadding: Boolean = true,
        shiftExtremeLabels: Boolean = false,
      ): ItemPlacer
```
and on the interface, `public fun getShiftExtremeLabels(context: CartesianMeasuringContext): Boolean`.
Applied at `HorizontalAxis.kt:194-207`:
```kotlin
      val shiftExtremeLabels = itemPlacer.getShiftExtremeLabels(this)
      ...
            shiftExtremeLabels && index == 0 -> Position.Horizontal.End
            shiftExtremeLabels && index == labelValues.lastIndex -> Position.Horizontal.Start
```
At FORK_POINT `aligned(...)` has only four parameters (`HorizontalAxis.kt:742`) — the fifth is new.

**Evidence.** `efa1dab65` "feat: add edge-aligned item placer for horizontal axis labels (#1484)".
`v3.3.0` note: *"anchor extreme `HorizontalAxis` labels to the plot-area edges without shrinking the
plot area, preventing clipping."*

**What it does for these charts.** `HorizontalItemPlacer.kt` delegates to `aligned()` and hand-filters
label values to the visible range with padding, and `ProductChart.kt` passes a fork-added
`startInsetDp = ChartStartGap` *"Reserved in the layer rather than asked for through the initial
scroll, so the gap before the first entry is there at the leftmost position too."* These are adjacent
problems, not the same one: `startInsetDp` is about plot padding before the first datum,
`shiftExtremeLabels` is about label clipping. Worth a visual comparison — if the real driver for
`ChartStartGap` was a clipped first date label, `shiftExtremeLabels = true` may retire it.

**Cost to adopt.** Small — one named argument in `HorizontalItemPlacer.kt`, then look at it.

**Confidence.** High that the API is new and does what it says. **Low** that it deletes `ChartStartGap`
— I did not find evidence that label clipping was the motivation, only that the two interact.

---

### V5. Marker targets no longer silently dropped when two layers coincide at the same *x*

**What it is.** Marker-target narrowing compared `canvasX` with **exact `Float` equality** to decide
whether targets occupied more than one horizontal position; if they appeared to, it narrowed to the
closest one. Two layers computing the same *x* by slightly different expressions diverge by ~1 ULP,
which becomes representably distinct at the largest (rightmost) coordinates — so a target was
dropped. Now compared with a sub-pixel tolerance, in an extracted `narrowMarkerTargets`.

**Upstream API.** Internal; `cartesian/CartesianChartHost.kt`.

**Evidence.** `4278255a4` "fix(cartesian): keep all marker targets in combo charts", quoting the
commit body: *"A column and a line sharing an x are computed via slightly different expressions, so
they can diverge by ~1 ULP, which only becomes a representably distinct `Float` at the
largest-magnitude (rightmost) coordinates. That false 'two positions' reading dropped one of the
targets, so the marker sometimes showed only one series."* Covered by new
`cartesian/MarkerTargetNarrowingTest.kt`.

**What it does for these charts.** Upstream frames this as a combo-chart bug, but the mechanism is
*two layers whose entries share an x*, which is exactly `rememberChartLayers` in `ProductChart.kt`:
baby = `arrayOf(percentileLayer, primaryLayer)`; weight-with-secondary =
`arrayOf(primaryLayer, secLayer)`. Scrubbing at the **right-hand** end of the chart — where the
marker spends most of its life, since the charts open scrolled to "now" — is precisely where the ULP
divergence becomes representable. If the baby marker has ever shown the reading without its
percentile context (or vice versa) at the right edge, this is a candidate cause.

**Cost to adopt.** Free with the migration (`CartesianChartHost.kt` is a hot file on both sides —
454+/8- ours, 196+/94- theirs — so expect conflict, but the change itself is small).

**Confidence.** High that the fix is real and tested. **Medium** that this consumer hits it — I did
not reproduce it; the inference is from the two-layer shape and the right-edge condition.

---

### V6. The chart stops consuming taps and long presses it does not act on

**What it is.** `Modifier.pointerInput`'s `onInteraction` callback changed from
`((Interaction) -> Unit)?` to `((Interaction) -> Boolean)?`. Events are consumed only when the
handler says it used them; previously the chart's `detectTapGestures` swallowed taps and long
presses unconditionally, blocking ancestors.

**Upstream API.** `cartesian/Modifier.kt` (internal), with a hand-rolled
`private suspend fun PointerInputScope.detectTapGestures(...)` replacing the foundation one.

**Evidence.** `edc8dc292` "Improve `PointerInputChange` consumption logic (#1419)". The diff turns
each branch into a boolean and defers consumption:
```kotlin
              event.type == PointerEventType.Move -> {
                onInteraction(Interaction.Move(pointerPosition))
                consumeMoveEvents && !scrollState.scrollEnabled
              }
              ...
          if (consume) { event.changes.fastForEach { it.consume() } }
```
`v3.2.0` release note: *"Fixed an issue where charts with markers could needlessly consume taps and
long presses, blocking upstream detection."*

**What it does for these charts.** These charts live inside a `HorizontalPager`
(`GraphPagerView.kt:73`) and carry a `ScrubMarkerController` with a 200 ms long-press delay
(`ScrubMarkerController.kt:54 delayMs: Long = 200L`). Gesture ownership between pager swipe, chart
scroll and scrub start is the contested resource here; a chart that stops claiming events it ignored
gives the pager back its swipes.

**Cost to adopt.** Free with the migration — but `Modifier.kt` is the single hottest contested file
(234+/27- ours, 121+/25- theirs) **and the `onInteraction` signature change touches
`ScrubMarkerController`'s integration point**. Budget real merge time; the *capability* is free, the
*merge* is not.

**Confidence.** High on the change. Medium on the size of the improvement for this app, since the
fork has already reworked gesture handling around scrubbing.

---

### V7. `initialAnimationSpec` replaces the boolean `animateIn`

**What it is.** `CartesianChartHost` gains `initialAnimationSpec: AnimationSpec<Float>? = animationSpec`.
`null` skips the initial reveal; `animateIn: Boolean` is deprecated. The intro animation and the
data-diff animation are now separately specifiable.

**Upstream API.** `cartesian/CartesianChartHost.kt`:
```kotlin
public fun CartesianChartHost(
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
```
(The `animateIn: Boolean` overload survives, deprecated.)

**Evidence.** `ac445d2e8` "Add separate initial-animation settings (#1514)"; `v3.3.0` release note.

**What it does for these charts.** `GraphView.kt:101` passes `animateIn = false` today — that is
exactly `initialAnimationSpec = null`, a one-line swap off a deprecated parameter. The *new* freedom
is that the first paint can now be silent while segment-to-segment data changes still animate, which
is currently an all-or-nothing choice.

**Cost to adopt.** Free with the migration (one argument rename in meApp).

**Confidence.** High.

---

### V8. Content-area chart sizing (`chartAreaHeight`)

**What it is.** The default height now sizes the **coordinate system**, with the marker, axis margins
and legend adding to it, instead of a total height inside which everything is squeezed. Default 192 dp
for Cartesian. `BaseAxis.Size.Fraction` is deprecated; `HorizontalAxis`'s auto-height cap becomes a
fixed 64 dp instead of a fraction of canvas height. `MeasuringContext.canvasSize` is deprecated in
favour of `canvasWidth` (the canvas height is no longer known at measure time).

**Upstream API.** `chartAreaHeight: Dp = Defaults.CARTESIAN_CHART_AREA_HEIGHT.dp` on all three
`CartesianChartHost` overloads; new `common/ChartHostBox.kt`.

**Evidence.** `a3e3d6cd0` "feat: size charts by their content area, not their total height". The
commit's own screenshot-test churn is the proof it is visible: all ten sample-chart baselines changed
(e.g. `BasicLineChartScreenshotTest_0.png` 51326 → 55159 bytes).

**What it does for these charts.** `GraphView.kt:98` pins `modifier.height(chartHeight)`, and the
release note is explicit that *"`Modifier.height` and similar still constrain the component bounds"* —
so adopting nothing changes nothing about the outer box. The opportunity is the inverse: the app
currently pins **total** height, so the plot area silently shrinks whenever the *x*-axis labels grow
(longer month names, larger font scale, a taller marker). Pinning `chartAreaHeight` instead would
make the plot height stable across products and segments and let the component grow.

**Caveat, and it cuts the other way.** The `HorizontalAxis` auto-height cap changing from a canvas
fraction to a flat 64 dp will shift this chart's layout *during the migration even if the app adopts
nothing*. Treat that as a required visual regression check, not an optional feature.

**Cost to adopt.** Small to adopt (`chartAreaHeight` + drop `Modifier.height`), but the *regression
check* is mandatory regardless.

**Confidence.** High that the behaviour changed. Medium on whether the app wants the new model —
`rememberChartHeight(product)` and `LocalDashboardViewportHeight` suggest the total height is
deliberately controlled from outside, which argues for keeping `Modifier.height`.

---

### V9. Series keys

**What it is.** Models and series carry keys (`lineModel { series(key = ...) }`), surfaced as
`LineCartesianLayerModel.Entry.seriesKey` and `CartesianLayerDrawingModel.seriesKeys`, and used by
the drawing-model interpolator to associate old and new series across a data update.

**Upstream API.** `cartesian/data/LineCartesianLayerModel.kt`,
`cartesian/data/CartesianLayerDrawingModel.kt`; `LineCartesianLayer.LineProvider.getLine` gains a
**leading** `seriesKey: Any` parameter (the one declared breaking change in `v3.2.0`).

**Evidence.** `a4c0f12ed` "feat(cartesian): track series by key" (1094 insertions / 297 deletions
across 28 files), with `cartesian/data/CartesianLayerDrawingModelInterpolatorTest.kt` new. `v3.2.0`
notes 6–9: *"it lets `CartesianLayerDrawingModelInterpolator` implementations better handle
nonfinal-series deletion."*

**What it does for these charts.** Series counts here are not constant: BP plots systolic+diastolic,
weight plots one series plus an optional secondary overlay, baby plots a percentile band layer whose
series count depends on the bands. When a non-final series disappears, the index-based interpolator
cross-fades the wrong pairs — a visible wrong-line-morphs-into-wrong-line during the diff animation.
Keys fix the association.

**Cost to adopt.** Free to *receive* (the breaking `getLine` signature change arrives with the
migration whether or not keys are used). Small to *benefit*: pass `key = ...` at the `lineModel`
call sites in the app's model builders.

**Confidence.** High on the mechanism. Medium that the app sees the artefact today.

---

### V10. Reveal (sweep) animation for `LineCartesianLayer`

**What it is.** The line's first appearance animates as a clip sweeping in from the start edge,
instead of a global fade.

**Upstream API.** `cartesian/data/CartesianLayerDrawingModelInterpolator.kt:55`:
```kotlin
    public fun line(
      sweep: Boolean = false
    ): CartesianLayerDrawingModelInterpolator<
      LineCartesianLayerDrawingModel.Entry, LineCartesianLayerDrawingModel>
```
Passed as `drawingModelInterpolator` on `rememberLineCartesianLayer`. Drawn at
`cartesian/layer/LineCartesianLayer.kt:536-551` via `sweepFraction` and `canvas.clipRect`.

**Evidence.** `5cbc0b24c` "Add reveal animation (#1516)" + sample `RevealLineChart.kt`. **Take `3.3.1`
specifically**: `632d413f0` "fix(compose): keep sweep canvas operations balanced" fixes an
`IllegalStateException("Underflow in restore—more restores than saves")` crash on Android 10 caused
by an ART JIT bug (`b/553511933`) evaluating `sweepFraction < 1f` twice with different answers. That
crash is the entire content of the `3.3.1` release.

**What it does for these charts.** A health chart revealing left-to-right reads as "your history
drawing itself" and is a better first impression than a fade. Purely optional polish — and the app
currently suppresses the intro animation entirely (`animateIn = false`), so adopting this means
reversing a product decision, not just flipping a flag.

**Cost to adopt.** Small (one argument), but it is a design change that needs sign-off.

**Confidence.** High on the API; this is a product call, not an engineering one.

---

### V11 (low). `ExtremesHorizontalAxisItemPlacer` tracks the visible viewport

`dfdbebdce` (#1513) changes the `extremes` placer from `listOf(ranges.minX, ranges.maxX)` to
`listOf(visibleXRange.start, visibleXRange.endInclusive)`. **The consumer uses `aligned()`, not
`extremes()`** (`HorizontalItemPlacer.kt`), so this is inert today. Listed only because a
first/last-visible-date axis is a plausible future ask and the capability now exists without a fork.

### V12 (low). Configurable axis title positions

`66ad84593` (#1489) adds `titlePosition` to `HorizontalAxis`/`VerticalAxis` and `BaseAxis.TitlePosition`
(`Side`, `End`). These charts have **no axis titles** (`axis/Horizontalxis.kt`, `axis/VerticalAxis.kt`
set `valueFormatter` only). Inert.

### V13 (low). `VerticalAxis` `Size.Text` measures the label, not the title

`d25928f66` (#1438). The consumer's `VerticalAxis` uses `ListItemPlacer` and does not configure
`Size.Text`. Inert unless that changes.

---

## Performance wins

### P1. Line point collection is now sliced to the **visible** range, not the full data range — **the big one**

**What it is.** `collectPointsAndVisibleIndexRange` built its `points: MutableList<Offset>` by
iterating `series.forEachIn(minX = ranges.minX, maxX = ranges.maxX)` — i.e. **every entry in the
chart's whole *x* range, every frame**, allocating an `Offset` per entry. It now slices to the visible
*x* range plus a padding the interpolator declares.

**Upstream API.**
`cartesian/layer/LineCartesianLayer.kt:342-360` — new member on the public `Interpolator` interface:
```kotlin
  public interface Interpolator {
    /**
     * Returns the number of extra points required on either side of the visible range, beyond the
     * standard single extra point already included in `visibleIndexRange`. `null` indicates that
     * the full series should be provided.
     */
    public val visiblePadding: Int?
      get() = 0
```
`cartesian/data/CartesianLayerModel.kt:113-140` — `getSliceIndices` rewritten to
`(visibleXRangeStart, visibleXRangeEnd, visiblePadding: Int? = 1): IntRange`.
`cartesian/layer/CatmullRomInterpolator.kt:26` — `override val visiblePadding: Int = 1`.

**Evidence.** `608587edb` "fix: simplify visible slice padding". The replaced call sites, quoting the
diff:
```diff
-    val maxX = ranges.maxX
     val xStep = ranges.xStep
+    val visibleXRange = getVisibleXRange()
+    val visiblePointPadding = interpolator.visiblePadding?.plus(1)
...
-    series.forEachIn(minX = minX, maxX = maxX) { entry, _ ->
+    val visibleIndices =
+      series.getSliceIndices(visibleXRange.start, visibleXRange.endInclusive, visiblePointPadding)
+    if (visibleIndices.isEmpty()) return IntRange.EMPTY
+    series.subList(visibleIndices.first, visibleIndices.last + 1).forEach { entry ->
       points += Offset(getDrawX(entry), getDrawY(entry))
     }
```
The same substitution is applied to `forEachPointInBounds` (the loop that draws points and data
labels). `visiblePadding` does not exist at FORK_POINT:
`git show e67e49520:...LineCartesianLayer.kt | grep -c visiblePadding` → `0`.

**What it does for these charts.** The `TOTAL` segment plots a user's entire weight history in one
model and shows a window of it. Before: a per-frame `Offset` allocation and loop over *the whole
history*, per line, per layer — and baby charts stack a percentile layer on top. After: the visible
window plus one or two points. This is the single largest per-frame saving in the range for a
scrubbing, scrolling line chart over a long series.

**Cost to adopt.** Free with the migration — **with one required action.** The fork's
`MonotoneInterpolator` (`cartesian/layer/MonotoneInterpolator.kt`, 173 lines) implements
`LineCartesianLayer.Interpolator` and will inherit the new `visiblePadding get() = 0` default.
Fritsch–Carlson tangents need neighbours on both sides, so the fork must decide and declare its
padding explicitly (`catmullRom` chose `1`) or the curve may change shape at the viewport edges
while scrolling. **Verify this, do not assume `0` is right.**

**Confidence.** High that the work removed is real and large. **Medium** on the exact magnitude — I
did not benchmark it; the claim rests on reading the before/after loop bounds.

---

### P2. One offscreen buffer per **frame** instead of one per **series** during fade-in

**What it is.** `LineCartesianLayer.drawInternal` wrapped each series in its own `saveLayer` while
`opacity < 1`. Opacity is layer-global, so the per-series layers were pure waste. The
`saveLayer`/`restore` pair now wraps the whole series loop.

**Upstream API.** Internal; `cartesian/layer/LineCartesianLayer.kt`.

**Evidence.** `72a0ccb87` "perf(line-layer): composite all series onto a single layer", with a
measured number in the commit body: *"On a Pixel 10, the intro animation of a five-line, five-column
combo chart fell from ~12.8 to ~9.9 ms/frame, almost entirely from a ~2 ms reduction in the
RenderThread record phase."* The diff hoists the pair:
```diff
+      saveLayer(opacity = drawingModel?.opacity ?: 1f)
       model.series.forEachIndexed { seriesIndex, series ->
-        saveLayer(opacity = drawingModel?.opacity ?: 1f)
...
-        canvas.restore()
       }
+      canvas.restore()
```
Shipped as `v3.2.1`, whose release note adds the visual side effect: *"This also prevents
interseries bleed-through during the intro animation."*

**What it does for these charts.** Applies during every animated data change, not only the intro —
segment switches, unit changes, a baby's data loading. Baby charts run a percentile layer with
multiple series plus the primary layer, so the series count is where the saving comes from. The
bleed-through fix is a genuine visual win too: overlapping percentile curves currently composite
*through* each other mid-animation.

**Cost to adopt.** Free with the migration; it is a 12-line change in a file the fork has reworked
(320+/54-), so re-apply it by hand if the merge drops it.

**Confidence.** High — measured upstream, mechanism verified in the diff.

---

### P3. `ShapeComponent` draws resolved `Outline`s directly instead of replaying them into a `Path`

**What it is.** The draw path was: `path.rewind()` → `createOutline(...)` → replay the outline into
the `Path` (`addRect` / `addRoundRect` / `addPath`) → `canvas.drawPath`. Now: `createOutline(...)` →
`canvas.drawOutline`. Rectangles and rounded rectangles skip path construction entirely.

**Upstream API.** Internal; `common/component/ShapeComponent.kt`.

**Evidence.** `2fbd1ab51` "Draw shape outlines directly (#1563)":
```diff
-      path.rewind()
       val width = adjustedRight - adjustedLeft
       val height = adjustedBottom - adjustedTop
+      val outline = shape.createOutline(Size(width, height), layoutDirection, density)
       applyBrushes(Size(width, height))
-      shape.outline(density, layoutDirection, path, 0f, 0f, width, height)
...
-        canvas.drawPath(path, paint)
+        canvas.drawOutline(outline, paint)
...
-        canvas.drawPath(path, strokePaint)
+        canvas.drawOutline(outline, strokePaint)
```
The replaced helper at FORK_POINT (`common/Shape.kt:29-47`) confirms the double work — it called
`createOutline` **and then** replayed the result into the path:
```kotlin
    when (val outline = createOutline(Size(width, height), layoutDirection, density)) {
      is Outline.Rectangle -> addRect(Rect(left, top, right, bottom))
      is Outline.Rounded -> addRoundRect(outline.roundRect.translate(Offset(left, top)))
      is Outline.Generic -> addPath(outline.path, Offset(left, top))
    }
```

**What it does for these charts.** `PrimaryLayer.kt` gives every line a
`PointProvider.single(Point(component = rememberShapeComponent(Fill(lineColor), CircleShape, ...)))`
— so a `ShapeComponent` is drawn **once per visible data point, per series, per frame**. `CircleShape`
resolves to `Outline.Rounded`, which previously meant a `rewind` + `addRoundRect` + `drawPath` per
point and now means a `drawOutline`. The marker pill
(`GraphDecoration.kt`, `rememberShapeComponent(fill, shape = CircleShape)`) benefits too, as does
every axis label background. Combines multiplicatively with P1: P1 cuts *how many* points are drawn,
P3 cuts *the cost of each*.

**Cost to adopt.** Free with the migration — `common/component/ShapeComponent.kt` is not on the
fork's modified list.

**Confidence.** High on the mechanism. Medium on magnitude (unmeasured upstream).

---

### P4. `UpdateReceiver` leak when a chart's identity changes

**What it is.** Registration, transformation and unregistration with `CartesianChartModelProducer`
now key off the effect-stable chart ID rather than a value that mutates on recomposition, so the old
receiver is actually unregistered.

**Upstream API.** Internal; `cartesian/data/CartesianChartModel.kt` (13 insertions / 9 deletions).

**Evidence.** `e8cc75ed1` "fix: prevent UpdateReceiver leak when a chart's identity changes (#1549)".
`v3.3.0` release note: *"Registration, transformation, and unregistration with
`CartesianChartModelProducer` now use the effect-stable chart ID rather than a value that mutates on
recomposition."*

**What it does for these charts.** `GraphView.kt:93` wraps the host in `key(modelProducer)` precisely
because *"swapping the producer instance on the same host (e.g. switching baby, which hands this host
a different per-baby producer) throws 'A new CartesianChartModelProducer was provided'."* Chart
identity changing is therefore a **routine** event here — every baby switch, every product switch.
Each one leaked a receiver that kept running transformations against a dead chart. This is a leak
*and* a per-update CPU cost that grows with how much the user navigates.

**Cost to adopt.** Free with the migration; `CartesianChartModel.kt` is not on the fork's modified
list.

**Confidence.** High on the fix. High that this consumer changes chart identity often. Medium on the
severity of the leak (no measurement).

---

### P5 (low). Zero-duration scroll animations no longer cost a frame

`b7b8674cb` "Fix: instantaneous scrolling for 0-duration `VicoScrollState` animations (#1429)";
`v3.2.0`: *"Fixed zero-duration automatic scrolls being delayed by a frame."* Relevant only if the
app ever issues a zero-duration programmatic scroll — I found no such call. Free with the migration,
but `VicoScrollState.kt` is the fork's hottest file, so it may be lost in the merge; low stakes.

### P6 (low). `CartesianValueFormatter.Decimal` / `DefaultValueFormatter` equality

`a9bd3e46a` (#1405) makes `equals`/`hashCode` consider `decimalSeparator`, `thousandsSeparator`,
`prefix`, `suffix` so changing them actually triggers recomposition. **The consumer uses lambda
`CartesianValueFormatter { _, value, _ -> ... }` throughout** (`axis/Horizontalxis.kt:55`,
`axis/VerticalAxis.kt:83`), never `Decimal`. Correctness fix the app does not touch — and lambdas have
identity equality, which is a *different* recomposition concern this commit does not address.

---

## Adopt now / adopt later / ignore

### Adopt now (take with the migration; high value, low marginal cost)

| # | Item | Why now |
|---|---|---|
| P1 | Visible-slice line point collection (`608587edb`) | Largest per-frame saving in the range. **Must** settle `MonotoneInterpolator.visiblePadding` as part of the merge — this is not optional, the default is `0`. |
| P2 | Single `saveLayer` per frame (`72a0ccb87`) | Measured 12.8 → 9.9 ms/frame upstream; also removes inter-series bleed-through on percentile layers. Re-apply by hand if the merge drops it. |
| V1 | Visible-_x_-range preservation (`a1e3d0296` + `2b389ba95`) | Library-side half of the MOB-3494 "range jump". Not present in the fork. |
| V2 | `scrollEnabled` toggle keeps scroll position (`1fe9daef9`) | `GraphView.kt:198` flips this value during loading; the fork still has `scrollEnabled` in the saver keys. |
| P3 | `ShapeComponent.drawOutline` (`2fbd1ab51`) | One `ShapeComponent` per data point per frame. Free — file untouched by the fork. |

Close behind, and also free: **P4** (`e8cc75ed1`, receiver leak — chart identity changes on every
baby switch here) and **V7** (`initialAnimationSpec`, a one-line swap off deprecated `animateIn`).

### Adopt later (real value, needs a decision or design work)

- **V5** marker-target tolerance (`4278255a4`) — arrives free, but *verify* against the baby chart's
  two-layer scrub at the right edge before claiming it fixed anything.
- **V6** pointer-consumption rework (`edc8dc292`) — the pager/scrub gesture win is real, but
  `onInteraction` changes signature and `Modifier.kt` is the most contested file in the migration.
- **V9** series keys (`a4c0f12ed`) — the breaking `getLine(seriesKey, ...)` arrives regardless;
  passing real keys is a follow-up.
- **V4** `shiftExtremeLabels` (`efa1dab65`) — one named argument, then look at whether `ChartStartGap`
  is still needed. Cheap experiment, uncertain payoff.
- **V8** `chartAreaHeight` (`a3e3d6cd0`) — the *regression check* is mandatory (the `HorizontalAxis`
  64 dp cap lands either way); *adopting* the new sizing model is optional and may conflict with
  `LocalDashboardViewportHeight`.
- **V3** `xSnapStep` (`01c7571ad`) — only the `WEEK` segment is plausibly expressible. Pursue only if
  the fork-reduction is worth a per-segment behaviour change.
- **V10** reveal sweep (`5cbc0b24c`, crash-fixed in `632d413f0`) — product decision; the app
  currently suppresses the intro animation on purpose.

### Ignore (honestly: this app will not use these)

- **Everything pie.** `097dad96e`, `bfc38bb56`, `d8a25a5da`, `56de686f8`, `632d413f0`'s pie half,
  `PieChartRenderer.kt`, `PieChartImage.kt`. No pie charts here, none planned.
- **`AreaFill` / Catmull-Rom inversion fix** (`b4be7364e`, `v3.2.3`). Impressive and well-reasoned,
  but I grepped the consumer: **zero `AreaFill` usages**, and the lines use the fork's `monotone()`,
  not `catmullRom()`. Both halves of the fix are unreachable from this app.
- **`compose-glance`** (`f2923084f`, `9604c552b`) — Glance widget hosting. No widget in meApp.
  `CartesianChart.renderToImageBitmap` is `@RestrictTo(LIBRARY_GROUP)` anyway, so a "share your
  chart as an image" feature could not call it without another fork patch.
- **`VicoZoomState.animateZoom`** (`841ca6b9c`) — `GraphView.kt:102` passes
  `rememberVicoZoomState(zoomEnabled = false)`. Zoom is off by design.
- **Axis `titlePosition`** (`66ad84593`) — no axis titles in these charts.
- **Column/candlestick work** — `1c1a99927` (grouped-column tap selection), `4fc3b7e53`,
  `ColumnCartesianLayerDrawingModelInterpolator`, `CandlestickCartesianLayerDrawingModelInterpolator`.
  Line layers only.
- **`ee3d554db`** (`HorizontalLine`/`HorizontalBox` equals) — touches `vico/core` and
  `vico/multiplatform`, modules that **do not exist at `v3.3.1`**. A v2.x backport riding in the log;
  not a `3.3.1` capability at all. (The app's goal line is a `VerticalAxis.MarkerDecoration`
  regardless — `GraphDecoration.kt`.)
- **`a132e3508`** (`rememberLine` `dataLabelValueFormatter` key) — these lines set no data labels.
- **`bb9cfed7e`** (`ShapeComponent` margins) — the app's shape components set no margins.
- **`TextComponent.kt`'s 120 changed lines** — worth saying out loud so nobody re-investigates: that
  file's entire delta in this range is `f23073cf4` "chore: update ktfmt to 0.64" plus the
  `d4a7b65b9` source-sharing move. **Pure reformatting. No behaviour, no performance.**

---

## Code the fork could delete, honestly assessed

This is the category the locked "minimise the fork" decision cares most about. The result is thinner
than one would hope — upstream converged on adjacent problems, rarely the same ones.

| Fork file | Lines | Upstream candidate | Verdict |
|---|---|---|---|
| `cartesian/SnapFlingBehavior.kt` | 131 | `xSnapStep` / `snapAnimationSpec` / `SnapScroll.kt` (`01c7571ad`) | **Partial.** Upstream snaps to uniform multiples of an *x*-step; `GraphSnapHelper` snaps to **calendar** boundaries (day for `WEEK`, *month* for `YEAR`). Months are not a uniform step. `WEEK` may port; `YEAR` cannot. |
| `cartesian/VicoScrollState.kt` patches | — | `a1e3d0296`, `2b389ba95`, `1fe9daef9` | **Reduction, not deletion.** Upstream now owns range-preservation and the `scrollEnabled` toggle; whatever the fork added for those specific behaviours can go. The fork's `visibleXRange` / `getVisibleAxisLabels` / `getInterpolatedYValues` / `isUserScrolling` / `isScrollFrozen` additions have **no** upstream equivalent and stay. |
| `cartesian/axis/HorizontalAxis.kt` `startInsetDp` | — | `shiftExtremeLabels` (`efa1dab65`) | **Unproven.** Adjacent problem (label clipping) rather than the same one (plot padding before the first datum). Worth one visual experiment; do not plan on it. |
| `cartesian/axis/ListItemPlacer.kt` | 118 | — | **Keep.** A `VerticalAxis.ItemPlacer` taking an explicit tick list. Upstream added nothing comparable; all the new placer work (`efa1dab65`, `dfdbebdce`) is on the *horizontal* axis. |
| `cartesian/data/ScrollAwareRangeProvider.kt` + `WindowSettleSignal.kt` | 398 | — | **Keep.** No upstream equivalent. `a1e3d0296` preserves the *x* viewport; nothing upstream recomputes the *Y* range from the visible window. This remains the fork's largest and most load-bearing addition. |
| `cartesian/layer/MonotoneInterpolator.kt` | 173 | — | **Keep, and change.** Upstream still ships only `Sharp`, `cubic`, `catmullRom` (`LineCartesianLayer.kt:370-412`). But it must now declare `visiblePadding` (see P1) — a keep that is *not* a no-op. |
| `cartesian/marker/ScrubMarkerController.kt` | 158 | — | **Keep.** No upstream scrub/long-press-to-track marker controller. `edc8dc292` only changes how gestures are consumed around it. |
| `cartesian/InterpolationType.kt` | 26 | — | **Keep.** |

**Net:** of ~1,000 lines of fork-only library code, upstream v3.3.1 plausibly retires **at most**
part of `SnapFlingBehavior.kt` (131 lines) and some `VicoScrollState` patching. The fork's two
genuine inventions — scroll-aware **Y** ranging and the scrub marker — have no upstream counterpart
and will not shrink. Anyone planning this migration on the hope that v3.3.1 absorbs the fork should
recalibrate: the wins here are in **speed and correctness of upstream's own code**, not in deletion.

