# S3-B — Disposition: markers, scrubbing, and the gesture path

Stage-3 decision table for cluster S1-B plus the gesture path through `Modifier.kt`.
Refs per `../BASELINE.md`: FORK_POINT `e67e49520`, OURS `HEAD`, TARGET `v3.3.1` (`3d675ccc7`).

Paths abbreviated `$C` = `vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose`.
Every line number below is **at v3.3.1** unless the row says HEAD. Everything was read with
`git show <ref>:<path>`; the working tree was not touched.

**Verdicts.** DROP (upstream does it) · DELETE (wrong / self-inflicted / dead / now pointless) ·
REPLACE (re-express through an upstream extension point) · MOVE (relocate to meApp) ·
RE-APPLY (port substantially as-is) · RE-DERIVE (intent survives, code cannot be ported).

Two verdicts are deliberately absent from this cluster: **nothing is marked DROP.** The one
candidate — `acceptsGestures` — is DELETE rather than DROP, because what supersedes it is not an
upstream re-implementation of our feature but a *different* mechanism that happens to cover the
same need, and the thing it covers has to be re-expressed by meApp, not simply deleted. C1 in
`../CONTRADICTIONS.md` is the standing reminder of what a careless DROP costs here.

---

## 1. Verdict table

### 1a. The twelve S1-B adaptations

| id | adaptation | verdict | risk if wrong | blocks / blocked by |
|---|---|---|---|---|
| B-1 | `ScrubMarkerController` class + `rememberScrubMarkerController` | **RE-APPLY** (class) — and **DELETE** its unread `scrollState` parameter | loud: no scrub at all; hold-and-drag scrolls the chart, dashboard header never follows a finger | blocked by M-1, H-5, H-6 · blocks nothing |
| B-2 | `interaction` 3rd param on `onMarkerIndexChanged` | **RE-APPLY** | quiet: lifting off a scrub clears the marker the scrub just placed (the exact bug `b7d7e0583` fixed) | must land in the same change as B-1 and H-6 |
| B-3a | `acceptsGestures` on `CartesianMarkerController` + the `Modifier.kt` guard | **DELETE** — superseded (see §3) | quiet: snapshot card stops opening the full graph | blocked by B-3b (do them together) · unblocks M-1 |
| B-3b | `rememberNonInteractive()` / `NonInteractiveMarkerController` | **MOVE** to meApp (≈8 lines) | same as B-3a, plus a press on the card replaces the date-range caption while held | blocks B-3a |
| B-4a | `CartesianMarker.drawResting(context)` + the `CartesianChart.draw` dispatch | **RE-APPLY** (patch; sealed door S8) | loud: the date-range caption vanishes from the dashboard graph *and* the snapshot card | blocks B-4b, B-6 |
| B-4b | `restingLabel` / `restingLabelFormatter` / `restingLabelAnchor` / `labelGap` / measured-height reservation in `DefaultCartesianMarker` | **MOVE** to a meApp `DefaultCartesianMarker` subclass | quiet: caption lands on the wrong line, or the chart reserves the wrong gap | blocked by B-4a, D-1, B-4c |
| B-4c | `ensureVerticalValuesAtLeast` (the helper that undoes our halving) | **DELETE**; call upstream's `ensureValuesAtLeast` | quiet and two-sided — see §2 B-4c | **must land in the same commit as S1-C's deletion of the halving** |
| B-5 | `CartesianMarker.onScrollSettled(visibleXRange)` | **RE-APPLY** (patch; sealed door S8). The `restingText` state moves to meApp with B-4b | quiet: caption flickers through every window of a fling and runs date arithmetic per frame | blocked by H-4 (host feed) |
| B-6 | nullable `label: TextComponent?` | **REPLACE** — obviated once the meApp subclass owns `drawOverLayers` + `updateLayerMargins` | quiet: snapshot card draws an empty bubble and over-reserves top margin | **blocked by B-4b + B-7 + B-8 landing as MOVE.** If the subclass does not land, this reverts to RE-APPLY |
| B-7 | horizontal crosshair + crosshair label | **MOVE** to the meApp subclass | quiet: percentile rule misplaced, or drawn under the dots again | blocked by B-4a, D-1 · needs 4 internal helpers re-written (§2 B-7) |
| B-8 | `yLabelCallback` | **MOVE** to the meApp subclass | quiet: dashboard header stops following the scrub | blocked by B-4b (same subclass) · interacts with D-1 |
| B-9 | `contentPadding: Insets` | **DELETE** — no meApp caller, affects only label positions meApp does not use | none observable | none |
| B-10 | `isInterpolated` on `LineCartesianLayerMarkerTarget.Point` | **RE-APPLY** (2-line defaulted parameter; file untouched upstream) | low: a dot asserts a reading that does not exist — and synthesised points are `Color.Transparent` anyway, so largely masked | blocks the meApp subclass's indicator loop |
| B-11 | `TextComponent.getBounds` appends `measuredText`, not `text` | **RE-APPLY** — and send upstream | silent, and **the most losable change in this cluster**: axis gutters 4 characters too wide, marker label line measured too tall | **do this first and alone**; B-4b's measured reservation depends on it |
| B-12 | caller-owned clearing via `initialMarkerX` *(host cluster owns the file)* | **RE-DERIVE** | quiet: a marker the caller cleared keeps being drawn at an x nobody holds | blocked by the host's rewritten overload set |

### 1b. The gesture path (`Modifier.kt`, S1-E M-1…M-4)

| id | adaptation | verdict | risk if wrong | blocks / blocked by |
|---|---|---|---|---|
| M-1 | the ~180-line scrub state machine (`NONE/DECIDING/MARKER_SELECTION/MARKER_SCRUBBING/SCROLLING`) | **RE-DERIVE** — rebuild against the `Boolean` contract, in a new file `ScrubGesture.kt` | loud: the scrub dies, or the parent `LazyColumn` steals the drag mid-scrub | blocked by B-3a (removes a condition from the `.then` chain) · blocks B-1 |
| M-2 | `scrollable(enabled = true)` + freeze-in-`ScrollableState` | **RE-APPLY** (2 lines) — **not** superseded by v3.3.1's mutable `scrollEnabled` | quiet-then-loud: on a non-scrollable chart a horizontal drag escapes to the parent list | blocked by the scroll cluster keeping `isScrollFrozen` |
| M-3 | external `flingBehavior` parameter | **RE-DERIVE**, pending C2 | quiet: YEAR view stops snapping to month boundaries | **blocked by C2** (scroll cluster) · the `SnapFlingBehavior` name clash must be resolved regardless |
| M-4 | the `acceptsGestures` gate on the tap detector | **DELETE** — same decision as B-3a | see B-3a | — |

### 1c. Two defects to dispose of, not port

