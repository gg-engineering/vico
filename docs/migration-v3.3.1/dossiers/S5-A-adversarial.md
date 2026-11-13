# S5-A — Adversarial review of the S3 disposition verdicts

Attack pass on `S3-A-disposition-scroll-range.md`, `S3-B-disposition-marker-scrub.md` and
`S3-C-disposition-axis-layer.md`. Targets, in the brief's order: every DELETE, every MOVE, every
REPLACE, the cross-cluster couplings, and S3-C's three corrected premises P1/P2/P3.

Everything below was verified against `v3.3.1` (`3d675ccc7`), `HEAD`
(`MOB-2953-week-range-spans-eight-days`), `e67e49520` and the meApp checkout
(`/Users/selvakumar/Projects/meApp-bug/Android`). Read-only throughout — `git show` / `git diff` /
`git grep` only; the shared working tree was not touched.

Path prefix `$C = vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose`.

**Outcome vocabulary.** **WRONG** — the verdict or a load-bearing claim under it fails, with
evidence. **UNPROVEN** — the reasoning does not establish the claim; the conclusion may still hold,
and the named check settles it. **CONFIRMED** — attacked and could not be broken.

**Headline: 5 WRONG, 3 UNPROVEN, 21 CONFIRMED.** Nothing found here overturns a Stage-3 cluster
wholesale. One finding (F1) would silently lose a shipped label; two (F2, F3) would silently
re-introduce the MOB-3494 defect shape or clip the goal pill; the rest are incomplete checklists
and one cross-cluster verdict that must flip.

---

# Part 1 — Findings that would silently lose shipped behaviour

## F1 — **WRONG.** `A4a` (S3-C, MOVE): the verdict describes half the adaptation, and the other half cannot be moved

**The verdict challenged.** S3-C A4a: *"MOVE → meApp `HorizontalItemPlacer`. meApp already owns a
full `HorizontalAxis.ItemPlacer by defaultPlacer` wrapper … Apply `closesTheWindow` in the `else ->`
branches of both."* Risk stated: "silent — the eight-day week returns".

**What the adaptation actually is.** The whole of it:

```
git diff e67e49520 HEAD -- $C/cartesian/axis/HorizontalAxisItemPlacers.kt
```

Inside the private `CartesianDrawingContext.getLabelValues` loop (v3.3.1 `HorizontalAxisItemPlacers.kt:40-67`)
the fork makes **two** changes, not one:

```kotlin
-   if (potentialValue < ranges.minX || potentialValue == fullXRange.start) continue
-   if (potentialValue > ranges.maxX || potentialValue == fullXRange.endInclusive) break
-   values += potentialValue
+   if (potentialValue < ranges.minX) continue
+   if (potentialValue > ranges.maxX) break
+   val closesTheWindow = abs(potentialValue - visibleXRange.endInclusive) < endEpsilon
+   if (!closesTheWindow) values += potentialValue
```

1. the `closesTheWindow` epsilon filter — the half S3-C describes;
2. **the deletion of upstream's two `fullXRange` guards** — not mentioned anywhere in S3-C's A4a
   entry, its risk line, or its sequencing.

**Why (2) cannot be reproduced from meApp.** meApp's wrapper is
`object : HorizontalAxis.ItemPlacer by defaultPlacer` with an `else -> defaultPlacer.getLabelValues(…)`
delegation (`features/common/components/chart/HorizontalItemPlacer.kt:40-97`, read in full). A
delegating wrapper can only **subtract** values from the list upstream returns. It cannot add back a
value upstream's `break` suppressed — the loop exits before `values += potentialValue`.

**And (2) is load-bearing for the WEEK/TOTAL path, which is exactly the `else ->` branch.** meApp's
end padding is zero in the post-migration shape: `L9` keeps our removal of `maxPointSize.half`
(HEAD `$C/cartesian/layer/LineCartesianLayer.kt:1051` vs v3.3.1 `:901`), `A3a` makes
`HorizontalAxis.updateLayerDimensions` return early (v3.3.1 `$C/cartesian/axis/HorizontalAxis.kt:573`),
and meApp passes no `unscalableEnd` (`ProductChart.kt:87-105` — confirmed, it passes only
`startInsetDp`). With `endPadding == 0`, `getFullXRange` gives
`fullXRange.endInclusive == ranges.maxX` (`$C/cartesian/CartesianMeasuringContext.kt:62-67`), so
upstream's guard `potentialValue == fullXRange.endInclusive -> break` fires exactly on the last
data x and drops its label. Our fork includes it.

