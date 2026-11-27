# S6-A — The upstream guide, `ExtraStore`, and where our per-frame work should go

Research-only. No code written, no working tree modified. All library claims carry a `file:line`
at a named ref: `v3.3.1` = `3d675ccc7` (upstream target), `HEAD` = our fork
(`MOB-2953-week-range-spans-eight-days`, published 4.0.46).

Paths are abbreviated. Unless marked otherwise, read
`cartesian/...` as
`vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/cartesian/...`
and `common/...` as the sibling `.../vico/compose/common/...`.

**The guide read in full**: upstream ships its prose under `guide/` — 37 Markdown files at
`v3.3.1`, which is the GitBook source for <https://patrykandpatrick.com/vico/wiki/>. There is no
`docs/` tree at `v3.3.1`. Exactly three pages mention extras:
`guide/cartesian-charts/cartesianchartmodelproducer.md` (20 hits — the normative page),
`guide/cartesian-charts/cartesianlayer.md` (1 — the range-provider rule), and
`guide/cartesian-charts/cartesianvalueformatter.md` (6 — two worked examples).

---

## 0. Headline

**`ExtraStore` is not a cache. It is the only sanctioned channel for pushing non-series data
through the same transaction boundary as the series**, so that a value and the data it describes
can never be one frame out of step. Upstream's own uses are all of that shape, and the guide
forbids in as many words the thing our fork does instead:

> Never introduce a separate channel that makes setup changes as a transaction runs.
> — `guide/cartesian-charts/cartesianchartmodelproducer.md`, "Asynchrony"

Two consequences run through everything below:

1. **Writing an extra does not invalidate a frame.** `ExtraStore` appears in no `remember` key and
   in no `equals` that gates drawing. It gates a *push*. Treating extras as a per-frame memo table
   is a category error — that is what `CacheStore` is for, and it already exists.
2. **The model's extras are hashed once per frame, unconditionally** (`CartesianChart.kt:311` at
   `v3.3.1`; `:240` in our fork). So parking a large collection in model extras converts one O(n)
   per frame into a different O(n) per frame. This is the single most important constraint on the
   wins in §4, and it is not documented anywhere in the guide.

---

## 1. The lifecycle — per push vs per frame

### 1a. The per-push path (off the main thread)

`runTransaction` is `suspend` and hops to `Dispatchers.Default`
(`cartesian/data/CartesianChartModelProducer.kt:153-155`). The guide is explicit that this runs
off the main thread and that two models can be in flight at once.

| # | Step | Evidence (`v3.3.1`) |
|---|---|---|
| 1 | `Transaction()` allocates a **fresh** `MutableExtraStore` per transaction | `CartesianChartModelProducer.kt:158-160` |
| 2 | `lineModel { series(...) }` appends `Partial`s; `extras { }` writes into that fresh store | `:163-173` |
| 3 | `commit()` → `update(newPartials, newExtraStore)` | `:175-177` |
| 4 | **Push dedup.** If `partials == lastPartials` **and** `transactionExtraStore == lastTransactionExtraStore`, the update returns without doing anything | `:43-48` |
| 5 | **Model reuse.** If `partials.hashCode() == cachedModelPartialHashCode`, the cached model is reused via `cachedModel.copy(transactionExtraStore)` — series objects are *not* rebuilt, only the store is swapped | `:197-201` |
| 6 | Otherwise `getModel` → `partials.map { it.complete(extraStore) }` builds a new `CartesianChartModel` | `:58-67` |
| 7 | **Ranges.** `updateRanges(model)` → `chart.updateRanges(ranges, model)` → each layer's `updateChartRanges`, which calls `rangeProvider.getMinY(minY, maxY, model.extraStore)` etc., then `ranges.xStep = getXStep(...)` | `:202`; `CartesianChart.kt:406-409`; `layer/LineCartesianLayer.kt:907-928` |
| 8 | `prepareForTransformation(model, hostExtraStore, ranges)` — each layer hands its old drawing model (read from the **host** store) and its new one to the interpolator | `:203`; `CartesianChart.kt:441-452`; `LineCartesianLayer.kt:951-960` |
| 9 | `startAnimation { ... transform(key, fraction, model, ranges) }` | `:204` |

**Steps 4–9 are the whole of "per data push". Step 7 is the only place ranges are ever computed.**
The guide states this as a rule, not an implementation detail:

> they're updated via `CartesianChartModelProducer.Transaction`, and **a transaction is required
> for a chart's _x_- and _y_-ranges to be updated**.
> — `guide/cartesian-charts/cartesianlayer.md`, "`CartesianLayerRangeProvider`"

### 1b. The per-animation-frame path

`transform` runs per difference-animation frame while an animation is in flight
(`CartesianChartModelProducer.kt:69-82`):

```
transform(hostExtraStore, fraction)          // each layer writes its interpolated drawing model
onUpdate(model, ranges, hostExtraStore.copy())   // :79 — a full map copy, per animation frame
```

`onUpdate` lands in `CartesianChartDataState.set`, which writes a `mutableStateOf`
(`cartesian/data/CartesianChartData.kt:41-48`) — that is what triggers recomposition.
**Note `hostExtraStore.copy()` at `:79`: one `MutableExtraStore` allocation plus a map copy per
animation frame.** Cheap at three keys; linear in the number of keys you add.

### 1c. The per-draw-frame path

Everything from here lives inside the `Canvas` draw lambda,
`cartesian/CartesianChartHost.kt:334-372` (`v3.3.1`) / `:470-540` (fork). It re-runs on every
frame of a scroll, a zoom, a fling and a difference animation:

