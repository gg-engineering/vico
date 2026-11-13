# S3-E — Disposition: the eight host orphans, and the completed consumer-API table

Closes the gap S5-B §3.3 names: eight S1-E adaptations referenced as dependencies by three Stage-3
dossiers but never given a verdict of their own. Also completes S3-D Part 2's consumer-API table
(S5-B §3.4).

Refs: `FORK_POINT = e67e49520`, `OURS = HEAD` (`MOB-2953-week-range-spans-eight-days`, `4.0.46`),
`TARGET = v3.3.1` (`3d675ccc7`).
`$C = vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose`.
Everything below was read with `git show <ref>:<path>` and `git grep <ref>`. **The checkout was not
modified.** meApp read at `/Users/selvakumar/Projects/meApp-bug/Android` (branch
`MOB-198-appsync-bmi-unit`), read-only.

Every claim here is against source at `v3.3.1` or `HEAD`. Where another dossier is cited it is as a
*cross-reference*, never as evidence — C1 is the record of what happens when a well-cited assertion
is taken on trust.

**Verdict vocabulary used here**
| verdict | means |
|---|---|
| **RE-APPLY** | port the hunk substantially as it stands; upstream's surrounding code still fits it |
| **RE-DERIVE** | the behaviour survives, the hunk does not; rebuild it against v3.3.1's shape |
| **REPLACE** | upstream has an equivalent; use theirs instead of ours |
| **MOVE** | the behaviour leaves the library for meApp |
| **DELETE** | the adaptation does not survive, and its removal has consequences that must be executed |
| **DROP** | the adaptation is simply not carried forward; nothing depends on it |

---

# 1. Verdict table

| id | adaptation | verdict | risk if wrong | loud / silent | sequencing |
|---|---|---|---|---|---|
| **C-7** | interpolated (synthesised) marker targets | **RE-APPLY (amended)** | a consumer-chosen x that is not a data point marks nothing; persistent markers off a data point draw nothing; scrubbing an empty window stops marking | **silent** | after `L2b` (the interpolator seam); with `L6`, `L7`, `B-10`; draw-tail hunk inside CF-5's single-owner pass (P12/P13) |
| **H-4** | marker resting text formatted on scroll settle | **RE-APPLY (re-sited)** | caption formatted per frame (date arithmetic ×60/s, names every window a fling crosses) — or never appears at all | **silent** | after `H-3` lands in the fork's shape; with `B-5`. P13. Re-open at P16 per CF-4 |
| **H-5** | caller-owned marker via `initialMarkerX` | **RE-DERIVE** | a marker the caller cleared keeps being drawn at an x nobody holds | **silent** | with `H-6`, `B-1`, `B-2`, `B-12` — one contract, one change. P14 |
| **H-6** | scrub callback through `onInteraction`; scrub exempt from `onViewportChange` | **RE-DERIVE** | release-after-scrub clears the marker the scrub just placed; no marker in a window with no entries | **silent** | with `H-5`. Needs `D-2` (amended) first and `C-7` for parity. P14 |
| **H-7** | two-pass prepare + `VerticalAxis.updateScrollState` | **DELETE** | frame 1's vertical-axis width and plot left edge differ from frame 2's — a one-frame jump on chart open | **silent** | with `A7b`, in the same change. **Resolves CF-1 / BL-5.** P9/P14 |
| **H-8** | `flingBehavior` threaded host → `Modifier` | **RE-DERIVE (seam survives, narrowed)** | YEAR stops snapping to month boundaries; or snap cannot leave the library at all and `S1A-05a` dies | **silent** (no snap looks like a design choice) | the seam lands in P14 with `M-3`; `S1A-05a`'s MOVE then lands in P16. **Resolves BL-4** |
| **H-9** | `scrollState.drawingContext = drawingContext` | **RE-APPLY** | `getVisibleAxisLabels` falls through to the even-walk strategy and returns plausible wrong labels | **silent** | with `S1A-09a`. One line. P7 |
| **H-10** | chart-area height parity (`heightIn(max = CHART_HEIGHT.dp)` removal) | **DELETE** — do not apply | none for meApp (both hosts pin a height); the *dead imports* break the build | **loud** (see below) | P4, with the import cleanup. Zero conflict risk |

**One correction to S1-E's own ratings.** S1-E rated H-10 "very high conflict risk as a patch, zero
as a deletion" and listed three imports as merely *dead*. One of them does not resolve at v3.3.1:
`Defaults.CHART_HEIGHT` (`HEAD $C/common/Defaults.kt:63`, `const val CHART_HEIGHT: Float = 200f`)
**does not exist at v3.3.1** — `git show v3.3.1:$C/common/Defaults.kt | grep CHART_HEIGHT` returns
only `CARTESIAN_CHART_AREA_HEIGHT: Float = 192f` (`:61`). So the import at `HEAD $C/cartesian/
CartesianChartHost.kt:40` is an unresolved reference, not a lint warning. H-10's deletion is the only
item in this set whose failure mode is loud, and only because of that import.

---

# 2. Evidence, item by item

## C-7 — interpolated (synthesised) marker targets · **RE-APPLY (amended)**

**What it is at HEAD.** `private fun synthesizeInterpolatedTargets(context, x)` at
`$C/cartesian/CartesianChart.kt:551-597` (KDoc from `:544`), plus the retained
`private var drawingContext: CartesianDrawingContext?` (`:481`, set at `:325`, nulled at `:414`), the
active-marker fallback (`:387-390`) and the persistent-marker fallback (`:486-490`).

