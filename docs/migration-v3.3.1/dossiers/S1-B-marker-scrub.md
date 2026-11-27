# S1-B — Markers, scrubbing, and the marker label

Cluster dossier for the v3.3.1 migration. Refs per `../BASELINE.md`:
FORK_POINT `e67e49520`, OURS `HEAD`, TARGET `v3.3.1` (**not evaluated here** — another agent owns that).

Files owned by this dossier (all under
`vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/`):

| Path | Ours | Upstream churn since fork |
|---|---|---|
| `cartesian/marker/ScrubMarkerController.kt` | NEW, 158 lines | absent upstream |
| `cartesian/marker/DefaultCartesianMarker.kt` | 288+/19- | 39+/25- |
| `cartesian/marker/CartesianMarker.kt` | +21 | untouched |
| `cartesian/marker/CartesianMarkerController.kt` | +36 | untouched |
| `cartesian/marker/LineCartesianLayerMarkerTarget.kt` | +2 | untouched |
| `common/component/TextComponent.kt` | 4+/1- | 59+/61- (rewritten) |

The cluster exists to imitate the **iOS Health app's scrub interaction** on the meApp dashboard
graph: a chart inside a vertically scrolling list that you can swipe horizontally to scroll, hold
and drag to scrub a marker along the data, and tap to place or clear one — with a header above the
chart that follows whatever is marked, and a date-range caption that occupies the marker's own
label line while nothing is marked.

---

## Summary table

| id | name | files | conflict risk | undocumented | meApp-visible |
|---|---|---|---|---|---|
| B-1 | ScrubMarkerController — hold-to-scrub gesture controller | `ScrubMarkerController.kt` (new) | **medium** (file is new; its contract is bolted to `Modifier.kt` + `CartesianChartHost.kt`, both high-churn) | partly (FEATURE-2 covers the original; 4 later behaviour changes are not) | **yes** — `GraphView.kt`, `ProductChart.kt` |
| B-2 | Gesture identity in the marker callback (`interaction` 3rd param) | `ScrubMarkerController.kt`, (`CartesianChartHost.kt`) | low | **yes** | **yes** — `GraphView.handleMarkerIndexChanged` |
| B-3 | A chart that declines the gesture (`acceptsGestures`, `rememberNonInteractive`) | `CartesianMarkerController.kt`, (`Modifier.kt`) | low (upstream untouched) | **yes** | **yes** — `SnapshotLineChart.kt` |
| B-4 | Resting marker label (`drawResting`, `restingLabel`, `labelGap`, measured margin) | `CartesianMarker.kt`, `DefaultCartesianMarker.kt`, (`CartesianChart.kt`, `CartesianLayerMargins.kt`) | **high** | **yes** | **yes** — `GraphMarker.kt`, `ChartHeader.kt` |
| B-5 | Format the resting text on scroll settle (`onScrollSettled`) | `CartesianMarker.kt`, `DefaultCartesianMarker.kt`, (`CartesianChartHost.kt`) | medium | **yes** | yes, indirectly (`rangeLabelFormatter`) |
| B-6 | Optional `label` — a marker that only ever rests | `DefaultCartesianMarker.kt` | **high** (signature break on the primary ctor + `remember` fn) | **yes** | **yes** — `rememberRangeOnlyMarker` |
| B-7 | Horizontal crosshair + crosshair label | `DefaultCartesianMarker.kt` | **high** | no (MA-3773 pre-dates docs; refinement `ab3fe3bfc` is undocumented) | **yes** — `BabyCrosshairLabel.kt` |
| B-8 | `yLabelCallback` — per-series Y values out to the chart header | `DefaultCartesianMarker.kt` | medium | no (FEATURE-8) | **yes** — `GraphMarker.rememberDefaultMarker` |
| B-9 | `contentPadding` + guideline extension beyond the plot | `DefaultCartesianMarker.kt` | medium | no (FEATURE-8, now **stale**) | **no** (vestigial — no meApp caller) |
| B-10 | `isInterpolated` on `LineCartesianLayerMarkerTarget.Point` | `LineCartesianLayerMarkerTarget.kt`, `DefaultCartesianMarker.kt` | low (upstream untouched) | no (FEATURE-8) | no (library-internal; visible as "no dot between points") |
| B-11 | `TextComponent.getBounds` measured the literal word "null" | `TextComponent.kt` | **high collision, trivial content** | **yes** | yes, indirectly (axis gutters, marker margin) |
| B-12 | Caller-owned marker clearing (`initialMarkerX` keyed on value) | `CartesianChartHost.kt` — **owned by the host cluster**, recorded here because it is half of B-1's contract | n/a here | **yes** | **yes** — `GraphView` / `segmentState.markerIndex` |

Commit set for this cluster (`git log --oneline e67e49520..HEAD -- <files>`), newest first:

```
2e2596784 MOB-3494: stop measuring the word "null" when there is no text
ab3fe3bfc MOB-2953: hold the crosshair inside the chart, and its label above the points
b7d7e0583 MOB-2953: tell the marker callback which gesture called it
3faabb926 MOB-2953: let a chart decline the gesture as well as the marker
08d194bbd MOB-2953: let a marker rest without ever marking, and settle on model change
b271cb4ba MOB-2953: measure the marker label's height instead of stating it
39bca6cb9 MOB-2953: format the marker's resting text on scroll settle, not per frame
2433daf0d MOB-2953: give the marker a resting label and make its gap a parameter
45169bb5f MA-3773: DefaultCartesianMarker horizontal crosshair + label (v4.0.28)
8052261ae fix: Marker dismiss on scroll start only + no viewport change for scrub
b5343399c fix: Tap always shows marker (v3 parity) — no toggle dismiss on same position
196bb54af feat(marker): Add yLabelCallback + isScrolling for chart header updates
2c00808b6 feat(marker): Interpolated markers without indicator + accept empty target taps
24858daf2 feat: Add contentPadding to DefaultCartesianMarker (v3 parity)
ccf2ff5cf Feature-2: Add ScrubMarkerController for iOS Health-like marker scrubbing
```

Plus `bd58edc86` (B-12), whose only file is `CartesianChartHost.kt`.

---

## B-1 — ScrubMarkerController

**What it does.** A new `CartesianMarkerController` that implements hold-to-scrub: a press starts a
200 ms timer; if the finger stays put the timer fires, the chart's scroll is frozen and the marker
follows the finger; if the finger moves horizontally first, the chart scrolls instead and any
marker is dismissed. It owns two pieces of state (`isScrubbing`, `hasActiveMarker`) that the
gesture code in `Modifier.kt` and the host in `CartesianChartHost.kt` both write to and read from,
and it carries the consumer's `onMarkerIndexChanged` callback that decides which _x_ the marker
lands on.

**Why we needed it.** This is the core of the Health-app imitation. Vico's stock controllers are
`ShowOnPress`, `ShowOnHover` and `ToggleOnTap` — all of them decide on a single event. None of them
can distinguish "the user is dragging the chart" from "the user is dragging the marker", which is
precisely the gesture the dashboard graph needs because the chart lives inside a `LazyColumn` and
is itself horizontally scrollable. The controller also lets meApp own marker placement entirely
(snap to a visible axis label, not merely to the nearest data point), which v3's `onChartClick`
used to do and which vico's internal nearest-target logic cannot express.

