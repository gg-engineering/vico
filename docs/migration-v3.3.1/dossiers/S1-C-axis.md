# S1-C — Axes and layer margins

Cluster dossier for the vico fork migration to v3.3.1.
Refs: FORK_POINT `e67e49520` · OURS `HEAD` (`MOB-2953-week-range-spans-eight-days`, published 4.0.46).
Read-only reconstruction from `git diff e67e49520..HEAD` plus the files at HEAD. No v3.3.1 evaluation here.

## Files in scope (ours, against FORK_POINT)

| File | +/- | Upstream churn (per BASELINE) |
|---|---|---|
| `cartesian/axis/HorizontalAxis.kt` | 152 / 149 | 277+ / 77− |
| `cartesian/axis/VerticalAxis.kt` | 211 / 25 | 175+ / 29− |
| `cartesian/axis/HorizontalAxisItemPlacers.kt` | 26 / 4 | 71+ / 16− |
| `cartesian/axis/ListItemPlacer.kt` | 118 / 0 (NEW) | n/a |
| `cartesian/axis/BaseAxis.kt` | 13 / 0 | 31+ / 0− |
| `cartesian/axis/AxisManager.kt` | 19 / 0 | untouched |
| `cartesian/layer/CartesianLayerMargins.kt` | 16 / 2 | untouched |

All paths are under `vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/`.

## Summary table

| id | name | files | conflict risk | undocumented | meApp-visible |
|---|---|---|---|---|---|
| A1 | `HorizontalAxis.Separators` — period-boundary lines | HorizontalAxis.kt | **high** | partly (FEATURE-6 covers v1; the edge-exclusion rework is not) | yes (`HorizontalAxis.Separators(values, line)`) |
| A2 | Label positioning params (`LabelVerticalMode`, `labelHorizontalPosition`) | HorizontalAxis.kt | medium | no (FEATURE-6) | yes (both set on every bottom axis) |
| A3 | v3 clip / padding parity on `HorizontalAxis` | HorizontalAxis.kt | **high** | no (FEATURE-9) | indirect (label placement, first/last label visibility) |
| A4 | Half-open visible window + labels clipped to the plot | HorizontalAxisItemPlacers.kt, HorizontalAxis.kt | **high** | **yes** (MOB-2953) | yes (`visibleLabelsCount = 7.0` means 7) |
| A5 | No guideline at the chart's own first/last entry (`isChartEdge`) | HorizontalAxis.kt | medium | **yes** (MOB-2953) | indirect (no doubled line on the start axis) |
| A6 | `VerticalAxis.MarkerDecoration` — the goal pill | VerticalAxis.kt, AxisManager.kt | **high** | partly (FEATURE-6 covers v1; the unclipped pass, the Dp offset and the gutter sizing are not) | yes (`rememberGoalMarker`) |
| A7 | `BaseAxis.Size.Scroll` + scrolling vertical axis | BaseAxis.kt, VerticalAxis.kt | medium | no (FEATURE-9) | yes (`Size.Scroll(YGutterWidth)` on the end axis) |
| A8 | Chart-anchored axis line (`chartEdgeX`, `drawLineOnly`, `drawStartAxisLine`) | VerticalAxis.kt, AxisManager.kt | **high** | **yes** (MOB-2953) | yes (start axis is `Size.Fixed(0.dp)` + a line) |
| A9 | Vertical-axis labels centred in the gutter | VerticalAxis.kt | medium | **yes** (no FEATURE doc) | yes (50dp gutter, labels centred) |
| A10 | NaN guards on a zero-length y range | VerticalAxis.kt | low | **yes** (no FEATURE doc) | indirect (crash fix) |
| A11 | `ListItemPlacer` (new file) | ListItemPlacer.kt | low (new file) | no (FEATURE-1) | yes (`ListItemPlacer(ticksProvider)`) |
| A12 | `CartesianLayerMargins` halving + `ensureVerticalValuesAtLeast` | CartesianLayerMargins.kt | low to merge, **high** semantically | partly (FEATURE-10 covers the halving; the second method is MOB-2953) | indirect (chart height, marker room) |

Commit set (`git log --oneline e67e49520..HEAD -- <cluster files>`, oldest first):

```
e0b886359 Feature-1: Add ScrollAwareRangeProvider for dynamic Y-axis range on scroll
6a2af9fd0 Feature-6: Add MarkerDecoration on VerticalAxis + Separators on HorizontalAxis
d39964206 Feature-9: Add Size.Scroll for scrollable vertical axis + point clip fix
776fa00f2 Feature-9: Match v3 HorizontalAxis — no label padding, v3-style clip
57ce985ea perf: Eliminate per-frame allocations and dead code
57274083d fix: NaN crash guard, remove pointSpacingProvider, scrub LazyColumn fix
38d53e145 fix: Center axis labels, NaN guard, remove snap skip for range updates
1a08d48a6 feat: Add label positioning params to HorizontalAxis (v3 parity)
14542561e feat(chart-layout): Restore v3 chart area height parity
eaefd2bdf feat(marker-decoration): Fix outside-range positioning + remove target range
a2a9a7690 fix(axis): Size.Scroll works without scrollEnabled gate
fc2435259 MOB-2953: Close the week window at its own edge, not the next one's
9a7799e00 MOB-2953: Give a chart a start, by not drawing before it
b323662e6 MOB-2953: One source for the space before a chart's first entry
91e72bf64 MOB-2953: Put the data on top of what describes it
1bf0a6d9a MOB-2953: let the goal pill float free of the chart's clip and layer
2433daf0d MOB-2953: give the marker a resting label and make its gap a parameter
```

Five of those carry `MOB-2953:` and none carry `MOB-3494:` — the MOB-3494 work (`e6c766cb8 keep the axis and a transformed line in step`, `ac9d19175 give the chart's start inset a length, not a fraction of a step`) lands in `CartesianChartHost.kt`, `CartesianChart.kt`, `LineCartesianLayer.kt` and `ScrollAwareRangeProvider.kt`, which belong to other clusters. This cluster is on the receiving end of those: see "Cross-cluster dependencies".

---

## A1 — `HorizontalAxis.Separators`

**What it does.** Adds an optional `Separators` object to a horizontal axis: a plain `List<Double>` of x data values and a `LineComponent`. On each draw the axis maps every value to a canvas x and draws a full-height vertical line across `layerBounds`, skipping any that fall outside the plot. It is drawn after the guidelines and before the layers.

**Why we needed it.** The weight dashboard draws week and month boundaries behind the data (`GraphView.chartSeparators` → `GraphUtil.periodStarts`), so a MONTH view shows where each week begins and a YEAR view where each month begins. These are not axis ticks — they do not come from the item placer, they do not get labels, and their style is independent of the axis line.

**Commits.**
- `6a2af9fd0` Feature-6: Add MarkerDecoration on VerticalAxis + Separators on HorizontalAxis
- `fc2435259` MOB-2953: Close the week window at its own edge, not the next one's (trailing-edge exclusion + epsilon)
- `91e72bf64` MOB-2953: Put the data on top of what describes it (moved `SEPARATOR_EDGE_EPSILON_PX` above the class KDoc; stacking order)

**Files and hunks.**
- `HorizontalAxis.kt:46-47` — file-private constant:
  ```kotlin
  /** A stroke's worth of tolerance, so a separator on the closing edge is not drawn. */
  private const val SEPARATOR_EDGE_EPSILON_PX = 1f
  ```
- `HorizontalAxis.kt:72` — new primary-constructor property `public val separators: Separators? = null`.
- `HorizontalAxis.kt:252` — call site, last thing in `drawUnderLayers`, after `drawGuidelines`.
- `HorizontalAxis.kt:256-282` — `drawSeparators`:
  ```kotlin
  if (canvasX < layerBounds.left || canvasX >= layerBounds.right - SEPARATOR_EDGE_EPSILON_PX) {
    return@forEach
  }
  sep.line.drawVertical(context, canvasX, layerBounds.top, layerBounds.bottom)
  ```
- `HorizontalAxis.kt:659-669` (class at `:666`) — the `Separators` data class.
- `HorizontalAxis.kt:812, 842` — `separators` parameter added to `rememberTop` and `rememberBottom` and to their `remember` key lists.

**Public API added or changed.**
```kotlin
public data class Separators(
  public val values: List<Double>,
  public val line: LineComponent,
)

// HorizontalAxis primary constructor (protected), new trailing parameter:
public val separators: Separators? = null

// Companion:
@Composable public fun rememberTop(
  /* …existing params… */,
  separators: Separators? = null,
): HorizontalAxis<Axis.Position.Horizontal.Top>

@Composable public fun rememberBottom(
  /* …existing params… */,
  separators: Separators? = null,
  labelVerticalMode: LabelVerticalMode = LabelVerticalMode.Center,
  labelHorizontalPosition: Position.Horizontal = Position.Horizontal.Center,
): HorizontalAxis<Axis.Position.Horizontal.Bottom>
```
Note: `separators` is **not** included in `HorizontalAxis.equals`/`hashCode` (those still compare `itemPlacer` only, `HorizontalAxis.kt:655-657`). Two axes differing only in separators compare equal. Uncertain whether that is deliberate; it is masked in practice because `remember` keys on `separators` so a new instance is built anyway.