**DROP and DELETE are not available.** Three verdicts in two other dossiers exist to serve it and
have no other consumer: S3-B `B-10` (`isInterpolated` on `LineCartesianLayerMarkerTarget.Point` —
the flag is set only at `CartesianChart.kt:590`), S3-C `L6` (`markerTargetsEnabled` — its *second*
enforcement point is `CartesianChart.kt:576`), and S3-C `L2b` (the `getYAt` seam exists because
`CartesianChart.kt:582` is one of `getYAtXFromEntries`'s two library call sites). Deleting C-7 strands
all three.

**MOVE is not available either.** It needs draw-time geometry (`context.scroll`, `layerBounds`,
`layerDimensions`) and `CartesianChart`'s private `layers` and `persistentMarkerMap`. Confirmed by
reading, not inferred.

### The finding that changes the risk rating

S1-E's named hazard — *"it re-implements the layer's own point placement, so any upstream change to
how a layer maps x→canvasX silently desynchronises the synthesised target from the drawn line"* —
**did not materialise in this version bump.** The two formulas are equivalent at v3.3.1:

```
C-7                 HEAD  $C/cartesian/CartesianChart.kt:560-564
  drawingStart = layerBounds.getStart(isLtr) + layoutDirectionMultiplier * layerDimensions.startPadding - scroll
  canvasX      = drawingStart + layoutDirectionMultiplier * layerDimensions.xSpacing * ((x - ranges.minX) / ranges.xStep).toFloat()

the layer           v3.3.1 $C/cartesian/layer/LineCartesianLayer.kt:568-572, 762-764 (and 838-839)
  drawingStart = layerBounds.getStart(isLtr = isLtr) + drawingStartAlignmentCorrection - scroll
                 where drawingStartAlignmentCorrection = layoutDirectionMultiplier * layerDimensions.startPadding
  getDrawX(e)  = drawingStart + layoutDirectionMultiplier * layerDimensions.xSpacing * ((e.x - minX) / xStep).toFloat()
                 where minX = ranges.minX, xStep = ranges.xStep   (:754-755)
```

Term for term identical. That downgrades C-7 from "re-derive against a moved target" to "graft, then
verify the formula once" — and it is why the verdict is RE-APPLY rather than RE-DERIVE.

### Two divergences S1-E did not record. Both pre-date the migration; neither blocks it.

**(a) The Y maths ignores the drawing model.** The layer's `getDrawY`
(`v3.3.1 LineCartesianLayer.kt:766-771`) prefers `pointInfoMap?.get(entry.x)?.y` over the raw
`(y - minY) / length`. C-7 (`HEAD CartesianChart.kt:583-584`) has no `pointInfoMap` path at all.
During a difference animation the drawn line sits at the animated y and the synthesised marker point
at the final y — **they separate for the duration of the animation.** Silent. Record it now so the
P15 parity diff does not read it as a port defect. Note this interacts with `L5`: on a layer whose
`drawingModel` is nulled (`rangeProvider is ScrollAwareRangeProvider || alwaysUseLiveRange`,
`HEAD LineCartesianLayer.kt:614`) `pointInfoMap` is null anyway and the two agree — which is why
nobody has seen it. It is the *other* layers that diverge.

**(b) Bounds-test asymmetry.** Layer: `canvasX <= left - 1 || canvasX >= right + 1` → drop
(`v3.3.1 :622, :647`). C-7: `canvasX < left - 1 || canvasX > right + 1` → drop
(`HEAD CartesianChart.kt:567`). A target landing exactly on the boundary is dropped by the layer and
kept by C-7. One pixel. Noted for completeness; align them while porting, at no cost.

### Amendments the port must carry

1. **`HEAD :582`** `MonotoneInterpolator.getYAtXFromEntries(x, series)` → the `L2b` seam
   (`interpolator.getYAt(x, series)`). **Preserve the `?: continue` contract** — out of range returns
   `null`, deliberately *not* clamped (`25dc072a9`). A re-implementation that clamps resurrects flat
   phantom markers. Silent.
2. **`HEAD :576`** `if (!layer.markerTargetsEnabled) continue` must land with `L6` as a `val`.
3. **`HEAD :590`** `isInterpolated = true` must land with `B-10`.
4. **`HEAD :578`** `layer.internalVerticalAxisPosition` must land with `L7` —
   `verticalAxisPosition` is still `protected val` at `v3.3.1 LineCartesianLayer.kt:68`, so the
   `internal` accessor (`HEAD :122`) is still required.
5. **The retained `drawingContext`** re-sites into v3.3.1's `draw()` — set at the top of
   `with(context) {` (`v3.3.1 CartesianChart.kt:378`), nulled before the closing brace (`:403`).
6. **Stale comment to fix while there:** `HEAD :548` still names a `MonotoneInterpolator.getYAtX`
   overload deleted by `57ce985ea`.

### Graft sites at v3.3.1

| hunk | v3.3.1 site | state |
|---|---|---|
| persistent-marker fallback | `forEachPersistentMarker`, `CartesianChart.kt:470-476` | upstream body is 4 lines, untouched in range — **clean** |
| active-marker fallback | `getMarkerTargets(markerX, markerSeriesIndex)`, `:390` | **contested — CF-5's tail.** Single owner, P12/P13 |
| context stash/clear | `draw()` `:378` / `:403` | clean |
| the function itself | new private member | clean |

**Fork-minimising shape, deferred.** Ask the **layer** for the position instead of recomputing it:
`CartesianLayer.getInterpolatedTarget(context, x): Target?` puts the maths beside the code that draws
the line, removes ~50 lines from `CartesianChart`, and fixes divergence (a) for free. Propose
upstream; schedule the fork-side move at **P16**, not during the port. "Markers at arbitrary x" is a
reasonable library feature and this is the second-best upstream candidate in the host cluster.

**Risk if wrong.** Silent in every direction. Dropping it: a consumer-chosen x that is not a data
point marks nothing, persistent markers off a data point draw nothing, and scrubbing a window with no
entries silently stops marking. Porting it with the wrong interpolator contract: confident phantom
markers on a flat line.

---

## H-4 — marker resting text formatted on scroll settle · **RE-APPLY (re-sited)**

**What it is at HEAD.** `$C/cartesian/CartesianChartHost.kt:316` (the `visibleXRanges`
`MutableSharedFlow(replay = 1, extraBufferCapacity = 8)`), `:322-329` (the
`LaunchedEffect(chart.marker, visibleXRanges, model)` that emits `visibleXRanges.first()` immediately
then `debounce(MARKER_SETTLE_MS).collect(marker::onScrollSettled)`), and `:560`
(`MARKER_SETTLE_MS = 100L`, equal to `ScrollAwareRangeProvider`'s default `debounceMs` so the text
and the axis it describes come to rest together).

**Why RE-APPLY and not RE-DERIVE.** Everything the effect reads exists unchanged at v3.3.1 and in the
same composable: `chart.marker` is read by upstream itself at
`v3.3.1 CartesianChartHost.kt:251`, and the effect sits among `LaunchedEffect`s upstream left
structurally intact (`:297-310`). The hunk is purely additive in a region upstream did not
restructure beyond renumbering. It is **blocked**, not reshaped: its input is H-3's emission, and H-3
is RE-DERIVE'd in the fork's shape (S3-A, "move late", acknowledged in CF-2). H-4 lands after it.

**Two things to fix while re-siting.**
- `kotlinx.coroutines.flow.debounce` is `@FlowPreview`. At HEAD the `@OptIn(FlowPreview::class)` sits
  on `ScrollAwareRangeEffect` only (`HEAD :566`), not on `CartesianChartHostImpl`; the call compiles
  because the warning is not an error in this build. **Put the annotation on the host impl
  explicitly** rather than relying on warning level surviving v3.3.1's Kotlin 2.4.10 and whatever
  `-Werror` the rebased build ends up with. Cheap insurance against a loud failure at the worst time.
- Keep `model` in the key. It is not redundant: the resting text is formatted from the window *and*
  from the caller's state, and new data can change the second without moving the first. `replay = 1`
  is what makes the restart re-format rather than wait for the next scroll.

**Risk if wrong.** Dropping the debounce: the caller's formatter (date arithmetic) runs per frame and
the caption names every window a fling crosses. Dropping the `first()` emission: the caption is one
debounce late on the opening frame. Dropping the effect entirely: no caption at all, on both the
dashboard chart and the snapshot card. All **silent** — nothing errors; the chart just looks wrong.

**CF-4 is a budget decision, not a contradiction.** If H-3 eventually lands as a `Decoration` in
meApp, H-4's feed disappears by the same route and this effect — plus `CartesianMarker.onScrollSettled`
(`B-5`) — can be deleted. S3-B is right that the marker port must not be gated on that. **Take the
decision explicitly at P16 and record which way**, per S5-B.

---

## H-5 — caller-owned marker via `initialMarkerX` · **RE-DERIVE**

**What it is at HEAD.** `$C/cartesian/CartesianChartHost.kt:76` (producer overload param), `:199`
(pass-through), `:226` (model overload param), `:234` (pass-through), `:248` (impl param), `:255`
(`val scrubController = chart.markerController as? ScrubMarkerController`), `:266-288` (the two
effects).

**Why RE-DERIVE and not RE-APPLY — the overload set moved under every parameter.**

| | HEAD | v3.3.1 |
|---|---|---|
| producer overload | `…, flingBehavior, animationSpec, animateIn, initialMarkerX, placeholder` (`:70-78`) | `…, animationSpec, initialAnimationSpec, chartAreaHeight, placeholder` (`:93-96`) — **no `animateIn`** |
| deprecated overload | — | exists, takes `animateIn` (`v3.3.1 :138-149`) |
| model overload | `…, zoomState, initialMarkerX` (`:220-227`) | `…, zoomState, chartAreaHeight` (`:180-186`) |
| `CartesianChartHostImpl` | `(chart, model, scrollState, zoomState, ranges, previousModel, extraStore, flingBehavior, initialMarkerX)` (`:239-249`) | `(chart, model, modifier, scrollState, zoomState, ranges, chartAreaHeight, previousModel, extraStore)` (`:205-215`) — `modifier` inserted at 3, `chartAreaHeight` at 7 |

**What survives untouched, so the effects' bodies port almost verbatim.** `markerX`,
`markerSeriesIndex` and `lastAcceptedInteraction` are still `rememberSaveable` at
`v3.3.1 :216-219`, and `Interaction.Saver` still exists (`:219`). The state H-5 writes is upstream's
and is unchanged. Only the plumbing moves.

**Port instruction, from S3-D and worth repeating because it is easy to get backwards:** add
`initialMarkerX` **only to the non-deprecated overloads.** Patching the deprecated one too would make
meApp's `GraphView.kt:94-109` compile on a deprecated path and defer the `animateIn` → 
`initialAnimationSpec` work.

**Keep both halves of the asymmetry.** The clear is forced; the restore is conditional
(`if (markerX == null) markerX = initialMarkerX`). `bd58edc86` fixed the clear; `c65d25ef4` is the
restore. Making the restore unconditional drags the marker backwards under the finger during a drag,
because the local value is ahead of the one the caller has echoed back. The `LaunchedEffect` is keyed
on `initialMarkerX`, **not `Unit`** — that is the whole of `bd58edc86`.

**Depends on** `ScrubMarkerController.hasActiveMarker` (`B-1` RE-APPLY keeps it; `B-1` deletes only
the controller's unread `scrollState` parameter).

**Risk if wrong.** A marker the caller cleared keeps being drawn — the dot and guideline sit at an x
nobody holds, and the dashboard header shows a reading for an entry that was deleted. **Silent.**

**The better shape, recorded and deferred to P18.** Hoist the host's marker state:
`CartesianChartHost(…, markerX: Double?, onMarkerXChange: (Double?) -> Unit, …)` or a
`rememberCartesianMarkerState()`. That is one upstream-style change replacing H-5, H-6 and most of
`ScrubMarkerController`. It cannot be done in meApp today because `markerX` is private host state.
**This is the single best candidate in the host cluster for converting a patch into an upstream
feature request** — but it is a redesign, and CF-2's rule puts it after parity.

---

## H-6 — scrub callback through `onInteraction`; scrub exempt from `onViewportChange` · **RE-DERIVE**

**What it is at HEAD.** `$C/cartesian/CartesianChartHost.kt:356-371` (hand-inlined target narrowing),
`:376-407` (the scrub branch), `:415-421` (`onViewportChange`'s early return for
`ScrubMarkerController`).

**Three upstream changes force a re-derivation rather than a graft.**

1. **`onInteraction`'s type changed from `(Interaction) -> Unit` to `(Interaction) -> Boolean`**
   (`v3.3.1 $C/cartesian/Modifier.kt:58`; the host's lambda returns `true`/`false` at
   `v3.3.1 CartesianChartHost.kt:281-284`). The return value now drives event consumption. Our
   branch must produce a boolean on every path.
2. **The narrowing was extracted upstream** into `internal fun narrowMarkerTargets(targets, pointX):
   Pair<List<Target>, Int?>` (`v3.3.1 CartesianChartHost.kt:56-66`) with
   `MARKER_POSITION_TOLERANCE = 0.5f` (`:47`). Our copy (`HEAD :356-371`) compares `canvasX` for
   **exact** equality. **Use upstream's helper and delete ours** — ours is also the ULP bug S2-C V5
   flags, and keeping it would fix one of two causes of the same symptom.
3. `onViewportChange`'s surrounding shape is otherwise identical (`v3.3.1 :291-295`), so the
   one-line `if (chart.markerController is ScrubMarkerController) return` guard re-applies cleanly.

### A decision the `Boolean` contract creates, which no dossier has recorded

What should the scrub branch return when the consumer callback returns `null` (i.e. "no marker
here")? The interaction *was* accepted — only the marker was declined. It must return **`true`**.

Returning `false` would leave the tap unconsumed (`v3.3.1 Modifier.kt:173`,
`if (inputChange.isTap(down) && onTap(inputChange.position)) inputChange.consume()`), so a tap meant
to clear the marker would fall through to whatever surrounds the chart and navigate.

This is consistent with — and must be taken together with — S3-B `B-3a`/`M-4`, which DELETE
`acceptsGestures` **because** a controller that returns `false` now leaves the tap unconsumed for a
surrounding clickable. The two decisions are the same mechanism read from opposite ends:

| controller | returns | consequence |
|---|---|---|
| `ScrubMarkerController` (dashboard, `GraphView.kt:268`) | `true` on every accepted interaction, **including the clear** | tap consumed; the card behind does not navigate |
| meApp's moved `NonInteractiveMarkerController` (snapshot card, `SnapshotLineChart.kt:145`) | `false` | tap unconsumed; the card opens the full graph |

Both dossiers are right; neither states the pairing. **State it in the P14 change.**

**Depends on** `chart.allMarkerTargetXValues` — which survives `D-2`. S3-B's `D-2` DELETEs the
*size-keyed cache* and restores upstream's sort; the `internal val` itself stays, because H-6 is its
only consumer. Verified against S3-B §2 `D-2` disposition items 1 and 2.

**Risk if wrong.** Losing the third `interaction` argument (`b7d7e0583`): the release that ends a
scrub is indistinguishable from a tap, so lifting off clears the marker the scrub just placed — the
exact bug that commit fixed; meApp reads it at `GraphView.kt` (`val isTap = interaction is
Interaction.Tap`). Losing the empty-target acceptance (`2c00808b6`): no marker at all in a window
with no entries, which is where meApp snaps to an axis label. Losing the `onViewportChange` guard:
the scrub marker is re-placed on every scroll delta instead of being dismissed. All **silent**.

---

## H-7 — two-pass prepare and axis scroll state · **DELETE**

See §3 for the full CF-1 resolution. The verdict rests on three facts established from source:

1. **`updateScrollState`'s values have exactly one reader.** `scrollValue` and `maxScroll` are
   private fields on `VerticalAxis` (`HEAD $C/cartesian/axis/VerticalAxis.kt:398-399`), written only
   by `updateScrollState` (`:402-405`), and read only at `:491-492` — inside
   `if (scrollSize != null && scrollSize.isLabelsScrollable)`.
2. **`isLabelsScrollable` is never true in meApp.** It defaults `false`
   (`HEAD $C/cartesian/axis/BaseAxis.kt:214`). meApp's end axis is
   `BaseAxis.Size.Scroll(YGutterWidth)` with no second argument
   (`features/common/components/chart/axis/VerticalAxis.kt:94`); its start axis is
   `BaseAxis.Size.Fixed(0.dp)` (`:56`) and is not a `Size.Scroll` at all.
   `grep -rn isLabelsScrollable app/src` → nothing.
3. **Nothing else measured in `prepare` depends on the scroll value.** `CartesianChart.prepare`
   (`HEAD $C/cartesian/CartesianChart.kt:233-275`) takes a `CartesianMeasuringContext`, which carries
   no scroll. `HorizontalAxis`'s only `scroll` reads at HEAD are in draw methods (`:203`, `:335`);
   `:573` is `is Size.Scroll -> size.value.pixels`, a constant.

So H-7's first pass is a no-op for the only consumer, and the second `updateScrollState` pair is
dead code once `A7b` lands.

**Risk if wrong — and it is silent, so plan for it.** If a scroll-dependent measurement does exist
that this analysis missed, frame 1's vertical-axis width and plot left edge differ from frame 2's: a
one-frame jump when a chart opens. Nothing errors. **A golden screenshot taken at frame ≥ 2 would
not catch it.** Concrete mitigation: P15's golden for chart-open must capture the **first** drawn
frame explicitly, not a settled frame.

**Invariant to record with the deletion.** *Measurement must not depend on the scroll value.* It is
already upstream's invariant — `measureExtras` calls `chart.getVerticalExtras(context, …)` from
`Modifier.layout` (`v3.3.1 CartesianChartHost.kt:237-247`, `ChartHostBox.kt:51-68`), strictly before
any draw pass and therefore before any scroll is known. If measurement depended on scroll, upstream's
own pre-measure would be wrong. Anyone who later re-introduces a scroll-dependent measurement
re-creates the two-pass problem; write the invariant into the axis file as a comment, not into a
dossier nobody will read again.

---

## H-8 — external `flingBehavior` · **RE-DERIVE (seam survives, narrowed)**

See §4 for the full BL-4 resolution. Evidence:

- **v3.3.1 owns the construction.** `Modifier.pointerInput` is now `@Composable internal` with **no**
  `flingBehavior` parameter; it builds one itself from `scrollState.xSnapStep`
  (`v3.3.1 $C/cartesian/Modifier.kt:63-72`) and passes it to `scrollable` (`:78`). Upstream's
  `internal class SnapFlingBehavior` is at `:191-209`.
- **`xSnapStep` is a single `Double?`** (`v3.3.1 $C/cartesian/VicoScrollState.kt:60, 103, 138, 344`)
  consumed by `getSnapDelta` (`:265-277`). C2 settled that a month is not a uniform millisecond step,
  so meApp's YEAR snap is not expressible as one. Verified at the consumer:
  `GraphView.kt:210-224` builds a `SnapBehaviorConfig` whose `snapToLabel` calls
  `GraphSnapHelper.getSnappedPositionOnDrag` / `getSnapPositionOnFling` with the segment — calendar
  arithmetic, not arithmetic on a step.
- **One dead argument to drop while re-deriving:** `flingBehavior` is also passed into
  `ScrollAwareRangeEffect` (`HEAD CartesianChartHost.kt:174`, declared `:567`) and **never read**.
  DROP that parameter; it is not part of the seam.
- **C4's hazard resolves by deletion, not rename** — if `S1A-05a` lands, the fork's
  `$C/cartesian/SnapFlingBehavior.kt` (containing `SnapBehaviorConfig`, `ChartSnapFlingBehavior`,
  `rememberChartSnapFlingBehavior`) is deleted outright and the file-name collision with upstream's
  `SnapFlingBehavior` disappears. If `S1A-05a` is deferred, the file **must** be renamed
  `ChartSnapFlingBehavior.kt` — an `import …cartesian.SnapFlingBehavior` would otherwise silently
  resolve to upstream's class.

---

## H-9 — `scrollState.drawingContext = drawingContext` · **RE-APPLY**

**What it is.** One line, `HEAD $C/cartesian/CartesianChartHost.kt:531`, immediately before
`chart.draw(drawingContext)`.

**Graft site at v3.3.1 is clean.** Insert between the `CartesianDrawingContext(...)` construction
(ends `v3.3.1 CartesianChartHost.kt:368`) and `chart.draw(drawingContext)` (`:370`). Upstream barely
touched this block.

**Why it is load-bearing.** `internal var VicoScrollState.drawingContext`
(`HEAD $C/cartesian/VicoScrollState.kt:52`) is what `getVisibleAxisLabels` consults:
`if (itemPlacer != null && drawingContext != null) return itemPlacer.getLabelValues(drawingContext!!,
range, fullRange, 0f)` (`HEAD :130-131`). meApp calls `getVisibleAxisLabels` at
`GraphView.kt:408` and `:497`.

**Risk if wrong — and the correction that matters.** Omit the assignment and `getVisibleAxisLabels`
silently falls through to the even-walk strategy, returning **plausible wrong labels**. The chart
still draws; the header names the wrong days. **Silent.**

**Do not "fix" the retention by mirroring C-7's null-out.** `CartesianChart` nulls its own
`drawingContext` at the end of `draw()` (`HEAD CartesianChart.kt:414`), and the obvious tidy-up is to
do the same on `VicoScrollState`. **That would break `getVisibleAxisLabels`**, which meApp calls from
scroll effects *outside* the draw pass (`GraphView.kt:408`, `:497`). The retention past the draw pass
is deliberate. S1-E's open question ("whether that retains a `Canvas`/`DrawScope` across disposal") is
real but the answer is not a null-out; it is S1A-09a's narrowing of the retained reference to the
subset the item placer needs. **Cleanup, not blocker.**

**Noted duplication, no action this migration.** The fork now retains two drawing contexts for two
consumers — `VicoScrollState.drawingContext` (H-9, for `getVisibleAxisLabels`) and
`CartesianChart.drawingContext` (C-7, for `forEachPersistentMarker`). One published geometry snapshot
would serve both. Consolidation candidate for P16, alongside H-3's `Decoration`.

---

## H-10 — chart-area height parity · **DELETE — do not apply**

**Confirmed at v3.3.1, as S2-A and S2-B said, and with one addition they did not make.**

- The function we patched **no longer exists**. `CartesianChartHostBox`
  (`HEAD $C/cartesian/CartesianChartHost.kt:536-540`) is replaced by `ChartHostBox`
  (`v3.3.1 $C/common/ChartHostBox.kt:33-43`), used at `v3.3.1 CartesianChartHost.kt:116` and `:312`
  and shared with `PieChartHost`.
- **The cap is gone and a consumer `Modifier.height()` now wins outright.**
  `ChartHostBox` applies `Modifier.chartAreaHeight(chartAreaHeight, measureExtras)`
  (`ChartHostBox.kt:40`), whose layout block is explicit: `val height = if
  (constraints.hasFixedHeight) constraints.maxHeight else (chartAreaHeight.roundToPx() +
  extras).coerceIn(...)` (`:61-66`). A fixed outer height is honoured and the extras are subtracted
  from the coordinate system instead — stated in upstream's own KDoc at `:45-50`.
- `chartAreaHeight: Dp = Defaults.CARTESIAN_CHART_AREA_HEIGHT.dp` is a **public parameter** on all
  three host overloads (`v3.3.1 :95, :148, :186`), default `192f` (`$C/common/Defaults.kt:61`).

**S3-D's finding confirmed at the consumer: both meApp hosts pin a fixed height.**

| host | site | height |
|---|---|---|
| dashboard | `GraphView.kt:98` | `modifier.height(chartHeight).testTag(…)` |
| snapshot card | `SnapshotLineChart.kt:158` | `.height(SNAPSHOT_CHART_HEIGHT)` |

So `constraints.hasFixedHeight` is true at both, `chartAreaHeight` is ignored, and the behaviour is
what the fork's patch produced. **meApp needs no change and should not pass `chartAreaHeight`.**

**The loud part — the three imports.** S1-E lists them as "dead". At v3.3.1,
`com.patrykandpatrick.vico.compose.common.Defaults.CHART_HEIGHT`
(`HEAD CartesianChartHost.kt:40`) is an **unresolved reference**: `CHART_HEIGHT` exists at
`HEAD $C/common/Defaults.kt:63` and is **absent at v3.3.1**, where only
`CARTESIAN_CHART_AREA_HEIGHT` (`:61`) exists. The other two —
`androidx.compose.ui.unit.dp` (`:29`) and `androidx.compose.ui.draw.alpha` (`:28`) — resolve but are
unused. Remove all three with the deletion.

**Also in this area, and a separate item:** `3b9146ca8` removed an "alpha hiding" phase so the chart
renders immediately and `initialScroll` applies with correct `context.ranges` (comment at
`HEAD :186-187`). That is a *behaviour* change independent of the box, and `alpha` is the import it
left behind. Check it against v3.3.1's `initialAnimationSpec` reveal before assuming it is also
obsolete — do not fold it into H-10's deletion. **Marked for the P4 change, flagged to whoever owns
the reveal animation.**

**One new surface the upstream box introduces — a check, not a blocker.** `measureExtras`
(`v3.3.1 CartesianChartHost.kt:237-247`) calls `chart.getVerticalExtras` → `measure()`
(`$C/cartesian/CartesianChart.kt:337-343`, `:304-330`) from the layout pass, with
`canvasSize = (width, chartAreaHeight.pixels)` — i.e. a height that is **wrong** for meApp's
fixed-height hosts, whose `extras` result is then discarded by the `hasFixedHeight` branch. The call
still mutates chart state: `_markerTargets.clear()`, `layerMargins.clear()`,
`updatePersistentMarkers(...)` (`:309-315`). The draw-pass `prepare` re-runs all of it, so the
*values* are fine — but **the fork's additions to that path have not been checked against being run
twice per frame with a bogus height.** C-7's retained `drawingContext` and `allMarkerTargetXValues`
are the two to look at. **Check during P7**, on the first green host build; cost is one read.

---

# 3. CF-1 resolved — `A7b` DELETE vs `S1A-08c` RE-APPLY

**Resolution: `A7b` stands. `H-7` is DELETE. `S1A-08c` becomes DROP — leave v3.3.1's
`private var initialScrollHandled` untouched.**

### S1-E's two purposes for H-7, established from the code

S1-E gives H-7 two purposes and S3-C disposes of only one. Reading the hunk
(`HEAD $C/cartesian/CartesianChartHost.kt:463-480`), the two are:

> **(a)** push `scrollState.value` / `maxValue` into the start and end `VerticalAxis` via
> `updateScrollState` before `prepare` (`:469-470` in the throwaway pass, `:478-479` in the real one).
>
> **(b)** run `scrollState.update(...)` **inside** the throwaway pass (`:472-474`), so that
> `initialScroll` is applied before the *real* `prepare` — i.e. so measurement happens at the
> post-initial-scroll scroll value. This is what the in-file comment at `:464-467` describes:
> *"so `VerticalAxis.updateHorizontalLayerMargins` sees the post-initialScroll scroll value."*

### (b) is not independent of (a). It has the same single reader.

Purpose (b) only matters if something *measured in `prepare`* reads the scroll value. Exactly one
thing does, and it is (a)'s:

```
HEAD $C/cartesian/axis/VerticalAxis.kt:398-399   private var scrollValue / maxScroll
HEAD $C/cartesian/axis/VerticalAxis.kt:402-405   updateScrollState — the only writer
HEAD $C/cartesian/axis/VerticalAxis.kt:483-499   updateHorizontalLayerMargins — the only reader:
    val scrollOffset = if (scrollSize != null && scrollSize.isLabelsScrollable) {
      if (position == Axis.Position.Vertical.Start) scrollValue else maxScroll - scrollValue
    } else 0f
    val effectiveWidth = (width - scrollOffset).coerceAtLeast(0f)
```

Everything else measured in `prepare` is scroll-independent:
- `CartesianChart.prepare` (`HEAD CartesianChart.kt:233-275`) takes a `CartesianMeasuringContext`,
  which carries no scroll value at all.
- `HorizontalAxis`'s only `scroll` reads at HEAD are in draw methods (`:203`, `:335`).
- `VerticalAxis.drawLineAndTicks`'s `effectiveScroll` (`:350-352`) reads `scroll` from the **drawing**
  context, not `scrollValue`, so it is unaffected either way.

With `isLabelsScrollable == false` — which it always is for meApp (`isLabelsScrollable` defaults
`false` at `BaseAxis.kt:214`; meApp's end axis passes no second argument at
`features/common/components/chart/axis/VerticalAxis.kt:94`; its start axis is `Size.Fixed(0.dp)` at
`:56`) — `scrollOffset = 0f` and `effectiveWidth == width`. **The margin is scroll-independent, so
frame 1 and frame 2 measure identically and the throwaway pass does nothing.**

**So (b) does not survive `A7b`'s deletion, because (b) was never a second purpose.** S1-E
double-counted one mechanism described at two levels of abstraction.

### The draw itself is unaffected — the non-obvious half of the deletion

Deleting the block moves the `scrollState.update(...)` call to its normal position *after* `prepare`
on frame 1. That is safe, and here is why precisely: in the single-pass order
`scrollState.update(...)` (`HEAD :484`) still runs **before** the `CartesianDrawingContext` is
constructed (`:516-527`), so the frame-1 draw already uses the post-`initialScroll` value. Only
measurement sees `scroll = 0`, and measurement does not care. **State this in the port note** — it is
the part of the deletion that looks wrong at a glance.

### `v3.3.1`'s `measure` / `getVerticalExtras` split does not serve the surviving purpose

There is no surviving purpose. And even if there were, upstream's pre-measure could not serve it:
`getVerticalExtras` (`v3.3.1 CartesianChart.kt:337-343`) → `measure` (`:304-330`) is invoked from
`measureExtras` inside `Modifier.layout` (`v3.3.1 CartesianChartHost.kt:237-247`,
`ChartHostBox.kt:51-68`) — during layout, strictly before the Canvas draw and therefore before any
scroll value is available. Upstream's own split is a **height** pre-measure, not a scroll one, and it
only works because measurement is scroll-independent by design. Our two-pass hack and upstream's
pre-measure are not solving overlapping problems after all; they look alike because both run
`measure` twice.

### Therefore `S1A-08c`

`initialScrollHandled` has exactly one reader outside `VicoScrollState` in the compose module:

```
git grep -n "initialScrollHandled" HEAD -- vico/
  $C/cartesian/CartesianChartHost.kt:467                      ← the H-7 guard, deleted here
  $C/cartesian/VicoScrollState.kt:50,250,258,284,308,310,362,363,371   (its own file)
```

meApp never touches it. Once H-7's block goes, the `private → internal` widening has **no consumer**.
S3-A's stated risk — *"reverting to `private` fails to compile at the host — loud"* — is **false**:
there is no longer a host call site to fail. `S1A-08c` = **DROP the widening**; leave
`v3.3.1 $C/cartesian/VicoScrollState.kt:55` as upstream wrote it. That removes one more hunk from
`VicoScrollState.kt`, a file both clusters want to stop patching.

**Independently derived; S5-A `F5` reaches the same verdict.** Recorded as corroboration, not as the
basis — F5 and this section were produced from the same source, separately.

### The check that settles it, and it is cheaper than CF-1's

CF-1 proposes a frame-1-vs-frame-2 screenshot comparison in P0. That is a reasonable belt-and-braces
check but it is not the settling one, because a negative result is ambiguous (the two frames could
match for an unrelated reason). The settling checks are static and take a minute:

1. After `A7b` lands: `git grep -n "scrollValue\|maxScroll" -- $C/cartesian/axis/` returns nothing.
2. `git grep -n "initialScrollHandled" -- vico/` returns only `$C/cartesian/VicoScrollState.kt`.
3. `git grep -n "isLabelsScrollable" -- vico/ app/src` returns nothing in either tree.

**Keep the screenshot comparison, but move it from P0 to P15's goldens, and require it to capture the
first drawn frame** — see H-7's risk note. BL-5 is **unblocked**: `S1A-08c`'s edit can leave P6 and
`H-7` can leave P14's dependency list.

---

# 4. BL-4 resolved — does the `flingBehavior` seam survive, and in what shape?

**Resolution: the seam survives, as a narrowed override. `S1A-05a`'s MOVE is viable. Snap leaves the
library; the seam does not.**

### Why the seam cannot be replaced by `xSnapStep`

Verified at both ends:

- Upstream's path is `xSnapStep: Double?` → `getSnapDelta(targetValue)` → `SnapFlingBehavior`
  (`v3.3.1 VicoScrollState.kt:60, 265-277`; `Modifier.kt:67-72, 191-209`). A single uniform step.
- meApp's `snapToLabel` (`GraphView.kt:212-222`) dispatches to
  `GraphSnapHelper.getSnappedPositionOnDrag(xLabel, segment)` or
  `getSnapPositionOnFling(timeStamp, segment, isForward)` — calendar boundaries, per segment. C2
  settled YEAR (a month is not a uniform millisecond step); WEEK could in principle be a day step.

### Two shapes were costed. Shape A is the recommendation.

**Shape A — keep the override on the host and `Modifier.pointerInput`.**
Re-add `flingBehavior: FlingBehavior? = null` to the public producer overload, to
`CartesianChartHostImpl`, and to `Modifier.pointerInput`; inside `Modifier.pointerInput`, prefer the
override and fall back to upstream's own block:

```
val flingBehavior = flingBehaviorOverride ?: remember(scrollState, defaultFlingBehavior, decayAnimationSpec) {
    if (scrollState.xSnapStep != null) SnapFlingBehavior(scrollState, decayAnimationSpec) else defaultFlingBehavior
}
```

- **Library cost:** 3 added parameters (2 of them public) and ~2 changed lines in
  `v3.3.1 Modifier.kt:63-72`. Upstream's `xSnapStep` path is untouched and still works for anyone
  else.
- **What it buys:** the *entire* snap implementation leaves the library. `ChartSnapFlingBehavior`
  needs only `scrollState.scrollValueToX()`, `scrollState.value`, `scrollState.maxValue` and
  `scrollState.xToScrollValueBehindStartInset(x)` (`HEAD $C/cartesian/SnapFlingBehavior.kt:77, 86,
  90-92`), plus `ScrollScope.scrollBy` (public Compose). `value` and `maxValue` are **already public**
  at v3.3.1 (`VicoScrollState.kt:80, 89`). The other two are fork-`internal` (`HEAD :105`, `:98`) and
  `S1A-06` already widens them to public. So with `S1A-06` + `S1A-10`, meApp can own
  `SnapBehaviorConfig` + `ChartSnapFlingBehavior` + `rememberChartSnapFlingBehavior` outright, and
  the fork's `$C/cartesian/SnapFlingBehavior.kt` (~130 lines) is **deleted**.
- **Net fork surface: +3 parameters, −1 file.**

**Shape B — replace `xSnapStep` with a snap-target lambda on `VicoScrollState`** (e.g.
`snapTarget: ((targetX: Double) -> Double?)?` consulted inside `getSnapDelta`), so no host or
`Modifier` parameter is needed at all.

- **Rejected**, for three reasons.
  1. It **kills `S1A-05a`**: the snap algorithm stays inside the library, because upstream's
     `SnapFlingBehavior` is what performs the fling.
  2. It patches `VicoScrollState.kt` — constructor, `rememberVicoScrollState`, the `Saver` input list
     and `getSnapDelta` — the file both clusters are trying to stop patching (and the one `S1A-08c`
     just stopped patching).
  3. It **changes shipped gesture behaviour.** `isForward` is recoverable (`targetX > currentX`);
     `isDrag` is not — upstream hands `getSnapDelta` only the decayed target, so the fork's
     `abs(initialVelocity) < 500f` split between `getSnappedPositionOnDrag` and
     `getSnapPositionOnFling` cannot be reconstructed. C1 is the record of what a confident
     behaviour-equivalence claim costs. Do not stack a second.

### Do not split WEEK onto `xSnapStep`

C2 leaves open that "WEEK can plausibly move to upstream's snap". **Recommend against it.** Two snap
mechanisms for one product feature doubles the test matrix, and the two paths would have different
animation feel (upstream: `snapAnimationSpec`, default `spring()`; ours: `tween(500,
LinearOutSlowInEasing)` — `GraphView.kt:223`, `HEAD SnapFlingBehavior.kt:104-107`) on two tabs of the
same screen. One mechanism, one seam. **BL-4's proposed experiment** (build a WEEK chart with
`rememberVicoScrollState(xSnapStep = one day)` and compare rest positions) therefore becomes
*informational*, not blocking — run it only if someone wants to reopen the split.

### The check that actually gates this

**Can `scrollValueToX` and `xToScrollValueBehindStartInset` be public?** That is `S1A-06`'s widening,
and it is what decides whether snap can leave the library. If it cannot (e.g. the signatures turn out
to expose something the fork does not want public), Shape A still keeps snapping working — the seam is
unaffected — but `S1A-05a` reverts from MOVE to RE-APPLY and the fork keeps the file, renamed per C4.
**Settle in P14, before `S1A-05a` is scheduled into P16.**

### Consequences to book

- `M-3` (S3-B, "RE-DERIVE pending C2") is now unblocked: RE-DERIVE as the override parameter.
- `S1A-05a` (MOVE snap to meApp) is viable; schedule in P16 behind `S1A-06` and `S1A-10`.
- `S1A-05b` (`isSnapping`) remains DELETE per C5 — and becomes moot once the class leaves.
- C4's file-name hazard is resolved **by deletion** if `S1A-05a` lands; by **rename** if it does not.
- DROP `ScrollAwareRangeEffect`'s unused `flingBehavior` parameter (`HEAD CartesianChartHost.kt:174`,
  `:567`) either way.

---

# 5. Completed consumer-API additions (S3-D Part 2 §2.0.1)

S3-D's table has 22 rows and is incomplete (S5-B §3.4). **18 rows added below**, in three groups.
Every declaration line and every call site was read; "absent at v3.3.1" means verified by reading the
v3.3.1 declaration, not by its absence from a grep.

## 5.1 Additions — fork-only API meApp consumes, missing from S3-D's table

| Fork API | Declared at `HEAD` | At `v3.3.1` | meApp call sites | What the consumer must do |
|---|---|---|---|---|
| `rememberVicoScrollState(key = …)` | `$C/cartesian/VicoScrollState.kt:386` (param), first `rememberSaveable` input at `:389` | **absent** — param list ends at `snapAnimationSpec` (`v3.3.1 :338-345`) | `GraphView.kt:197-201` (`key = Triple(segment, isEmptyGraph, endTimestamp)`). **`SnapshotLineChart.kt:167-170` does not pass it** | **`S1A-08b` MOVE.** Wrap the call in Compose's `key(segment, isEmptyGraph, endTimestamp) { … }`. Verify the A→B→A segment switch re-applies `initialScroll` (S3-A's own caveat); if it does not, fall back to RE-APPLY re-sited to the end of upstream's input list |
| `VicoScrollState.visibleXRange` | `$C/cartesian/VicoScrollState.kt:77` (`public val`, nullable) | **absent** — v3.3.1's public surface on `VicoScrollState` is `value` (`:80`) and `maxValue` (`:89`) only | `GraphView.kt:401`, `:444`, `:496` | **Kept** (`S1A-06` RE-APPLY). No consumer change. **This row is in neither S3-D's table nor S5-B's gap list** — three live call sites with no entry anywhere |
| `VicoScrollState.getVisibleAxisLabels(itemPlacer, labelProvider)` | `$C/cartesian/VicoScrollState.kt:118-120` | **absent** | `GraphView.kt:408`, `:497` | **Kept** (`S1A-09a`). No consumer change — **but it is silently wrong without `H-9`**: `:130-131` gates the item-placer path on `drawingContext != null` and otherwise falls through to the even-walk strategy |
| `VicoScrollState.getInterpolatedYValues(xValues, interpolationType)` | `$C/cartesian/VicoScrollState.kt:152` | **absent** | `GraphView.kt:413-416` | **`S1A-09b` MOVE.** Re-implement in meApp alongside the moved `MonotoneInterpolator` (`L1`) and `InterpolationType` (`L2a`). **Carry `25dc072a9`'s contract: outside the data range return `null`, do not clamp** — a clamping re-implementation puts confident wrong numbers in the header. Silent |
| `Scroll.Absolute.xBehindStartInset(x, bias)` | `$C/cartesian/Scroll.kt:73-77` | **absent** — `Scroll.kt` is byte-identical fork-point↔v3.3.1, so this is purely ours | `GraphView.kt:188`, `SnapshotLineChart.kt:94` | **`S1A-10` MOVE.** Declare in meApp verbatim — `Scroll.Absolute` is a `public fun interface` and every parameter type is public. Drop the stale KDoc block at `HEAD Scroll.kt:58-62`, which documents a removed `xWithPadding` |
| `rememberScrubMarkerController(scrollState = …)` | the `scrollState` parameter | **absent** (whole class is fork-only; S3-D has a row for the class, none for this parameter) | `GraphView.kt:268-269` | **`B-1` deletes the parameter.** Remove the argument at the call site. The `onMarkerIndexChanged` argument stays |