**Commits.**
- `ccf2ff5cf` Feature-2: Add ScrubMarkerController for iOS Health-like marker scrubbing
- `b5343399c` fix: Tap always shows marker (v3 parity) — no toggle dismiss on same position
- `8052261ae` fix: Marker dismiss on scroll start only + no viewport change for scrub
- `2c00808b6` feat(marker): …accept empty target taps (the `shouldAcceptInteraction` half)
- `b7d7e0583` (callback shape — see B-2)

**Files and hunks.**
- `cartesian/marker/ScrubMarkerController.kt:1-158` — the whole file.
  - `:53-73` class header and the three overridden controller properties.
  - `:75-87` `shouldAcceptInteraction` — accepts `Tap`/`LongPress` **even with an empty target
    list**, accepts `Move` only while scrubbing, always refuses `Press`.
  - `:89-118` `shouldShowMarker` — the state transitions. `Tap` always shows (never toggles);
    `LongPress` also sets `isScrubbing = true`; `Release` clears `isScrubbing` and keeps the marker.
  - `:120-127` `onDismiss()` — idempotent, guarded on `!hasActiveMarker && !isScrubbing`, and
    notifies the consumer with `(null, emptyList(), null)`.
  - `:129-132` `equals`/`hashCode` on `delayMs` alone — deliberate, so the controller instance is
    stable across recompositions while the callback is swapped through `rememberUpdatedState`.
  - `:143-158` `rememberScrubMarkerController`.

**Public API added or changed.**

```kotlin
package com.patrykandpatrick.vico.compose.cartesian.marker

public class ScrubMarkerController(
  public val delayMs: Long = 200L,
  internal val onMarkerIndexChanged:
    ((clickX: Double?, targets: List<Double>, interaction: Interaction?) -> Double?)? = null,
) : CartesianMarkerController {
  public var isScrubbing: Boolean          // getter public, `internal set`
  internal var hasActiveMarker: Boolean
  override val acceptsLongPress: Boolean = false
  override val consumeMoveEvents: Boolean = true
  override val lock: CartesianMarkerController.Lock = CartesianMarkerController.Lock.Position
  override fun shouldAcceptInteraction(
    interaction: Interaction,
    targets: List<CartesianMarker.Target>,
  ): Boolean
  override fun shouldShowMarker(
    interaction: Interaction,
    targets: List<CartesianMarker.Target>,
  ): Boolean
  internal fun onDismiss()
  override fun hashCode(): Int
  override fun equals(other: Any?): Boolean
}

@Composable
public fun rememberScrubMarkerController(
  scrollState: VicoScrollState,
  delayMs: Long = 200L,
  onMarkerIndexChanged:
    ((clickX: Double?, targets: List<Double>, interaction: Interaction?) -> Double?)? = null,
): ScrubMarkerController
```

Two notes on that contract, both load-bearing:
- the constructor's callback parameter is **`internal`**, so a consumer in another module cannot
  pass it directly. `rememberScrubMarkerController` is the only usable entry point. Preserve both
  or the feature is unreachable from meApp.
- **`scrollState` is accepted and never read** (`ScrubMarkerController.kt:144-157` uses only
  `delayMs` and the callback). It is a leftover from the first design where the controller did its
  own scroll-dismiss; `8052261ae` moved that to the host and left the parameter. meApp passes
  `scroll.scrollState` into it (`GraphView.kt:268-269`). Dropping it is a source break for one call
  site; keeping it is a lie in the API. Flagging, not deciding.

**Coupling to upstream internals.** This is the deepest coupling in the cluster.
- `Modifier.kt` (high-churn, 234+/27- ours) **type-tests** for `ScrubMarkerController`
  (`:72 markerController as? ScrubMarkerController`) and runs an entirely separate ~180-line
  `pointerInput` state machine when the cast succeeds, bypassing upstream's `detectTapGestures`
  path (`:280 markerController !is ScrubMarkerController`). It reaches into the controller's
  `delayMs` (`:104`), writes `isScrubbing` (`:108, :135, :205`), reads `hasActiveMarker` (`:137`)
  and calls the `internal fun onDismiss()` (`:138`).
- It also writes `VicoScrollState.isScrollFrozen` (`:109, :136, :146, :206`) — a fork-added
  property. **The scrollable modifier is left `enabled = true` permanently**
  (`Modifier.kt:59-66`) so `nestedScroll` still claims deltas and the parent `LazyColumn` stays
  blocked during a scrub; freezing happens inside `ScrollableState`, not by disabling it.
- `CartesianChartHost.kt` type-tests the controller three times: to install the
  `hasActiveMarker` ⇄ `markerX` sync (`:255, :270-281`), to route `markerX` through the consumer
  callback instead of vico's nearest-target logic (`:376-397`), and to **skip `onViewportChange`
  entirely** (`:416-417`) so a scroll never replays the last accepted interaction at a shifted
  position. That last one silently disables upstream's `Lock.Position` replay machinery for this
  controller — even though the controller declares `lock = Lock.Position`. The declaration is
  therefore inert; do not take it as a statement of behaviour.
- `CartesianChart.allMarkerTargetXValues` (internal, cached accessor added by `ccf2ff5cf`) is the
  list handed to the callback.

**Could it be an extension point, or live in meApp instead?** Partly, and it is worth real effort.
The controller class itself is additive and could in principle live in meApp — it implements a
public interface. What blocks that today is the three `as? ScrubMarkerController` type-tests. If
the gesture behaviour were expressed as controller **properties** rather than as a type test —
something like `holdDelayMs: Long?` (null = no hold-to-scrub), `dismissOnScroll: Boolean`,
`replayOnViewportChange: Boolean` on `CartesianMarkerController`, plus making `onDismiss()` a
public interface method — then `Modifier.kt` and the host would branch on data, not on identity,
and `ScrubMarkerController` could be a meApp class. That is the single largest fork-surface
reduction available in this cluster. The `isScrollFrozen` write is the one piece that genuinely
needs library cooperation. **Recommend: pursue this.**

**Conflict risk: medium.** The file is new so it cannot conflict textually. The risk is that its
counterparties — `Modifier.kt` (ours 234+/27-, upstream 121+/25-) and `CartesianChartHost.kt`
(ours 454+/8-, upstream 196+/94-) — are two of the four hottest files on both sides, and the
controller is useless without them. FEATURE-2 already rates `Modifier.kt` **CRITICAL** for this
reason and I agree.

**Doc disagreements (trust the code).**
- FEATURE-2's gesture table and `ScrubMarkerController.kt:33` both say **"Tap: toggle marker"**.
  The code has not toggled since `b5343399c` — `shouldShowMarker` returns `true` for every tap.
  Toggle now lives in meApp (`GraphView.kt:531-534`, `!(isTap && it == segmentState.markerIndex)`),
  which is only possible because of B-2. The file's own KDoc contradicts its own body.
- FEATURE-2 §"Scroll-Dismiss Flow" step 4 describes a `consumedXDeltas` `LaunchedEffect` that
  dismisses the marker. `8052261ae` **removed** it (it false-dismissed on snap-settle and on tap);
  dismissal now runs through `snapshotFlow { hasActiveMarker }`.