**Coupling to upstream internals.**
- Reads `CartesianDrawingContext.layerBounds`, `layerDimensions.xSpacing`, `ranges.minX`, `ranges.xStep`, `layoutDirectionMultiplier`.
- Takes `baseCanvasX` from `drawUnderLayers`, which is computed as `bounds.getStart(isLtr) - scroll + layerDimensions.startPadding * layoutDirectionMultiplier` (`HorizontalAxis.kt:202-203`) — i.e. it depends on the exact x-projection formula upstream uses inside the axis.
- Draw-order coupling: it must run after `drawGuidelines` and before the layer bitmap is composited. That ordering is enforced in `CartesianChart.draw` (another cluster), not here.
- Deliberately does **not** use a canvas clip, which is why the trailing edge needs the 1px epsilon.

**Could it be an extension point, or live in meApp instead?** Yes — this is the cleanest candidate in the cluster to leave the fork. A separator is a vertical rule at an x value spanning `layerBounds`; that is exactly what upstream's `Decoration` abstraction is for (`decorations.forEach { it.drawUnderLayers(context) }` in `CartesianChart.draw`). Two obstacles to check against v3.3.1: (a) a decoration would need the same `baseCanvasX` projection, which means it needs `layerDimensions.startPadding`, `scroll` and `bounds.getStart(isLtr)` — if `CartesianDrawingContext` exposes those publicly the decoration can live in meApp verbatim; (b) stacking — our order puts separators under the axis line but above the guidelines, and a decoration drawn in `drawUnderLayers` lands under both. If the visual difference is acceptable, this adaptation can be deleted from the fork entirely and re-implemented in `meApp/features/common/components/chart/`. Recommend costing that first; it removes the largest additive block from the hottest file.

**Conflict risk: high** — not because the code is complicated, but because it lives inside `drawUnderLayers`, the method upstream rewrote most heavily (277 added lines across the file), and it depends on a local `baseCanvasX` whose definition upstream may have moved.

---

## A2 — Label positioning params on `HorizontalAxis`

**What it does.** Restores two degrees of freedom that v3's single `horizontalLabelPosition` enum used to give: `labelVerticalMode` chooses how far the label sits from the axis line (at the line, at the tick midpoint, or below the full tick), and `labelHorizontalPosition` chooses how the label aligns horizontally against its tick x.

**Why we needed it.** meApp draws its bottom-axis labels tucked against the axis line and right-aligned to the tick, so a date label sits immediately to the left of the separator it belongs to (`Horizontalxis.kt:82-83, 110-111` — `LabelVerticalMode.Inside`, `Position.Horizontal.End`). Upstream v4 hard-codes "centred on the tick, offset by half the tick length", which put the labels in the middle of the band and centred them over the boundary line.

**Commits.** `1a08d48a6` feat: Add label positioning params to HorizontalAxis (v3 parity).

**Files and hunks.**
- `HorizontalAxis.kt:73-76` — two new constructor properties with inline KDoc.
- `HorizontalAxis.kt:165-174` — replaces upstream's fixed `textY`:
  ```kotlin
  val tickOffset = when (labelVerticalMode) {
    LabelVerticalMode.Inside -> 0f
    LabelVerticalMode.Center -> outwardTickLength / 2
    LabelVerticalMode.Outside -> outwardTickLength
  }
  val textY = if (isTop) bounds.bottom - lineThickness - tickOffset
              else bounds.top + lineThickness + tickOffset
  ```
- `HorizontalAxis.kt:222` — `horizontalPosition = labelHorizontalPosition` passed to `label.draw`.
- `HorizontalAxis.kt:565-567` — in `getHeight`, `lineThickness` is only added for a Bottom axis.
- `HorizontalAxis.kt:853-869` (enum at `:861`) — the `LabelVerticalMode` enum.

**Public API added or changed.**
```kotlin
public enum class LabelVerticalMode { Inside, Center, Outside }

// HorizontalAxis primary constructor (protected):
public val labelVerticalMode: LabelVerticalMode = LabelVerticalMode.Center
public val labelHorizontalPosition: Position.Horizontal = Position.Horizontal.Center

// rememberBottom gains both (see A1 for the full signature).
// rememberTop does NOT — asymmetry, see below.
```
`rememberTop` was given `separators` but not the two label parameters (`HorizontalAxis.kt:812` vs `842-844`). A top axis therefore cannot be label-positioned from Compose. Probably an oversight rather than a decision; meApp's `topAxis()` passes `label = null`, so nothing has needed it.

**Coupling to upstream internals.**
- `tickOffset` consumes `outwardTickLength`, which is derived from upstream's `TickPosition` enum in the same method.
- `labelHorizontalPosition` is forwarded to `TextComponent.draw(horizontalPosition = …)` — a public upstream parameter.
- The `getHeight` change reaches into upstream's `Size.Auto` height formula and makes it position-dependent. This is a behavioural change to a `protected open fun` that upstream may itself have rewritten.
- Defaults are chosen so that an untouched caller gets the upstream behaviour (`Center` + `Center` reproduces `outwardTickLength / 2` and a centred label), **except** for the `getHeight` change, which alters the measured height of every Top axis whether or not the new params are used.

**Could it be an extension point, or live in meApp instead?** The label-draw parts could be, if upstream exposes a label-placement hook; neither parameter needs private state. A cheaper non-patch is to override `drawUnderLayers` in a meApp subclass of `HorizontalAxis` — but `drawUnderLayers` is `override fun` on a public open class, so meApp could subclass and re-implement the label loop. That trades a fork patch for a full copy of the method in meApp, which is worse on every future upstream fix to that method. My read: keep this in the fork but push upstream's own `Position.Horizontal` through rather than inventing `LabelVerticalMode` — check whether v3.3.1 reintroduced anything equivalent before re-applying. The `getHeight` Bottom-only `lineThickness` is a separate decision from the two params and should be re-litigated on its own.

**Conflict risk: medium** — three small, well-localised hunks, but all three sit in methods upstream churned, and the `getHeight` hunk is a semantic change inside an expression upstream is likely to have reformatted.

---

## A3 — v3 clip and padding parity on `HorizontalAxis`

**What it does.** Three unrelated-looking reversions that together restore the v3 horizontal-axis geometry: (1) `updateLayerDimensions` becomes a no-op, so the axis no longer reserves unscalable start/end padding for its first and last labels; (2) the `Path`-based clip in both draw methods becomes a plain `canvas.clipRect`, bounded by the item placer's layer margins rather than by the vertical axes' line positions; (3) labels are drawn with no `maxWidth`, so they are never squeezed by the spacing between neighbours.

**Why we needed it.** meApp's charts were measured by v3 for years. Upstream v4's `updateLayerDimensions` shifts the whole plot right by half the first label's width, which moved the first data point off the start axis and made the plot narrower than the design. The `maxWidth` constraint computed `ceil(min(x - previousX, nextX - x) / xStep * xSpacing)`, which evaluates to 0 at a boundary value and silently blanked the first and last labels. The `getLineLeft`/`getLineRight` clip tied the horizontal axis's drawable region to wherever the vertical axes' lines happened to be, which breaks once the vertical axis has zero-width bounds (see A8).

**Commits.**
- `776fa00f2` Feature-9: Match v3 HorizontalAxis — no label padding, v3-style clip (118 changed lines in this file alone — 34+/88−)
- `d39964206` Feature-9: Add Size.Scroll … (adds the `is Size.Scroll` branch to `getHeight`)
- `57ce985ea` perf: Eliminate per-frame allocations and dead code

**Files and hunks.**
- `HorizontalAxis.kt:507-512` — the whole ~50-line body replaced:
  ```kotlin
  // v3 computed unscalableStartPadding/unscalableEndPadding here but never applied them.
  // Matching v3: no-op. No label padding shift.
  override fun updateLayerDimensions(
    context: CartesianMeasuringContext,
    layerDimensions: MutableCartesianLayerDimensions,
  ): Unit = Unit
  ```
- `HorizontalAxis.kt:178-200` — `drawUnderLayers` clip, now `canvas.clipRect(...)` on `startMargin`/`endMargin` (see A4 for the `layerBounds` clamp that `fc2435259` later added).
- `HorizontalAxis.kt:314-332` — `drawLineAndTicks` clip and line extents, now `clipLeft`/`clipRight` from the item placer's margins; `line.drawHorizontal(left = clipLeft, right = clipRight)`.
- `HorizontalAxis.kt:210-227` — the label loop: `previousX`, `nextX` and `maxWidth` computed per label per frame are gone, and `maxWidth` is no longer passed to `label.draw`.
- `HorizontalAxis.kt:573` — `is Size.Scroll -> size.value.pixels` added to `getHeight`'s `when`.

**Public API added or changed.** None directly. Behavioural change to two `override fun`s and one `protected open fun getHeight`.

