# Do-not-apply register (MOB-4096)

The port is a hand-applied per-file diff. A verdict of DROP or DELETE is realised by **not applying
a hunk** — it costs bookkeeping, not code. This is that bookkeeping, keyed by the file you are
porting, because that is when you need it.

**Check this file before applying any patch in `ports/`.**

---

## Settled in P4 (done)

| Item | File | Action |
|---|---|---|
| **B-11** | `TextComponent.kt` | **APPLIED.** `append(measuredText)`, not `append(text)`. The nullable one writes the literal `"null"`, so every `Size.Auto` axis measured four characters too wide. Live at v3.3.1 — also the upstream PR. |
| **L13** | `CartesianChartModelProducer.kt` | **APPLIED, differently.** Do **not** apply our rename of `PreviewContext` → `InlineUpdateContext`. Upstream's name stays; a public `InlineUpdateContext` is added beside it and `getDispatcher()` checks both keys. meApp consumes the public one in `BaseDashboardViewModel` and a test util. This keeps `CartesianChartModel.kt` out of the conflict surface entirely — it now needs no change at all. |
| **D-2** | `CartesianChart.kt` | **Do not apply** our removal of the per-frame marker-target sort. We removed it believing the map was a sorted `TreeMap`; `10fa978bb` had already made it `mutableMapOf`, and v3.3.1 still sorts at `CartesianChart.kt:386-388`. The nearest-target scan early-**breaks**, so it is correct only on ascending keys — without the sort it picks the wrong point on any chart whose layers contribute different x sets, i.e. the baby chart. |

---

## To check when you port each file

### `CartesianChart.kt`
- **D-2** above — keep upstream's sort.
- **`allMarkerTargetXValues`** — our cache is keyed on the collection's *size* only, so it is stale
  whenever the x set changes but the count does not, which is most of a scroll. Targets come from the
  visible points, so this is not rare. Either key it correctly or drop the cache; do not port as-is.
  It is a correctness bug on the gesture path, not a per-frame cost.

### `VicoScrollState.kt`
- **`S1A-08a`** — do not apply the re-apply-`initialScroll`-on-`maxValue`-change heuristic. v3.3.1's
  `update()` re-anchors the viewport properly via `previousScrollLayout`. Three agents agreed.
- **`S1A-08c`** — do not apply the `initialScrollHandled` `private` → `internal` widening. Its only
  reader outside the file was the guard on `H-7`'s first prepare pass, which is deleted. The stated
  "loud compile error" risk was false.

### `SnapFlingBehavior.kt`
- **`S1A-05b`** — drop the `isSnapping` flag. Written, never read, and `internal` so meApp cannot read
  it either. Restoring the behaviour FEATURE-4 documents would now be **wrong**: emissions during a
  snap are already collapsed by the three-scalar dedup plus `debounce`, so suppressing as well would
  make the Y axis lag the chart by the full 500 ms snap animation. Delete the FEATURE-4 paragraph too.

### `CartesianLayerMargins.kt` + `DefaultCartesianMarker.kt`
- **`A12`** — do not apply the top/bottom **halving**, and do not apply `ensureVerticalValuesAtLeast`.
  Self-inflicted: we halved, then added a second method to undo the halving for the marker. v3.3.1
  stores as given, and **all six** of its callers already apply their own `.half` where that is right.
  **These two must land in one commit.** Split them and the marker reserves half the space it needs and
  the label overlaps the plot, silently.
- Note `LineCartesianLayer` does **not** pre-double to compensate, contrary to the catalogue: the `* 2`
  was added by `14542561e` and reverted by `a2a9a7690`. So the fork currently reserves a *quarter* of
  the widest stroke. Deleting the halving fixes a live under-reservation.

### `HorizontalAxis.kt`
- **`A3b`** — do not apply the `clipPath` → `clipRect` substitution. v3.3.1's `getLineLeft` default
  branch already *is* the quantity we substituted, and the vertical-axis `lineBounds` is consulted only
  via `minOf`/`maxOf`, so it can widen but never narrow. The failure we patched around cannot occur.
  Dropping this also removes ~70 lines of fork-created dead code.
