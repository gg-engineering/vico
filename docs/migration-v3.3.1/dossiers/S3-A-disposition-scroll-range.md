# S3-A — Disposition: scroll, scroll state, and dynamic Y/X range

Stage 3 decision document for the S1-A cluster (13 adaptations) plus the host-side range machinery
catalogued as **H-1 / H-2 / H-3** in `S1-E-host-build.md`.

Refs: `FORK_POINT = e67e49520` · `OURS = HEAD` (`MOB-2953-week-range-spans-eight-days`, 4.0.46) ·
`TARGET = v3.3.1 = 3d675ccc7`.
Path prefix abbreviated throughout as
`$C = vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose`.
Everything was read with `git show <ref>:<path>` / `git diff <refA> <refB>`. The checkout was not
modified.

Verdict vocabulary is the brief's: **DROP** (upstream does it, delete ours) · **DELETE** (wrong or
pointless, delete without replacement) · **REPLACE** (re-express through an upstream extension
point) · **MOVE** (relocate to meApp as consumer code) · **RE-APPLY** (port substantially as-is) ·
**RE-DERIVE** (intent survives, code must be rebuilt against v3.3.1's structure).

Three S1-A entries are split, because one id covers two changes with different fates
(S1A-05 / S1A-08 / S1A-09). The split is called out in each row.

---

## 1. Verdict table

| id | adaptation | verdict | risk if wrong | loud? | blocked by | blocks |
|---|---|---|---|---|---|---|
| S1A-01 | `ScrollAwareRangeProvider` (core) | **RE-APPLY** | the fork's flagship feature disappears: the Y axis stops following the visible window | **silent** | — | H-1, H-2, H-3 |
| S1A-02 | `Window` / `WindowKey` in `ExtraStore` | **RE-APPLY** | chart draws a frame against the previous window on every data push | **silent** | S1A-01 | H-1 |
| S1A-03 | `seedFromWindow` + cache/window guards | **RE-APPLY** | first frame opens on the whole dataset, then re-letters the axis (MOB-3494 regression) | **silent** | S1A-01, H-1 | — |
| S1A-04 | `WindowSettleSignal` / `settleEpoch` | **RE-APPLY** | a transformed line stays scaled for the window the user left — a visible kink | **silent** | — | S1-D (`yTransform`) |
| S1A-05a | `SnapBehaviorConfig` + `ChartSnapFlingBehavior` + `rememberChartSnapFlingBehavior` | **MOVE** to meApp (file renamed `ChartSnapFlingBehavior.kt`) | charts stop paging to calendar boundaries | **loud** (compile) | S1A-06 (public conversions), H-8/M-3 (`flingBehavior` seam) | — |
| S1A-05b | `ChartSnapFlingBehavior.isSnapping` | **DELETE** | none — it is written and never read | n/a | — | — |
| S1A-06 | scroll ⇄ X helpers + `visibleXRange` | **RE-APPLY**, widening three members `internal` → `public` | chart header loses the window name and estimated values; snap cannot be moved out | **loud** (compile) | — | S1A-05a, S1A-09a, H-3 |
| S1A-07 | scrub freeze (`isScrollFrozen`, `isScrolling`, `isUserScrolling`) | **RE-APPLY** | scrubbing a chart inside a `LazyColumn` scrolls the screen instead | **silent** | M-1/M-2 (gesture cluster) | — |
| S1A-08a | re-apply `initialScroll` on `maxValue` change | **DELETE** | — (locked: `CONTRADICTIONS.md`, resolved-by-convergence) | n/a | — | — |
| S1A-08b | `key` parameter on `rememberVicoScrollState` | **MOVE** to meApp (`key(…) { rememberVicoScrollState(…) }`) | chart opens on a stale/empty region after a segment switch (MOB-598 / MOB-1537) | **silent** | — | — |
| S1A-08c | `initialScrollHandled` `private` → `internal` | **RE-APPLY** | H-7's two-pass prepare stops compiling or silently stops running | **loud** (compile) | — | H-7 |
| S1A-09a | `getVisibleAxisLabels` + retained `drawingContext` | **RE-APPLY** (scoped) | chart header shows the wrong labels, or stale ones | **silent** | S1A-06, H-9 | — |
| S1A-09b | `getInterpolatedYValues` | **MOVE** to meApp | header loses estimated values for empty windows | **loud** (compile) | S1-D moving `MonotoneInterpolator` out | — |
| S1A-10 | `Scroll.Absolute.xBehindStartInset` | **MOVE** to meApp | chart opens flush against the left edge instead of behind the reserved gap | **loud** (compile) | — | — |
| S1A-11 | fading edges sized from the layer's own padding | **RE-APPLY** | the line emerges from a hard edge; a `0.dp` fade stops fading | **silent** | C-2 / C3 outcome (what sets `startPadding`) | — |
| S1A-12 | `CartesianChartRanges.getTargetYRange` | **RE-APPLY** | a transformed line is stranded outside the plot while the axis animates | **loud** (compile) | — | H-2, S1-D (`yTransform`) |
| S1A-13 | `MutableCartesianChartRanges.tryUpdateX` + `NeutralYRange` | **RE-APPLY** | one metric's readings widen the other metric's axis; `NoSuchElementException` on an all-transformed chart | mixed: `tryUpdateX` **loud**, `NeutralYRange` **silent** | — | S1-D (`yTransform`) |
| H-1 | host-owned range recomputation + opening-range derivation | **RE-DERIVE** | chart serves the domain captured at push time; two layers share one range | **silent** | S1A-01, S1A-02, S1A-03 | H-2, S1A-03 |
| H-2 | animated Y-range overlay + seed fallback | **RE-DERIVE** (wrapper stays a patch; the effect should MOVE in a follow-up) | Y range never animates; frame 0 flashes the full-model range | **silent** | H-1, S1A-12 | — |
| H-3 | viewport-window broadcast from the draw pass | **MOVE** to meApp as a `Decoration` | the provider is never told where the window is — the whole feature goes dead | **silent** | S1A-01 (publicise `scrollUpdates`), S1A-06 | H-2's effect move |

Count: 11 RE-APPLY · 4 MOVE · 2 RE-DERIVE · 3 DELETE · 0 DROP · 0 REPLACE.

**No DROP anywhere in this cluster.** C1 already caught one wrong DROP here; nothing I found at
v3.3.1 justifies another. The three DELETEs are a dead flag, a heuristic upstream genuinely
superseded (locked), and nothing else.

---

## 2. The three cluster-specific questions

### (a) C3 settled — `unscalableStart` **does** reach the same slot. S2-B is right.

**Verdict: C3 resolves in S2-B's favour. `CartesianLayerPadding(unscalableStart = …)` lands in
exactly the slot our `startInsetDp` patch writes, by the same mutator, and reaches `getFullXRange`
by the same property. S2-C's `shiftExtremeLabels` is a different feature and does not bear on this
question.** The patch to `CartesianChart.kt` (C-2 in S1-E) can be deleted — subject to two
arithmetic caveats below, both cheap and both checkable.

The call chain at v3.3.1, walked end to end:

1. `$C/cartesian/CartesianChart.kt:700` — `rememberCartesianChart(layerPadding: ((ExtraStore) -> CartesianLayerPadding) = { CartesianLayerPadding() })`.
   **Public parameter**, so a consumer can supply it. It becomes `CartesianChart.layerPadding`
   (`CartesianChart.kt:73`, `internal val`) and is evaluated into the measuring context as
   `CartesianMeasuringContext.layerPadding` (`$C/cartesian/CartesianMeasuringContext.kt:53`, **public**).
2. `$C/cartesian/layer/CartesianLayerPadding.kt:32-37` — `public val unscalableStart: Dp = 0f.dp`.
3. `$C/cartesian/layer/LineCartesianLayer.kt:897-903`, inside `updateLayerDimensions`:
   ```kotlin
   dimensions.ensureValuesAtLeast(
     xSpacing = xSpacing,
     scalableStartPadding = layerPadding.scalableStart.pixels,
     scalableEndPadding = layerPadding.scalableEnd.pixels,
     unscalableStartPadding = maxPointSize.half + layerPadding.unscalableStart.pixels,   // :901
     unscalableEndPadding  = maxPointSize.half + layerPadding.unscalableEnd.pixels,
   )
   ```
   (`ColumnCartesianLayer.kt:404` and `CandlestickCartesianLayer.kt:284` do the same, so this is
   every layer, not just lines.)
4. `$C/cartesian/layer/MutableCartesianLayerDimensions.kt:44-56` — `ensureValuesAtLeast` is a pure
   `coerceAtLeast` per field, i.e. a max. **Order-independent.** It writes
   `unscalableStartPadding` (declared at `:24`).
5. `$C/cartesian/layer/CartesianLayerDimensions.kt:52-54` —
   `public val startPadding: Float get() = scalableStartPadding + unscalableStartPadding`.
6. `$C/cartesian/CartesianMeasuringContext.kt:62-67` —
   ```kotlin
   internal fun CartesianMeasuringContext.getFullXRange(layerDimensions: CartesianLayerDimensions) =
     layerDimensions.run {
       val start = ranges.minX - startPadding / xSpacing * ranges.xStep
       val end = ranges.maxX + endPadding / xSpacing * ranges.xStep
       start..end
     }
   ```
   and `getVisibleXRange` (`:119-129`) is built on it.

Our mechanism, at `HEAD:$C/cartesian/CartesianChart.kt:291-300`, is the **same step 4 on the same
field**, just invoked from `CartesianChart.prepare` instead of from the layer:

```kotlin
if (startInsetDp > 0.dp) {
  layerDimensions.ensureValuesAtLeast(unscalableStartPadding = startInsetDp.pixels)   // :299
}
```

Supporting facts that make this conclusive rather than suggestive:

- `CartesianLayerPadding.kt`, `CartesianLayerDimensions.kt` and `MutableCartesianLayerDimensions.kt`
  are **byte-identical across all three refs**: `git diff e67e49520 v3.3.1 --stat` and
  `git diff e67e49520 HEAD --stat` on those three files both return empty. So the `unscalableStart`
  route existed at the fork point and we never touched it. S2-B's "we may have patched something
  that always had a parameter" is literally true.
- `MutableCartesianLayerDimensions.scale(factor)` (`:51-59`) multiplies `xSpacing` and the two
  *scalable* paddings and passes the two *unscalable* ones through untouched. So our patch's
  position *after* the `visibleLabelsCount` scale (`CartesianChart.kt:284-289`) buys nothing — the
  scale could never have touched this field. The late placement is not load-bearing.
- meApp does not currently pass `layerPadding` at all (`ProductChart.kt:104` and
  `SnapshotLineChart.kt:148` pass `startInsetDp`; no `layerPadding` anywhere under `app/src`), so
  the slot is free.

**Caveat 1 — the arithmetic differs by `maxPointSize.half`, and only because of another fork patch.**
Upstream's layer **adds** the inset to `maxPointSize.half` (`LineCartesianLayer.kt:901`); our chart
hook **maxes** it. At `HEAD` our `LineCartesianLayer` has dropped `maxPointSize.half` entirely
(`HEAD:…/LineCartesianLayer.kt:1051`: `unscalableStartPadding = layerPadding.unscalableStart.pixels`
— this is S2-D row 20g), so today the result is exactly `ChartStartGap` (12 dp,
`meApp…/chart/axis/VerticalAxis.kt:43`). On v3.3.1 with row 20g **not** ported, the same
`unscalableStart = 12.dp` yields `12.dp + maxPointSize/2` — a few dp wider. That is a product
decision, not a correctness one, and it interacts with S1A-11 (the fade widens with it). Settle 20g
and C-2 together.

**Caveat 2 — `calculateLabelSpacingScale` needs the number.**
`CartesianChart.kt:285` passes `startInsetDp.pixels` into `calculateLabelSpacingScale`
(`:304-317`), which subtracts it from the available width. Deleting `startInsetDp` requires
substituting `layerDimensions.unscalableStartPadding`, which is already populated at that point
(the layer's `updateLayerDimensions` runs at `CartesianChart.kt:245-250`, well before `:284`). This
is a one-line substitution and is arguably more correct, since it counts whatever else reserved
space.

**Caveat 3 — ordering vs. the bottom axis, and why it is moot today.**
`HorizontalAxis.updateLayerDimensions` at v3.3.1 (`:568-600`) reads
`internalGetFullXRange(layerDimensions)` at `:576` *before* contributing its own
`ensureValuesAtLeast(unscalableStartPadding = …)` at `:599`. So moving the inset earlier (into the
layer) changes what the axis sees. In practice this cannot bite us in either plausible world:
our fork makes `HorizontalAxis.updateLayerDimensions` a no-op (`HEAD:…/HorizontalAxis.kt:509-512`),
and v3.3.1's own version returns early when `itemPlacer.getShiftExtremeLabels(context)` is true
(`:573`) — the S2-C V4 path. Only a world where we restore upstream's `updateLayerDimensions`
*and* decline `shiftExtremeLabels` needs a visual re-check.

**What this does to the migration:** `CartesianChart.kt` is the fork's hardest file (ours +203/−12,
upstream +228/−18). This removes one of its seven patch sites outright — the `startInsetDp`
constructor/`copy`/`rememberCartesianChart` parameter and the `prepare` hook — and turns it into a
named argument in `ProductChart.kt` and `SnapshotLineChart.kt`. The patch site is C-2, owned by the
host/composition cluster; this dossier settles the *question*, not the port.

S2-C's `shiftExtremeLabels` claim stands as written: it is an adjacent, unproven answer to a
different problem (label clipping, not plot padding). It neither competes with nor substitutes for
`unscalableStart`. Both may be adopted; they do different things.

---

### (b) The root cause — which half moves, which half stays

**Short answer: yes, the window feed can become a consumer `Decoration` with no new library seam.
No, the range *application* cannot — it stays a patch. The dividing line is the moment a computed
Y range has to become the `CartesianChartRanges` the chart draws with.**

**The half that moves — measurement and broadcast (H-3, and H-2's effect in a follow-up).**
`Decoration` is a public interface whose two methods are handed a full `CartesianDrawingContext`
(`$C/cartesian/decoration/Decoration.kt:30-37`), and it is drawn every frame from
`CartesianChart.draw` (`$C/cartesian/CartesianChart.kt:380`, `:399`). Every input the internal
`getVisibleXRange` uses is public on that context:

| needed | where | visibility at v3.3.1 |
|---|---|---|
| `layerBounds`, `layerDimensions`, `scroll`, `zoom` | `$C/cartesian/CartesianDrawingContext.kt:29-40` | public |
| `ranges.minX` / `ranges.xStep` | `$C/cartesian/data/CartesianChartRanges.kt:26-32` | public |
| `layerDimensions.startPadding` / `xSpacing` | `$C/cartesian/layer/CartesianLayerDimensions.kt:25,52` | public |
| `layoutDirectionMultiplier`, `isLtr` | `$C/common/MeasuringContext.kt:63-72` | public |
| `model`, `extraStore` | `MeasuringContext` / `CartesianMeasuringContext` | public |
| `decorations` entry point | `$C/cartesian/CartesianChart.kt:703` (`rememberCartesianChart`) | public |

So a meApp `WindowProbe : Decoration` can reproduce `getFullXRange`/`getVisibleXRange` verbatim
(the formula is three lines, `CartesianMeasuringContext.kt:62-67` and `:119-129`) and publish the
window. That deletes the whole of H-3 from `CartesianChartHost.kt` — the `visibleXRanges` flow
(Host:316), the three-scalar dedup (Host:331-333) and the per-frame emit block (Host:487-512) —
from the file upstream churned +196/−94. It also removes H-4's feed (marker cluster) by the same
route.

One thing it needs from us, and it is free: `ScrollAwareRangeProvider.scrollUpdates`,
`computeVisibleEntries`, `computeDisplayRange`, `isCacheReady`, `buildCache`, `ScrollInfo` are
`internal` **in our own new file**. Widening them to `public` is not a patch to upstream code at
all. Once they are public, H-2's `ScrollAwareRangeEffect` (the debounce, the `Animatable`, the
settle bump, the tick swap — roughly 130 lines at Host:562-690) can also move to meApp, because
every symbol it touches is then either public upstream API or our own public API.

**The half that stays — range application (H-1 + H-2's wrapper).** Verified directly at v3.3.1:

- `CartesianChart.updateRanges(ranges, model)` is `internal fun`
  (`$C/cartesian/CartesianChart.kt:406`). Its only callers are
  `$C/cartesian/data/CartesianChartModel.kt:129` (once per data push) and
  `$C/cartesian/CartesianChartHost.kt:191` (inside `remember(chart, model)`).
- `MutableCartesianChartRanges.reset()` / `toImmutable()` are `internal`.
- `CartesianChartHostImpl` **does** take `ranges: CartesianChartRanges`
  (`CartesianChartHost.kt:211`, passed at `:199`) — but `CartesianChartHostImpl` is `internal` and
  neither public `CartesianChartHost` overload exposes it (`:180-202`, `:100-161`).

So there is no way, from meApp, to hand the chart a `CartesianChartRanges` of its own, and no way
to make the chart re-ask a `CartesianLayerRangeProvider` outside a data push. That is S2-D's sealed
door **S1**, and it is confirmed. Concretely, these stay:

- **H-1** — the host-owned `MutableCartesianChartRanges`, `reset()` + `chart.updateRanges(...)` +
  `toImmutable()` keyed on `remember(chart, model)`, and the provider seeding that must run inside
  that block *before* `updateRanges`.
- **H-2's `AnimatedYCartesianChartRanges`** (Host:696-722) and the `ranges` selection that chooses
  it over `initialRanges` (Host:138-184).
- **S1A-12** (`getTargetYRange`), because the wrapper is the only implementor that returns anything
  different from `getYRange`.

**The smallest shape that remaining patch could take.** Since `CartesianChartHostImpl` already
accepts `ranges`, a single defaulted parameter on the two public overloads —
`ranges: ((CartesianChartRanges) -> CartesianChartRanges)? = null`, applied immediately before the
`CartesianChartHostImpl(...)` call at `CartesianChartHost.kt:193-201` — would let meApp supply the
animated decorator itself. That is roughly six lines replacing roughly 250, and it is also the
cleanest upstream PR in this cluster (S2-D's S1). **It is a design proposal, not a verdict:** it
needs a spike confirming that a decorator whose values are backed by snapshot state invalidates the
draw pass the way our current host-state-plus-recomposition path does. Until that spike passes,
H-1/H-2 are RE-DERIVE as written.

**What this means for the sequencing:** do the port first in the shape the fork already has
(RE-DERIVE H-1/H-2 into v3.3.1's restructured host), prove parity, and only then move H-3 and
H-2's effect out. Doing both at once makes a port defect indistinguishable from a redesign defect,
and this cluster's defects are overwhelmingly silent.

---

### (c) The `SnapFlingBehavior` collision — what survives, where, and under what name

**First, a correction to S2-A §1.4.** There is **no redeclaration conflict**. Upstream's class is
`internal class SnapFlingBehavior` declared *inside* `$C/cartesian/Modifier.kt:191-209`. Ours is
`internal class ChartSnapFlingBehavior`, declared in `$C/cartesian/SnapFlingBehavior.kt:65`.
Different simple names in the same package compile fine; what collides is the **file name's
connotation**, not any declaration. Checked tree-wide: `git grep -n 'SnapBehaviorConfig\|rememberChartSnapFlingBehavior\|isSnapping' v3.3.1 -- vico/`
returns **nothing**, so none of our three public names clashes either. The hazard is real but
smaller than recorded: `import …cartesian.SnapFlingBehavior` would silently resolve to upstream's
class, and anyone renaming ours to match its file would then break the build.

**What survives, and where it lives.**

Per C2 (locked): WEEK may move to upstream's `xSnapStep`, YEAR cannot, because calendar months are
not a uniform step. Reading meApp's rules narrows C2's WEEK half further, and this is new evidence
rather than a re-litigation — `GraphSnapHelper` applies **different rules for drag and fling on the
same segment**: WEEK snaps to a *day* boundary on drag (`GraphSnapHelper.kt:61`,
`snapToDayBoundary`) and to a *week* boundary on fling (`:153-157`, `:189`); MONTH does nothing on
drag (`:63`) but snaps to a month on fling (`:164-165`, `:192`). Upstream has one rule per state
(`SnapScroll.kt:45-62`, a single `xSnapStep` with `0f` and `maxValue` as the only alternatives) and
no drag/fling distinction (`Modifier.kt:191-209` runs one path for both). So `xSnapStep` could at
most replace WEEK's *drag* rule. That is not enough to retire anything; it is an optional
simplification for a product decision, not a migration task.

**Therefore the whole of our snap behaviour survives, and it should leave the library:**

| what | where it goes | called |
|---|---|---|
| `SnapBehaviorConfig` + nested `SnapAnimation` | meApp, beside `GraphSnapHelper` | unchanged |
| `ChartSnapFlingBehavior` | meApp | unchanged (already distinct from upstream's name) |
| `rememberChartSnapFlingBehavior` | meApp | unchanged |
| the file | `meApp…/features/dashboard/components/chart/ChartSnapFlingBehavior.kt` | **renamed from `SnapFlingBehavior.kt`** |
| `isSnapping` | nowhere | **deleted** |

`FlingBehavior` is a Compose Foundation interface, not a Vico one, so nothing in the file needs the
library — except three `VicoScrollState` members it calls: `scrollValueToX()`,
`xToScrollValueBehindStartInset()` and `maxValue`/`value`. Two of those are ours (S1A-06) and
currently `internal`; `value`/`maxValue` are already public at v3.3.1
(`$C/cartesian/VicoScrollState.kt:80`, `:89`). **Widening our two conversions to `public` is
strictly cheaper than keeping the file** — it is a visibility change in a patch we are keeping
anyway, and it is also S2-D's sealed door **S6**, i.e. the right upstream ask.

The move has one hard prerequisite outside this cluster: the `flingBehavior` parameter on
`CartesianChartHost` and `Modifier.pointerInput` (H-8 / M-3). v3.3.1 has no such seam —
`Modifier.pointerInput` picks its fling behaviour internally from `xSnapStep`
(`Modifier.kt:63-72`) and `CartesianChartHost` takes no `FlingBehavior`. If that patch is kept
(it must be, or snapping dies entirely), the MOVE is free. If H-8 were ever dropped in favour of
`xSnapStep`, this MOVE dies with it. **Settle H-8 before executing this move.** Fallback if the
conversions cannot be made public for some reason: **RE-APPLY** in the library, under the renamed
file `ChartSnapFlingBehavior.kt`.

**`isSnapping` — a bug to delete, not to fix.**
`git grep -n 'isSnapping' HEAD -- vico/ sample/` returns three hits, all in
`SnapFlingBehavior.kt` (declaration `:71`, `isSnapping = true` `:99`, `isSnapping = false` `:115`).
It is `internal`, so meApp cannot see it either; the grep is conclusive. `FEATURE-4`'s "Range
Provider Integration" section describes a suppression that does not exist.

**Delete the flag; do not restore the suppression.** Restoring it would now be wrong, not merely
redundant. During a snap the scroll really is moving, so the window emissions are correct; they are
already collapsed by two independent mechanisms — the three-scalar dedup at Host:491-512 (no emit
unless the viewport actually moved) and `debounce(provider.debounceMs)` + `collectLatest` in
`ScrollAwareRangeEffect` (Host:562-690), which means only the window the snap *lands on* starts a
range animation. Suppressing emissions for the snap's duration (default 500 ms in meApp,
`GraphView.kt:222`) would instead make the Y axis lag the chart by the whole animation. Delete
three lines, and delete the `FEATURE-4` paragraph that describes them.

---

## 3. Evidence, per adaptation

### S1A-01 — `ScrollAwareRangeProvider` (core) · RE-APPLY

**Evidence.** `CartesianLayerRangeProvider` at v3.3.1
(`$C/cartesian/data/CartesianLayerRangeProvider.kt`) is still
`getMinX/getMaxX/getMinY/getMaxY(minY, maxY, extraStore)` with no scroll, bounds or window input —
verified in C1 and re-checked here. There is no upstream analogue; the file is new and
`git diff e67e49520 v3.3.1` touches nothing it depends on.

**Why not MOVE now.** The provider *could* become meApp code once the window feed moves (b), but
the file grafts clean and moving it in the same step as the port makes port defects and redesign
defects indistinguishable. Port it; widen `scrollUpdates`, `buildCache`, `computeVisibleEntries`,
`computeDisplayRange`, `isCacheReady`, `cachedSeries`, `cachedWindow`, `ScrollInfo` to `public` so
H-3's move is unblocked.

**Risk if wrong.** Deleting it deletes the fork's flagship behaviour and nothing fails to compile —
the chart simply draws every window at full-dataset scale. **Silent.** C1 already caught one
attempt.

**Sequencing.** Must land before H-1, H-2, H-3. The visibility widening must land with it.

---

### S1A-02 — `Window` / `WindowKey` in `ExtraStore` · RE-APPLY

**Evidence.** Built entirely on `ExtraStore.Key` / `ExtraStore.getOrNull`, public at v3.3.1
(`$C/common/data/ExtraStore.kt`) and unchanged in the range. `Transaction.extras(block)` is
still the sanctioned channel (`$C/cartesian/data/CartesianChartModelProducer.kt`). Nothing
upstream competes with it.

**Risk if wrong.** Without the travelling window the chart composes against the previous window and
draws one frame off-canvas per push. **Silent** — it reads as a flicker.

**Sequencing.** With S1A-01. Its correctness depends on H-1 recomputing ranges when the chart
changes; port them together or the window arrives with nowhere to land.

---

### S1A-03 — `seedFromWindow` + cache/window guards · RE-APPLY

**Evidence.** Provider-local code (`ScrollAwareRangeProvider.kt:123-179, 210-244`) with no upstream
dependency; it ports verbatim. What does **not** port verbatim is its call site, which is H-1.

**Risk if wrong.** Two failure shapes, both **silent**: run it a frame late and the chart opens on
the whole dataset's extent, then re-letters the axis (the MOB-3494 defect it was built for); lose
the `cachedSeries` / `cachedWindow` split and every model push drags the axis back to the opening
window from wherever the user had scrolled.

**Do not move it to meApp.** Splitting this computation between meApp and the library is exactly
what caused the two-producer defect `a98cf86de` fixed.

**Sequencing.** After H-1 is re-derived, because it is H-1's ordering guarantee that makes it work.

---

### S1A-04 — `WindowSettleSignal` / `settleEpoch` · RE-APPLY

**Evidence.** 35-line interface with zero upstream dependencies. No v3.3.1 counterpart (S2-B #1,
#11: there is no render-time Y transform hook anywhere in v3.3.1, so nothing to key against).
meApp already implements it directly (`ProductChart.kt:277-296`), so the public shape is load-bearing.

**Risk if wrong.** Two distinct silent failures. Drop the interface and `yTransform` falls back to
keying on the Y range, so two windows that round to the same axis leave the second line scaled for
the window the user left. Keep the interface but move `provider.settleEpoch++` to *after* the
"range did not move" early return (Host:654 vs :656-657) and you get the same bug for the
no-movement case only — which is the common case. **Silent**, and the second form is a merge
artefact waiting to happen.

**Sequencing.** Before S1-D's `yTransform` port. The file itself can go first, any time.

---

### S1A-05a — snap behaviour · MOVE to meApp · S1A-05b — `isSnapping` · DELETE

Evidence and reasoning in §2(c). Line references: ours
`$C/cartesian/SnapFlingBehavior.kt:43-52, 65-120, 126-131`; upstream's collision candidate
`$C/cartesian/Modifier.kt:191-209`; upstream's snap maths `$C/cartesian/SnapScroll.kt:23-62`;
upstream's selection `$C/cartesian/Modifier.kt:63-72`; meApp's wiring
`features/dashboard/components/chart/GraphView.kt:203, 210-225`; meApp's rules
`features/common/helper/graph/GraphSnapHelper.kt:59-64, 143-199`.

**Risk if wrong.** A botched move fails to compile (**loud**). Carrying `isSnapping` forward risks
someone "restoring" the documented suppression and introducing a 500 ms Y-axis lag — **silent**.

**Sequencing.** Blocked by S1A-06 (public conversions) and by H-8/M-3 (`flingBehavior` seam). Do
not start until H-8 is settled.

**Preserve verbatim:** the `500f` drag/fling velocity threshold (`:78`) and the `4.5f` decay divisor
(`:85`). Neither has a derivation anywhere in the repo; S1-A flagged both and I did not find one
either. They are tuned constants.

---

### S1A-06 — scroll ⇄ X helpers and `visibleXRange` · RE-APPLY, widened to public

**Evidence.** At v3.3.1 `context`, `layerDimensions` and `bounds` are `private`
(`$C/cartesian/VicoScrollState.kt:152-161`, the `withUpdated` accessor), so these four functions
cannot live outside the class — the move is impossible, the patch is irreducible. Upstream added
nothing equivalent: `value` and `maxValue` are public (`:80`, `:89`) but there is no public
conversion from pixels to an x value and no public visible range. This is S2-D's sealed door **S6**
and the strongest upstream-PR candidate in the cluster.

**What changes vs. HEAD.** Make `xToScrollValue`, `xToScrollValueBehindStartInset` and
`scrollValueToX` `public` rather than `internal`. `visibleXRange` is already public. Four functions,
~25 additive lines over state the class already holds.

**Risk if wrong.** Omitting them fails to compile in meApp (`GraphView.kt:401, 444, 496`) — **loud**.
Getting the arithmetic subtly wrong (e.g. including `startPadding` in the wrong one of the two) is
**silent** and there is no test pinning it. Note the asymmetry deliberately: `xToScrollValue`
includes `startPadding`, `xToScrollValueBehindStartInset` does not; that difference is the whole
point of S1A-10 and S1A-11's single-source rule.

**Sequencing.** Early. Blocks S1A-05a, S1A-09a and H-3.

---

### S1A-07 — scrub freeze · RE-APPLY

**Evidence, and a correction to the S1-A risk rating.** The `ScrollableState` lambda body is
**identical** at the fork point (`e67e49520:…/VicoScrollState.kt:55-65`) and at v3.3.1
(`v3.3.1:…:65-75`) — upstream did not touch it in the range. Our change
(`HEAD:…:203-221`) is a pure wrap of that unchanged body in
`if (isScrollFrozen || !scrollEnabled) delta else { … }`. S1-A rated this "high — structural
rewrite of a hot upstream expression"; on the evidence it is a mechanical re-wrap. Treat the risk
as the *semantics*, not the merge.

**The semantic change to watch.** At v3.3.1 `scrollEnabled` became
`internal var scrollEnabled by mutableStateOf(true)`, driven by `SideEffect` in
`rememberVicoScrollState` (`:385`) and removed from the `rememberSaveable` key list (`:348-354`) —
upstream commit `1fe9daef9`. Our `!scrollEnabled` arm now reads snapshot state that can flip
mid-composition, which is what meApp's expression at `GraphView.kt:198` actually does. This is an
improvement (S2-C V2: toggling no longer resets the scroll position), but it means the freeze arm
becomes reactive where it previously could not change. Worth one pass on a chart whose
`isSingleWindow` resolves late.

**Risk if wrong.** Lose the wrap and scrubbing a chart inside a `LazyColumn` scrolls the screen
instead of the marker — the exact defect `3a284be42` fixed. **Silent.** Lose only the
`!scrollEnabled` arm and the same thing happens on TOTAL / single-window segments only.

**Sequencing.** With M-1/M-2 (the gesture cluster), which owns the four `isScrollFrozen` writes
(`Modifier.kt:109, 136, 146, 206`) and the `scrollable(enabled = true)` patch. The freeze/unfreeze
pairing is invariant-by-convention and nothing enforces it; if the gesture cluster is re-derived,
re-check the pairing rather than assuming it.

---

### S1A-08a — re-apply `initialScroll` on `maxValue` change · DELETE

**Evidence.** Locked by `CONTRADICTIONS.md` (resolved-by-convergence, S2-A + S2-C + S2-D agree).
Re-verified: v3.3.1's `update()` (`$C/cartesian/VicoScrollState.kt:163-207`) re-anchors the viewport
properly across `minX`/`xStep` changes via `previousScrollLayout` and a `preClampValue` captured
before the `maxValue` setter clamps (`:171-198`), with the shrinking-content case named in
upstream's own comment at `:171-176`.

**Note for whoever executes it.** Upstream's mechanism and ours are not the same behaviour —
upstream *preserves* the x on screen, ours *re-applies* `initialScroll`. The case ours was written
for (a stale placeholder model replaced by real data, where we want to jump to the latest rolling
window) is covered instead by S1A-08b's `key`, which discards the scroll state entirely. Delete
08a, keep 08b, and re-test the placeholder→data transition specifically.

**Risk if wrong.** Carrying it forward re-introduces a second, competing re-anchoring rule inside
the method upstream just rewrote. **Silent**, and it would fight upstream's correction.

---

### S1A-08b — `key` on `rememberVicoScrollState` · MOVE to meApp

**Evidence.** The parameter exists only to make `rememberSaveable` produce a fresh state. Wrapping
the call in Compose's `key(…) { }` achieves the same thing without a library change: `key()` changes
`currentCompositeKeyHash`, which is what `rememberSaveable` derives its auto-generated key from, and
it gives a fresh `remember` slot. meApp's only use is
`GraphView.kt:197-201` (`key = Triple(segment, isEmptyGraph, endTimestamp)`);
`SnapshotLineChart.kt:167-170` does not pass it. S2-D row 9(g) reaches the same conclusion.

This also sidesteps a re-siting problem: our `key` is the **first** `rememberSaveable` input, and
upstream reworked that input list at v3.3.1 (`:348-354` — `scrollEnabled` removed, `xSnapStep` and
`snapAnimationSpec` added). And it removes a signature change that S1-A notes is source-compatible
but **not** binary-compatible.

**Risk if wrong.** The chart opens on a stale or empty region after a segment switch — MOB-598 and
MOB-1537 return. **Silent.**

**Verification that settles it** (do this once, before deleting the parameter): switch segment
A → B → A and confirm the chart re-applies `initialScroll` on the return rather than restoring A's
old scroll position. Our `key`-as-input discards the saved value; `key()`-as-wrapper relies on the
inner `rememberSaveable` unregistering when it leaves composition. I expect them to agree, but I
did not prove it from the repo. If they disagree, fall back to **RE-APPLY** — re-sited to the end
of upstream's new input list.

---

### S1A-08c — `initialScrollHandled` `private` → `internal` · RE-APPLY

**Evidence.** `private var initialScrollHandled` at `v3.3.1:$C/cartesian/VicoScrollState.kt:55`;
`internal var` at `HEAD:…:50`. The widening exists for a **second** consumer that has nothing to do
with S1A-08a: the host reads it at `CartesianChartHost.kt:467` to decide whether to run H-7's
two-pass prepare, so `VerticalAxis.updateHorizontalLayerMargins` sees a post-initial-scroll scroll
value.

**This row exists so the widening is not deleted along with S1A-08a.** They are adjacent lines in
the same diff and they have opposite verdicts.

**Risk if wrong.** Reverting to `private` fails to compile at the host — **loud**. Keeping the
widening but losing H-7's call site reads as an optimisation being tidied away — **silent**.

**Sequencing.** With H-7 (host cluster).

---

### S1A-09a — `getVisibleAxisLabels` + retained `drawingContext` · RE-APPLY (scoped)

**Evidence.** The signature it depends on is unchanged: `HorizontalAxis.ItemPlacer.getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth)`
at `v3.3.1:$C/cartesian/axis/HorizontalAxis.kt:840-845` is identical to ours at
`HEAD:…:704-709`. So the hard dependency survives the upgrade despite `HorizontalAxis` being hot on
both sides. The `itemPlacer` branch genuinely needs a `CartesianDrawingContext`, which only the draw
pass has, so it cannot move to meApp.

**Keep it, but scope it.** `drawingContext` is a draw-pass object retained past the draw pass
(assigned every frame at Host:531 — that is H-9); reading it later reads a stale snapshot by
construction. Prefer narrowing the retained reference to what the item placer actually needs. That
is a cleanup, not a blocker.

**Risk if wrong.** Omission fails to compile in meApp (`GraphView.kt:408, 413-416`) — **loud**.
Keeping it but losing the H-9 assignment leaves `drawingContext` null and silently falls through to
the even-walk strategy — **silent**, and it produces plausible-looking wrong labels.

**Unverified, carried forward from S1-A:** `getVisibleAxisLabels` passes `maxLabelWidth = 0f`.
Whether an upstream item placer reads `0f` as "unconstrained" or "no room" is still unestablished;
meApp's own `HorizontalItemPlacer` handles it, so the exposure is limited to anyone passing an
upstream placer. See §5.

---

### S1A-09b — `getInterpolatedYValues` · MOVE to meApp

**Evidence.** It reads only `ctx.model.models`, filters to `LineCartesianLayerModel` and
interpolates. meApp produces that model and holds the same series it pushed, so it needs nothing
from the chart. Its only library tie is `MonotoneInterpolator`, which S1-D is moving to meApp
anyway: v3.3.1 introduced a public `LineCartesianLayer.Interpolator`
(`interpolate(context, path, points, visibleIndexRange)` + `visiblePadding` + `getYRange`), which
S2-D names as the single cleanest conversion in the whole audit (library change: zero).

**Risk if wrong.** Fails to compile in meApp (`GraphView.kt:497`) — **loud**. The subtlety to carry
across is `25dc072a9`: outside the data range it returns `null`, deliberately **not** clamped. A
re-implementation that clamps produces confident wrong numbers in the header — **silent**.

**Sequencing.** After S1-D has moved `MonotoneInterpolator` out. Not on the critical path.

---

### S1A-10 — `Scroll.Absolute.xBehindStartInset` · MOVE to meApp

**Evidence.** `Scroll.Absolute` is a `public fun interface` at
`v3.3.1:$C/cartesian/Scroll.kt:27-34`, and every parameter type is public
(`CartesianMeasuringContext`, `CartesianLayerDimensions`, `Rect`, `Float`); `context.ranges` and
`layerDimensions.xSpacing` are public. Our body is upstream's `x()` (`Scroll.kt:51-56`) minus
`layerDimensions.startPadding`. meApp can declare it today, unchanged:

```kotlin
fun xBehindStartInset(x: Double, bias: Float = 0f) = Scroll.Absolute { ctx, dims, bounds, _ -> … }
```

`Scroll.kt` is in BASELINE's "upstream never touched" list — confirmed:
`git diff e67e49520 v3.3.1 --stat -- $C/cartesian/Scroll.kt` is empty. So this patch grafts
cleanly and we are deleting it anyway, purely to stop patching the file at all.

**Risk if wrong.** Fails to compile in meApp (`GraphView.kt:188`,
`SnapshotLineChart.kt:94`) — **loud**. The silent risk is in the body, not the move: it assumes
`startPadding` is the only thing between the scroll origin and the first entry. That assumption is
shared with S1A-11 and is exactly what C3's outcome changes the *size* of (not the shape).

**Housekeeping.** Our `Scroll.kt` carries two stacked KDoc blocks (`:58-62` and `:63-72`); the first
documents the removed `xWithPadding` and names a parameter that no longer exists. Drop it in the move.

---

### S1A-11 — fading edges sized from the layer's own padding · RE-APPLY

**Evidence.** `FadingEdges.kt` is byte-identical between the fork point and v3.3.1
(`git diff e67e49520 v3.3.1 --stat -- $C/cartesian/FadingEdges.kt` → empty), so the +10/−4 patch
grafts clean. It cannot become an extension point: `FadingEdges` is `public open` with `protected`
width fields but `draw` is `internal fun`, **not open** — a subclass can change the four constructor
values and nothing else. That is S2-D's sealed door **S9**, and it is the route `PaddedFadingEdges`
took before MOB-2953 deleted it as a mistake.

**Risk if wrong.** The line emerges from a hard edge instead of a soft one. **Silent.** There is a
second, sharper silent risk: the patch makes a zero-width `FadingEdges` draw a fade whenever the
layer reserves padding, and meApp depends on that (`GraphView.kt:181-185` passes
`startWidth = 0.dp, endWidth = 0.dp` precisely to get the layer's own padding). Reverting to
upstream's behaviour removes the fade entirely at both edges with no error.

**Sequencing.** Any time, but re-check the visual **after** C3's outcome lands: the fade width is
`maxOf(startWidth.pixels, layerDimensions.startPadding)`, so if the inset moves to
`layerPadding.unscalableStart` and upstream's `maxPointSize.half` returns, the fade widens with it.

**Worth offering upstream** as written: "the fade should cover the layer's reserved padding" is a
general improvement, not a meApp quirk.

---

### S1A-12 — `CartesianChartRanges.getTargetYRange` · RE-APPLY

**Evidence.** v3.3.1's `CartesianChartRanges` has `minX`, `maxX`, `xStep`, `getYRange`, `xLength`,
`YRange` and `Empty` and nothing else (`$C/cartesian/data/CartesianChartRanges.kt:26-76`); there is
no target-range concept. The file is untouched in the range (`git diff` empty). It cannot move:
meApp has no way to observe an in-flight library animation, and the only implementor that returns
something different is H-2's `AnimatedYCartesianChartRanges`.

**Risk if wrong.** Dropping it is a **compile** error at both the implementor (Host:714-721) and the
consumer (`LineCartesianLayer.kt:843`) — the good failure mode, which is why this is a cheap row.
The silent risk is subtler: implement it but leave the consumer reading `getYRange`, and a
transformed line is stranded outside the plot while the axis animates out from under it.

**Alternative worth one experiment during the port** (S1-A's own proposal): drive the `yTransform`
recompute entirely from `settleEpoch` (S1A-04), with the transform told the target range through the
signal rather than through the ranges. That removes a patch to a public upstream interface for the
cost of a wider settle signal. Do not attempt it in the same step as the port.

---

### S1A-13 — `tryUpdateX` + `NeutralYRange` · RE-APPLY

**Evidence.** At v3.3.1, `MutableCartesianChartRanges` has no `tryUpdateX` — `tryUpdate(minX, maxX, minY, maxY, axisPosition)`
(`:47`) is the only mutator and it is x-and-y together — and `yRanges.getValue(null)` is still
present in **both** places: `getYRange` at `:41` and the `toImmutable()` anonymous object at `:101`.
The file is untouched in the range (`git diff` empty). `_minX`/`_maxX` are private and
`updateChartRanges` is upstream's own method, so neither half can live outside the library.

**The trap, verbatim from S1-A and now confirmed by line number.** The `getValue(null)` →
`?: NeutralYRange` substitution must be applied at **:41 and :101**. Our test
(`MutableCartesianChartRangesTest.kt`) pins the first and not the `toImmutable()` copy, so applying
only one passes the suite.

**Risk if wrong.** Losing `tryUpdateX` is a **compile** error at its single caller
(`LineCartesianLayer.kt:1057-1072`). Losing `NeutralYRange` is a **crash** —
`NoSuchElementException` — but only on a chart whose every layer is transformed, which is a reachable
degenerate case the `yTransform` work newly created. Applying the substitution to only one of the
two sites is the **silent** failure: it works until the `toImmutable()` path is hit.

**Upstream-PR note.** The `NeutralYRange` half is a genuine upstream bug fix independent of the fork
(S2-D sealed door **S4**) and should be offered on its own; `tryUpdateX` is a weaker, fork-specific ask.

---

### H-1 — host-owned range recomputation + opening-range derivation · RE-DERIVE

**Evidence.** The code is additive and the destructuring line it replaces still exists in the same
form, so the *textual* conflict is small — but the function around it changed shape:
`CartesianChartHostImpl` gained `modifier` and `chartAreaHeight` parameters, the `Canvas` moved
inside the new `ChartHostBox` (`$C/common/ChartHostBox.kt`), `chart.prepare(context, layerDimensions)`
became `chart.prepare(context, layerDimensions, size)`, and the producer call's
`animateIn: Boolean` became `initialAnimationSpec: AnimationSpec<Float>?`
(`CartesianChartHost.kt:158`, deprecated overload retained). The block's correctness depends on an
**ordering** upstream never promised — it must run during composition, inside the
`remember(chart, model)`, *before* `chart.updateRanges(hostRanges, model)` — and that ordering has to
be re-established against the new structure rather than transplanted.

**Risk if wrong.** Three silent failures, all previously shipped and fixed:
the chart serves the domain captured at push time and draws off-canvas until an unrelated push
(`25f0fb793`); the opening range is derived a frame late so the chart opens on the whole dataset
(`a98cf86de`); layers pair with models by `indexOf`/`equals` instead of by position, so two
similarly-configured layers both resolve to `models[0]` and the second draws against the first's
range (`e6c766cb8`). **All silent.** The last one is the nastiest: it only shows up with two layers
configured alike.

**Sequencing.** After S1A-01/02/03 are in the tree. Before H-2. This is the hardest single item in
the cluster and should be done alone, with the three defects above as explicit regression cases.

---

### H-2 — animated Y-range overlay and seed fallback · RE-DERIVE

**Evidence and the split.** See §2(b). The `AnimatedYCartesianChartRanges` wrapper
(Host:696-722) and the `ranges` selection (Host:138-184) **stay a patch**, because
`CartesianChartHost`'s public overloads expose no `ranges` parameter
(`CartesianChartHost.kt:180-202`) while `CartesianChartHostImpl` — which does take one at `:211` —
is `internal`. `ScrollAwareRangeEffect` (Host:562-690) **can** move to meApp once S1A-01's members
are public, and should, in a follow-up step.

**Risk if wrong.** Losing the wrapper means the Y range never animates and the axis snaps — **silent**.
Losing the seed fallback (Host:146-160) means frame 0 flashes the full-model Y range before the
first settle — **silent**, and it is the defect `82cda206e` fixed. Four correctness details recorded
in the in-file comments are each a silent regression on their own: animatables initialised *after*
cache build; dedup by provider identity; `NaN` as the uninitialised sentinel; and — the one most
likely to be lost — `provider.settleEpoch++` placed **before** the "range did not move" early
return (Host:654 vs :656-657).

**Sequencing.** After H-1 and S1A-12. The effect's MOVE comes after parity is proven, never in the
same step.

---

### H-3 — viewport-window broadcast from the draw pass · MOVE to meApp as a `Decoration`

**Evidence.** Full table of the public members a `Decoration` is handed is in §2(b). The decisive
facts: `Decoration` is a public interface taking a full `CartesianDrawingContext`
(`$C/cartesian/decoration/Decoration.kt:30-37`); it is invoked every frame from
`CartesianChart.draw` (`$C/cartesian/CartesianChart.kt:380, 399`); `decorations` is a public
parameter of `rememberCartesianChart` (`:703`); and `getVisibleXRange`'s entire input set
(`layerBounds`, `layerDimensions`, `scroll`, `ranges`, `layoutDirectionMultiplier`) is public, so
the three-line formula at `CartesianMeasuringContext.kt:62-67` and `:119-129` is reproducible
without the `internal` helper.

This is the single largest reduction available in the cluster: it deletes the `visibleXRanges` flow
(Host:316), the three-scalar dedup (Host:331-333) and the per-frame emit (Host:487-512) from the
file upstream churned +196/−94 — the most expensive file in the fork to re-merge.

**Two things to carry across, not reinvent.** (1) The dedup: emitting unconditionally re-triggers
the whole settle chain on draws that changed nothing, and that was a real performance defect
(`6ef5fdcc7`, `57ce985ea`). Keep the three-scalar guard in the decoration. (2) The
`layerDimensions.xSpacing > 0f` guard.

**Two caveats.** `Decoration` is annotated `@Immutable` and `CartesianChart.equals` compares
`decorations`, so the probe must be a stable remembered instance whose mutable state lives behind it,
not in it. And a decoration draws *inside* `chart.draw`, a few lines after the host's current emit
point in the same frame — equivalent timing, but confirm it is still after `scrollState.update(...)`
in v3.3.1's restructured Canvas.

**Risk if wrong.** If the decoration is not installed, or is installed on a chart instance that
`equals` an older one, the provider is simply never told where the window is and the entire
scroll-aware feature goes dead — **silently**, with the chart drawing a correct-looking full-range
axis.

**Sequencing.** Last. It requires S1A-01's members to be public and S1A-06 in place, and it should
land only after H-1/H-2 have been proven at parity in their ported shape.

---

## 4. Order of work for this cluster

The ordering is driven by one fact: almost every failure in this cluster is **silent**. So the
sequence puts the loud-failing, upstream-untouched work first (it proves the build), then the
irreducible core (it proves the behaviour), and only then the reductions (which are the only places
a *new* defect can be introduced).

**Phase 0 — settle the two open prerequisites before anyone writes code.**
1. **C3 / C-2.** Decide `startInsetDp` → `layerPadding(unscalableStart = …)` together with S2-D row
   20g (`maxPointSize.half`), since the two set the same number. §2(a) gives the answer and the two
   caveats; what remains is a product look at the few-dp difference. Blocks S1A-11's visual check.
2. **H-8 / M-3.** Confirm the `flingBehavior` seam is kept. Blocks S1A-05a. If it were ever dropped,
   S1A-05a reverts from MOVE to dead.

**Phase 1 — the files upstream never touched (graft clean, fail loudly).**
3. **S1A-12** `getTargetYRange` · **S1A-13** `tryUpdateX` + `NeutralYRange` (both sites, :41 and :101)
   · **S1A-11** FadingEdges · **S1A-04** `WindowSettleSignal`.
   All four are in BASELINE's untouched list, total well under 100 lines, and three of the four fail
   to compile if dropped. Doing them first means the data-layer contracts the rest depends on are
   already in place, and it gets an early green build on v3.3.1.

**Phase 2 — `VicoScrollState`, which everything else reads.**
4. **S1A-06** (RE-APPLY, widening the three conversions to `public`) — this unblocks three later rows.
5. **S1A-07** (RE-APPLY the wrap; confirm the `scrollEnabled`-is-now-state change at `:385`).
6. **S1A-08a DELETE**, **S1A-08c RE-APPLY**. Same diff region, opposite verdicts — do them in one
   edit so the `private` → `internal` widening is not swept away with the heuristic.
   Defer **S1A-08b** (the `key` MOVE) to Phase 4; it needs the A→B→A parity test and nothing depends
   on it.

**Phase 3 — the provider and the irreducible host core. One item at a time.**
7. **S1A-01** (+ widen its internals to `public`), then **S1A-02**.
8. **H-1** — alone, with the three named regression cases (push-time domain, frame-late opening
   range, layer↔model pairing by position). This is the hardest item in the cluster.
9. **S1A-03** — immediately after H-1, since H-1's ordering is what makes it work.
10. **H-2** — the wrapper and the effect ported in place, including the four comment-recorded details
    and especially `settleEpoch++` *before* the no-movement early return.

**Checkpoint: prove parity here.** Everything above reproduces shipped behaviour on v3.3.1. Nothing
below is required for the migration. Do not start Phase 4 until the charts are visually at parity,
because every Phase 4 item makes a port defect and a redesign defect look identical.

**Phase 4 — the reductions, cheapest and loudest first.**
11. **S1A-10** `xBehindStartInset` → meApp. Twenty lines out of a file we then stop patching
    entirely; fails loudly if botched.
12. **S1A-05a + S1A-05b** — move the snap file to meApp as `ChartSnapFlingBehavior.kt`, delete
    `isSnapping`, and delete the `FEATURE-4` paragraph describing the suppression.
13. **S1A-09b** `getInterpolatedYValues` → meApp, after S1-D has moved `MonotoneInterpolator`.
14. **S1A-08b** — the `key` MOVE, gated on the A→B→A test.
15. **H-3** — the `Decoration`. Largest win, largest silent risk, and it depends on everything above;
    it goes last.

**Phase 5 — deferred, and explicitly not part of this migration.**
16. The `ranges` decorator parameter on `CartesianChartHost` (§2(b)), which would collapse H-1 + H-2
    from ~250 lines to ~6. Needs a spike on snapshot-state invalidation first.
17. Move H-2's `ScrollAwareRangeEffect` to meApp (free once 16 lands, or once S1A-01's members are
    public and the effect no longer needs the host).
18. Upstream PRs, in descending value-per-line: `getVisibleXRange` public (S2-D **S2**),
    `VicoScrollState` conversions public (**S6**), `NeutralYRange` (**S4**),
    `flingBehavior` parameter (**S7**), fading edges over unscalable padding (**S9**),
    and the ranges-invalidation ask (**S1**).

**Cross-cluster dependencies this cluster cannot land without.** H-7 (two-pass prepare) reads
S1A-08c. S1-D's `yTransform` reads S1A-04, S1A-12 and S1A-13, and owns the `drawingModel` cache
skip (`LineCartesianLayer.kt:612-614`) that S1A-01 needs to be correct per-frame. M-1/M-2 own the
four `isScrollFrozen` writes S1A-07 depends on. H-8/M-3 own the `flingBehavior` seam S1A-05a
depends on. None of these are optional.

---

## 5. UNRESOLVED

Three items. None blocks the port; each is recorded with what would settle it.

**U1 — The `ranges` decorator seam (§2(b), Phase 5 item 16).**
I established that `CartesianChartHostImpl` already takes `ranges: CartesianChartRanges`
(`CartesianChartHost.kt:211`) and that no public overload exposes it, so the ~6-line parameter is
*mechanically* available. I did **not** establish that a `CartesianChartRanges` whose values are
backed by snapshot state would invalidate the draw pass the way our current
host-state-plus-recomposition path does — `rememberCartesianMeasuringContext` keys on the ranges
object, and a stable identity with changing values is a different invalidation story.
*What would settle it:* a spike that supplies a decorator backed by `mutableFloatStateOf` and
confirms the axis re-letters on every animation frame. Until then H-1/H-2 are RE-DERIVE as written.

**U2 — `key()` vs `rememberSaveable` input, for S1A-08b.**
`key(…) { rememberVicoScrollState(…) }` changes `currentCompositeKeyHash` and gives a fresh
`remember` slot, so it should be equivalent to our `key` input. The case I could not settle from the
repo is the *return*: our input-based key discards the saved value when the key changes, whereas the
wrapper relies on the inner `rememberSaveable` unregistering as it leaves composition. If it does
not, returning to a previously-visited segment would restore that segment's old scroll instead of
re-applying `initialScroll`.
*What would settle it:* the A→B→A segment-switch test named in §3. If it fails, the verdict reverts
to RE-APPLY, re-sited to the end of v3.3.1's new `rememberSaveable` input list (`:348-354`).

**U3 — `maxLabelWidth = 0f` in `getVisibleAxisLabels` (carried forward from S1-A).**
`getVisibleAxisLabels` calls `ItemPlacer.getLabelValues(context, visibleXRange, fullXRange, 0f)`.
The signature is unchanged at v3.3.1 (`HorizontalAxis.kt:840-845`), so this compiles — but whether
an upstream item placer reads `0f` as "unconstrained" or as "no room" is still unestablished.
meApp's own `HorizontalItemPlacer` handles it, so the exposure is limited to anyone passing an
upstream placer.
*What would settle it:* pass `ItemPlacer.aligned()` to `getVisibleAxisLabels` on a test chart and
compare the returned list against the labels the axis actually drew.

Two further items in the cluster are **not** unresolved but are explicitly "preserve, do not
rationalise": the `500f` drag/fling velocity threshold and the `4.5f` decay divisor in
`ChartSnapFlingBehavior` (`SnapFlingBehavior.kt:78, 85`). Neither has a derivation anywhere in the
repository. Port them verbatim.