| Step | What | Evidence (`v3.3.1`) |
|---|---|---|
| 1 | `canvasWidth` / `canvasSize` written onto the mutable measuring context | `CartesianChartHost.kt:336-337` |
| 2 | `layerDimensions.clear()`; **`chart.prepare(...)`** | `:339-340` |
| 3 | …which calls **`measure(...)`**: clears `_markerTargets`, clears margins, computes `31 * persistentMarkers.hashCode() + model.extraStore.hashCode()`, runs every layer's `updateDimensions`, every axis's `updateLayerDimensions`, every margin updater, and the legend height | `CartesianChart.kt:304-330`, hash at **`:311`** |
| 4 | `zoomState.update(...)`, `scrollState.update(...)` | `CartesianChartHost.kt:344-350` |
| 5 | `CartesianDrawingContext(...)` allocated | `:359-368` |
| 6 | `chart.draw(context)` — decorations under, axes under, **all layers composited onto one offscreen bitmap**, then `_markerTargets.toList().sortedBy { it.first }` (a full sort, every frame), persistent markers, marker, fading edges, axes over, decorations over, legend, marker over | `CartesianChart.kt:377-404`, sort at **`:386`** |
| 7 | **`cacheStore.purge()`** — two-generation sweep: anything not read this frame is dropped | `CartesianChartHost.kt:371`; `common/data/CacheStore.kt:56-59` |

**Measuring is not a separate phase that runs less often than drawing.** `measure()` is invoked
from `prepare()` inside the draw lambda (step 2→3) and again from `getVerticalExtras` during
Compose's measure pass (`CartesianChart.kt:337-343`, host `:237-247`). There is no
"measure once, draw many".

### 1d. Where the documented extension points sit

| Extension point | Phase it runs in | Which store it is handed |
|---|---|---|
| `CartesianLayerRangeProvider.getMinY/getMaxY/getMinX/getMaxX` | **per push only** (step 1a.7) | `model.extraStore` |
| `getXStep(model, minX, maxX)` | **per push only** (`CartesianChart.kt:408`) | — (model) |
| `CartesianLayer.prepareForTransformation` | per push | `hostExtraStore` (mutable) |
| `CartesianLayer.transform` | per animation frame | `hostExtraStore` (mutable) |
| `layerPadding(ExtraStore)` | per recomposition, memoised on `remember(chart.layerPadding, model.extraStore)` | `model.extraStore` |
| `persistentMarkers(ExtraStore)` | per frame, gated on a hash of `persistentMarkers` + `model.extraStore` | `model.extraStore` |
| `Legend` items `(ExtraStore) -> Unit` | per frame, gated on `resolvedExtraStore.hashCode()` | `model.extraStore` |
| `LineProvider.getLine`, `PointProvider.getPoint`, `AreaFill.splitY`, `ColorScale` | **per frame**, per series | `model.extraStore` |
| `CartesianValueFormatter`, marker `ValueFormatter` | per frame, per label | `context.model.extraStore` |
| `Axis` `title(ExtraStore)`, `ItemPlacer` `spacing`/`offset`/`step`/`count` | per frame | `model.extraStore` |
| `Decoration.drawUnderLayers` / `drawOverLayers` | per frame | context (host store) |

The pattern is consistent: **model extras decide what the chart *is*; the host store carries only
the current frame of the difference animation.**

---

## 2. `ExtraStore` reference

`common/data/ExtraStore.kt` is **byte-identical between `v3.3.1` and our fork** — verified by
`git diff v3.3.1:... HEAD:...`, empty output. Every claim here already describes code shipping in
4.0.46.

### 2a. The types

```kotlin
public abstract class ExtraStore internal constructor() {        // :21
  protected abstract val mapDelegate: Map<Key<*>, Any>           // :23
  public open operator fun <T : Any> get(key: Key<T>): T = mapDelegate[key] as T   // :26
  public fun <T : Any> getOrNull(key: Key<T>): T? = mapDelegate[key] as? T         // :29
  internal abstract fun copy(): ExtraStore                       // :31
  override fun equals(other: Any?): Boolean =
    this === other || other is ExtraStore && mapDelegate == other.mapDelegate      // :37-38
  override fun hashCode(): Int = mapDelegate.hashCode()                            // :40
  @Suppress("UNUSED") public open class Key<T : Any>                               // :43
}
```

Four things follow directly from those lines:

- **`Key` has no members and no `equals`/`hashCode` override.** It is a pure identity token; its
  type parameter exists only to type the cast at `:26`/`:29`. The guide spells out the
  consequence: *"`ExtraStore.Key` instances are compared by identity… persisted at least as long
  as the corresponding `CartesianChartModelProducer` instance."* Declare keys at top level or in a
  companion object.
- **`get` is unsafe.** `mapDelegate[key] as T` on a missing key is an unchecked cast of `null`;
  for a non-null `T` this throws `NullPointerException` at the use site, not at `get`. Use
  `getOrNull` for anything written conditionally — the guide says the same.
- **`ExtraStore.equals` is a full map equality** (`:37-38`), and `hashCode` a full map hash
  (`:40`). Both are O(number of keys) *plus the cost of each value's own `equals`/`hashCode`*.
  This is the hinge of §2c and §2e.
- `MutableExtraStore.copy()` (`:74`) allocates and copies the map; `plus` (`:80-86`) builds a
  third map. Neither is deep.

### 2b. The two stores — the one paragraph