## 5.2 Additions — the `DefaultCartesianMarker` constructor surface

**S3-D's table has no `DefaultCartesianMarker` row at all, yet `B-4b`, `B-6`, `B-7` and `B-8` all
land here.** The whole fork-only surface, from the two declarations:

```
HEAD    $C/cartesian/marker/DefaultCartesianMarker.kt:80-96   (class ctor)
HEAD    $C/cartesian/marker/DefaultCartesianMarker.kt:668-684 (rememberDefaultCartesianMarker)
v3.3.1  $C/cartesian/marker/DefaultCartesianMarker.kt:59-66   (class ctor)
v3.3.1  $C/cartesian/marker/DefaultCartesianMarker.kt:433-442 (rememberDefaultCartesianMarker)
```

Upstream's parameter list ends at `guideline`. Everything after it is ours.

| Fork API | Declared at `HEAD` | At `v3.3.1` | meApp call sites | What the consumer must do |
|---|---|---|---|---|
| `label: TextComponent?` **nullable** | ctor `:81`, remember `:669` | **present but non-null and required** — `label: TextComponent` with no default, and the **first positional parameter** (`v3.3.1 :60`, `:434`) | `GraphMarker.kt:75-85` (`rememberRangeOnlyMarker` passes **no `label` at all**), `:139-140` (passes one) | **`B-6` REPLACE**, contingent on the subclass landing. `rememberRangeOnlyMarker` does **not compile** at stock v3.3.1 — `label` has no default. Note also `v3.3.1 :68-69` is `label.background`, un-null-safe, so the nullability is not a one-line change in the ctor either. If the subclass does not land, B-6 reverts to RE-APPLY |
| `restingLabel: TextComponent?` | ctor `:92`, remember `:681` | absent | `GraphMarker.kt:80`, `:156` | **`B-4b` MOVE** to a meApp `DefaultCartesianMarker` subclass |
| `restingLabelFormatter: ((ClosedFloatingPointRange<Double>) -> CharSequence?)?` | ctor `:93-94`, remember `:682` | absent | `GraphMarker.kt:82` (`{ rangeText }`), `:157` (`rangeLabelFormatter(segment, segmentState)`, defined `:193-207`) | **`B-4b` MOVE.** Its parameter *is* `onScrollSettled`'s payload — H-4's feed and B-5's hook are what deliver it |
| `labelGap: Dp` | ctor `:95`, remember `:683` | absent | `GraphMarker.kt:83`, `:158` (`CHART_LABEL_GAP = 10.dp`, `:36`) | **`B-4b` MOVE.** `CHART_RESTING_LABEL_BAND` (`GraphMarker.kt:43`) is meApp's own mirror of the reservation and must stay in step with whatever the subclass reserves |
| `restingLabelAnchor()` | `protected open fun CartesianDrawingContext.restingLabelAnchor(): Pair<Float, Position.Vertical>`, `:410`; called `:392` | absent | **none today** | **Arrival.** It is the hook the meApp subclass overrides, not something meApp calls now. It must be `protected open` on whatever remains in the library, or `B-4b` cannot be a subclass |
| `yLabelCallback: ((List<List<Double>>) -> Unit)?` | ctor `:89`, remember `:678` | absent | `GraphMarker.kt:153` (built at `:108-123`) | **`B-8` MOVE** to the subclass. Carry the empty-list-for-non-line-targets contract. Gated by S3-B `U-1` (does the fallback path run) — instrument in P1 |
| `horizontalLabelPosition: Position.Horizontal?` | ctor `:90`, remember `:679` | absent | `GraphMarker.kt:154` (derived at `:99-100`: non-null only when a crosshair label is supplied — i.e. baby only) | **`B-7` MOVE** to the subclass |
| `horizontalLabelFormatter: ((List<List<Double>>, Double) -> CharSequence?)?` | ctor `:91`, remember `:680` | absent | `GraphMarker.kt:155` | **`B-7` MOVE**. S5-A `F8` corrects S3-B's internal-helper list for this move — check it before starting |
| `contentPadding: Insets` | ctor `:87`, remember `:677` | absent | **none** — `grep -rn contentPadding` over meApp's chart packages returns only unrelated Compose `PaddingValues` (`DashboardSnapshotScreen.kt:130`, `DashboardMileStone.kt:70`, `BpDashboardContent.kt:184`) | **`B-9` DELETE, zero consumer cost.** The row exists to record that it is free — it is the only fork-only marker parameter with no meApp caller |

