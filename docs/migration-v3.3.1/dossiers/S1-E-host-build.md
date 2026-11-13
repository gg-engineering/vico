# S1-E — Chart host & composition, and build/publishing

Cluster dossier for the v3.3.1 migration.
Refs: `FORK_POINT = e67e49520`, `OURS = HEAD` (`MOB-2953-week-range-spans-eight-days`, published `4.0.46`), `TARGET = v3.3.1` (`3d675ccc7`).
Everything below was read with `git show <ref>:<path>` / `git diff`. The checkout was not modified.

Scope:
* **Half 1 — host and composition**: `vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/{CartesianChartHost,CartesianChart,Modifier}.kt`
* **Half 2 — build, publishing, distribution**: `vico/build.gradle.kts`, `vico/compose/build.gradle.kts`, `buildSrc/src/main/kotlin/*`, `gradle/libs.versions.toml`, `.gitignore`, `gradle-local.properties`, plus the consumer side in meApp.

Conventions used below:
* **UNDOC** marks a commit prefixed `MOB-2953:` / `MOB-3494:` — no `docs/FEATURE-*.md` covers it.
* Line numbers are at `HEAD` unless stated.
* Where I could not establish something from the repo I say so under *Uncertain*.

---

# Part 1 — Host and composition

## 1.0 Summary table

| ID | Adaptation | File | Public API touched | Doc | Conflict risk at v3.3.1 |
|---|---|---|---|---|---|
| H-1 | Host-owned range recomputation + opening-range derivation | Host 79–136 | no (behavioural) | UNDOC (`25f0fb793`, `a98cf86de`, `e6c766cb8`) | **High** |
| H-2 | Animated Y-range overlay (`AnimatedYCartesianChartRanges`, `ScrollAwareRangeEffect`) + seed fallback | Host 138–184, 542–722 | no (private types), depends on added `CartesianChartRanges.getTargetYRange` | FEATURE-1 (partly) | **High** |
| H-3 | Viewport-window broadcast from the draw pass | Host 307–333, 487–512 | no | FEATURE-1 | **High** |
| H-4 | Marker resting text formatted on scroll settle | Host 313–329, 554–560 | `CartesianMarker.onScrollSettled` (other cluster) | UNDOC (`39bca6cb9`, `08d194bbd`) | Medium |
| H-5 | Caller-owned marker via `initialMarkerX` | Host 76, 199, 226, 248, 250–288 | **yes** — new param on both public overloads | UNDOC for the clear half (`bd58edc86`); `c65d25ef4` for the restore half | **High** (positional + upstream overload set changed) |
| H-6 | Scrub consumer callback routed through `onInteraction`; scrub exempted from `onViewportChange` | Host 255, 335–421 | no (uses `chart.allMarkerTargetXValues`, internal) | UNDOC for the `interaction` arg (`b7d7e0583`) | **High** |
| H-7 | Two-pass prepare + `VerticalAxis.updateScrollState` before every prepare | Host 463–480 | no | FEATURE-9 / `82cda206e` | **Very high** (upstream re-wrote `prepare`) |
| H-8 | `flingBehavior` threaded host → `Modifier.pointerInput` | Host 73, 174, 198, 247, 457 | **yes** — new param, inserted before `animationSpec` | FEATURE-4 | **Very high** (upstream now builds its own `SnapFlingBehavior` internally) |
| H-9 | `scrollState.drawingContext = drawingContext` | Host 531 | no (internal var on `VicoScrollState`) | FEATURE-8 | Low |
| H-10 | Chart-area height parity — `heightIn(max = CHART_HEIGHT.dp)` removed from the host box | Host 537–540 | no | FEATURE-10 / `14542561e` | **Very high** (upstream replaced the box with `ChartHostBox` + `chartAreaHeight`) |
| C-1 | `visibleLabelsCount` → label-spacing scale | Chart 69, 283–289, 304–317 | **yes** — new ctor/`copy`/`rememberCartesianChart` param | FEATURE-7 (successor of `pointSpacingProvider`) | Medium |
| C-2 | `startInsetDp` → unscalable start padding | Chart 70, 291–300 | **yes** — new ctor/`copy`/`rememberCartesianChart` param | UNDOC (`9a7799e00`, `b323662e6`, `ac9d19175`) | Medium |
| C-3 | Start-strip clipping in `draw()`, plus `drawStartAxisLine` | Chart 349–361, 371–376 | no (uses `AxisManager.drawStartAxisLine`, internal) | UNDOC (`9a7799e00`) | Low–Medium |
| C-4 | Draw-order reorder — data composited on top of axes/marker | Chart 362–399 | no | UNDOC (`91e72bf64`) | Low |
| C-5 | `axisManager.drawMarkerDecorations` past the fading-edge layer | Chart 404–408 | no | UNDOC (`1bf0a6d9a`) | Low |
| C-6 | Marker resting pass (`marker.drawResting`) when nothing is marked | Chart 410–412 | `CartesianMarker.drawResting` (other cluster) | UNDOC (`2433daf0d`) | Low |
| C-7 | Interpolated (synthesised) marker targets, active and persistent | Chart 387–390, 481, 483–490, 544–602 | no (private) | FEATURE-8 + `06209b8c8` | Medium |
| C-8 | Marker-target bookkeeping: `allMarkerTargetXValues` cache; per-frame sort removed | Chart 161–171, 382 | no (internal) | `2c00808b6`, `10fa978bb`, `57ce985ea` | Medium |
| M-1 | Scrub gesture state machine in `Modifier.pointerInput` | Modifier 68–239 (machine), 240–270 (preserved upstream branch), 313–320 (enum) | no (internal) | FEATURE-2 + `3a284be42`, `9886f978c`, `2ddae34f4`, `130fde18f` | **Very high** |
| M-2 | `scrollable(enabled = true)` + freeze via `VicoScrollState.isScrollFrozen` | Modifier 59–67 | no | `3a284be42`, `2ddae34f4` | **Very high** |
| M-3 | External `flingBehavior` parameter on `Modifier.pointerInput` | Modifier 19, 57, 62 | no (internal) | FEATURE-4 | **Very high** |
| M-4 | `acceptsGestures` gate on `detectTapGestures` | Modifier 272–283 | **yes** indirectly — `CartesianMarkerController.acceptsGestures` | UNDOC (`3faabb926`) | **High** |

Size on our side: `CartesianChartHost.kt` 276 → 722 lines (+454/−8), `CartesianChart.kt` 613 → 804 (+203/−12), `Modifier.kt` 114 → 321 (+234/−27).
Upstream churn on the same three files between `e67e49520` and `v3.3.1`: +529/−125 (`CartesianChart.kt` 246 changed lines, `CartesianChartHost.kt` 262, `Modifier.kt` 146).

---

## H-1 — Host-owned range recomputation and opening-range derivation

**What it does.** The host ignores the `CartesianChartRanges` the model producer hands it (`val (model, previousModel, _, extraStore) = modelWrapper`, Host:82) and recomputes ranges itself in a `remember(chart, model)` block into a host-owned `MutableCartesianChartRanges` (Host:92–136). Inside the same block, before the first frame, it walks `chart.layers`, pairs each `LineCartesianLayer` **by position** with `model.models[index]`, and for each `ScrollAwareRangeProvider`:
* rebuilds the provider's cache only when the series *instance* changed (`provider.cachedSeries !== series`);
* re-seeds the provider's opening window only when `provider.openingWindow(model.extraStore)` differs from `provider.cachedWindow`, or when `provider.currentTicks` is empty.

Then `hostRanges.reset(); chart.updateRanges(hostRanges, model); hostRanges.toImmutable()`.

**Why we needed it.** Three defects, each recorded in the in-file comments:
1. The producer computes ranges only at push time. A window derived *from* the data that triggered the push arrives afterwards, so the chart kept serving the domain captured at push time and drew its data off-canvas until an unrelated push happened (`25f0fb793`).
2. Deriving the opening range a frame later left `updateRanges` with nothing to ask for, so the chart opened on the whole dataset's extent and then replaced it; deriving it in meApp instead produced two independent selections of entries that could disagree (`a98cf86de`).
3. `indexOf` matched on `equals`, so two layers configured alike both resolved to `models[0]` and the second layer drew against the first one's range — hence pairing by index (`e6c766cb8`).

**Commits.** `25f0fb793` (UNDOC), `a98cf86de` (UNDOC), `e6c766cb8` (UNDOC). Earlier groundwork: `e0b886359`, `bc6a1a922`, `a77154f85`, `82cda206e`.

**Files and hunks.** `CartesianChartHost.kt` 79–136 (destructuring + `hostRanges` + `initialRanges`). Replaces the single upstream line `val (model, previousModel, ranges, extraStore) = modelWrapper`.

**Public API added or changed.** None in these three files. It *consumes* API added elsewhere:
```kotlin
internal fun ScrollAwareRangeProvider.openingWindow(extraStore: ExtraStore): ScrollAwareRangeProvider.Window?
internal fun ScrollAwareRangeProvider.seedFromWindow(extraStore: ExtraStore)
internal fun ScrollAwareRangeProvider.buildCache(series: List<List<LineCartesianLayerModel.Entry>>)
internal var ScrollAwareRangeProvider.cachedSeries: Any?
internal var ScrollAwareRangeProvider.cachedWindow: ScrollAwareRangeProvider.Window?
public var ScrollAwareRangeProvider.currentTicks: List<Double>   // internal set
internal val LineCartesianLayer.internalRangeProvider: CartesianLayerRangeProvider
```

**Coupling to upstream internals.**
* `CartesianChartModelProducer.collectAsState(chart, animationSpec, animateIn, mutableRanges)` — we keep calling it with the `mutableRanges` out-param and then throw the result away. `mutableRanges` is still allocated and still written by the producer (Host:79–80); the work is simply discarded.
* `CartesianChart.updateRanges(MutableCartesianChartRanges, CartesianChartModel)` — `internal`.
* `MutableCartesianChartRanges.reset()` / `.toImmutable()` — `internal`.
* `CartesianChartRanges.Empty`.
* Positional correspondence between `chart.layers` and `model.models` — an upstream invariant we rely on but that upstream does not state.
* `CartesianChart.copy()` keeping `id` — the comment at Host:90–91 depends on that, because a provider change arrives as a new `CartesianChart` instance and `remember(chart, …)` must therefore refire.

**Could it be an extension point, or live in meApp instead?** Partly.
* The *recompute-on-chart-change* half is a genuine library concern and the cleanest upstream-shaped form is a hook on `CartesianChartModelProducer.collectAsState` (or a `recomputeRangesOn: Any?` key). It cannot move to meApp: meApp has no access to `updateRanges`, `MutableCartesianChartRanges` or `CartesianChartRanges.Empty`, all `internal`.
* The *provider seeding* half is `ScrollAwareRangeProvider` lifecycle and belongs with that class, not in the host. A `ScrollAwareRangeProvider.onModel(model, index)` entry point called from one place would remove ~40 lines of layer-walking from the host and the same walk from `ScrollAwareRangeEffect` (H-2), which duplicates it.
* The layer↔model pairing by index is the kind of thing that should live on `CartesianChart` (`layers.zip(model.models)`), not be re-derived in two host-level blocks.