**Dead code this leaves behind in the file** (re-applying agent should decide whether to carry it):
- `private val clipPath = Path()` (`HorizontalAxis.kt:99`) — no longer used.
- `getLineStart`, `getLineEnd`, `getLineLeft`, `getLineRight` (`HorizontalAxis.kt:355-425`, ~70 lines) — no longer called. `axisDimensions` is now an unused parameter of `drawLineAndTicks`/`drawUnderLayers`.
- `import androidx.compose.ui.graphics.Path` and `import androidx.compose.ui.geometry.Rect` are now unused; `kotlin.math.ceil` is unused after the `maxWidth` removal.
- `val clipLeft` at `HorizontalAxis.kt:208-209` in `drawUnderLayers` is computed and never read — a leftover from the `fc2435259` rework. Harmless but it recomputes an item-placer call per frame.
- `ItemPlacer.getFirstLabelValue` / `getLastLabelValue` (`HorizontalAxis.kt:686, 695`) are now called from nowhere in the module; they exist only to satisfy the interface.

**Coupling to upstream internals.**
- Deletes, rather than adapts, an upstream `override`. If v3.3.1 made `updateLayerDimensions` load-bearing for something new (e.g. a new padding source), the no-op silently disables it. This is the single highest-value thing to re-derive rather than re-apply.
- Depends on `ItemPlacer.getStartLayerMargin` / `getEndLayerMargin` keeping their four-argument shape `(context, layerDimensions, tickThickness, maxLabelWidth)`.
- Depends on `MutableCartesianLayerDimensions.ensureValuesAtLeast(unscalableStartPadding = …)` still existing for other callers — our `CartesianChart.startInsetDp` now uses it (`CartesianChart.kt:299`).
- Reads `context.tickThickness`, `context.zoomEnabled` (the latter only in the deleted code).

**Could it be an extension point, or live in meApp instead?** No, not as written: it is a removal of upstream behaviour, and nothing in meApp can un-reserve padding the library reserves. The realistic non-patch is the opposite direction — accept upstream's padding and have meApp stop compensating for it. That is a product-visible change (plot width and first-point position) and would need design sign-off, but it would delete the single largest hunk in the hottest file. Worth putting to the team explicitly.

**Conflict risk: high** — a 50-line deletion inside a method upstream has had two releases to rewrite, plus two clip rewrites in the two draw methods. Expect `git` to hand this back as a whole-method conflict.

---

## A4 — Half-open visible window, and labels clipped to the plot

**What it does.** Treats the visible x range as half-open at its end: a label or line value landing exactly on `visibleXRange.endInclusive` is dropped, because it is the opening boundary of the *next* window rather than a member of this one. Separately, the horizontal axis's label clip is clamped so labels can no longer overhang `layerBounds`, and separators exclude the trailing edge by a 1px epsilon.

