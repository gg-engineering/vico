# Cross-agent contradictions, adjudicated

Phase 1 ran nine independent agents. Where they disagree, the disagreement is recorded here with a
verdict and the evidence, so Stage 3 does not re-litigate it or silently pick the more confident voice.

---

## C1 — "Delete ScrollAwareRangeProvider, upstream does it now"  → **REJECTED**

**The claim (S2-A, severity-1 entry 4):** upstream's `a1e3d0296` "now natively does what MOB-3494 was
built for", pointing at "deleting our `SnapFlingBehavior.kt`, `ScrollAwareRangeProvider.kt` and
`WindowSettleSignal.kt` rather than porting them."

**Contradicted by** S2-B (`NOT UPSTREAM`, and it warned about this exact confusion), S2-C
(`ScrollAwareRangeProvider` + `WindowSettleSignal` → "all keep, no upstream counterpart"), and
S2-D (the root-cause sealed door S1 exists precisely because upstream cannot do this).

**Verified directly at v3.3.1:**
- `CartesianLayerRangeProvider` is still `getMinY(minY, maxY, extraStore)` / `getMaxY(...)` —
  **zero** occurrences of "scroll" or "visible" in `CartesianLayerRangeProvider.kt`,
  `CartesianChartRanges.kt` or `MutableCartesianChartRanges.kt`.
- `CartesianChart.updateRanges` is **`internal`** (`CartesianChart.kt:406`) and is called only from
  data-push paths: `CartesianChartHost.kt:191`, `CartesianChartModelProducer.kt:126,202`,
  `CartesianChartRenderer.kt:48`. Nothing re-asks it on scroll.

**Verdict: the two features are different and only one of them is upstream.**
- Upstream `a1e3d0296` preserves the visible **X** range across a data update — a scroll-value
  correction, so the same x-coordinates stay on screen. We should adopt it (it is the library half of
  the MOB-3494 range jump).
- Ours recomputes the **Y** range from the entries currently visible, continuously, as the user
  scrolls. Upstream has no analogue.

**Consequence if unchecked: deleting the fork's flagship feature.** `ScrollAwareRangeProvider.kt` and
`WindowSettleSignal.kt` are KEEP. Only the `SnapFlingBehavior` half of that recommendation survives,
and only partly — see C2.

---

## C2 — Does upstream's `xSnapStep` retire our `SnapFlingBehavior`? → **PARTIALLY**

S2-D could not settle it from the library diff alone; S2-A read it as full retirement.
S2-B and S2-C supply the consumer fact that decides it: **meApp snaps to calendar boundaries** —
day for WEEK, **month** for YEAR — and a month is not a uniform millisecond step, so it is not
expressible as a single `xSnapStep`.

**Verdict:** WEEK can plausibly move to upstream's snap; YEAR cannot. Partial retirement.
Independently, upstream added an `internal class SnapFlingBehavior` in
`com.patrykandpatrick.vico.compose.cartesian` — **same package and simple name as our new file**, so
whatever we keep must be renamed regardless of this decision.

---

## C3 — Is "start inset as a length" already an upstream parameter? → **RESOLVED: yes. S2-B was right.**

**Settled by S3-A against v3.3.1 source.** `CartesianLayerPadding(unscalableStart = …)` writes **the same
field by the same mutator** our `CartesianChart.kt:299` patch does. Chain:
`rememberCartesianChart(layerPadding=…)` (CartesianChart.kt:700, public) → `CartesianMeasuringContext.layerPadding`
(:53, public) → `LineCartesianLayer.kt:901` `ensureValuesAtLeast(unscalableStartPadding = …)` →
`MutableCartesianLayerDimensions.ensureValuesAtLeast` (:44-56, pure per-field `coerceAtLeast`, order-independent)
→ `startPadding` → `getFullXRange` → `getVisibleXRange`. Ours is literally step 4 on the same field.

