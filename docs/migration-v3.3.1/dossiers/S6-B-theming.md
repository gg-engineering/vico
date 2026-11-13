# S6-B — vico theming (`VicoTheme`, `compose-m3`) and what meApp should do with it

Scope: what `VicoTheme` controls at v3.3.1, which components read it, what meApp does today, and
whether meApp should adopt it. Read-only; no working tree touched.

Sources: `vico-migration` at tag `v3.3.1` (= `3d675ccc7`), the fork at
`MOB-2953-week-range-spans-eight-days` (= `73e068afb`, "MOB-3494: 4.0.46"), and
`meApp-bug/Android` as read on 2026-11-13.

**Headline: the recommendation is "keep doing what you do", with one one-word bug fix.** The
evidence is a pixel measurement of meApp's own committed goldens, below.

---

## 1. `VicoTheme` and the M3 bridge at v3.3.1

### 1.1 The declaration

`vico/compose/src/commonMain/kotlin/com/patrykandpatrick/vico/compose/common/VicoTheme.kt:44-51`:

```kt
public data class VicoTheme(
  val candlestickCartesianLayerColors: CandlestickCartesianLayerColors,
  val columnCartesianLayerColors: List<Color>,
  val lineCartesianLayerColors: List<Color> = columnCartesianLayerColors,
  val pieChartColors: List<Color> = columnCartesianLayerColors,
  val lineColor: Color,
  val textColor: Color,
)
```

**That is the whole thing. Six colour fields, nothing else.** No typography, no thickness, no shape,
no spacing, no per-axis distinction. `lineColor` is one colour for *all* axis lines, ticks,
guidelines and separators on every axis; `textColor` is one `Color` (not a `TextStyle`) for all axis
labels.

Nested (`:59-74`):

```kt
public data class CandlestickCartesianLayerColors(
  val bullish: Color, val neutral: Color, val bearish: Color,
)
```

### 1.2 How it is provided and read — composition local, not a parameter

`VicoTheme.kt:93-103`:

```kt
private val LocalVicoTheme = staticCompositionLocalOf<VicoTheme?> { null }

/** The current [VicoTheme]. */
public val vicoTheme: VicoTheme
  @Composable get() = LocalVicoTheme.current ?: if (isSystemInDarkTheme()) Dark else Light

/** Provides a [VicoTheme]. */
@Composable
public fun ProvideVicoTheme(theme: VicoTheme, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalVicoTheme provides theme, content)
}
```

Composition local only — there is no `theme` parameter on `CartesianChartHost` or
`rememberCartesianChart`. The local is **private**; `ProvideVicoTheme` is the only way to set it and
`vicoTheme` the only way to read it.

Three consequences that matter:

- **The local is `staticCompositionLocalOf`.** Reads are free (no recomposition subscription), but a
  *change* to the provided value recomposes the entire subtree under `ProvideVicoTheme`
  unconditionally. A consumer that builds a `VicoTheme` without `remember`ing it recomposes its whole
  chart subtree on every recomposition of the provider. This is why both bridges wrap the
  construction in `remember` (`compose-m3/.../VicoTheme.kt:38-45`).
- **The fallback is `isSystemInDarkTheme()`**, evaluated per read, not a separate opt-in. See §4.4.
- **`Light` / `Dark` are `internal`** (`VicoTheme.kt:76-79`), so a consumer cannot name vico's own
  defaults and `copy()` one field of them. To change one colour you must construct all six.

### 1.3 The built-in defaults

`Defaults.kt:75-104` (`@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public class DefaultColors`):

| | Light | Dark |
|---|---|---|
| `bullishCandleColor` | `0xff0ac285` | `0xff0ac285` |
| `neutralCandleColor` | `0xff000000` | `0xffffffff` |
| `bearishCandleColor` | `0xffe8304f` | `0xffe8304f` |
| `cartesianLayerColors` | `[0xff3287ff, 0xff0ac285, 0xffffab02]` | same |
| **`lineColor`** | **`0xffbcbfc2`** | **`0xff494c50`** |
| `textColor` | `0xff000000` | `0xffffffff` |

`VicoTheme.Companion.fromDefaultColors` (`VicoTheme.kt:81-89`) maps these onto the six fields.

**`#bcbfc2` is the number that matters** — see §3.3.

### 1.4 `compose-m3` — the whole module

`vico/compose-m3` at v3.3.1 contains exactly two files: `build.gradle.kts` and
`src/commonMain/kotlin/com/patrykandpatrick/vico/compose/m3/common/VicoTheme.kt`. Its entire public
API is one function.

`compose-m3/.../VicoTheme.kt:26-54` — **the exact entry point and the exact mapping**:

```kt
/** Creates and remembers a [VicoTheme] based on [MaterialTheme.colorScheme]. */
@Composable
public fun rememberM3VicoTheme(
  candlestickCartesianLayerColors: VicoTheme.CandlestickCartesianLayerColors =
    VicoTheme.CandlestickCartesianLayerColors.fromDefaultColors(getDefaultColors()),
  columnCartesianLayerColors: List<Color> =
    MaterialTheme.colorScheme.run { listOf(primary, secondary, tertiary) },
  lineCartesianLayerColors: List<Color> = columnCartesianLayerColors,
  pieChartColors: List<Color> = columnCartesianLayerColors,
  lineColor: Color = MaterialTheme.colorScheme.outline,
  textColor: Color = MaterialTheme.colorScheme.onBackground,
): VicoTheme = remember(/* all six */) { VicoTheme(/* all six */) }
```

**The bridge reads exactly five M3 `ColorScheme` roles:**

| vico role | M3 role |
|---|---|
| `columnCartesianLayerColors` / `lineCartesianLayerColors` / `pieChartColors` | `primary`, `secondary`, `tertiary` |
| `lineColor` (every axis line, tick, guideline, separator) | `outline` |
| `textColor` (every axis label) | `onBackground` |
| `candlestickCartesianLayerColors` | *not from M3* — `DefaultColors` |

It returns the theme; the consumer still has to call `ProvideVicoTheme(rememberM3VicoTheme()) { ... }`
itself. There is no `M3VicoTheme { }` wrapper.

Note `getDefaultColors()` (`DefaultColors.kt:24-27`) is `@RestrictTo(LIBRARY_GROUP)`, so a consumer
writing that default by hand trips the `RestrictedApi` lint. See §5.

### 1.5 `compose-m2` and `compose-glance`, one line each

- **`compose-m2`** — same single-file shape; `rememberM2VicoTheme` reads Material **2**'s
  `MaterialTheme.colors` (`primary`/`secondary`, `onBackground.copy(alpha = 0.2f)` for `lineColor`).
  **Irrelevant to an M3 app** — meApp's `MeAppTheme` wraps `androidx.compose.material3.MaterialTheme`
  (`MeTheme.kt:208-213`), and compose-m2 would need the Material 2 artifact on the classpath.
- **`compose-glance`** — two files, `CartesianChartImage` / `PieChartImage`: renders a chart to a
  bitmap for a Jetpack **Glance app widget**. **Irrelevant** unless meApp ships a home-screen widget;
  it does not.

Upstream's entire prose on both is one sentence, `guide/getting-started.md:18`:

> Add only the modules you need. `compose-m2` and `compose-m3` provide Material 2 and Material 3
> theming, respectively; `compose-glance` provides chart-image composables for Jetpack Glance app
> widgets.

The guide page for the theme itself (`guide/common/vicotheme.md`, mirrored at
`patrykandpatrick.com/vico/wiki/`) adds only:

> `VicoTheme` houses default chart colors. Functions like `rememberColumnCartesianLayer` and
> `rememberLineCartesianLayer` use these for their **default arguments**. You can use
> `ProvideVicoTheme` to provide a custom instance.

(The hosted wiki renders client-side and returns no body text to a fetch; the in-repo file at the tag
is the authority quoted here.)

---

## 2. Which components read the theme vs take parameters

**This is the crux, and the answer is stark: nothing reads `vicoTheme` at draw time. Every read is a
Kotlin default-argument expression in a `remember*` builder, evaluated only when the caller omits
that argument.**

The complete set of reads at v3.3.1 — `git grep -n vicoTheme` over `vico/compose`, eight files, all
of them default-argument positions:

| file:line | parameter | default |
|---|---|---|
| `cartesian/axis/AxisComponents.kt:34` | `rememberAxisLabelComponent(style =)` | `TextStyle(color = vicoTheme.textColor, fontSize = Defaults.AXIS_LABEL_SIZE.sp)` |
| `cartesian/axis/AxisComponents.kt:49` | `rememberAxisLineComponent(fill =)` | `Fill(vicoTheme.lineColor)` |
| `cartesian/axis/AxisComponents.kt:62` | `rememberAxisTickComponent(fill =)` | `Fill(vicoTheme.lineColor)` |
| `cartesian/axis/AxisComponents.kt:75` | `rememberAxisGuidelineComponent(fill =)` | `Fill(vicoTheme.lineColor)` |
| `cartesian/layer/Candle.kt:68,69,70,82,85,89,95,98,102` | candle colours | `vicoTheme.candlestickCartesianLayerColors.*` |
| `cartesian/layer/ColumnCartesianLayer.kt:701` | `rememberColumnCartesianLayer(columnProvider =)` | `vicoTheme.columnCartesianLayerColors.map { ... }` |
| `cartesian/layer/LineCartesianLayer.kt:1065` | `rememberLineCartesianLayer(lineProvider =)` | `vicoTheme.lineCartesianLayerColors.map { ... }` |
| `cartesian/layer/LineCartesianLayer.kt:1113, 1155` | `LineCartesianLayer.rememberLine(fill =)` | `vicoTheme.lineCartesianLayerColors.first()` |
| `pie/PieChart.kt:727` | pie slices | `vicoTheme.pieChartColors.map { ... }` |