## 5.3 Additions — removals from, and arrivals on, meApp's surface

S3-D's table models only *additions* (API meApp consumes that v3.3.1 lacks). These three are the
other two shapes, and they are where the silent risk is.

| Item | Declared at `HEAD` | At `v3.3.1` | meApp call sites | What the consumer must do |
|---|---|---|---|---|
| **REMOVAL** `rememberLineCartesianLayer(alwaysUseLiveRange = …)` + `LineCartesianLayer.alwaysUseLiveRange` | `$C/cartesian/layer/LineCartesianLayer.kt:576` (`public var`), `:1244` (param), `:1273` (assignment); guard at `:614` `rangeProvider is ScrollAwareRangeProvider \|\| alwaysUseLiveRange` | absent | `PrimaryLayer.kt:41` (param), `:73` (pass-through), `:98` (`= true` in `secondaryLayer`); commentary at `ProductChart.kt:302` | **`L5` RE-DERIVE as `CartesianLayerRangeProvider.isStable`.** meApp deletes the parameter and its two pass-throughs and answers `isStable` from the provider instead. **See the trap below — this is not the one-line change S3-C describes.** |
| **REMOVAL** `rememberLineCartesianLayer(yTransform = …)` + `LineCartesianLayer.yTransform` | `$C/cartesian/layer/LineCartesianLayer.kt:114-122` (`protected val`), `:1238-1242` (param); used `:838, 865, 878, 1066` | absent | **exactly one supplying site**: `ProductChart.kt:324-340`. Pass-through declarations at `PrimaryLayer.kt:36-40` (`rememberLineLayerWithConnection`) and `:86-90` (`secondaryLayer`), forwarded at `:72` and `:97` | **`L3` Option A RE-DERIVE the seam.** meApp writes a ~120-line `CartesianLayerYMapper` implementation (the `DoubleArray` cache, index map, three keys, fade state machine, `TimeSource`) and passes `yMapper =` instead; the two pass-through declarations change type. Take S3-C's five verbatim invariants and the `series.hashCode()` → `MetricBasis.version` swap with the move |
| **ARRIVAL** `rememberLineCartesianLayer(contributesYRange = …)` | **does not exist** — the behaviour is *inferred* from `yTransform != null` at `$C/cartesian/layer/LineCartesianLayer.kt:1066-1072` (`updateChartRanges` calls `tryUpdateX` and returns early) | n/a (new) | will be **one** site: `ProductChart.kt:320-323`'s `secLayer`, via `secondaryLayer` | **`L4` RE-DERIVE as an explicit flag.** meApp must pass `contributesYRange = false` at the secondary-overlay site. **This is the most losable row in the table — see below** |