**Clinching evidence:** `CartesianLayerPadding.kt`, `CartesianLayerDimensions.kt` and
`MutableCartesianLayerDimensions.kt` are **byte-identical across fork point, HEAD and v3.3.1**. The parameter
existed all along and we never touched it. meApp passes no `layerPadding` today, so the slot is free.

**Consequence: a patch site on the hottest file disappears.** Three cheap caveats are recorded in S3-A
(upstream *adds* to `maxPointSize.half` where we *max*; `calculateLabelSpacingScale` needs the value from
`layerDimensions.unscalableStartPadding`; bottom-axis ordering is moot in both plausible worlds).

S2-C's `shiftExtremeLabels` is a genuinely different feature (label clipping). It neither competes nor substitutes.

---

## C4 — "The `SnapFlingBehavior` name collision is a hard compile blocker" → **REJECTED**

**The claim (S2-A §1.4):** upstream added `internal class SnapFlingBehavior` in
`com.patrykandpatrick.vico.compose.cartesian` — "same package and simple name as our new
`SnapFlingBehavior.kt`. Redeclaration error on any naive graft."

**Corrected by S3-A:** upstream's class is `SnapFlingBehavior`, declared inside `Modifier.kt:191-209`.
**Ours is `ChartSnapFlingBehavior`**, in a file named `SnapFlingBehavior.kt`. Different simple names compile
fine. `git grep 'SnapBehaviorConfig|rememberChartSnapFlingBehavior|isSnapping' v3.3.1 -- vico/` returns nothing.

**The real hazard is the file name, not a collision:** `import …cartesian.SnapFlingBehavior` now silently
resolves to upstream's class. Rename the file; there is no redeclaration error to fix.

---

## C5 — `isSnapping`: fix the missing suppression, or delete it? → **DELETE**

S1-A found `ChartSnapFlingBehavior.isSnapping` written but never read, contradicting FEATURE-4's documented
range-update suppression. S3-A confirms it is written-only (3 hits, all ours, and `internal` so meApp cannot
read it either) — and goes further: **restoring the documented behaviour would now be wrong, not merely
redundant.** Emissions during a snap are already collapsed by the three-scalar dedup plus `debounce` +
`collectLatest`; suppressing as well would make the Y axis lag the chart by the whole snap animation (500 ms
in meApp). Delete the flag and the FEATURE-4 paragraph.
---

## Resolved-by-convergence (recorded so they are not re-opened)

- **`VicoScrollState`'s re-apply-initialScroll-on-maxValue heuristic: DELETE, do not port.**
  S2-A, S2-C and S2-D agree; v3.3.1's `update()` re-anchors the viewport properly.
- **Chart layout parity (FEATURE-10) is obsolete.** S2-A and S2-B agree: the 200 dp cap is gone,
  `chartAreaHeight` is a public parameter. (The `CartesianLayerMargins` halving half is still ours —
  and S1-C/S2-D agree that half should be deleted, not ported, as self-inflicted.)
- **The port is a hand-applied per-file diff, never a rebase or cherry-pick.** S2-A's transient
  `vico/shared` finding; nothing contradicts it.
