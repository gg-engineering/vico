# S5-B — The merged plan of work

Consolidates the four Stage-3 disposition dossiers (`S3-A` scroll/range, `S3-B` marker/scrub/gesture,
`S3-C` axis/layer/composition, `S3-D` build/consumer/harness) into one ordered plan, plus `S4-A`
(parity harness), `BASELINE.md` and `CONTRADICTIONS.md`.

**This document sequences verdicts; it does not re-decide them.** Where two dossiers cannot both be
followed, both positions are stated with what would settle it, and any adjudication I make is marked
**[S5-B call]**. Assumptions are marked **[ASSUMPTION]**.

**Framing that changes how the list reads.** The port is a hand-applied per-file
`git diff e67e49520..HEAD -- <file>` onto a v3.3.1 base (BASELINE process hazard, S2-A §1.1). In that
direction, **DROP and DELETE are "do not apply the hunk"** — they cost bookkeeping, not code. Almost
all the effort sits in RE-APPLY, RE-DERIVE, MOVE and REPLACE. The danger with a DELETE is not doing
it, it is *partially* doing it: applying one half of a coupled pair (see §1.2).

Vocabulary and ids are the dossiers': `S1A-nn` (scroll), `A-n`/`L-n` (axis/layer), `B-n`/`M-n`/`D-n`
(marker/gesture), `C-n`/`H-n` (composition/host), `B-n` build items in S3-D (disambiguated as
`bld-Bn` below where it collides with the marker cluster's `B-n`).

---

# 1. The consolidated dependency graph

## 1.1 Hard ordering edges

Read `X → Y` as "X must land before Y". Edges marked **†** are cross-cluster. Edges marked **‡** are
ones **no dossier states** — found while merging.

### Gates over everything

```
T0 goldens (S3-D Part 3, S4-A) → every item below                    †
Paparazzi spike (S3-D app. 6)  → T0 goldens
formatting normalisation (L15 / S2-A §3.2 / S1-C / S1-D) → any hand-merge of a hot file
migration clone + build rebase (S3-D Part 1) → any library edit
composite-build substitution works (S3-D app. 7) → every meApp-side MOVE
```

### Within the scroll/range cluster (S3-A)

```
S1A-06 (public conversions) → S1A-05a, S1A-09a, H-3
S1A-01 (+ widen to public)  → S1A-02 → H-1 → S1A-03
S1A-01, S1A-06              → H-3
S1A-12, H-1                 → H-2
S1A-08a DELETE ‖ S1A-08c RE-APPLY   (same diff region, opposite verdicts — one edit)
H-8/M-3 flingBehavior seam  → S1A-05a                                  †
S1-D MonotoneInterpolator out → S1A-09b                                †
```

### Within the marker/gesture cluster (S3-B)

```
B-11 (TextComponent one-word fix) → everything that measures a label, incl. B-4b
D-2 (sort + cache invalidation)   → all gesture work (M-1, B-1)
D-1 decision (drawLabels vs collapse) → B-4b, B-7, B-8
B-4a (drawResting) → B-4b, B-6
B-4c ‖ A12                       (one commit)                          †
B-3a + B-3b + M-4 → M-1 → B-1 + B-2
B-4b + B-7 + B-8 landing as MOVE → B-6 REPLACE (else B-6 reverts to RE-APPLY)
H-4 (host settle feed) → B-5                                           †
B-1 → the meApp GraphView.kt:268-269 argument deletion
```

### Within the axis/layer cluster (S3-C)

```
L15 formatting → everything on LineCartesianLayer.kt
A3b → A4b
A2b ‖ A3c            (re-derived together)
A3a ‖ A4a            (one meApp edit session on HorizontalItemPlacer.kt)
L2b → L1 → L2a
L5 → L3 ;  L4 ← MutableCartesianChartRanges.tryUpdateX + NeutralYRange (= S1A-13)   †
L8 ‖ L9              (L8 is what makes L9 safe)
L9 → C-2 → C-3 → A8 → A5 ;  C-2 → C-1's re-derived inset term
C-4 → C-5 → A6 ;  A6′ and drawMarkerDecorations die with A6
A7b → H-7's first prepare pass                                         †  (contested — see §3.1)
L6 ‖ CartesianChart.kt:576
```

### Within build/consumer (S3-D)

```
bld-B9 (drop the Kotlin pin) ‖ alias(libs.plugins.composeMultiplatform)   (one change)
bld-B1 ‖ bld-B12  (take upstream's vico/build.gradle.kts whole, change one line)
clone → build rebase → first green :vico:compose:assemble → port
port → publish 5.0.0 → meApp version bump → T5 re-render
every MOVE → its meApp side, in the same change                        †
```

### Cross-cluster edges the dossiers state

| Edge | Source |
|---|---|
| `A12` (margin halving, S3-C) **‖** `B-4c` (S3-B) — one commit, no intermediate state | S3-B §2 B-4c, S3-C (c) |
| `A7b` (S3-C) removes the machinery `H-7`'s first prepare pass uses | S3-C A7b |
| `C-2` after `L9`, so upstream's *add* to `maxPointSize.half` and our former *max* agree | S3-C C-2, S3-A §2(a) caveat 1 |
| `H-3`, `A6`, `A1`, `C-5` all contend for `Decoration` passes in `CartesianChart.draw()` | S3-A §2(b), S3-C A1/A6/C-5 |
| `S1A-05a` needs `S1A-06` public **and** the `flingBehavior` seam kept by `H-8`/`M-3` | S3-A §2(c) |
| `L4` needs `tryUpdateX` + `NeutralYRange` (= `S1A-13`) | S3-C L4 |
| `L2a` (`InterpolationType`) is read only by `VicoScrollState.getInterpolatedYValues` (= `S1A-09b`) | S3-C L2a |
| `S1A-04` (`WindowSettleSignal`) → `L3` (`yTransform`) | S3-A S1A-04 |
| `S1A-12` (`getTargetYRange`) → `L3`, and its consumer is `LineCartesianLayer.kt:843` | S3-A S1A-12 |
| `S1A-01`'s drawing-model cache skip correctness is owned by `L3`/`L5` | S3-A §4 |
| `B-10` (`isInterpolated`) is consumed by `C-7`'s synthesis | S3-B B-10 |
| every MOVE/DELETE of a fork API is a meApp compile break (22 APIs, S3-D Part 2) | C6 |

### Cross-cluster edges **no dossier states** ‡

1. **`S1A-09b` must precede `L2a`.** S3-C moves `InterpolationType` out of the library in its Phase 2;
   S3-A moves `getInterpolatedYValues` — the only reader of that enum — out in its Phase 4. Executed
   in dossier order the library loses the enum while still holding its reader. Loud (compile), so it
   will be caught, but it inverts two phases.
2. **`A6` (goal pill) depends on `H-2`.** S3-C's case for the pill is that `ranges.getYRange(position)`
   keeps it "in lockstep with the axis under our animated-range machinery for free". That machinery
   *is* `H-2`'s `AnimatedYCartesianChartRanges`. If A6 lands before H-2, the pill is in lockstep with a
   static range and the lockstep claim is untested.
3. **`C-7` depends on `L2b`, `L6` and `B-10` — and `C-7` has no verdict in any dossier** (§3.3).
   `synthesizeInterpolatedTargets` calls `getYAtXFromEntries`, which L2b replaces with a defaulted
   `Interpolator.getYAt`; it skips layers by `markerTargetsEnabled` (L6); and it emits
   `isInterpolated = true` (B-10).
4. **`H-4`'s `MARKER_SETTLE_MS = 100` is tied to `ScrollAwareRangeProvider.debounceMs`** (S1-E H-4:
   "chosen to equal … so the text beside the chart and the axis it describes come to rest together").
   Re-deriving either side independently desynchronises the caption from the axis. Silent.
5. **`L5`'s re-derivation changes `ScrollAwareRangeProvider`'s contract.** S3-C proposes expressing the
   drawing-model bypass as `CartesianLayerRangeProvider.isStable`, answered `false` by meApp's
   delegating wrapper. That is a new member on an interface the scroll cluster owns, and it **removes
   `alwaysUseLiveRange`** from the public API — a meApp break at `PrimaryLayer.kt:73,98` that is not in
   S3-D's four and not in its 22-API table as a *removal*.