### The trap in the `alwaysUseLiveRange` removal

S3-C `L5` says *"meApp's delegating `rememberFollowing` wrapper (`ProductChart.kt:276-290`) answers
`false` naturally"*, treating `rememberFollowing` as one thing. **It has two call sites with opposite
requirements:**

```
ProductChart.kt:323   rememberFollowing(scrollAwareRange, basis)   → secondaryLayer  → alwaysUseLiveRange = true   (PrimaryLayer.kt:98)
ProductChart.kt:303   rememberFollowing(scrollAwareRange)          → rememberPercentileBand → alwaysUseLiveRange = false (default)
```

and meApp's own comment at `ProductChart.kt:302` records why the second must stay off:
*"alwaysUseLiveRange stays off — it bypasses the provider and flashes the Y axis on frame 0."*

The library guard is a disjunction (`HEAD LineCartesianLayer.kt:614`), so the three providers in play
must answer three ways: the bare `ScrollAwareRangeProvider` (primary layer) → `isStable = false`; the
`basis`-carrying wrapper (secondary) → `false`; the bare wrapper (percentile band) → **`true`**.

**That is achievable** — they are distinct anonymous objects (`ProductChart.kt:282-292`) — **but it
is not what S3-C describes.** `rememberFollowing` must gain a parameter
(`rememberFollowing(scrollAwareRange, basis, isStable = …)`) or split into two. If both wrappers
answer `false`, the percentile band regresses to the frame-0 Y-axis flash that comment was written
about. **Silent.**
**Flagged to the layer cluster as an amendment to `L5`.**