- **Formatting inflates the conflict.** S1-C (~56 of HorizontalAxis's 149 removed lines),
  S1-D (~half of LineCartesianLayer's 320 added lines), S2-A (ktfmt 0.59→0.64 touched 14 files).
  Normalise formatting, or diff with `-w`, before attempting any merge.

---

## C6 — "meApp breaks in exactly four places" → **TRUE ONLY CONDITIONALLY**

S1-E reported four day-one consumer breaks (`animateIn = false` ×2, single-arg `getXStep` ×2).
S3-D established that figure holds **only if the port carries the fork's entire added API forward**.
Against *stock* v3.3.1, meApp breaks in **~30 places**: it consumes **22 fork-only APIs**, enumerated in
S3-D Part 2 with declaration line at HEAD, absence at v3.3.1, and every call site.

**Why this matters more than the four:** under the locked "minimise the fork" decision, every adaptation
dispositioned DROP / DELETE / MOVE **becomes a meApp break that must land in the same change**. The 22-API
list is the migration's real coupling surface; the four are only the breaks that survive a perfect port.

S3-D also found one S1-E missed: `CartesianChartModelProducer.runTransaction(animate = …)` is fork-only
(HEAD `:174`; absent at both `e67e49520:153` and `v3.3.1:153`), used at **7 call sites across 4 ViewModels**.

Also corrected: the two `getXStep` call sites are `ProductChart.kt:99` and `SnapshotLineChart.kt:149`
(S1-E said :97 and :150).

---

## C7 — meApp's two `@SuppressLint("RestrictedApi")` are migration risk → **REJECTED, both are vestigial**

Raised in S4-A as a risk in their own right (a `@RestrictTo` member can vanish without deprecation).
S3-D cross-checked every symbol `ProductChart.kt` and `PrimaryLayer.kt` reference against the complete
`@RestrictTo` set — six declarations at 4.0.46, eight at v3.3.1 — and found **no overlap**. Neither file
uses any restricted API.

**Provenance:** the suppression entered meApp at `291601de62` (2025-09-25), in the same commit that upgraded
vico 2.1.8→2.2.8, when `PrimaryLayer.kt` used `CorneredShape.Pill` — and vico 2.x had 37 files carrying
`@RestrictTo`, including `CorneredShape.outline()`. That member is what upstream later removed. The
suppression outlived its cause and was copied into `ProductChart.kt` by `78b5fce2c8`.

**Disposition: delete both.** Settling check recorded: remove them and run `./gradlew :app:lintDebug`
against 4.0.46 before the migration starts.

---

## C8 — pointer: five more verdict errors live in `dossiers/S5-A-adversarial.md`

The adversarial pass over every DELETE / MOVE / REPLACE returned **5 WRONG, 3 UNPROVEN, 21 CONFIRMED**.
They are recorded there in full, not duplicated here, but the register would be misleading without the
pointer. Headlines:

- **F1** `A4a` MOVE describes only half the adaptation. The fork makes *two* changes in
  `AlignedHorizontalAxisItemPlacer.getLabelValues`; S3-C describes the epsilon filter and never mentions
  the **deletion of upstream's `fullXRange.start`/`endInclusive` guards**. A delegating wrapper can only
  subtract from upstream's list — it cannot restore a value upstream's `break` suppressed.
  `A4a` and `A5` are **one feature** and must be sequenced together.
- **F5** `S1A-08c` must flip **RE-APPLY → DELETE**: once `A7b` goes, `initialScrollHandled`'s only
  outside reader (`CartesianChartHost.kt:467`) goes with it, so the widening has no consumer.
  This resolves CF-1 from the opposite direction to S3-A's stated risk, which was false.
- **F7** `L1` has **three** meApp call sites, not two — `PrimaryLayer.kt:50` was missed.
- **F6/F8** two "touches nothing internal" claims were wrong in detail (`Float.half`; `Rect.getEnd`).
  Neither blocks, but both are the sentence that stops someone looking.

**The systemic one — F3, UNPROVEN and unowned.** Three MOVEs (`A1` separators, `A6` goal pill,
`H-3` window probe) all land as `decorations` entries, and `C-2` adds a `layerPadding` lambda. Both are
`rememberCartesianChart` remember-keys **and** `CartesianChart.equals` terms, and the host's
`remember(chart, model) { ranges.reset(); chart.updateRanges(…) }` — where the provider seeding lives —
is keyed on that equality. An unstable list, or a capturing `layerPadding` lambda, re-seeds the range on
every recomposition: **the MOB-3494 defect shape**. meApp passes no `decorations` today, so this is an
entirely new surface created *by* the minimise-the-fork strategy. No cluster owns it.

The dossier also lists what it deliberately did **not** attack (C1–C7, the RE-DERIVE verdicts, the open
U-items, ~30 unflagged RE-APPLY rows) so that silence is not read as clearance.