**Conflict risk. High.** Upstream rewrote the host around this code: `CartesianChartHostImpl` gained `modifier` and `chartAreaHeight` parameters and moved its `Canvas` inside a new `ChartHostBox`, and the producer call became `collectAsState(chart, animationSpec, initialAnimationSpec, mutableRanges)` (`animateIn: Boolean` replaced by `initialAnimationSpec: AnimationSpec<Float>?`, with a `@Deprecated` overload keeping the old shape). The destructuring line we replaced is still there, unchanged in form, so the *textual* conflict is small; the hazard is semantic — `animateIn` no longer exists on the primary overload, and our call must be re-pointed at `initialAnimationSpec`.

---

## H-2 — Animated Y-range overlay and seed fallback

**What it does.** Wraps the computed `initialRanges` in a private `AnimatedYCartesianChartRanges` (Host:696–722) that delegates `minX`/`maxX`/`xStep` and overrides `getYRange` / `getTargetYRange` with two pre-built immutable `YRange` objects. The animated values come from `ScrollAwareRangeEffect` (Host:562–690), which per `ScrollAwareRangeProvider`:
* dedupes providers by identity (`distinctBy { it.first }`) and wraps each in `key(provider)`;
* on model change, waits for the first `provider.scrollUpdates` emission, computes the display range and either snaps (first ever render, `Animatable` still `NaN`) or animates;
* on each subsequent settle (`debounce(provider.debounceMs)`, `collectLatest`), bumps `provider.settleEpoch`, swaps `provider.currentTicks` **as the animation starts**, and animates `animMinY`/`animMaxY`;
* pushes every animation frame back into `provider.currentMinY/currentMaxY` and into host state via `snapshotFlow`.

A seed fallback (Host:146–160) uses `firstScrollAwareProvider.seedMinY/seedMaxY` until the animatables have a value, so frame 0 does not flash the full-model Y range.

**Why we needed it.** iOS-Health-style behaviour: the Y axis follows the visible window, the labels swap instantly and the plotted content animates into the new scale. The in-file comments record four distinct fixes: animatables initialised after cache build (no 0→actual flash); dedup by provider identity (a shared provider processed once); `NaN` sentinel for uninitialised state; `settleEpoch` announced *before* the no-movement early return, so a layer that transforms its series against the window recomputes even when the Y range does not move (`e6c766cb8`); `collectLatest` rather than `collect`, because a second settle can land inside the 300 ms animation.

**Commits.** `e0b886359`, `bc6a1a922`, `6e9849068`, `6ef5fdcc7`, `a77154f85`, `0a35ee586`, `82cda206e`, `e6c766cb8` (UNDOC).

**Files and hunks.** `CartesianChartHost.kt` 138–184 (state + `effectiveMinY/MaxY` + `ranges` selection + effect call), 542–560 (doc comment + `MARKER_SETTLE_MS`), 562–690 (`ScrollAwareRangeEffect`), 692–722 (`AnimatedYCartesianChartRanges`).

**Public API added or changed.** None here. Requires, in the data cluster:
```kotlin
public fun getTargetYRange(axisPosition: Axis.Position.Vertical?): YRange = getYRange(axisPosition)   // CartesianChartRanges — ADDED by us, absent at v3.3.1
public var ScrollAwareRangeProvider.seedMinY: Double
public var ScrollAwareRangeProvider.seedMaxY: Double
internal var ScrollAwareRangeProvider.currentMinY: Double
internal var ScrollAwareRangeProvider.currentMaxY: Double
override var settleEpoch: Int   // internal set
internal val ScrollAwareRangeProvider.scrollUpdates: MutableSharedFlow<ScrollInfo>
internal fun computeVisibleEntries(info: ScrollInfo): List<List<Pair<Double, Double>>>?
internal fun computeDisplayRange(...): Pair<ClosedRange<Double>, List<Double>>?
internal val isCacheReady: Boolean
internal val debounceMs: Long ; internal val animDurationMs: Int
```

**Coupling to upstream internals.**
* `CartesianChartRanges` is an upstream interface we **extended** with `getTargetYRange`. That default-method addition is the load-bearing coupling: our `AnimatedYCartesianChartRanges` overrides it, and the vertical-axis cluster reads it.
* `CartesianChartRanges.YRange` is implemented by anonymous objects here.
* `Axis.Position.Vertical` in the `getYRange` signature.
* `chart.layers` / `LineCartesianLayer.internalRangeProvider` again (duplicated walk — see H-1).

**Could it be an extension point, or live in meApp instead?** The *mechanism* could become upstream-shaped: a `CartesianLayerRangeProvider` that is allowed to be time-varying, driven by a `visibleXRange` the chart already knows, is a reasonable upstream feature and would remove `AnimatedYCartesianChartRanges`, `ScrollAwareRangeEffect` and `getTargetYRange` from the fork in one go. It cannot live in meApp — the range interfaces, the draw pass that produces the window, and `MutableCartesianChartRanges` are all `internal`. If the fork must stay, the minimum upstream patch is `getTargetYRange` plus a public way to supply a `CartesianChartRanges` decorator to the host; everything else could then move into `ScrollAwareRangeProvider` (which is our own file and grafts clean).

**Conflict risk. High.** The code is additive and upstream did not touch the corresponding region, but it sits inside a function whose signature and body structure changed, and the whole block is positioned between the producer call and the `ChartHostBox` that upstream introduced.

---

## H-3 — Viewport-window broadcast from the draw pass

**What it does.** Inside the `Canvas` draw lambda, after `scrollState.update(...)`, computes the visible X range **once** and fans it out (Host:487–512):
* to `visibleXRanges` — a `MutableSharedFlow<ClosedFloatingPointRange<Double>>(replay = 1, extraBufferCapacity = 8)` (Host:316) — when `chart.marker != null`;
* to every `ScrollAwareRangeProvider.scrollUpdates` as a `ScrollAwareRangeProvider.ScrollInfo(scroll, xSpacing, chartWidth, visibleXStart, visibleXEnd)`.

Guarded by a three-value dedup (`lastEmittedScroll`, `lastEmittedXSpacing`, `lastEmittedChartWidth`, Host:331–333) so the draw pass only emits when the viewport actually moved, and by `layerDimensions.xSpacing > 0f`.

**Why we needed it.** The Y range and the marker's resting text both settle off the visible window, and the only place that window is genuinely known (rather than estimated) is after `prepare` + `scrollState.update` in the draw pass. Emitting unconditionally re-triggered the whole settle chain on draws that changed nothing.

**Commits.** `6e9849068`, `6ef5fdcc7`, `57ce985ea`, `39bca6cb9` (UNDOC — added the marker feed beside the provider feed), `130fde18f`.

**Files and hunks.** `CartesianChartHost.kt` 307–316 (`scrollAwareProviders`, `visibleXRanges`), 331–333 (dedup wrappers), 487–512 (the emit block).

**Public API added or changed.** None. Uses `internal data class ScrollAwareRangeProvider.ScrollInfo`.

**Coupling to upstream internals.**
* `CartesianMeasuringContext.getVisibleXRange(layerDimensions, layerBounds, scroll)` — `internal`, upstream.
* `MutableCartesianLayerDimensions.xSpacing`, `CartesianChart.layerBounds`, `VicoScrollState.value`.
* `ValueWrapper` (upstream internal) for the three non-snapshot dedup slots.
* Runs inside the `Canvas` `DrawScope`; the emits are `tryEmit`, i.e. deliberately non-suspending inside draw.

**Could it be an extension point, or live in meApp instead?** This is the single most reusable piece of the cluster and the most natural upstream feature: *"expose the visible x-range as a `State`/`Flow` from the host."* meApp already wants it (`VicoScrollState.visibleXRange`, `getVisibleAxisLabels`). An upstream `VicoScrollState.visibleXRange: StateFlow<ClosedFloatingPointRange<Double>>` would let both consumers (`ScrollAwareRangeProvider` and the resting marker) subscribe without any host patch at all. Recommend pursuing this as a non-patch.

**Conflict risk. High.** Upstream moved the `Canvas` into `ChartHostBox`, changed `chart.prepare(context, layerDimensions)` to `chart.prepare(context, layerDimensions, size)` and added `measuringContext.value.canvasWidth = size.width` immediately above our block. The insertion point survives, but every surrounding line moved.

---

## H-4 — Marker resting text formatted on scroll settle

**What it does.** A `LaunchedEffect(chart.marker, visibleXRanges, model)` (Host:322–329) feeds the chart's marker the visible X range: immediately for the first window (`visibleXRanges.first()`), then `debounce(MARKER_SETTLE_MS).collect(marker::onScrollSettled)`. `MARKER_SETTLE_MS = 100L` (Host:560), chosen to equal `ScrollAwareRangeProvider`'s default `debounceMs`, so the text beside the chart and the axis it describes come to rest together.

**Why we needed it.** Formatting the resting label per frame was wasteful and jittery. Keying on `model` as well as on the flow is deliberate: the resting text is formatted from the window *and* from the caller's own state, and new data can change the second without moving the first — a card naming the week its entries fall in would go on naming the old one. Restarting the effect replays the last window (hence `replay = 1`), so it re-formats.

**Commits.** `39bca6cb9` (UNDOC), `08d194bbd` (UNDOC), `2433daf0d` (UNDOC — the `CartesianChart` side).

**Files and hunks.** `CartesianChartHost.kt` 313–316, 318–329, 554–560.

**Public API added or changed.** None in this file. Depends on (marker cluster):
```kotlin
public fun CartesianMarker.onScrollSettled(visibleXRange: ClosedFloatingPointRange<Double>) {}
```

**Coupling to upstream internals.** `CartesianChart.marker` is `internal val` on the chart; we read it from the host. `kotlinx.coroutines.flow.debounce` is `@FlowPreview` — the file carries `@OptIn(FlowPreview::class)` only on `ScrollAwareRangeEffect`, not on `CartesianChartHostImpl`; this `debounce` call compiles because `CartesianChartHostImpl` is `internal` and the warning is not an error in this build. *Uncertain:* whether a stricter `-Werror` configuration upstream would reject it.

**Could it be an extension point, or live in meApp instead?** Yes, and it should be. If H-3 becomes a public `visibleXRange` flow, this entire effect collapses into something meApp (or a marker implementation) subscribes to itself. `CartesianMarker.onScrollSettled` would then be unnecessary as an interface method.

**Conflict risk. Medium.** Purely additive, in a region upstream did not restructure beyond the surrounding declarations.

---

## H-5 — Caller-owned marker via `initialMarkerX`

**What it does.** Adds `initialMarkerX: Double?` to both public `CartesianChartHost` overloads and to `CartesianChartHostImpl`. When the chart's `markerController` is a `ScrubMarkerController` (Host:255, 266–288):
* `LaunchedEffect(initialMarkerX)` — **keyed on the value, not `Unit`** — restores the marker into an *empty* saveable when non-null (`if (markerX == null) markerX = initialMarkerX`) and sets `scrubController.hasActiveMarker = true`; when null it force-clears `markerX`, `markerSeriesIndex`, `lastAcceptedInteraction` and `hasActiveMarker`.
* `LaunchedEffect(Unit)` mirrors the other direction: `snapshotFlow { scrubController.hasActiveMarker }` clears local marker state when the controller drops it.

**Why we needed it.** `c65d25ef4` restored a marker across recomposition. `bd58edc86` (UNDOC) fixed the inverse: running once per host meant a caller that *cleared* its marker — because the marked entry was deleted, or fell outside the window — never reached the chart, which went on drawing the dot and guideline at an x the caller no longer held. Only the clear is forced; a restore is still conditional, because during a drag the local value is ahead of the one the caller has echoed back and an unconditional assign would drag the marker backwards under the finger.