Everything else — `TextComponent`, `LineComponent`, `ShapeComponent`, markers, decorations, fading
edges, points, area fills, strokes, the goal-pill — **takes an explicit colour and never consults the
theme.** `DefaultCartesianMarker` has no themed default anywhere.

So the practical reach of `VicoTheme` for a line-chart app is:

- **`lineColor`** — the default `fill` of the three axis component aliases, *if* you omit `fill`.
- **`textColor`** — the default *colour inside a default `TextStyle*`* for axis labels, *if* you omit
  `style` entirely. You cannot keep the theme's colour and set your own font: `style` is one
  parameter.
- **`lineCartesianLayerColors`** — the default series palette, *if* you omit `lineProvider` / `fill`.
- The other three fields are candlestick, column and pie. meApp draws none of those.

**A theme buys exactly as much as the number of arguments you are willing to stop passing.**

### 2.1 Recomposition and the `remember` key lists

`vicoTheme` is not a key of anything. It cannot be: it is consumed before the key list is built.

`rememberCartesianChart` (`cartesian/CartesianChart.kt:692-775`) keys on
`*layers, startAxis, topAxis, endAxis, bottomAxis, marker, markerVisibilityListener, layerPadding,
legend, fadingEdges, decorations, persistentMarkers, getXStep, markerController`.
`CartesianChart.equals` (`CartesianChart.kt:619-636`) compares the same set plus `id`.

The theme reaches those keys *indirectly*: change the provided `VicoTheme` → the default-argument
expression yields a different `Fill` → `rememberLineComponent`'s key changes → a new `LineComponent`
→ a new `Axis` → `CartesianChart.equals` is false → `rememberCartesianChart` rebuilds the chart →
the host's range-seeding `remember`, keyed on chart equality, re-seeds. That is the MOB-3494 defect
shape (SYNTHESIS §5, risk 1) — but it only fires on a theme flip, which already rebuilds the world,
so it is not a new hazard. It *would* become one if a consumer provided an unremembered `VicoTheme`,
since `staticCompositionLocalOf` makes every such change a full subtree recomposition.

### 2.2 `@RestrictTo` / `internal` in the theming path

| symbol | visibility | does a consumer hit it? |
|---|---|---|
| `LocalVicoTheme` | `private` | no — unreachable, use `ProvideVicoTheme` |
| `VicoTheme.Companion` (`Light`, `Dark`, `fromDefaultColors`) | `internal` | **yes, as a wall** — you cannot start from vico's defaults and override one field |
| `VicoTheme.CandlestickCartesianLayerColors.Companion` | `@RestrictTo(LIBRARY_GROUP)` | yes, if you copy `rememberM3VicoTheme`'s default |
| `DefaultColors`, `DefaultColors.Light/.Dark` | `@RestrictTo(LIBRARY_GROUP)` | same |
| `getDefaultColors()` | `@RestrictTo(LIBRARY_GROUP)` | same |
| `VicoTheme` constructor, `vicoTheme`, `ProvideVicoTheme` | `public` | clean |

Net: constructing a `VicoTheme` with all six fields spelled out is lint-clean. Constructing one that
*reuses* any vico default is not, and needs the `@SuppressLint("RestrictedApi")` meApp already
applies at `PrimaryLayer.kt:29` and `ProductChart.kt:34`.

---

## 3. meApp's current practice — the audit

### 3.1 Does meApp ever set a `VicoTheme`?

**No.** `grep -rn "VicoTheme\|vicoTheme" app/src` returns **zero hits** across the whole module.
meApp has never called `ProvideVicoTheme`, never imported `VicoTheme`, and does not depend on
`compose-m3`: `app/build.gradle.kts:301` is `implementation(libs.vico.gg)` and nothing else;
`libs.versions.toml:76` resolves that to `com.dmdbrands.lib:compose-android` at `4.0.46`, i.e. the
fork's `:vico:compose` alone.

Every chart colour in meApp reaches vico as an explicit argument, from `MeTheme.colorScheme`.

### 3.2 The threading inventory

**24 files import vico; 20 of the chart files' call sites construct a vico component.** Grouped by
the value threaded in — file:line against `app/src/main/java/com/dmdbrands/gurus/weight/`:

| # | Role | Token | Sites |
|---|---|---|---|
| A | **Axis line** (`rememberAxisLineComponent`, always with `thickness = MeTheme.strokeWidth.s`) | `iconDisabled` | `features/common/components/chart/axis/Horizontalxis.kt:26-29` (top), `:71-74` (segmented bottom), `:106-109` (total bottom); `axis/VerticalAxis.kt:60-63` (start), `:96-99` (end) — **5 sites, one value, verbatim** |
| B | Bottom guideline + separator line | `textDisabled` | `Horizontalxis.kt:62-65`, `:77-80` |
| C | End-axis guideline | `chartGrid` | `VerticalAxis.kt:102-105` |
| D | TOTAL axis hidden label + tick | `Color.Transparent` | `Horizontalxis.kt:99-101`, `:103-105` |
| E | **Axis / marker label**, `MeTheme.typography.labelMediumRegular.copy(color = MeTheme.colorScheme.textSecondary)` | `textSecondary` | `Horizontalxis.kt:66-68`; `VerticalAxis.kt:107-109`; `GraphMarker.kt:55-56`; `GraphMarker.kt:128-130` — **4 sites, one expression, verbatim** |
| F | Marker crosshair guideline | `textPrimary` | `GraphMarker.kt:132-135` |
| G | Marker indicator | the series colour, from the lambda | `GraphMarker.kt:143-150` |
| H | Data-line point | the series colour | `layer/DataLine.kt:47-51`; `PrimaryLayer.kt:53-57` |
| I | Goal pill (fill + label) | `progressGreen`, `surface` | `GraphDecoration.kt:36`, `:38-39`, `:42-45` |
| **J** | **nothing passed — falls through to `vicoTheme.lineColor`** | **none** | **`Horizontalxis.kt:69` — `tick = rememberAxisGuidelineComponent(),`** |

Colour *sources* (chosen once, then handed down as data, not as components):

- `ChartColors.kt:18-27` — 5 product roles (`metricWeight`, `metricBp`, `metricBaby`,
  `chartReference`, `progressGreen`).
- `config/ChartConfig.kt:59-67` — `primaryColor` = `metricWeight`, `secondaryColor` =
  `buttonPrimary`, `diastolicFallback` = `textSecondary`, `babyColor`, `percentileBand`.
- `dashboard/snapshot/components/ProductSnapshotCard.kt:204, 308-309, 410-411` — the snapshot card's
  per-product `lineColor` / `lineColors` / `secondaryLayerColor`.
- `BpHeaderValues.kt:145` — `metricPulse`.

### 3.3 The one place vico's own palette reaches a pixel — measured

`Horizontalxis.kt:69` is the **only** bare vico builder in the whole app. Tree-wide:

```
$ grep -rn "remember[A-Za-z]*Component()" app/src
app/src/main/java/.../chart/axis/Horizontalxis.kt:69:    tick = rememberAxisGuidelineComponent(),
```

It is on `segmentedBottomAxis`, i.e. every WEEK / MONTH / YEAR chart on every surface, drawn at
`tickLength = SeparatorTickLength` (16 dp). It resolves to `Fill(vicoTheme.lineColor)` —
**`#bcbfc2` light / `#494c50` dark**, colours in no meApp palette.

meApp's six committed chart goldens prove it is drawn. Decoding
`app/src/screenshotTestDefaultDebug/reference/com/dmdbrands/gurus/weight/chart/golden/VicoChartGoldenKt/*.png`
and counting exact RGB triples:

```
BabyWeekDark   #bcbfc2 × 304      BabyWeekLight   #bcbfc2 × 305
BpWeekDark     #bcbfc2 × 310      BpWeekLight     #bcbfc2 × 310
WeightWeekDark #bcbfc2 × 310      WeightWeekLight #bcbfc2 × 310
```

In `WeightWeekDark` (945 × 601 px) the `#bcbfc2` pixels sit in ten columns at
x = 136, 249, 361/362, 474/475, 587/588, 700/701, 814/815 — the seven day positions — each ~30 px
tall, below the bottom axis line (which the same image draws at y = 552/553 in `#4c5656`). Everything
else in that image is tokened and correct: `#4c5656` = `iconDisabled`/`textDisabled` (start-axis line
x 23/24, end-axis line x 813/814, the seven bottom guidelines, the axis line), `#404848` =
`chartGrid` (four horizontal gridlines at y 67, 190, 311, 432), `#3f97ed` = `metricWeight` dark.

**Two defects in one measurement:**

1. The tick is a hardcoded non-token grey, in a codebase whose `CLAUDE.md:151` says
   *"**Never** use hardcoded colors… Always use theme tokens"*. It is hidden behind a default
   argument, so no reviewer and no lint sees it.
