# Stage 4a — How do we prove the charts still look right?

## Finding: we currently cannot.

There is **no visual regression testing in meApp**. No Paparazzi, no Roborazzi, no Shot, no
screenshot library of any kind in `gradle/libs.versions.toml` or `app/build.gradle.kts`.

"Snapshot" in this codebase means the **Dashboard Snapshot card feature**, not image snapshots.
`SnapshotLineChartAccessibilityTest` and `ScaffoldAndChartAccessibilityTest` are instrumented
accessibility tests — they assert semantics, not pixels.

What chart coverage exists is entirely JVM logic around the data that feeds the chart:
`GraphUtilTest`, `GraphYAxisTest`, `GraphUtilWindowsTest`, `GraphUtilToGraphPointsTest`,
`GraphSnapHelperTest`, `GraphLabelHelperTest`, `GraphReducerTest`, `ChartCaptionTest`.
Useful, and all of it stays green no matter what the renderer does.

**Consequence for this migration.** We are re-applying 13 adaptations whose entire purpose is
visual and interactive — curve shape, axis insets, label placement, crosshair containment, marker
rest position, y-transform animation. Today the only acceptance test for all of that is Selva
looking at a device. Across 3 products x 4 time windows x light/dark, that is ~24 screens to eyeball
per iteration, and the subtle regressions (a 2dp inset, a label one step off, a curve that
overshoots) are exactly the ones an eye misses on the fifth pass.

## Recommendation: capture goldens from 4.0.46 BEFORE migrating.

This is the one piece of work that should land **ahead of** the port, not alongside it. Goldens
taken from the current, known-good fork turn every later question from "does this look right?"
into a diff.

**It is cheap here, because the chart is already a pure composable.**
`rememberProductChart(config, window, separators, defaultMarker, segment, horizontalItemPlacer, ...)`
in `features/common/components/chart/ProductChart.kt` takes plain data and **no ViewModel** —
`grep ViewModel` on that file returns nothing. Feed it a fixed `ChartConfig` + `PlotWindow` and it
renders deterministically.

**Suggested golden set** (~30–40 images):
- 3 configs — `ProductSelection.MyWeight`, `BloodPressure`, `Baby`/`BabyScale`
  (`ChartConfig.kt:73,85,94`)
- x 4 segments — `WEEK`, `MONTH`, `YEAR`, `TOTAL` (`GraphSegment`)
- x light/dark
- plus edge states: empty, single point, baby percentile bands, marker at rest, mid-scrub

**Tooling:** Paparazzi (JVM, no device, runs in CI, fast) over Roborazzi, unless the charts need
real GPU behaviour we can't fake. Worth a spike to confirm vico's Compose rendering is
Paparazzi-clean — that spike is itself a Phase 2 task and must be verified, not assumed.

## Second finding: meApp already reaches into restricted vico API.

`ProductChart.kt` and `PrimaryLayer.kt` both carry `@SuppressLint("RestrictedApi")`. The consumer
is using API upstream marks as not-for-consumers. Those two call sites are migration risk in their
own right — a `@RestrictTo` member can change or vanish between releases with no deprecation —
and they belong on the "sealed doors" list (see S2-D), as candidates for either an upstream PR or
a fork-side public wrapper.