6. **`L3` Option A removes `yTransform` from the public API** and replaces it with a
   `CartesianLayerYMapper` the consumer implements (~120 meApp lines). meApp breaks at
   `PrimaryLayer.kt:72,97` and `ProductChart.kt:324`. Likewise `L4`'s `contributesYRange` is a new
   parameter meApp must pass where it previously got the behaviour implicitly from `yTransform != null`.
7. **`D-2`'s new invalidation point sits inside `C-7`'s region.** S3-B puts the explicit cache
   invalidation "where `_markerTargets.clear()` runs (HEAD :238)"; C-7 owns `:325`, `:414`, `:481`,
   `:544-602` in the same file. One owner, or two people editing `CartesianChart.kt` blind.
8. **`S1A-09a` depends on `H-9`, which has no verdict** (§3.3). Losing the `scrollState.drawingContext`
   assignment leaves the retained context null and silently falls back to the even-walk label strategy.
9. **`B-4b`'s measured reservation sits downstream of three clusters**: `B-11` (the measurement is
   wrong until the `null`-append fix lands), `A12` (the reservation is halved until the halving goes),
   and `D-1` (the method split *is* the double-draw fix).
10. **The golden harness is downstream of every MOVE.** Each MOVE changes the golden test's call site
    in meApp while being required *not* to change the image. That is the property that makes a MOVE
    provable — and it means the harness files are edited in every phase that contains a MOVE.

## 1.2 Must-land-together sets (no safe intermediate state)

