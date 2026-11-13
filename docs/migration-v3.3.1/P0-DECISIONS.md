# P0 — blocker decisions (MOB-4096)

## BL-3 — Kotlin context parameters at 2.4.10 · SETTLED, not a blocker

v3.3.1 uses `context(pointerEventScope: AwaitPointerEventScope)` at `Modifier.kt:178`, and there is
**no opt-in flag anywhere** — `git grep "context-parameters|freeCompilerArgs|languageVersion"` over
`v3.3.1:buildSrc v3.3.1:vico v3.3.1:gradle.properties v3.3.1:gradle/libs.versions.toml` returns nothing.

Upstream's `.github/workflows/build.yml` runs `./gradlew build -x lint -x test` on **JDK 17** at
**Kotlin 2.4.10**, and `publish.yml` ran `publishToMavenCentral` for this tag. The release exists, so
it compiled. Context parameters are on by default at 2.4.10.

**Action:** take upstream's `gradle/libs.versions.toml` verbatim, as PLAN §P2 already says. The only
thing that would have broken this is our own `kotlin = "2.1.21"` pin, which we are dropping anyway.

---

## BL-6 — the `drawLabels` split · SETTLED: add the phase, do not collapse

**What HEAD does.** `DefaultCartesianMarker.drawOverLayers` does five things in one method
(`:116-173`): computes per-series y values, `drawGuideline`, `drawHorizontalCrosshair`, the indicator
dots, then `drawLabel`. `CartesianChart.draw` then calls it **twice** — at `:393`, before the layer
bitmap is composited, and again at `:412`, after the fading-edge restore, the goal pill and the legend.

So every frame draws the guideline twice, the crosshair twice, every dot twice, the label twice, and
fires `yLabelCallback` twice. Invisible on opaque paint; doubles translucent alpha.

**Why not simply delete the second call.** The two calls genuinely want different z-positions. The
**label** must sit above the goal pill and the legend — that is why the second call is placed after
`axisManager.drawMarkerDecorations(context)` and `legend?.draw(context)`. The **guideline, crosshair
and dots** must sit above the lines but below the pill. Collapse to one call and one of the two
regresses visibly, whichever position you pick.

**Decision.** Give `CartesianMarker` a third phase:

```
drawUnderLayers(context, targets)   // upstream, unchanged
drawOverLayers(context, targets)    // guideline, crosshair line, indicator dots — ONCE
drawLabels(context, targets)        // the label and yLabelCallback — ONCE, after pill and legend
drawResting(context)                // already exists; shares drawLabels' slot and line
```

`drawLabels` is **defaulted to a no-op**, so it is additive for every other marker implementation.
`drawResting` already documents that it writes "on the line [drawLabel] uses, which is free while
nothing is marked" (`:382`), so the two belong in the same slot: `if (drawMarker) drawLabels(...)
else drawResting(...)`.

**Consequences recorded for later phases:**
- This split **is** the fix for defect D-1. It must exist before the meApp marker subclass is written
  in P13, because the subclass overrides these methods — hence BL-6 being a pre-start blocker.
- `yValuesPerSeries` is read by both `yLabelCallback` and the crosshair formatter
  (`DefaultCartesianMarker.kt:122-126`). After the split each phase computes what it needs. That is
  still two computations per frame — exactly what happens today — so this is not a regression, and
  halving it is a P10 optimisation, not part of this decision.
- Whether the crosshair's *line* stays in `drawOverLayers` while its *label* moves to `drawLabels` is
  implementation detail for P13, not part of this decision.
- `drawLabels` joins `drawResting`, `onScrollSettled` and `isInterpolated` in the upstream PR for
  explicit marker draw phases.