**Second-order:** `A5` (`isChartEdge` guideline suppression) exists *because* these guards were
removed — `fullXRange`-edge values only reach `drawGuidelines` at all once the placer stops
suppressing them. S3-C lists A5 as blocked by C-2 only. **A4a and A5 are one feature.**

**What the verdict should become.** A4a stays MOVE **only** if meApp's wrapper computes its own
value list for the `else ->` branch (the way it already does for YEAR and MONTH) rather than
delegating and filtering. If it delegates, A4a is **RE-APPLY**, because the guard removal is a
change to a private library loop with no consumer-side expression. Either way, A4a must be
sequenced **with A5**, not as an independent Phase-2 item.

**Check that settles which form is needed.** On a WEEK and a TOTAL chart, log
`ranges.maxX`, `fullXRange.endInclusive` and the placer's returned label list at scroll positions
where `maxX` sits strictly inside the visible window. If `maxX` appears in the list today and not
after a delegate-and-filter implementation, the last date label is being lost.

*(The A4a′ half — the `SegmentedHorizontalAxisItemPlacer` hunk, visible in the same diff — is
confirmed dead for meApp: `HorizontalItemPlacer.kt:40` uses `aligned()`, never `segmented()`.)*

---

## F2 — **UNPROVEN, leaning WRONG.** `A6′` (S3-C, DELETE): the stated reason is logically invalid, and the conclusion is font-scale dependent

**The verdict challenged.** S3-C A6′: *"`getMarkerDecorationWidth` is inert for the only consumer —
meApp pins `Size.Scroll(YGutterWidth = 50.dp)`, **a fixed width, so the `max()` never fires**.
Delete it."* Risk stated: "none".

**The reasoning does not hold.** `HEAD $C/cartesian/axis/VerticalAxis.kt:545-556`:

```kotlin
val markerWidth = getMarkerDecorationWidth()
max(
  markerWidth,
  when (size) { is Size.Auto -> … ; … }
)
```

The max fires iff `markerWidth > 50.dp`. Whether the size branch is *fixed* or *computed* is
irrelevant to that comparison. The dossier's premise and its conclusion are unconnected.

**And the quantity is close to the threshold.** The pill is
`rememberTextComponent(style = labelMediumRegular, padding = Insets(horizontal = 10.dp, …),
background = CircleShape)` (`features/common/components/chart/GraphDecoration.kt:40-47`);
`labelMediumRegular` is **12sp** (`theme/token/Type.kt:72`); `YGutterWidth = 50.dp`
(`features/common/components/chart/axis/VerticalAxis.kt:40`). The 20 dp of horizontal padding does
**not** scale with fontScale; the text does, up to meApp's cap `MAX_FONT_SCALE = 1.3f`
(`theme/MeTheme.kt:72, 158-159`). A three-character label at 1.3× is ≈46 dp — under. A
**four-character** label (`WeightlessDisplay.signedLabel` produces a signed value in weightless
mode, e.g. `+100`) at 1.3× is ≈54 dp — **over**.

If the pill does exceed the gutter, deleting A6′ does not produce "none": the pill is centred on the
gutter, so it overhangs by half the excess, and on an **end** axis that half runs off the canvas —
the exact defect the HEAD KDoc at `VerticalAxis.kt:530-534` records.