| id | defect | verdict | risk if wrong | blocks / blocked by |
|---|---|---|---|---|
| D-1 | `CartesianChart.draw` calls `marker.drawOverLayers` twice per frame | **DELETE** the second call; replace with an explicit second hook | silent: translucent guideline and crosshair draw at double alpha; `yLabelCallback` fires twice a frame | **blocks B-4b, B-7, B-8** — the subclass's method split *is* the fix |
| D-2 | size-keyed `allMarkerTargetXValues` cache + the deleted per-frame sort | **DELETE** both; restore upstream's sort, invalidate the cache on repopulation | silent and intermittent: wrong/empty target list on multi-layer charts; marker snaps to an x from the window the user just left | do before any gesture work, or you will debug the wrong layer |

---

## 2. Evidence, adaptation by adaptation

### B-1 — `ScrubMarkerController` → RE-APPLY (class); DELETE the `scrollState` parameter

**Evidence.** `CartesianMarkerController` is on S2-A §3.4's verified "upstream never touched in the
range" list, and v3.3.1's interface (`$C/cartesian/marker/CartesianMarkerController.kt`) carries the
same five members our class overrides — `acceptsLongPress`, `lock`, `consumeMoveEvents`,
`shouldAcceptInteraction`, `shouldShowMarker`. Nothing in the class body collides with v3.3.1.
The file is new, so it grafts by construction.

What changed underneath it is entirely in its **counterparties**, and they are handled as M-1
(the gesture machine) and H-5/H-6 (the host). S2-B §2 is right that "the gesture machine is not
upstream": `consumeMoveEvents` is gated on `!scrollState.scrollEnabled`
(`$C/cartesian/Modifier.kt:108`), so on a *scrollable* chart a drag scrolls rather than scrubs, and
there is no upstream analogue of `isScrollFrozen`.

**The `scrollState` parameter is dead.** HEAD `ScrubMarkerController.kt:144-157` reads only
`delayMs` and the callback; `8052261ae` moved scroll-dismissal to the host and left the parameter
behind. meApp passes it anyway (`GraphView.kt:268-269`). Delete it: the API currently states a
dependency that does not exist, and the fix at the one call site is to delete one argument.

**Fork-reduction note (do not block the port on it).** The three `as? ScrubMarkerController` type
tests — `Modifier.kt:72`, `:280`, and the host's three at `:255`, `:376-397`, `:416-417` — are what
keep this class inside the library. Expressed as controller *properties* (`holdDelayMs: Long?`,
`dismissOnScroll`, `replayOnViewportChange`, plus a public `onDismiss()`), both files would branch
on data rather than identity and the class could MOVE to meApp. That is the single largest fork
reduction available in this cluster, and it is a design project, not a migration step. Record it;
do not attempt it inside the port.

**Sequencing.** After M-1 and the host's H-5/H-6; it is useless before either.

### B-2 — `interaction` third parameter → RE-APPLY

**Evidence.** `Interaction` is an upstream public sealed class, unchanged in the range. The host
already holds the instance it would forward — at v3.3.1 that is the `interaction: Interaction`
parameter of the `onInteraction` lambda, `$C/cartesian/CartesianChartHost.kt:252-285`. This is a
pure widening of a fork-local callback type; no upstream declaration changes.

**Risk.** meApp's tap-to-clear rule (`GraphView.kt:531`, `val isTap = interaction is Interaction.Tap`)
is the only thing stopping a scrub's final `Release` from clearing the marker the scrub just placed.
Lose the parameter and that bug returns — and it looks like "the marker flickers off when I let go",
which reads as a gesture bug, not a callback-arity bug.

**Sequencing.** Same change as B-1 and H-6. It cannot be staged separately: the host's call sites are
the only producers.

### B-3 — `acceptsGestures` → DELETE; `rememberNonInteractive` → MOVE

See §3 for the full trace. In short: at v3.3.1 the tap is consumed only when the host's
`onInteraction` returns `true`, and that return value **is** `shouldAcceptInteraction`'s result —
so a controller that declines gets both halves of what `acceptsGestures` bought us.

- **DELETE** `CartesianMarkerController.acceptsGestures` (HEAD `CartesianMarkerController.kt:41-49`)
  and the third clause of the `Modifier.kt` guard (HEAD `Modifier.kt:278-282`). That removes a patch
  from a public interface *and* a condition from the `.then` chain M-1 has to rewrite.
- **MOVE** `NonInteractiveMarkerController` to meApp, beside `SnapshotLineChart.kt:145`. It is eight
  lines implementing a public `fun interface`:

  > `override val acceptsLongPress = false` (so the long-press branch is never even attached),
  > `shouldAcceptInteraction = false`, `shouldShowMarker = false`.

  Adding `acceptsLongPress = false` is new — today's object inherits the default `true`. It is not
  required for correctness (an unconsumed long press blocks nobody) but it stops the gesture from
  being tracked at all.

**Risk if wrong.** The snapshot card stops opening the full graph on tap. Nothing fails to compile
and no test goes red. Gate it on the smoke test named in §3.

### B-4a — `drawResting` → RE-APPLY (patch)

**Evidence.** `git grep -n 'drawResting\|onScrollSettled' v3.3.1 -- vico/` returns nothing (S2-B §13,
re-verified). At v3.3.1 `CartesianMarker`'s only draw entries are `drawUnderLayers` /
`drawOverLayers` (S2-D §1.3), and `CartesianChart.draw` calls them only when `markerTargets` is
non-empty (`$C/cartesian/CartesianChart.kt:390-392`, `:402`). There is **no** "draw while nothing is
marked" callback, and `CartesianChart.draw` is `internal`, so no consumer can add one. This is
S2-D's sealed door **S8** and it is genuinely sealed.

The one alternative S2-D floats — a consumer `Decoration` drawing the resting content and using the
public `CartesianMarkerVisibilityListener` to know when to suppress itself — is not equivalent:
a `Decoration` cannot learn the marker's label line, which is the entire point (the caption and the
marker label must occupy the *same* line, which only the chart knows). Reject it.

**Keep the patch minimal.** Nine lines on a public interface with an empty default body, plus the
one-line dispatch in `CartesianChart.draw`. Good upstream PR; keep the patch until it lands.

**Sequencing.** Before B-4b — the meApp subclass cannot override a method that does not exist.

### B-4b — the resting-label body → MOVE to a meApp subclass

**Evidence (feasibility, against v3.3.1).** `DefaultCartesianMarker` is
`public open class` with a public constructor (`$C/cartesian/marker/DefaultCartesianMarker.kt:59`),
`protected val label/valueFormatter/labelPosition/indicator/indicatorSize/guideline` (`:60-65`),
`protected val markerCornerBasedShape` / `tickSize` (`:68`, `:71`), `override fun drawOverLayers`
(`:73`), `override fun updateLayerMargins` (`:234`), `protected fun overrideXPositionToFit` (`:216`),
and `protected companion object { public val keyNamespace }` (`:336`). A meApp subclass can own the
resting label outright.

**What the subclass must re-write rather than call** — this is where S2-D was optimistic, see §2 (c)
below. Four `internal` one-liners and two non-`open` methods.