| # | Set | Why |
|---|---|---|
| 1 | `A12` + `B-4c` + the marker's two `ensureValuesAtLeast` call sites | either half alone is a silent layout bug (S3-B §2 B-4c) |
| 2 | `S1A-08a` (not applied) + `S1A-08c` (applied) | adjacent lines, opposite verdicts |
| 3 | `C-2` + `C-3` + `A8` + `A5` (+ `C-1`'s re-derived term) | "one feature in three files; re-applying the pieces independently yields a chart that compiles and is subtly wrong" |
| 4 | `B-1` + `B-2` + `H-5` + `H-6` + `B-12` | the controller, the callback shape and the host's marker plumbing are one contract |
| 5 | `L8` + `L9` | without the clip, removing the padding is what let a dot spill outside the plot |
| 6 | `L6` + `CartesianChart.kt:576` | two enforcement points for one invariant |
| 7 | `S1A-13`'s `getValue(null)` substitution at **both** `:41` **and** `:101` | the existing test pins only the first |
| 8 | `A2b` + `A3c` | A2b re-breaks the last label's `maxWidth` unless A3c is re-derived with it |
| 9 | `A3a` + `A4a` | one meApp edit session on the same file |
| 10 | `B-4a` + `B-5` + `B-10` + `drawLabels` | one coherent interface change / one upstream PR |
| 11 | `C-5` + `A6` + deleting `AxisManager.drawMarkerDecorations` | the moved pass is what lets the pill leave |
| 12 | `S1A-01` + widening its members to `public` | H-3 and H-2's effect-move are blocked without it |
| 13 | `H-3` + the three-scalar dedup + the `xSpacing > 0f` guard | emitting unconditionally re-triggers the settle chain (`6ef5fdcc7`, `57ce985ea`) |
| 14 | every MOVE + its meApp side | a MOVE that lands alone is a broken consumer build (C6) |

## 1.3 File contention map — who edits what, and how often

The port's real coordination cost is here, not in the dependency list.

| File | Upstream churn in range | Contending items | Phases that touch it |
|---|---|---|---|
| `CartesianChart.kt` | +228/−18 | `D-2`, `C-1`, `C-2`, `C-3`, `C-4`, `C-5`, `C-6`, `C-7`, `L6`(:576), `L2b`(:582), `B-4a` dispatch, `D-1` `drawLabels` | P4, P10, P11, P12, P13 — **five passes** |
| `CartesianChartHost.kt` | +196/−94 (ours +454/−8) | `H-1`…`H-10`, `A7b`'s four call sites, `B-12`, `L14b`'s dangling `CHART_HEIGHT` import | P4, P7, P13, P14 — **four passes** |
| `LineCartesianLayer.kt` | +130/−35 (ours +320/−54) | `L1`, `L3`, `L4`, `L5`, `L6`, `L7`, `L8`, `L9`, `L15` | P8, P10 |
| `HorizontalAxis.kt` | +277/−77 | `A1`, `A2a`, `A2b`, `A2c`, `A3a`, `A3b`, `A3c`, `A4b`, `A7a` | P4, P8, P9 |
| `VerticalAxis.kt` | +175/−29 | `A6`, `A6′`, `A7a`, `A7b`, `A9`, `A10`, `A11` | P4, P8, P9, P12 |
| `VicoScrollState.kt` | +182/−41 | `S1A-06`, `S1A-07`, `S1A-08a/b/c`, `S1A-09a/b`, `L2b`(:164), `H-9`'s target | P6, P8, P16 |
| `DefaultCartesianMarker.kt` | +39/−25 (ours +288/−19) | `B-4b`, `B-6`, `B-7`, `B-8`, `B-9`, `B-4c` call sites | P13 |
| `Modifier.kt` | +121/−25 (ours +234/−27) | `M-1`…`M-4`, `B-3a` guard | P14 |

**[S5-B call]** Assign a single owner per file for the duration, not per cluster. `CartesianChart.kt`
and `CartesianChartHost.kt` are each edited by three different dossiers' items; with four clusters and
one developer this is a sequencing problem, but if the work is ever parallelised it is the first thing
that breaks.

---

# 2. The merged order of work

Constraints honoured: T0 goldens gate everything; formatting before any merge; hand-applied per-file
diff, never rebase or cherry-pick; reductions after parity is proven; every phase ends green.

**A standing caveat about "green".** Phases P4–P6 leave a library that builds and renders but whose
flagship behaviour is dead, because the host half is not in yet. That is why the host core is pulled
forward to **P7** rather than left where S3-A's cluster-local order implies — see the departure note
there. From P7 onward every golden is meaningful and a regression is attributable to the phase that
introduced it.

---

## P0 — Settle the blocking questions. No library code.

**Lands:** six decisions and three spikes, written into this file as answers.

1. **Paparazzi spike** (S3-D §3.2, app. 6) — one test, three pass criteria. Blocks T0, which blocks
   everything.
2. **Composite-build substitution at v3.3.1's module layout** (S3-D app. 7) — `dependencyInsight` in
   the composite build before any library code moves. Blocks the whole iteration loop.
3. **Kotlin context parameters at 2.4.10** (S3-D app. 1) — one compile of stock v3.3.1. Settle before
   porting, or a toolchain failure is indistinguishable from a port failure.
4. **`H-8`/`M-3`: is the `flingBehavior` seam kept?** (S3-A Phase 0 #2, S3-B U-3, C2's open half.)
   Blocks `S1A-05a` and `M-3`. If the seam is dropped, snapping dies entirely.
5. **`C-2` + `L9`: the start-gap arithmetic** (S3-A §2(a) caveat 1). A product look at a few-dp
   difference. Blocks the whole start-of-chart unit (P11) and `S1A-11`'s visual check.
6. **`D-1`: add `CartesianMarker.drawLabels` or collapse to upstream's single late call?** (S3-B §2
   D-1.) Blocks the marker interface set and the meApp subclass; the subclass's method split *is* the fix.
7. **`A7b` vs `S1A-08c`/`H-7`** (§3.1). Blocks P14.
8. **`L3`: Option A confirmed** (S3-C recommends Option A; nothing contradicts). Record it so P10 is
   not re-opened.
9. **S3-D app. 4** — one grep: does the fork's `yTransform` path touch the now-throwing
   `CartesianLayerDrawingModel.transform`? Minutes, and it prevents an `UnsupportedOperationException`
   in P10 being read as a port bug.

**Why here:** every one of these changes *what* is built, not how fast. Items 1–3 can each invalidate
the plan's shape.
**Proof:** each question has a written answer and, for 1–3, a command output pasted beside it.

---

## P1 — T0: the golden baseline, and the five checks that can only be run on 4.0.46.

**Lands:** a meApp PR against the current `vico = "4.0.46"`, resolved from GPR with `vico.local` unset.

- `ChartFixtures.kt` (fixed timestamps, locale, time zone — no `Calendar.getInstance()`),
  `ProductChartGoldenTest.kt`, `ChartEdgeStateGoldenTest.kt`.
- The 36-image set: 3 configs × 4 segments × 2 themes, plus the 12 edge/state cases, each pinned to a
  named silent-change risk (S3-D §3.4).
- **The five 4.0.46-only checks**, which become unanswerable once the branch tip moves:
  1. `acceptsGestures` smoke gate — tap the snapshot card; the full graph must open; press-and-hold
     must leave the caption put (S3-B §3).
  2. `./gradlew :app:lintDebug` with both `@SuppressLint("RestrictedApi")` removed (C7, S3-D app. 5).
  3. The rotation check for `L12b` — does the restored model sweep in? (S3-C (f).)
  4. `yLabelCallback` instrumentation — is the `fallbackValues` path ever the value used? (S3-B U-1.)
  5. Segment A → B → A — record today's behaviour as the comparison for `S1A-08b` (S3-A U2).

**Why here and not later:** "once `4.0.46` is off the branch tip, the baseline is gone" (S3-D §3.0).
A golden captured after any migration work is a record of a half-migrated state. Checks 1–5 need a
running un-migrated app.
**Proof:** 36 committed PNGs; two consecutive `recordPaparazziDebug` runs byte-identical;
`verifyPaparazziDebug` green on CI; five written check results.

---

## P2 — The migration clone and the build rebase.

**Lands:** `/Users/selvakumar/Projects/vico-migration`, branched from `v3.3.1`; S3-D Part 1 §1.2
steps 1–8 (take upstream's `settings.gradle.kts`, catalogue, wrapper, `gradle.properties`, `buildSrc`,
`sample/**` verbatim; `group = com.dmdbrands.lib`; `maven-publish` for `publishing-convention`;
re-add the GPR block; delete `gpr-publishing-convention.gradle.kts`; drop the Kotlin pin with
`alias(libs.plugins.composeMultiplatform)`; `VICO = "5.0.0"` + `UPSTREAM_BASE = "3.3.1"`;
re-point `origin` to `gg-engineering`). Delete `DmdBrandsTestChart.kt` and the four sample edits.
The guarded, property-switched `includeBuild` in meApp's `settings.gradle.kts`.

**Why here:** nothing can be ported until there is somewhere to port it to, and the shared checkout
is read-only. Doing it before any adaptation means the first compile failure is a build failure, not
an ambiguous one.
**Proof:** `./gradlew :vico:compose:assemble` green on **stock** v3.3.1 + fork build config;
`dependencyInsight` reports `project :vico:compose`; `:vico:compose:tasks --all | grep -i publish`
recorded.

---

## P3 — Formatting normalisation.

**Lands:** upstream's ktfmt 0.64 formatting applied to our HEAD side of every file we will hand-merge
(14 files in `vico/compose`, S2-A §3.2), and the star-import expansion on `LineCartesianLayer.kt`
reverted (`L15`). `L16` confirmed as nothing.

**Why here:** "roughly half our 320 added lines on that file are whitespace and imports, and every one
will conflict". Everything after this is roughly twice as fast. **Why not later:** a merge attempted
first has to be redone.
**Position to note:** S3-B offers `diff -w` as an alternative to normalising. **[S5-B call]**
normalise — the brief's constraint says normalisation precedes any merge attempt, and `-w` does not
help with the star-import churn.
**Proof:** `git diff e67e49520..HEAD -- <file> -w` and without `-w` produce the same hunk count on the
two hottest files.

---

## P4 — The no-ops, the one-liners, and the two defects.

**Lands** (mostly by *not applying* hunks):

- Not applied: `A12` (margin halving) + `B-4c` (`ensureVerticalValuesAtLeast`) **as one decision**;
  `A7b`; `A3b`; `A4a′`; `A6′`; `L10`; `L12b`; `L15`; `B-9`; `S1A-05b` (`isSnapping`);
  `S1A-08a`; `H-10`.
- Applied: **`B-11`** (the `TextComponent` `append(text)` → `append(measuredText)` fix) **first and
  alone, in its own commit**, with the upstream PR opened the same day; **`D-2`** (restore upstream's
  three sort lines verbatim; replace the size-keyed cache with explicit invalidation at
  `_markerTargets.clear()`); **`L13`** (`InlineUpdateContext` as a second element on upstream's
  `PreviewContextKey`; `runTransaction` uses `getDispatcher()`).
- Doc deletions that travel with the code: the `FEATURE-4` suppression paragraph (`S1A-05b`), the
  `FEATURE-8` `contentPadding` section (`B-9`), the stale `TreeMap` comment (`D-2`), the stale
  `MonotoneInterpolator.getYAtX` comment at `CartesianChart.kt:548`.

**Why here:** these shrink the conflict surface before any hot-file merge. `B-11` is "the most losable
change in this cluster" — a take-theirs resolution reverts it silently, so it goes in alone where it
can be verified by hand. `D-2` goes before any gesture work "or you will debug the wrong layer".
**Why not later:** every later phase that measures a label is wrong until `B-11` lands, and nothing
will tell you.
**Files that leave the fork here:** `CartesianLayerMargins.kt`, `CartesianDrawingContext.kt`,
`CartesianChartModel.kt`, `ListItemPlacer.kt` (in P8), `Defaults.kt` (in P8).
**Proof:** `git diff v3.3.1..<branch>` shows those files absent; the `pad` branch of `TextComponent`
re-read by hand after the merge; a two-layer chart's marker returns both series.

---

## P5 — The clean-graft contracts.

**Lands:** `S1A-12` (`getTargetYRange`), `S1A-13` (`tryUpdateX` + `NeutralYRange` **at both `:41` and
`:101`**), `S1A-04` (`WindowSettleSignal`), `S1A-11` (fading edges over the layer's padding),
`B-10` (`isInterpolated`), `L11` (`isReady`), `L12a` (`runTransaction(animate =)`), `L7`.

**Why here:** every one is in BASELINE's "upstream never touched" list or on a byte-identical file;
three of them fail to compile if dropped. They are the data-layer contracts the rest of the plan
reads, and landing them first buys an early green build on v3.3.1.
**Why not later:** `L4` needs `tryUpdateX` + `NeutralYRange`; `L3` needs `S1A-04` and `S1A-12`;
`C-7` needs `B-10`.
**Proof:** build green; `MutableCartesianChartRangesTest` green **and** a new case covering the
`toImmutable()` copy (the existing test pins only one of the two substitution sites).

---

## P6 — `VicoScrollState`.

**Lands:** `S1A-06` RE-APPLY with `xToScrollValue`, `xToScrollValueBehindStartInset` and
`scrollValueToX` widened `internal` → `public`; `S1A-07` (the scrub-freeze re-wrap);
`S1A-08c` (`initialScrollHandled` → `internal`) in the **same edit** as not-applying `S1A-08a`.

**Why here:** `S1A-06` unblocks `S1A-05a`, `S1A-09a`, `S1A-09b` and `H-3`; it is the file everything
else reads. **Why not earlier:** it is a hot file (+182/−41 upstream) and wants the normalised diff
from P3.
**Watch:** `scrollEnabled` became `mutableStateOf` at v3.3.1 (`1fe9daef9`), so the `!scrollEnabled`
freeze arm is now reactive where it previously could not change.
**Proof:** build green; the three conversions resolve from meApp in the composite build.

---

## P7 — The host core. The fork's flagship goes live.

**Lands:** `S1A-01` (`ScrollAwareRangeProvider`, **with its members widened to `public` in the same
commit**), `S1A-02` (`Window`/`WindowKey`), `H-1` (**alone**), `S1A-03` (immediately after H-1),
`H-2` (wrapper + effect, ported in place), `H-9`.

**Why here — this is a departure from both S3-A's and S3-C's cluster-local orders. [S5-B call]**
S3-A places the host core third within its own cluster; S3-C's phases imply the axis and layer work
comes first. Merged, the host has to be early for a reason neither dossier could see from inside its
cluster: **until `H-1`/`H-2` are in, the scroll-aware Y range is dead, and every golden taken between
P4 and P7 is a golden of a chart that is missing its headline behaviour.** Landing the host here makes
every subsequent phase individually goldenable and makes a regression attributable to the phase that
introduced it. The cost is that `CartesianChartHost.kt` — the most-churned file in the fork — is
hand-merged here and touched again in P13 and P14; the regions are disjoint (range machinery vs marker
plumbing vs prepare/fling), but the file wants one owner.

**Carry verbatim, each a previously-shipped silent defect:** the push-time domain (`25f0fb793`), the
frame-late opening range (`a98cf86de`), layer↔model pairing by position not `indexOf` (`e6c766cb8`),
animatables initialised *after* cache build, dedup by provider identity, `NaN` as the uninitialised
sentinel, and `provider.settleEpoch++` **before** the "range did not move" early return.
**Proof:** the full golden set re-rendered; the Y axis follows the visible window on a WEEK scroll;
the three named regression cases exercised by hand.

---

## P8 — Reduce the hot files before merging them.

**Lands, each with its meApp side in the same change:**
`A1` (`Separators` → a meApp `Decoration.drawUnderLayers`) ·
`A11` (`ListItemPlacer` → meApp, file deleted from the library) ·
`L2b` → `L1` (`MonotoneInterpolator` → meApp, **declaring `visiblePadding = 1`**) →
**`S1A-09b`** (`getInterpolatedYValues` → meApp) → `L2a` (`InterpolationType` → meApp) ·
`A3a` + `A4a` (one meApp edit session on `HorizontalItemPlacer.kt`: `aligned(shiftExtremeLabels = true)`
plus the half-open filter on **both** overrides, **with a new meApp test** — there is none today, in
either repo) · `L14a` + `L14b`.

**Why here, and the methodological conflict it settles.** S3-A's principle is "reductions come after
parity is proven". S3-C puts seven MOVEs in its Phase 2, before any parity. Both are right about
different items. **[S5-B call] the dividing rule: move early iff the code sits in a file you must
otherwise hand-merge against upstream churn; move late iff the file grafts clean.** `A1` takes the
largest additive block out of `HorizontalAxis.kt` (+277/−77 upstream) *before* P9 has to merge it —
doing it after means hand-merging 250 lines you are about to delete. `A11`, `L1`, `L2a` are the same
argument. By contrast `S1A-10` (`Scroll.kt`, untouched upstream) and `S1A-05a` (our own new file) cost
nothing to port and then move, so they wait for parity (P16). The price of the rule is that each early
MOVE must be goldened **at the moment it lands**, against T0 — the golden is the parity proof for that
one item.
**Conflict resolved here ‡:** `S1A-09b` is pulled forward from S3-A's Phase 4 so it precedes `L2a`.
**Behaviour changes to golden explicitly:** `visiblePadding = 1` changes the two edge segments' shape
on a scrolling chart (and is invisible on a static one — no screenshot of a non-scrolling preview will
catch it); `A1` puts separators *under* the guidelines instead of over.
**Pre-existing defect to fix while there:** `HorizontalItemPlacer.kt:92`'s `getLineValues` `else ->`
branch calls `defaultPlacer.getLabelValues`.
**Proof:** goldens unchanged except where predicted; the new half-open-window test; the eight-day week
does not return.

---

## P9 — The axes.

**Lands:** `A2a` + `A2c` (with `LabelVerticalMode`'s default changed to `Outside`, not `Center`) ·
`A2b` + `A3c` re-derived together as a nullable `labelHorizontalPosition` · `A4b` (clamp upstream's
`lineLeft`/`lineRight` to `layerBounds`) · `A7a` (narrowed: the `Size.Scroll` case plus the two
behaviours meApp consumes) · `A9` (as a parameter, not a hard-code) · `A10` (NaN guards).

**Why here:** onto a surface `A1`, `A3a`, `A3b`, `A4a′`, `A6′` and `A11` have already shrunk.
**Why not later:** `A5`/`A8` in P11 land on these files.
**Open:** `U5` — `labelHorizontalPosition = End` on every label combined with
`shiftExtremeLabels = true` has never been rendered.
**Proof:** WEEK and YEAR bottom axes screenshotted at scroll 0 / mid / max — first and last labels
neither clipped nor overlapping; `A10`'s guards exercised on an empty chart and on a chart whose every
entry has the same y.

---

## P10 — The line layer.

**Lands:** `L9` + `L8` (together) · `L6` (as a constructor `val`, in `equals`/`hashCode`, **with
`CartesianChart.kt:576`**) · `L5` (re-derived as `CartesianLayerRangeProvider.isStable`, preserving
v3.3.1's reveal-sweep read of the drawing model) · `L4` (re-derived as `contributesYRange: Boolean`) ·
`L3` (**Option A**: a `CartesianLayerYMapper` seam, ~15 library lines; the cache, the three keys and
the fade state machine move to meApp; `series.hashCode()` replaced by `MetricBasis.version`).

**Why here:** `L9` must precede `C-2` (P11) so upstream's *add* to `maxPointSize.half` and our former
*max* agree. `L5` must precede `L3`. The file is the hardest in the port and wants the normalised diff
(P3), the interpolator already gone (P8) and the host live (P7) so the overlay can be seen working.
**Note on S3-C's own ordering:** S3-C's Phase 4 (C-2) depends on its Phase 6 (L9), and S3-C flags the
inversion itself. Fixed here by putting the layer before the start-of-chart unit.
**Invariants to carry verbatim** (each was a bug once, none covered by a test): compute against
`getTargetYRange`, never the animating `getYRange`; increment the settle epoch before any no-movement
early return; skip the fade when the new result `contentEquals` the old; the window key must be
nullable (`0` is a real key); the cache must survive `copy()`.
**meApp side, larger than S3-D's table implies ‡:** `yTransform` and `alwaysUseLiveRange` both leave
the public API; `contributesYRange` arrives. `PrimaryLayer.kt` and `ProductChart.kt` change shape.
**Proof:** the secondary-overlay golden (#31) and the baby percentile goldens (#27/#28) unchanged;
`U4` instrumented — the fade phase returns to 0 across two windows that round to the same axis.

---

## P11 — The start-of-chart feature, as one unit.

**Lands:** `C-2` (MOVE — `startInsetDp` deleted; meApp passes
`layerPadding = { CartesianLayerPadding(unscalableStart = ChartStartGap) }`) · then `C-3` → `A8` →
`A5`, in that order, landed and verified **together** · `C-1` (re-sited into v3.3.1's
`measure`/`prepare` split; the inset term re-derived to read `layerDimensions.unscalableStartPadding`;
the `equals`/`hashCode` defect on `visibleLabelsCount` fixed).

**Why here:** after `L9` (P10), per the locked `C-2` edge. **Why not split:** "re-applying the pieces
independently yields a chart that compiles and is subtly wrong."
**Open:** `U1` — `A8` draws the line at `chartEdgeX()` while `AxisDimensions.lineBounds` still
describes the gutter. Exact check: log `lineLeft`/`lineRight` and the final bottom-axis clip at scroll
0 / mid / max on WEEK and confirm the clip equals `layerBounds` horizontally.
**Proof:** golden #36 (scrolled away from the start edge) and the start-edge goldens; no second line
painted on the start axis; the correct number of periods per window.

---

## P12 — Draw order, the decorations pass, and the goal pill.

**Lands:** `C-4` (the reorder — clean graft, `draw()` is byte-identical fork → v3.3.1) ·
`C-5` re-derived (**move the generic `decorations.forEach { it.drawOverLayers(context) }` pass** to
where `axisManager.drawMarkerDecorations` sits today, past the fading-edge restore; delete
`drawMarkerDecorations`) · `A6` (the pill → a meApp `Decoration`), **verified on the harness before
the `VerticalAxis` code is deleted, not after**.

**Why here:** `C-4` → `C-5` → `A6`; and `A6`'s lockstep-with-the-axis claim is only true once `H-2`
is in (P7) ‡. **Why not later:** `A6` takes the largest single block out of `VerticalAxis.kt`, and
P13's `A1`-style argument applies — though the file is already merged by P9, so this is a reduction
after its merge, accepted because the dependency chain forbids anything earlier.
**Accepted order changes:** the pill now draws before the legend (meApp passes none) and before the
marker over-passes; a decoration's over-pass now draws over the composited data, which is upstream's
own behaviour.
**Proof:** golden #32 (goal pill) at a y near the canvas bottom — the `1bf0a6d9a` defect was a 63 px
pill at y=822 on an 825 px canvas losing its bottom half.

---

## P13 — The marker.

**Lands:** the interface set — `B-4a` (`drawResting`), `B-5` (`onScrollSettled`), and
**`drawLabels`** if P0 decided so — as one additive, defaulted change and **one upstream PR**
("explicit draw phases and an idle phase for `CartesianMarker`") · `C-6` (the dispatch) ·
`C-7` (synthesised targets — see §3.3, it needs a verdict first) · `H-4` (the settle feed, with
`MARKER_SETTLE_MS` kept equal to the provider's `debounceMs` ‡) · then **the meApp
`DefaultCartesianMarker` subclass**: `B-4b`, `B-7`, `B-8`, `B-6`'s REPLACE.

**Why here:** the subclass cannot override methods that do not exist, and it sits downstream of
`B-11` (P4), `A12` (P4), `D-1` (P0 decision) and `D-2` (P4). Under P8's rule the subclass moves early
relative to its file's merge — ~290 of our lines never get hand-applied to
`DefaultCartesianMarker.kt` at all.
**Carry as a checklist, not a diff:** the crosshair's three `ab3fe3bfc` refinements (start at
`chartStartX()` not `layerBounds.left`; label after the axis line; label in a *later pass* than the
dots); `yLabelCallback`'s empty-list-for-non-line-targets contract; the six `internal` helpers to
re-write; the two non-`open` method bodies to re-implement.
**Conditional verdict to re-check:** if the subclass is abandoned, `B-6` reverts to RE-APPLY and the
seven null-guards become a checklist.
**Proof:** goldens #29/#30 (BP marker at rest and mid-scrub); the date-range caption appears at rest
on both charts and is replaced by the marker label while marked; the guideline's opacity matches the
design (the only visible trace `D-1` leaves).

---

## P14 — The gesture path and the marker's host plumbing.

**Lands:** `B-3a` + `B-3b` + `M-4` (delete `acceptsGestures` and its guard; move
`rememberNonInteractive` to meApp with `acceptsLongPress = false`) — **run P1's smoke gate result
before committing** · `M-1` RE-DERIVE **in a new file `ScrubGesture.kt`**, reducing `Modifier.kt` to a
one-line delegation · `M-2` (`enabled = true` + freeze in `ScrollableState`) · `H-5` + `H-6` + `B-12` +
`B-1` + `B-2` as one contract · `H-7` (per P0's adjudication) · `H-8` + `M-3` (per P0's decision; rename
whatever survives).

**Why here:** `D-2` (P4) first so scrub misbehaviour cannot be blamed on a stale target list;
`B-3a`/`M-4` before `M-1` so there is one fewer condition in the `.then` chain to carry through the
rewrite; the host's marker plumbing is the controller's counterparty.
**Why `M-1` in a new file:** it turns a 234-line conflict surface on the hottest file into about five
lines, permanently — it pays again at the next upgrade.
**Preserve verbatim:** `M-1`'s `Release` exception (keep the explicit state check; discard the returned
`Boolean` on that branch); the `500f` drag/fling velocity threshold and the `4.5f` decay divisor.
**Resist:** using v3.3.1's mutable `scrollEnabled` in place of `isScrollFrozen` —
`enabled = false` detaches the nested-scroll node, which is the exact defect `3a284be42` fixed.
**Proof:** a 200 ms hold starts a scrub and the parent `LazyColumn` does not move during it; a
horizontal drag on the TOTAL chart does not scroll the dashboard; the snapshot card opens the full
graph on tap; YEAR still comes to rest on a month boundary.

---

## P15 — PARITY. T3/T4.

**Lands:** no code. The full golden set re-rendered against the migrated fork through the composite
build, every diff adjudicated as intended / upstream-silent-change / regression, and the adjudication
written down per image.

**Why here:** this is the checkpoint S3-A's principle is built around. Nothing below is required for
the migration, and every item below makes a port defect and a redesign defect look identical.
**The diffs to expect, each already named:** `S-1` the 64 dp auto-height cap on all five surfaces;
`S-2` `drawOutline` vs `drawPath` antialiasing on every data point and the pill; `S-3` one offscreen
layer for seven 40%-alpha percentile strokes; `S-7` marker narrowing on the three coincident BP
series; `U2` — the plot rect moves because `A12` removed the halving *and* v3.3.1 changed what margins
are subtracted from.
**Proof:** every non-empty diff has a written verdict; the device pass (3 products × 4 windows ×
light/dark) is clean.

---

## P16 — Post-parity reductions.

**Lands, cheapest and loudest first:** `S1A-10` (`xBehindStartInset` → meApp; `Scroll.kt` stops being
patched) · `S1A-05a` + the `SnapFlingBehavior.kt` → `ChartSnapFlingBehavior.kt` rename ·
`S1A-08b` (the `key` MOVE, **gated on P1's A→B→A result**) · `H-3` (the window-probe `Decoration`) —
**last**.

**Why here:** each of these files grafts clean, so porting then moving costs nothing extra, and each
introduces a *new* defect class. `H-3` is the single largest reduction in the port — it deletes the
`visibleXRanges` flow, the three-scalar dedup and the per-frame emit from the most-churned file — and
also the largest silent risk: if the decoration is not installed, or is installed on a chart that
`equals` an older one, the whole scroll-aware feature goes dead with the chart still drawing a
plausible full-range axis.
**Knock-on, worth taking here:** with `H-3` landed, `B-5`/`H-4` become deletable (S3-B flagged this
and deliberately did not gate the marker port on it). Budget the rework or accept the patch —
**state which, do not drift**.
**Proof:** goldens unchanged; the window probe emits on exactly the frames the host used to; the fade
still appears with `startWidth = 0.dp`.

---

## P17 — Publish 5.0.0 and cut meApp over. T5.

**Lands:** publish `5.0.0` from the migration clone; verify the POM/`.module` publication set matches
4.0.46's shape (six publications minus `compose-iosx64`, all six runtime deps declared, `.module`
present); `vico.local=false`; `vico = "5.0.0"` in meApp's catalogue; the four upstream compile breaks
(`animateIn = false` ×2 → `initialAnimationSpec = null`; one-arg `getXStep` ×2 → three-arg); delete the
two `@SuppressLint("RestrictedApi")`; rename the four `lineSeries` imports opportunistically;
re-render the goldens against the **published** artifact.

**Why a separate phase:** a composite build bypasses the POM, the `.module` metadata and the AAR
packaging — the three things Part 1 is about, and the three things that took six failed attempts to
get right once already.
**Proof:** T5's diff against T3's goldens is **empty**; `:app:assembleDebug` and `:app:lintDebug`
green against the published artifact.

---

## P18 — Deferred. Explicitly not this migration.

The `ranges` decorator parameter on `CartesianChartHost` (would collapse `H-1`+`H-2` from ~250 lines
to ~6, needs the `U1` snapshot-invalidation spike) · moving `H-2`'s `ScrollAwareRangeEffect` to meApp ·
expressing `ScrubMarkerController` as controller *properties* so it can leave the library ·
hoisting `markerX`/`onMarkerXChange` onto the host (replaces `H-5`, `H-6` and much of `B-1`) ·
`L3` Option B (normalise in meApp, push pre-normalised values) · the upstream PR list, in descending
value-per-line: `getVisibleXRange` public (S2-D **S2**), `VicoScrollState` conversions public (**S6**),
`NeutralYRange` (**S4**), the `CartesianMarker` draw-phase set (**S8**), `flingBehavior` (**S7**),
fading edges over unscalable padding (**S9**), `drawLabel`/`drawGuideline` `open`,
`Position.Vertical.inBounds` + the two `unaryMinus` operators public, `label: TextComponent?`,
`B-11`'s one-word fix, `L8`'s point clip, and the ranges-invalidation ask (**S1**).

---

# 3. Conflicts and gaps

## 3.1 Direct conflicts — two dossiers that cannot both be followed

### CF-1 — `A7b` DELETE vs `S1A-08c` RE-APPLY: does `H-7` survive? **Blocking.**

- **S3-C (A7b):** delete `isLabelsScrollable`, `scrollValue`/`maxScroll`, `updateScrollState`,
  `effectiveScroll` and the four host call sites — "and the real prize — **`H-7`'s entire throwaway
  first `prepare` pass**", which S1-E rated "very high" conflict risk and which collides with v3.3.1's
  new `measure`/`getVerticalExtras` split. Risk of being wrong: "none".
- **S3-A (S1A-08c):** `initialScrollHandled` must be widened `private` → `internal` **because**
  "the host reads it at `CartesianChartHost.kt:467` to decide whether to run `H-7`'s two-pass prepare".
  The row exists expressly so the widening is not swept away with `S1A-08a`.

If `H-7`'s first pass dies, `S1A-08c`'s only stated justification dies with it and the widening should
*not* be applied — the opposite verdict. Conversely if `S1A-08c` is applied as written, something still
reads it, and `A7b`'s "the real prize" claim is overstated.

**The substantive question underneath:** S1-E gives `H-7` **two** purposes — (a) feed
`updateScrollState` into `Size.Scroll` vertical axes, which is exactly what `A7b` deletes, and (b)
make `VerticalAxis.updateHorizontalLayerMargins` see a post-initial-scroll scroll value. S3-C only
disposes of (a). Whether (b) survives independently is unestablished.
**What would settle it:** with `A7b` not applied, build a WEEK chart with meApp's end axis
(`Size.Scroll(50.dp)`, `isLabelsScrollable = false`) and compare frame 1 against frame 2 — if the
first frame's vertical-axis width and plot left edge already match frame 2's, purpose (b) is dead too
and both `H-7` and `S1A-08c` go. If frame 1 differs, `H-7` must be re-derived onto upstream's
`getVerticalExtras` and `S1A-08c` stays. **Settle in P0; it blocks P14.**

### CF-2 — "Reductions after parity" (S3-A) vs "free wins first" (S3-C). **Blocking for P8's content.**

- **S3-A:** "do the port first in the shape the fork already has, prove parity, and only then move
  … Doing both at once makes a port defect indistinguishable from a redesign defect, and this
  cluster's defects are overwhelmingly silent." All four of its MOVEs sit after a parity checkpoint.
- **S3-C:** seven MOVEs in its Phase 2, before anything is proven, on the argument that they "shrink
  the conflict surface immediately" — `A1` alone removes the largest additive block from
  `HorizontalAxis.kt` before that file has to be hand-merged against +277/−77 of upstream churn.

Both arguments are sound and they point in opposite directions for the same items.
**[S5-B call]** the rule in P8: *move early iff the code sits in a file you must otherwise hand-merge
against upstream churn; move late iff the file grafts clean* — with the obligation that every early
MOVE is goldened against T0 at the moment it lands, so the golden is that item's parity proof.
**What would settle it empirically:** the first early MOVE (`A11`, lowest stakes, character-identical
margin methods) — if its golden is pixel-clean, the rule is validated for `A1` and `L1`; if it is not,
fall back to S3-A's position and accept the larger merges.
**Acknowledged exception:** `H-3` would qualify as "move early" under the rule — it is the single
largest deletion from the most-churned file — but its dependency chain (`S1A-01` public, `S1A-06`,
`H-1`/`H-2` proven) forbids it. S3-A's "last" stands; the cost is ~90 lines of `CartesianChartHost.kt`
ported and then deleted. **Stated, not hidden.**

### CF-3 — `S1A-09b` after `L2a` (dossier order) vs before it (dependency). **Resolved here. ‡**

S3-C moves `InterpolationType` out in its Phase 2; S3-A moves `getInterpolatedYValues` — the enum's
only reader, and a `VicoScrollState` member — out in its Phase 4. Executed as written, the library
loses the enum while still holding its reader. Loud, so it self-reports, but it inverts two phases.
**Resolution:** `S1A-09b` pulled forward into P8, immediately before `L2a`.

### CF-4 — `B-5`/`H-4` RE-APPLY vs `H-3` making them deletable.

S3-A: `H-3`'s `Decoration` "also removes `H-4`'s feed by the same route". S3-B: "**Do not gate the
marker port on it**; re-apply now, and revisit if the `Decoration` lands." Not a contradiction, but it
books the same ~40 lines twice: applied in P13, candidate for deletion in P16.
**What would settle it:** nothing to settle — it is a budget decision. **Take it explicitly at P16 and
record which way**, rather than letting it drift into "we meant to".

### CF-5 — `CartesianChart.draw()`'s tail is contested by two clusters.

Four items from two dossiers edit the same ~40 lines: `C-4` (reorder), `C-5` (move the decorations
over-pass past the fade restore), `C-6`/`B-4a` (the `drawResting` dispatch at `:410-412`), and `D-1`'s
`drawLabels` (replacing the second `drawOverLayers` at `:412`). `C-5`'s target position is precisely
where `axisManager.drawMarkerDecorations` sits, which is adjacent to `C-6`'s dispatch.
Separately `D-2` edits `:382-388` and `:161-171`/`:238`, and `C-7` owns `:325`, `:414`, `:481`,
`:544-602`.
**Not a contradiction, a coordination hazard.** Neither dossier knows the other is editing the method.
**Resolution:** one owner for `CartesianChart.kt`; P12 and P13 execute its tail in the stated order
(`C-4` → `C-5` → `A6`, then `B-4a`/`C-6`/`drawLabels`), and the method is re-read end to end once
after P13 rather than trusted hunk by hunk.

### CF-6 — Formatting: normalise (S3-C) vs `diff -w` (S3-B).

Minor, but they are different workflows and only one can be the house rule. **[S5-B call]** normalise
(P3): the brief's constraint requires it, and `-w` does not address `L15`'s star-import expansion.

### CF-7 — S3-C's own Phase 4 depends on its Phase 6.

Internal to one dossier rather than between two, but it must not be executed as numbered: `C-2`
(Phase 4 step 19) requires `L9` (Phase 6 step 26). S3-C flags it in a parenthesis. **Fixed** by P10
preceding P11.

## 3.2 Items with different verdicts or different placement in two plans

| Item | S3-A | S3-B | S3-C | Reconciliation |
|---|---|---|---|---|
| `S1A-08c` / `H-7` | RE-APPLY, "with H-7" | — | `A7b` kills H-7's first pass | **CF-1, unresolved** |
| `C-2` | Phase 0 *decision* (with row 20g) | — | Phase 4 *execution*, after `L9` | decision P0, execution P11 — same substance |
| `B-4c` / `A12` | — | step 3 (after `B-11`, `D-2`) | Phase 1 step 6 | one commit in P4, after `B-11` |
| `B-11` | — | "first and alone, before anything else" | — | P4, after formatting; formatting cannot precede it in S3-B's numbering because S3-B's step 0 *is* formatting |
| `getVisibleXRange` | upstream-PR candidate for `public` | notes it is `internal` | `L10`: revert to `internal` | consistent — `H-3` reproduces the 3-line formula rather than calling it |
| `WindowSettleSignal` | `S1A-04` RE-APPLY; meApp implements it directly | — | `L3` Option A "hands the scroll cluster the option of deleting that interface" | RE-APPLY in P5; re-examine after P10, do not pre-empt |
| `onScrollSettled` / `H-4` | deletable once `H-3` lands | RE-APPLY now | — | **CF-4** |
| `markerTargets` sort | — | `D-2` DELETE both, restore upstream's sort | — | single owner; S3-C does not touch it |

## 3.3 Adaptations that appear in **no** plan

S1-E catalogues `H-1`…`H-10`, `C-1`…`C-8`, `M-1`…`M-4`. The four Stage-3 dossiers give verdicts to
`H-1`, `H-2`, `H-3` (S3-A), `C-1`…`C-5` (S3-C), `M-1`…`M-4` and `C-8`-as-`D-2` (S3-B). **Eight items
have no verdict anywhere.** Each is referenced as a dependency by at least one dossier, which is how
they survived unnoticed.

| Item | What it is | Where it is referenced | Status |
|---|---|---|---|
| **`C-7`** | `synthesizeInterpolatedTargets` — ~60 lines in `CartesianChart.kt` that build a marker target at an arbitrary x, per layer, skipping `markerTargetsEnabled = false`, interpolating with `getYAtXFromEntries`. Needs the retained `drawingContext` at `:325`/`:414`/`:481`. | S3-B B-10 ("couples to C-7, which the layer-data cluster owns"); S3-C L2b and L6 cite its line numbers | **No verdict. The largest orphan.** It is the thing `B-10`, `L6` and `L2b` all exist to serve, and it duplicates the layer's own x→canvasX maths — "any upstream change to how a layer maps x→canvasX silently desynchronises the synthesised target from the drawn line." Needs a verdict before P13. |
| **`H-4`** | marker resting text formatted on scroll settle; `MARKER_SETTLE_MS = 100` tied to the provider's `debounceMs` | S3-A (deletable via `H-3`); S3-B B-5 ("blocked by H-4") | implied RE-DERIVE; no owner. Scheduled P13. |
| **`H-5`** | `initialMarkerX` on both public overloads + the two-way binding with `hasActiveMarker` | S3-B B-12 and step 9 | implied RE-DERIVE (S3-B says "the host's rewritten overload set"); no verdict row. P14. |
| **`H-6`** | scrub callback routed through `onInteraction`; scrub exempted from `onViewportChange` | S3-B B-1/B-2, step 9 | same. P14. |
| **`H-7`** | two-pass prepare | S3-A `S1A-08c`; S3-C `A7b` | **contested, CF-1.** |
| **`H-8`** | external `flingBehavior` threaded host → `Modifier` | S3-A §2(c) ("settle H-8 before executing this move"); S3-B M-3 | **no verdict, and S3-A's `S1A-05a` MOVE dies without it.** S1-E's own recommendation was to *delete* it in favour of `xSnapStep` — which C2 rejects for YEAR. Must be decided in P0. |
| **`H-9`** | `scrollState.drawingContext = drawingContext` (one line, Host:531) | S3-A `S1A-09a` ("blocked by … H-9"; losing it is silent) | one line, implied RE-APPLY. Scheduled P7. |
| **`H-10`** | chart-area height parity (`heightIn(max = CHART_HEIGHT.dp)` removed) | `CONTRADICTIONS.md` resolved-by-convergence: "obsolete — the 200 dp cap is gone, `chartAreaHeight` is a public parameter" | effectively DROP, and S1-E agrees ("zero conflict risk as a deletion"). Also leaves three dead imports including the dangling `Defaults.CHART_HEIGHT`. Recorded in P4; **no dossier states it as a verdict.** |
| `C-6` | the `drawResting` dispatch line | folded into S3-B's `B-4a` ("+ the `CartesianChart.draw` dispatch") | covered in substance, not as a row |

**[ASSUMPTION]** that the host cluster (`H-4`…`H-10`) was left without a Stage-3 owner because S1-E's
items were split across the four dossiers by *file*, and `CartesianChartHost.kt` fell between S3-A
(which took `H-1`/`H-2`/`H-3`) and S3-B (which took the marker's counterparties by reference only).
If that is wrong and a fifth dossier exists, this section is void.

## 3.4 Gaps in S3-D's consumer analysis ‡

S3-D Part 2's 22-API table is the migration's coupling surface, but it is **not complete**, and the
gaps are all MOVEs whose meApp side is therefore unbudgeted:

- `rememberVicoScrollState(key = …)` — `GraphView.kt:197-201` (`S1A-08b`).
- `VicoScrollState.getInterpolatedYValues` — `GraphView.kt:497` (`S1A-09b`).
- `Scroll.Absolute.xBehindStartInset` — `GraphView.kt:188`, `SnapshotLineChart.kt:94` (`S1A-10`).
- `VicoScrollState.getVisibleAxisLabels` — `GraphView.kt:408, 413-416` (`S1A-09a`, kept).
- The whole of `DefaultCartesianMarker`'s fork-only constructor surface — `restingLabel`,
  `restingLabelFormatter`, `restingLabelAnchor`, `labelGap`, `yLabelCallback`, the crosshair pair,
  nullable `label` — consumed by `GraphMarker.kt`. `B-4b`/`B-6`/`B-7`/`B-8` all land here and the
  table has no `DefaultCartesianMarker` row at all.
- `ScrubMarkerController(scrollState = …)` — the argument deleted by `B-1`, at `GraphView.kt:268-269`.
- **Removals** the table does not model as removals: `alwaysUseLiveRange` (`L5`), `yTransform` (`L3`),
  and the arrival of `contributesYRange` (`L4`).

**Consequence:** the meApp change-set is substantially larger than "four breaks plus a version bump".
It is spread across P8, P10, P12, P13, P14 and P16 rather than concentrated in P17, which is the
right shape — but it must be budgeted there, not at the cutover.

## 3.5 UNRESOLVED items that **block** a phase

Twenty UNRESOLVED items are recorded across the four dossiers. Seven block; the rest inform.

| # | Item | Blocks | Settle by |
|---|---|---|---|
| **BL-1** | Is vico's Compose rendering Paparazzi-clean at 4.0.46? (S3-D app. 6) | **T0 (P1), therefore everything** | the one-test spike, all three pass criteria. If (1) fails → AGP Compose Screenshot Testing, mirroring upstream's config, before concluding Layoutlib cannot render vico |
| **BL-2** | Does `substitute(module("com.dmdbrands.lib:compose-android")).using(project(":vico:compose"))` resolve at v3.3.1's layout, across AGP 9.3.2 (lib) vs 9.5.1 (app)? (S3-D app. 7) | **the entire iteration loop, P4 onward** | `dependencyInsight` in the composite build at v3.3.1, before porting any library code. Fallbacks (a)/(b)/(c) are recorded |
| **BL-3** | Are Kotlin context parameters on by default at 2.4.10? (S3-D app. 1) | **the first green build, P2** | `./gradlew :vico:compose:compileKotlinAndroid` in the clone. Failure → add `-Xcontext-parameters`, do not downgrade Kotlin |
| **BL-4** | Is the `flingBehavior` seam (`H-8`/`M-3`) kept? (S3-A Phase 0 #2, S3-B U-3, C2's open half) | **P14**, and `S1A-05a` in P16 | build a WEEK chart with `rememberVicoScrollState(xSnapStep = one day)` and compare rest positions after a fling against the fork's behaviour. YEAR is already settled (a month is not a uniform step) |
| **BL-5** | `A7b` vs `S1A-08c`/`H-7` (CF-1) | **P6's `S1A-08c` edit and P14's `H-7`** | the frame-1-vs-frame-2 comparison in CF-1 |
| **BL-6** | `D-1`: add `drawLabels` or collapse to one call? (S3-B §2 D-1) | **P13** — the subclass's method split *is* the fix, so it cannot be decided after the subclass is written | a decision, not a spike. If the extra member is judged too much patch, state the cost (indicator dots paint over the layer bitmap) explicitly |
| **BL-7** | `C-2` + `L9`: the few-dp start-gap difference (S3-A §2(a) caveat 1) | **P11**, and `S1A-11`'s visual check | a product look at `12.dp` vs `12.dp + maxPointSize/2`. Not a code question |

**Informing, not blocking** (settle during the phase that touches them): S3-A `U1` (the `ranges`
decorator spike — explicitly deferred to P18), `U2` (`key()` vs `rememberSaveable`, gates `S1A-08b`
only, baseline captured in P1), `U3` (`maxLabelWidth = 0f`); S3-B `U-1` (does the `yLabelCallback`
fallback path run — gates MOVE-vs-DELETE of `B-8`, instrumented in P1), `U-2` (`markerTargets`
iteration order — gates only the sort *optimisation*; restoring the sort is safe regardless);
S3-C `U1` (`A8` vs `lineBounds` — a verification inside P11), `U2` (does `A12` change the measured
height — answered by the P15 diff, which is why P1 is a gate), `U3` (dashed lines and `visiblePadding`
— one grep), `U4` (is the fade ever stalled — instrumented in P10), `U5` (`labelHorizontalPosition`
with `shiftExtremeLabels` — a P9 screenshot); S3-D app. 2 (publish task name — P2), app. 3 (does the
64 dp cap bind — a P15 diff to adjudicate, not a decision), app. 5 (lint's verdict on the two
suppressions — P1), app. 8 (GPR version ordering — informational only), app. 9 (which overload
`getXStep` binds to — "a compile", and moot either way).

**One consolidation worth acting on:** five of these can **only** be answered against a running,
un-migrated 4.0.46 build — the `acceptsGestures` smoke gate, the lint run, the `L12b` rotation check,
the `yLabelCallback` instrumentation, and the A→B→A baseline. They are listed in four different
dossiers and none of them says so. **All five are scheduled into P1**, beside the goldens, for the
same reason the goldens are there.

---

# 4. Effort estimate

**Calibration.** The user's board estimates comparable work at **1d–3d per ticket**; this is a senior
Android developer working alone on a Compose chart library. The phases below are each **larger than a
board ticket** — most would be split into two to six tickets. Where a phase is uncertain the range is
wide on purpose; a narrow wrong number is worse than an honest spread.

| Phase | What dominates the number | Days | Confidence |
|---|---|---|---|
| **P0** Settle blockers | The Paparazzi spike is the long pole (S3-D scopes it to 1d; criterion 3 — CI/JDK/OS drift — is the one that most often fails and can add 2d). The other six are reading and deciding. | **2–4** | moderate |
| **P1** T0 goldens + 4.0.46 checks | 36 images is not the cost; `ChartFixtures.kt` is — deterministic data for 3 configs with fixed timestamps, locale and TZ, plus driving the async Y settle to a settled frame. Five manual checks on a device. | **4–7** | **uncertain** — depends entirely on BL-1 |
| **P2** Clone + build rebase | Fully enumerated in S3-D §1.2; three edits to two files plus verbatim takes. Risk is toolchain: three AGP versions in one composite build. | **1–3** | predictable |
| **P3** Formatting normalisation | Mechanical over 14 files; fiddly (ktfmt version pin, star-import expansion). | **1–2** | predictable |
| **P4** No-ops + `B-11` + `D-2` + `L13` | 13 items that are "do not apply" and cost bookkeeping; 3 real applies. `D-2`'s invalidation needs thought. | **2–4** | predictable |
| **P5** Clean-graft contracts | 8 items, well under 300 lines, all on untouched or byte-identical files; three fail loudly if dropped. | **1–2** | predictable |
| **P6** `VicoScrollState` | Hot file (+182/−41) but `S1A-07` is "a mechanical re-wrap" on an identical lambda body; the rest is visibility. | **2–3** | predictable |
| **P7** Host core | `H-1` is "the hardest single item in the cluster"; `H-2` is ~250 lines; the host is ours +454/−8 against theirs +196/−94, with `prepare` re-signatured, the `Canvas` moved into `ChartHostBox`, and `animateIn` → `initialAnimationSpec`. A RE-DERIVE cannot be estimated from the diff. Three named silent regressions to re-prove. | **6–12** | **genuinely uncertain** |
| **P8** Hot-file reductions | Five MOVEs with meApp sides, a new `Decoration`, a new meApp test for the half-open rule, and a behaviour change (`visiblePadding = 1`) to golden. | **4–7** | moderate |
| **P9** Axes | `A2b`+`A3c` is real design work against upstream's new per-index `when`; the rest re-applies onto a surface P8 has already shrunk. | **3–5** | moderate |
| **P10** Line layer | The hardest file. `L3` Option A is a new library seam plus a ~120-line meApp class; `L5` and `L8` are re-derivations against functions upstream rewrote (visible slice, reveal sweep, ART JIT workaround). Zero existing test coverage on `yTransform`. | **5–9** | **genuinely uncertain** |
| **P11** Start-of-chart unit | Four coupled items plus `C-1`'s re-siting into the `measure`/`prepare` split. Small individually; `U1` could bite. | **2–4** | moderate |
| **P12** Draw order + pill | Two clean grafts and one statement moved, plus a meApp `Decoration`. Contested file region is the risk, not the code. | **1–3** | predictable |
| **P13** Marker | Interface hooks are nine defaulted lines each; the meApp subclass is "the largest single piece of work in the cluster" — six `internal` helpers re-written, two non-`open` bodies re-implemented, three field-found refinements to preserve. Plus `C-7`, which has no verdict yet. | **5–9** | **uncertain** |
| **P14** Gesture + host plumbing | `M-1` is a 180-line RE-DERIVE against a changed (`Boolean`) contract and a re-implemented tap detector; `H-5`/`H-6`/`B-12` rebuild onto a changed overload set; `H-7` and `H-8` depend on P0's answers. | **5–9** | **uncertain** |
| **P15** Parity (T3/T4) | The number of diffs is unknowable in advance. `A12`'s margin change alone moves the plot rect and the vertical axis's measured width, which can cascade through every one of the 36 images. | **3–6** | **genuinely uncertain** |
| **P16** Post-parity reductions | Four cheap moves plus `H-3`, which is the largest reduction and the largest silent risk in the port. | **3–5** | moderate |
| **P17** Publish + cutover | Mechanical, but the publication configuration took six failed attempts once before. | **2–4** | predictable |
| | **Total** | **52–98 days** | |

**≈ 11–20 working weeks for one developer; midpoint ~15 weeks.**

**Which phases are predictable, and why.** P2, P3, P4, P5, P6, P12, P17 are predictable because the
work is enumerated down to line numbers in the dossiers and the files are either untouched upstream,
byte-identical, or already shrunk. Their risk is clerical: applying half of a coupled pair.

**Which are genuinely uncertain, and why.** P1, P7, P10, P13, P14, P15.
- **P1** rests on BL-1. If Paparazzi cannot render vico deterministically, the fallback is AGP's
  plugin (a different source set and a less ergonomic golden workflow) or instrumented capture (which
  removes the whole "runs in CI, no device" premise). That is a different plan, not a longer one.
- **P7 and P10** are RE-DERIVE work: the intent survives, the code cannot be ported, and the target
  functions were rewritten by upstream. You cannot estimate a re-derivation by counting the diff —
  the diff tells you what was, not what the new shape costs. Both also carry defects that are silent
  by construction, so "done" means "goldens agree", not "it compiles".
- **P13 and P14** are RE-DERIVE *plus* the largest meApp-side builds in the plan, against contracts
  that changed shape (`(Interaction) -> Boolean`, a private tap detector, a changed overload set).
- **P15** cannot be estimated at all until P1 exists: its cost is the count of non-empty diffs, and
  at least three named upstream silent changes (`S-1`, `S-2`, `S-3`) are expected to produce some.

**The one compression lever, stated honestly.** Deferring P16 and the P13 subclass to a follow-up
release would cut roughly 10–18 days and leave `~290` marker lines plus `~90` host lines in the fork.
BASELINE decision 2 ("minimise the fork; a smaller fork surface is worth a slower port") says not to
take it. It is recorded so the trade is visible, not recommended.

**What would most change these numbers, in order:** BL-1 (a failed Paparazzi spike re-shapes P1 and
P15), CF-1's answer (it adds or removes `H-7`'s re-derivation from P14), and the count of P15 diffs.