### Why `contributesYRange` is the most losable row

`contributesYRange: Boolean = true` is a **defaulted** parameter. If meApp never passes it, meApp
**compiles clean and is silently wrong**: the overlay layer resumes unioning its raw values into the
chart range, and the defect recorded verbatim in the fork's own comment returns —

> *"Unioning its raw values in widened the axis to cover readings in a different unit entirely — a
> pulse of 60 against a weight of 150 opened the axis at zero — and because a union only ever widens,
> the first frame of a tab kept that range until the animation replaced it."*
> — `HEAD $C/cartesian/layer/LineCartesianLayer.kt:1063-1065`

Today the flag cannot be forgotten, because it is inferred from the presence of `yTransform`.
Making it explicit is right (S3-C is correct that the general statement is upstream-shaped and that
it is what lets `L3` be deleted later), but **it converts a structurally-enforced invariant into a
remembered one.** Two mitigations, both cheap:

1. Make it **non-defaulted** on the fork's `LineCartesianLayer` constructor, so omission is a compile
   error. The remember-function can still default it.
2. Failing that, P15's parity check for the weight-with-secondary chart must assert the Y-axis
   **minimum** on the first frame of a tab entry, not just the settled frame — that is the only frame
   on which the regression is visible.

This is the same failure shape as C1: a correct-looking refactor that silently removes shipped
behaviour. **Flagged to the layer cluster.**