**Commits.** `c65d25ef4`, `bd58edc86` (UNDOC).

**Files and hunks.** `CartesianChartHost.kt` 76 (producer overload param), 199 (pass-through), 226 (model overload param), 234 (pass-through), 248 (impl param), 250–288 (the two effects + `scrubController`).

**Public API added or changed.**
```kotlin
@Composable
public fun CartesianChartHost(
  chart: CartesianChart,
  modelProducer: CartesianChartModelProducer,
  modifier: Modifier = Modifier,
  scrollState: VicoScrollState = rememberVicoScrollState(),
  zoomState: VicoZoomState = rememberDefaultVicoZoomState(scrollState.scrollEnabled),
  flingBehavior: FlingBehavior? = null,                       // ADDED (H-8)
  animationSpec: AnimationSpec<Float>? = defaultCartesianDiffAnimationSpec,
  animateIn: Boolean = true,
  initialMarkerX: Double? = null,                             // ADDED
  placeholder: @Composable BoxScope.() -> Unit = {},
)

@Composable
public fun CartesianChartHost(
  chart: CartesianChart,
  model: CartesianChartModel,
  modifier: Modifier = Modifier,
  scrollState: VicoScrollState = rememberVicoScrollState(),
  zoomState: VicoZoomState = rememberDefaultVicoZoomState(scrollState.scrollEnabled),
  initialMarkerX: Double? = null,                             // ADDED
)

@Composable
internal fun CartesianChartHostImpl(
  chart: CartesianChart,
  model: CartesianChartModel,
  scrollState: VicoScrollState,
  zoomState: VicoZoomState,
  ranges: CartesianChartRanges,
  previousModel: CartesianChartModel? = null,
  extraStore: ExtraStore = ExtraStore.Empty,
  flingBehavior: FlingBehavior? = null,                       // ADDED (H-8)
  initialMarkerX: Double? = null,                             // ADDED
)
```
Note `flingBehavior` is inserted **before** `animationSpec` on the producer overload — a positional source break for any caller using positional arguments. meApp uses named arguments throughout, so it is unaffected.

**Coupling to upstream internals.**
* `rememberSaveable` state `markerX` / `markerSeriesIndex` / `lastAcceptedInteraction` — upstream's, written from our effects.
* `Interaction.Saver` — upstream internal.
* `ScrubMarkerController.hasActiveMarker` — `internal var` on our own class, mutated from the host (a two-way binding between host and controller).

**Could it be an extension point, or live in meApp instead?** The right upstream shape is clear and strictly smaller: make the host's marker state *hoistable* —
```kotlin
public fun CartesianChartHost(..., markerX: Double?, onMarkerXChange: (Double?) -> Unit, ...)
```
or a `rememberCartesianMarkerState()`. That is one upstream-style change replacing H-5, H-6 and a good part of `ScrubMarkerController`. It cannot live in meApp as things stand, because `markerX` is private host state. **This is the single best candidate in the cluster for converting a patch into an upstream feature request.**

**Conflict risk. High.** Upstream's overload set changed under us: the producer overload now takes `initialAnimationSpec` instead of `animateIn` and adds `chartAreaHeight: Dp`, plus a `@Deprecated` overload that still takes `animateIn`. Our two added parameters must be re-inserted into a different parameter list, and `CartesianChartHostImpl` has a different signature (`modifier` and `chartAreaHeight` added, in positions 3 and 7).

---

## H-6 — Scrub consumer callback routed through `onInteraction`

**What it does.** Inside `onInteraction` (Host:335–413), after target narrowing and `shouldAcceptInteraction`, if the controller is a `ScrubMarkerController` with an `onMarkerIndexChanged` callback:
* on show, calls `scrubCallback(x, chart.allMarkerTargetXValues, interaction)` and uses the returned `Double?` as `markerX` (null ⇒ no marker);
* on dismiss, calls `scrubCallback(null, emptyList(), interaction)` and clears.
Otherwise it falls back to upstream's nearest-target logic.
Separately, `onViewportChange()` (Host:415–421) returns immediately when `chart.markerController is ScrubMarkerController`, because that controller dismisses on scroll and needs no position tracking.

**Why we needed it.** meApp decides where a marker may sit (snap to an axis label, refuse a point outside the period, toggle off when the marked point is re-tapped). `df5561288` reverted an earlier clamp so the *raw* X reaches the consumer; `00c1a0ac3`/`41613334a` nulled the X when a scrub projects outside the data range; `2c00808b6` made empty target lists acceptable so the consumer can still snap to a label in a window with no data points; `b7d7e0583` (UNDOC) added the third `interaction` argument so the consumer can tell a tap from the moves of a scrub and the release that ends it — all three previously arrived indistinguishable. meApp uses exactly that: `GraphView.kt:531 val isTap = interaction is Interaction.Tap`.

**Commits.** `ccf2ff5cf`, `2c00808b6`, `00c1a0ac3`, `41613334a`, `df5561288`, `8052261ae`, `b7d7e0583` (UNDOC).

**Files and hunks.** `CartesianChartHost.kt` 255, 376–407, 415–421.

**Public API added or changed.** The callback type, declared on `ScrubMarkerController` (marker cluster) but *shaped by* this file:
```kotlin
internal val onMarkerIndexChanged:
  ((clickX: Double?, targets: List<Double>, interaction: Interaction?) -> Double?)?
```
and consumed through `rememberScrubMarkerController(scrollState, delayMs, onMarkerIndexChanged)`. In this file the only addition is the `internal` read of `CartesianChart.allMarkerTargetXValues` (C-8).

**Coupling to upstream internals.**
* Upstream's whole `onInteraction` body is wrapped, not replaced: `pointerPositionToX`, `getMarkerTargets`, the `distinctBy { it.canvasX }` narrowing, `shouldAcceptInteraction`, `shouldShowMarker`, `lastAcceptedInteraction`.
* `CartesianMarkerController.Lock.Position` for `onViewportChange`.

**Could it be an extension point, or live in meApp instead?** Yes — folded into the hoisted-marker-state proposal in H-5. With `markerX` hoisted, meApp performs all of this logic itself in `onMarkerXChange` and the host needs none of it. Note also that upstream v3.3.1 extracted the narrowing into a top-level `internal fun narrowMarkerTargets(targets, pointX): Pair<List<Target>, Int?>` with a `MARKER_POSITION_TOLERANCE = 0.5f`; our hand-inlined copy uses exact `canvasX` equality and should be replaced by the upstream helper during the port rather than carried forward.

**Conflict risk. High.** Upstream changed `onInteraction`'s *type* from `(Interaction) -> Unit` to `(Interaction) -> Boolean` (the return value now drives event consumption) and replaced the narrowing block with `narrowMarkerTargets(...)`. Our inserted branch has to be re-expressed with a `true`/`false` result on every path.

---

## H-7 — Two-pass prepare and axis scroll state

**What it does.** In the draw lambda (Host:463–480):
* On the very first draw (`!scrollState.initialScrollHandled`), runs a complete throwaway measurement pass — `layerDimensions.clear()`, push the scroll into the start/end `VerticalAxis` via `updateScrollState(scrollState.value, scrollState.maxValue)`, `chart.prepare(...)`, then `scrollState.update(...)` if `layerBounds` is non-empty.
* Then the real pass, again pushing `updateScrollState` into both vertical axes before `chart.prepare(...)`.

**Why we needed it.** `Size.Scroll` vertical axes (FEATURE-9) compute their effective width from the scroll value. On frame 1 `initialScroll` has not been applied, so they measured with `scrollValue = 0` and produced a cutoff that only resolved on frame 2.

**Commits.** `d39964206` (FEATURE-9), `82cda206e`, `3b9146ca8`.

**Files and hunks.** `CartesianChartHost.kt` 463–480.

**Public API added or changed.** None here. Depends on (axis cluster):
```kotlin
internal fun VerticalAxis<*>.updateScrollState(scroll: Float, maxScroll: Float)
internal var VicoScrollState.initialScrollHandled: Boolean
```

**Coupling to upstream internals.** Very tight — `chart.prepare`, `chart.layerBounds`, `scrollState.update(measuringContext, layerBounds, layerDimensions)`, `MutableCartesianLayerDimensions.clear()`, and a cast of `chart.startAxis`/`chart.endAxis` to `VerticalAxis<*>`.

**Could it be an extension point, or live in meApp instead?** No — it is pure library-internal measurement ordering and cannot move to meApp. The durable fix is to give the axis the scroll it needs through the measuring context rather than by a side-channel setter, which would remove both the `updateScrollState` calls and the double pass. That is a change to the axis cluster, not to the host.

**Conflict risk. Very high.** Upstream split `prepare` into `measure(context, layerDimensions): Float` + `prepare(context, layerDimensions, canvasSize: Size)` and added `getVerticalExtras(context, layerDimensions)` for a pre-height measurement pass invoked from `ChartHostBox`. Our two-pass hack and upstream's new pre-measure pass are solving overlapping problems in incompatible ways; this hunk needs to be re-derived, not ported. **Flag for the axis-cluster owner** — upstream's `getVerticalExtras` may already make our first pass unnecessary.

---

## H-8 — External `flingBehavior`

**What it does.** Threads a caller-supplied `FlingBehavior?` from the public host through `CartesianChartHostImpl` into `Modifier.pointerInput(..., flingBehavior = flingBehavior)`, where it is handed to `scrollable(flingBehavior = …)`. It is also passed into `ScrollAwareRangeEffect` (Host:174, 567) — where, as of HEAD, it is **unused** (the parameter is declared and never read).

**Why we needed it.** FEATURE-4: snap-to-period flinging. meApp builds its own `rememberChartSnapFlingBehavior(scrollState, config)` and hands it in (`GraphView.kt:103`).

**Commits.** `01ad34f5d`, `130fde18f`.

**Files and hunks.** `CartesianChartHost.kt` 23 (import), 73, 174, 198, 247, 457, 567.

**Public API added or changed.** `flingBehavior: FlingBehavior? = null` on the producer overload and on `CartesianChartHostImpl` — see H-5 for the full signatures. Not added to the `CartesianChartModel` overload.

**Coupling to upstream internals.** `Modifier.pointerInput(...)` is `internal`; the parameter was added there (M-3).

**Could it be an extension point, or live in meApp instead?** **It already is one upstream, differently.** At v3.3.1 `Modifier.pointerInput` is `@Composable`, builds its own fling behaviour, and selects `SnapFlingBehavior(scrollState, decayAnimationSpec)` when `scrollState.xSnapStep != null`; `VicoScrollState` owns `xSnapStep`, `getSnapDelta` and `snapAnimationSpec`. The port should **delete our parameter** and express meApp's snapping through upstream's `xSnapStep`/`snapAnimationSpec` if the semantics match. *Uncertain:* whether meApp's snap config (which snaps to period boundaries computed from a `HorizontalAxis.ItemPlacer`) can be expressed as a single `xSnapStep`; the FEATURE-4 doc and the snap-fling cluster owner should decide. If it cannot, the fork keeps a parameter — but it should then be named and placed to coexist with upstream's internal default rather than replace it.

**Conflict risk. Very high.** Direct collision: upstream now computes `flingBehavior` inside `Modifier.pointerInput`, which is exactly the argument we pass in.