There are two `ExtraStore` instances alive per chart and they are not interchangeable. **The model
store** is created fresh inside every `Transaction` (`CartesianChartModelProducer.kt:160`),
written only through `extras { }`, and becomes `CartesianChartModel.extraStore`; it is immutable
once published, it is carried onto each layer model by `copy(extraStore)`
(`cartesian/data/CartesianChartModel.kt:83-84`), it is part of `CartesianChartModel.equals` and
`hashCode` (`:86-90`), and it survives recomposition for as long as its model does — it is
replaced only by the next push, and it is **owned by the consumer**. **The host store** is a
single `MutableExtraStore` created once per chart identity, `remember(chart.id)
{ MutableExtraStore() }` (`CartesianChartModel.kt:119`), handed to the producer as
`hostExtraStore`; it is written only by the library, only from `CartesianLayer.transform` /
`prepareForTransformation`, it holds exactly the three `drawingModelKey` entries, it is **copied
in full on every animation frame** (`CartesianChartModelProducer.kt:79`) and that copy is what
`MeasuringContext.extraStore` exposes to the draw pass — *"Houses auxiliary **drawing** data"*
(`common/MeasuringContext.kt:53-54`). So: the model store survives a recomposition and dies on a
push; the host store survives a push and is rewritten per animation frame; neither is rebuilt per
*draw* frame, and the consumer can write only the first of them. A third, genuinely per-frame
store exists and is often what people actually want — `CacheStore`
(`common/data/CacheStore.kt`), keyed by `KeyNamespace` plus arbitrary components, swept every
frame by `purge()` (`:56-59`, called at `CartesianChartHost.kt:371`).

### 2c. Caching and invalidation — what an extras write actually invalidates

| Question | Answer | Evidence |
|---|---|---|
| Does writing a model extra invalidate the **model**? | **Yes, and it is the dedup key.** A push whose series are unchanged but whose extras differ is *not* deduped and produces a new model; a push where both are unchanged is dropped entirely | `CartesianChartModelProducer.kt:43-48` |
| …does it rebuild the series? | **No.** `partials.hashCode()` still matches, so `cachedModel.copy(transactionExtraStore)` reuses the series instances | `:197-198`; `CartesianChartModel.kt:83-84`; `data/LineCartesianLayerModel.kt:80-81` (fork) passes the same `series` reference through |
| Does it invalidate the **ranges**? | **Yes** — `updateRanges` runs on every non-deduped push, and range providers read `model.extraStore` | `:202`; `LineCartesianLayer.kt:922-925` |
| Does it invalidate the **measure** pass? | Only in the sense that measure re-runs every frame anyway. It *does* re-fire `updatePersistentMarkers` and the legend-item rebuild, both gated on `extraStore.hashCode()` | `CartesianChart.kt:311-315`; `common/LegendItemManager.kt:39-43` |
| Does it invalidate the **draw** pass? | Indirectly — the new model reaches `CartesianChartDataState` via `mutableStateOf`, recomposing the host | `CartesianChartData.kt:41-48` |
| Is it in `CartesianChart.equals`? | **No.** The terms are `id, marker, markerVisibilityListener, layerPadding, legend, fadingEdges, decorations, persistentMarkers, getXStep, layers, startAxis, topAxis, endAxis, bottomAxis, markerController` | `CartesianChart.kt:619-636` |
| Is it in `rememberCartesianChart`'s key list? | **No.** Keys are `*layers, startAxis, topAxis, endAxis, bottomAxis, marker, markerVisibilityListener, layerPadding, legend, fadingEdges, decorations, persistentMarkers, getXStep, markerController` | `CartesianChart.kt:711-725` |
| Is it in any other `remember` key? | **Yes, one**: `remember(chart.layerPadding, model.extraStore) { chart.layerPadding(model.extraStore) }` | `CartesianChartHost.kt:228` |

**The operative sentence**: an extras write is a *push-level* event. It cannot be used to
invalidate something per frame, and it cannot be used to make the chart redraw without a
transaction.

Note for S5-A F3: `decorations` and `layerPadding` are each *both* a `rememberCartesianChart` key
and a `CartesianChart.equals` term (`:628`, `:625`, `:722`, `:719`). `ExtraStore` is neither. The
instability risk in F3 is therefore entirely about those lambda/list arguments and is *not*
mitigated by moving their inputs into extras — moving a *value* into extras is safe, but the
lambda that reads it still has to be stable.

### 2d. Every `ExtraStore.Key` upstream declares, and why

`git grep -n "ExtraStore.Key" v3.3.1 -- vico/` returns six lines: three property declarations and
three constructions. **Upstream declares exactly three keys, and all three are the same key for
three layer types.**

| Key | Declared | Constructed | Purpose |
|---|---|---|---|
| `LineCartesianLayer.drawingModelKey: ExtraStore.Key<LineCartesianLayerDrawingModel>` | `layer/LineCartesianLayer.kt:75` | `:528` | Holds the current frame of the line's difference animation |
| `ColumnCartesianLayer.drawingModelKey` | `layer/ColumnCartesianLayer.kt:84` | `:121` | ditto, columns |
| `CandlestickCartesianLayer.drawingModelKey` | `layer/CandlestickCartesianLayer.kt:67` | `:114` | ditto, candlesticks |

Read `LineCartesianLayer` as the worked example of the intended shape:

- written per animation frame into the **host** store —
  `drawingModelInterpolator.transform(fraction)?.let { extraStore[drawingModelKey] = it } ?: extraStore.remove(drawingModelKey)` (`:962-965`). Note the `remove`: the key is cleaned up when
  there is nothing to interpolate.
- read per draw frame off the context's host store —
  `val drawingModel = extraStore.getOrNull(drawingModelKey)` (`:535`), then `drawingModel.opacity`
  (`:559`), `.sweepFraction` (`:536`), `.getOrNull(seriesIndex)` (`:563`).
- read on the next push to seed the interpolator's `old` (`:957`).

Both halves of a difference animation are therefore carried on the same channel as the data — the
same invariant the guide gives for consumer extras, applied to the library's own state.

**Upstream's documented *consumer* examples all put collections in the model store instead**, and
`guide/cartesian-charts/cartesianvalueformatter.md` gives two verbatim:

```kt
val labelListKey = ExtraStore.Key<List<String>>()
cartesianChartModelProducer.runTransaction {
    columnModel { series(data.values) }
    extras { it[labelListKey] = data.keys.toList() }
}
CartesianValueFormatter { context, x, _ -> context.model.extraStore[labelListKey][x.toInt()] }
```

and an `ExtraStore.Key<Map<Float, LocalDate>>` carrying an x→date map. Both are read **per frame,
per label**, and both are hashed per frame by `CartesianChart.kt:311` (§2e).

### 2e. Cost — memory, copying, key lifetime

Four costs, all evidenced, none documented in the guide:

1. **The model store is hashed once per draw frame, unconditionally.**
   `CartesianChart.kt:311` (`v3.3.1`) / `:240` (fork):
   `val persistentMarkerHashCode = 31 * persistentMarkers.hashCode() + model.extraStore.hashCode()`
   — inside `measure()`, which is inside `prepare()`, which is inside the `Canvas` lambda. It runs
   **even when `persistentMarkers` is null**. `ExtraStore.hashCode()` is `mapDelegate.hashCode()`,
   which hashes every value. A `List<String>` of n labels or a `Map<Float, LocalDate>` of n
   entries costs **O(n) per frame**. `LegendItemManager.kt:39` adds a second full hash per frame
   when a legend is present.
   > **Therefore: park identity or a small summary in model extras, never bulk data.** A value
   > whose class does not override `hashCode` (identity hash) or a `data class` of a few
   > primitives costs O(1). A collection does not. This single line decides the shape of every win
   > in §4.
2. **The host store is copied per animation frame.** `hostExtraStore.copy()`
   (`CartesianChartModelProducer.kt:79`), again at `:126` in `getCachedData`. One allocation + map
   copy, O(keys). Three keys today.
3. **Key lifetime.** `drawingModelKey` is constructed in the layer's *public constructor*
   (`LineCartesianLayer.kt:528`), so a fresh layer instance means a fresh key and an orphaned
   entry in the host store. Upstream's defence is the wrapper-plus-`copy` idiom in
   `rememberLineCartesianLayer` (`:1081-1106`) — `copy` passes `drawingModelKey` through
   (`:1003`), so parameter changes reuse the key. `rememberCartesianChart` does the same for
   `chart.id` (`:710`, `:728`, `:574`), which is what keeps the host store itself alive across
   recomposition. **Break that idiom and the host store grows without bound.**
4. **Memory.** The model store holds one strong reference per key for the life of the model, and a
   model is held by `CartesianChartDataState` plus `previousModel` — so a bulk collection in
   extras is retained **twice** (`CartesianChartData.kt:24-25, 39-47`).

---

## 3. Our usage audit

### 3a. The fork — `git grep -n "ExtraStore\|extraStore" HEAD -- vico/`

697 hits, of which all but the ones below are upstream's own code, unchanged or reformatted. The
fork adds exactly **one** key.

| # | Use | Location | Verdict |
|---|---|---|---|
| F1 | `WindowKey: ExtraStore.Key<Map<String, Window>>`, in a companion object | `cartesian/data/ScrollAwareRangeProvider.kt:89` | **Correct — textbook** |
| F2 | Read through `windowFromExtras(extraStore)` from `getMinX/getMaxX/getMinY/getMaxY` | `:92-93`, `:191-208` | **Correct** |
| F3 | Read through `openingWindow` / `seedFromWindow` from the host's per-push `remember(chart, model)` | `:96`, `:162-179`; `CartesianChartHost.kt:126-130` | **Correct** |
| F4 | `xRangeMin/xRangeMax/seedMinY/seedMaxY` — public mutable fields assigned on **every recomposition**, outside any `remember` | `:120-128`; `rememberScrollAwareRangeProvider` `:358-361` | **Misuse** — the forbidden second channel |
| F5 | `currentMinY/currentMaxY/currentTicks/settleEpoch/isCacheReady/cachedSeries/cachedWindow` — mutable provider state driven by a scroll flow | `:109-146`; `CartesianChartHost.kt:596-668` | **Misuse** — same channel, plus `CartesianLayerRangeProvider` is declared `@Immutable` (`v3.3.1:cartesian/data/CartesianLayerRangeProvider.kt:25`) |
| F6 | `scrollUpdates: MutableSharedFlow<ScrollInfo>` on the provider, fed from inside the `Canvas` lambda | `:182`; `CartesianChartHost.kt:491-512` | **Misuse of the provider, but a reasonable mechanism** — see W4 |

**F1–F3 are the single best piece of extras design in the fork**, and the comment at
`ScrollAwareRangeProvider.kt:79-89` independently re-derives the guide's own rationale —
*"sending them separately … lets a model arrive before its own window … Travelling in the
transaction makes the two inseparable."* That is the guide's "Asynchrony" section in our own
words. Keep it; port it unchanged.

**F4–F6 are the part the migration should attack.** Nothing about them is accidental — they exist
because `updateRanges` is per-push only and the fork needs a per-scroll range. But they are the
reason the fork has to patch `CartesianChartHost` at all (`:92-136` re-runs
`chart.updateRanges(hostRanges, model)` on `remember(chart, model)`, outside the producer), which
is SYNTHESIS §8.4's "~6 lines of theirs would replace ~250 of ours".

Also noted, not extras but adjacent:

| # | Finding | Location |
|---|---|---|
| F7 | `allMarkerTargetXValues` cached on `_markerTargets.size` alone | `cartesian/CartesianChart.kt:161-172` |
| F8 | `series.hashCode()` per series per draw frame | `cartesian/layer/LineCartesianLayer.kt:839` |
| F9 | `getVisibleXRange` opened from `internal` to `public` on `CartesianDrawingContext` (upstream keeps it `internal` at `v3.3.1`) | `cartesian/CartesianDrawingContext.kt:55` vs `v3.3.1:...:55` |
| F10 | `getVisibleAxisLabels` / `getInterpolatedYValues` added as public API on `VicoScrollState` | `cartesian/VicoScrollState.kt:118-175` |