---

# 6. UNRESOLVED

| # | Item | Blocks | The exact check that settles it |
|---|---|---|---|
| **E-1** | Does upstream's layout-pass `measure()` (via `measureExtras`, run with a height that meApp's fixed-height hosts discard) interact badly with the fork's additions to that path — C-7's retained `drawingContext` and `allMarkerTargetXValues`? | nothing; a P7 observation | On the first green host build, log `_markerTargets.size` and `drawingContext != null` at the top of `measure()` and at the top of `draw()` for one frame of the dashboard chart. Expect: `measure()` runs twice per frame (layout + prepare) and `drawingContext` is null in both |
| **E-2** | Does `S1A-06` widen `scrollValueToX` and `xToScrollValueBehindStartInset` to `public`? | `S1A-05a` only (P16). Not the H-8 seam | Read the two signatures (`HEAD $C/cartesian/VicoScrollState.kt:105`, `:98`) against what the fork is willing to make public. If no, `S1A-05a` reverts MOVE → RE-APPLY and the file is renamed per C4 |
| **E-3** | `3b9146ca8`'s "alpha hiding" removal (`HEAD CartesianChartHost.kt:186-204`) — is it also obsoleted by v3.3.1's `initialAnimationSpec` reveal, or is it an independent behaviour we must carry? | P4's import cleanup touches the same lines | Read `v3.3.1`'s `collectAsState(chart, animationSpec, initialAnimationSpec, mutableRanges)` reveal path end to end and compare against the comment at `HEAD :186-187`. **Do not fold it into H-10's deletion on the strength of adjacency** |

