# S2-A — Breaking-change and structural-change register

Range: `e67e49520` (FORK_POINT, upstream v3.1.0-alpha.3+8, 2026-03-26) → `v3.3.1` = `3d675ccc7` (2026-08-28), 236 commits.
Repo read-only. All claims below are from `git show`/`git diff`/`git log` against those refs, plus upstream
GitHub release notes for `v3.2.0`, `v3.2.1`, `v3.2.2`, `v3.2.3`, `v3.3.0-next.1`, `v3.3.0`, `v3.3.1`
(fetched via `gh release view --repo patrykandpatrick/vico`).

Our 26 modified library files, as established by `git diff --name-status e67e49520..HEAD -- vico/`, are referenced
throughout by short path under `vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/`.

---

## 0. CORRECTION to a premise in the brief (read first)

**The module consolidation the brief describes did not happen in this range. It happened *before* the fork point.**

- At `e67e49520`, `git ls-tree -d --name-only e67e49520:vico/` returns exactly: `compose`, `compose-m2`,
  `compose-m3`, `views`. There is no `core`, no `multiplatform`, no `multiplatform-m2`/`-m3`, no
  `compose-m3-complete`.
- Those modules were folded into `compose`/`views` by `161ccf051` *“Restructure library (#1275)”*, 2026-01-04.
  `git merge-base --is-ancestor 161ccf051 e67e49520` succeeds — it is an ancestor of our fork point.
  `git log -5 -- vico/core` and `-- vico/multiplatform` both terminate at `161ccf051`.
- `compose-m3-complete` has **no history at all** in this repo (`git log -- vico/compose-m3-complete` is empty).

So the only module-set changes inside `e67e49520..v3.3.1` are:

| Change | Commit | Evidence |
|---|---|---|
| `vico/views` **deleted** | `55cdd2b3b` *refactor: drop views from active branch* | `git log --diff-filter=D e67e49520..v3.3.1 -- vico/views` |
| `vico/compose-glance` **added** | `f2923084f` *Add Glance widget support for Cartesian charts (#1371)*; build file created by renaming `vico/views/build.gradle.kts` (`R056`) | `git diff --diff-filter=R -M --name-status` |
| `settings.gradle.kts` include list rewritten; sample split `sample:charts:*` → `sample:shared`/`desktop`/`web` | various | `git show v3.3.1:settings.gradle.kts` |

**Consequence for us: none, structurally.** All our work is in `vico/compose`, which neither moved nor was
renamed. `vico/compose/src/commonMain/.../compose/**` is the same path at both refs, and **no file in
`vico/compose/src/commonMain` was deleted** in the range (`git diff --diff-filter=D` returns empty). Twelve files
were added; two were moved *within* `compose` (entry 3 below).

The one real structural hazard the range introduces is §1 below — a transient source tree that will wreck
any attempt to rebase or cherry-pick across the middle of the range.

---

## SEVERITY 1 — will break the build or silently corrupt a replay

### 1.1 Transient `vico/shared` source tree: our 105 commits cannot be rebased/cherry-picked naively across it

**What changed.** Between `d4a7b65b9` *refactor: share compose and views implementation sources* (2026-06-07) and
`8be98e01e` *refactor: inline compose shared sources*, upstream **deleted 26 files out of
`vico/compose/src/commonMain`**, re-created them under `vico/shared/src/commonMain/.../vico/shared/**`,
generated the Compose and Views copies from there at build time, then (at `8be98e01e`) deleted `vico/shared`
again and inlined the files back into `vico/compose`. `git ls-tree -d --name-only d4a7b65b9:vico/` shows
`shared` present; the same command at `8be98e01e` shows it gone.

Of **our 26 modified files**, four were round-tripped through `vico/shared`:
`cartesian/data/CartesianChartRanges.kt`, `cartesian/data/MutableCartesianChartRanges.kt`,
`cartesian/layer/CartesianLayerMargins.kt`, `cartesian/axis/HorizontalAxisItemPlacers.kt`.
(`common/Collections.kt` and `cartesian/data/CartesianLayerModel.kt` also round-tripped; we don't modify them.)

**Evidence.** `git show --name-status d4a7b65b9 -- vico/compose/src/commonMain | grep '^D'`;
`git ls-tree -r --name-only d4a7b65b9:vico/shared`; `git show --stat 8be98e01e`.

**How it breaks us.** Not a compile break — a *process* break. A `git rebase` or `git cherry-pick` of our
commits onto `v3.3.1` will, for those four files, see delete-then-add rather than a modification, and will either
drop our hunks or raise delete/modify conflicts. Rename detection across two hops with a package rename
(`vico.compose.*` → `vico.shared.*` → `vico.compose.*`) is unreliable. `git log --follow` on those paths is
likewise untrustworthy for archaeology.

**What the migration must do.** Treat the port as a **re-application of a diff against `v3.3.1`**, not a replay of
105 commits. Compute `git diff e67e49520..HEAD -- <file>` per file and apply by hand. Do not attempt
`rebase --onto v3.3.1`.

---

### 1.2 `CartesianChartHost` — parameter list rewritten; `animateIn` deprecated; new `chartAreaHeight`

**What changed.**
- `animateIn: Boolean = true` is gone from the primary overload, replaced by
  `initialAnimationSpec: AnimationSpec<Float>? = animationSpec`. A `@Deprecated` overload taking
  `animateIn: Boolean` (no default) is retained.
- New parameter `chartAreaHeight: Dp = Defaults.CARTESIAN_CHART_AREA_HEIGHT.dp` on **both** public overloads.
- The internal `CartesianChartHostImpl` gained `modifier: Modifier` and `chartAreaHeight: Dp` parameters.
- `CartesianChartHostBox` (private, `Box(modifier.heightIn(max = CHART_HEIGHT.dp).fillMaxWidth())`) was
  **deleted** and replaced by `ChartHostBox(modifier, chartAreaHeight, measureExtras)` in the new file
  `common/ChartHostBox.kt`.
- `chart.prepare(context, layerDimensions)` → `chart.prepare(context, layerDimensions, size)`.
- New call `chart.getVerticalExtras(context, measureLayerDimensions)` used as the layout measure hook.
- `measuringContext.value.canvasWidth = size.width` added alongside `canvasSize`.
- `onInteraction` changed from `((Interaction) -> Unit)?` to `((Interaction) -> Boolean)?`.
- The inline marker-target narrowing block was extracted to `internal fun narrowMarkerTargets(targets, pointX)`.
- `scrollState.autoScroll(...)` is now launched with `CoroutineStart.UNDISPATCHED`.

**Evidence.** `git diff e67e49520..v3.3.1 -- .../cartesian/CartesianChartHost.kt`. Commits:
`a3e3d6cd0` *feat: size charts by their content area, not their total height*,
`ac445d2e8` *Add separate initial-animation settings (#1514)*,
`4278255a4` *fix(cartesian): keep all marker targets in combo charts*,
`b7b8674cb` (#1429), `edc8dc292` (#1419).

**How it breaks us: compile.** `CartesianChartHost.kt` is one of our two hottest files
(ours 454+/8-, theirs 196+/94-). Our fork added two of our own parameters to the same signature —
`flingBehavior` and `initialMarkerX`, both of which meApp passes
(`app/src/main/java/com/dmdbrands/gurus/weight/features/dashboard/components/chart/GraphView.kt:103-104`).
Our parameters and upstream's `initialAnimationSpec`/`chartAreaHeight` land in the same parameter list and the
same `CartesianChartHostImpl` call sites. Guaranteed conflict; must be merged by hand.

**How it breaks meApp: compile warning + behaviour.** meApp calls `animateIn = false` at
`GraphView.kt:101` and `SnapshotLineChart.kt:166`. Both still compile (the deprecated overload takes a named
`animateIn`), but both emit deprecation warnings and should become `initialAnimationSpec = null`.

**Hits:** `cartesian/CartesianChartHost.kt`, `cartesian/CartesianChart.kt`, `common/Defaults.kt`.

**Migration must:** re-site our `flingBehavior`/`initialMarkerX` parameters inside upstream's new signature;
decide whether our bespoke host box (we already deleted upstream's `heightIn(max = CHART_HEIGHT.dp)` clamp —
see §3.1) is still needed now that `ChartHostBox` honours a fixed outer height; adapt our `onInteraction`
to return `Boolean`.

---

### 1.3 `CartesianChart.getXStep` arity change (3 params) + `prepare`/`measure`/`getVerticalExtras` split

**What changed.**
- `getXStep: ((CartesianChartModel) -> Double)` → `getXStep: ((CartesianChartModel, Double, Double) -> Double)`
  on the `CartesianChart` constructor, on `copy`, and on `rememberCartesianChart`. The extra arguments are the
  **final** `minX` and `maxX`. `@Deprecated` single-argument overloads are retained for the protected
  constructor, `copy`, and `rememberCartesianChart`.
- Default implementation is now `getDefaultXStep(model, minX)`, which folds `minX` offset into the GCD and
  `require`s the result be non-zero with the message *“The x-values are too precise. The maximum precision is
  four decimal places.”* — **a new runtime throw path**.
- `updateRanges` reordered: ranges are populated **first**, then `ranges.xStep = getXStep(model, ranges.minX, ranges.maxX)`.
  Previously `xStep` was computed before `forEachWithLayer`.
- `prepare` split into `private fun measure(context, layerDimensions): Float` (legend height; width-only, no
  canvas height) and `internal fun prepare(context, layerDimensions, canvasSize: Size)`; a new
  `internal fun getVerticalExtras(context, layerDimensions): Float` returns `layerMargins.vertical + legendHeight`.
- `legend?.getHeight(context, canvasSize.width)` → `legend?.getHeight(context, canvasWidth)`.

**Evidence.** `git diff e67e49520..v3.3.1 -- .../cartesian/CartesianChart.kt`. Commits: `9745cfb91`
*fix: account for range origin in default x step* (also `b09702dd1` #1320), `a3e3d6cd0`.

**How it breaks us: compile + silent behaviour.** `CartesianChart.kt` is ours-203+/12-, theirs-228+/18-.
Our range/scroll work reads and writes `MutableCartesianChartRanges` and we modified `MutableCartesianChartRanges.kt`
and `CartesianChartRanges.kt`; the reorder of `updateRanges` changes **when** `xStep` is visible relative to
range population, which is exactly the window our `ScrollAwareRangeProvider`/`WindowSettleSignal` logic lives in.

**How it breaks meApp: compile.** meApp passes a one-argument `getXStep` in exactly two places, both directly to
`rememberCartesianChart`:
- `ProductChart.kt:99` — `remember(segment) { { _: CartesianChartModel -> GraphUtil.calculateXStep(segment) } }`.
  Explicitly typed, so it binds unambiguously to the deprecated overload and compiles with a warning.
- `SnapshotLineChart.kt:149` — `{ GraphUtil.calculateXStep(GraphSegment.WEEK) }`, a lambda with no declared
  parameters. Kotlin will not conform a no-parameter lambda to a three-parameter function type, so it should
  still resolve to the deprecated arity-1 overload — but with two candidate overloads and an untyped lambda this
  is the one call in meApp that may go ambiguous. Verify by compiling.

Both of those same call sites also pass **our fork's own added parameters** `visibleLabelsCount` and
`startInsetDp` to `rememberCartesianChart` (`ProductChart.kt:101` ff., `SnapshotLineChart.kt:147-148`), so our
parameter additions and upstream's `getXStep` arity change collide in the same parameter list.

**Hits:** `cartesian/CartesianChart.kt`, `cartesian/data/MutableCartesianChartRanges.kt`,
`cartesian/data/CartesianChartRanges.kt`, `cartesian/data/CartesianChartModel.kt`.

---

### 1.4 `VicoScrollState` — new constructor params, `scrollEnabled` became mutable state, and upstream now
### natively does the thing MOB-3494 was built for

**What changed.**
- Public constructor: all five existing parameters gained defaults, and two new parameters were appended —
  `xSnapStep: Double? = null`, `snapAnimationSpec: AnimationSpec<Float> = spring()`.
  `VicoScrollState.Saver(...)` gained the same two.
- `internal val scrollEnabled: Boolean` → `internal var scrollEnabled by mutableStateOf(true)`, driven by a
  `SideEffect` in `rememberVicoScrollState`.
- `rememberVicoScrollState` **no longer keys `rememberSaveable` on `scrollEnabled`** (it keys the `saver` on it,
  but not the state). Toggling `scrollEnabled` no longer resets scroll position (`1fe9daef9`).
- New `private data class ScrollLayout(minX, xStep, xSpacing, startPadding, layoutDirectionMultiplier)` and a
  repositioning block inside `update()` that **preserves the visible x-range across data updates**: it captures
  `preClampValue` before `maxValue` clamping and, when `ranges.minX` or `ranges.xStep` changed, recomputes
  `value` so the same x-coordinates stay on screen (`a1e3d0296` #1539).
- `internal suspend fun scroll(scroll, maxScroll)` changed from `isScrollInProgress.first { !it }` (wait) to
  `if (scrollableState.isScrollInProgress) return` (**drop the stale zoom compensation**) (`2b389ba95` #1554).
- `animateScroll` now vectorizes the spec, and for `durationNanos == 0L` does an instant `scrollBy` instead of
  `animateScrollBy` (`b7b8674cb` #1429).
- New `internal fun getSnapDelta(...)` and `internal suspend fun performSnap(...)`.

**Evidence.** `git diff e67e49520..v3.3.1 -- .../cartesian/VicoScrollState.kt`;
`git show v3.3.1:.../cartesian/SnapScroll.kt`.

**How it breaks us: compile + direct feature overlap.** `VicoScrollState.kt` is ours-168+/9-, theirs-182+/41-.
Two overlaps are substantive, not cosmetic:
1. **Snap scrolling.** Upstream's `xSnapStep`/`snapAnimationSpec` + `SnapScroll.kt` + an `internal class
   SnapFlingBehavior` in `Modifier.kt` is the same feature as our new files `cartesian/SnapFlingBehavior.kt`
   and `SnapBehaviorConfig`/`rememberChartSnapFlingBehavior` (both imported by meApp).
   **Upstream's `SnapFlingBehavior` is declared in package `com.patrykandpatrick.vico.compose.cartesian` — the
   same package and same simple name as our file. A naive graft is a redeclaration compile error.**
2. **Visible-x-range preservation.** Upstream's `ScrollLayout` repositioning is the native fix for the
   "range jump" defect recorded in our MOB-3494 notes. Our `ScrollAwareRangeProvider` / `WindowSettleSignal`
   (both new files, both imported by meApp) may now be partly or wholly redundant.

**Hits:** `cartesian/VicoScrollState.kt`, `cartesian/Modifier.kt`, `cartesian/Scroll.kt` (ours only — upstream
never touched `Scroll.kt`), and our new `SnapFlingBehavior.kt`, `ScrollAwareRangeProvider.kt`,
`WindowSettleSignal.kt`.

**Migration must:** establish, before porting, whether upstream's `xSnapStep` and range-preservation satisfy the
MOB-2953/MOB-3494 acceptance criteria. Per locked decision 2 (minimise the fork), if they do, three of our eight
new files and a large part of our `VicoScrollState` delta should be deleted rather than ported.

---

### 1.5 `LineCartesianLayer.LineProvider.getLine` — the one hard, un-deprecated removal in the range

**What changed.** `public fun getLine(seriesIndex: Int, extraStore: ExtraStore): Line`
→ `public fun getLine(seriesKey: Any, seriesIndex: Int, extraStore: ExtraStore): Line`.
No deprecated overload exists. Upstream says so explicitly in the v3.2.0 notes:

> “There's one minor breaking change: `LineCartesianLayer.LineProvider.getLine` has a new `seriesKey` parameter
> at the start.[^1] … `LineProvider` is a functional interface, and Kotlin offers no means of effectively updating
> the signature of such an interface's abstract function in a backward-compatible manner.”

Three `protected open` members of `LineCartesianLayer` also gained a `seriesKey: Any` parameter — overriding
subclasses break:
`CartesianDrawingContext.updateMarkerTargets(entry, seriesKey, canvasX, canvasY, lineFillBitmap)`,
`CartesianDrawingContext.updateMarkerTargets(entry, seriesKey, canvasX, canvasY, color)`,
`CartesianDrawingContext.drawPointsAndDataLabels(line, series, seriesKey, seriesIndex, drawingStart, pointInfoMap)`.
`protected fun collectPointsAndVisibleIndexRange` gained a leading `interpolator: Interpolator` parameter.

By contrast, the equivalent column/candlestick provider changes **do** ship deprecated defaults
(`ColumnCartesianLayer.ColumnProvider.getColumn`/`getWidestSeriesColumn`,
`CandlestickCartesianLayer.CandleProvider.getCandle`/`getWidestCandle`) — those throw `NotImplementedError`
at runtime if neither overload is implemented, wrapped into an `IllegalStateException` by `getCandleOrThrow`.

**Evidence.** `git show v3.3.1:.../layer/LineCartesianLayer.kt` lines 418/423, 469-493, 617/642/658, 1028-1046;
commit `a4c0f12ed` *feat(cartesian): track series by key*; v3.2.0 release notes.

**How it breaks us: compile — but *not* meApp.** Checked: all three meApp `LineProvider` sites go through the
built-in factory, which upstream updated: `DataLine.kt:60` and `PercentileBand.kt:44` and `PrimaryLayer.kt:65`
are all `LineCartesianLayer.LineProvider.series(lines)`. `DataLine.kt:41` only *declares the return type* as
`LineCartesianLayer.LineProvider` and returns that factory — no SAM conversion, no custom implementation.
**meApp is unaffected by this removal.** Our own fork code is the thing to check: anything in
`LineCartesianLayer.kt` that calls or overrides `getLine` must be re-sited.

`LineCartesianLayer.PointProvider.getPoint(entry, seriesIndex, extraStore)` is deprecated in favour of
`getPoint(entry, extraStore)` and **throws `NotImplementedError` by default**; meApp's
`PointProvider.single(...)` calls (`DataLine.kt:48`, `PrimaryLayer.kt:51`) use the built-in factory and are safe.

**Hits:** `cartesian/layer/LineCartesianLayer.kt` (ours 320+/54-, theirs 130+/35-),
`cartesian/marker/LineCartesianLayerMarkerTarget.kt` (ours only; upstream untouched).

---

### 1.6 `MeasuringContext.canvasSize` deprecated; `canvasWidth` is the new measuring-phase API

**What changed.** `MeasuringContext` gained `public val canvasWidth: Float` and `canvasSize` is now
`@Deprecated` with a long message: during measurement the canvas **height is not known** (because of
`chartAreaHeight`), so measuring code must use `canvasWidth`; drawing code must narrow its receiver to
`DrawingContext`, which re-declares `canvasSize` with `@Suppress("OVERRIDE_DEPRECATION")`.
`MutableCartesianMeasuringContext` gained `override var canvasWidth: Float` as its **first** constructor parameter.

Also moved out of `common/MeasuringContext.kt` in `8080b49d9`: the `@RestrictTo` extension
`MeasuringContext.pointerPositionToX(...)` now lives in the new file
`cartesian/PointerPosition.kt` (package `…vico.compose.cartesian`).

**Evidence.** `git diff e67e49520..v3.3.1 -- .../common/MeasuringContext.kt`,
`.../common/DrawingContext.kt`, `.../cartesian/MutableCartesianMeasuringContext.kt`;
`git show 8080b49d9 -- .../common/MeasuringContext.kt`.

**How it breaks us: compile warnings everywhere + real logic risk.** Any of our patched measuring-phase code
reading `canvasSize.height` is now reading **the chart-area height, not the real canvas height** (per the
deprecation text: “for the automatic case, it represents the chart-area height”). Where meApp pins a fixed
height this stays exact; where it does not, the value changes meaning silently.

**Hits:** `cartesian/axis/HorizontalAxis.kt`, `cartesian/axis/VerticalAxis.kt`,
`cartesian/CartesianDrawingContext.kt`, `cartesian/CartesianChart.kt`.

---

### 1.7 `Modifier.pointerInput` — `onInteraction` returns `Boolean`, and tap/long-press detection was rewritten

**What changed.**
- `onInteraction: ((Interaction) -> Unit)?` → `((Interaction) -> Boolean)?`.
- Event consumption is now conditional on that return value: `if (consume) event.changes.fastForEach { it.consume() }`.
  Previously only `Move` events were consumed, and only when `consumeMoveEvents && !scrollEnabled`.
- `androidx.compose.foundation.gestures.detectTapGestures` was **replaced with a private re-implementation**
  (`awaitEachGesture` + `awaitFirstDown` + `awaitLongPressOrCancellation`/`waitForUpOrCancellation` + a
  `context(pointerEventScope: AwaitPointerEventScope)` helper `PointerInputChange?.isTap(firstDown)`), so a tap
  is consumed only when the handler says it was handled.
- `scrollable(...)` gained `flingBehavior = flingBehavior`, chosen between `ScrollableDefaults.flingBehavior()`
  and the new internal `SnapFlingBehavior` based on `scrollState.xSnapStep`.
- `Modifier.desktop.kt` / `Modifier.web.kt` `onDragStopped` branches on `xSnapStep`.

**Evidence.** `git diff e67e49520..v3.3.1 -- .../cartesian/Modifier.kt .../desktopMain .../webMain`.
Commits: `edc8dc292` *Improve `PointerInputChange` consumption logic (#1419)*, `01c7571ad` (#1372).

**How it breaks us: compile + the SnapFlingBehavior name clash (see §1.4).**
`Modifier.kt` is ours-234+/27-, theirs-121+/25-. Our `ScrubMarkerController` and `CartesianMarkerController`
work is downstream of this gesture pipeline.

Note: the file now uses Kotlin **context parameters** (`context(pointerEventScope: AwaitPointerEventScope)`)
with **no `-Xcontext-parameters` compiler flag anywhere in the build** — so this requires a Kotlin version where
context parameters are enabled by default. See the toolchain table.

**Hits:** `cartesian/Modifier.kt`, `cartesian/marker/CartesianMarkerController.kt` (ours only),
`cartesian/marker/CartesianMarker.kt` (ours only).

---

### 1.8 Axis constructors gained a non-defaulted `titlePosition` parameter

**What changed.** `BaseAxis` gained `public enum class TitlePosition { Side, End }` and
`protected fun isNotInRestrictedBounds(bounds: Rect)`. Both `HorizontalAxis` and `VerticalAxis` gained
`public val titlePosition: TitlePosition` as the **last** constructor parameter of *both* their
`protected constructor`s — **with no default**. `copy(...)` and the `rememberTopAxis`/`rememberBottomAxis`/
`rememberStartAxis`/`rememberEndAxis` factories gained `titlePosition: TitlePosition = TitlePosition.Side`.
`equals`/`hashCode` on both classes now include it.

**Evidence.** `git diff e67e49520..v3.3.1 -- .../cartesian/axis/BaseAxis.kt .../HorizontalAxis.kt .../VerticalAxis.kt`;
commit `66ad84593` *Add configurable axis title positions (#1489)*.

**How it breaks us: compile.** Our `HorizontalAxis.kt` (ours 152+/149-, theirs 277+/77-) and `VerticalAxis.kt`
(ours 211+/25-, theirs 175+/29-) both patch these constructors, both `remember*` factories, and both drawing
paths. Any `super(...)` call or positional construction in our fork must be extended.

**How it breaks meApp:** meApp imports `HorizontalAxis`, `VerticalAxis` and `BaseAxis`. Named-argument
`remember*Axis` calls are safe (new param is defaulted and appended); any positional call past the first few
arguments is not. The v3.3.0-next.1 notes explicitly warn: *“positional calls that pass later parameters may need
to switch to named arguments.”*

**Hits:** `cartesian/axis/HorizontalAxis.kt`, `cartesian/axis/VerticalAxis.kt`, `cartesian/axis/BaseAxis.kt`,
`cartesian/axis/AxisManager.kt` (ours only; upstream untouched).

---

### 1.9 `Defaults.CHART_HEIGHT` renamed and re-valued

**What changed.** `internal object Defaults`: `const val CHART_HEIGHT: Float = 200f` was **removed** and replaced by
`const val CARTESIAN_CHART_AREA_HEIGHT: Float = 192f` and `const val PIE_CHART_AREA_HEIGHT: Float = 232f`.

**Evidence.** `git diff e67e49520..v3.3.1 -- .../common/Defaults.kt`; commit `a3e3d6cd0`.

**How it breaks us: compile.** `common/Defaults.kt` is one of our 26. Our `CartesianChartHost.kt` still
`import …Defaults.CHART_HEIGHT` at HEAD (`git show HEAD:.../CartesianChartHost.kt`, line 40) even though we
dropped the clamp that used it — a dangling import that will not resolve at `v3.3.1`.

---

## SEVERITY 2 — compiles, behaves differently (the dangerous ones)

### 2.1 Chart sizing model replaced: content-area height instead of capped total height

**What changed.** Old: `Box(modifier.heightIn(max = CHART_HEIGHT.dp).fillMaxWidth())` — the chart's total height
was **capped at 200 dp** and everything (legend, marker, axis margins) was fitted inside it.
New: `ChartHostBox` uses a `Modifier.layout` that, when the incoming constraints have **no fixed height**, sizes
the chart to `chartAreaHeight.roundToPx() + measureExtras(width)` — i.e. the coordinate system is
`chartAreaHeight` (192 dp default) and legend/marker/margins are **added on top**. When the constraints *do*
have a fixed height, that height is honoured and the extras are subtracted from the coordinate system instead.

**Evidence.** `git show v3.3.1:.../common/ChartHostBox.kt`; `git show e67e49520:.../CartesianChartHost.kt:274-276`;
commit `a3e3d6cd0`; v3.3.0 release notes.

**How it breaks us: none-but-notable → our patch is now redundant.** Our fork already removed the
`heightIn(max = CHART_HEIGHT.dp)` clamp (`git show HEAD:.../CartesianChartHost.kt:538-540` is
`Box(modifier.fillMaxWidth())`). Upstream has now solved the same problem properly. **Per locked decision 2,
our custom `CartesianChartHostBox` should be dropped in favour of upstream's `ChartHostBox`.**

**meApp impact: none expected.** Both call sites pin a fixed height —
`GraphView.kt:98` `modifier.height(chartHeight)` and `SnapshotLineChart.kt:158` `.height(SNAPSHOT_CHART_HEIGHT)`
(`= 199.dp + CHART_RESTING_LABEL_BAND`) — so `constraints.hasFixedHeight` is true and the chart honours it, as
it does on our fork today. Worth a visual regression check regardless, because *where the extras come out of*
changed.

---

### 2.2 `HorizontalAxis` automatic-height cap: `canvasSize.height / 3` → fixed `64.dp`

**What changed.** `private const val MAX_HEIGHT_DIVISOR = 3` with
`.coerceAtMost(canvasSize.height / MAX_HEIGHT_DIVISOR)` → `private val MAX_AUTO_HEIGHT = 64.dp` with
`.coerceAtMost(MAX_AUTO_HEIGHT.pixels)`.

**Evidence.** `git diff … .../axis/HorizontalAxis.kt`; commit `a3e3d6cd0`; v3.3.0 release notes.

**How it breaks us: silent behaviour change.** For meApp's charts (199–~250 dp tall), the old cap was
~66–83 dp; the new cap is a flat 64 dp. X-axis height can shrink. Compiles clean.

**Hits:** `cartesian/axis/HorizontalAxis.kt`.

---

### 2.3 `BaseAxis.Size.Fraction` deprecated; `VerticalAxis` `Size.Text` now measures with `label`, not `titleComponent`

**What changed.**
- `Size.Fraction` is `@Deprecated` ("…set the chart area's size directly instead; see the `chartAreaHeight`
  parameter of `CartesianChartHost`"). Still functional.
- `VerticalAxis.getWidth`: `is Size.Text -> titleComponent?.getWidth(…)` → `is Size.Text -> label?.getWidth(…)`.
  This is a **bug fix** (`d25928f66` *Fix Y axis label width issue in BasicLineChart (#1438)*) but it changes
  the computed axis width for anyone using `Size.Text`.
- `is Size.Fraction -> canvasSize.width * fraction` → `canvasWidth * fraction`.

**Evidence.** `git diff … .../axis/BaseAxis.kt .../axis/VerticalAxis.kt`.

**How it breaks us: silent behaviour change if we use `Size.Text`. meApp does not.** Checked:
`grep -rn "Size\.\(Text\|Fraction\|Auto\|Fixed\)" app/src` in `meApp-bug/Android` returns exactly one hit —
`features/common/components/chart/axis/VerticalAxis.kt:56`, `size = BaseAxis.Size.Fixed(0.dp)`. Neither the
`Size.Fraction` deprecation nor the `Size.Text` measurement fix reaches meApp. Our own fork's axis patches still
need checking against both.

**Hits:** `cartesian/axis/VerticalAxis.kt`, `cartesian/axis/BaseAxis.kt`.

---

### 2.4 `LineCartesianLayer` draws from a **visible-range slice** with interpolator-declared padding

**What changed.**
- `LineCartesianLayer.Interpolator` gained `public val visiblePadding: Int? get() = 0` — the number of extra
  points to supply beyond the one already included on each side. `null` means "give me the whole series".
  `CatmullRomInterpolator` declares `override val visiblePadding: Int = 1`.
- `collectPointsAndVisibleIndexRange` and `forEachPointInBounds` no longer use `ranges.minX..ranges.maxX` +
  `series.forEachIn(...)`. They now compute `getVisibleXRange()` and slice via
  `List<Entry>.getSliceIndices(visibleXRangeStart, visibleXRangeEnd, visiblePadding)`.
- `getSliceIndices` itself was rewritten: `(layerXRangeStart, layerXRangeEnd, visibleXRangeStart,
  visibleXRangeEnd): Triple<Int, Int, Int>` → `(visibleXRangeStart, visibleXRangeEnd, visiblePadding: Int? = 1): IntRange`.

**Evidence.** `git diff … .../layer/LineCartesianLayer.kt .../data/CartesianLayerModel.kt`; commits
`608587edb` *fix: simplify visible slice padding*, `d24f81ae2`/`b4be7364e` (#1517/#1520);
v3.2.0 note 2 ("Prevented the built-in `CartesianLayer` implementations from trimming their data to their
reported x-ranges (sometimes incompletely)").

**How it breaks us: silent behaviour change, and a required action on our new interpolator.**
Our new `cartesian/layer/MonotoneInterpolator.kt` + `cartesian/InterpolationType.kt` implement
`LineCartesianLayer.Interpolator` and are reached from meApp through
`LineCartesianLayer.Interpolator.monotone()` (used at `DataLine.kt:47` and `PercentileBand.kt:40` — i.e. on
**every line meApp draws**). They will compile against v3.3.1 (the new member is defaulted), but they will
inherit `visiblePadding = 0`, meaning **only one point of context on each side of the viewport**. A monotone
scheme that needs two neighbours will draw a visibly different curve at the viewport edges while scrolled.
It must declare the padding it actually needs — compare `CatmullRomInterpolator`, which declares
`override val visiblePadding: Int = 1`.

**Hits:** `cartesian/layer/LineCartesianLayer.kt`; our `MonotoneInterpolator.kt`, `InterpolationType.kt`.

---

### 2.5 All line series composite onto **one** offscreen layer; new reveal-sweep clip

**What changed.** The `saveLayer(opacity = …)` / `canvas.restore()` pair moved from *inside* the
`model.series.forEachIndexed` loop to *outside* it — one offscreen buffer per frame instead of one per series.
Additionally a `sweepFraction` clip (reveal animation, `5cbc0b24c`) now wraps the whole draw, guarded by a
single-evaluation `isSweepInProgress` boolean to work around ART JIT bug `b/553511933` (`632d413f0`, the sole
substantive change in v3.3.1).

**Evidence.** `git diff … .../layer/LineCartesianLayer.kt`; commits `72a0ccb87`, `5cbc0b24c`, `632d413f0`;
v3.2.1 and v3.3.1 release notes.

**How it breaks us: silent visual change.** Overlapping series no longer bleed through one another during the
fade-in. meApp draws a percentile band plus a data line (`PercentileBand.kt`, `DataLine.kt`) — overlapping
translucent geometry is exactly the case this changes.

---

### 2.6 Interpolation policy moved out of the drawing models; `default()` deprecated

**What changed.** New per-type factories `CartesianLayerDrawingModelInterpolator.line(sweep: Boolean = false)`,
`.column()`, `.candlestick()`, backed by new files `BaseCartesianLayerDrawingModelInterpolator.kt`,
`LineCartesianLayerDrawingModelInterpolator.kt`, `ColumnCartesianLayerDrawingModelInterpolator.kt`,
`CandlestickCartesianLayerDrawingModelInterpolator.kt`. `default()` is `@Deprecated`.
`CartesianLayerDrawingModel.transform(entries, from, fraction)` and `CartesianLayerDrawingModel.Entry.transform(from, fraction)`
are `@Deprecated` **and now throw `UnsupportedOperationException` by default**.
The default parameter on `LineCartesianLayer` and `rememberLineCartesianLayer` changed from
`CartesianLayerDrawingModelInterpolator.default()` to `.line()`.

**Evidence.** `git show v3.3.1:.../cartesian/data/CartesianLayerDrawingModelInterpolator.kt`,
`.../CartesianLayerDrawingModel.kt`, `.../DefaultCartesianLayerDrawingModelInterpolator.kt`;
commits `7e2e199a7` (#1515), `48bf8f178`; v3.3.0-next.1 notes.

**How it breaks us: silent behaviour change.** The default interpolator for line layers changed identity.
`.line()` does **not** call `transform()`. Any custom drawing-model `transform` override in our fork or in
meApp stops being invoked — silently, with no compile error. Separately,
`DefaultCartesianLayerDrawingModelInterpolator` (still reachable via the deprecated `default()`) now matches
old-to-new entries by **series key** rather than index, which changes diff-animation behaviour when series are
added/removed.

---

### 2.7 Series identity: `seriesKeys` added to every model; `Entry.equals`/`hashCode` changed

**What changed.** `LineCartesianLayerModel` (and the column/candlestick equivalents) gained
`public val seriesKeys: List<Any>`, a second public constructor `(series, seriesKeys)`, and `require`s that keys
be same-size and unique. `Entry`'s internal constructor gained `seriesKey: Any = 0` and `seriesIndex: Int = 0`,
and **`Entry.equals`/`hashCode`/`toString` now include both**. `Partial`'s public constructor changed from
`Partial(series)` to `Partial(series, seriesKeys)`. The `BuilderScope.series(...)` overloads gained
`key: Any = series.size`. `CartesianLayerDrawingModel` gained `seriesKeys` and a same-size/uniqueness `require`.

**Evidence.** `git diff … .../data/LineCartesianLayerModel.kt`, `.../data/CartesianLayerDrawingModel.kt`;
commit `a4c0f12ed`; v3.2.0 notes 6-9.

**How it breaks us: silent behaviour change + two new runtime throws.** Any code of ours that compares
`LineCartesianLayerModel.Entry` values — including recomposition keys and model-equality short-circuits — now
also compares `seriesKey`/`seriesIndex`. Entries built outside the builder DSL get the `seriesKey = 0` /
`seriesIndex = 0` defaults and will compare unequal to builder-produced entries with the same `(x, y)`.
meApp passes `List<LineCartesianLayerModel.Entry>` around explicitly
(`features/common/components/chart/...`, seen in the import scan).

**Also:** `lineSeries` → `lineModel` (deprecated alias kept, `ReplaceWith`). meApp imports
`com.patrykandpatrick.vico.compose.cartesian.data.lineSeries` — compiles with a warning.

---

### 2.8 Marker-target narrowing tolerance

**What changed.** The inline narrowing logic (`targets.distinctBy { it.canvasX }.size > 1`) became
`narrowMarkerTargets(targets, pointX)` using a `MARKER_POSITION_TOLERANCE = 0.5f` span check
(`targets.maxOf { canvasX } - targets.minOf { canvasX } <= 0.5f`).

**Evidence.** `git diff … .../CartesianChartHost.kt`; commit `4278255a4`; v3.2.0 note 4.

**How it breaks us: silent behaviour change.** Targets within 0.5 px of each other are now **all** kept
(combo charts) instead of being narrowed to one. Our `DefaultCartesianMarker` work (ours 288+/19-) and
`ScrubMarkerController` consume these target lists.

---

### 2.9 `ShapeComponent` now respects margins and draws `Outline`s directly

**What changed.** The drawn rect is now `adjustedLeft/Top/Right/Bottom` (margin- and stroke-inset) rather than
the raw `left/top/right/bottom`; `shape.outline(density, layoutDirection, path, …)` + `canvas.drawPath` became
`shape.createOutline(Size(w, h), …)` + `canvas.drawOutline`.

**Evidence.** `git diff … .../common/component/ShapeComponent.kt`; commits `bb9cfed7e` (#1418), `2fbd1ab51` (#1563).

**How it breaks us: silent visual change.** meApp imports `ShapeComponent` and `rememberShapeComponent`
(marker bodies, points, guideline components). Anything that previously relied on margins being ignored will
now render inset.

---

### 2.10 `CartesianValueFormatter.Decimal` / `DefaultValueFormatter` equality widened

**What changed.** `equals`/`hashCode` now include `decimalSeparator`, `thousandsSeparator`, `prefix`, `suffix`
(previously only `decimalCount`, plus `colorCode` for `DefaultValueFormatter`).

**Evidence.** `git diff … .../data/CartesianValueFormatter.kt .../marker/DefaultCartesianMarker.kt`;
commit `a9bd3e46a` (#1405).

**How it breaks us: silent behaviour change (a fix).** Formatters that differ only in separator/prefix/suffix
now recompose correctly where they previously did not. meApp imports `CartesianValueFormatter`.

---

### 2.11 `rememberLine` key-list fix; `rememberDefaultVicoZoomState` saver change; `VicoZoomState.zoom` un-scaling

- `LineCartesianLayer.rememberLine`'s `remember(...)` key list had `dataLabelRotationDegrees` twice and omitted
  `dataLabelValueFormatter`; fixed (`a132e3508` #1482). Previously-skipped recompositions now happen.
- `rememberDefaultVicoZoomState(scrollEnabled)` changed from a plain `rememberVicoZoomState(initialZoom = …)` to
  a `rememberSaveable` with an explicit `VicoZoomState.Saver(true, initialZoom, Zoom.Content, Zoom.max(Zoom.fixed(Defaults.MAX_ZOOM), Zoom.Content))`.
  It is **no longer keyed on `scrollEnabled`**, and it now sets an explicit max zoom.
- `VicoZoomState.zoom(Zoom)` now un-scales `layerDimensions` by `1f / value` before calling `zoom.getValue(...)`
  (fixing double-scaling), uses `context.canvasWidth / 2f` instead of `canvasSize.center.x`, and the mutex moved
  from `kotlinx.coroutines.sync.Mutex` to Compose's `MutatorMutex`. New public `animateZoom(zoom, animationSpec, bias)`
  (`841ca6b9c` #1488).

**How it breaks us.** meApp calls `rememberVicoZoomState(zoomEnabled = false)` (`GraphView.kt:102`) — unaffected.
Our fork does not modify `VicoZoomState.kt`, so this is upstream-only, but programmatic zoom results change.

---

## SEVERITY 3 — compiles, no behaviour change, but affects the mechanics of the port

### 3.1 Package relocation with deprecated aliases: `common.data` → `cartesian.data`

`CartesianLayerDrawingModel` and `CartesianLayerDrawingModelInterpolator` moved from
`com.patrykandpatrick.vico.compose.common.data` to `com.patrykandpatrick.vico.compose.cartesian.data`.
`@Deprecated` `typealias`es remain at the old package, so old imports compile with a warning.
`git diff --diff-filter=R -M` reports the first as an `R058` rename **from `vico/views/.../views/common/data/`** —
that is a rename-detection artefact of the simultaneous `views` deletion, not a real provenance.
Also moved: `DefaultCartesianLayerDrawingModelInterpolator.kt` (`R074`, within `compose`).
Commit: `8080b49d9` *refactor: move cartesian-specific APIs out of common*.

### 3.2 ktfmt 0.59 → 0.64 reformat touches 14 files in `vico/compose`

`f23073cf4` *chore: update ktfmt to 0.64* reformatted 18 files repo-wide, 14 of them under `vico/compose/src`,
including `HorizontalAxis.kt`, `HorizontalAxisItemPlacers.kt`, `LineCartesianLayer.kt`,
`DefaultCartesianMarker.kt`, `CartesianChartModel.kt`, `TextComponent.kt`, `ShapeComponent.kt`,
`ValueWrapper.kt`, `Modifier.desktop.kt`, `Modifier.web.kt`. The `check-formatting` workflow now downloads
`ktfmt-0.64-with-dependencies.jar` and runs `--google-style --set-exit-if-changed` on **JDK 24**.
`7c8c837a1` added a pre-commit ktfmt hook.

**Effect:** pure whitespace/line-wrapping churn that will collide with every one of our hunks in those files,
inflating conflict counts without changing semantics. Reformat our patches with ktfmt 0.64 before diffing.

### 3.3 `common/component/TextComponent.kt` — upstream change is **formatting only**

Despite a 120-line diffstat, the entire `TextComponent.kt` delta is ktfmt re-wrapping plus two explicit imports
(`half`, `piRad`). No signature or logic change. Our `TextComponent.kt` patch should graft cleanly.

### 3.4 Eleven of our 26 modified files were **never touched** by upstream in the range

Verified with `git diff --numstat e67e49520..v3.3.1 -- <path>` returning empty for each:
`cartesian/Scroll.kt`, `cartesian/CartesianDrawingContext.kt`, `cartesian/FadingEdges.kt`,
`cartesian/marker/CartesianMarker.kt`, `cartesian/marker/CartesianMarkerController.kt`,
`cartesian/marker/LineCartesianLayerMarkerTarget.kt`, `cartesian/axis/AxisManager.kt`,
`cartesian/data/CartesianChartRanges.kt`, `cartesian/data/MutableCartesianChartRanges.kt`,
`cartesian/data/CartesianChartModelProducer.kt`, `cartesian/layer/CartesianLayerMargins.kt`.
(BASELINE lists ten; `CartesianChartModelProducer.kt` is an eleventh.) These graft byte-for-byte — **except**
for the four of them that round-tripped through `vico/shared` (§1.1), which graft cleanly as a *diff* but not
as a *rebase*.

### 3.5 Additive-only new public surface

`cartesian/CartesianChartRenderer.kt` and `pie/PieChartRenderer.kt` (render-to-`ImageBitmap`, for Glance),
`cartesian/PointerPosition.kt`, `cartesian/SnapScroll.kt`, `common/ChartHostBox.kt`,
`layer/CatmullRom.kt` (extracted `getCatmullRomYRange` + `cubicBezierExtrema`),
`HorizontalAxis.ItemPlacer.extremes(...)` and `ExtremesHorizontalAxisItemPlacer`
(`efa1dab65` #1484, `dfdbebdce` #1513), `HorizontalAxis.ItemPlacer.getShiftExtremeLabels` (defaulted),
`ItemPlacer.aligned(..., shiftExtremeLabels: Boolean = false)`.

**Note for decision 2:** `ItemPlacer.extremes(...)` and `shiftExtremeLabels` overlap with our new
`cartesian/axis/ListItemPlacer.kt` (imported by meApp). Our `ListItemPlacer` still compiles — the new interface
member is defaulted — but may now be replaceable by an upstream extension point.

### 3.6 `CatmullRom`/`AreaFill` correctness rewrite

`AreaFills.kt` dropped `PathOperation.Intersect` entirely; `BaseAreaFill` replaced
`reset`/`onTopAreasCreated`/`onBottomAreasCreated`/`onAreasCreated` with a single
`abstract fun CartesianDrawingContext.drawAreas(areaPath: Path, canvasSplitY: Float)`, and the split-y is now
`coerceIn(layerBounds.top, layerBounds.bottom)`. `CatmullRomInterpolator` clamps control-point x to the
segment's x-range. Commits `d24f81ae2`/`b4be7364e` (#1517/#1520), v3.2.3 notes. These are `internal`; they do
not break us, but they change area-fill geometry for Catmull-Rom lines.

---

## SEVERITY 4 — publishing / licensing

### 4.1 No licensing or attribution change

`git diff --name-status e67e49520..v3.3.1 -- LICENSE NOTICE README.md` reports **`M README.md` only**.
`LICENSE` is unchanged (Apache 2.0). Per-file copyright headers in touched files moved from
"Copyright 2025" to "Copyright 2026". The `publishing-convention` POM block
(`git show v3.3.1:buildSrc/src/main/kotlin/publishing-convention.gradle.kts`) is **byte-identical** to the fork
point: same `name = "Vico"`, same `description`, same Apache-2.0 licence node, same `scm`, same two developers.
Nothing in the range affects our right or obligation to republish a fork under Apache 2.0, beyond the standard
requirement to preserve the licence and the notice.

### 4.2 Publishing wiring our fork patches **has moved under it**

- `vico/build.gradle.kts` now imports `java.time.Year`, lists `dokka(project(":vico:compose-glance"))` instead of
  `dokka(project(":vico:views"))`, and computes the Dokka footer year dynamically. Our patch here is a one-line
  `group = "com.dmdbrands.lib"` substitution — it will conflict only trivially, but the `dokka(...)` block must
  be re-aligned or Gradle configuration fails on the missing `:vico:views` project.
- `vico/compose/build.gradle.kts`: `id("org.jetbrains.compose")` → `alias(libs.plugins.composeMultiplatform)`.
  Our patch replaces `` `publishing-convention` `` with `` `maven-publish` `` in that same `plugins {}` block and
  appends a GPR `publishing { repositories { … } }` block pointing at
  `https://maven.pkg.github.com/gg-engineering/vico`. Both edits sit in the lines upstream changed.
- `kotlin { androidLibrary { … } }` → `kotlin { android { … } }` (commit `e902ca7fb`
  *chore: migrate KMP Android target blocks*). Our fork does not touch that block, but `buildSrc`'s
  `AndroidConfiguration.kt` `configure()` extension is resolved against it — **unchanged** in the range
  (`git diff … -- buildSrc` shows only `build.gradle.kts` and `Versions.kt`), so it still applies.
- `-Xannotation-default-target=param-property` was **removed** from the `compose` module's
  `compilerOptions.freeCompilerArgs` (commit `336552cc7` *chore: remove redundant Kotlin compiler flags*),
  because Kotlin 2.4 made that the default. If our fork's Kotlin is older, annotation targeting changes.
- `explicitApi()` remains on. Any new `public` declaration we add must carry an explicit visibility and KDoc.
- `buildSrc/src/main/kotlin/Project.kt` is unchanged: `moduleNamespace = "${group}.${project.name.replace("-", ".")}"`.
  Because our fork sets `group = "com.dmdbrands.lib"`, the published Android namespace is
  `com.dmdbrands.lib.compose` while the Kotlin packages stay `com.patrykandpatrick.vico.compose.*`. That split
  is unchanged by the migration — the artifact coordinate `com.dmdbrands.lib:compose-android` is stable.
- `buildSrc/build.gradle.kts` dropped `implementation(libs.compose)` and added `implementation(libs.kotlinJvm)`;
  the `compose` library alias was deleted from the catalogue in favour of the `[plugins]` entries
  `composeMultiplatform` / `composeMultiplatformSample`. A fork that still references `libs.compose` will fail
  catalogue resolution.
- `gradle.properties` gained `android.experimental.enableScreenshotTest=true`, `org.gradle.parallel=true`,
  `org.gradle.tooling.parallel=true`, and raised `org.gradle.jvmargs` from `-Xmx4096m` to `-Xmx6144m`.

### 4.3 Published target set shrank: `iosX64` dropped

`vico/compose/build.gradle.kts`: `listOf(iosX64(), iosArm64(), iosSimulatorArm64())` →
`listOf(iosArm64(), iosSimulatorArm64())` (commits `44aeab541` #1422, `d4821ac00`). The `js(browser)` and
`wasmJs(browser)` targets survive (they were removed and restored in-range — `bc79cab82`, v3.2.2).
Irrelevant to meApp (Android-only consumer) but it changes what a republished fork publishes.

---

## Toolchain floor table

| Thing | FORK_POINT `e67e49520` | TARGET `v3.3.1` | meApp (`meApp-bug/Android`) | meApp can meet it? |
|---|---|---|---|---|
| Kotlin | 2.3.20 | **2.4.10** | 2.4.21 | Yes |
| AGP | 9.1.0 | **9.3.2** | 9.5.1 | Yes |
| Gradle (wrapper) | 9.4.1 | **9.7.1** | 9.8.1 | Yes |
| JDK (CI build/publish) | 17 | 17 | JVM target 11, `JavaVersion.VERSION_11` | Yes |
| JDK (ktfmt check only) | 24 | **24** | n/a | n/a (formatting job only) |
| Jetbrains Compose Multiplatform (library) | 1.10.3 | **1.12.0** | n/a (consumes AAR) | n/a |
| androidx Compose BOM (sample/consumer ref) | 2026.03.01 | **2026.08.00** | 2026.09.00 | Yes |
| `compileSdk` | 36 | **37** | 37 | Yes |
| `minSdk` | 23 | 23 (unchanged) | 26 | Yes |
| Java source/target compat | 11 | 11 (unchanged) | 11 | Yes |
| kotlinx-coroutines | 1.10.2 | **1.11.0** | 1.11.0 (test) | Yes |
| lifecycle-runtime-compose (JB) | 2.10.0 | **2.11.0** | 2.11.0 | Yes |
| vanniktech maven-publish | 0.36.0 | 0.37.0 | n/a (we use `maven-publish` + GPR) | n/a |
| Dokka | 2.2.0 | 2.2.0 (unchanged) | n/a | n/a |
| ktfmt | 0.59 | **0.64** | ktlint 14.2.0 (different tool) | n/a |
| KMP targets | android, iosX64, iosArm64, iosSimulatorArm64, jvm(desktop), js(browser), wasmJs(browser) | **android, iosArm64, iosSimulatorArm64, jvm(desktop), js(browser), wasmJs(browser)** — `iosX64` dropped | android only | Yes |
| Kotlin context parameters | not used | **used** (`Modifier.kt` `context(pointerEventScope: AwaitPointerEventScope)`), with **no** `-Xcontext-parameters` flag in the build | n/a (library-internal) | Yes, at Kotlin 2.4.x |

**Verdict on floors: meApp clears every floor already, with headroom on all of Kotlin, AGP, Gradle, compileSdk
and the Compose BOM.** No meApp-side upgrade is forced by this migration. The one item to confirm by building
rather than by reading: that our fork's build environment is on a Kotlin that enables context parameters by
default — upstream relies on it with no opt-in flag, and `git grep -n "context-parameters\|freeCompilerArgs"`
over `v3.3.1:buildSrc v3.3.1:vico v3.3.1:gradle.properties` returns nothing.

---

## Structural-difficulty verdict

**Structurally this is an easy migration; semantically it is a medium-hard one, and the real cost is concentrated
in four files.** The module consolidation that the brief flagged as the biggest risk turns out to pre-date our
fork point by three months — `vico/compose` neither moved nor was renamed, no file we modified was deleted,
eleven of our twenty-six modified files were not touched upstream at all, and the single package relocation
(`common.data` → `cartesian.data`) ships deprecated type aliases. The toolchain floors all sit below what meApp
already runs, the licence is unchanged, and the publishing convention's POM is byte-identical, so republishing a
fork needs only the same three edits we make today, re-sited onto lines upstream happened to touch. What is
genuinely hard is that upstream spent this range rewriting exactly the surfaces our fork exists to modify:
`CartesianChartHost`'s parameter list and sizing model, `VicoScrollState`'s update/scroll/fling path,
`Modifier.kt`'s gesture pipeline, and `CartesianChart`'s measure/prepare split — and in three of those cases it
shipped a *native* version of a feature we had forked to add (snap-to-grid scrolling, visible-x-range
preservation across data updates, and the removal of the 200 dp height cap). Those are not conflicts to resolve
but decisions to make, and under the locked "minimise the fork" rule they point toward deleting fork code rather
than porting it. The two traps that will cost real time if missed are the transient `vico/shared` source tree
(which makes any rebase or cherry-pick of our 105 commits unsafe for four of our files, so the port must be done
as a hand-applied diff) and the `internal class SnapFlingBehavior` that upstream added to
`com.patrykandpatrick.vico.compose.cartesian` — the same package and simple name as one of our eight new files.
The only un-deprecated API removal in 236 commits is `LineCartesianLayer.LineProvider.getLine`'s new leading
`seriesKey` parameter; everything else that breaks does so loudly at a named-argument call site or quietly
through a changed default, and the quiet ones — the 64 dp axis cap, `Size.Text` measuring with `label`,
single-layer series compositing, entry equality including `seriesKey`, and the drawing-model `transform` hooks
that now throw — are what the visual regression pass must be aimed at.

---

## Things this dossier could not settle

1. **Whether `SnapshotLineChart.kt:149`'s untyped `getXStep = { GraphUtil.calculateXStep(...) }` lambda stays
   unambiguous between the deprecated one-arg and the new three-arg overload.** Only a compile will settle it.
2. **Whether upstream's `xSnapStep` and `ScrollLayout` range-preservation fully satisfy MOB-2953/MOB-3494.**
   That is a behavioural equivalence question against our acceptance criteria, not a diff question; it needs the
   reverse-engineered specification of our 34 undocumented commits (BASELINE §Documentation state) to answer.
3. **Whether the `vico` fork's build environment Kotlin enables context parameters by default.** No flag exists
   in the `v3.3.1` build; confirm by compiling `vico/compose` at the target Kotlin before assuming it.