---

## H-9 — `scrollState.drawingContext`

**What it does.** One line, Host:531: `scrollState.drawingContext = drawingContext` immediately before `chart.draw(drawingContext)`.

**Why we needed it.** `VicoScrollState` exposes consumer-facing helpers that need a live drawing context — `visibleXRange`, `getVisibleAxisLabels(...)`, `getInterpolatedYValues(...)`, `xToScrollValue`, `scrollValueToX`, `xToScrollValueBehindStartInset`. meApp uses `visibleXRange` and `getVisibleAxisLabels` in `GraphView`'s scroll effects.

**Commits.** `7baf04e8b` (FEATURE-8), with later users added in `ac9d19175`.

**Files and hunks.** `CartesianChartHost.kt` 531.

**Public API added or changed.** None in this file. `internal var VicoScrollState.drawingContext: CartesianDrawingContext?` (scroll cluster).

**Coupling to upstream internals.** The context is set but never cleared — it outlives the draw pass and the `DisposableEffect(scrollState) { onDispose { scrollState.clearUpdated() } }` does not null it. *Uncertain:* whether that retains a `Canvas`/`DrawScope` across disposal; worth a look by the scroll-cluster owner, but it is not a migration blocker.

**Could it be an extension point, or live in meApp instead?** The helpers themselves are a reasonable public surface on a scroll state. A cleaner shape is for the host to publish the measured geometry (see H-3) and for the helpers to derive from that, rather than holding the drawing context.

**Conflict risk. Low.** One additive line in a block upstream barely changed.

---

## H-10 — Chart-area height parity

**What it does.** `CartesianChartHostBox` (Host:537–540) is `Box(modifier = modifier.fillMaxWidth(), content = content)`. Upstream at the fork point was `Box(modifier = modifier.heightIn(max = CHART_HEIGHT.dp).fillMaxWidth(), content = content)`.

**Why we needed it.** FEATURE-10 / `14542561e`: "restore v3 chart area height parity". The v4 `heightIn(max = …)` cap meant the chart could not grow to the height meApp gives it; meApp sets the height explicitly on the host modifier (`GraphView.kt:98 modifier.height(chartHeight)`, `SnapshotLineChart.kt .height(SNAPSHOT_CHART_HEIGHT)`).

A side effect: `import androidx.compose.ui.unit.dp`, `import androidx.compose.ui.draw.alpha` and `import com.patrykandpatrick.vico.compose.common.Defaults.CHART_HEIGHT` (Host:28–29, 40) are now **dead imports**.

Also in this area: `3b9146ca8` removed an "alpha hiding" phase so the chart renders immediately and `initialScroll` applies with correct `context.ranges` (comment at Host:186–187).

**Commits.** `14542561e`, `3b9146ca8`.

**Files and hunks.** `CartesianChartHost.kt` 186–204, 537–540.

**Public API added or changed.** None, but the *rendered* default height changes, which is an observable behaviour change for any caller that did not constrain the host.