### 3b. meApp — `grep -rn "ExtraStore\|extraStore" app/src`

All Vico usage is in the `app` module; the other modules contain no reference to `vico` at all.

**meApp writes exactly one extra, never reads one, and never declares an `ExtraStore.Key`.**

```kotlin
// app/src/main/java/.../dashboard/viewmodel/base/BaseDashboardViewModel.kt:40-52
typealias ChartWindows = Map<String, ScrollAwareRangeProvider.Window>

fun CartesianChartModelProducer.Transaction.putWindows(windows: ChartWindows) {
  if (windows.isEmpty()) return
  extras { it[ScrollAwareRangeProvider.WindowKey] = windows }
}
```

| # | Use | Location | Verdict |
|---|---|---|---|
| M1 | `putWindows` — the one extras write, inside `runTransaction`, keyed by `GraphSegment.name` | `BaseDashboardViewModel.kt:40-52`, called at `:248`, `WeightDashboardViewModel.kt:262`, `BabyDashboardViewModel.kt:233` | **Correct.** On the push path, key is a library-owned companion `val` (`ScrollAwareRangeProvider.kt:89` — verified a `val`, not a `get()`), value is a `data class` of six `Double`s so the per-frame hash at `CartesianChart.kt:240` stays O(segments) |
| M2 | `ExtraStore` appears only as a parameter type on four delegating overrides | `ProductChart.kt:284-291` | **Correct** (pure delegation) |
| M3 | Snapshot card pushes **no** extras; it threads its window through composable params into `CartesianLayerRangeProvider.fixed(...)` | `DashboardSnapshotViewModel.kt:345, 414, 479`; `SnapshotLineChart.kt:102-109` | **Acceptable** — `fixed` is static per composition, which is exactly what the guide permits (*"`fixed` should be used only for entirely static overrides"*). Not a bug; just note the asymmetry |
| M4 | `HorizontalItemPlacer.getLabelValues`/`getLineValues` enumerate the **full x-range** (whole account history) per call, on the draw path | `HorizontalItemPlacer.kt:42-96` (verified: both overrides have identical bodies) | **Missed opportunity — the biggest one.** See W1 |
| M5 | `MetricBasis.version` — an `Int` bumped only when the recorded **window** changes | `ProductChart.kt:206-221` (verified) | **Correct for what it does**, and already consumed as `settleEpoch` (`ProductChart.kt:283` → `LineCartesianLayer.kt:856`). **Not** a series identity — see W2 |
| M6 | Unremembered lambda/formatter arguments handed to `rememberCartesianChart` | `axis/Horizontalxis.kt:55, :98`; `GraphMarker.kt:153, :157`; `SnapshotLineChart.kt:149` | **Misuse — live defect risk.** See W3 |
| M7 | `getVisibleAxisLabels`/`getInterpolatedYValues` callers are debounced (`delay(50)`, `.debounce(100)`), **not** per frame | `GraphView.kt:329-358, 392-423` | **Correct as built** — the S6 lead over-stated this. See W6 |

---

## 4. Ranked wins

Ranked by (value × confidence) ÷ risk. **Every saving below is unmeasured** — meApp has no chart
benchmark and no visual regression suite (SYNTHESIS §6). Where I give a magnitude it is an
operation count read off the source, not a timing.

### W1 — Hoist meApp's axis label set from the draw path into model extras · **model extras**

**Now.** `HorizontalItemPlacer.getLabelValues` and `getLineValues` (`HorizontalItemPlacer.kt:42-96`)
each enumerate every month (`GraphSegment.YEAR` → `monthStartTimestampsMillis()`) or every week
(`GraphSegment.MONTH` → `weekStartsBetween(...)`) across **`fullXRange` — the entire account
history** — constructing a `ZonedDateTime` per step, then allocate two further lists narrowing to
the viewport. The two overrides are byte-identical, so the work is done twice. The fork calls both
from `drawUnderLayers` (`axis/HorizontalAxis.kt:205-206`) and again from `drawLineAndTicks`
(`:338-339`) — **up to four full enumerations per frame, per horizontal axis.** Cost scales with
how long the user has owned the account, not with what is on screen.

**Would become.** The VM computes the segment's label values once, in the transaction that already
calls `putWindows` — the values are a pure function of the window it is already sending. A second
key beside `WindowKey`, same shape:
`ExtraStore.Key<Map<String, LabelValues>>`. The placer becomes a map lookup plus the existing
`filterToVisibleWithPadding`.

**The trap, and the reason this is not a straight copy of the guide's example.**
`CartesianChart.kt:240` computes `model.extraStore.hashCode()` **every frame, unconditionally,
even with `persistentMarkers == null`**. `ExtraStore.hashCode()` hashes every value
(`ExtraStore.kt:40`). Storing a bare `List<Double>` of 156 week-starts would hash all 156 every
frame — trading one O(n) for another. **Store a wrapper class that does not override `hashCode`**
(identity hash → O(1)), or store a small descriptor and hang the list off `CacheStore` keyed on
it. Upstream's own documented examples (`cartesianvalueformatter.md`: `ExtraStore.Key<List<String>>`,
`ExtraStore.Key<Map<Float, LocalDate>>`) walk straight into this and the guide never mentions it.