---

# 7. What this dossier changes elsewhere

| Dossier | Row | Change |
|---|---|---|
| S3-A | `S1A-08c` | RE-APPLY → **DROP**. Stated risk "loud (compile)" is false. CF-1 / BL-5 closed |
| S3-A | `S1A-05a` | MOVE **confirmed viable**, gated on E-2 rather than on BL-4 |
| S3-B | `M-3` | "RE-DERIVE, pending C2" → **RE-DERIVE as an override parameter**. BL-4 closed |
| S3-B | `B-3a` / `M-4` | unchanged, but **must be stated together with H-6's `true`/`false` return policy** (§2 H-6) |
| S3-C | `A7b` | **confirmed**, with the non-obvious half written down: the early `scrollState.update` moves after `prepare` and that is safe because the draw-context construction still follows it |
| S3-C | `L5` | **amended** — `rememberFollowing` has two call sites with opposite `isStable` requirements |
| S3-C | `L4` | **amended** — recommend non-defaulted on the constructor; the inferred invariant becomes a remembered one |
| S3-C | `L6`, `L7`, `L2b` | **unblocked** — C-7 survives, so all three keep their second enforcement point / call site |
| S3-B | `B-10` | **unblocked** — same |
| S3-D | §2.0.1 | **+18 rows** (§5). The 22-API coupling surface is 40 |
| S5-B | CF-1, BL-4, BL-5, §3.3, §3.4 | closed |
| S5-B | CF-4 | unchanged — still a P16 budget decision, now with H-4 given a verdict so the decision has something to act on |