**Check that settles it.** Render the weight dashboard at `fontScale = 1.3` with the longest goal
label the product allows (signed, weightless on) and measure the pill's `getWidth` against 50 dp.
If it exceeds, A6′ is not a free DELETE — either keep it, or widen `YGutterWidth` in the same change
(S3-C's own named fallback, which should then be promoted from a footnote to part of the verdict).

---

## F3 — **UNPROVEN.** Three independent MOVEs converge on `decorations`, which is a range-recomputation key. Nobody owns the stability requirement

**The verdicts challenged.** S3-C `A1` (separators → `Decoration`), S3-C `A6` (goal pill →
`Decoration`), S3-A `H-3` (window probe → `Decoration`), and S3-C `C-2` (`startInsetDp` →
`layerPadding` lambda). Each is individually sound (see Part 3). Together they change what triggers
a full range recomputation, and only S3-A notices, for H-3 alone.

**The mechanism, at v3.3.1.**

- `rememberCartesianChart` `remember`s on `layerPadding` and `decorations` among its keys
  (`$C/cartesian/CartesianChart.kt:712-725`).
- `CartesianChart.equals` compares `layerPadding == other.layerPadding` and
  `decorations == other.decorations` (`:619-635`).
- The host runs `remember(chart, model) { ranges.reset(); chart.updateRanges(ranges, model) }`
  (`$C/cartesian/CartesianChartHost.kt:189-191`) — keyed on that equality.
- S3-A's H-1 and S1A-03 put the `ScrollAwareRangeProvider` **seeding** inside that same block,
  before `updateRanges`.

So after these four verdicts land, "the goal changed", "the separator list was rebuilt", "the probe
was re-allocated" and "the `layerPadding` lambda captured something" each become a trigger for
`ranges.reset()` + re-seed. That is the loop whose defect history is MOB-3494.

**Two concrete landmines.**
1. **`C-2`'s lambda.** `layerPadding = { CartesianLayerPadding(unscalableStart = ChartStartGap) }`
   is non-capturing (`ChartStartGap` is a top-level val) and therefore a singleton — safe. The
   moment anyone writes `{ CartesianLayerPadding(unscalableStart = if (isSnapshot) a else b) }` it
   captures, allocates per composition, and the chart becomes unequal every recomposition. Neither
   S3-A §2(a) nor S3-C C-2 states this constraint.
2. **The decoration list.** meApp passes **no** `decorations` today (verified: zero hits for
   `decorations` under `app/src`), so this is an entirely new surface, introduced by three dossiers
   that each assumed they were the only new entry.

**Check before any of the three MOVEs lands.** Instrument the `remember(chart, model)` block to log
each entry, then exercise: scroll, segment switch, goal edit, unit toggle, rotation. It must fire
once per genuine model/config change and never per frame or per recomposition. **Record the
stability requirement once, in the synthesis, as a precondition on A1 + A6 + H-3 + C-2 jointly** —
not three times as a footnote.

---

## F4 — **UNPROVEN.** `C-1`'s re-derivation is equivalent only if `A3a` lands; the coupling is unrecorded

**The verdicts challenged.** S3-C `C-2` ("C-1 needs one re-derivation … it must read
`layerDimensions.unscalableStartPadding` instead. Same number, different source") and S3-A §2(a)
caveat 2, which says the same.

**It is the same number only under A3a.** `calculateLabelSpacingScale` subtracts
`startInsetDp.pixels` today (`HEAD $C/cartesian/CartesianChart.kt:285, 304-317`) — a quantity that
is *only* the fork's inset, because at HEAD `HorizontalAxis.updateLayerDimensions` is
`: Unit = Unit`. At v3.3.1 that method contributes **half the first label's padded width** to the
same field (`$C/cartesian/axis/HorizontalAxis.kt:568-600`) and skips doing so only when
`itemPlacer.getShiftExtremeLabels(context)` returns true (`:573`) — i.e. only when **A3a** has
landed in meApp's `HorizontalItemPlacer`.

So `layerDimensions.unscalableStartPadding == ChartStartGap` holds under (L9 ∧ A3a ∧ C-2). Drop or
revert A3a and the term silently grows by half a label width, which changes `visibleLabelsCount`'s
scale factor — "the thing the whole feature exists for", in S3-C's own words.

**Verdict change:** add **A3a** to C-1's "blocked by" column. S3-C's dependency table currently
records only `C-2 blocks … C-1`.

**Check.** After A3a + C-2 land, assert at runtime that `layerDimensions.unscalableStartPadding`
equals `ChartStartGap.pixels` exactly, on WEEK/MONTH/YEAR/TOTAL.

---

# Part 2 — Findings that would block or mis-scope a plan

## F5 — **WRONG (cross-cluster).** `A7b` DELETE (S3-C) and `S1A-08c` RE-APPLY (S3-A) cannot both stand

**The two verdicts.** S3-C A7b: DELETE `isLabelsScrollable` / `scrollValue` / `maxScroll` /
`updateScrollState` / `effectiveScroll`, *"and — the real prize — H-7's entire throwaway first
`prepare` pass"*. S3-A S1A-08c: RE-APPLY the `initialScrollHandled` `private → internal` widening,
risk **"loud (compile)"**, *"This row exists so the widening is not deleted along with S1A-08a."*

**A7b's own half is sound.** Verified: `isLabelsScrollable` defaults `false`
(`HEAD $C/cartesian/axis/BaseAxis.kt:214`); its single behavioural read is
`HEAD $C/cartesian/axis/VerticalAxis.kt:491-494`; meApp passes `BaseAxis.Size.Scroll(YGutterWidth)`
with no second argument (`features/common/components/chart/axis/VerticalAxis.kt:94`) and `grep -rn
isLabelsScrollable app/src` returns nothing. With `isLabelsScrollable == false`,
`updateHorizontalLayerMargins` computes `scrollOffset = 0f` and `effectiveWidth == width`, so the
axis's margin is scroll-independent — which is the *entire* stated purpose of H-7's first pass
(`HEAD $C/cartesian/CartesianChartHost.kt:464-475`, comment read in full). **A7b DELETE: confirmed.**

**The contradiction.** `initialScrollHandled` has exactly one reader outside `VicoScrollState` in
the compose module:

```
git grep -n "initialScrollHandled" HEAD -- vico/
  → $C/cartesian/CartesianChartHost.kt:467      ← the H-7 guard
  → $C/cartesian/VicoScrollState.kt:50,250,258,284,308,310,362,363,371  (own file)
  → vico/views/…/ScrollHandler.kt               (private local copy; module DELETED at v3.3.1)
```

meApp never touches it. So once A7b removes the `if (!scrollState.initialScrollHandled) { … }`
block, the widening has **no consumer**: reverting it to `private` is not a compile error, and
S3-A's "loud" risk rating is false.

**Verdict change:** `S1A-08c` becomes **DELETE** (leave v3.3.1's `private var initialScrollHandled`
at `$C/cartesian/VicoScrollState.kt:55` untouched), contingent on A7b. That removes one more hunk
from `VicoScrollState.kt`, a file both clusters want to stop patching.

**One thing A7b's owner must carry over that S3-C does not mention.** The deleted block also runs an
**early `scrollState.update(…)`** (Host:472-474), which is what applies `initialScroll` before the
second `prepare`. Deleting the block moves that call to its normal position *after* `prepare` on
frame 1. That is safe only because `prepare` is scroll-independent once A7b is gone — state that
explicitly in the port note, because it is the non-obvious part of the deletion.

---

## F6 — **WRONG.** `A11` (S3-C, MOVE): "Nothing internal is touched" is false

**The claim.** S3-C A11: *"our `getTopLayerMargin`/`getBottomLayerMargin` are character-identical to
`StepVerticalAxisItemPlacer.kt:61-88` at v3.3.1, modulo line wrapping. **Nothing internal is
touched.** Move the file."*

**The drift half is correct** — I diffed `HEAD $C/cartesian/axis/ListItemPlacer.kt:91-117` against
`v3.3.1 $C/cartesian/axis/StepVerticalAxisItemPlacer.kt:61-88` and they are identical modulo
wrapping. **Confirmed.**

**The "nothing internal" half is false.** `HEAD ListItemPlacer.kt:22`:

```kotlin
import com.patrykandpatrick.vico.compose.common.half
```

and `half` is `internal inline val Float.half` at `v3.3.1 $C/common/Math.kt:68`. It is used four
times — `ListItemPlacer.kt:99, 102, 115, 116`. A meApp file cannot import it.

**Consequence:** not blocking — replace with `/ 2f` or a local `private inline val Float.half` — but
the migration note must say so, because this is precisely the class of fact the brief asks for, and
"nothing internal is touched" is the sentence that would stop anyone looking.

*(For completeness, the same scan over the other two moved files: `MonotoneInterpolator.kt` imports
nothing internal — only `Offset`, `Path`, `CartesianDrawingContext`, `LineCartesianLayerModel`,
`abs`, `min`, all public. `InterpolationType.kt` is clean.)*

---

## F7 — **WRONG.** `L1` (S3-C, MOVE): two meApp call sites listed; there are three

S3-C: *"change meApp's two call sites (`chart/layer/DataLine.kt:47`, `chart/layer/PercentileBand.kt:40`)
from `LineCartesianLayer.Interpolator.monotone()` to the meApp object."*

```
grep -rn "Interpolator.monotone()" app/src
  features/common/components/chart/layer/PercentileBand.kt:39
  features/common/components/chart/layer/DataLine.kt:47
  features/common/components/chart/PrimaryLayer.kt:50     ← missing from the dossier
```

Loud failure (compile error), so low consequence — but the migration checklist is incomplete, and
`PrimaryLayer.kt` is the layer that draws the primary data series.

---

## F8 — **WRONG (minor).** `B-7` (S3-B, MOVE): the internal-helper list is missing one entry

S3-B §2 B-7 lists six `internal` helpers the meApp marker subclass must re-write, and §4
"Correction 2" calls it six. I re-derived the set mechanically — every identifier in
`git diff e67e49520 HEAD -- $C/cartesian/marker/DefaultCartesianMarker.kt` intersected against every
`internal` declaration in `$C` at v3.3.1 — and the set is:

`Rect.getStart` (`$C/common/Rect.kt:57`) · **`Rect.getEnd` (`$C/common/Rect.kt:59`)** ·
`Float.half` (`$C/common/Math.kt:68`) · `Position.Vertical.inBounds` (`$C/common/Position.kt:52`) ·
`Position.Horizontal.unaryMinus` (`:38`) · `Position.Vertical.unaryMinus` (`:45`) · `Dp?.orZero`
(`$C/common/Math.kt:83`).

Seven, not six. `Rect.getEnd` is one line. Otherwise S3-B's list is exactly right, and I found
nothing else internal in the fork's marker additions — **`CartesianDrawingContext.layerBounds`,
`layerDimensions` and `scroll` are all public** (`$C/cartesian/CartesianDrawingContext.kt:29-40`);
the only `internal layerBounds` in the tree is `CartesianChart.layerBounds` (`:163`), a different
member that the subclass never touches.

---

# Part 3 — Attacked and could not be broken

Everything in this section was stress-tested with the intent of finding a defect and held. Listed so
the synthesis can distinguish "checked" from "merely unchallenged".

### The three corrected premises (S3-C)

| | result | evidence |
|---|---|---|
| **P1** — no pre-doubling in `LineCartesianLayer` | **CONFIRMED** | `HEAD $C/…/LineCartesianLayer.kt` `updateLayerMargins` computes `max(stroke, point).half` and passes it straight to `ensureValuesAtLeast(top =, bottom =)`. No `* 2` anywhere. v3.3.1's body is identical modulo `getLine` → `getLineOrThrow`. The halving is therefore uncompensated, exactly as P1 says. |
| **P2** — `updateLayerDimensions` gained no responsibility | **CONFIRMED** | Body at `v3.3.1 $C/cartesian/axis/HorizontalAxis.kt:568-621`; the two added early returns are `val label = label ?: return` and `if (itemPlacer.getShiftExtremeLabels(context)) return`. `getShiftExtremeLabels` confirmed as a defaulted member (`:888`, `= false`) reachable through the public factory `aligned(…, shiftExtremeLabels = false)` (`:919-932`) and through `AlignedHorizontalAxisItemPlacer` (`HorizontalAxisItemPlacers.kt:82-87`). |
| **P3** — `CartesianChart.draw()` byte-identical fork → v3.3.1 | **CONFIRMED** | Extracted both methods and `diff`'d: identical, 28 lines, `v3.3.1 $C/cartesian/CartesianChart.kt:377-403`. |

### DELETEs

- **`A12`** (halving + `ensureVerticalValuesAtLeast`) — **CONFIRMED.** v3.3.1
  `$C/cartesian/layer/CartesianLayerMargins.kt:55-65` stores as given; `ensureVerticalValuesAtLeast`
  has zero hits at v3.3.1. All six top/bottom call sites re-read: each either applies its own
  `.half` at the call site or passes a quantity that sits wholly outside the plot. *One sub-claim is
  loose and strengthens the verdict rather than weakening it:* S3-C says `VerticalAxis.kt:507` is
  "already halved by the item placer", but
  `StepVerticalAxisItemPlacer.getTopLayerMargin`/`getBottomLayerMargin` return the **full**
  `maxLabelHeight` on the `Top` and `else` branches (only the `Center` branch halves). So the fork
  halves a full label height there — under-reservation, not over. Verdict unchanged: DELETE.
- **`B-4c`** (switch the marker to `ensureValuesAtLeast`) — **CONFIRMED, and the "no layout change"
  claim is exact.** At HEAD *both* relevant branches use the un-halved helper:
  `HEAD $C/…/DefaultCartesianMarker.kt:488-494` routes `Top`/`AbovePoint` → `ensureVerticalValuesAtLeast(top)`
  and `Bottom`/`BelowPoint` → `(bottom)`. meApp uses `LabelPosition.Top` (`GraphMarker.kt:141`), so
  once A12 un-halves `ensureValuesAtLeast`, the two calls are literally the same call. The
  "both halves in one commit, no intermediate state" rule is the correct one.
- **`A3b`** (drop the `clipPath` rewrite) — **CONFIRMED.** v3.3.1's `defaultLineLeft` /
  `defaultLineRight` (`HorizontalAxis.kt:462-503`) are the quantity our patch substituted; and the
  axis `lineBounds` is combined with `minOf` (left) / `maxOf` (right), so it can only **widen** the
  clip. The "zero-width vertical axis" failure mode we patched around also cannot occur: the
  `axisDimensions` map is pre-populated with all four positions at construction
  (`$C/cartesian/axis/AxisManager.kt:35-41`), so `getValue` never throws, and an absent axis leaves
  `lineBounds.isEmpty`, which short-circuits to the default branch. A4b's clamp to `layerBounds` then
  bounds the widening back.
- **`A7b`** — **CONFIRMED** (meApp half). See F5 for the cross-cluster consequence.
- **`L12b`** (`registerForUpdates(animate = false)`) — **CONFIRMED as ours alone.** Blob SHAs:
  `e67e49520:…/CartesianChartModelProducer.kt` and `v3.3.1:…` are both
  `60e3541a9521678404a2b3261bb3c8f633dfc211`; HEAD is `6bbba3290…`. v3.3.1's `registerForUpdates`
  calls `receiver.handleUpdate(lastPartials, lastTransactionExtraStore, restoredModel)` with no
  animate parameter at all; HEAD adds `animate = false` at `:127`. The DELETE carries a real
  behaviour change on rotation, which S3-C already gates with a named manual check — keep that gate.
- **`S1A-05b`** (`isSnapping`) — **CONFIRMED.** `git grep isSnapping HEAD -- vico/ sample/` → three
  hits, all in `$C/cartesian/SnapFlingBehavior.kt` (`:71` declaration, `:99` write, `:115` write).
  `internal`, so meApp cannot read it. Written-only, dead.
- **`B-9`** (`contentPadding`) — **CONFIRMED.** Every `contentPadding` under meApp's `app/src` is a
  Compose `PaddingValues` on an unrelated component; vico's is passed nowhere.
- **`L10`** (`getVisibleXRange` back to `internal`) — **CONFIRMED.** `grep -rn getVisibleXRange app/src`
  returns nothing, and `git diff e67e49520 v3.3.1 -- $C/cartesian/CartesianDrawingContext.kt` is empty.
- **`B-3a` / `M-4`** (`acceptsGestures` superseded) — **CONFIRMED by full trace.** v3.3.1's event
  loop returns a hard-coded `false` for `Press` and `Release` (`$C/cartesian/Modifier.kt:97-104`),
  `consumeMoveEvents && !scrollEnabled` for `Move`, and the host's value for `Enter`/`Exit`. Taps
  are consumed only by `if (inputChange.isTap(down) && onTap(inputChange.position)) inputChange.consume()`
  (`:173`), where `onTap` is `onInteraction(Interaction.Tap(…))`, and the host returns
  `chart.markerController.shouldAcceptInteraction(...)` verbatim (`$C/cartesian/CartesianChartHost.kt:271-284`).
  A declining controller leaves the change unconsumed. S3-B's `acceptsLongPress` caveat is also
  necessary and correctly diagnosed: the interface default is `true`
  (`$C/cartesian/marker/CartesianMarkerController.kt:26-27`), the host wires it at `:332`, and a
  non-null `onLongPress` makes `detectTapGestures` take the `awaitLongPressOrCancellation` path and
  `return@awaitEachGesture` before any tap is reported (`Modifier.kt:159-167`).
- **`D-2`** (the sort is load-bearing) — **CONFIRMED, and I tried hard to break it.** My first read
  suggested the early-break scan was dead at v3.3.1, because `draw()` calls the *private*
  `getMarkerTargets(markerX, markerSeriesIndex)` (`:390`), which is an exact `markerTargets[x]`
  lookup (`:497-526`). It is not dead: the **public** `getMarkerTargets(x, visibleXRange)` scan
  (`:479-495`) is called from `$C/cartesian/CartesianChartHost.kt:262`, per gesture event, on the
  `onInteraction` path. Key order therefore matters exactly as S3-B says, and upstream still re-sorts
  every frame at `CartesianChart.kt:386-388` while our fork removed it on a false `TreeMap` premise
  (`HEAD $C/cartesian/CartesianChart.kt:83` is `mutableMapOf`, `:382` is the stale comment).

### MOVEs — every symbol checked for visibility at v3.3.1

- **`A1` (Separators)** — **CONFIRMED, including the equality the verdict rests on.**
  `bottomAxis.bounds.getStart(isLtr) == layerBounds.getStart(isLtr)`, proven both ways:
  `AxisManager.setBottomAxisBounds` sets `left = if (isLtr) layerMargins.start else layerMargins.end`
  and `right = canvasSize.width - if (isLtr) layerMargins.end else layerMargins.start`
  (`$C/cartesian/axis/AxisManager.kt:103-116`); `CartesianChart.prepare` sets
  `setLayerBounds(layerMargins.getLeft(isLtr), …, canvasSize.width - layerMargins.getRight(isLtr), …)`
  (`$C/cartesian/CartesianChart.kt:358-363`). LTR and RTL both resolve to the same term. The fork's
  `baseCanvasX = bounds.getStart(isLtr) - scroll + layerDimensions.startPadding * layoutDirectionMultiplier`
  (`HEAD $C/cartesian/axis/HorizontalAxis.kt:202-203`) and `drawSeparators` (`:261-283`) read only
  `layerBounds`, `layerDimensions`, `scroll`, `ranges`, `layoutDirectionMultiplier` and
  `LineComponent.drawVertical` — all public on `CartesianDrawingContext`. `Rect.getStart` is
  internal and must be inlined, which S3-C states.
- **`A11` (ListItemPlacer)** — feasible; one internal import to replace (F6). Drift check passes.
- **`L1` (MonotoneInterpolator)** — **CONFIRMED feasible.** `LineCartesianLayer.Interpolator` is a
  fully public interface at v3.3.1 (`$C/…/LineCartesianLayer.kt:342-413`) with `interpolate`,
  `getYRange` and `visiblePadding`; our implementation imports nothing internal. Three call sites,
  not two (F7).
- **`L1`'s `visiblePadding = 1`** — **CONFIRMED by independent derivation.** I checked the claim
  against the actual slice arithmetic rather than the dossier's prose. `visiblePointPadding =
  interpolator.visiblePadding?.plus(1)` (`LineCartesianLayer.kt:757`) feeds
  `getSliceIndices(start, end, padding)`, which moves both ends by `padding`
  (`$C/cartesian/data/CartesianLayerModel.kt:114-141`). With `visiblePadding = 0` the slice padding
  is 1, and the pixel-bounds loop (`:772-798`) then sets `visibleStart = 0` — the padding point is
  consumed by `visibleIndexRange` itself, so there is nothing to the left of it. With
  `visiblePadding = 1` the slice padding is 2 and `visibleIndexRange.first = 1`. Our drawing loop
  consumes `tangents[first … last]`, `tangents[i]` needs `secants[i-1]` hence `points[i-1]`
  (`HEAD $C/cartesian/layer/MonotoneInterpolator.kt:62-91`), so it needs exactly one point beyond
  each end. Same reach as `CatmullRomInterpolator`, which declares 1. **1 is right; 0 is one short
  and fails silently.**
- **`S1A-10` (`Scroll.Absolute.xBehindStartInset`)** — **CONFIRMED.** `Scroll.Absolute` is a
  `public fun interface` and every parameter type is public (`$C/cartesian/Scroll.kt:26-34`);
  upstream's `x()` body (`:49-56`) is the same expression minus `startPadding`; the file is
  byte-identical e67e49520 → v3.3.1.
- **`S1A-09b` (`getInterpolatedYValues`)** — feasible; its only library tie is
  `MonotoneInterpolator`, which L1 moves. (It is used at `GraphView.kt`; failure is a compile error.)
- **`C-2` (`startInsetDp` → `layerPadding`)** — **CONFIRMED, including the order-independence
  argument I set out to break.** `MutableCartesianLayerDimensions.scale(factor)` multiplies
  `xSpacing` and the two *scalable* paddings and passes both *unscalable* ones through untouched
  (`$C/cartesian/layer/MutableCartesianLayerDimensions.kt:67-76`), so our patch's placement **after**
  the `visibleLabelsCount` scale (`HEAD CartesianChart.kt:284-289` vs `:297-300`) genuinely buys
  nothing. The L9 ordering constraint is real and confirmed: v3.3.1 writes
  `unscalableStartPadding = maxPointSize.half + layerPadding.unscalableStart.pixels`
  (`LineCartesianLayer.kt:901`), HEAD writes `layerPadding.unscalableStart.pixels` alone (`:1051`).
  **L9 must land before C-2**, as S3-C says. See F4 for the A3a coupling the dossiers missed.
- **`B-4b` / `B-7` / `B-8` (the marker subclass)** — **CONFIRMED, S3-B's two corrections are both
  right.** `DefaultCartesianMarker` is `public open class` with a public constructor
  (`$C/cartesian/marker/DefaultCartesianMarker.kt:59`), `protected val` fields (`:60-71`), overridable
  `drawOverLayers` (`:73`), `protected open drawIndicator` (`:117`), overridable `updateLayerMargins`
  (`:234`), `protected overrideXPositionToFit` (`:216`) and a `protected companion object` (`:336`).
  **`drawLabel` (`:135`) and `drawGuideline` (`:227`) are `protected fun`, not `open`** — confirmed.
  The internal-helper set is as listed, plus `Rect.getEnd` (F8).
- **`B-3b` (`rememberNonInteractive`)** — **CONFIRMED.** `CartesianMarkerController` at v3.3.1 is a
  `public fun interface` carrying `acceptsLongPress` (default `true`), `lock`, `consumeMoveEvents`,
  `shouldAcceptInteraction` and `shouldShowMarker`, all public; `acceptsGestures` does not exist
  upstream, so it really is ours to delete.
- **`H-3` (window probe as a `Decoration`)** — feasible: `Decoration` is a public `@Immutable`
  interface with two defaulted methods taking a full `CartesianDrawingContext`
  (`$C/cartesian/decoration/Decoration.kt:26-37`), invoked from `draw()` at `:380` and `:399`, and
  every input `getVisibleXRange` needs is public. See F3 for the stability precondition.

### REPLACEs

- **`A6` / `C-5` (the `decorations.drawOverLayers` slot)** — **CONFIRMED by line count on the
  extracted method.** In v3.3.1's `draw()` (`$C/cartesian/CartesianChart.kt:377-403`):
  `canvas.restore()` is `:396` (inside `fadingEdges?.run`), `axisManager.drawOverLayers` `:398`,
  `decorations.forEach { it.drawOverLayers(context) }` **`:399`**, persistent markers `:400`,
  `legend?.draw` `:401`. So the slot is after the fade restore and before the legend, as claimed.
  `draw()` installs no clip of its own — the only canvas operation is the fading-edge `saveLayer`,
  restored before `:399`. (Our HEAD order differs — `decorations.drawOverLayers` sits at `:370`,
  *before* the composite — which is exactly why C-5 is a re-derivation rather than a graft.)
- **`A3a` (`ItemPlacer.aligned(shiftExtremeLabels = true)`)** — **CONFIRMED.** The parameter exists
  on the public factory (`HorizontalAxis.kt:919-932`), is forwarded to
  `AlignedHorizontalAxisItemPlacer` (`HorizontalAxisItemPlacers.kt:82-87`), and its two behavioural
  uses are the `updateLayerDimensions` early return (`:573`) and the label `when`. meApp's wrapper
  is `by defaultPlacer`, so the new member forwards automatically — verified in
  `HorizontalItemPlacer.kt:40-41`. The KDoc also confirms the precedence rule S3-C relies on
  ("If both `addExtremeLabelPadding` and `shiftExtremeLabels` are enabled, `shiftExtremeLabels` takes
  precedence"). Note that `shiftExtremeLabels` does **not** change `getLabelValues`, so it does not
  interfere with A4a's filter — which is the one piece of good news for F1.

### RE-APPLYs spot-checked

- **`S1A-06`** — **CONFIRMED irreducible.** `context`, `layerDimensions` and `bounds` are all
  `private` at `v3.3.1 $C/cartesian/VicoScrollState.kt:56-58`, so the four conversions cannot move;
  `value`/`maxValue` are public (`:80`, `:89`).
- **`L11`** — the producer is byte-identical e67e49520 ↔ v3.3.1 (same blob), so the hunks replay with
  zero upstream drift, as claimed.

---

# What I did not attack

Recorded so the synthesis does not mistake silence for clearance.

- **C1–C7** — settled by instruction, not re-litigated.
- The **RE-DERIVE** verdicts (`L3`/`L5`/`H-1`/`H-2`/`M-1`): design proposals, out of scope for a
  verdict-correctness pass.
- **U1–U5** (S3-C) and **U-1…U-3** (S3-B) — already open with named checks; I did not duplicate them.
- `A2a`/`A2b`/`A2c`/`A3c`/`A4b`/`A5`/`A8`/`A9`/`A10`/`L4`/`L6`/`L7`/`L8`/`L13`/`L14`/`L15`/`L16`,
  `C-3`/`C-4`, `B-1`/`B-2`/`B-5`/`B-10`/`B-11`/`B-12`, `M-2`/`M-3`, `S1A-01…04`, `S1A-07`,
  `S1A-08a/b`, `S1A-09a`, `S1A-11…13` — not individually attacked. They are RE-APPLY or RE-DERIVE
  verdicts on code the brief did not flag as removing shipped behaviour.