**Sequencing.** After B-4a, B-4c, D-1 and B-11.

### B-4c — `ensureVerticalValuesAtLeast` → DELETE, and the margin coupling to the axis cluster

**Evidence.** At v3.3.1 `CartesianLayerMargins.ensureValuesAtLeast` stores top/bottom **as given**:

```
$C/cartesian/layer/CartesianLayerMargins.kt:55-65
  this.top = this.top.coerceAtLeast(top)
  this.bottom = this.bottom.coerceAtLeast(bottom)
```

At HEAD the same method **halves** them (`CartesianLayerMargins.kt:62-63`,
`this.top.coerceAtLeast(top.div(2))`), and `ensureVerticalValuesAtLeast`
(HEAD `:76-80`) exists for one reason: to get the un-halved behaviour back for callers whose content
sits wholly outside the plot. `DefaultCartesianMarker.updateLayerMargins` is such a caller
(HEAD `DefaultCartesianMarker.kt:487-493`, `reserved = labelGap.pixels + labelHeight + tickSize.pixels`
passed through `ensureVerticalValuesAtLeast`).

**What deleting the halving does to the marker's reservation: nothing.** Once
`ensureValuesAtLeast` stores as given, `ensureValuesAtLeast(top = reserved)` and
`ensureVerticalValuesAtLeast(top = reserved)` are the same call. The marker reserves exactly
`labelGap + max(label, restingLabel) height + tickSize`, as it does today. So the migration switches
the marker's two call sites to `ensureValuesAtLeast` and deletes the helper, with **no change to the
marker's layout**.

**⚠ Flagged for the axis cluster (S1-C).** The two halves are a pair and splitting them is the one
way to break this quietly:

- halving deleted **and** `ensureVerticalValuesAtLeast` kept → the helper becomes a redundant alias;
  harmless for the marker, but every *other* caller that was compensating for the halving now
  reserves twice what it should.
- halving kept **and** `ensureVerticalValuesAtLeast` deleted → the marker reserves **half** the room
  it asks for; the label collides with the top of the plot and with whatever sits above the chart.
  This is the failure mode the HEAD comment at `CartesianLayerMargins.kt:68-74` was written to warn
  about, and it is silent — the chart renders, it just overlaps.

**Both halves land in one commit, owned by S1-C, with this cluster's two call sites switched in the
same commit.** No intermediate state is safe.

### B-5 — `onScrollSettled` → RE-APPLY (patch)

**Evidence.** Same sealed door as B-4a: no upstream equivalent
(`git grep onScrollSettled v3.3.1` → nothing), and the only place that can compute the visible
window per frame is the host — `CartesianDrawingContext.getVisibleXRange()` is `internal`
(`$C/cartesian/CartesianDrawingContext.kt:55`).