2. **Its pixel count and bounding box are identical in the light and the dark golden** (310 and 310,
   same ten columns, same rows). It is rendering `DefaultColors.**Light**.lineColor` in the dark
   golden — `isSystemInDarkTheme()` is returning `false` under `MeAppTheme(ThemeMode.DARK)` in the
   Layoutlib screenshot renderer. See §4.4 and UNRESOLVED U2.

### 3.4 Repetition, quantified

| repeated expression | occurrences |
|---|---|
| `rememberAxisLineComponent(thickness = MeTheme.strokeWidth.s, fill = Fill(MeTheme.colorScheme.iconDisabled))` | **5**, character-identical |
| `MeTheme.typography.labelMediumRegular.copy(color = MeTheme.colorScheme.textSecondary)` as a vico `style` | **4**, character-identical |
| `thickness = MeTheme.strokeWidth.s` on a vico component | **9** (`Horizontalxis.kt:27, 63, 72, 78, 107`; `VerticalAxis.kt:61, 97, 103`; `GraphMarker.kt:133`) |
| the data-line recipe (`ChartLineThickness` + `Interpolator.monotone()` + a `CircleShape` point at `strokeWidth.none`) | **2** independent copies (`DataLine.kt:40-55`, `PrimaryLayer.kt:47-61`) |

### 3.5 Remaining drift of the recorded shape

The snapshot-card-vs-dashboard axis drift the dossiers record is **closed**: both now call the shared
`topAxis()` / `startAxis()` / `endAxis()` / `bottomAxis()` (`ProductChart.kt:89-96` vs
`SnapshotLineChart.kt:138-146`), and both build the plotted line through the same
`rememberDataLines` (`ProductChart.kt:263`, `SnapshotLineChart.kt:113`). Four residues of the same
shape are still live:

1. **`PrimaryLayer.kt:44-61` is a second line builder.** `rememberLineLayerWithConnection` constructs
   its own `rememberLine` inline instead of calling `rememberDataLines`. Its values — `ChartLineThickness`,
   `monotone()`, `pointSize = if (TOTAL) 4f else 8f`, `strokeWidth.none` — are *today* identical to
   `DataLine.kt:24-25, 39-55`. Its one and only consumer is `secondaryLayer` (`PrimaryLayer.kt:92`),
   whose one and only consumer is `ProductChart.kt:320`. So the weight chart's secondary (metric
   overlay) line is the one line in the app that will not follow a change to `rememberDataLines`.
   This is the pre-drift state of the exact defect already fixed once.
2. **`PrimaryLayer.kt:84` — `lineColor: Color = MeTheme.colorScheme.buttonPrimary`** is a dead
   default: `ProductChart.kt:322` always passes `config.secondaryLineColor`, which
   `ChartConfig.kt:60` sets to… `MeTheme.colorScheme.buttonPrimary`. The same token declared twice,
   in two files, one of them unreachable.
3. **`SnapshotLineChart.kt:83-85`** computes `lineStyle = iconDisabled`, `gridColor = chartGrid`,
   `labelColor = textSecondary` and **uses none of them**. They are the tokens the card's now-deleted
   private axes used; they survived the merge as dead locals and read as a second opinion on the
   axis palette.
4. **Two gridline tokens on one chart**: the bottom-axis guideline is `textDisabled`
   (`Horizontalxis.kt:64`) and the end-axis guideline is `chartGrid` (`VerticalAxis.kt:104`). In
   light these are the same pixel (`textDisabled`, `chartGrid`, `iconDisabled` and `borderDark` are
   **all** `neutral200 #D0CCCA`, `ColorTokens.kt:58, 62, 66, 89`), so the difference is invisible and
   untested. In dark they diverge — `#4C5656` vs `#404848` — and the dark golden shows both.
   Possibly deliberate; see U3.

Adjacent but **not** vico-threaded, so out of any theme's reach either way:
`GraphSkeletonView.kt:55` and `HeaderSkeletonView.kt:45` draw their shimmer grid on a plain Compose
`Canvas` in `textSecondary` at alpha 0.08–0.15, where the real chart's grid is `chartGrid`.
`GraphScrollHintModal.kt:271-274` is likewise a hand-drawn Canvas illustration.

---

## 4. Recommendation

### 4.1 Should meApp provide a `VicoTheme` derived from `MeTheme`? — **No.**

**The components meApp uses take explicit parameters anyway, and the one exception is a bug, not a
theming gap.**

Field by field, against §2's table:

| `VicoTheme` field | what meApp would set it to | what it would actually save |
|---|---|---|
| `lineColor` | `MeTheme.colorScheme.iconDisabled` | the `fill =` argument on the 5 group-A sites. **But all 5 must still pass `thickness = MeTheme.strokeWidth.s`**, which `VicoTheme` has no slot for — so all 5 call sites survive, one argument shorter. It would also silently re-colour `Horizontalxis.kt:69` and `:62-65`/`:77-80` (which want `textDisabled`, a *different* token) if those were ever un-specified. |
| `textColor` | `MeTheme.colorScheme.textSecondary` | **nothing.** All 4 group-E sites pass a full `TextStyle` (Open Sans, `labelMediumRegular`) because vico's default style is the platform font at `Defaults.AXIS_LABEL_SIZE`. `style` is one parameter: you cannot take the theme's colour and your own font. The `.copy(color = …)` would be written regardless. |
| `lineCartesianLayerColors` | — | **nothing, and it is the wrong shape.** meApp's series colours are per-product and per-reading (`ChartConfig.kt:74, 86, 95` — weight is 1 colour, BP is 3 of which 2 are a *severity-classified* colour recomputed from the focused reading, baby is 1 plus a 7-curve band). A fixed list on a composition local cannot express that; it is handed down as `config.lineColors` and must be. |
| `columnCartesianLayerColors`, `pieChartColors`, `candlestickCartesianLayerColors` | — | nothing. meApp draws no column, pie or candlestick layer. These three would be filler. |

**Call sites that get simpler: 5 of 20, and none of them disappears.** Against that:

- a new `ProvideVicoTheme` wrapper to place and keep placed (there are two independent chart entry
  points, `ProductChart` and `SnapshotLineChart`, plus `VicoChartGolden.kt`, and any surface that
  forgets it silently gets vico's grey);
- a `remember` discipline the `staticCompositionLocalOf` enforces harshly (§1.2);
- a second place a chart colour can be set, which is precisely the thing `ChartColors.kt:10-15`
  records as having gone wrong before ("they lived inside the snapshot card while the shared chart
  config read them, so the chart layer depended on one of its own callers").

**What to do instead** — a meApp-local `ChartComponents.kt` holding three thin `@Composable`
wrappers (`chartAxisLine()`, `chartGuideline()`, `chartAxisLabel()`) that bind colour **and**
thickness **and** typography together. That collapses groups A + B + C + E — **11 of the 20 sites**,
against VicoTheme's 5 — fixes `Horizontalxis.kt:69` by construction, is pure consumer code, and costs
the fork nothing. *The 11 is a count of call sites, not of saved lines: each wrapper still needs
per-site arguments (`shape`, `margins`, `minWidth`, the `Color.Transparent` cases at group D), so the
net line saving is **unmeasured** and should be measured by writing one wrapper before committing to
three.*

**The minimum viable change, today, independent of the migration:** `Horizontalxis.kt:69` becomes

> `tick = rememberAxisGuidelineComponent(thickness = MeTheme.strokeWidth.s, fill = Fill(MeTheme.colorScheme.iconDisabled)),`

matching the three sibling calls in the same function. One line. It removes the only hardcoded colour
in the chart, and it will change all six goldens (~310 px each) — which is the point: that is the
regression those goldens exist to catch, and they caught it.

### 4.2 Should meApp adopt `vico/compose-m3`? — **No. The bridge maps five roles and gets five wrong.**

Concretely, `rememberM3VicoTheme` reads `primary`, `secondary`, `tertiary`, `outline`,
`onBackground`. meApp fills its M3 `ColorScheme` in `MeTheme.kt:257-278` (`materialColorsFrom`):

| M3 role the bridge reads | what meApp puts there | what the chart needs | verdict |
|---|---|---|---|
| `primary` | `ds.controlSelected` — *"Selection handles"* (`MeTheme.kt:259`); `neutral900 #2C2827` light, `brand100 #F5F3F0` dark | `metricWeight` / `metricBp` / `metricBaby`, per product | wrong role entirely |
| `secondary` | `ds.controlSelected` **again** (`MeTheme.kt:260`) | the 2nd series | wrong, and **identical to `primary`** — a 2-series chart would draw both series the same colour |
| `tertiary` | **not overridden.** `base.copy(...)` leaves Material's untouched baseline tertiary (a stock purple/pink nobody on this project picked) | the 3rd series (BP pulse = `metricPulse`) | wrong, and it is the one role that is not even a meApp token |
| `outline` | `ds.borderDark` (`MeTheme.kt:274`) | `iconDisabled` | same pixel in light (both `neutral200`) but **divergent in dark**: `borderDark` = `teal700 #4C5656`, `iconDisabled` = `teal700 #4C5656` — equal today, by coincidence of the token table, not by intent |
| `onBackground` | `ds.textPrimary` (`MeTheme.kt:264`) | `textSecondary` | wrong — `textPrimary` is `neutral900 #2C2827` light; axis labels are `neutral500 #7B726E` |

To make the bridge right, meApp would have to repurpose `primary` / `secondary` / `tertiary` as
chart-series slots. They are not that — they are the app-wide selection-handle and text roles, read by
every Material component on every screen. Changing them to fix the chart breaks everything else.

**Plus a delivery cost the mapping question hides.** `compose-m3` is a separate Gradle module and a
separate artifact. In the fork at 4.0.46 only `:vico:compose` carries the GitHub-Packages publishing
convention (`buildSrc/src/main/kotlin/gpr-publishing-convention.gradle.kts:26`, `artifactId =
"vico-gg"`); `vico/compose-m3/build.gradle.kts:21` applies upstream's `publishing-convention`
(Maven Central coordinates). So adopting it means adding a **second published artifact** to a release
process the ggBLE/vico notes record as manual and CI-less, for a mapping that is wrong on every role.

### 4.3 Does this reduce the fork, and does v3.3.1 change the theming API? — **No, and no.**

**`VicoTheme.kt` is byte-identical between the fork at 4.0.46 and upstream v3.3.1.**

```
$ git diff MOB-2953-week-range-spans-eight-days:vico/compose/.../common/VicoTheme.kt \
           v3.3.1:vico/compose/.../common/VicoTheme.kt
(no output)
```

So is the set of consumers. The fork's `vicoTheme` reads are at
`AxisComponents.kt:34, 49, 62, 75` — the **same four line numbers** as v3.3.1 — plus the same
`Candle.kt`, `ColumnCartesianLayer`, `LineCartesianLayer` and `PieChart` sites (at shifted line
numbers, from unrelated churn). `compose-m3` and `compose-m2` exist in both trees with the same
single-file shape.

Therefore:

- **No fork patch is a workaround for anything the theme could do properly.** None of the 87
  catalogued adaptations touches the theming path.
- **No adaptation row, no disposition, no porting work.** Theming is a genuine no-op for the
  migration — the one place this analysis set out to look for a reduction and found none.
- The migration does *not* change the colour of anything through theming. (It does change layout
  through the 64 dp axis cap, SYNTHESIS §5 — unrelated.)
- The `@RestrictTo` surface (§2.2) is unchanged too, so the existing `@SuppressLint("RestrictedApi")`
  at `PrimaryLayer.kt:29` / `ProductChart.kt:34` neither grows nor shrinks.

**Corollary for the fork-minimisation strategy:** adopting a `VicoTheme` would not let a single fork
line be deleted. The reduction available here is in *meApp*, and it is the wrapper file in §4.1, not
the theme.

### 4.4 Dark mode — a `VicoTheme` would **add** a second source of truth, and none is needed

meApp drives light/dark from its own `ThemeMode` and already republishes that decision where vico
reads it:

- `MeTheme.kt:128-132` — `resolveDarkTheme(themeMode)`: `DARK → true`, `LIGHT → false`,
  `SYSTEM/UNRECOGNIZED → isSystemInDarkTheme()`.
- `MeTheme.kt:172-184` — rebuilds `Configuration` from the live `LocalConfiguration` with the night
  bit flipped (`applyNightFlag`), wrapped in a `ThemedContextWrapper`.
- `MeTheme.kt:190-191` — **`LocalContext provides themedContext, LocalConfiguration provides
  themedContext.resources.configuration`.**

`isSystemInDarkTheme()` reads `LocalConfiguration.current.uiMode`. So vico's fallback
(`VicoTheme.kt:97`) **already follows meApp's `ThemeMode`, not the OS** — the mode is in sync today,
for free, with no `VicoTheme` and nothing to maintain. This was built for MA-3996 (theme-aware
drawables following the in-app Appearance pick) and vico inherits it.

What is wrong is the *palette*, in one place (`Horizontalxis.kt:69`), not the *mode*.

Providing a `VicoTheme` would mean: a `MeTheme → VicoTheme` derivation that must be re-derived on
every mode flip, `remember`ed correctly or it recomposes the whole subtree (§1.2), provided at every
chart entry point, and then **golden-tested in both modes on top of the explicit parameters that
remain** — because 15 of the 20 sites would still pass their colour directly. Two mechanisms, two
sets of goldens, one palette.

**A correction to the migration record.** SYNTHESIS §6 states *"meApp has no visual regression
testing at all"* and schedules ~36 T0 goldens for P1. That is now **stale**:
`app/src/screenshotTest/kotlin/com/dmdbrands/gurus/weight/chart/golden/VicoChartGolden.kt` exists
(AGP Compose Screenshot Testing, `@PreviewTest` + `@Preview`), with **six committed references**
under `app/src/screenshotTestDefaultDebug/reference/...` — weight / BP / baby × light / dark, each
`MeAppTheme(ThemeMode.LIGHT)` or `MeAppTheme(ThemeMode.DARK)` at 360 × 229 dp, captured 2026-11-13.
P1's scope should be re-stated as *extend* (the dashboard `ProductChart` is not covered; only the
snapshot card is), not *create*. The mechanism for "goldens in both modes" already works.

**But those goldens carry a live caveat, and it is the §3.3 measurement.** The `#bcbfc2` pixel count
is identical in the light and dark references, which means `isSystemInDarkTheme()` returns `false`
inside `MeAppTheme(ThemeMode.DARK)` **under Layoutlib** — meApp's `createConfigurationContext` night
override apparently does not survive preview rendering, even though every `MeTheme.colorScheme.*`
read in the same image is correctly dark (`#3f97ed`, `#4c5656`, `#404848`). So the goldens currently
test meApp's tokens in dark **and vico's fallback in light**, in one image. Any future reliance on
the `vicoTheme` fallback — including a `VicoTheme` whose own construction branches on
`isSystemInDarkTheme()` — would be untested by exactly the goldens meant to test it. One more reason
to pass colours explicitly: an explicit `MeTheme` token is right in both renderers.

---

## 5. UNRESOLVED

**U1 — Does fixing `Horizontalxis.kt:69` change only the intended pixels?**
*Check:* make the one-line change in §4.1, run `./gradlew :app:validateDebugScreenshotTest`, and diff
the six references. Expect: `#bcbfc2` drops to 0 in all six; `#d0ccca` rises by ~310 in each light
image and `#4c5656` by ~310 in each dark image; no other colour's count moves. Anything else means
the tick is load-bearing for layout, not just colour.

**U2 — Why does `isSystemInDarkTheme()` return `false` under `MeAppTheme(ThemeMode.DARK)` in
Layoutlib, and does the same happen on a device?**
*Check:* add a throwaway `@Preview` that renders `Text(isSystemInDarkTheme().toString())` inside
`MeAppTheme(ThemeMode.DARK)`, and the same composable in a debug screen on the SM-G990E with
Appearance forced to Dark while the OS is Light. If the device says `true` and the preview says
`false`, it is a Layoutlib limitation (`createConfigurationContext` is a stub there) and only the
goldens are affected. If the device also says `false`, MA-3996's fix does not cover
`isSystemInDarkTheme()` and anything in the app relying on it under a manual override is wrong —
a broader defect than this dossier's scope.

**U3 — Is the bottom-guideline (`textDisabled`) / end-guideline (`chartGrid`) split deliberate?**
*Check:* the two are the same pixel in light (`neutral200`, `ColorTokens.kt:58, 62`) and differ in
dark (`#4C5656` vs `#404848`, `ColorTokens.kt:174, 178`). Open the dark golden and ask design whether
the vertical and horizontal gridlines are meant to differ; or compare against the iOS
`DashboardChartStyleProvider` that `ChartConfig.kt:16-19` says these colours mirror. If not
deliberate, it is a drift the light theme has been hiding.

**U4 — How much does the `ChartComponents.kt` wrapper actually save?**
*Unmeasured.* I counted 20 builder call sites and two expressions repeated 5× and 4×, but did not
write a wrapper to see what survives per site (`shape`, `margins`, `minWidth`,
`labelVerticalMode`, and the two `Color.Transparent` cases at `Horizontalxis.kt:99-105` all vary).
*Check:* write `chartAxisLine()` alone, convert the 5 group-A sites, count lines before and after.
If it does not clear ~2 lines per site, the other two wrappers are not worth writing.

**U5 — Does v3.3.1 add any *new* defaulted colour parameter to a component meApp constructs?**
This dossier establishes that the existing `vicoTheme` reads are unchanged, and that meApp has
exactly one bare builder today. It does **not** establish that v3.3.1 introduces no new themed
default on `TextComponent`, `LineComponent`, `ShapeComponent`, `DefaultCartesianMarker` or the axis
builders that meApp currently omits.
*Check:* after the port, re-run `grep -rn "remember[A-Za-z]*Component()" app/src` **and** diff each
builder's parameter list between the two refs
(`git diff MOB-2953…:<path> v3.3.1:<path>` for `AxisComponents.kt`, `component/Components.kt`,
`marker/DefaultCartesianMarker.kt`) looking for added parameters whose default mentions `vicoTheme`.
A new themed default on a parameter meApp omits is a silent colour change no compiler will report —
the goldens in §4.4 are the only thing that would catch it, and only on the snapshot card.

**U6 — Is the `PrimaryLayer.kt` / `DataLine.kt` duplicate (§3.5-1) worth collapsing now or after
parity?**
*Check:* it is consumer code with one call path (`ProductChart.kt:320 → secondaryLayer →
rememberLineLayerWithConnection`), so it is independent of the migration — but
`rememberLineLayerWithConnection` also carries `yTransform` and `alwaysUseLiveRange`, two of the
fork's own additions that P8/P10 will move. Confirm whether those parameters survive the port before
merging the two builders, or the merge will have to be undone.