**Why we needed it.** This is the MOB-2953 headline bug: the My Weight dashboard's WEEK period drew **eight** days — Sunday through Saturday, plus a partial second "su" — and the eighth day's entry was counted into the week average. The window genuinely has to span seven whole intervals (a week runs Sun 00:00 up to the next Sun 00:00, and shortening it would drop Saturday's own readings), but seven intervals have eight boundaries and the eighth sat inside the window and drew itself. The app had been working around it by tuning `visibleLabelsCount` to a fraction below a whole step, which is why no value there ever looked principled. After this change `visibleLabelsCount = 7.0` means seven.

**Commits.** `fc2435259` MOB-2953: Close the week window at its own edge, not the next one's. **UNDOCUMENTED** — no FEATURE doc covers it.

**Files and hunks.**
- `HorizontalAxisItemPlacers.kt:53-67, 82` — in the private `CartesianDrawingContext.getLabelValues`:
  ```kotlin
  val endEpsilon = ranges.xStep / 1000
  …
  val closesTheWindow = abs(potentialValue - visibleXRange.endInclusive) < endEpsilon
  if (!closesTheWindow) values += potentialValue
  ```
  The loop still *continues* past the closing boundary (the `hasEndOverflow` logic is untouched), so the deliberate one-value overflow that keeps scrolling continuous still works; only the emission is suppressed.
- `HorizontalAxisItemPlacers.kt:199-213` — the same rule in `SegmentedHorizontalAxisItemPlacer.getLineValues`, which is a separate loop that appends *before* testing whether it has passed the end:
  ```kotlin
  if (abs(potentialValue - visibleXRange.endInclusive) >= endEpsilon) values += potentialValue
  ```
- `HorizontalAxis.kt:178-200` — labels clamped to the plot:
  ```kotlin
  canvas.clipRect(
    maxOf(bounds.left - startMargin, layerBounds.left),
    min(bounds.top, layerBounds.top),
    minOf(bounds.right + endMargin, layerBounds.right),
    max(bounds.bottom, layerBounds.bottom),
  )
  ```
  with `startMargin`/`endMargin` each `maxOf(itemPlacer.get*LayerMargin(...), maxLabelWidth.half)`. The margins still apply vertically and are still what the layer reserves; horizontally they are now bounded by `layerBounds`. The reasoning, quoted from the source comment: guidelines are clipped strictly to `layerBounds`, so a boundary at the edge used to draw a readable label with no line beneath it. Clipping rather than skipping also means a label sliding in under a scroll is revealed progressively instead of appearing whole once its centre crosses over.
- `HorizontalAxis.kt:276` — separators use exclusion rather than clipping, because clipping cannot help a hairline (half a stroke is still a visible sliver).

**Public API added or changed.** None. But the **meaning** of `visibleLabelsCount` changes, which is a contract change for consumers: it now counts labels, not boundaries.

**Coupling to upstream internals.**
- Edits the body of a module-private helper (`private fun CartesianDrawingContext.getLabelValues`) that both `AlignedHorizontalAxisItemPlacer` and `SegmentedHorizontalAxisItemPlacer` route through, plus one `internal class`'s override. Nothing public is touched; the coupling is entirely to the shape of two `while (true)` loops.
- The epsilon `ranges.xStep / 1000` assumes `xStep` is meaningful and non-zero. For meApp it is milliseconds-per-day, so the tolerance is ~86 seconds — comfortably below any real boundary spacing and comfortably above double-rounding error. For a chart with a tiny `xStep` this tolerance shrinks proportionally, which is the right behaviour.
- After `776fa00f2` the `fullXRange` parameter of the private `getLabelValues` is unused (`HorizontalAxisItemPlacers.kt:43`), though callers still pass it.

**Could it be an extension point, or live in meApp instead?** Partly. meApp already supplies its own `HorizontalAxis.ItemPlacer` by delegation for YEAR and MONTH (`HorizontalItemPlacer.kt:40-97`), so the half-open rule could be applied in meApp's wrapper for those two segments. It cannot for WEEK and DAY, which fall through to `defaultPlacer.getLabelValues` — and WEEK is exactly the segment the bug was about. meApp could stop delegating and filter the default placer's output (`defaultPlacer.getLabelValues(...).filterNot { closesTheWindow(it) }`), which would need `visibleXRange` and `ranges.xStep`, both of which it already has in that override. **This is a genuine candidate for moving out of the fork**, and it would also take the `SegmentedHorizontalAxisItemPlacer` hunk with it if meApp overrides `getLineValues` the same way (it already does). The label-clip hunk in `HorizontalAxis.kt` cannot move and would stay.

**Conflict risk: high** — `HorizontalAxisItemPlacers.kt` has 71 upstream added lines against our 26, and ours sit inside the exact loop upstream is most likely to have touched. The `HorizontalAxis.kt` clip hunk conflicts with A3's clip hunk and with upstream's rewrite of the same lines.

---

## A5 — No guideline at the chart's own first and last entry

**What it does.** Suppresses the vertical guideline at the x values that are the chart's first and last data entries, in addition to upstream's existing suppression at the bounds of `fullXRange`. The test uses a tolerance rather than equality.

**Why we needed it.** Upstream's `x.isBoundOf(fullXRange)` used to cover this case by accident: `fullXRange` began at the first entry, so the first entry was a bound and its guideline was suppressed. Once the chart started reserving space *before* the first entry (`startInsetDp`, see Cross-cluster dependencies), `fullXRange.start` moved earlier, the first entry stopped being a bound, and its guideline came back — drawing a second line directly on top of the start axis line.

**Commits.** `b323662e6` MOB-2953: One source for the space before a chart's first entry. **UNDOCUMENTED.**

**Files and hunks.**
- `HorizontalAxis.kt:427-437`:
  ```kotlin
  private fun CartesianDrawingContext.isChartEdge(x: Double): Boolean {
    val tolerance = ranges.xStep / 1000
    return abs(x - ranges.minX) < tolerance || abs(x - ranges.maxX) < tolerance
  }
  ```
  The KDoc explains the tolerance: a label value is rebuilt by rounding to the nearest step before use, so the value at the first entry is not bit-identical to `ranges.minX` and an exact test passes straight over it.
- `HorizontalAxis.kt:468` and `:481` — both branches of `drawGuidelines`:
  ```kotlin
  .takeUnless { x.isBoundOf(fullXRange) || isChartEdge(x) }
  ```
- `HorizontalAxis.kt:451-458` — an 8-line comment block above the `if (lineValues == null)` explaining the above.

**Public API added or changed.** None. `drawGuidelines` remains `protected open fun` with the same signature.

**Coupling to upstream internals.**
- `ranges.minX` / `ranges.maxX` / `ranges.xStep` on `CartesianChartRanges` — public.
- `isBoundOf` — an upstream extension this fork keeps using alongside the new test; if upstream removed or renamed it the hunk needs rewording but not rethinking.
- Semantic coupling to A8 and to `CartesianChart.startInsetDp`: this hunk exists **only** because the start inset changed what `fullXRange.start` means. If the start inset is reworked during migration, re-derive this rather than re-applying it.

**Could it be an extension point, or live in meApp instead?** meApp cannot suppress a guideline the library draws, short of returning fewer line values from its item placer — and it cannot do that without also losing the label at that x, which it wants. So: no, this stays in the fork. It is, however, three lines plus a helper, and is the kind of thing worth proposing upstream as a `guidelineAtChartEdges: Boolean` or similar.

**Conflict risk: medium** — small and additive, but `drawGuidelines` is in the hot file and the surrounding `canvasX` arithmetic is exactly what upstream reworks.

---

## A6 — `VerticalAxis.MarkerDecoration` (the goal pill)

**What it does.** Adds a decoration pinned to a y data value on a vertical axis: a `TextComponent` pill drawn centred in the axis's gutter at that value's canvas y. When the value falls outside the visible y range the pill is parked just below the plot (`bounds.bottom + outsideRangeOffset`) or just above it (`bounds.top - outsideRangeOffset`) rather than being clamped or hidden. It is drawn in its own pass, outside the chart's clip and outside the fading-edge layer, and the axis's gutter width is widened to fit it.

**Why we needed it.** The weight dashboard shows the user's goal weight as a green pill on the right-hand axis (`GraphDecoration.rememberGoalMarker`). It must stay visible when the goal is far outside the plotted range — that is precisely when the user most wants to see how far away it is.

**Commits.**
- `6a2af9fd0` Feature-6: Add MarkerDecoration on VerticalAxis + Separators on HorizontalAxis (v1, drawn inside `drawOverLayers`)
- `eaefd2bdf` feat(marker-decoration): Fix outside-range positioning + remove target range (out-of-range sides were inverted; default offset `0f` → `60f`)
- `1bf0a6d9a` MOB-2953: let the goal pill float free of the chart's clip and layer (**UNDOCUMENTED** — own draw pass, `Dp` offset, reservation removed)
- `91e72bf64` MOB-2953: Put the data on top of what describes it (stacking order)
- `2433daf0d` MOB-2953: give the marker a resting label and make its gap a parameter (added `ensureVerticalValuesAtLeast`, see A12)

**Files and hunks.**
- `VerticalAxis.kt:76` — `public val markerDecoration: MarkerDecoration? = null` on the primary constructor.
- `VerticalAxis.kt:265-296` — `drawMarkerDecoration`, extracted out of `drawOverLayers`. Position:
  ```kotlin
  val canvasY = if (yRange.length > 0 && markerY in yRange.minY..yRange.maxY) {
    bounds.bottom - bounds.height * ((markerY - yRange.minY) / yRange.length).toFloat()
  } else if (markerY < yRange.minY) {
    bounds.bottom + decoration.outsideRangeOffset.pixels
  } else {
    bounds.top - decoration.outsideRangeOffset.pixels
  }
  val markerX = bounds.center.x
  ```
  Note it uses the *animated* `yRange` read from `ranges`, so the pill glides with the y-axis animation.
- `VerticalAxis.kt:527-544` — `getMarkerDecorationWidth()`, and `:554-556` — `getWidth` becomes `max(markerWidth, when (size) { … })`. The KDoc's reason: the marker is centred on the gutter, so a pill wider than the gutter overhangs it by half the difference, and on an end axis that half runs off the canvas.
- `VerticalAxis.kt:669-684` — `markerDecoration` added to `equals` and `hashCode`.
- `VerticalAxis.kt:694-708` — the `MarkerDecoration` data class.
- `VerticalAxis.kt:631-668` — `markerDecoration` added to `copy`.
- `VerticalAxis.kt:830, 890` — `markerDecoration` added to `rememberStart` and `rememberEnd` and their `remember` key lists.
- `AxisManager.kt:147-150` — `fun drawMarkerDecorations(context)`, which calls it on both start and end axes.
- Call site (other cluster): `CartesianChart.kt:408`, after `fadingEdges?.run { draw(context); canvas.restore() }` and before `legend?.draw(context)`.

**Public API added or changed.**
```kotlin
public data class MarkerDecoration(
  public val y: (ExtraStore) -> Double,
  public val markerComponent: TextComponent? = null,
  public val label: (ExtraStore) -> CharSequence = { "" },
  public val verticalLabelPosition: Position.Vertical = Position.Vertical.Center,
  public val labelRotationDegrees: Float = 0f,
  public val outsideRangeOffset: Dp = 20.dp,
)

public open fun drawMarkerDecoration(context: CartesianDrawingContext)   // new public method on VerticalAxis

// VerticalAxis primary constructor (protected):
public val markerDecoration: MarkerDecoration? = null

public fun copy(/* …existing… */, markerDecoration: MarkerDecoration? = this.markerDecoration): VerticalAxis<P>

@Composable public fun rememberStart(/* …existing… */, markerDecoration: MarkerDecoration? = null): VerticalAxis<Axis.Position.Vertical.Start>
@Composable public fun rememberEnd(/* …existing… */, markerDecoration: MarkerDecoration? = null): VerticalAxis<Axis.Position.Vertical.End>

// internal, on AxisManager:
fun drawMarkerDecorations(context: CartesianDrawingContext)
```

**Coupling to upstream internals.**
- `ranges.getYRange(position)` and its `minY`/`maxY`/`length` — the *animated* range, not the model range. The pill's smooth motion depends on that being what `ranges` holds during draw.
- `bounds` — the axis's own reserved gutter rect, which upstream computes in the measuring pass. Both the x centre and the out-of-range parking points come from it.
- `getWidth` is a `protected open fun` whose result upstream feeds back into `bounds`; wrapping its `when` in a `max()` means the decoration participates in the measurement pass. This is a two-way coupling: measurement widens the gutter, draw centres on the gutter.
- **Draw-order coupling is the sharpest part.** The pill must be drawn (a) outside the start-strip clip in `CartesianChart.draw`, (b) *after* `canvas.restore()` of the fading-edge `saveLayer`, because that layer's bounds are the canvas and a pill parked below the plot was being sliced (the commit records a 63px pill at y=822 on an 825px canvas losing its bottom half), and (c) before the legend. None of that is expressible from inside `VerticalAxis`; it is why `AxisManager.drawMarkerDecorations` and the `CartesianChart` call site exist.
- `model.extraStore` is read per frame for both `y` and `label`.

**Could it be an extension point, or live in meApp instead?** This is the key migration question and my read is **yes, it should become a standalone decoration, not an axis patch.** Evidence: the thing has already been pulled out of the axis's draw pass twice (into `drawMarkerDecoration`, then out past the fading-edge layer), it is drawn at a canvas position the axis only supplies as a convenience (`bounds.center.x`), and it does not interact with ticks, labels, guidelines or the item placer. The one genuine tie to the axis is `getMarkerDecorationWidth` widening the gutter — and meApp already fixes its gutter at a constant (`YGutterWidth = 50.dp`, `Size.Scroll(YGutterWidth)`), so that sizing is currently doing nothing for the only consumer. A `Decoration` implementation in meApp would need: the animated y range for its position, the axis gutter's x centre, and a draw slot after the fading edges. The first two are the real blockers — check in v3.3.1 whether a decoration's draw context exposes the animated `ranges` and whether there is any post-fade draw slot. If both exist, delete `MarkerDecoration` from the fork entirely. Note that upstream touched `VerticalAxis` by 175 lines, so every line we avoid re-applying here is worth a lot.

Caveat on the "zero per-frame allocations" claim in FEATURE-6: `y` and `label` are lambdas invoked on every draw and `label` returns a `CharSequence`, so allocation depends entirely on what the consumer's lambda does. meApp's is `{ labelText }` over a `remember`ed string, so in practice it is allocation-free — but the property is the consumer's, not the design's.

**Conflict risk: high** — 211 added lines on our side against 175 upstream on the same file, spread across the constructor, `equals`/`hashCode`, `copy`, both `remember*` factories, `getWidth` and a new draw method. Every one of those is a place upstream also edits.

---

## A7 — `BaseAxis.Size.Scroll` and the scrolling vertical axis

**What it does.** Adds a fourth `Size` variant. `Size.Scroll(value, isLabelsScrollable)` sizes the axis like `Size.Fixed(value)` but changes three draw behaviours: guidelines are drawn unconditionally across the layer instead of being suppressed when they would collide with the axis band; the axis line and ticks are drawn inside an explicit clip to `layerBounds ± lineThickness`; and ticks are offset by the current scroll. With `isLabelsScrollable = true` the reserved horizontal layer margin additionally shrinks as the user scrolls, so the gutter disappears when scrolled away from.

**Why we needed it.** The v3 chart let the y-axis gutter be scrolled away so the plot could use the full width. The end axis in meApp uses `Size.Scroll(YGutterWidth)` (i.e. `isLabelsScrollable = false`), so what it actually gets today is the fixed 50dp width plus the unconditional-guideline behaviour — gridlines that run the full width of the plot rather than stopping short where the axis band begins.

**Commits.**
- `d39964206` Feature-9: Add Size.Scroll for scrollable vertical axis + point clip fix
- `a2a9a7690` fix(axis): Size.Scroll works without scrollEnabled gate (removed `context.scrollEnabled &&` from the margin condition, so `Size.Scroll` renders regardless of whether the chart itself scrolls)

**Files and hunks.**
- `BaseAxis.kt:207-220`:
  ```kotlin
  public class Scroll(public val value: Dp, public val isLabelsScrollable: Boolean = false) : Size() {
    override fun equals(other: Any?): Boolean =
      this === other || other is Scroll && value == other.value && isLabelsScrollable == other.isLabelsScrollable
    override fun hashCode(): Int = 31 * value.hashCode() + isLabelsScrollable.hashCode()
  }
  ```
- `VerticalAxis.kt:180-203` — the guideline branch in `drawUnderLayers`: under `Size.Scroll` the `isNotInRestrictedBounds(...)` check is skipped entirely.
- `VerticalAxis.kt:344-396` — `drawLineAndTicks`: `isScrollMode`, `effectiveScroll` (`-scroll` for a left axis, `getMaxScrollDistance() - scroll` for a right one), a `canvas.save()`/`clipRect`/`restore()` pair, and `effectiveScroll` added to the tick's `left`/`right`. Note the comment at `:366-367`: `effectiveScroll` is deliberately **not** applied to the line, because `chartEdgeX()` already carries the scroll and adding it twice put the line at neither edge. So `effectiveScroll` now only moves ticks.
- `VerticalAxis.kt:398-405` — scroll state pushed in from the host:
  ```kotlin
  private var scrollValue: Float = 0f
  private var maxScroll: Float = 0f
  internal fun updateScrollState(scroll: Float, maxScroll: Float) { … }
  ```
- `VerticalAxis.kt:483-499` — `updateHorizontalLayerMargins` shrinks the reserved width:
  ```kotlin
  val scrollOffset = if (scrollSize != null && scrollSize.isLabelsScrollable) {
    if (position == Axis.Position.Vertical.Start) scrollValue else maxScroll - scrollValue
  } else 0f
  val effectiveWidth = (width - scrollOffset).coerceAtLeast(0f)
  ```
- `VerticalAxis.kt:583` and `HorizontalAxis.kt:573` — `is Size.Scroll -> size.value.pixels` in both exhaustive `when`s.
- Call sites (other cluster): `CartesianChartHost.kt:469-470, 478-479`, which call `updateScrollState` on both axes before `prepare()`.

**Public API added or changed.**
```kotlin
public class Scroll(public val value: Dp, public val isLabelsScrollable: Boolean = false) : BaseAxis.Size()
// plus internal: fun VerticalAxis<*>.updateScrollState(scroll: Float, maxScroll: Float)
```

**Coupling to upstream internals.**
- **`BaseAxis.Size` is a `sealed class`** (`BaseAxis.kt:147`). Adding a subclass is source-compatible only because the only exhaustive `when (size)` expressions in the module are `HorizontalAxis.getHeight` and `VerticalAxis.getWidth`, both of which we edit. Any new `when (size)` upstream adds in v3.3.1 will fail to compile until a `Scroll` branch is added — that is a *useful* compile error and should be treated as a checklist, not a nuisance.
- `internal fun CartesianDrawingContext.getMaxScrollDistance()` (`CartesianDrawingContext.kt:52`) — an internal upstream helper, reachable only because we are in the same module.
- Mutable per-instance state (`scrollValue`, `maxScroll`) on what upstream treats as an immutable, `remember`ed, `equals`-comparable axis object. The axis is pushed state by the host between frames. This breaks the "axis is a value" assumption and is why `updateScrollState` has to be called twice in the host (lines 469 and 478) around `prepare()`.
- `isNotInRestrictedBounds` is `protected fun` on `BaseAxis` — we bypass rather than extend it.
- `scrollEnabled` on the measuring context was deliberately removed from the margin condition (`a2a9a7690`).

**Could it be an extension point, or live in meApp instead?** Partly. Given that meApp uses `isLabelsScrollable = false`, the only behaviour it actually consumes is "fixed width + guidelines not suppressed by the axis band". If v3.3.1 lets a guideline be drawn full-width some other way, meApp could switch to `Size.Fixed(50.dp)` and this adaptation shrinks to nothing. **Check that first**: the margin-shrinking half of `Size.Scroll`, the `scrollValue`/`maxScroll` mutable state, the two `CartesianChartHost` call sites and the `effectiveScroll` tick offset are all currently unexercised by the only consumer. If that holds, most of A7 is dead weight and should be dropped rather than ported. I have not verified every meApp surface — `SnapshotLineChart` and `ProductChart` both route through the shared `endAxis()`, which hard-codes `Size.Scroll(YGutterWidth)`, so I believe no caller sets `isLabelsScrollable = true`, but say so as a belief, not a fact.

**Conflict risk: medium** — `BaseAxis.kt` is purely additive against 31 upstream added lines (likely clean). The `VerticalAxis` half is medium-to-high because it is interleaved with A8 in the same method.

---

## A8 — Chart-anchored axis line

**What it does.** Moves a vertical axis's line from "the edge of the gutter the axis reserved" to "the x where the chart's data actually starts or ends", and makes the start axis's line drawable on its own, outside the clip that hides the strip before the chart's first entry.

**Why we needed it.** Two bugs, both from MOB-2953. First, `drawLineAndTicks` took its x from `bounds`, which is the gutter the axis reserves — and meApp's start axis reserves nothing (`Size.Fixed(0.dp)`), so it had zero-width bounds against the canvas edge and the line landed there with half of it off-screen. Second, once the chart imposed a start by *not drawing* before it, everything that describes the data correctly stopped at that edge — but the axis line is what *marks* the edge, so it had to be exempted and drawn anyway.

**Commits.**
- `9a7799e00` MOB-2953: Give a chart a start, by not drawing before it. **UNDOCUMENTED.**
- `91e72bf64` MOB-2953: Put the data on top of what describes it (moved the exempt line draw before the layers).

**Files and hunks.**
- `VerticalAxis.kt:322-342`:
  ```kotlin
  private fun CartesianDrawingContext.chartEdgeX(): Float {
    val firstEntryX = layerBounds.left + layerDimensions.startPadding - scroll
    return if (position.isLeft(this)) {
      firstEntryX
    } else {
      val contentWidth =
        ((ranges.maxX - ranges.minX) / ranges.xStep).toFloat() * layerDimensions.xSpacing
      firstEntryX + contentWidth + layerDimensions.endPadding
    }
  }
  ```
  The KDoc records why the end branch is not `layerBounds.right`: pinning it there showed an end line against the right edge wherever the scroll happened to be, marking an end that was not there.
- `VerticalAxis.kt:307-320` — `internal fun drawLineOnly(context)`, which draws only the line, at `chartEdgeX()`, from `layerBounds.top` to `layerBounds.bottom`.
- `VerticalAxis.kt:354, 368-372` — `drawLineAndTicks` now uses `x = lineX` (i.e. `chartEdgeX()`) in place of upstream's `bounds.right - lineThickness.half` / `bounds.left + lineThickness.half`.
- `AxisManager.kt:152-154`:
  ```kotlin
  fun drawStartAxisLine(context: CartesianDrawingContext) {
    (startAxis as? VerticalAxis<*>)?.drawLineOnly(context)
  }
  ```
  Only the start axis — the end axis already sits at the last entry and is drawn on the ordinary path, so including it drew a second end line beside the start whenever the strip was hidden.
- Call site (other cluster): `CartesianChart.kt:374`, `if (hidesStrip) axisManager.drawStartAxisLine(context)`, between `clippedToChart { axisManager.drawUnderLayers(context) }` and `clippedToChart { axisManager.drawOverLayers(context) }`.

**Note a documentation defect in `AxisManager.kt`:** the KDoc block at `AxisManager.kt:137-145` describes `drawStartAxisLine` ("Only this one. The end axis's line already sits at the last entry…") but sits immediately above `drawMarkerDecorations`, which has a one-line doc of its own at `:146`. The intended ordering was evidently lost when `1bf0a6d9a` inserted `drawMarkerDecorations`. Worth fixing while re-applying.

**Public API added or changed.**
```kotlin
// VerticalAxis, internal:
internal fun drawLineOnly(context: CartesianDrawingContext)
// AxisManager (internal class), new:
fun drawStartAxisLine(context: CartesianDrawingContext)
```
No public API. Behavioural change to a private method's geometry affects every vertical axis, including ones that do not opt in.

**Coupling to upstream internals.**
- `layerDimensions.startPadding` (= `scalableStartPadding + unscalableStartPadding`) and `layerDimensions.endPadding` — public getters on `CartesianLayerDimensions`, but their *meaning* is what A3 and `CartesianChart.startInsetDp` changed. `chartEdgeX` is only correct because `CartesianChart` reserves the start inset via `layerDimensions.ensureValuesAtLeast(unscalableStartPadding = startInsetDp.pixels)` and because `HorizontalAxis.updateLayerDimensions` no longer adds label padding on top (A3). **These three are one mechanism in three files.**
- `scroll`, `layerBounds`, `layerDimensions.xSpacing`, `ranges.minX/maxX/xStep` on the drawing context.
- `AxisManager.startAxis` / `endAxis` — internal fields of an internal class.
- The `hidesStrip` condition and the clip live in `CartesianChart.draw` (`CartesianChart.kt:349-361`), not here. This adaptation is unusable without that one.
- Reaches into draw order: the start line must be drawn *before* the layer bitmap is composited, so a point on the first entry and a marker placed on it pass over the edge they sit against.

**Could it be an extension point, or live in meApp instead?** No. It needs the layer dimensions and the scroll at draw time, and it has to be interleaved with the chart's own clip. The honest framing for the migration is that A8 + A5 + `CartesianChart.startInsetDp` + the `hidesStrip` clip are **one feature** ("a chart that scrolls without end but has a visible start"), which happens to be spread over three files and two clusters. Re-apply it as one unit or not at all; re-applying the pieces independently will produce a chart that is subtly wrong in a way that is hard to see.

**Conflict risk: high** — `drawLineAndTicks` is private and short, which is in our favour, but 175 upstream added lines on this file make a clean three-way merge of this method unlikely, and the correctness of the hunk depends on two other files' hunks landing with it.

---

## A9 — Vertical-axis labels centred in the gutter

**What it does.** Draws every vertical-axis label at the horizontal centre of the axis's own bounds, centre-aligned, instead of at the tick's left or right edge with an outward alignment derived from `horizontalLabelPosition`.

**Why we needed it.** meApp reserves a fixed 50dp gutter for the y labels on every surface (`YGutterWidth`, chosen to fit the longest label the axis prints) and the design centres the numbers in it. Upstream's rule pins the label to the tick and aligns it away from the plot, which with a zero-length tick and a wide gutter leaves the numbers hard against one side.

**Commits.** `38d53e145` fix: Center axis labels, NaN guard, remove snap skip for range updates. **UNDOCUMENTED** — no FEATURE doc mentions it.

**Files and hunks.**
- `VerticalAxis.kt:220` — in `drawOverLayers`:
  ```kotlin
  val labelX = bounds.center.x            // was: if (areLabelsOutsideAtStartOrInsideAtEnd == isLtr) tickLeftX else tickRightX
  ```
- `VerticalAxis.kt:450` — in `drawLabel`:
  ```kotlin
  horizontalPosition = Position.Horizontal.Center,   // was: textHorizontalPosition
  ```

**Public API added or changed.** None — but `VerticalAxis.HorizontalLabelPosition` (`Outside`/`Inside`) is now **inert for label placement**. It still influences the default `tickPosition` in `rememberStart`/`rememberEnd` and still participates in `equals`/`hashCode`, so it is not fully dead, but a caller setting it expecting the labels to move will see nothing happen. That is a silent public-API behaviour change and should be called out in the migration notes.

Dead code left behind: `protected val textHorizontalPosition` (`VerticalAxis.kt:97-103`) is now unreferenced, and `areLabelsOutsideAtStartOrInsideAtEnd` loses one of its two uses.

**Coupling to upstream internals.**
- `bounds` — the axis's reserved gutter. Centring is only sensible because the gutter is a fixed width; with `Size.Auto` the gutter is sized to the widest label and centring is a no-op.
- `drawLabel` is `protected open fun`; we change its body rather than its signature.

**Could it be an extension point, or live in meApp instead?** Yes, cleanly — this is two lines that hard-code one of upstream's own enum values. The minimal fork-shrinking move is to make it a parameter rather than a hard-code: either honour `Position.Horizontal.Center` through the existing `HorizontalLabelPosition` enum (add a `Center` case) or add a `labelHorizontalPosition: Position.Horizontal` to `VerticalAxis` mirroring what A2 added to `HorizontalAxis` — which would also make the two axes consistent. Failing that, meApp can subclass `VerticalAxis` and override `drawLabel`, since it is `protected open`. Recommend the parameter: it is a two-line patch either way, but a parameter does not silently disable `HorizontalLabelPosition` for every caller.

**Conflict risk: medium** — two one-line changes, but both inside methods upstream rewrote, and the second is inside a `protected open fun` whose signature upstream may have changed.

---

## A10 — NaN guards on a zero-length y range

**What it does.** Returns early from both vertical-axis draw passes when the y range has zero length, instead of dividing by it.

**Why we needed it.** `(lineValue - yRange.minY) / yRange.length` is `NaN` when `length == 0`, which happens on an empty chart, on a chart whose every entry has the same y, and on frame zero before a range provider has produced anything. The NaN propagates into a canvas y and crashes or blanks the draw.

**Commits.**
- `57274083d` fix: NaN crash guard, remove pointSpacingProvider, scrub LazyColumn fix
- `38d53e145` fix: Center axis labels, NaN guard, … (the second guard)

Both **UNDOCUMENTED**.

**Files and hunks.**
- `VerticalAxis.kt:173` — `if (yRange.length == 0.0) return@with // Guard: avoid NaN from division by zero` in `drawUnderLayers`, placed after the line values are fetched and before the loop.
- `VerticalAxis.kt:222` — `if (yRange.length == 0.0) return` in `drawOverLayers`.
- `VerticalAxis.kt:278-280` — `drawMarkerDecoration` makes the same test inline as `yRange.length > 0 && markerY in …`, falling through to the out-of-range placement rather than returning. That is deliberate: a goal pill on an empty chart should still be parked at an edge.

**Public API added or changed.** None.

**Coupling to upstream internals.** `ranges.getYRange(position).length` only. Exact `== 0.0` comparison on a `Double`, which is correct for the case being guarded (a range whose min and max are the same value produces exactly 0.0) but would not catch a denormal-small range. Low risk; noting it as an observation, not a defect.

**Could it be an extension point, or live in meApp instead?** No — it is a defensive guard inside library draw code. It is also the kind of fix upstream may have made independently; **check v3.3.1 before re-applying**, since a duplicated guard is harmless but a differently-placed one may indicate upstream restructured the division away.

**Conflict risk: low** — three lines, each adjacent to code upstream may have moved, but trivially re-derivable from scratch.

---

## A11 — `ListItemPlacer`

**What it does.** A new `VerticalAxis.ItemPlacer` that takes a `() -> List<Double>` of tick values and returns exactly those, filtered to the current y range with a small epsilon, with a one-entry cache keyed on the range plus the identity of the returned list. It implements the measurement-value methods by falling back to `listOf(minY, maxY)` when the filter is empty, and supplies its own top/bottom layer margins.

**Why we needed it.** The scroll-aware range provider (FEATURE-1, another cluster) computes the exact y ticks for the visible window. Without a placer that simply echoes them, the axis recomputes its own ticks from the range and the two disagree — gridlines in one set of places and labels from another. meApp wires it as `ListItemPlacer(ticksProvider)` whenever a scrolling chart supplies a provider (`VerticalAxis.kt:90-93` in meApp), and falls back to `VerticalAxis.ItemPlacer.step(...)` for static charts.

**Commits.** `e0b886359` Feature-1: Add ScrollAwareRangeProvider for dynamic Y-axis range on scroll.

**Files and hunks.** `ListItemPlacer.kt:1-118`, the whole file. Cache at `:39-55`; the margin methods at `:91-117` are copies of upstream's `step` placer arithmetic:
```kotlin
verticalLabelPosition == Position.Vertical.Center ->
  (max(maxLabelHeight, maxLineThickness) +
    if (shiftTopLines) maxLineThickness else -maxLineThickness).half
```

**Public API added or changed.**
```kotlin
public class ListItemPlacer(
  private val ticks: () -> List<Double>,
  private val shiftTopLines: Boolean = true,
) : VerticalAxis.ItemPlacer
```
Implements `getShiftTopLines`, `getLabelValues`, `getWidthMeasurementLabelValues`, `getHeightMeasurementLabelValues`, `getTopLayerMargin`, `getBottomLayerMargin`.

**Coupling to upstream internals.**
- Entirely through the public `VerticalAxis.ItemPlacer` interface — **the only file in this cluster with no reach into upstream internals.** Its whole risk is whether that interface's shape survived v3.3.1 (six methods, their parameter lists, and whether any gained a default body).
- Duplicates upstream's layer-margin arithmetic rather than delegating to it. If upstream changed that formula, our copy is now wrong in a way that will not produce a compile error — a silent drift. **Worth diffing our `getTopLayerMargin`/`getBottomLayerMargin` against v3.3.1's `step`/`count` placers as a specific migration check.**
- `EPSILON = 0.01` is an absolute tolerance in data units. For weight in lb/kg that is fine; it is unit-dependent and would be wrong for a chart plotting, say, fractions.
- The identity-based cache (`cachedTicksIdentity === currentTicks`) assumes the provider returns a stable list instance between frames. meApp's `ticksProvider` is `remember`ed, so this holds; a provider that rebuilds its list each call defeats the cache silently (correctness is unaffected, only the allocation saving).

**Could it be an extension point, or live in meApp instead?** **Yes — this should move to meApp.** It implements a public interface, uses nothing internal, and its only consumer is meApp. The one thing keeping it in the library is the duplicated margin arithmetic, which meApp would also have to copy — but copying it in meApp is strictly better than copying it in the fork, because it then cannot drift *silently* against upstream (meApp pins a published artifact version and would notice). Of all twelve adaptations this is the lowest-cost fork reduction. Recommend it.

**Conflict risk: low** — a new file; `git` will not conflict. The risk is compilation against a changed `VerticalAxis.ItemPlacer` and the silent margin drift above.

---

## A12 — `CartesianLayerMargins`: halving, and `ensureVerticalValuesAtLeast`

**What it does.** Two changes to the margin accumulator. First, `ensureValuesAtLeast` halves its `top` and `bottom` arguments before storing them, restoring v3 behaviour. Second, a new `ensureVerticalValuesAtLeast(top, bottom)` stores them **as given**, for callers whose content sits wholly outside the plot.

**Why we needed it.** v3 halved top/bottom on the way in and every caller — axes, markers, layers — passed full values expecting that. Upstream v4 removed the halving, so axis heights and marker margins were stored at twice their v3 value and the plot area shrank: a 300dp chart lost visibly more height than in v3 (FEATURE-10 tabulates it). The second method exists because the halving is right for content *centred* on the plot edge (an axis label, half of which protrudes) and wrong for content wholly outside it (a marker's label). Passing double through the halving method would have read as a request for twice the room and would have doubled again if the halving were ever removed.

**Commits.**
- `14542561e` feat(chart-layout): Restore v3 chart area height parity
- `2433daf0d` MOB-2953: give the marker a resting label and make its gap a parameter (**UNDOCUMENTED**)

**Files and hunks.**
- `CartesianLayerMargins.kt:54-66`:
  ```kotlin
  this.top = this.top.coerceAtLeast(top.div(2))
  this.bottom = this.bottom.coerceAtLeast(bottom.div(2))
  ```
- `CartesianLayerMargins.kt:68-80` — the new method, with a KDoc explaining the distinction quoted above.

**Public API added or changed.**
```kotlin
public fun ensureVerticalValuesAtLeast(top: Float = this.top, bottom: Float = this.bottom)
// ensureValuesAtLeast keeps its signature; its SEMANTICS change for top and bottom.
```

**Coupling to upstream internals.** None structurally — but this is the most dangerous adaptation in the cluster precisely *because* it has no structural coupling. `ensureValuesAtLeast` is a public method called from every margin-contributing site in the library. Changing what it stores is a change to a contract that **every current and future upstream caller depends on**, and nothing about it will fail to compile. Current in-fork callers:
- `HorizontalAxis.updateLayerMargins` (`HorizontalAxis.kt:532-535`) — passes the full axis height, expects half stored.
- `VerticalAxis.updateLayerMargins` — top/bottom from the item placer.
- `LineCartesianLayer` — per FEATURE-10, changed to pass `maxMargin * 2` to compensate.
- `DefaultCartesianMarker` (`DefaultCartesianMarker.kt:490, 493`) — uses the **new** `ensureVerticalValuesAtLeast` for its above/below-point label reservations.

**Could it be an extension point, or live in meApp instead?** No. But the *framing* should change during migration. Halving inside the accumulator is a trap: it makes a public method's name lie, it forces one caller (`LineCartesianLayer`) to pre-double, and it required a second method to escape. The long-term fix is to drop the halving and have each caller pass what it actually wants reserved — which is a larger change touching every caller, but it would let `CartesianLayerMargins.kt` go back to stock. Given the "minimise the fork" decision, that is worth costing: this file is upstream-untouched (so grafting is trivial) but it is also the file whose fork is most likely to produce a silent wrong answer when v3.3.1 adds a caller we do not know about.

**Conflict risk: low to merge** (upstream never touched the file, per BASELINE) — **high semantically.** A clean graft here is not evidence of correctness. The migration check that matters is: enumerate every v3.3.1 caller of `ensureValuesAtLeast(top = …)` / `(bottom = …)` and confirm each one wants halving.

---

## Cross-cluster dependencies

Things this cluster needs from files owned elsewhere. Re-applying our axis hunks without these produces a chart that compiles and is wrong.

| Needed from | What | Who uses it here |
|---|---|---|
| `CartesianChart.kt:349-361` | `contentStart` / `hidesStrip` and the `clippedToChart` helper | A8 (the exemption only makes sense against the clip) |
| `CartesianChart.kt:374` | `if (hidesStrip) axisManager.drawStartAxisLine(context)` | A8 |
| `CartesianChart.kt:408` | `axisManager.drawMarkerDecorations(context)` after the fading-edge restore | A6 |
| `CartesianChart.kt:70, 298-299` | `startInsetDp` and `layerDimensions.ensureValuesAtLeast(unscalableStartPadding = …)` | A3, A5, A8 |
| `CartesianChartHost.kt:469-479` | `updateScrollState` pushed into both axes around `prepare()` | A7 |
| `common/Defaults.kt:32` | `AXIS_LABEL_VERTICAL_PADDING: Int = 2` (was 4 upstream) | A2 (FEATURE-6 lists it as part of the same change) |
| `DefaultCartesianMarker.kt:490-493` | the only consumer of `ensureVerticalValuesAtLeast` | A12 |
| `LineCartesianLayer.kt` | passes `maxMargin * 2` to compensate for A12's halving | A12 |
| `CartesianDrawingContext.kt:52` | `internal fun getMaxScrollDistance()` | A7 |

## Where the FEATURE docs disagree with the code

Trusting the code, as instructed.

1. **FEATURE-6, MarkerDecoration defaults.** The doc's example passes `outsideRangeOffset = 30f` and states "Default `outsideRangeOffset = 60f` (matching v3)". At HEAD it is `public val outsideRangeOffset: Dp = 20.dp`. It went `0f` → `60f` (`eaefd2bdf`) → `20.dp` (`1bf0a6d9a`), the last change because a raw pixel count shrank as density rose — 60px read as 20dp on a 3x phone and 30dp on a 2x one.
2. **FEATURE-6, out-of-range placement.** The doc says above-range is `bounds.bottom - height - outsideRangeOffset`. At HEAD it is `bounds.top - decoration.outsideRangeOffset.pixels` (`VerticalAxis.kt:285`).
3. **FEATURE-6, where it is drawn.** The doc says "drawing in `drawOverLayers`". At HEAD it is its own `public open fun drawMarkerDecoration`, invoked from `CartesianChart` after the fading-edge layer is restored. The doc also says the chart reserves layer margin for the pill; `1bf0a6d9a` removed that reservation outright ("charts with a goal no longer carry a band of empty space the pill never used").
4. **FEATURE-6, "Zero per-frame allocations".** `y` and `label` are lambdas called every frame. Allocation is the consumer's property, not the design's. meApp's happens to be allocation-free.
5. **FEATURE-6, separators "Clipping: bounds check per separator".** True, but the doc predates the trailing-edge exclusion; the live rule is half-open at the right edge with a 1px epsilon, which the doc does not mention and which is the behaviour that matters.
6. **FEATURE-9, "Label clip extension — drawUnderLayers clip extends by `maxLabelWidth.half` so first/last labels are fully visible".** That was `776fa00f2`'s behaviour and `fc2435259` deliberately reversed it: the extension is still computed but is now clamped by `layerBounds`, precisely so a label can **not** be fully visible past the plot edge. The doc now describes the bug the later commit fixed.
7. **FEATURE-9, "`Size.Scroll` … labels also scroll".** Only when `isLabelsScrollable = true`, which no meApp caller sets.
8. **FEATURE-10, margin table.** Still accurate as far as it goes, but it predates `ensureVerticalValuesAtLeast` and so does not mention that one caller class now opts out of the halving entirely.

No FEATURE doc covers A4, A5, A8, A9, A10, the second half of A6, or the second half of A12.

## Test coverage

There is none for this cluster. `vico/compose/src/{commonTest,androidHostTest}` contains eight test files, none of which touch any axis, item placer or margin class. Every adaptation above is verified only by eye in meApp. That is worth stating plainly before a migration that will rewrite these files.

---

## HorizontalAxis: what our 152 added and 149 removed lines actually do

Written so that someone re-applying onto a rewritten upstream `HorizontalAxis.kt` can work from this section alone. Line numbers are HEAD unless stated. Counts are approximate to within a line or two where a hunk both adds and removes.

### Group 0 — pure reformatting (≈15 added, ≈56 removed). Do not re-apply.

`6a2af9fd0` re-wrapped the argument lists of `rememberTop` and `rememberBottom`: upstream's one-argument-per-line `remember(line, label, …)` and `HorizontalAxis(Axis.Position…, line, label, …)` blocks (14 lines each, four blocks) were collapsed to three or four lines each. This is **the single largest source of noise in the diff** and accounts for more than a third of the removed lines. It carries no behaviour. When re-applying onto v3.3.1, keep upstream's formatting and add only the new parameters (A1's `separators`, A2's `labelVerticalMode` and `labelHorizontalPosition`) to the four lists. Doing this alone should cut the apparent conflict size by roughly a third.

### Group 1 — new imports and the file-level constant (4 added)

`kotlin.math.abs` (`:42`), and `SEPARATOR_EDGE_EPSILON_PX = 1f` with its one-line KDoc at `:46-47`. The constant was briefly declared between the class KDoc and the class, which left the class undocumented; `91e72bf64` moved it above. Place it above the KDoc.

Now-unused imports to drop if upstream's file still has them: `androidx.compose.ui.graphics.Path`, `androidx.compose.ui.geometry.Rect`, `kotlin.math.ceil`.

### Group 2 — three new constructor properties (5 added)

`:72-76`. `separators: Separators? = null`, `labelVerticalMode: LabelVerticalMode = LabelVerticalMode.Center`, `labelHorizontalPosition: Position.Horizontal = Position.Horizontal.Center`, the last two with inline KDoc. All default to the upstream behaviour. They go on the `protected constructor`, after `lineDrawingOrder`. The `internal constructor` secondary (`:101-129`) does **not** pass them and relies on the defaults — leave it alone.

### Group 3 — label vertical offset in `drawUnderLayers` (9 added, 6 removed)

`:165-174`. Upstream computes `textY` as `bounds.bottom - lineThickness - outwardTickLength` (top) or `bounds.top + lineThickness + outwardTickLength` (bottom). We interpose `tickOffset`, a `when (labelVerticalMode)` over `0f` / `outwardTickLength / 2` / `outwardTickLength`, and substitute it for `outwardTickLength` in both branches. `Center` reproduces upstream only if upstream's constant is `outwardTickLength / 2` — **check that against v3.3.1**; at FORK_POINT it was the full `outwardTickLength`, so our `Center` default is already a behaviour change relative to the fork point. This is worth a deliberate decision rather than a mechanical re-apply.

### Group 4 — the `drawUnderLayers` clip (23 added, 15 removed)

`:178-200`. Removed: `getLineLeft(context, maxLabelWidth, axisDimensions)` / `getLineRight(…)`, then `clipPath.rewind()`, `clipPath.addRect(Rect(lineLeft, min(bounds.top, layerBounds.top), lineRight, max(bounds.bottom, layerBounds.bottom)), Path.Direction.Clockwise)`, `canvas.clipPath(clipPath)`.

Added: `startMargin` and `endMargin`, each `maxOf(itemPlacer.get{Start,End}LayerMargin(this, layerDimensions, tickThickness, maxLabelWidth), maxLabelWidth.half)`; a 9-line comment; and a single `canvas.clipRect(maxOf(bounds.left - startMargin, layerBounds.left), min(bounds.top, layerBounds.top), minOf(bounds.right + endMargin, layerBounds.right), max(bounds.bottom, layerBounds.bottom))`.

Two separate decisions are fused here. (a) `clipPath` → `clipRect` and the switch from the vertical axes' line positions to the item placer's margins — A3, from `776fa00f2`. (b) the `maxOf(..., layerBounds.left)` / `minOf(..., layerBounds.right)` clamp — A4, from `fc2435259`, and the one that makes labels agree with guidelines. If you re-apply only (a) you get the eight-day bug's cosmetic half back.

### Group 5 — dead `clipLeft` in `drawUnderLayers` (2 added)

`:208-209`. Computed, never read. A leftover from Group 4's rework. Do not carry it forward; it costs an item-placer call per frame.

### Group 6 — the label loop (1 added, 5 removed)

`:210-227`. Removed: `previousX` (`labelValues.getOrNull(index - 1) ?: (fullXRange.start.doubled - x)`), `nextX` (symmetric), `maxWidth = ceil(min(x - previousX, nextX - x) / ranges.xStep * layerDimensions.xSpacing).toInt()`, and the `maxWidth = maxWidth` argument to `label.draw`. Added: `horizontalPosition = labelHorizontalPosition`.

Two reasons for the removal, both A3: the computation allocated per label per frame (`57ce985ea`), and it evaluates to 0 at a boundary value, blanking the first and last labels. `forEachIndexed` is now `forEachIndexed` with an unused `index` — can become `forEach`.

### Group 7 — separators (29 added)

`:252` the call (`drawSeparators(context, baseCanvasX)`, immediately after `drawGuidelines`, **outside** the `canvas.restore()` that closes the label clip), and `:256-282` the method. See A1 for the body. The ordering matters twice over: after `drawGuidelines` so separators sit above gridlines, and before the layer bitmap is composited so data sits above separators.

### Group 8 — the `drawLineAndTicks` clip and line extents (12 added, 16 removed)

`:314-332`. Same substitution as Group 4 but **without** the `layerBounds` clamp — `clipLeft = bounds.left - itemPlacer.getStartLayerMargin(…)` and `clipRight = bounds.right + itemPlacer.getEndLayerMargin(…)`, used both for `canvas.clipRect` and as the `left`/`right` of `line.drawHorizontal`. Upstream passed `lineLeft`/`lineRight` from the (now dead) `getLineLeft`/`getLineRight`. The axis line is deliberately allowed past `layerBounds`; only labels are held to it.

### Group 9 — `isChartEdge` and the guideline suppression (21 added, 2 removed)

`:427-437` the helper, `:451-458` an 8-line comment, `:468` and `:481` the two `takeUnless { x.isBoundOf(fullXRange) || isChartEdge(x) }`. A5. The tolerance (`ranges.xStep / 1000`) is load-bearing — an exact `==` test passes straight over the value, because a label value is rebuilt by rounding to the nearest step before use.

### Group 10 — `updateLayerDimensions` gutted (4 added, ~50 removed)

`:507-512`. The whole upstream body — measuring the first and last label values via `itemPlacer.getFirstLabelValue`/`getLastLabelValue`, formatting each, taking half its padded width, subtracting the distance to `ranges.minX`/`maxX` when `!context.zoomEnabled`, and calling `layerDimensions.ensureValuesAtLeast(unscalableStartPadding = …)` / `(unscalableEndPadding = …)` — is replaced by `: Unit = Unit` and a two-line comment.

**This is the single largest and single riskiest hunk in the file.** It is a deletion of upstream behaviour, justified by v3 parity (A3). Two things to verify against v3.3.1 before re-applying: whether `updateLayerDimensions` acquired any new responsibility, and whether anything other than this method now contributes `unscalableStartPadding` (in our tree `CartesianChart.startInsetDp` does, at `CartesianChart.kt:299` — that is the chart's start inset, a different mechanism that must survive).

### Group 11 — `getHeight` (4 added, 1 removed)

`:565-567` — `lineThickness` is added to the `Size.Auto` height only for a Bottom axis: `(labelHeight + titleComponentHeight + (if (position == Axis.Position.Horizontal.Bottom) lineThickness else 0f) + outwardTickLength)`. Part of A2 (v3 parity), but independent of the two label parameters and should be decided on its own.
`:573` — `is Size.Scroll -> size.value.pixels`, required by A7 to keep the `when` exhaustive over the sealed `Size`.

### Group 12 — the `Separators` data class (11 added)

`:659-669` (declared at `:666`), placed between `hashCode` and the `ItemPlacer` interface. Not referenced by `equals`/`hashCode` (see A1).

### Group 13 — the `LabelVerticalMode` enum (9 added)

`:853-869` (enum declared at `:861`), the last thing in the class, after the companion object. Three cases with one-line KDoc each mapping to v3's `horizontalLabelPosition`: `Inside` ≙ v3 `End`, `Center` ≙ v3 `Center`, `Outside` ≙ v3 `Start`.

### What becomes dead and can be dropped while re-applying

`private val clipPath = Path()` (`:99`); `getLineStart` / `getLineEnd` / `getLineLeft` / `getLineRight` (`:355-425`, ~70 lines, all unreferenced); the `axisDimensions` parameter of `drawUnderLayers` and `drawLineAndTicks` (still in the signature, no longer read — but `drawUnderLayers`'s is an `override`, so it stays); the dead `clipLeft` at `:208`; the `index` of `forEachIndexed` at `:210`; and `ItemPlacer.getFirstLabelValue` / `getLastLabelValue` (`:686, :695`), which after Group 10 are implemented but never called.

Dropping the four `getLine*` methods would turn a 152/149 diff into roughly 152/80 against upstream, at the cost of removing upstream code outright. Given "minimise the fork", leaving upstream's dead methods in place is the cheaper merge; removing them is the cleaner file. My read: leave them, and note them, so that a future upstream change to them merges without conflict rather than without meaning.