- **`A3a`** — do not re-apply the `updateLayerDimensions` no-op. Replace it with
  `ItemPlacer.aligned(shiftExtremeLabels = true)` from the consumer. The method gained no new
  responsibility at v3.3.1, but no-opping it again would kill the half-label padding it reserves.

### `VerticalAxis.kt`
- **`A7b`** — do not apply `isLabelsScrollable`, `updateScrollState`, `effectiveScroll` or the mutable
  `scrollValue`/`maxScroll`. No meApp caller sets it; meApp's own `startAxis` KDoc records the
  shrinking margin as the defect it moved *away* from. This deletion also removes **`H-7`**, the host's
  throwaway first `prepare` pass, which collides head-on with v3.3.1's new `measure`/`getVerticalExtras`
  split.
- **`A6′`** — do not apply the `getMarkerDecorationWidth` gutter widening. Inert: meApp pins a fixed
  50 dp gutter. **Gated** — confirm the goal pill does not exceed 50 dp at font scale 1.3 with the
  longest allowed label before deleting, because the original "risk: none" reasoning was unsound.

### `Modifier.kt` + `CartesianMarkerController.kt`
- **`B-3a` / `M-4`** — do not apply `acceptsGestures` or its `detectTapGestures` guard. v3.2.0's
  conditional tap consumption covers it: the detector attaches only when `chart.marker != null`, the
  host returns `shouldAcceptInteraction` verbatim, and consumption is gated on that boolean — the host
  returns `false` *before* touching `markerX`, so no marker is placed either. Two files stop being
  patched; `rememberNonInteractive` moves to meApp as ~8 lines with `acceptsLongPress = false`.
  **Gate before this lands:** tap a snapshot card, confirm the full graph opens. The failure is silent.

### `DefaultCartesianMarker.kt`
- **`B-9`** — do not apply `contentPadding`. No meApp caller at all, and it now affects only guideline
  extents for two label positions meApp does not use.

### `ScrubMarkerController.kt`
- **`B-1`** — do not apply the `scrollState` parameter on `rememberScrubMarkerController`. It is never
  read; it is a leftover from before `8052261ae` moved scroll-dismiss to the host. meApp passes it and
  must stop.

### `CartesianDrawingContext.kt`
- **`L10`** — do not apply the `getVisibleXRange()` `internal` → `public` widening. Nothing outside the
  library calls it, and the file's fork→v3.3.1 diff is empty, so leaving it alone means this file needs
  no change at all.

### `CartesianChartModelProducer.kt`
- **`L12b`** — do not apply `registerForUpdates(animate = false)`. It was never intended: v3.3.1's
  `registerForUpdates` animates, the producer file is byte-identical fork→v3.3.1, and the commit that
  added `animate` names `runTransaction` only. It changes behaviour for every consumer while looking
  like an opt-in flag. If the instant restore turns out to be wanted, use
  `CartesianChartHost(initialAnimationSpec = null)`.

### `CartesianChartHost.kt`
- **`H-10`** — do not apply the chart-area height parity work, and do not carry the
  `Defaults.CHART_HEIGHT` import with it. Upstream removed the 200 dp cap: `ChartHostBox` honours a
  consumer `Modifier.height()` outright and `chartAreaHeight` is a public parameter. `CHART_HEIGHT`
  **does not exist at v3.3.1** — this is the only item in the whole port whose failure is loud.
- **`H-7`** — see `A7b` above.

### `LineCartesianLayer.kt`
- **`L15`** — the whole-file reformat is already handled: the patches in `ports/` were produced from
  trees formatted with upstream's ktfmt 0.64. Do not reformat again.
- **`L16`** — `pointSpacingProvider` is fully removed at HEAD; there is nothing to port.

### `HorizontalAxisItemPlacers.kt`
- **`A4a′`** — do not apply the `Segmented…` half of the half-open window change; it is dead for the
  only consumer.
- **Warning**: `A4a` is **two** changes, not one. Besides the epsilon filter, the fork **deletes
  upstream's `fullXRange.start`/`endInclusive` guards**. A delegating wrapper in meApp can only
  subtract from upstream's list — it cannot restore a value upstream's `break` suppressed. And `A5`
  (the `isChartEdge` guideline suppression) exists *because* those guards were removed. **`A4a` and
  `A5` are one feature and land together**, in P11.