**Could it be an extension point, or live in meApp instead?** Entirely — upstream v3.3.1 already solved it properly. The host now takes `chartAreaHeight: Dp = Defaults.CARTESIAN_CHART_AREA_HEIGHT.dp` and `ChartHostBox(modifier, chartAreaHeight, measureExtras)` measures the coordinate system to that height and *adds* the margins and legend on top, using the new `CartesianChart.getVerticalExtras`. **Delete this adaptation at the port** and pass `chartAreaHeight` (or just keep meApp's explicit `.height(...)`, which overrides it).

**Conflict risk. Very high as a patch, zero as a deletion.** The function we modified no longer exists upstream in that form.

---

## C-1 — `visibleLabelsCount` and label-spacing scale

**What it does.** A new public property on `CartesianChart`. When `> 0`, at the end of `prepare` (Chart:283–289) it computes
```kotlin
private fun calculateLabelSpacingScale(
  layerDimensions: MutableCartesianLayerDimensions,
  startInsetPx: Float,
): Float {
  val availableWidth = layerBounds.width
  val currentSpacing = layerDimensions.xSpacing
  if (currentSpacing <= 0f || availableWidth <= 0f) return 1f
  val desiredSpacing = (availableWidth - startInsetPx) / visibleLabelsCount
  return (desiredSpacing / currentSpacing).toFloat()
}
```
and applies it with `layerDimensions.scale(scaleFactor)` when it is not exactly `1f`.

**Why we needed it.** The dashboard must show exactly N periods in the viewport (7 days for WEEK, 12 months for YEAR, a computed count for TOTAL). `57274083d` removed the earlier `pointSpacingProvider` on `LineCartesianLayer` in favour of this. The `- startInsetPx` term is from `ac9d19175`: the reserved start gap is inside the window, so it comes off the width rather than into the denominator; leaving it out over-spaced the labels by exactly the gap and pushed the last one off the edge.

**Commits.** `57274083d`, `ac9d19175` (UNDOC).

**Files and hunks.** `CartesianChart.kt` 69, 283–289, 304–317 (`calculateLabelSpacingScale`), plus `copy` (~627, ~645) and `rememberCartesianChart` (741–742, and the three forwarding call sites below it).

**Public API added or changed.**
```kotlin
public val visibleLabelsCount: Double = 0.0      // on the internal primary constructor
```
on `CartesianChart`, and as a parameter of
```kotlin
public fun CartesianChart.copy(..., visibleLabelsCount: Double = this.visibleLabelsCount, startInsetDp: Dp = this.startInsetDp, markerController: ... ): CartesianChart

@Composable public fun rememberCartesianChart(..., visibleLabelsCount: Double = 0.0, startInsetDp: Dp = 0.dp, markerController: ...): CartesianChart
```
**It is NOT on the `protected` secondary constructor** (Chart:193–227), so a subclass of `CartesianChart` cannot set either new property. Asymmetry worth fixing or documenting.

**Coupling to upstream internals.** `MutableCartesianLayerDimensions.scale(factor)` (internal extension), `layerBounds`, `layerDimensions.xSpacing`, and the exact position in `prepare` *after* `setLayerBounds`.

**Could it be an extension point, or live in meApp instead?** It cannot live in meApp — `xSpacing` is produced during measurement. It could plausibly be upstreamed as a `CartesianLayerPadding`/`xSpacing` strategy, but a simpler fork-minimising form exists: express it as a `CartesianLayerDimensions` transform supplied by the caller, so one hook covers both C-1 and C-2 instead of two properties.

**Conflict risk. Medium.** The insertion point in `prepare` survives upstream's `measure`/`prepare` split (both the `setLayerBounds` call and the lines after it are unchanged context), but `prepare` gained a `canvasSize: Size` parameter and its first half moved into `measure`.

**Latent defect found.** `visibleLabelsCount` is in neither `equals` nor `hashCode`; `startInsetDp` is in `equals` but **not** in `hashCode`. That breaks the equals/hashCode contract on a `@Stable` Compose type. Two charts differing only in `visibleLabelsCount` compare equal.

---

## C-2 — `startInsetDp`: reserved space before the first entry

**What it does.** A new public `Dp` property. At the end of `prepare` (Chart:291–300):
```kotlin
if (startInsetDp > 0.dp) {
  layerDimensions.ensureValuesAtLeast(unscalableStartPadding = startInsetDp.pixels)
}
```

**Why we needed it.** The chart needs a fixed gap between the start axis and the first data point. Asking for it through the initial scroll offset cannot work: the scroll clamps at zero, so at the start of the chart there is nothing behind the first entry to scroll into and the gap collapses. Reserving it as **unscalable** padding means zooming moves the data under a margin that stays put rather than stretching it, and `getFullXRange` extends the scrollable range back by `startPadding`, making the space real at every position.

**Commits.** `9a7799e00` (UNDOC), `b323662e6` (UNDOC — "one source for the space before a chart's first entry", collapsing a duplicate), `ac9d19175` (UNDOC — changed it from a fraction of an x-step to a length in dp).

**Files and hunks.** `CartesianChart.kt` 70, 291–300; `copy`/`rememberCartesianChart` as in C-1.

**Public API added or changed.**
```kotlin
public val startInsetDp: Dp = 0.dp
```
(same constructor/`copy`/`rememberCartesianChart` story as C-1, including the missing `protected` constructor parameter).

**Coupling to upstream internals.** `MutableCartesianLayerDimensions.ensureValuesAtLeast(unscalableStartPadding = …)` is upstream public API on an internal-ish type; `Dp.pixels` is an upstream extension on the measuring context (`with(context)` scope). The scroll-side half — `getFullXRange` extending by `startPadding`, and `VicoScrollState.xToScrollValueBehindStartInset` — lives in the scroll cluster.

**Could it be an extension point, or live in meApp instead?** `CartesianLayerPadding` already exists upstream (`layerPadding: (ExtraStore) -> CartesianLayerPadding`) and has `unscalableStart`. **Check first whether `CartesianLayerPadding(unscalableStartPadding = …)` already does this at v3.3.1** — if it does, C-2 is a pure deletion and meApp just passes a `layerPadding` lambda. I did not evaluate v3.3.1's `CartesianLayerPadding` (out of scope for this dossier), but this is the highest-value thing for the layer-cluster owner to check, because C-3 (below) is only needed *because* the inset exists.

**Conflict risk. Medium** — same positional story as C-1.

---

## C-3 — Start-strip clipping, and the start-axis line inside it

**What it does.** In `draw()` (Chart:324–361), computes
```kotlin
val contentStart = layerBounds.left + layerDimensions.startPadding - scroll
val hidesStrip = startInsetDp > 0.dp && contentStart > layerBounds.left
fun clippedToChart(block: () -> Unit) { /* canvas.save(); canvas.clipRect(contentStart, 0f, canvasSize.width, canvasSize.height); block(); canvas.restore() */ }
```
and wraps **only** `axisManager.drawUnderLayers` and `axisManager.drawOverLayers`. When the strip is hidden it then draws the start axis line explicitly, outside the clip: `if (hidesStrip) axisManager.drawStartAxisLine(context)`.

**Why we needed it.** Nothing may be painted in the reserved strip. The comment records every thing that was tried and rejected:
* clipping `layerBitmap` alone was not enough — guidelines, separators, ticks and axis lines paint straight onto the canvas, before and after the bitmap;
* the layers are **not** clipped: they draw from the first entry onwards and the strip ends exactly on that entry, so clipping only sliced that entry's point in half;
* markers (active and persistent) are **not** clipped: a marker sits on the strip's edge and reaches back over it with its bubble, so clipping halved it — and on a non-scrolling chart the clip is permanently on and the marker never appeared at all;
* decorations are **not** clipped: a goal line spans the plot, and clipping stopped it short of the start axis, leaving a gap against the very edge the axis line marks;
* the start axis *line* belongs in the strip, because it marks where the chart begins — hence `drawStartAxisLine`.

**Commits.** `9a7799e00` (UNDOC), refined by `91e72bf64` and `1bf0a6d9a` (both UNDOC).

**Files and hunks.** `CartesianChart.kt` 326–361 (comment block, `contentStart`/`hidesStrip` at 349–350, `clippedToChart` at 352–361), 371 and 375 (the two `clippedToChart` call sites), 372–374 (`drawStartAxisLine`).

**Public API added or changed.** None public. Requires (axis cluster):
```kotlin
fun AxisManager.drawStartAxisLine(context: CartesianDrawingContext)
```

**Coupling to upstream internals.** `CartesianDrawingContext.canvas` save/clip/restore, `layerDimensions.startPadding`, `scroll`, `canvasSize`, `layerBounds`, and `AxisManager`'s two draw passes being separable.

**Could it be an extension point, or live in meApp instead?** No, and it should not need to exist. If C-2 can be expressed with upstream's `CartesianLayerPadding`, then whether upstream already keeps its axes out of the unscalable start padding is the question to answer — if it does, C-3 disappears with C-2. If it does not, this is a bug report upstream would probably accept, which is a better outcome than a permanent `draw()` patch.

**Conflict risk. Low–Medium.** `CartesianChart.draw()` is **byte-identical** at `v3.3.1` and at `e67e49520` — upstream did not touch it. The graft is mechanical. The risk is semantic only: `measure`/`prepare` now run at different times, so `layerDimensions.startPadding` at draw time should be re-verified.

---

## C-4 — Draw order: the data on top of what describes it

**What it does.** Reorders `draw()` so the composited layer bitmap goes **last**, over the axes and the marker (Chart:362–399). Stacking, bottom to top: guidelines/separators → axis → marker → points. Upstream's order composited `layerBitmap` first and then drew `axisManager.drawOverLayers`, decorations, persistent markers' over-pass and the active marker's over-pass on top of it. Persistent and active markers now get both their under- and over-passes in one place (Chart:383–386 and 391–395).

**Why we needed it.** An axis line and a marker were drawing over the very point they refer to.

**Commits.** `91e72bf64` (UNDOC).

**Files and hunks.** `CartesianChart.kt` 362–399.

**Public API added or changed.** None. Behaviourally visible to any `Decoration`, `CartesianMarker` or `Axis` implementation that assumed the old z-order.

**Coupling to upstream internals.** `getBitmap(cacheKeyNamespace)`, `withCanvas`, `EmptyPaint`, `canvas.drawImage`, `fadingEdges.draw` + the `saveLayer`/`restore` pairing — all upstream internals, and the fading-edge layer bracket is now load-bearing for C-5.

**Could it be an extension point, or live in meApp instead?** No. This is a global z-order decision. The fork-minimising form would be an upstream `drawOrder` enum, but a one-line reorder is cheap to re-apply and probably not worth asking upstream for.

**Conflict risk. Low** — `draw()` is unchanged upstream, so this is a clean graft.

---

## C-5 — Marker decorations drawn past the fading-edge layer

**What it does.** After `fadingEdges?.run { draw(context); canvas.restore() }` and before `legend?.draw(context)`, calls `axisManager.drawMarkerDecorations(context)` (Chart:404–408).

**Why we needed it.** meApp's goal pill is an axis marker decoration pinned to a value that may sit outside the visible range; the layer's bounds used to slice whatever hung past the canvas. Drawing it past the fading-edge `saveLayer` gives it the same freedom the marker's final over-pass has always had, so no chart has to reserve room for it.

**Commits.** `1bf0a6d9a` (UNDOC).

**Files and hunks.** `CartesianChart.kt` 404–409.

**Public API added or changed.** None public. Requires (axis cluster):
```kotlin
fun AxisManager.drawMarkerDecorations(context: CartesianDrawingContext)
```
and the `markerDecoration` parameter on `endAxis(...)` that meApp uses (`ProductChart.kt:96`).

**Coupling to upstream internals.** Placement relative to the `saveLayer`/`restore` bracket is the whole point, so it is coupled to `fadingEdges`' implementation detail.

**Could it be an extension point, or live in meApp instead?** It is already close to one — a decoration that is drawn unclipped is a generic need. Upstream's `Decoration.drawOverLayers` would be the natural home if it were drawn outside the fade layer. Worth proposing as a `Decoration` ordering flag rather than an axis concept.

**Conflict risk. Low.**

---

## C-6 — Marker resting pass

**What it does.** The final line of `draw()` (Chart:410–412):
```kotlin
if (drawMarker) marker?.drawOverLayers(context, markerTargets) else marker?.drawResting(context)
```

**Why we needed it.** The marker's label line carries one of two things: the marker's own label while a point is marked, the marker's resting text the rest of the time. Pairs with H-4, which supplies the window that text is formatted from. meApp's `SnapshotLineChart` uses it for the week range (`rememberRangeOnlyMarker(rangeText)`), with a non-interactive controller so a touch never replaces it (M-4).

**Commits.** `2433daf0d` (UNDOC), `08d194bbd` (UNDOC).

**Files and hunks.** `CartesianChart.kt` 410–412.

**Public API added or changed.** None here. Requires:
```kotlin
public fun CartesianMarker.drawResting(context: CartesianDrawingContext) {}
```

**Coupling to upstream internals.** `CartesianChart.marker` is `internal`; the `drawMarker` boolean is local.

**Could it be an extension point, or live in meApp instead?** The resting label is a *chart header*, not really a marker: meApp draws it on the chart's label line only to get the same place, size and inset as the dashboard. Drawing it in meApp above the host, with the window supplied by a public `visibleXRange` (H-3), removes C-6, H-4 and `CartesianMarker.onScrollSettled`/`drawResting` entirely. **Strong candidate for moving out of the library.**

**Conflict risk. Low.**

---

## C-7 — Interpolated (synthesised) marker targets

**What it does.** `private fun synthesizeInterpolatedTargets(context, x): List<CartesianMarker.Target>` (Chart:544–602, doc comment from 544, body from 551) builds a marker target at an arbitrary X when no data point sits there:
* computes `canvasX` from `drawingStart + layoutDirectionMultiplier * xSpacing * ((x - ranges.minX) / ranges.xStep)`;
* returns empty if `canvasX` falls outside `layerBounds` ±1;
* creates **one target per enabled layer**, skipping layers whose `markerTargetsEnabled == false` (meApp's CDC percentile bands) and non-`LineCartesianLayerModel` models;
* interpolates Y with `MonotoneInterpolator.getYAtXFromEntries(x, series)` (O(log n), no list allocation) and emits `LineCartesianLayerMarkerTarget.Point(entry, canvasY, color = Color.Transparent, isInterpolated = true)`.

Used in two places: as the fallback for the active marker (`getMarkerTargets(markerX, markerSeriesIndex).ifEmpty { markerX?.let { synthesizeInterpolatedTargets(context, it) } ?: emptyList() }`, Chart:387–390) and for persistent markers (`forEachPersistentMarker`, Chart:483–490), which required stashing the drawing context in a `private var drawingContext: CartesianDrawingContext?` set at the top of `draw()` and nulled at the bottom (declaration Chart:481, `drawingContext = context` at 325, `drawingContext = null` at 414).

**Why we needed it.** The consumer's callback (H-6) may return an X that is not a data point — e.g. snapped to an axis label in a window with no entries. FEATURE-8 covers the base; `06209b8c8` made it per-layer and added the `markerTargetsEnabled` skip after a single shared target drew the percentile band's value.

**Commits.** `7baf04e8b`, `f9ce73e95`, `2c00808b6`, `06209b8c8`.

**Files and hunks.** `CartesianChart.kt` 325 and 414 (context stash/clear), 387–390, 481, 483–490, 544–602, plus imports at 25–26, 30, 34–36.

**Public API added or changed.** None public in this file. Requires:
```kotlin
internal fun MonotoneInterpolator.getYAtXFromEntries(x: Double, series: List<LineCartesianLayerModel.Entry>): Double?
internal class MutableLineCartesianLayerMarkerTarget(x: Double, canvasX: Float)
LineCartesianLayerMarkerTarget.Point(entry, canvasY, color, isInterpolated: Boolean)   // isInterpolated ADDED
public var LineCartesianLayer.markerTargetsEnabled: Boolean
internal val LineCartesianLayer.internalVerticalAxisPosition: Axis.Position.Vertical?
```

**Coupling to upstream internals.** Heavy: `context.ranges`, `layerDimensions.startPadding`/`xSpacing`, `isLtr`, `layoutDirectionMultiplier`, `scroll`, `layerBounds`, `context.model.models`, and the private `layers`/`persistentMarkerMap`. The duplicated canvas-X maths is the real hazard — it re-implements the layer's own point placement, so any upstream change to how a layer maps x→canvasX silently desynchronises the synthesised target from the drawn line.

**Could it be an extension point, or live in meApp instead?** Not in meApp (it needs draw-time geometry). The fork-minimising form is to ask the **layer** for the position rather than recomputing it: a `CartesianLayer.getInterpolatedTarget(context, x): Target?` would put the maths beside the code that draws the line and remove ~50 lines from `CartesianChart`. Worth proposing upstream — "markers at arbitrary x" is a reasonable library feature.

**Conflict risk. Medium.** `draw()` and `forEachPersistentMarker` are untouched upstream, so the graft is clean; the exposure is to changes in the layer's x→canvasX mapping elsewhere in v3.3.1, which another cluster owns.

---

## C-8 — Marker-target bookkeeping

**What it does.** Two changes.
1. `allMarkerTargetXValues` (Chart:161–171) — an `internal val` that returns `_markerTargets.keys.toList()`, cached and invalidated on size change:
```kotlin
private var cachedMarkerTargetXValues: List<Double> = emptyList()
private var cachedMarkerTargetSize: Int = -1
internal val allMarkerTargetXValues: List<Double>
  get() { /* rebuild when _markerTargets.size != cachedMarkerTargetSize */ }
```
2. The per-frame re-sort of `_markerTargets` was **deleted** (upstream's `sortedMarkerTargetPairs` / `clear` / `putAll` at the top of the bitmap block), with the comment "TreeMap keeps `_markerTargets` sorted by key — no per-frame sort needed".

**Why we needed it.** (1) feeds H-6's consumer callback with every candidate X. (2) was a per-frame allocation (`57ce985ea`).

**Commits.** `2c00808b6`, `57ce985ea`, `10fa978bb`.

**Files and hunks.** `CartesianChart.kt` 161–171; the deletion sits where line 382 (`// TreeMap keeps _markerTargets sorted by key`) now stands.

**Public API added or changed.** `internal val allMarkerTargetXValues: List<Double>`.

**Coupling to upstream internals.** `_markerTargets` is upstream's private map, written by `drawingConsumer`.

**⚠ Correctness concern — carry into the migration.** The comment claims a `TreeMap`, but `_markerTargets` at HEAD is `mutableMapOf<Double, MutableList<CartesianMarker.Target>>()` (Chart:83) — a `LinkedHashMap`, **not sorted**. Commit `10fa978bb` changed `TreeMap` → `mutableMapOf` for Kotlin/Multiplatform `commonMain` compatibility and the comment was not updated. Upstream's sort therefore did real work and we removed it. The cache in (1) is also only size-keyed: if the set of X values changes while the count stays the same (a scroll that swaps one entry for another), the cached list is stale. Both are latent bugs, not migration blockers, but they should be fixed rather than ported. *Uncertain:* whether anything downstream actually depends on `markerTargets` iteration order at v3.3.1 — worth a check by whoever owns `DefaultCartesianMarker`.

**Conflict risk. Medium** — deleting four upstream lines that are still present verbatim at `v3.3.1` is a clean but deliberate divergence.

---

## M-1 — Scrub gesture state machine

**What it does.** `Modifier.pointerInput(...)` branches: when `markerController is ScrubMarkerController && onInteraction != null`, it runs a five-state machine (Modifier:68–239) instead of upstream's flat event loop; otherwise it runs an exact copy of upstream's loop (Modifier:240–270). States: `NONE`, `DECIDING`, `MARKER_SELECTION`, `MARKER_SCRUBBING`, `SCROLLING` (private enum, Modifier:313–320). Constants: `MOVEMENT_THRESHOLD = 20f`, `TAP_SLOP = 5f`.

Behaviour:
* **Press** → `DECIDING`, start a `scrubController.delayMs` timer on a `CoroutineScope(currentCoroutineContext())`; emit `Interaction.Press`. If the timer fires while still `DECIDING` → `MARKER_SELECTION`, `isScrubbing = true`, `scrollState.isScrollFrozen = true`, emit `Interaction.LongPress`.
* **Move in `DECIDING`** → once movement exceeds `MOVEMENT_THRESHOLD`, dominant axis decides: `dx > dy` → `SCROLLING` (cancel timer, unfreeze, and if a marker was up, `scrubController.onDismiss()` + emit `Release`); otherwise → `NONE` (let the parent `LazyColumn` take it). Small movements are deliberately **not** consumed, so `scrollable` can cancel an in-flight fling/snap.
* **Move in `MARKER_SELECTION`/`MARKER_SCRUBBING`** → consume every change (blocks the parent `LazyColumn`) and emit `Interaction.Move`.
* **Release** → `DECIDING` with movement `< TAP_SLOP` emits `Interaction.Tap`, otherwise `Interaction.Release`; marker states consume the release, clear `isScrubbing`/`isScrollFrozen` and emit `Release`; `SCROLLING` emits nothing.

**Why we needed it.** iOS-Health scrubbing inside a vertically scrolling `LazyColumn`: a tap toggles, a 200 ms hold starts a scrub, a horizontal swipe scrolls the chart, a vertical swipe scrolls the list — and the marker must dismiss when the chart scrolls.

**Commits.** `ccf2ff5cf` (FEATURE-2), `9886f978c`, `3a284be42`, `130fde18f`, `2ddae34f4`.

**Files and hunks.** `Modifier.kt` 19–43 (imports + `MOVEMENT_THRESHOLD`/`TAP_SLOP`), 68–239 (the machine), 240–270 (the preserved copy of upstream's old loop), 313–320 (the `InteractionMode` enum).

**Public API added or changed.** None public from this file. Drives `ScrubMarkerController.isScrubbing` (public, internal setter), `hasActiveMarker` and `onDismiss()` (internal), and `VicoScrollState.isScrollFrozen` (internal).

**Coupling to upstream internals.** `awaitPointerEventScope`, `PointerEventType`, `PointerInputChange.consume`, `Offset.fits(size)`, `scrollState.scrollEnabled`, `Interaction.*`. Also a `CoroutineScope(currentCoroutineContext())` created inside the pointer-input block — the delay job's lifetime is the pointer-input lifetime, which is correct but fragile.

**Could it be an extension point, or live in meApp instead?** Not in meApp: it must sit inside the chart's own `pointerInput` to interleave with `scrollable` and to consume before the parent list. Upstream-shaped alternatives: Compose's own `detectDragGesturesAfterLongPress`, or a `CartesianMarkerController` that is allowed to *drive* gesture interpretation rather than only react to it. If the fork keeps it, isolating it in a **new file** (e.g. `ScrubGesture.kt`) and reducing `Modifier.kt` to a one-line delegation would cut the conflict surface here from 234 lines to ~5 — **strongly recommended for the port**.

**Conflict risk. Very high.** Upstream rewrote `Modifier.pointerInput` wholesale: it is now `@Composable`, returns `Modifier` explicitly, takes `onInteraction: ((Interaction) -> Boolean)?`, accumulates a `consume` boolean per event and consumes via `event.changes.fastForEach { it.consume() }`, and ships its own `detectTapGestures` built on `awaitEachGesture`/`awaitLongPressOrCancellation` with a context-parameter `isTap` helper. Our preserved "default upstream behaviour" branch is a copy of the **old** loop and must be replaced by the new one; our machine must be re-expressed against the `Boolean` return.

---

## M-2 — `scrollable(enabled = true)` and freeze-instead-of-disable

**What it does.** `scrollable(..., enabled = true, ...)` unconditionally (Modifier:59–67), with the comment "Always enabled so nestedScroll blocks parent scroll during scrubbing. Actual scroll is frozen via ScrollableState when `scrollEnabled = false` or during scrub." The freeze lives in `VicoScrollState.isScrollFrozen`, consulted by `scrollableState`'s delta lambda.

**Why we needed it.** `scrollable(enabled = false)` detaches the nested-scroll node, so a non-scrollable chart (or one frozen mid-scrub) let the parent `LazyColumn` take the gesture. Freezing inside `ScrollableState` keeps the node attached and therefore keeps the parent blocked (`3a284be42`, `2ddae34f4`).

**Commits.** `3a284be42`, `2ddae34f4`.

**Files and hunks.** `Modifier.kt` 59–67.

**Public API added or changed.** None here; `internal var VicoScrollState.isScrollFrozen` and `public val VicoScrollState.isUserScrolling: Boolean get() = isScrolling && !isScrollFrozen` (scroll cluster, `41613334a`). meApp reads `isUserScrolling` at `GraphView.kt:334`.

**Coupling to upstream internals.** `VicoScrollState.scrollableState` and `scrollEnabled`.

**Could it be an extension point, or live in meApp instead?** No — it is a two-line behaviour choice inside the library. It is a good upstream bug report ("a non-scrollable chart should still consume horizontal drags so the parent list does not steal them"), and if upstream accepted it the fork would carry nothing.

**Conflict risk. Very high** — same rewritten region as M-1; upstream still has `enabled = scrollState.scrollEnabled`.

---

## M-3 — External `flingBehavior` on `Modifier.pointerInput`

**What it does.** Adds `flingBehavior: FlingBehavior? = null` as the last-but-one parameter and passes it to `scrollable(flingBehavior = flingBehavior)`.

**Why we needed it.** FEATURE-4 snapping; see H-8.

**Commits.** `01ad34f5d`, `130fde18f`.

**Files and hunks.** `Modifier.kt` 19 (import), 57 (parameter), 62 (passed to `scrollable`).

**Public API added or changed.**
```kotlin
@Composable
internal fun Modifier.pointerInput(
  scrollState: VicoScrollState,
  onInteraction: ((Interaction) -> Unit)?,
  onZoom: ((Float, Offset) -> Unit)?,
  consumeMoveEvents: Boolean,
  longPressEnabled: Boolean,
  markerController: CartesianMarkerController? = null,   // ADDED (M-1/M-4)
  flingBehavior: FlingBehavior? = null,                  // ADDED
)
```
(Ours is **not** `@Composable`; upstream's v3.3.1 version is.)

**Coupling to upstream internals.** `androidx.compose.foundation.gestures.scrollable`.

**Could it be an extension point, or live in meApp instead?** See H-8 — upstream now owns this. Prefer deleting it in favour of `VicoScrollState.xSnapStep`/`snapAnimationSpec` if the semantics match.

**Conflict risk. Very high** — direct collision.

---

## M-4 — `acceptsGestures` gate on the tap detector

**What it does.** The `detectTapGestures` branch is now gated three ways (Modifier:272–283):
```kotlin
if (
  onInteraction != null &&
    markerController !is ScrubMarkerController &&
    markerController?.acceptsGestures != false
) { ... } else { Modifier }
```
`ScrubMarkerController` is excluded because M-1 already produces `Tap`/`LongPress`. `acceptsGestures` is a new property on `CartesianMarkerController` (default `true`), set `false` by the new `NonInteractiveMarkerController`.

**Why we needed it.** A chart with a marker attaches a tap detector, which **consumes** the tap. For a controller that never marks anything that is worse than useless: it swallows the gesture on the way to whatever surrounds the chart, so a card built to be tapped stops responding. meApp's snapshot card hit exactly this (`SnapshotLineChart.kt:145 markerController = CartesianMarkerController.rememberNonInteractive()`).

**Commits.** `3faabb926` (UNDOC).

**Files and hunks.** `Modifier.kt` 29 and 31 (imports), 57 (`markerController` parameter), 272–283 (the gate).

**Public API added or changed.** In the marker cluster, but driven from here:
```kotlin
public val CartesianMarkerController.acceptsGestures: Boolean get() = true
@Composable public fun CartesianMarkerController.Companion.rememberNonInteractive(): CartesianMarkerController
```

**Coupling to upstream internals.** The `.then(if (onInteraction != null) …)` chain.

**Could it be an extension point, or live in meApp instead?** **Upstream v3.3.1 appears to have solved this generically**: `onInteraction` now returns `Boolean`, and upstream's own `detectTapGestures` only calls `inputChange.consume()` when the handler returns `true`. A controller whose `shouldAcceptInteraction` returns `false` would therefore no longer swallow the tap. If that holds, `acceptsGestures` and the gate become a **deletion** and `rememberNonInteractive` reduces to a controller that returns `false` from both methods. **This is the cleanest fork reduction in the Modifier half — verify it first.** *Uncertain:* I read upstream's code but did not run it; confirm that the `false` path really leaves the tap unconsumed for a surrounding clickable.

**Conflict risk. High** — the condition sits in a `.then(...)` block upstream reshaped, and the semantics may be superseded.

---

## 1.1 Consumer usage in meApp (`/Users/selvakumar/Projects/meApp-bug/Android`)

| Symbol (ours) | meApp call site |
|---|---|
| `CartesianChartHost(chart, modelProducer, …)` | `app/src/main/java/com/dmdbrands/gurus/weight/features/dashboard/components/chart/GraphView.kt:94`; `…/dashboard/snapshot/components/SnapshotLineChart.kt:152` |
| `animateIn = false` | `GraphView.kt:101`, `SnapshotLineChart.kt:166` — **both break at v3.3.1** (param replaced by `initialAnimationSpec`, surviving only on a `@Deprecated` overload) |
| `flingBehavior = holder.flingBehavior` | `GraphView.kt:103` |
| `initialMarkerX = segmentState.markerIndex` | `GraphView.kt:104` |
| `placeholder = { GraphSkeletonView(...) }` | `GraphView.kt:109` |
| `rememberCartesianChart(..., visibleLabelsCount, startInsetDp, getXStep, markerController)` | `features/common/components/chart/ProductChart.kt:87–105`; `SnapshotLineChart.kt:132–150` |
| `visibleLabelsCount` | `ProductChart.kt:101` (from `GraphUtil.visibleLabelsCount()`), `SnapshotLineChart.kt:147` (`7.0`) |
| `startInsetDp` | `ProductChart.kt:104` (`ChartStartGap`), `SnapshotLineChart.kt:148` (`SnapshotChartStartGap`) |
| `getXStep = { _: CartesianChartModel -> … }` | `ProductChart.kt:97`, `SnapshotLineChart.kt:150` — **both break at v3.3.1** (lambda is now `(model, minX, maxX) -> Double`; a `@Deprecated` single-arg overload exists) |
| `CartesianMarkerController.rememberNonInteractive()` | `SnapshotLineChart.kt:145` |
| `rememberScrubMarkerController(scrollState, onMarkerIndexChanged = { clickX, targets, interaction -> … })` | `GraphView.kt:268–277` |
| `interaction is Interaction.Tap` | `GraphView.kt:531` |
| `VicoScrollState.isUserScrolling` | `GraphView.kt:334` |
| `ScrollAwareRangeProvider.Window` / `.WindowKey` | `features/dashboard/viewmodel/base/BaseDashboardViewModel.kt:41, 51, 60–62, 170` |
| (none) | `GraphPagerView.kt` imports only `CartesianChartModelProducer`; it delegates to `GraphView` |

`SnapshotLineChart` composes a chart with `scrollEnabled = false` and `zoomEnabled = false`, which is the configuration M-2 and M-4 were both written for.

---

# Part 2 — Build and publishing

## 2.0 Summary table

| ID | Change | Where | Final state at HEAD | Breaks at v3.3.1? |
|---|---|---|---|---|
| B-1 | Group → `com.dmdbrands.lib` | `vico/build.gradle.kts:20` | live | no — one line, upstream untouched |
| B-2 | Fork version line `4.0.x`, now `4.0.46` | `buildSrc/src/main/kotlin/Versions.kt:20` | live | no (value conflict only; upstream sets `3.3.1`) |
| B-3 | GPR repository on `:vico:compose`, `publishing-convention` swapped for `maven-publish` | `vico/compose/build.gradle.kts:20–24, 84–108` | live | **yes** — adjacent lines rewritten upstream |
| B-4 | `gpr-publishing-convention.gradle.kts` (artifact `vico-gg`, POM dep-stripping, signing off) | `buildSrc/src/main/kotlin/` | **dead — applied by nothing** | n/a — delete |
| B-5 | POM transitive-dep stripping | abandoned | **not in effect** (4.0.46's POM carries all 6 deps) | n/a |
| B-6 | `.module` metadata suppression | abandoned | **not in effect** (`compose-android-4.0.46.module` is published) | n/a |
| B-7 | "Publish the AAR directly like v3" | abandoned | **not in effect** (standard KMP publications) | n/a |
| B-8 | Publish repo org `dmdbrands` → `gg-engineering` | `0eca6f8b5`, two files | live | no |
| B-9 | Kotlin pinned `2.3.20` → `2.1.21` | `gradle/libs.versions.toml:14` | live | **yes** — upstream moves to `2.4.10` |
| B-10 | `gradle-local.properties` ignored | `.gitignore:16` | live | no |
| B-11 | Fork-only sample chart `DmdBrandsTestChart.kt` + 4 sample edits | `sample/charts/compose/`, `sample/app/` | live | **yes** — both Gradle modules deleted at v3.3.1 |
| B-12 | `dokka(project(":vico:views"))` retained in our copy of `vico/build.gradle.kts` | `vico/build.gradle.kts:29` | live (upstream default at fork point) | **yes if our file is taken wholesale** — module deleted |

## 2.1 The final working publishing configuration

Established by `1c1693f92` ("Change group to com.dmdbrands.lib, publish KMP Android target + 4.0.9") and unchanged since, apart from the org rename in `0eca6f8b5`.

**Coordinates.** Group `com.dmdbrands.lib`, set in `vico/build.gradle.kts`:
```kotlin
subprojects {
  group = "com.dmdbrands.lib"          // was "com.patrykandpatrick.vico"
  version = Versions.VICO              // "4.0.46"
}
```
Artifact IDs come from the Kotlin Multiplatform plugin's standard publication naming, i.e. `<project.name>` plus a target suffix. `project.name` for `:vico:compose` is **`compose`**, so the published set is:

| Publication | Coordinate | Versions in GPR today |
|---|---|---|
| root (metadata) | `com.dmdbrands.lib:compose` | 9 |
| **Android** | `com.dmdbrands.lib:compose-android` | **37 — the one meApp consumes** |
| desktop (JVM) | `com.dmdbrands.lib:compose-desktop` | 12 |
| iOS arm64 | `com.dmdbrands.lib:compose-iosarm64` | 10 |
| iOS simulator arm64 | `com.dmdbrands.lib:compose-iossimulatorarm64` | 8 |
| iOS x64 | `com.dmdbrands.lib:compose-iosx64` | 6 |

Dead coordinates from earlier eras, still present in the registry: `com.dmdbrands.lib:vico-gg` (7), `vico-core` (24), `vico-compose` (21), `vico-compose-m3` (20), plus two stray `com.patrykandpatrick.vico.compose*` packages.

**Repository.** `https://maven.pkg.github.com/gg-engineering/vico`, declared only on `:vico:compose`:
```kotlin
plugins {
  `dokka-convention`
  `maven-publish`                      // replaces upstream's `publishing-convention`
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.multiplatform")
  id("org.jetbrains.kotlin.plugin.compose")
}
...
val localProps = Properties()
val localPropsFile = rootProject.file("gradle-local.properties")
if (localPropsFile.exists()) localProps.load(localPropsFile.inputStream())

publishing {
  repositories {
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/gg-engineering/vico")
      credentials {
        username = localProps.getProperty("gpr.user") as String?
          ?: project.findProperty("gpr.user") as String?
          ?: System.getenv("GITHUB_USERNAME") ?: "Selva-GG"
        password = localProps.getProperty("gpr.token") as String?
          ?: project.findProperty("gpr.token") as String?
          ?: System.getenv("GITHUB_TOKEN") ?: ""
      }
    }
  }
}
```
No `publications { }` block of our own: the KMP plugin's generated publications are published as-is. Credentials come from `gradle-local.properties` at the repo root (present on this machine, gitignored since `543a112ac`, keys `gpr.user` / `gpr.token`), falling back to Gradle properties then to `GITHUB_USERNAME`/`GITHUB_TOKEN`, with a hardcoded `"Selva-GG"` username default and an empty-string token default.

**What is in the POM.** Nothing is stripped. `com.dmdbrands.lib:compose-android:4.0.46`'s POM (verified from the Gradle cache at `~/.gradle/caches/modules-2/files-2.1/com.dmdbrands.lib/compose-android/4.0.46/`) is `packaging=aar`, carries the `do_not_remove: published-with-gradle-metadata` marker, and declares six `runtime`-scope dependencies:
`androidx.annotation:annotation-jvm:1.9.1`, `org.jetbrains.compose.foundation:foundation:1.10.3`, `org.jetbrains.compose.runtime:runtime:1.10.3`, `org.jetbrains.compose.ui:ui:1.10.3`, `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2`, `org.jetbrains.kotlin:kotlin-stdlib:2.1.21`.
They are `runtime`-scoped because `vico/compose/build.gradle.kts` declares them with `implementation`, not `api`.

**How the AAR is produced.** By the AGP KMP plugin (`com.android.kotlin.multiplatform.library`), via `kotlin { androidLibrary { configure(); namespace = moduleNamespace; withHostTest { … } } }`. Not by a hand-wired `artifact(...)`. The published file is named `compose.aar` in the cache. Note that `moduleNamespace` is `"${group}.${project.name.replace("-", ".")}"` (`buildSrc/src/main/kotlin/Project.kt`), so changing the group also changed the Android namespace to **`com.dmdbrands.lib.compose`**. Kotlin package names are untouched (`com.patrykandpatrick.vico.compose.*`).

**Also published:** a sources jar (`compose-android-4.0.46-sources.jar`) and Gradle module metadata (`compose-android-4.0.46.module`).

**Publish command.** Not recorded anywhere in the repo (no README/AGENTS/docs entry, no CI job). The upstream `.github/workflows/publish.yml` is unmodified and is guarded by `if: github.repository == 'patrykandpatrick/vico'`, so it never runs for the fork — publishing is a manual local Gradle invocation. *Uncertain:* the exact task; from the configuration it must be `./gradlew :vico:compose:publishAllPublicationsToGitHubPackagesRepository` (or a per-publication variant). **Document this during the migration** — it is currently tribal knowledge.

**Signing.** Not configured on `:vico:compose` (we dropped `publishing-convention`, which applies vanniktech's `signAllPublications()`). `gpr-publishing-convention.gradle.kts` still contains signing-disable logic, but nothing applies it.

## 2.2 Each problem the configuration was solving

The 4.0.1 → 4.0.9 sequence is a trail of failed hypotheses; only the last survives. For the migration, **do not reinstate any of 4.0.4–4.0.8.**

1. **`10fa978bb` (4.0.1) — publish at all.** Created `gpr-publishing-convention.gradle.kts`: a single `MavenPublication("gpr")` at `com.dmdbrands.lib:vico-gg`, built `from(components["release"] ?: components["kotlin"])`, GPR repo at `dmdbrands/vico`, signing disabled. Also changed `TreeMap` → `mutableMapOf` for KMP `commonMain` compatibility (see C-8's correctness note — this is where the sort guarantee was silently lost).
2. **`8a417644e` (4.0.4) — transitive deps in the POM.** Stripped the `<dependencies>` node via `pom.withXml`. *Why:* the consumer was resolving Compose Multiplatform artifacts it did not want. **Superseded.**
3. **`19530a75b` (4.0.5) — replace the component with a raw AAR.** `artifact(tasks.named("bundleReleaseAar"))` instead of `from(component)`. **Superseded.**
4. **`c961c9eba` (4.0.6) — "like v3": a literal file path.** `artifact("build/outputs/aar/compose-release.aar")`, with a comment telling the operator to run a compile task first. This is also where the GPR block moved out of the convention plugin and into `vico/compose/build.gradle.kts` and where `publishing-convention` was replaced by `maven-publish`. **Superseded, except for the plugin swap and the file location, both of which are still in force.**
5. **`61d9a0589` (4.0.7) — back to a component, inside `afterEvaluate`.** `from(components["android"] ?: "release" ?: "kotlin")` plus POM dep-stripping. **Superseded.**
6. **`84986e06b` (4.0.8) — `.module` beats the POM.** Disabled `GenerateModuleMetadata`, because Gradle prefers module metadata over the POM and the metadata re-declared the very dependencies the POM stripping removed. This is the diagnosis that invalidated steps 2–5: **stripping the POM alone can never work for a Gradle consumer.** **Superseded.**
7. **`1c1693f92` (4.0.9) — give up on stripping; publish the KMP publications properly.** Deleted the hand-rolled `gpr` publication entirely, kept only `publishing { repositories { maven { … } } }`, and moved the group from the publication to `subprojects { group = … }` so the KMP plugin's own publications carry it. This is the configuration still in force, and it is the right one: Compose Multiplatform's `org.jetbrains.compose.*` artifacts carry Gradle metadata that redirects to the AndroidX artifacts on an Android target, so the transitive dependencies resolve correctly and never needed stripping. meApp declares no exclusions or resolution strategy for them.
8. **`0eca6f8b5` (during MOB-2953) — the org rename.** Both publish URLs still said `dmdbrands`. GitHub Packages' Maven registry does not follow org-rename redirects (the repo API does), so every publish 401'd. Changed both to `gg-engineering`.
9. **Later commits** (`afb365388` 4.0.10, `a8fe03ef8` 4.0.12, `168ea2510` 4.0.37, `6b172a566` 4.0.39, `8e01d0c3d` 4.0.40, `7ac3d4a9e` 4.0.41, `7c6939249` 4.0.42, `17b134500` 4.0.43, `a98cf86de` 4.0.44, `d9d17f08e` 4.0.45, `73e068afb` 4.0.46) touch only `Versions.kt`.

**B-9, the Kotlin pin, deserves its own note.** `57274083d` lowered `kotlin = "2.3.20"` → `"2.1.21"` in `gradle/libs.versions.toml`. The commit is titled "NaN crash guard, remove pointSpacingProvider, scrub LazyColumn fix" and its body does not mention Kotlin at all — the downgrade is **undocumented and incidental**. The plausible motive (match the consumer's compiler) no longer holds: meApp's `Android/gradle/libs.versions.toml` is on `kotlin = "2.4.21"` and `agp = "9.5.1"` today, far ahead of the fork's 2.1.21 / 9.1.0. Migrating to v3.3.1's `kotlin = 2.4.10` therefore moves the fork *closer* to its only consumer, not further away. Treat the pin as removable unless someone can name the failure it prevented.

## 2.3 How meApp resolves the artifact

`/Users/selvakumar/Projects/meApp-bug/Android/settings.gradle.kts`:
* Credentials loaded from `local.properties` (`gpr.user`, **`gpr.key`** — note the key differs from the fork's `gpr.token`) or `GITHUB_USERNAME` / `GITHUB_TOKEN`; a missing pair only logs a warning.
* `dependencyResolutionManagement { repositoriesMode.set(FAIL_ON_PROJECT_REPOS) }` with `google()`, `mavenCentral()`, and **two separate GPR repos**, one per package: `gg-engineering/ggBluetoothNativeLibrary` and `gg-engineering/vico`. The file carries a comment explaining the org rename and that GPR resolution is per-repo, so each package needs its own URL.
* The composite-build path is present but **commented out** at the bottom of the file:
```kotlin
// Local vico development — uncomment to use local vico source
//includeBuild("/Users/selvakumar/Projects/vico") {
//  dependencySubstitution {
//    substitute(module("com.dmdbrands.lib:compose-android")).using(project(":vico:compose"))
//  }
//}
```
Note it hardcodes an absolute path to the **main** vico checkout (the one this migration must not touch), and substitutes `compose-android` with the `:vico:compose` *project*, which is the KMP module rather than a platform variant — Gradle resolves that through variant matching.

`/Users/selvakumar/Projects/meApp-bug/Android/gradle/libs.versions.toml`:
```toml
vico = "4.0.46"
vico-gg = { module = "com.dmdbrands.lib:compose-android", version.ref = "vico" }
```
consumed once, `app/build.gradle.kts:283 implementation(libs.vico.gg)`. meApp depends on the **platform artifact directly**, not on the `com.dmdbrands.lib:compose` root module. The catalog alias is still called `vico-gg`, a leftover from the abandoned `vico-gg` artifactId.

## 2.4 The artifact-hosting answer

**`gg-engineering` hosts the package, and the git remote is stale in name but still functional.**

* `gh api repos/dmdbrands/vico` returns `full_name: gg-engineering/vico`, `owner.login: gg-engineering` — the GitHub **repo API follows the org-rename redirect**, so `git fetch`/`git push` against `git@github.com:dmdbrands/vico.git` still work and nobody noticed the drift.
* `gh api orgs/gg-engineering/packages?package_type=maven` lists `com.dmdbrands.lib.compose-android` with 37 versions, latest `4.0.46` (2026-10-23). There is no `dmdbrands` org package namespace to resolve against.
* The **Maven registry does not follow the redirect**, which is precisely the failure `0eca6f8b5` fixed: publishes to `maven.pkg.github.com/dmdbrands/vico` returned 401.
* Both the fork's publish URL and meApp's resolution URL now say `gg-engineering`; the only thing left saying `dmdbrands` is the git remote (and the dead `Version-2.5.0` remote beside it).

**Recommendation:** `git remote set-url origin git@github.com:gg-engineering/vico.git` (and drop or rename `Version-2.5.0`). Low risk — redirects work today, but they are a courtesy GitHub can withdraw, and the mismatch already cost one debugging session. Also note the group `com.dmdbrands.lib` and the namespace `com.dmdbrands.lib.compose` still carry the old org's name; renaming those would invalidate every published coordinate and is **not** worth doing.

## 2.5 Dependency check against v3.3.1's module layout

Module layout at `e67e49520` (our fork point): `vico`, `vico:compose`, `vico:compose-m2`, `vico:compose-m3`, **`vico:views`**, plus samples `sample:android`, `sample:app`, `sample:charts:compose`, `sample:charts:views`.
Module layout at `v3.3.1`: `vico`, `vico:compose`, **`vico:compose-glance`** (new), `vico:compose-m2`, `vico:compose-m3`, plus samples `sample:android`, `sample:desktop`, `sample:shared`, `sample:web`.
(`vico/core` and `vico/multiplatform*` were already gone before the fork point; `compose-m3-complete` likewise. The only library module that disappears *for us* is `vico:views`.)

Every piece of our configuration that breaks:

| # | Our artefact | Depends on | Status at v3.3.1 | Consequence | Fix |
|---|---|---|---|---|---|
| 1 | `vico/build.gradle.kts` (our copy) line 29 `dokka(project(":vico:views"))` | `:vico:views` | **deleted** | `dokkaGenerate` and configuration itself fail if our file is taken wholesale | Take **upstream's** file and re-apply only `group = "com.dmdbrands.lib"`. Upstream already removed the `views` entry and added `dokka(project(":vico:compose-glance"))`. |
| 2 | `settings.gradle.kts` | `vico:views`, `sample:app`, `sample:charts:compose`, `sample:charts:views` | **all deleted** | n/a — **we never modified this file**; take upstream's verbatim | none |
| 3 | `sample/charts/compose/.../DmdBrandsTestChart.kt` (+270 lines, `10fa978bb`) | Gradle module `sample:charts:compose` | **module deleted** | our only manual test harness for the fork stops building | Port to `sample:shared` (new) or drop. It is the only place our APIs are exercised outside meApp — losing it silently is worse than the port cost. |
| 4 | 4 edits in `sample/app/` (`Charts.kt`, `Charts.android.kt`, `Chart.kt`, `ChartScreen.kt`, 7 lines total) wiring `DmdBrandsTestChart` in | Gradle module `sample:app` | **module deleted** | wiring has no home | Re-wire into `sample:shared`/`sample:android`, or drop with #3. |
| 5 | `buildSrc/src/main/kotlin/gpr-publishing-convention.gradle.kts` | nothing (no module reference) | **not applied by any module at HEAD** — dead since `c961c9eba` | no breakage, but it hardcodes the abandoned `com.dmdbrands.lib:vico-gg` coordinate and stale POM-stripping logic that must never be reinstated | **Delete the file** as part of the migration. |
| 6 | `vico/compose/build.gradle.kts` plugins block — we replaced `` `publishing-convention` `` with `` `maven-publish` `` | the `publishing-convention` precompiled script, which applies vanniktech `com.vanniktech.maven.publish` → Maven Central + `signAllPublications()` | still exists upstream and is still applied by `compose`, `compose-m2`, `compose-m3`, and now `compose-glance` | our swap conflicts textually with upstream's `id("org.jetbrains.compose")` → `alias(libs.plugins.composeMultiplatform)` change on the adjacent line | Re-apply by hand on upstream's plugins block. Do **not** keep both `publishing-convention` and our GPR repo: vanniktech's `signAllPublications()` would require GPG keys we do not have for a local publish. |
| 7 | `vico/compose/build.gradle.kts` — `kotlin { androidLibrary { … } }` | AGP KMP DSL name | upstream renamed it to `kotlin { android { … } }` (AGP 9.3.2) | n/a for our patch (we did not touch it) but it is in the same file | take upstream's |
| 8 | `iosX64()` target | — | **removed upstream** (only `iosArm64()` + `iosSimulatorArm64()` remain) | the `com.dmdbrands.lib:compose-iosx64` publication stops being produced | Harmless — meApp consumes only `compose-android`. Note it so nobody reports the missing package as a regression. |
| 9 | `subprojects { group = "com.dmdbrands.lib" }` | the set of `:vico` subprojects | `compose-glance` is new and inherits the group; `views` no longer does | `compose-glance` would be configured with our group but has **no GPR repository**, so it has no publish task — harmless. | none, but be aware the group now applies to a module we never asked for |
| 10 | `gradle/libs.versions.toml` `kotlin = "2.1.21"` | — | upstream `kotlin = 2.4.10`, `agp = 9.3.2`, `compose = 1.12.0`, Gradle `9.7.1`, `COMPILE_SDK = 37` | hard conflict; keeping our pin would also mean keeping AGP 9.1.0 and Gradle 9.4.1, which v3.3.1's build files do not target | **Drop the pin.** Rationale undocumented, and meApp is already on Kotlin 2.4.21. |
| 11 | `buildSrc/build.gradle.kts` `implementation(libs.compose)` | `compose = { group = "org.jetbrains.compose", name = "compose-gradle-plugin" }` catalog entry | **removed upstream**; the plugin is now declared as `libs.plugins.composeMultiplatform` / `composeMultiplatformSample` and `libs.kotlinJvm` was added | our `id("org.jetbrains.compose")` in `vico/compose/build.gradle.kts` resolves through that buildSrc dependency and would stop resolving | take upstream's `buildSrc/build.gradle.kts` and use `alias(libs.plugins.composeMultiplatform)` |
| 12 | `.gitignore` `gradle-local.properties` | — | upstream file unchanged in that region | clean graft | none |
| 13 | `.github/workflows/publish.yml` | `sample/android` apk path, Maven Central secrets | upstream file, unmodified by us | still guarded by `github.repository == 'patrykandpatrick/vico'`; never runs for us | none, but it means **there is still no CI for our publish** — consider adding one during the migration |

**Net:** nothing in the *live* publishing configuration depends on a deleted module. The breakages are (a) the `views` dokka entry, which disappears if we take upstream's `vico/build.gradle.kts` and re-apply one line, and (b) the fork's sample code, which has no home at v3.3.1. The dead `gpr-publishing-convention.gradle.kts` is the one piece of the build that should be deleted rather than ported.

## 2.6 Migration checklist for Part 2

1. Take upstream's `vico/build.gradle.kts`; re-apply only `group = "com.dmdbrands.lib"`.
2. Take upstream's `settings.gradle.kts`, `buildSrc/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/*` verbatim. Do **not** carry the Kotlin 2.1.21 pin.
3. In `buildSrc/src/main/kotlin/Versions.kt`, keep upstream's `COMPILE_SDK = 37` / `MIN_SDK = 23` and set `VICO` to the next fork version (`5.0.0` would signal the upstream rebase honestly).
4. Take upstream's `vico/compose/build.gradle.kts`; re-apply exactly two things: swap `` `publishing-convention` `` for `` `maven-publish` ``, and append the `publishing { repositories { maven { … gg-engineering … } } }` block plus the `Properties` import and `localProps` loading.
5. Delete `buildSrc/src/main/kotlin/gpr-publishing-convention.gradle.kts`.
6. Decide on `sample/charts/compose/DmdBrandsTestChart.kt` and its 4 wiring edits — port to `sample:shared` or drop.
7. Re-point `origin` at `gg-engineering/vico`.
8. Write down the publish command (and ideally add a `workflow_dispatch` CI job for it), and record that credentials are `gpr.user`/`gpr.token` in `gradle-local.properties` in the vico repo but `gpr.user`/`gpr.key` in `local.properties` in meApp.
9. After the first publish, bump `vico` in meApp's `gradle/libs.versions.toml` and fix the two compile breaks the library API change forces (`animateIn` and the `getXStep` arity — see §1.1).