- `ScrubMarkerController.kt:38` says `isScrubbing` is "read by Modifier.kt to disable scrollable
  modifier". It is not — `Modifier.kt` only ever *writes* it, and the scrollable is never disabled.
  `isScrubbing` is read only inside the controller itself (`:83`, `:106`). FEATURE-2's own
  §"Scroll Disable During Scrub" gets this right, so the doc and the KDoc disagree with each other.
- FEATURE-2 and FEATURE-8 both show the callback with **two** parameters. It has three (B-2).

---

## B-2 — Gesture identity in the marker callback

**What it does.** Widens `onMarkerIndexChanged` by a third parameter, `interaction: Interaction?`,
so the consumer is told which gesture produced the call. It is the actual `Interaction` instance
the host is about to act on; it is `null` when nothing the user did caused the call — i.e. a
programmatic dismiss.

**Why we needed it.** A scrub reports its `Move`s and the `Release` that ends it through the same
callback a `Tap` arrives on. meApp wants "tap the point that is already marked to clear it", which
is a comparison of the returned _x_ against the currently marked one — and that comparison fired as
the finger lifted off a scrub, clearing the marker the scrub had just placed. With the gesture in
hand the consumer can scope the rule to taps alone. This is the piece that let the toggle move out
of the library into meApp (see B-1's doc disagreement).

**Commits.** `b7d7e0583` MOB-2953: tell the marker callback which gesture called it.

**Files and hunks.**
- `ScrubMarkerController.kt:43-51` (KDoc), `:55-56` (ctor param type), `:126`
  (`onMarkerIndexChanged?.invoke(null, emptyList(), null)`), `:140-141`, `:147-148`, `:154-155`.
- `CartesianChartHost.kt:377, 385, 395` — the host passes `interaction` through on both the show
  and the dismiss branch.

**Public API added or changed.** The callback type, in both the constructor and the composable:

```kotlin
((clickX: Double?, targets: List<Double>, interaction: Interaction?) -> Double?)?
// was: ((clickX: Double?, targets: List<Double>) -> Double?)?
```

`Interaction` is an upstream public sealed class (`cartesian/marker/Interaction.kt`), unchanged by
us — so this adds no new type, only a parameter.

**Coupling to upstream internals.** Minimal and clean: the host already held the `Interaction`
instance (`CartesianChartHost.kt:366-377`), it simply was not forwarded. No private state reached
into.

**Could it be an extension point, or live in meApp instead?** It cannot live in meApp — only the
host knows which gesture it is acting on. But it is a strictly additive widening of a fork-local
callback; if B-1's type test were ever replaced by a general extension point, this parameter should
travel with it.

**Conflict risk: low.** The ScrubMarkerController side is a new file. The host side is three
argument-list edits inside our own `if (scrubCallback != null)` block, which is itself fork-local.

---

## B-3 — A chart that declines the gesture

**What it does.** Adds `acceptsGestures: Boolean` (default `true`) to `CartesianMarkerController`,
and a `rememberNonInteractive()` controller that returns `false` for it and refuses every
interaction. `Modifier.kt` checks the flag before attaching `detectTapGestures` at all.

**Why we needed it.** The dashboard snapshot card (`SnapshotLineChart.kt`) shows a small chart that
is read, not explored — and the whole card is tappable to open the full graph. A chart with a
marker attaches a tap detector, and a tap detector **consumes** the tap, so the card stopped
opening. Separately, taking the default `ShowOnPress` controller meant a finger on the card marked
a point and replaced the resting date-range label for as long as the touch lasted. Marking and
claiming are two decisions: a controller that declines the first must also be able to decline the
second, or the chart is inert and everything around it is too.

**Commits.** `3faabb926` MOB-2953: let a chart decline the gesture as well as the marker.

**Files and hunks.**
- `CartesianMarkerController.kt:41-49` — the `acceptsGestures` property with KDoc.
- `CartesianMarkerController.kt:106-116` — `rememberNonInteractive()` in the companion (`:114`).
- `CartesianMarkerController.kt:120-133` — `private object NonInteractiveMarkerController`.
- `Modifier.kt:274-282` — `markerController?.acceptsGestures != false` added to the condition
  guarding `detectTapGestures`.

**Public API added or changed.**

```kotlin
public fun interface CartesianMarkerController {
  public val acceptsGestures: Boolean
    get() = true
  // …
  public companion object {
    @Composable public fun rememberNonInteractive(): CartesianMarkerController
  }
}
```

`CartesianMarkerController` remains a `fun interface` — `shouldShowMarker` is still its only
abstract member (`acceptsGestures` has a default body, as do `acceptsLongPress`, `lock`,
`consumeMoveEvents` and `shouldAcceptInteraction`). Adding a defaulted property does not break SAM
conversion.

**Coupling to upstream internals.** Only to `Modifier.kt`'s decision about whether to install
`detectTapGestures`. Nothing private. **Uncertainty I could not close:** the `else` branch of the
`pointerInput` block (upstream's own pointer loop) is still installed for a non-interactive
controller, because the guard is only on `detectTapGestures`. Whether that loop consumes anything
that would matter to a surrounding tappable card — I did not read it closely enough to say. In
practice the card works, so presumably not, but a migration should re-verify "tap the snapshot card
opens the graph" rather than assume.

**Could it be an extension point, or live in meApp instead?** This one is the best candidate in the
whole cluster to **propose upstream**. It is twelve lines, it is a genuine gap in vico's API
(nothing today lets a chart opt out of consuming touches), and it has no meApp specificity
whatsoever. If upstream takes it the fork shrinks by a file. Failing that, `rememberNonInteractive`
itself could live in meApp — a `CartesianMarkerController` that returns `false` everywhere is
trivially expressible by a consumer — but `acceptsGestures` cannot, because only `Modifier.kt` can
act on it.

**Conflict risk: low.** `CartesianMarkerController.kt` is on BASELINE's "upstream never touched"
list, so our additions graft cleanly. The `Modifier.kt` one-line condition is inside a region we
have already heavily rewritten for B-1 and will be resolved as part of that.

---

## B-4 — The resting marker label

**What it does.** Gives `CartesianMarker` a second drawing entry point, `drawResting(context)`,
which the chart calls **instead of** the marker pass whenever nothing is marked.
`DefaultCartesianMarker` implements it as a `restingLabel` text component plus a formatter, drawn
on exactly the line the marker's own label would occupy. The space outside the plot is now reserved
as `labelGap + max(label height, restingLabel height) + tickSize`, measured rather than stated.

**Why we needed it.** The dashboard graph has a date-range caption ("jun 29 – jul 5, 2025") that
sits above the plot and must vanish the moment a marker appears, because the marker's label lands
in the same place. meApp used to draw that caption itself in a Compose `Text` above the chart —
which meant **guessing** where vico would put the marker label, and blanking its own text whenever
a marker existed. Two owners for one line. `drawResting` hands the line to whichever of the two is
live, and the chart — the only thing that actually knows where the line is — places both. In the
Health-app analogy this is the date range that sits above the chart and is replaced by the scrubbed
reading.

**Commits.**
- `2433daf0d` MOB-2953: give the marker a resting label and make its gap a parameter
- `b271cb4ba` MOB-2953: measure the marker label's height instead of stating it
- `08d194bbd` MOB-2953: …(the `labelGap` half — it now *places* the label as well as reserving for it)

**Files and hunks.**
- `CartesianMarker.kt:36-44` — `public fun drawResting(context: CartesianDrawingContext) {}`.
- `DefaultCartesianMarker.kt:381-402` — `drawResting` override. Draws at
  `layerBounds.getStart(isLtr)` with `horizontalPosition = -Position.Horizontal.Start`.
- `DefaultCartesianMarker.kt:404-416` — `restingLabelAnchor()`, which resolves the same _y_
  expression `drawLabel` uses so the two cannot drift:
  ```kotlin
  LabelPosition.Bottom ->
    layerBounds.bottom + tickSize.pixels + labelGap.pixels to Position.Vertical.Bottom
  else ->
    layerBounds.top - tickSize.pixels - labelGap.pixels to Position.Vertical.Top
  ```
- `DefaultCartesianMarker.kt:315, 321` — `drawLabel`'s Top/Bottom _y_ now uses `labelGap`
  (was `contentPadding.bottom` / `contentPadding.top`).
- `DefaultCartesianMarker.kt:431, 446` — `drawGuideline`'s Top/Bottom extents follow suit.
- `DefaultCartesianMarker.kt:466-497` — `updateLayerMargins` rewritten (the new arithmetic is `:485-493`):
  ```kotlin
  val labelHeight = maxOf(label?.getHeight(this) ?: 0f, restingLabel?.getHeight(this) ?: 0f)
  if (labelHeight == 0f) return
  val reserved = labelGap.pixels + labelHeight + tickSize.pixels
  … layerMargins.ensureVerticalValuesAtLeast(top = reserved)
  ```
- `CartesianChart.kt:412` (host cluster) — the dispatch:
  ```kotlin
  if (drawMarker) marker?.drawOverLayers(context, markerTargets) else marker?.drawResting(context)
  ```
- `CartesianLayerMargins.kt:62-79` (host/axis cluster) — `ensureVerticalValuesAtLeast`, added
  because our `ensureValuesAtLeast` **halves** top and bottom (also a fork change, same commit) and
  a marker label sits wholly outside the plot so it needs all of what it asks for.

**Public API added or changed.**

```kotlin
// CartesianMarker
public fun drawResting(context: CartesianDrawingContext) {}

// DefaultCartesianMarker — new constructor parameters
protected val restingLabel: TextComponent? = null,
protected val restingLabelFormatter: ((ClosedFloatingPointRange<Double>) -> CharSequence?)? = null,
protected val labelGap: Dp = 0.dp,

// DefaultCartesianMarker — new protected open member
protected open fun CartesianDrawingContext.restingLabelAnchor(): Pair<Float, Position.Vertical>

// CartesianLayerMargins (adjacent cluster, but this adaptation depends on it)
public fun ensureVerticalValuesAtLeast(top: Float = this.top, bottom: Float = this.bottom)
```

An earlier `restingLabelPosition: Position.Horizontal = Position.Horizontal.Start` parameter
existed between `2433daf0d` and `b271cb4ba` and was **removed**; the resting text is always drawn
from the plot's start, and where it sits across the plot is the component's own `margins` to say
(meApp does exactly that — `GraphMarker.kt:60` sets `margins = Insets(start = spacing.sm)`).
Do not resurrect it. `labelGap`'s default also moved `12.dp → 0.dp` in `b271cb4ba` once the height
stopped being folded into it; meApp passes `CHART_LABEL_GAP = 10.dp`.

**Coupling to upstream internals.**
- **Draw-order**: `drawResting` is called from the *last* draw step in `CartesianChart.draw`, past
  the fading-edge `saveLayer`/`restore` and past `axisManager.drawMarkerDecorations`, and
  **outside** the start-strip clip. It assumes it is drawing onto an unclipped, unlayered canvas.
- It assumes `layerMargins` has already been grown by `updateLayerMargins`, i.e. that measuring and
  drawing see the same `labelGap`/height arithmetic. The two are written as the same expression in
  two places (`restingLabelAnchor` and `updateLayerMargins`) and will silently disagree if only one
  is edited.
- It depends on our `ensureValuesAtLeast` halving behaviour being *avoided* via the new method.
  If a migration takes upstream's un-halved `ensureValuesAtLeast`, `ensureVerticalValuesAtLeast`
  becomes a redundant alias and every *other* caller's margins double. **Resolve those two together
  or not at all.**
- `TextComponent.getHeight(context)` is called with **no text**, which relies on the `pad` branch
  padding the layout out to `lineCount` lines — which is exactly the code B-11 fixes. B-4 and B-11
  are coupled: before B-11 this measured a line containing the word "null".

**Could it be an extension point, or live in meApp instead?** Not as it stands, and I think that is
correct. The whole point is that meApp *cannot* know where the line is. A smaller library surface
would be a single `drawResting(context)` hook on `CartesianMarker` with **no** `restingLabel` /
`restingLabelFormatter` / `restingLabelAnchor` in `DefaultCartesianMarker` at all — meApp would
subclass `DefaultCartesianMarker`, override `drawResting`, and draw its own `TextComponent` using a
public accessor for the label line's _y_ (which `restingLabelAnchor` already is, in protected form).
That would cut three constructor parameters and ~40 lines from the hottest file in the cluster and
move the date-range specifics to meApp, where they belong. `labelGap` and the measured-height
margin must stay in the library either way. **Recommend: investigate this split.**

**Conflict risk: high.** `updateLayerMargins`, `drawLabel` and `drawGuideline` are all rewritten by
us, all in the file upstream also changed (39+/25-), and the margin arithmetic additionally depends
on a fork change in `CartesianLayerMargins.kt`. `drawResting` itself is additive and safe; the
margin/placement arithmetic around it is the dangerous part.

---

## B-5 — Format the resting text on scroll settle

**What it does.** Adds `CartesianMarker.onScrollSettled(visibleXRange)`. The host emits every
window the draw pass sees into a shared flow, a collector takes the first one immediately and then
debounces by 100 ms, and `DefaultCartesianMarker` formats the resting text once per settle into a
Compose `mutableStateOf`. `drawResting` then only *places* the already-formatted string.

**Why we needed it.** `drawResting` as first written ran the consumer's formatter on every frame
and threw the result away. For meApp's date range that means day snapping, clipping and
`Instant`/`ZoneId` formatting sixty times a second — and, worse, the caption flickered through
every window the scroll passed over instead of naming the one it stopped at. The Health app names
the window you land on, not the ones you fly past. The 100 ms matches `ScrollAwareRangeProvider`'s
settle, so the caption and the axis it describes come to rest together.

**Commits.** `39bca6cb9` MOB-2953: format the marker's resting text on scroll settle, not per frame.
Also `08d194bbd`, which re-keyed the host's collector on `model` as well as the scroll — new data
can change what the caption says without changing the window, and a card naming the week its
entries fall in went on naming the old one.

**Files and hunks.**
- `CartesianMarker.kt:46-54` — `public fun onScrollSettled(visibleXRange: ClosedFloatingPointRange<Double>) {}`.
- `DefaultCartesianMarker.kt:103-114`:
  ```kotlin
  private var restingText: CharSequence? by mutableStateOf(null)

  override fun onScrollSettled(visibleXRange: ClosedFloatingPointRange<Double>) {
    restingText = restingLabelFormatter?.invoke(visibleXRange)
  }
  ```
- `DefaultCartesianMarker.kt:391` — `val text = restingText ?: return` (was a live
  `restingLabelFormatter?.invoke(getVisibleXRange())`; the `getVisibleXRange` import went with it).
- `CartesianChartHost.kt:313-328` (host cluster) — the `MutableSharedFlow(replay = 1,
  extraBufferCapacity = 8)`, the `LaunchedEffect(chart.marker, visibleXRanges, model)`, the
  first-then-debounced pair, and `MARKER_SETTLE_MS = 100L` at `:560`.
- `CartesianChartHost.kt:504` — `if (chart.marker != null) visibleXRanges.tryEmit(visibleRange)`,
  inside the existing viewport guard.

**Public API added or changed.**

```kotlin
// CartesianMarker
public fun onScrollSettled(visibleXRange: ClosedFloatingPointRange<Double>) {}
```

Nothing else. `restingText` is private; the state-ness of it is the contract (the draw that reads
it must re-run when it changes), not the field.

**Coupling to upstream internals.**
- Requires the host to compute the visible _x_ range during the draw pass and emit it. That
  computation is shared with `ScrollAwareRangeProvider`'s feed and sits behind the same
  change-detection guard (`lastEmittedScroll` / `lastEmittedXSpacing` / `lastEmittedChartWidth`),
  so a draw that leaves the viewport alone costs nothing. If upstream restructures how the host
  tracks the viewport, this emit site moves with it.
- `restingText` being Compose state means `onScrollSettled` must be called from somewhere a
  snapshot write is legal, and the draw must be reading it inside a composition-observed scope.
  That is true today because the chart draws from a `Canvas` composable.
- The marker instance must survive recomposition, or the formatted string is lost. This is why
  `rememberDefaultCartesianMarker` keys on the *components*, not on the formatters, and routes
  every formatter through `rememberUpdatedState`. meApp learned this the hard way —
  `GraphMarker.kt:213` carries a comment about a `valueFormatter` that was rebuilt each time and
  "lost what it holds".

**Could it be an extension point, or live in meApp instead?** The hook is already minimal and
general — "tell the marker the window, once it settles" is a reasonable thing for a chart library
to offer and has no meApp in it. If B-4 moves the resting-label specifics into a meApp subclass,
`onScrollSettled` is exactly the hook that subclass needs, so it stays. The 100 ms constant could
reasonably become a parameter rather than a `private const` if upstream ever generalises it.

**Conflict risk: medium.** `CartesianMarker.kt` is upstream-untouched (graft clean) and the
`DefaultCartesianMarker` side is a small additive block. The exposure is entirely in
`CartesianChartHost.kt` (ours 454+/8-, upstream 196+/94-), whose flow plumbing may need
re-threading onto a restructured host.

---

## B-6 — Optional `label`: a marker that only ever rests

**What it does.** Makes `DefaultCartesianMarker.label` nullable with a `null` default, in both the
class constructor and `rememberDefaultCartesianMarker`. Every path that draws or measures the label
now bails out on null, and `updateLayerMargins` reserves nothing when both labels are absent.

**Why we needed it.** The snapshot card's chart has no gesture that can mark a point (B-3) — but
the date range still belongs on the same line, at the same size, as it does on the dashboard graph.
So the card needs a marker that is *only* a resting label. With `label` mandatory, meApp had to
construct a throwaway `TextComponent` that would never be drawn, which also silently inflated the
reserved margin (`maxOf(label, restingLabel)`).

**Commits.** `08d194bbd` MOB-2953: let a marker rest without ever marking, and settle on model change.

**Files and hunks.**
- `DefaultCartesianMarker.kt:81` ctor: `protected val label: TextComponent? = null,`.
- `:98` — `(label?.background as? ShapeComponent)?.shape as? MarkerCornerBasedShape`.
- `:242` — `val label = label ?: return` in `drawHorizontalCrosshairLabel`.
- `:302` — `val label = label ?: return` in `drawLabel`.
- `:485-486` — `maxOf(label?.getHeight(this) ?: 0f, restingLabel?.getHeight(this) ?: 0f)` and
  `if (labelHeight == 0f) return`.
- `:512` — `var result = label?.hashCode() ?: 0`.
- `:668-669` — `rememberDefaultCartesianMarker(label: TextComponent? = null, …)`.

**Public API added or changed.**

```kotlin
// was: protected val label: TextComponent,
protected val label: TextComponent? = null,
// was: public fun rememberDefaultCartesianMarker(label: TextComponent, …)
public fun rememberDefaultCartesianMarker(label: TextComponent? = null, …)
```

This is the only **breaking** change in the cluster to a pre-existing signature: `label` moves from
the first required positional parameter to an optional one. Source-compatible for named arguments
and for anyone who passed it first positionally; **not** binary-compatible.

**Coupling to upstream internals.** None beyond `DefaultCartesianMarker` itself. It is a nullability
widening of an upstream-declared property. The subtle part is `markerCornerBasedShape`/`tickSize`,
which derive from `label.background` and now quietly become `null`/zero for a resting-only marker —
correct, since there is no bubble to put a tick on, but it means `tickSize` is zero in the margin
arithmetic for such markers.

**Could it be an extension point, or live in meApp instead?** No — it is a one-character change to
an upstream declaration and cannot be expressed from outside. It is also the kind of thing upstream
might plausibly accept. Note that if B-4's "move the resting label into a meApp subclass" idea is
taken, **this change is still needed**, because that subclass would still want no marker label.

**Conflict risk: high.** Not because the change is large — it is seven small edits — but because it
touches the constructor signature and five separate methods of the file upstream also edits, and
because a resolution that takes upstream's `label: TextComponent` for any one of those sites
produces a compile error at best and a crash on the snapshot card at worst. Treat the null-guards
as a checklist, not as diff hunks.

---

## B-7 — Horizontal crosshair and its label

**What it does.** When `horizontalLabelPosition` is non-null, the marker draws a **horizontal** line
across the plot at the marked point's canvas-_y_, reusing the `guideline` component, and anchors a
label to it at Start/Center/End whose text comes from `horizontalLabelFormatter(yValues, x)`. The
line is drawn *before* the indicator dots and the label *after* them, as two separate passes.

**Why we needed it.** The baby growth dashboard shows the CDC percentile of whatever point is being
scrubbed ("50%", "< 1%", "> 99%") on a horizontal rule at the point's height — the iOS Health
growth-chart presentation. The value is age-dependent, so it has to be recomputed per scrub
position from the hovered _x_, which is why the formatter takes `(List<List<Double>>, Double)` and
not just a string.

Three refinements in `ab3fe3bfc`, all of them bugs found in the field:
1. the line spanned `layerBounds.left`, so it ran through the blank strip before the first entry and
   out past the start axis line that marks where the chart begins — but the marker is drawn
   *outside* the start-strip clip on purpose (clipping it sliced the bubble and the point's radius
   in half), so the crosshair has to respect the start itself;
2. the label sat against the raw layer edge rather than after the axis line;
3. the label was drawn in the same pass as its line, i.e. *under* the indicator dots, so a dot
   drawn over the text won and the text is there to be read.

**Commits.**
- `45169bb5f` MA-3773: DefaultCartesianMarker horizontal crosshair + label (v4.0.28)
- `ab3fe3bfc` MOB-2953: hold the crosshair inside the chart, and its label above the points

**Files and hunks.**
- `DefaultCartesianMarker.kt:62-70` — KDoc for both parameters.
- `:90-91` — the two constructor parameters.
- `:126-131` — `drawGuideline(targets)` then `drawHorizontalCrosshair(context, targets)`, both
  before the indicator loop.
- `:167-170` — `drawHorizontalCrosshairLabel(...)` after the indicator loop.
- `:187-198` — `crosshairAnchor()`: `targets.lastOrNull { it is LineCartesianLayerMarkerTarget }`.
  The *last* line layer, not the first, because charts with percentile bands push the bands as
  earlier layers and the real data line as the final one; `points[0]` would snap the crosshair onto
  the lowest band.
- `:200-211` — `chartStartX()`:
  ```kotlin
  private fun CartesianDrawingContext.chartStartX(): Float =
    (layerBounds.left + layerDimensions.startPadding - scroll)
      .coerceIn(layerBounds.left, layerBounds.right)
  ```
- `:213-221` — `drawHorizontalCrosshair`, line only, `chartStartX() → layerBounds.right`.
- `:223-277` — `drawHorizontalCrosshairLabel`, including the `Position.Vertical.Top.inBounds(...)`
  flip-to-below-when-it-would-clip logic and the Start-anchored `chartStart + labelGap.pixels`.

**Public API added or changed.**

```kotlin
// DefaultCartesianMarker — new constructor parameters
protected val horizontalLabelPosition: Position.Horizontal? = null,
protected val horizontalLabelFormatter: ((List<List<Double>>, Double) -> CharSequence?)? = null,

// DefaultCartesianMarker — new protected members
protected fun drawHorizontalCrosshair(
  context: CartesianDrawingContext,
  targets: List<CartesianMarker.Target>,
): Unit
protected fun drawHorizontalCrosshairLabel(
  context: CartesianDrawingContext,
  targets: List<CartesianMarker.Target>,
  yValuesPerSeries: List<List<Double>>,
): Unit

// equals/hashCode now include horizontalLabelPosition (`:509`, `:518`) —
// but NOT horizontalLabelFormatter, restingLabel, restingLabelFormatter, labelGap,
// contentPadding or yLabelCallback. Deliberate (lambdas have no useful identity),
// worth knowing: two markers differing only in labelGap compare equal.

// rememberDefaultCartesianMarker — corresponding parameters
horizontalLabelPosition: Position.Horizontal? = null,
horizontalLabelFormatter: ((List<List<Double>>, Double) -> CharSequence?)? = null,
```

**Coupling to upstream internals.** The heaviest *drawing*-side coupling in the cluster:
- `CartesianDrawingContext.scroll` and `CartesianLayerDimensions.startPadding` — `chartStartX()`
  recomputes, inside the marker, the same expression `CartesianChart.draw` uses for its start-strip
  clip (`CartesianChart.kt:349: layerBounds.left + layerDimensions.startPadding - scroll`). Two
  copies of one rule in two files. If upstream changes how `startPadding` or `scroll` relate to
  `layerBounds`, this silently misplaces the crosshair rather than failing.
- `LineComponent.drawHorizontal` (upstream public) and the internal `Position.Vertical.inBounds`
  and `operator fun Position.Horizontal.unaryMinus()` helpers in `common/Position.kt` — both
  `internal`, so this code can only live inside the library module as written.
- **Draw-order assumption, and a real defect worth recording.** `CartesianChart.draw` at HEAD calls
  `marker.drawOverLayers(context, markerTargets)` **twice** per frame: once at `:394`, under the
  layer bitmap composite (added by `91e72bf64`, "put the data on top of what describes it"), and
  once at `:412`, after the fading edges and decorations (so the label is unclipped). Upstream at
  FORK_POINT calls it once. `DefaultCartesianMarker.drawOverLayers` does not know which pass it is
  in, so **the guideline, the crosshair, every indicator dot, the crosshair label and the marker
  label are all drawn twice**, and `yLabelCallback` fires twice per frame. For opaque paint this is
  invisible; for a translucent guideline it doubles the alpha, and for `yLabelCallback` it doubles
  the work. The two-pass split is intentional but the marker has no way to honour it. I would call
  this a latent bug in our fork that the migration should fix rather than port — the clean shape is
  two hooks (`drawOverLayers` / `drawLabels`), or a pass argument. `CartesianChart.kt` is owned by
  another cluster; flagging it here because the symptom lands in my file.

**Could it be an extension point, or live in meApp instead?** The *percentile* is already in meApp
(`BabyCrosshairLabel.kt`) — only the drawing is here, which is right. But the whole crosshair is
expressible by a `DefaultCartesianMarker` subclass **if** `crosshairAnchor` and `chartStartX` were
protected rather than private and the `Position` helpers were public. That would take ~90 lines out
of the hot file. The cost is that meApp would then own the anchor rule, and `BabyCrosshairLabel.kt`
already duplicates that rule by hand (`:36-41`, "match DefaultCartesianMarker's crosshair-Y pick by
taking the LAST series' value") — so moving it out would at least collapse the duplicate.
**Recommend: consider, but lower priority than B-1 and B-4.**

**Conflict risk: high.** It is ~110 lines inserted into the middle of `drawOverLayers` and a new
helper block in a file upstream also rewrote, and it reaches into `scroll`/`startPadding`/`internal`
position helpers whose shape upstream is free to change.

---

## B-8 — `yLabelCallback`

**What it does.** A callback invoked on every marker draw with the per-series _y_ values of the
marked targets, as `List<List<Double>>` (outer = target, inner = that target's points).

**Why we needed it.** The dashboard header above the graph shows the value of whatever is marked —
weight, body fat, BMI — and must update continuously as the finger scrubs. The marker is the only
thing that knows what is under the finger each frame. In v3 this was `onChartClick`; v4 had no
equivalent.

**Commits.** `196bb54af` feat(marker): Add yLabelCallback + isScrolling for chart header updates.
(The `isScrolling` half is on `VicoScrollState` — another cluster.)

**Files and hunks.**
- `DefaultCartesianMarker.kt:89` — the constructor parameter.
- `:122-125` — `yValuesPerSeries` computed once at the top of `drawOverLayers` and shared with the
  crosshair formatter:
  ```kotlin
  val yValuesPerSeries: List<List<Double>> = targets.map { target ->
    if (target is LineCartesianLayerMarkerTarget) target.points.map { it.entry.y }
    else emptyList()
  }
  ```
- `:172` — `yLabelCallback?.invoke(yValuesPerSeries)`.
- `:673, 688, 697` — the `remember` wiring through `rememberUpdatedState`.

**Public API added or changed.**

```kotlin
protected val yLabelCallback: ((List<List<Double>>) -> Unit)? = null,
// and the matching rememberDefaultCartesianMarker parameter:
yLabelCallback: ((List<List<Double>>) -> Unit)? = null,
```

**Coupling to upstream internals.** Reads `LineCartesianLayerMarkerTarget.Point.entry.y` — public.
Non-line targets contribute an empty list rather than being dropped, so **index alignment with
`targets` is part of the contract**; meApp's baby label depends on "the real data series is last"
(`BabyCrosshairLabel.kt:41, lastOrNull()`). The bigger coupling is temporal: it fires **during a
draw pass**, so the consumer's lambda runs on the draw thread, and (per B-7) **twice per frame**.
meApp's implementation is written defensively around this — `GraphMarker.kt:104` builds an O(1)
`Map<Long, PeriodSummary>` precisely because the original O(n) scan "ran every scrub frame".

**Could it be an extension point, or live in meApp instead?** It could become redundant. meApp
already receives `markerIndex` through the `ScrubMarkerController` callback, and
`rememberDefaultMarker`'s `yLabelCallback` body (`GraphMarker.kt:108-124`) mostly re-derives the
entry from `markerIndex` via its own map — the `fallbackValues` are used only when the marker sits
*between* data points, i.e. only for interpolated targets (B-10). If interpolated _y_ values were
instead surfaced on the controller callback or on state, `yLabelCallback` could go entirely.
**Recommend: check with meApp whether the fallback path is still exercised; if it is not, delete
this parameter.** That would be a real fork reduction in the hottest file.

**Conflict risk: medium.** Three small additive hunks, but two of them are in the middle of
`drawOverLayers`, which is where B-7 also lives and where upstream's own changes land.

---

## B-9 — `contentPadding` and the guideline extension

**What it does.** Adds `contentPadding: Insets` and extends the vertical guideline past the plot
edge to reach a label that has been pushed outside. **At HEAD it affects only the guideline's
extents for the point-anchored label positions** (`AbovePoint`/`AroundPoint` top,
`BelowPoint` bottom).

**Why we needed it — and why it is now vestigial.** It was added for v3 parity, to offset the
marker label from `layerBounds` without changing the chart's margins. `08d194bbd` then observed
that `contentPadding` and `labelGap` "described the same gap and had to be kept equal by hand,
which is the kind of pair that drifts — and had: a marker setting one and not the other drew its
label flush against the plot while holding room above it." Top/Bottom placement moved to `labelGap`
and `contentPadding` was left governing only the positions that follow the marked point, which have
no shared line.

**Commits.** `24858daf2` feat: Add contentPadding to DefaultCartesianMarker (v3 parity); narrowed
by `08d194bbd`.

**Files and hunks.**
- `DefaultCartesianMarker.kt:87` — `protected val contentPadding: Insets = Insets.Zero,`.
- `:441` — `topPointY - tickSize.pixels - contentPadding.top.pixels` (AbovePoint/AroundPoint).
- `:456` — `bottomPointY + tickSize.pixels + contentPadding.bottom.pixels` (BelowPoint).
- `:483-484` — the comment recording that it is deliberately excluded from `updateLayerMargins`.
- `:677, 688, 696` — the `remember` wiring (it **is** a `remember` key).

Those are the only three live uses. It is referenced nowhere in `drawLabel` or `drawResting`.

**Public API added or changed.**

```kotlin
protected val contentPadding: Insets = Insets.Zero,
// and the matching rememberDefaultCartesianMarker parameter:
contentPadding: Insets = Insets.Zero,
```

**Coupling to upstream internals.** Only to `DefaultCartesianMarker`'s own `drawGuideline`.

**Could it be an extension point, or live in meApp instead?** **It should probably be deleted.**
No meApp call site passes it (I grepped the whole of `meApp-bug/Android/app/src`: every
`contentPadding =` hit is an unrelated Compose `PaddingValues`). Its only effect is on two label
positions meApp does not use — `GraphMarker.kt:141` uses `LabelPosition.Top` and
`rememberRangeOnlyMarker` uses the default. Removing it drops a constructor parameter, a `remember`
key and ~10 lines from the hottest file. Flagging for the decision owner rather than deciding.

**Conflict risk: medium.** Small, but it sits inside `drawGuideline`, which we rewrote wholesale.
Low *consequence* if lost — nothing observable depends on it.

**Doc disagreement.** FEATURE-8 §"Marker contentPadding (v3 Parity)" is **stale in two places**: its
table claims `Top` shifts the label by `contentPadding.bottom` and `Bottom` by `contentPadding.top`
(both are now `labelGap`), and §"Fixed Margin Height" claims `updateLayerMargins` uses a fixed 24 dp
(`b271cb4ba` replaced that with a measured height — see B-4).

---

## B-10 — `isInterpolated` on a marked point

**What it does.** Adds a fourth property to `LineCartesianLayerMarkerTarget.Point`. When `true`,
`DefaultCartesianMarker` skips the indicator dot for that point while still drawing the guideline,
the crosshair and the label.

**Why we needed it.** The scrub can land between data points — meApp snaps the marker to the
nearest *visible axis label*, not to the nearest entry, so for a week view with sparse readings the
marker frequently sits where there is no reading. `CartesianChart.synthesizeInterpolatedTargets`
manufactures a target with a monotone-interpolated _y_ so the guideline and label still work; but
painting a dot there would assert a measurement that does not exist. The Health app does the same:
the rule and the value follow the finger, the dot only appears on real samples.

**Commits.** `2c00808b6` feat(marker): Interpolated markers without indicator + accept empty target
taps.

**Files and hunks.**
- `LineCartesianLayerMarkerTarget.kt:35-41` (the `data class Point`), new property at `:39-40`:
  ```kotlin
  /** True when this point is interpolated (no real data point). Indicator dot is not drawn. */
  val isInterpolated: Boolean = false,
  ```
- `DefaultCartesianMarker.kt:158-164`:
  ```kotlin
  target.points.forEach { point ->
    if (!point.isInterpolated) {
      drawIndicator(target.canvasX, point.canvasY, point.color, halfIndicatorSize)
    }
  }
  ```

**Public API added or changed.**

```kotlin
public data class Point(
  val entry: LineCartesianLayerModel.Entry,
  val canvasY: Float,
  val color: Color,
  val isInterpolated: Boolean = false,   // added
)
```

Because `Point` is a **data class**, this changes the synthesized `copy`, `componentN`, `equals`,
`hashCode` and `toString`. Source-compatible (the parameter has a default); **binary-incompatible**
for `copy`. If upstream has added its own property to `Point` at TARGET, the parameter *order*
becomes a merge decision: appending ours last is the safe resolution.

**Coupling to upstream internals.** `MutableLineCartesianLayerMarkerTarget` (internal) and
`CartesianChart.synthesizeInterpolatedTargets` (fork-added, host cluster) are the only producers of
`isInterpolated = true`; they also set `color = Transparent` for such points, so even if the
indicator guard were lost the dot would be invisible — a useful belt-and-braces to verify rather
than rely on.

**Could it be an extension point, or live in meApp instead?** No, and it should not. It is two
lines, it is a property of the data vico itself synthesizes, and it has nothing meApp-specific in
it. This is the cleanest adaptation in the cluster.

**Conflict risk: low.** `LineCartesianLayerMarkerTarget.kt` is on BASELINE's "upstream never
touched" list. The `DefaultCartesianMarker` guard is a three-line wrap in a `forEach` that upstream
may or may not have moved.

---

## B-11 — `TextComponent.getBounds` measured the literal word "null"

**What it does.** One-word fix: in the `pad` branch of `getBounds`, append `measuredText` (the
already-null-coalesced local) rather than `text` (the nullable parameter).

**Why we needed it.** `buildAnnotatedString { append(text) }` with a null `CharSequence` writes the
four characters `null`. So **measuring with no text reserved four characters of width**. Every
`Size.Auto` axis sizes its gutter through this path, so an axis with no title or no labels was
given room for a word it never draws. It also lands directly on B-4: `updateLayerMargins` calls
`label.getHeight(this)` **with no text** precisely so the reserved height follows the type scale
and not the content — and before this fix that measurement was of a line containing "null".

**Commits.** `2e2596784` MOB-3494: stop measuring the word "null" when there is no text.

**Files and hunks.**
- `common/component/TextComponent.kt:283-292` (HEAD `getBounds`):
  ```kotlin
  var measuredText = text ?: ""
  if (pad) {
    measuredText = buildAnnotatedString {
      append(measuredText)   // was: append(text)
      repeat((lineCount - measuredText.lines().size).coerceAtLeast(0)) { append('\n') }
    }
  }
  ```

**Public API added or changed.** None. Behaviour-only.

**Coupling to upstream internals.** None — it is a correctness fix inside one upstream function.
`getWidth` and `getHeight` both delegate to `getBounds`, so all three are fixed by this one line.

**Could it be an extension point, or live in meApp instead?** No. **Send it upstream** — it is a
plain bug in upstream code, unrelated to anything meApp wants, and a one-line PR.

**Conflict risk: high collision, trivial content.** Upstream rewrote this file 59+/61- while we
changed 4+/1-. There is every chance the resolution takes upstream's version wholesale and our
word silently disappears — and nothing will fail to compile and no test will go red; axis gutters
will just be four characters too wide again and marker label lines slightly too tall. **This is the
single most losable change in my cluster.** The migration must re-check, by hand, that
`DefaultCartesianMarker` line ~283 of the merged `TextComponent.kt` appends the coalesced local.
Verify first whether v3.3.1 already fixed it independently (another agent's call, not mine).

---

## B-12 — Caller-owned marker clearing *(host cluster owns the file; recorded here for the contract)*

**What it does.** Re-keys the host's `initialMarkerX` effect on the **value** instead of on `Unit`,
so a caller clearing its marker reaches the chart — and makes the clear also set
`scrubController.hasActiveMarker = false`. Only the clear is forced; a non-null value is still
restored into an empty saveable rather than assigned over whatever is there.

**Why we needed it.** meApp's `segmentState.markerIndex` is the source of truth for the dashboard
header. When the marked entry was deleted, or scrolled out of the window, meApp cleared it — and
the chart went on drawing the dot and its guideline at an _x_ nobody held, while the header
correctly reverted to the window average. Nulling `markerX` alone was not enough either, because
the controller still believed a marker was up and `shouldShowMarker` would keep returning
`hasActiveMarker`. The asymmetry (force the clear, do not force the set) exists because during a
drag the host's local `markerX` runs ahead of the value the caller has echoed back, and assigning
would drag the marker backwards under the finger.

**Commits.** `bd58edc86` MOB-3494: let the caller clear a marker it no longer holds. (Predecessor:
`c65d25ef4` MA-3287: `initialMarkerX` — restore/clear marker on recomposition.)

**Files and hunks.** `CartesianChartHost.kt:255-288` — the `LaunchedEffect(initialMarkerX)` and the
`snapshotFlow { scrubController.hasActiveMarker }` collector. No file of mine changes.

**Public API added or changed.** `initialMarkerX: Double? = null` on the host composable (added by
`c65d25ef4`); `bd58edc86` changed only its semantics — from "applied once" to "a null clears".

**Coupling to upstream internals.** Writes `markerX`, `markerSeriesIndex` and
`lastAcceptedInteraction` — all `rememberSaveable` state private to the host — and writes the
`internal var hasActiveMarker` on our controller. Bidirectional: the host writes
`hasActiveMarker` here and *observes* it at `:279-287` to clear `markerX` when the gesture layer
dismisses. That two-way binding between a saveable and a plain `mutableStateOf` on a controller is
the fragile part of B-1's contract, and the comment at `:272-274` records that an earlier version
of this sync was circular.

**Could it be an extension point, or live in meApp instead?** It is the host's job and cannot move.
Worth noting that the whole `initialMarkerX` / `hasActiveMarker` dance exists because marker
position is *co-owned* by the library and the caller. A cleaner design — the one to reach for if
B-1's refactor happens — is a hoisted `markerX: Double?` + `onMarkerXChange: (Double?) -> Unit`
pair, which is the ordinary Compose state-hoisting shape and would delete both effects and the
`internal var`.

**Conflict risk:** not assessed here — `CartesianChartHost.kt` belongs to another dossier. Recorded
so that whoever ports the host knows this is load-bearing for the scrub contract, not incidental.

---

## Where the docs and the code disagree (consolidated)

| Doc | Claim | Reality at HEAD |
|---|---|---|
| FEATURE-2 gesture table; `ScrubMarkerController.kt:33` KDoc | Tap **toggles** the marker | Tap always shows (`b5343399c`); the toggle now lives in meApp `GraphView.kt:531-534`, enabled by B-2 |
| FEATURE-2 §Scroll-Dismiss Flow, step 4 | `consumedXDeltas` effect dismisses the marker | Removed in `8052261ae` — false-dismissed on snap-settle; dismissal is via `snapshotFlow { hasActiveMarker }` |
| `ScrubMarkerController.kt:38` KDoc | `isScrubbing` is "read by Modifier.kt to disable scrollable" | `Modifier.kt` only writes it; the scrollable is `enabled = true` always and freezing is via `isScrollFrozen`. FEATURE-2's own §Scroll Disable section is correct, so the doc contradicts the KDoc |
| FEATURE-2 and FEATURE-8 usage snippets | `onMarkerIndexChanged = { clickX, targets -> … }` | Three parameters since `b7d7e0583` (B-2) |
| FEATURE-8 §contentPadding table | `Top` shifts label by `contentPadding.bottom`, `Bottom` by `contentPadding.top` | Both are `labelGap` since `08d194bbd`; `contentPadding` now affects only guideline extents for the point-anchored positions |
| FEATURE-8 §Fixed Margin Height | `updateLayerMargins` uses a fixed 24 dp | Measured: `labelGap + max(label, restingLabel).getHeight() + tickSize`, reserved un-halved via `ensureVerticalValuesAtLeast` (`b271cb4ba`) |
| FEATURE-2 §Architecture file table | lists four files | omits `CartesianMarkerController.kt` (B-3), `CartesianMarker.kt` (B-4, B-5), `LineCartesianLayerMarkerTarget.kt` (B-10), `TextComponent.kt` (B-11) — all later work |
| FEATURE-6 | "MarkerDecoration" | despite the name this is a `VerticalAxis` feature (goal-weight pill), not part of this cluster. Its only contact with me is draw-order: `axisManager.drawMarkerDecorations` runs at `CartesianChart.kt:409`, between the two marker `drawOverLayers` passes |

No FEATURE doc exists for B-2, B-3, B-4, B-5, B-6, B-11 or B-12, or for the `ab3fe3bfc` half of
B-7. That is 8 of the 12 adaptations here reconstructed from the diff and the commit messages.
