# P1 — golden harness, as built (MOB-4096)

## What works

**Tool: AGP native screenshot test suites**, not Paparazzi. Paparazzi's AGP 9 support arrived only in
`2.0.0-alpha05.1` (2026-09-28) and targets AGP **9.4.1**; meApp runs **9.5.1**. The standalone
`com.android.compose.screenshot` plugin that upstream vico uses is deprecated. AGP test suites are the
supported path at 9.5.

Setup, in meApp on `MOB-4096-vico-golden-harness`:
- `gradle.properties`: `android.experimental.enableScreenshotTest=true` + `android.experimental.testSuiteSupport=true`. **Both are required** — dropping the first fails with an explicit message.
- `app/build.gradle.kts`: `testOptions.screenshotTests.create("screenshotTest")`, engine `0.0.1-alpha16`, target variant `debug`.
- `testOptions.unitTests.isIncludeAndroidResources = true` — **required**: preview rendering resolves layout XML and theme attributes off the unit-test classpath.
- `beforeVariants { it.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true }` — **required**: AGP generates Compose preview runfiles for *every* variant, and release has unit tests off by default, which fails configuration. Note `VariantBuilder.enableUnitTest` is hidden-deprecated at 9.5.1; the live API is `hostTests[UNIT_TEST_TYPE].enable`.
- Source set `app/src/screenshotTest/kotlin/`; references land in `app/src/screenshotTestDefaultDebug/reference/`.

Task names differ from the published docs — the variant comes **first**:
`:app:updateDebugScreenshotTestDefaultTestSuite` to record, `:app:testDebugScreenshotTestDefaultTestSuite` to verify.

**Six goldens recorded and visually confirmed** at the production height (229dp = 199 plot + 20 text +
10 gap), 360dp wide, light and dark: weight (monotone curve, 7 points, day labels, dashed separators),
blood pressure (3 series), baby (data line over the 7 pale CDC bands). Re-verify passes, 0 failed.

## The limit that matters — coroutine-driven behaviour does not render

**The resting marker label is absent from every golden, and it is not a height problem.**

`DefaultCartesianMarker.drawResting` draws `restingText`, which is produced by `onScrollSettled`. The
host drives that from a `LaunchedEffect`:

```
marker.onScrollSettled(visibleXRanges.first())
visibleXRanges.debounce(MARKER_SETTLE_MS).collect(marker::onScrollSettled)
```

Preview rendering runs no coroutines — upstream says so in its own sample
(`runBlocking` "only for previews, which don't support asynchronous execution"). So the effect never
runs, `restingText` stays null, and `drawResting` returns at its first line.

**The consequence is larger than one label.** The fork's own comment on `drawResting` says this is
"the same split `ScrollAwareRangeProvider` uses", and the host comment confirms the first-then-debounced
pair is shared with `ScrollAwareRangeEffect`. So **the scroll-aware Y range — the fork's flagship
feature — will not run under Layoutlib either.**

### What this means for the parity plan

- **Static surfaces golden correctly.** The snapshot cards use `CartesianLayerRangeProvider.fixed`, so
  what they draw does not depend on the effect. They cover rendering, interpolation, axes, separators,
  point shapes, single-layer compositing and both themes — most of the silent-change list.
- **The dashboard charts cannot be goldened as-is.** Their Y range arrives through the effect. A golden
  taken of `rememberProductChart` would capture a pre-settle frame and would *not* regress if the
  scroll-aware range broke.
- Three options, to decide before expanding the set: (a) golden the dashboard chart with an explicitly
  seeded fixed range, accepting that it tests drawing and not ranging; (b) cover scroll-aware ranging
  with an instrumented test on a device instead; (c) both, with the split stated so nobody reads
  green goldens as proof the range provider works.

**Recorded here because the whole plan treats T0 goldens as the gate on migration work.** They are still
the right gate — but they gate *drawing*, not *settling*, and the distinction has to be explicit or the
parity checkpoint will be read as stronger evidence than it is.