The hook itself is nine defaulted lines and has no meApp specificity in it ("tell the marker the
window, once it settles"). Keep it as a patch; it travels with B-4a as one upstream PR.

**There is a real alternative, and it belongs to another cluster.** S2-D's third-strongest conversion
is a consumer `Decoration` that computes the window from public members
(`layerBounds`, `layerDimensions.xSpacing`/`startPadding`, `scroll`, `ranges` are all public on
`CartesianDrawingContext` — `:30`, `:33`, `:36`) and publishes it. If the scroll/host cluster takes
that conversion, `onScrollSettled` and the host's H-4 effect both become deletable. **Do not gate the
marker port on it**; re-apply now, and revisit if the `Decoration` lands.

**Risk.** If the settle feed is lost but `drawResting` survives, the caption formats per frame and
names every window a fling passes over instead of the one it lands on. Visible as jank, easy to
misattribute to the scroll work.

### B-6 — nullable `label` → REPLACE

**Evidence.** v3.3.1 declares `protected val label: TextComponent` — non-null
(`DefaultCartesianMarker.kt:60`) — and `markerCornerBasedShape`/`tickSize` derive from
`label.background` at construction (`:68-71`). A subclass cannot widen a superclass property's type.

But it does not need to. The two methods that *use* `label` on the only path meApp cares about are
`drawOverLayers` (`:73`, overridable) and `updateLayerMargins` (`:234`, overridable); `drawLabel`
(`:135`) and `drawGuideline` (`:227`) are `protected` but **not `open`**, so the subclass does not
override them — it simply never calls them. A resting-only marker therefore passes an unused
`TextComponent`, overrides both open methods, and nothing ever measures or draws the dummy. The
inflation B-6 was created to avoid (`maxOf(label, restingLabel)` in the base `updateLayerMargins`)
disappears with the override.

**This verdict is conditional and the condition is load-bearing.** REPLACE is only correct if B-4b,
B-7 and B-8 all land as MOVE. If the subclass conversion is abandoned and the patch stays in
`DefaultCartesianMarker`, B-6 reverts to **RE-APPLY** and the seven null-guards at HEAD
(`:81`, `:98`, `:242`, `:302`, `:485-486`, `:512`, `:668-669`) must be treated as a checklist —
S1-B is right that taking upstream's `label: TextComponent` at any one of those sites is a compile
error at best and a crash on the snapshot card at worst.

**Independently worth an upstream PR** (`label: TextComponent? = null`), after which even the dummy
component goes.

### B-7 — horizontal crosshair + label → MOVE

**Evidence (feasibility).** Everything the crosshair reads is public at v3.3.1:
`CartesianDrawingContext.layerBounds` / `layerDimensions` / `scroll` (`:30`, `:33`, `:36`),
`CartesianLayerDimensions.startPadding` (`$C/cartesian/layer/CartesianLayerDimensions.kt:52`),
`MeasuringContext.isLtr` (`$C/common/MeasuringContext.kt:64`), and
`LineComponent.drawHorizontal` / `drawVertical` (`$C/common/component/LineComponent.kt:49`, `:67`).
`chartStartX()` — `(layerBounds.left + layerDimensions.startPadding - scroll).coerceIn(...)` — is
therefore reproducible verbatim in meApp.

**What does not come across, and must be re-written in meApp** (all trivial, all verified `internal`):

| helper | at v3.3.1 | size |
|---|---|---|
| `Rect.getStart(isLtr)` | `$C/common/Rect.kt:57` | 1 line |
| `operator Position.Horizontal.unaryMinus()` | `$C/common/Position.kt:38` | 3-arm `when` |
| `operator Position.Vertical.unaryMinus()` | `$C/common/Position.kt:45` | 3-arm `when` |
| `Position.Vertical.inBounds(bounds, componentHeight, referenceY, referenceDistance)` | `$C/common/Position.kt:52` | ~12 lines |
| `Float.half` / `Float.doubled` / `Dp?.orZero` | `$C/common/Math.kt:68`, `:74`, `:83` | `/2`, `*2`, `?: 0.dp` |
| the body of `drawLabel` for `LabelPosition.Top` | `DefaultCartesianMarker.kt:135-214` (not `open`) | ~25 lines for the Top case |
| the body of `drawGuideline` | `DefaultCartesianMarker.kt:227-232` (not `open`) | 4 lines |

That is roughly 60 lines copied into meApp to take ~250 out of the library. Under BASELINE decision 2
that trade is the right one, and it yields a tidy upstream PR list: make `drawLabel` and
`drawGuideline` `open`, and make `Position.Vertical.inBounds` and the two `unaryMinus` operators
public.

**Bonus.** `BabyCrosshairLabel.kt:36-41` already duplicates the anchor rule ("take the LAST series'
value") by hand in meApp. Moving the crosshair out collapses that duplicate instead of adding one.

**Risk.** The three `ab3fe3bfc` refinements are field-found bugs and each is easy to lose in a
re-write: the line must start at `chartStartX()` not `layerBounds.left`; the label must sit after the
axis line; and the label must be drawn in a **later pass** than the indicator dots or a dot paints
over the text. The third one is now entangled with D-1 — see below.

### B-8 — `yLabelCallback` → MOVE

**Evidence.** It reads only `LineCartesianLayerMarkerTarget.Point.entry.y` (public). Moving it into
the subclass's own `drawOverLayers` is a straight relocation of HEAD `DefaultCartesianMarker.kt:122-125`
and `:172`.

**Contract to carry across unchanged:** non-line targets contribute an **empty list** rather than
being dropped, so index alignment with `targets` is part of the contract — `BabyCrosshairLabel.kt:41`
depends on "the real data series is last".

**Note, not a blocker.** S1-B suspects the parameter may be redundant: meApp already has `markerIndex`
from the controller callback, and `GraphMarker.kt:108-124` mostly re-derives the entry from it; the
`fallbackValues` path is exercised only for interpolated targets (B-10). If meApp confirms that path
is dead, delete the callback instead of moving it. Worth one grep during the port; do not hold up the
MOVE for it.

**Interaction with D-1.** At HEAD this fires twice per frame. meApp was written defensively around
that (`GraphMarker.kt:104` builds an O(1) map because the original O(n) scan "ran every scrub frame").
Once D-1 is fixed the callback fires once; the defensive map stays correct either way.

### B-9 — `contentPadding` → DELETE

**Evidence.** Two independent confirmations:
1. **No meApp caller.** `grep -rn --include="*.kt" "contentPadding = " app/src` over
   `/Users/selvakumar/Projects/meApp-bug/Android` returns 14 hits and **every one is a Compose
   `PaddingValues`** (`DashboardSnapshotScreen.kt:130`, `BpDashboardContent.kt:184`,
   `AppButton.kt:483`, …). Vico's `contentPadding` is passed nowhere.
2. **Its only live effect is on positions meApp does not use.** At HEAD it survives in exactly two
   expressions — `DefaultCartesianMarker.kt:441` (`AbovePoint`/`AroundPoint`) and `:456`
   (`BelowPoint`) — and meApp uses `LabelPosition.Top` (`GraphMarker.kt:141`) and the default.
   `08d194bbd` already moved Top/Bottom off it onto `labelGap`.

**Risk if wrong: none observable.** This is the one row in the table where a mistake costs nothing.
Deleting it removes a constructor parameter, a `remember` key and ~10 lines from the hottest file.

**Also:** FEATURE-8 §"Marker contentPadding" is stale in two places (S1-B records both). Delete the
section with the parameter.

### B-10 — `isInterpolated` → RE-APPLY

**Evidence.** `$C/cartesian/marker/LineCartesianLayerMarkerTarget.kt` is on S2-A §3.4's verified
untouched list, so our two lines graft byte-for-byte and there is **no parameter-ordering decision** —
upstream added nothing to `Point` in the range. `git grep -ni 'isInterpolated' v3.3.1 -- vico/` →
no hits (S2-B §8).

This is one of the three hooks that escape the subclass (§4): `Point` is a `data class`, so a
consumer cannot extend it, and `points: List<Point>` is typed to that exact class even on the public
`LineCartesianLayerMarkerTarget` interface. S2-D's out-of-band alternative — the consumer remembers
which x values it synthesised — is feasible but couples the marker to C-7's synthesis, which the
layer-data cluster owns. Keep the 2-line patch; it is a clean upstream PR.

**Risk is genuinely low**, lower than S1-B implies: `synthesizeInterpolatedTargets` sets
`color = Color.Transparent` for such points (S1-E C-7), so losing the guard yields an invisible dot,
not a false reading. Verify that belt-and-braces rather than rely on it.

### B-11 — the `TextComponent` one-word fix → RE-APPLY, and send upstream

**Evidence — the bug is still there at v3.3.1:**

```
$C/common/component/TextComponent.kt:284-290
  var measuredText = text ?: ""
  if (pad) {
    measuredText = buildAnnotatedString {
      append(text)                       // ← line 287; `text: CharSequence?`
      repeat((lineCount - measuredText.lines().size).coerceAtLeast(0)) { append('\n') }
    }
  }
```

`Appendable.append(null)` writes the four characters `null`. `pad` defaults to `text == null`
(`:281`), so this path is taken by **every `Size.Auto` axis sizing its gutter** and by every
`getHeight`/`getWidth` call made with no text — including B-4b's measured reservation.

S2-A §3.3 confirms upstream's churn on this file in the range is **formatting only** (ktfmt
re-wrapping plus two explicit imports), so the one-word fix grafts cleanly.

**Why it is the most losable change here.** Ours is 4+/1- against a 120-line reformat. A "take
theirs" resolution reverts it, nothing fails to compile, and no test goes red — axis gutters are
simply four characters too wide again and the marker's measured line is slightly too tall.

**Sequencing: do this first, in its own commit, before anything else in the cluster**, and verify by
hand afterwards that the merged file's `pad` branch appends the coalesced local. Open the upstream PR
the same day; keep the patch until it lands.

### B-12 — caller-owned clearing → RE-DERIVE *(host cluster owns the file)*

**Evidence.** The host's overload set changed under us: v3.3.1's producer overload takes
`initialAnimationSpec` instead of `animateIn` and adds `chartAreaHeight: Dp`
(`$C/cartesian/CartesianChartHost.kt:87-97`), and `CartesianChartHostImpl` gained `modifier` and
`chartAreaHeight` in positions 3 and 7 (`:204-215`). Our two parameters and two effects have to be
re-inserted into a different shape; this is a rebuild, not a graft.

Recorded here because it is **load-bearing for B-1's contract, not incidental**: the two-way binding
between the host's `rememberSaveable markerX` and the controller's `internal var hasActiveMarker` is
how a caller's clear reaches the chart and how a gesture dismissal reaches the caller. The comment at
HEAD `CartesianChartHost.kt:272-274` records that an earlier version of this sync was circular.

**The better shape, for the record** (S1-E H-5): hoist it —
`CartesianChartHost(..., markerX: Double?, onMarkerXChange: (Double?) -> Unit, ...)`. That one
upstream-style change replaces H-5, H-6 and a good part of `ScrubMarkerController`.

### M-1 — the scrub state machine → RE-DERIVE

**What was rewritten under us** (`$C/cartesian/Modifier.kt` at v3.3.1, read end to end):

| | HEAD | v3.3.1 |
|---|---|---|
| function | not `@Composable`, expression body | `@Composable`, block body (`:55-62`) |
| callback | `((Interaction) -> Unit)?` | `((Interaction) -> Boolean)?` (`:58`) |
| consumption | ad-hoc `event.changes.forEach { it.consume() }` inside each branch | one `val consume = when { … }` then `if (consume) event.changes.fastForEach { it.consume() }` (`:86-120`) |
| fling | external `flingBehavior` parameter | built internally from `scrollState.xSnapStep` (`:63-72`) |
| scrollable | `enabled = true` (ours) | `enabled = scrollState.scrollEnabled` (`:76`) |
| tap detection | `androidx.compose.foundation.gestures.detectTapGestures` | a private re-implementation on `awaitEachGesture` (`:156-175`) with a `context(AwaitPointerEventScope)` `isTap` helper (`:177-187`) |

Our machine must be rebuilt, not merged. **Put it in a new file.** S1-E M-1's recommendation is
right and cheap: move the five-state machine to `ScrubGesture.kt` and reduce `Modifier.kt` to a
one-line delegation. That turns a 234-line conflict surface on the hottest file in the fork into
about five lines, permanently — it pays for itself at the *next* upgrade too.

**How the scrub blocks the parent `LazyColumn` under the new `Boolean` contract.** Two independent
legs, and both are needed because they cover different phases of the gesture:

1. **Pointer-path, during the scrub — drive it from the controller's own predicate.** The host's
   `onInteraction` now returns `shouldAcceptInteraction(...)` verbatim
   (`CartesianChartHost.kt:271-284`: `true` on the accept branch, `false` otherwise). And
   `ScrubMarkerController.shouldAcceptInteraction` already returns `true` for `Move` exactly when
   `isScrubbing` and `false` for `Press` (HEAD `ScrubMarkerController.kt:78-87`). So

   ```
   event.type == PointerEventType.Move ->
       onInteraction(Interaction.Move(pointerPosition))   // true iff scrubbing
   ```

   reproduces today's `MARKER_SELECTION`/`MARKER_SCRUBBING` consumption *from the controller's own
   state*, and consumes nothing in `DECIDING`/`SCROLLING`/`NONE` — which is exactly the current
   behaviour, including the deliberate non-consumption of small movements that lets `scrollable`
   cancel an in-flight fling or snap. The state machine stops owning that decision; the controller
   does. This is the piece of M-1 that genuinely gets *better* at v3.3.1.

   **One exception — `Release`.** `shouldAcceptInteraction(Release)` returns `true`
   unconditionally, so routing Release's consumption through the return value would start consuming
   releases in `DECIDING` and `NONE`, where today we do not. Keep the explicit state check for
   `Release` and discard the returned `Boolean` on that branch. Do **not** "fix" this by narrowing
   `shouldAcceptInteraction(Release)` — `shouldShowMarker(Release)` is what keeps the marker up
   after a scrub (HEAD `:113-116`), and it only runs when the accept predicate said yes.

2. **Drag-path, before and around the scrub — keep `scrollable` attached.** See M-2. Consumption in
   the inner `pointerInput` hides events from `scrollable` and from the parent, but only once the
   scrub has engaged. Before the hold timer fires, and on a chart whose `scrollEnabled` is false, the
   horizontal drag is claimed by `scrollable`'s nested-scroll node or by nobody.

**Risk.** Getting leg 1 wrong is loud (the chart scrolls under a scrubbing finger, or the list
scrolls). Getting leg 2 wrong is quiet at first — it only shows on the TOTAL/single-window chart and
on the first 200 ms of every gesture.

**Sequencing.** After B-3a/M-4 (one fewer condition to carry into the rewrite), before B-1.

### M-2 — `enabled = true` + freeze-in-`ScrollableState` → RE-APPLY

**Evidence, and the trap to avoid.** v3.3.1 made `scrollEnabled` mutable state —
`internal var scrollEnabled by mutableStateOf(true)` (`$C/cartesian/VicoScrollState.kt:59`) — and
S2-C V2 sells this as "`scrollEnabled` can be toggled without destroying the scroll position". It is
tempting to read that as a replacement for `isScrollFrozen`. **It is not.** `Modifier.kt:76` passes
`scrollEnabled` straight to `scrollable(enabled = …)`, and `enabled = false` **detaches the
nested-scroll node** — which is precisely the failure `3a284be42` / `2ddae34f4` fixed. Toggling
`scrollEnabled` during a scrub would hand the drag back to the parent `LazyColumn`, the exact
symptom M-2 exists to prevent.

So: keep `enabled = true` and keep the freeze inside `ScrollableState`
(HEAD `VicoScrollState.kt:201-210`, owned by the scroll cluster). Two lines in `Modifier.kt`.

**Risk.** Quiet-then-loud: on the TOTAL chart (`scrollEnabled = false`) a horizontal drag escapes to
the parent list, so the dashboard scrolls vertically-ish while the user tries to interact with the
chart. Easy to miss in review, obvious in the hand.

**Upstream PR candidate:** "a non-scrollable chart should still consume horizontal drags so a parent
list does not steal them" is a general bug report, not a meApp need.

### M-3 — external `flingBehavior` → RE-DERIVE, pending C2

**Evidence.** At v3.3.1 the fling behaviour is **chosen inside** `Modifier.pointerInput`:

```
$C/cartesian/Modifier.kt:63-72
  val flingBehavior = remember(scrollState, defaultFlingBehavior, decayAnimationSpec) {
    if (scrollState.xSnapStep != null) SnapFlingBehavior(scrollState, decayAnimationSpec)
    else defaultFlingBehavior
  }
```

so a passthrough parameter is no longer an extra `scrollable` argument — it has to become an override
of that `remember` block. That alone makes it RE-DERIVE rather than RE-APPLY, whatever C2 decides.

**Blocked by C2** (`../CONTRADICTIONS.md`): WEEK snapping can plausibly move to upstream's
`xSnapStep`; YEAR snaps to **month** boundaries, which are not a uniform millisecond step and cannot
be expressed as one. If YEAR still needs a custom `FlingBehavior`, something must carry it, and the
two candidate shapes are (a) keep the parameter and let it win over the internal choice, or
(b) generalise `xSnapStep` into a snap *provider*. (b) is the upstream-shaped answer and is the
scroll cluster's call.

**Independent of C2:** upstream added `internal class SnapFlingBehavior` in
`com.patrykandpatrick.vico.compose.cartesian` (`Modifier.kt:191`) — **the same package and simple
name as our new file**. Whatever survives must be renamed.

**Risk.** YEAR view stops snapping to month boundaries and comes to rest mid-month. Quiet; reads as
"scrolling feels loose".

### M-4 — the `acceptsGestures` gate → DELETE

Same decision as B-3a; evidence in §3.

### D-1 — `drawOverLayers` twice per frame → DELETE the second call

**Evidence.** At HEAD, `CartesianChart.draw` calls it twice:

```
HEAD $C/cartesian/CartesianChart.kt:392-395
  if (drawMarker) { marker?.drawUnderLayers(…); marker?.drawOverLayers(context, markerTargets) }
HEAD $C/cartesian/CartesianChart.kt:412
  if (drawMarker) marker?.drawOverLayers(context, markerTargets) else marker?.drawResting(context)
```

Upstream calls each exactly once — `drawUnderLayers` at `$C/cartesian/CartesianChart.kt:392`,
`drawOverLayers` at `:402`. The marker has **no way to tell which pass it is in**, so at HEAD the
guideline, the crosshair, every indicator dot, the crosshair label and the marker label are all drawn
twice, and `yLabelCallback` fires twice per frame.

**Why it has never been noticed:** for opaque paint a double draw is invisible. It shows only on
translucent paint (our guideline and crosshair), where the alpha composites to roughly double, and in
`yLabelCallback`, where it doubles the consumer's work. Both silent.

**The split itself is deliberate and worth keeping.** The first pass sits under the layer bitmap
composite (`91e72bf64`, "put the data on top of what describes it"); the second sits past the
fading-edge `saveLayer`/`restore` and past `axisManager.drawMarkerDecorations` so the *label* is
unclipped. Collapsing to one call loses one of those two intents.

**Disposition: fix during migration, do not port.** Give `CartesianMarker` a second defaulted entry
point in the same shape as `drawResting` — `public fun drawLabels(context, targets) {}` — and make
`CartesianChart.draw:412` call that instead of `drawOverLayers` a second time. The meApp subclass then
puts the guideline, the crosshair line and the indicator dots in `drawOverLayers`, and the marker
label plus the crosshair label in `drawLabels`. That preserves both intents, makes the pass identity
explicit, and — usefully — is the same mechanism B-7's third refinement needs ("the label must be
drawn in a later pass than the dots").

Fallback, if the extra interface member is judged too much patch: collapse to upstream's single late
call and accept that the indicator dots paint over the layer bitmap. State that cost explicitly if
you take it; do not take it silently.

**Sequencing. This blocks B-4b, B-7 and B-8** — the subclass's method split *is* the fix, so decide
D-1 before the subclass is written, not after.

### D-2 — the `allMarkerTargetXValues` cache and the deleted sort → DELETE both

**The premise of the deletion was false, and it is false at v3.3.1 too.**

```
HEAD    $C/cartesian/CartesianChart.kt:83   private val _markerTargets = mutableMapOf<…>()
HEAD    $C/cartesian/CartesianChart.kt:382  // TreeMap keeps _markerTargets sorted by key — no per-frame sort needed
v3.3.1  $C/cartesian/CartesianChart.kt:93   private val _markerTargets = mutableMapOf<…>()
v3.3.1  $C/cartesian/CartesianChart.kt:386-388
          val sortedMarkerTargetPairs = _markerTargets.toList().sortedBy { it.first }
          _markerTargets.clear(); _markerTargets.putAll(sortedMarkerTargetPairs)
```

`10fa978bb` changed `TreeMap` → `mutableMapOf` for commonMain compatibility and left the comment
behind. A `mutableMapOf` is a `LinkedHashMap` — insertion-ordered, not sorted. Upstream still sorts
every frame at v3.3.1.

**The sort is load-bearing, and here is why.** `getMarkerTargets(x, visibleXRange)` is an early-**break**
nearest scan:

```
v3.3.1 $C/cartesian/CartesianChart.kt:486-494   (HEAD :501-509, identical)
  for ((key, keyTargets) in markerTargets) {
    val delta = abs(key - x)
    if (delta > previousDelta) break
    targets = keyTargets; previousDelta = delta
  }
```

That is only correct on ascending keys. `_markerTargets` is filled layer by layer
(`:101-102`), so as soon as one layer contributes an x the earlier layers lack, the key sequence
stops being monotonic and the scan **breaks at the first local minimum** — returning a target from
the wrong x, or an empty list. meApp's charts are exactly the shape that triggers this:
`rememberChartLayers` in `ProductChart.kt` builds `arrayOf(percentileLayer, primaryLayer)` for baby
and `arrayOf(primaryLayer, secLayer)` for weight-with-secondary.

Note this compounds with S2-C V5 (the ULP narrowing fix): both bugs produce the same user-visible
symptom — "the marker sometimes shows only one series" — from different causes. Fixing one and not
the other will look like a partial fix.

**The cache is stale for most of a scroll.** It is keyed on `size` alone
(HEAD `$C/cartesian/CartesianChart.kt:164-172`), while the targets are produced by
`forEachPointInBounds` — i.e. from the **visible** points
(`$C/cartesian/layer/LineCartesianLayer.kt:591-593`, `:602-604`). So a horizontal scroll slides the
key *set* while the *count* stays constant, and the cached list keeps describing the window the user
has left. It refreshes only when a window boundary happens to add or drop a point. Silent and
intermittent — the worst kind.

**Mitigating fact, verified in meApp:** the consumer of that list,
`GraphView.getTargetPoints`, does a full linear scan with no ordering assumption
(`for (p in points) { if (p < effectiveMin || p > effectiveMax) continue; … }`) and binary-searches
only `fullList`, the visible axis labels, which are sorted independently. So the *order* of
`allMarkerTargetXValues` is harmless to meApp. The *staleness* is not: a stale list makes the marker
snap to a timestamp from the previous window.

**Disposition.**
1. Restore upstream's three sort lines verbatim (`:386-388`). If the per-frame allocation is a real
   cost, sort once per repopulation rather than per draw — but take the upstream lines first and
   optimise second, with a measurement.
2. Replace the size key with an explicit invalidation where `_markerTargets.clear()` runs
   (HEAD `:238`), so the list rebuilds lazily once per frame instead of once per `size` change.
   The cache was only ever saving a per-*call* rebuild — the host asks for it inside `onInteraction`,
   i.e. per gesture event, not per frame — so correct invalidation costs essentially nothing.

**Sequencing. Do D-2 before any gesture work.** With a stale or wrongly-ordered target list, every
scrub misbehaviour you see will look like a gesture bug.

---

## 3. `acceptsGestures`: settled — it is superseded. DELETE.

I read `$C/cartesian/Modifier.kt` end to end at v3.3.1 and traced the consume path. **Definitive read,
not a guess.** S2-B §2 and S1-E M-4 both suspected this; both were right, and the medium confidence
they attached to it can be raised.

**The trace, in four steps.**

1. **The detector is still attached.** `Modifier.kt:124-140` installs the tap detector iff
   `onInteraction != null`, and the host sets `onInteraction` non-null iff `chart.marker != null`
   (`CartesianChartHost.kt:251`, `:286-288`). Our fork's guard skipped attaching it; upstream's does
   not. That difference is **immaterial**, because of step 3.

2. **The host's callback now returns the controller's own verdict.**
   ```
   $C/cartesian/CartesianChartHost.kt:271-284
     if (chart.markerController.shouldAcceptInteraction(interaction, narrowedTargets)) {
       …; true
     } else {
       false
     }
   ```

3. **Consumption is gated on that return value.**
   ```
   $C/cartesian/Modifier.kt:173
     if (inputChange.isTap(down) && onTap(inputChange.position)) inputChange.consume()
   $C/cartesian/Modifier.kt:166
     if (onLongPress(longPress.position)) longPress.consume()
   ```
   `onTap` is `{ onInteraction(Interaction.Tap(it.toPoint())) }` (`:128`). So a controller whose
   `shouldAcceptInteraction` returns `false` leaves the change **unconsumed**, and an unconsumed
   change propagates to the ancestor `clickable` in the Main pass.

4. **Nothing else on the chart's modifier chain eats a tap.** I checked every node:
   - the event loop at `:86-120` returns a hard-coded `false` for `Press` (`:100`) and for
     `Release`/multi-touch `Press` (`:104`); `Move` returns `consumeMoveEvents && !scrollEnabled`,
     and a non-interactive controller inherits `consumeMoveEvents = false`; `Enter`/`Exit` return the
     host's value, which is `false`.
   - `scrollable(enabled = scrollState.scrollEnabled)` (`:73-79`) consumes drags, not taps.
   - the zoom detector (`:141-152`) is attached only when `scrollState.scrollEnabled && onZoom != null`
     and needs two pointers.
   - `extraPointerInput` is `= this` on Android (`Modifier.android.kt`) and on iOS; only
     desktop/web add a `draggable`.

**Both halves of B-3's rationale are covered**, not just the tap one. The second half — "a finger on
the card marked a point and replaced the resting date-range label for as long as the touch lasted" —
is covered because the host returns `false` at `:282-284` *before* touching `markerX`, so no marker
is placed. One controller returning `false` from `shouldAcceptInteraction` buys both.

**Therefore:**
- `CartesianMarkerController.acceptsGestures` → **DELETE** (removes a patch from a public interface).
- the `Modifier.kt` guard clause → **DELETE** (removes a condition from the chain M-1 rewrites).
- `rememberNonInteractive` / `NonInteractiveMarkerController` → **MOVE** to meApp, ~8 lines, beside
  `SnapshotLineChart.kt:145`; add `override val acceptsLongPress = false` while moving it.

**This is the cheapest fork reduction in the cluster: two files stop being patched for about eight
lines of consumer code.**

**The gate before you delete.** I could not run the app, and the rule is to demand evidence.
The code path is unambiguous, but the check is five minutes and the failure is silent, so take it:

> Build meApp's dashboard, tap the snapshot card (`SnapshotLineChart.kt`, controller
> `rememberNonInteractive`) anywhere over the chart area. **Pass:** the full graph screen opens.
> **Also check:** press and hold on the chart area — the "jun 29 – jul 5" caption must stay put and
> no marker dot must appear.