**Risk.** Low-to-medium. The label set must change in lockstep with the window — which is exactly
`WindowKey`'s existing argument, so the synchronisation story is already proven in this codebase.
What could break: a segment whose window is pushed but whose labels are not (guard with
`getOrNull` and fall through to today's computation); and the hashing trap above, which is silent
if got wrong.

**Confidence.** High — `HorizontalItemPlacer.kt:42-96` and `HorizontalAxis.kt:205-206, 338-339`
both read directly.

### W2 — Replace `series.hashCode()` with reference identity · **neither store**

**Now.** `cartesian/layer/LineCartesianLayer.kt:839`:

```kotlin
val seriesKey = series.hashCode().toLong()
val seriesChanged = transformLastSeriesKey != 0L && transformLastSeriesKey != seriesKey
```

`List.hashCode()` is O(n) and invokes each `Entry.hashCode()`. This sits in
`collectPointsAndVisibleIndexRange` (`:820-826`), called from `drawInternal`'s
`model.series.forEachIndexed` loop at `:634` — so **once per series, per draw frame, over the
whole series**. On the TOTAL segment that is every reading the user has ever recorded, at frame
rate.

**Would become.** `transformLastSeries !== series` — one reference compare.

**Why that is correct, both directions.** No false positive on an extras-only push:
`LineCartesianLayerModel.copy(extraStore)` passes the *same* `series` reference through
(`data/LineCartesianLayerModel.kt:80-81`), and the extras-only path is exactly the one that
reaches it (`CartesianChartModelProducer.kt:197-198`). No false negative on a real data push:
`Partial.complete` builds new lists (`data/LineCartesianLayerModel.kt:123-125`). **The fork
already does precisely this, for precisely this question, forty lines away**:
`CartesianChartHost.kt:114`, `val seriesChanged = provider.cachedSeries !== series`, with a
comment at `:112-113` spelling out the extras-copy case.

**Correction to the S6 brief.** The brief suggests `MetricBasis.version` as the O(1) replacement.
**That is wrong, and adopting it would introduce a bug.** Verified at `ProductChart.kt:206-221`:
`record(window)` increments `version` only when `(minX, maxX)` changes — it is a *window* version,
not a series identity, and it does not move when the user switches metric. Worse, it is **already
consumed** as the fork's window key: `ProductChart.kt:283` exposes it as `settleEpoch`, which
`LineCartesianLayer.kt:856` reads as `windowKey`. Using it for `seriesKey` as well would collapse
two deliberately independent detectors into one and defeat the "One path for both" logic the fork
documents at `:874-876`.

**Risk.** Low. Confined to the `yTransform` block; the sentinel `transformLastSeriesKey != 0L`
becomes a nullable reference. A mutated-in-place series list would defeat it —
`LineCartesianLayerModel` never does that.

**Saving.** Unmeasured. O(n)→O(1) per series per frame, n = full series length.

### W3 — Stabilise meApp's `rememberCartesianChart` arguments · **neither store**

Not an extras win, and probably the highest value-per-hour item here.

**Now.** Several arguments are allocated fresh on every recomposition: `CartesianValueFormatter`s
at `axis/Horizontalxis.kt:55` and `:98`; `yLabelCallback()` and `rangeLabelFormatter(...)` at
`GraphMarker.kt:153` and `:157`; and `getXStep = { ... }` at `SnapshotLineChart.kt:149`.

The vararg arrays (`arrayOf(...)`, `listOfNotNull(...).toTypedArray()`) are **not** a problem —
`rememberCartesianChart` spreads `*layers` into `remember(...)` (fork `CartesianChart.kt:746-747`;
`v3.3.1:711-712`), so the elements are compared, not the array. Two wasted allocations per
recomposition, nothing more. Dropped from this item.

**Why it matters more in our fork than upstream.** Each of these is both a
`rememberCartesianChart` key (`CartesianChart.kt:711-725`) and a `CartesianChart.equals` term
(`:619-636`). Upstream absorbs some of this: the wrapper-`copy` idiom keeps `chart.id` stable
(`:728`, `:574`), so `remember(chart.id) { MutableExtraStore() }` survives. **Our fork does not
absorb it**: `CartesianChartHost.kt:94` seeds ranges on `remember(chart, model)` — keyed on the
chart *instance*, not its id — so a new `CartesianChart` re-runs `buildCache` / `seedFromWindow` /
`chart.updateRanges`. That is the S5-A F3 / MOB-3494 defect shape, already live today.

**Would become.** `remember` each formatter on its real inputs; hoist the vararg arrays.
Separately, consider keying the fork's host seeding on `chart.id` rather than `chart`.

**Risk.** Low, and it is the kind of change a golden suite catches. **But it must land after the
T0 goldens** (PLAN P1), because it changes when ranges re-seed.

### W4 — Delete `ScrollAwareRangeProvider`'s four redundant window fields · **model extras**

**Verdict on the S6 lead.** `WindowKey` is **not** a misuse — it is the best extras design in the
fork and the comment at `ScrollAwareRangeProvider.kt:79-89` independently re-derives the guide's
own rationale. Port it unchanged. The misuse is everything around it.

**The per-frame window broadcast is already solved and offers nothing to reclaim.** The emit is
guarded on `(scroll, xSpacing, chartWidth)` changing (`CartesianChartHost.kt:496-499`) and the
collector is `.debounce(provider.debounceMs)` (`:634`). Reshaping it around extras would not make
it faster, and *could not*: extras cannot be written without a transaction.

**What extras can retire.** `xRangeMin / xRangeMax / seedMinY / seedMaxY`
(`ScrollAwareRangeProvider.kt:120-128`) are assigned on **every recomposition**, outside any
`remember`, at `rememberScrollAwareRangeProvider:358-361`. `Window` already carries all four
(`:56-75`), and the read sites already prefer the extras value for x (`:191-195`). Per the meApp
audit, all three dashboard VMs call `putWindows`. **So the four fields are dead weight on the
dashboard path and can go**, removing four of the six side-channel writes at no behavioural cost
to any caller that pushes a window.

**What cannot move, and this is the real finding.** `currentMinY / currentMaxY / currentTicks /
settleEpoch` are per-scroll. The guide's model has no way to express them — see C1. Our flagship
feature sits outside what `ExtraStore` can do, which is *why* it needs a host patch, and is the
strongest argument for the `ranges` seam PR in SYNTHESIS §8.4.

**Risk.** Medium. The snapshot card does not push windows, but it uses
`CartesianLayerRangeProvider.fixed(...)`, not `ScrollAwareRangeProvider`
(`SnapshotLineChart.kt:102-109`), so it is unaffected. **UNRESOLVED:** whether any caller outside
`app/src/main` (screenshot tests, previews) constructs a `ScrollAwareRangeProvider` and relies on
the `minX`/`maxX`/`seedMinY`/`seedMaxY` parameters of `rememberScrollAwareRangeProvider` without
calling `putWindows`. *Exact check:* `grep -rn "rememberScrollAwareRangeProvider\|ScrollAwareRangeProvider("
/Users/selvakumar/Projects/meApp-bug/Android/app/src` and confirm every hit's producer also calls
`putWindows`.

### W5 — `allMarkerTargetXValues`: a correctness fix, not a performance win · **neither store**

**Now.** `cartesian/CartesianChart.kt:161-172` caches the x list on `_markerTargets.size` alone.
`_markerTargets` is cleared and refilled every `measure()` (`:239`-ish) and `draw()`, and during a
scroll it holds only the targets for *visible* points — so the key *set* slides while the *count*
very often does not. The cached list is therefore stale for most of a scroll. Confirmed as
described in the S6 brief.

**But it is not per-frame work.** The only reader is the gesture path —
`CartesianChartHost.kt:383`, inside `onInteraction`, behind a `ScrubMarkerController` callback. So
there is no frame cost to reclaim; the bug is that the scrub callback receives the **wrong x
set**, which on the baby chart (layers contributing different x sets) is the same family as
SYNTHESIS defect #2.

**Would become.** Drop the cache (`_markerTargets.keys.toList()` over the visible targets is small
and the call is per gesture event), or bump an epoch wherever `_markerTargets` is refilled and key
on that.

**Risk.** Low. Moves one small allocation onto a path that already allocates.

### W6 — Downgrade: the scroll-state helpers are mostly fine · **neither store**

The brief lists `getVisibleAxisLabels` / `getInterpolatedYValues` as per-draw work. **They are
not.** On the scroll path their callers are debounced — `GraphView.kt:329-358` uses `delay(50)`
and `snapshotFlow { scrollState.value }.debounce(100)` — so ≤ ~10 Hz. And
`getInterpolatedYValues` is already O(|xValues| · log n) with zero allocation per point
(`cartesian/layer/MonotoneInterpolator.kt:105-117`, binary search + three secants).

They *are* called per scrub-move event at `GraphView.kt:497`, which is touch-event rate. The
expensive work on that path is not vico's:

- `GraphUtil.markerValueFormatter` (`GraphUtil.kt:198-215`) parses
  `DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)` **on every call**, then `.lowercase()`
  allocates again. Three hoisted formatters already sit at `GraphUtil.kt:59-61` — this one just
  missed the hoist. Smallest fix on this page.
- `BabyPercentileHelper.interpolateMeasurementRow` (`:365-387`) does `data.filter { it.day in ... }`
  — a full linear scan of the measurement table allocating a list — per frame while scrubbing, to
  find at most two rows. Binary search on the already-sorted day column.
- `PercentileSeries.spanCovering` (`BabyPercentileHelper.kt:110-125`) allocates a fresh 7-element
  `allBands()` and scans 7 × span per call, from inside the range-provider callback
  (`ProductChart.kt:180`). Its own KDoc says it runs on every scroll settle.

None of these needs extras. Listed because the brief asked for "anything else recomputed per draw
that depends only on the model", and this is what that search actually turned up.

### Not recommended

- **Parking the series, the visible entries, or the tick list in model extras.** All three are
  collections; `CartesianChart.kt:240` would hash them every frame. See the trap in W1.
- **Using extras to invalidate anything per frame.** It cannot — §2c. Use `CacheStore`, which is
  already threaded through `MeasuringContext` and purged for you.

---

## 5. Where the guide contradicts how we built the fork

### C1 — "A transaction is required for a chart's _x_- and _y_-ranges to be updated"

> *(`guide/cartesian-charts/cartesianlayer.md`, "`CartesianLayerRangeProvider`")*

Verified in code: `updateRanges` is called from exactly two places, both per-push —
`CartesianChartModelProducer.kt:202` and the manual-model `remember(chart, model)` at
`CartesianChartHost.kt:189-192` (`v3.3.1`). **Our flagship feature is a y-range that updates on
scroll, with no transaction.** The fork implements it by re-running `chart.updateRanges(hostRanges,
model)` from its own `remember(chart, model)` (`CartesianChartHost.kt:92-136`) and by mutating the
provider from a debounced scroll flow (`:632-668`).

This is not a defect to fix — it is the fork's reason to exist. But it is the single sentence that
explains why `CartesianChartHost.kt` and `LineCartesianLayer.kt` are our two largest patches, and
it is the strongest available argument for SYNTHESIS §8.4's `ranges` seam proposal: upstream has
closed this door deliberately, in prose, so a PR must argue the use case, not just the API.

### C2 — "Never introduce a separate channel that makes setup changes as a transaction runs"

> *(`guide/cartesian-charts/cartesianchartmodelproducer.md`, "Asynchrony")*

`rememberScrollAwareRangeProvider:358-361` assigns four provider fields on **every
recomposition**, and `ScrollAwareRangeEffect` writes five more from a scroll flow. `runTransaction`
runs on `Dispatchers.Default` (`CartesianChartModelProducer.kt:154`), so a recomposition can land
between the producer computing a model and the host reading ranges off it.

**UNRESOLVED** whether this has ever actually misfired. *Exact check:* in
`CartesianChartHost.kt:92-136`, log `(model.extraStore.hashCode(), provider.seedMinY,
provider.cachedWindow)` on entry, push a transaction while forcing a recomposition that changes
`seedMinY`, and assert the seed used matches the window in the model being seeded. Worth noting
that the window-in-extras path (F1–F3) exists *because* the equivalent problem did misfire — the
comment at `ScrollAwareRangeProvider.kt:79-89` is a first-hand account of it.

### C3 — The guide's own examples walk into a per-frame O(n)

`guide/cartesian-charts/cartesianvalueformatter.md` documents `ExtraStore.Key<List<String>>` and
`ExtraStore.Key<Map<Float, LocalDate>>` as the canonical patterns. `CartesianChart.kt:311`
(`v3.3.1`) hashes `model.extraStore` **every frame, unconditionally**. Nothing in the guide
mentions it. **This is the one place where following the documentation literally is the wrong
move**, and it constrains W1's design. Flagged here rather than in §4 because it is a property of
upstream, not of us — and it is a decent candidate for a docs PR alongside the four in
SYNTHESIS §8.

### C4 — `CartesianLayerRangeProvider` is `@Immutable`; ours has nine mutable fields

`v3.3.1:cartesian/data/CartesianLayerRangeProvider.kt:25` declares the interface `@Immutable`.
`ScrollAwareRangeProvider` carries nine mutable `var`s (`:109-146`). `@Immutable` is a promise to
the Compose compiler that a read returns the same value forever; breaking it licenses skipping
that would otherwise not happen.

**UNRESOLVED** whether the compiler exploits it at our call sites. *Exact check:* build
`vico/compose` with
`-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=<dir>`, and check whether
`rememberLineCartesianLayer`'s `rangeProvider` parameter is reported stable; then diff against a
build with the annotation stripped. Related to, but distinct from, S5-A F3.

### C5 — `getXStep` changed arity; both meApp call sites bind the deprecated overload

Our fork: `getXStep: ((CartesianChartModel) -> Double) = { it.getXDeltaGcd() }`
(`CartesianChart.kt:68`), used as `ranges.xStep = getXStep(model)` (`:418`).
`v3.3.1`: `((CartesianChartModel, Double, Double) -> Double) = { model, minX, _ ->
getDefaultXStep(model, minX) }` (`:705-707`, `:408`), with the one-arg form kept but
`@Deprecated` (`:247-251`, `:581-585`).

Both meApp sites pass one-arg lambdas — `ProductChart.kt:99`
(`{ _: CartesianChartModel -> ... }`, explicitly typed) and `SnapshotLineChart.kt:149`
(`{ GraphUtil.calculateXStep(GraphSegment.WEEK) }`, zero declared params). Both will resolve to
the deprecated overload and compile with a warning; neither is affected by the default change,
since both override it. Small, concrete, and worth a disposition row at the cutover.

Note the guide is *also* out of date here: `guide/cartesian-charts/cartesianchart.md` documents
`getXStep` as `(CartesianChartModel) -> Double` and shows `rememberCartesianChart(getXStep = { 1.0 })`.

### C6 — Where the guide and the fork agree, and we should not "fix" it

Three fork choices look irregular but are correct under the guide, and should survive the
migration untouched:

- **`WindowKey` as a `companion object val`** (`ScrollAwareRangeProvider.kt:89`) — meets the
  identity-and-lifetime rule exactly. meApp only ever references it statically, never constructs
  one, so the consumer side is safe by construction.
- **Keying the window map by consumer name** (`Map<String, Window>`) — the guide has nothing to
  say about one producer feeding several charts; our comment at `:85-88` reasons it out correctly,
  and the value stays small enough for the per-frame hash.
- **The snapshot card using `CartesianLayerRangeProvider.fixed(...)`** with no extras
  (`SnapshotLineChart.kt:102-109`) — the guide permits `fixed` precisely for *"entirely static
  overrides"*, which is what the snapshot card is. The asymmetry with the dashboard charts is
  intended, not drift.

---

## 6. Open items

| # | Question | Exact check that settles it |
|---|---|---|
| U1 | Does C2's race ever actually misfire? | The logging recipe in C2 |
| U2 | Does the Compose compiler exploit `@Immutable` on `CartesianLayerRangeProvider`? | The stability-report diff in C4 |
| U3 | Does any caller rely on `rememberScrollAwareRangeProvider`'s `minX`/`maxX`/`seedMinY`/`seedMaxY` without pushing a window? | `grep -rn "rememberScrollAwareRangeProvider\|ScrollAwareRangeProvider(" app/src` across all source sets, then confirm each hit's producer calls `putWindows` |
| U4 | ~~Does the fork's `rememberCartesianChart` key on the vararg *array identity*?~~ | **RESOLVED — no.** Both the fork (`CartesianChart.kt:746-747`) and `v3.3.1` (`:711-712`) spread `*layers` into `remember(...)`, so the spread *elements* are compared, not the array. A fresh `arrayOf(...)` / `listOfNotNull(...).toTypedArray()` per recomposition is harmless. W3's array half is dropped |
| U5 | How much does W1 actually save? | Profile `HorizontalAxis.drawUnderLayers` on the MONTH segment with a multi-year account, before and after. Needs a running 4.0.46 build — schedule beside the T0 goldens (SYNTHESIS §7) |