If that passes, delete. If it fails, the failure is almost certainly `acceptsLongPress` (the
long-press branch returns early from `awaitEachGesture` at `Modifier.kt:167` before a tap can be
reported) — set it `false` on the meApp controller and re-check before concluding the read was wrong.

---

## 4. The `DefaultCartesianMarker` subclass question: S2-D confirmed, with two corrections

**Confirmed.** `DefaultCartesianMarker` at v3.3.1 is a `public open class` with a public constructor
(`:59`), `protected` fields (`:60-65`, `:68`, `:71`), an overridable `drawOverLayers` (`:73`), an
overridable `updateLayerMargins` (`:234`), a `protected open drawIndicator` (`:117`), a `protected
overrideXPositionToFit` (`:216`) and a `protected companion object` exposing `keyNamespace` (`:337`).
S2-D's headline claim stands: **roughly 250 of our ~290 added lines belong in a meApp subclass**, and
upstream churned this file only 39+/25−, so the patch *would* graft — it simply should not have to.

**Correction 1 — `drawLabel` and `drawGuideline` are `protected` but NOT `open`.**
S2-D lists them among the overridable members; they are not (`:135` `protected fun drawLabel`,
`:227` `protected fun CartesianDrawingContext.drawGuideline`). The subclass can *call* them but not
*change* them, and our fork changes both (`labelGap` in place of `contentPadding`/`tickSize` for the
Top/Bottom y, and the guideline's extents). The subclass therefore re-implements them — about 25 lines
for the `LabelPosition.Top` case and 4 lines for the guideline. Worth an upstream PR (`open` on both);
not a blocker.

**Correction 2 — six `internal` helpers have to be re-written in meApp.** Listed with line numbers in
§2 B-7. All are one-to-twelve-line expressions; none is a blocker; three of them
(`Position.Vertical.inBounds`, the two `unaryMinus` operators) are reasonable upstream PRs.

**The hooks that genuinely escape the subclass.** S2-D said three. It is three, and these are exactly
them — two interface members and one data-class parameter, all defaulted, all additive, all good
upstream PRs:

| # | hook | why it cannot be reached from a subclass | upstream PR? | if upstream declines |
|---|---|---|---|---|
| 1 | `CartesianMarker.drawResting(context)` (B-4a) | `CartesianChart.draw` is `internal` and calls the marker only when `markerTargets` is non-empty (`:390-392`, `:402`). No "draw while nothing is marked" entry exists, and a `Decoration` cannot learn the marker's label line | **Yes** — 9 defaulted lines + a 1-line dispatch; "a marker that owns a line should be able to own it while idle" is a general gap | patch stays; 10 lines in two files |
| 2 | `CartesianMarker.onScrollSettled(visibleXRange)` (B-5) | same sealed call site; `getVisibleXRange()` is `internal` (`CartesianDrawingContext.kt:55`) | **Yes** — same PR as #1 | patch stays — **unless** the scroll/host cluster adopts S2-D's consumer `Decoration` window probe, which makes this one deletable |
| 3 | `LineCartesianLayerMarkerTarget.Point.isInterpolated` (B-10) | `Point` is a `data class` — not extensible — and `points: List<Point>` on the public interface is typed to it | **Yes** — 2-line defaulted parameter, the most trivially acceptable of the three | patch stays; or carry the flag out of band (consumer remembers which x it synthesised), which couples to C-7 in the layer-data cluster |

S2-D counted `ensureVerticalValuesAtLeast` as a fourth escape. It is not an escape, it is **DELETE** —
see B-4c.

**A fourth hook appears if D-1 is fixed the recommended way:** `CartesianMarker.drawLabels(context, targets)`.
That is an addition we are choosing, not one forced on us, and it folds into the same upstream PR as
#1 and #2 — the three together read as one coherent proposal: *give `CartesianMarker` explicit draw
phases and an idle phase.*

**Verdict on the question as posed: confirmed, with those corrections.** The subclass conversion is
worth doing. It takes the largest single block of marker code out of the library, it collapses an
existing duplicate in `BabyCrosshairLabel.kt`, and it leaves behind three defaulted hooks that make a
clean upstream PR instead of a 290-line patch on a file upstream is actively editing.

---

## 5. UNRESOLVED

Three items. None blocks the start of work; each blocks a specific step.

**U-1 — Does the `yLabelCallback` fallback path still run? (blocks: deleting B-8 instead of moving it)**
`GraphMarker.kt:108-124` mostly re-derives the entry from `markerIndex`, which meApp already has from
the controller callback; the `fallbackValues` are used only when the marker sits between data points,
i.e. only for interpolated targets. If that path is dead, B-8 is a DELETE rather than a MOVE.
*Settle by:* instrumenting `GraphMarker.rememberDefaultMarker`'s `yLabelCallback` to record whether
`fallbackValues` is ever the value actually used, and scrubbing a WEEK view with sparse readings —
the case B-10 exists for. One session's manual use is enough.

**U-2 — Does anything at v3.3.1 depend on `markerTargets` iteration order beyond `getMarkerTargets`? (blocks: optimising D-2's sort)**
`markerTargets` is `protected` (`$C/cartesian/CartesianChart.kt:169`), so it is part of the subclass
contract, and S1-E C-8 left this open. I verified the two in-library consumers:
`forEachPersistentMarker` (`:474`) is a map lookup, order-independent; `getMarkerTargets(x, visibleXRange)`
(`:486-494`) is order-**dependent**, which is the whole of D-2. I did not audit `CartesianChartRenderer`
or the glance module.
*Settle by:* `git grep -n "markerTargets" v3.3.1 -- vico/` and reading each hit. Ten minutes.
**Restoring the sort is safe regardless** — this only affects whether the cheaper
sort-once-per-repopulation optimisation is permissible.

**U-3 — Does WEEK snapping survive the move to `xSnapStep`? (blocks: M-3, and it is C2's open half)**
C2 adjudicated that YEAR cannot use `xSnapStep` (a month is not a uniform millisecond step) and that
WEEK "can plausibly" move to it. "Plausibly" is not settled, and M-3's verdict depends on it: if WEEK
also needs a custom behaviour, the passthrough stays in some form.
*Settle by:* the scroll cluster, not this one — build a WEEK chart with
`rememberVicoScrollState(xSnapStep = <one day in ms>)` and compare its rest positions after a fling
against the fork's `SnapFlingBehavior`. Flagged here only because M-3 is in my file.

**Explicitly NOT unresolved, though prior dossiers left it open:** whether `acceptsGestures` is
superseded. §3 settles it from the code, with a named five-minute gate before the delete lands.

---

## 6. Order of work for this cluster

Ten steps. The ordering is driven by three facts: B-11 is the easiest thing to lose, D-1 and D-2 are
defects whose symptoms masquerade as gesture bugs, and the meApp subclass cannot be written until the
hooks it overrides exist.

**0. Before anything.** Normalise formatting, or diff with `-w`. S1-C, S1-D and S2-A all independently
found that ktfmt 0.59 → 0.64 inflates the apparent conflict (14 files in `vico/compose`). Port as a
hand-applied `git diff e67e49520..HEAD -- <file>` per file — never a rebase or cherry-pick
(BASELINE process hazard, S2-A §1.1).

**1. B-11, alone, first.** The `TextComponent` one-word fix. Its own commit, verified by hand against
the merged file, and the upstream PR opened the same day. Everything downstream that measures a
label — including step 3's reservation — is wrong until this lands, and nothing will tell you.

**2. D-2.** Restore upstream's sort; fix the cache invalidation. Before any gesture work, so that
scrub misbehaviour cannot be blamed on the wrong layer.

**3. B-4c, jointly with S1-C.** Delete the `ensureValuesAtLeast` halving, delete
`ensureVerticalValuesAtLeast`, switch the marker's two call sites to `ensureValuesAtLeast`.
**One commit, no intermediate state** — either half alone is a silent layout bug (§2 B-4c).

**4. Decide D-1.** Recommended: add `CartesianMarker.drawLabels(context, targets)` and make
`CartesianChart.draw`'s late call use it. This must be decided before step 6, because the subclass's
method split *is* the fix.

**5. The interface patches: B-4a, B-5, B-10** (and `drawLabels` from step 4). Four defaulted,
additive members across three upstream-untouched files. They graft; they must exist before step 6
compiles; and they are one coherent upstream PR — *explicit draw phases and an idle phase for
`CartesianMarker`* — which should be opened at this point, not after.

**6. The meApp subclass: B-4b, B-7, B-8, and B-6's REPLACE; delete B-9.** The largest single piece of
work in the cluster and the largest single fork reduction. Carry across, as a checklist rather than a
diff: the crosshair's three `ab3fe3bfc` refinements; `yLabelCallback`'s empty-list-for-non-line-targets
contract; and the fact that B-6 is only safe once this subclass owns both `drawOverLayers` and
`updateLayerMargins`.

**7. B-3a + B-3b + M-4 together.** Delete `acceptsGestures` and its guard; move
`rememberNonInteractive` to meApp. **Run the §3 gate before committing.** Doing this before step 8
leaves one fewer condition in the `.then` chain to carry through the gesture rewrite.

**8. M-1, in a new file.** Extract the five-state machine to `ScrubGesture.kt` and reduce
`Modifier.kt` to a one-line delegation. Rebuild against the `Boolean` contract: route `Move`
consumption through the controller's own predicate (§2 M-1 leg 1), keep the explicit state check for
`Release`, and keep upstream's new `detectTapGestures` untouched. Then **M-2**: re-apply
`enabled = true`, and resist the invitation to use v3.3.1's mutable `scrollEnabled` instead.

**9. B-1 + B-2, with the host's H-5/H-6/B-12.** The controller class and the third callback parameter,
re-applied; the `scrollState` parameter deleted; the host's two effects re-derived onto v3.3.1's
changed overload set. These four move together or not at all — the controller, the callback shape and
the host's marker plumbing are one contract.

**10. M-3, once C2 lands.** Whatever survives, rename it: upstream's `internal class SnapFlingBehavior`
occupies our file's package and simple name.

**Then the parity harness** (`S4-A`), with these five checks specific to this cluster, in this order:
the snapshot card opens the full graph on tap (step 7's gate); the date-range caption appears at rest
and is replaced by the marker label while marked, on both charts; a 200 ms hold starts a scrub and the
parent `LazyColumn` does not move during it; a horizontal drag on the TOTAL chart does not scroll the
dashboard; and the guideline's opacity matches the design (the D-1 regression test — a double-drawn
translucent line is the only visible trace that fix leaves).
