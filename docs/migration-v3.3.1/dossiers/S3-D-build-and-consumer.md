# S3-D — Build, publishing, consumer impact, verification harness, dev workflow

Scope: the **build and publishing half** of the fork rebase onto `v3.3.1`, the consumer (meApp)
impact, the visual-regression harness, and how the migration is developed without disturbing the
shared checkout.

All refs read with `git show <ref>:<path>` / `git diff`. Nothing in either working tree was modified.
Refs per `BASELINE.md`: `FORK_POINT = e67e49520`, `OURS = HEAD` (`73e068afb`, branch
`MOB-2953-week-range-spans-eight-days`, published as `4.0.46`), `TARGET = v3.3.1` (`3d675ccc7`).

Conventions used below: **DO** = an action with a decision attached; **UNRESOLVED** = cannot be
established by reading, with the exact check that settles it.

---

# PART 1 — The fork's build and publishing, rebased onto v3.3.1

## 1.0 The shape of the answer

The live publishing configuration is **three edits to two upstream files**, plus one deletion. Every
other build-side difference between `HEAD` and `v3.3.1` is upstream's, and should be taken verbatim.
Confirmed by `git diff HEAD v3.3.1 -- vico/build.gradle.kts buildSrc/ gradle/ settings.gradle.kts
vico/compose/build.gradle.kts` — the full diff is small enough to enumerate, and is enumerated below.

The three live edits are:
1. `subprojects { group = "com.dmdbrands.lib" }` in `vico/build.gradle.kts:20`.
2. `` `publishing-convention` `` → `` `maven-publish` `` in `vico/compose/build.gradle.kts`'s plugins block.
3. The GPR `publishing { repositories { maven { … } } }` block appended to `vico/compose/build.gradle.kts`.

Everything else in the fork's build — B-4 through B-7, the Kotlin pin, the sample chart, the
`:vico:views` dokka entry — is either dead, abandoned, or should be dropped.

## 1.1 The change list

### B-1. `group = "com.dmdbrands.lib"` — RE-APPLY (one line, onto upstream's file)

**Do.** Take `v3.3.1:vico/build.gradle.kts` wholesale; change `group = "com.patrykandpatrick.vico"`
back to `"com.dmdbrands.lib"`. Take nothing else from our copy.

**Why.** Our copy of that file also carries the stale `dokka(project(":vico:views"))` entry
(B-12 below). Upstream's version already has `dokka(project(":vico:compose-glance"))` and the
`java.time.Year` footer. Our only real change there is the one group line.

**Evidence.** `git diff HEAD v3.3.1 -- vico/build.gradle.kts` is exactly four hunks: the `Year`
import, `group`, the `dokka` list (`+compose-glance`, `-views`), and the footer string.

**Consequence to record.** `moduleNamespace = "${group}.${project.name.replace("-", ".")}"`
(`buildSrc/src/main/kotlin/Project.kt`, unchanged in range) means the Android namespace stays
`com.dmdbrands.lib.compose` while Kotlin packages stay `com.patrykandpatrick.vico.compose.*`. The
coordinate `com.dmdbrands.lib:compose-android` is stable across the migration — meApp's catalogue
module line does not change, only its version.

### B-12. `dokka(project(":vico:views"))` — DELETED BY TAKING UPSTREAM'S FILE

**Do.** Nothing beyond B-1. `:vico:views` was deleted upstream in `55cdd2b3b`; `:vico:compose-glance`
was added in `f2923084f`. Upstream's file already lists the right four modules.

**Why it matters.** If our file is taken wholesale, Gradle **configuration** fails — not just
`dokkaGenerate` — on a `project(":vico:views")` that `settings.gradle.kts` no longer includes.

**Evidence.** `git diff HEAD v3.3.1 -- settings.gradle.kts`: `-"vico:views"`, `+"vico:compose-glance"`,
and the sample list changes to `sample:android, sample:desktop, sample:shared, sample:web`.
We never modified `settings.gradle.kts` — take it verbatim.

### B-3. Publishing plugin swap + GPR repository — RE-APPLY BY HAND onto upstream's file

Upstream's `v3.3.1:vico/compose/build.gradle.kts` and ours differ in exactly five places. Three are
upstream's and are taken as-is; two are ours and are re-applied.

| Line region | `HEAD` | `v3.3.1` | Action |
|---|---|---|---|
| plugins | `` `maven-publish` `` (ours) | `` `publishing-convention` `` | **ours — re-apply** |
| plugins | `id("org.jetbrains.compose")` | `alias(libs.plugins.composeMultiplatform)` | take upstream |
| kotlin block | `androidLibrary { … }` | `android { … }` | take upstream |
| targets | `listOf(iosX64(), iosArm64(), iosSimulatorArm64())` | `listOf(iosArm64(), iosSimulatorArm64())` | take upstream |
| compilerOptions | `freeCompilerArgs.add("-Xannotation-default-target=param-property")` | *(removed)* | take upstream |
| end of file | GPR `publishing { repositories { … } }` + `java.util.Properties` import + `localProps` loading | *(absent)* | **ours — re-apply** |

**Do.** Start from `v3.3.1:vico/compose/build.gradle.kts`. Replace `` `publishing-convention` `` with
`` `maven-publish` ``. Re-add `import java.util.Properties` and the final block verbatim from
`HEAD:vico/compose/build.gradle.kts:85–108`.

**Why the plugin swap, not both.** `publishing-convention` applies vanniktech
`com.vanniktech.maven.publish`, which calls `signAllPublications()` and targets Maven Central.
We have no GPG signing keys for a local publish, and we do not publish to Central. Keeping both
would make every local publish fail at signing. Confirmed byte-identical POM metadata upstream
(S2-A §4.1), so nothing is lost by dropping it.

**Why `-Xannotation-default-target` goes.** Removed upstream in `336552cc7` because Kotlin 2.4 made
`param-property` the default. Keeping the flag with Kotlin 2.4.10 is redundant; keeping it with our
2.1.21 pin is only meaningful because of that pin, which we are also dropping (B-9).

**Note on `iosX64`.** Dropping it stops producing `com.dmdbrands.lib:compose-iosx64` (6 versions in
GPR today). meApp consumes only `compose-android`. Record it so nobody reports the missing package
as a regression; do not re-add the target.

### B-4. `gpr-publishing-convention.gradle.kts` — DELETE

**Decision: delete the file.** Not wire it up.

**Why.** It is applied by nothing. `git grep -n 'gpr-publishing\|gpr' HEAD -- '*.gradle.kts'` returns
only the file's own body and the live block in `vico/compose/build.gradle.kts` — no `plugins {}`
block anywhere names it. It has been dead since `c961c9eba` (4.0.6) moved the GPR block into
`vico/compose/build.gradle.kts`. Its contents are actively harmful as a reference: a hand-rolled
`MavenPublication("gpr")` at the abandoned coordinate `com.dmdbrands.lib:vico-gg`, POM
dependency-stripping logic, and signing-disable logic. Reinstating any of it re-opens the 4.0.4→4.0.8
dead end (S1-E §2.2).

**Why not wire it up.** The only argument for a convention plugin is reuse across modules, and there
is exactly one module we publish (`:vico:compose`). A convention plugin for one consumer is
indirection that cost us five failed releases once already.

### B-5/6/7. POM stripping, `.module` suppression, AAR-direct — DO NOT REINSTATE

**What actually ships today**, verified from the Gradle cache at
`~/.gradle/caches/modules-2/files-2.1/com.dmdbrands.lib/compose-android/4.0.46/` (S1-E §2.1):

- Standard KMP publications, produced by the AGP KMP plugin
  (`com.android.kotlin.multiplatform.library`) — **no** hand-wired `artifact(...)`.
- Six publications: `compose` (root metadata), `compose-android`, `compose-desktop`,
  `compose-iosarm64`, `compose-iossimulatorarm64`, `compose-iosx64`.
- `compose-android-4.0.46.pom`: `packaging=aar`, carries the
  `do_not_remove: published-with-gradle-metadata` marker, declares **all six** runtime-scope
  dependencies (`androidx.annotation:annotation-jvm:1.9.1`, the three
  `org.jetbrains.compose.*:1.10.3`, `kotlinx-coroutines-core-jvm:1.10.2`, `kotlin-stdlib:2.1.21`).
  Nothing is stripped.
- `compose-android-4.0.46.module` **is** published; `GenerateModuleMetadata` is not disabled.
- A sources jar is published. Signing is not configured.

**Do.** Confirm the same set after the first migrated publish and move on. The transitive deps
resolve correctly because Compose Multiplatform's `org.jetbrains.compose.*` artifacts carry Gradle
metadata that redirects to AndroidX on an Android target; meApp declares no exclusions or resolution
strategy against them (S1-E §2.2 item 7). The only change the migration makes here is the *versions*
in the POM (Compose 1.10.3→1.12.0, coroutines 1.10.2→1.11.0, kotlin-stdlib 2.1.21→2.4.10,
androidx.annotation 1.9.1→1.10.0), all of which meApp already clears (S2-A toolchain floor table).

### B-9. Kotlin 2.1.21 pin — DROP

**Do.** Take `v3.3.1:gradle/libs.versions.toml`, `v3.3.1:gradle/wrapper/*`,
`v3.3.1:buildSrc/build.gradle.kts` and `v3.3.1:gradle.properties` verbatim. Do not carry `kotlin = "2.1.21"`.

**Why.** `57274083d` lowered `kotlin = "2.3.20"` → `"2.1.21"` with a commit title about a NaN crash
guard and no mention of Kotlin in the body — the downgrade is undocumented and incidental. The stated
motive ("match meApp") is **false today**: meApp's `Android/gradle/libs.versions.toml` is on
`kotlin = "2.4.21"`, `agp = "9.5.1"`, Gradle `9.8.1`. Moving the fork to v3.3.1's `kotlin = 2.4.10`
moves it *toward* its only consumer.

**What comes with it** (`git diff HEAD v3.3.1 -- gradle/libs.versions.toml`):
`agp 9.1.0→9.3.2`, `compose 1.10.3→1.12.0`, `coroutines 1.10.2→1.11.0`,
`lifecycleRuntime 2.10.0→2.11.0`, `mavenPublish 0.36.0→0.37.0`, `mockK 1.14.9→1.14.11`,
`jetpackComposeBom 2026.03.01→2026.08.00`, `androidXAnnotation 1.9.1→1.10.0`,
plus `Versions.COMPILE_SDK 36→37` (`buildSrc/src/main/kotlin/Versions.kt`) and Gradle wrapper
`9.4.1→9.7.1`.

**The catalogue entry that forces the plugin change.** Upstream deleted the `compose` *library*
alias (`org.jetbrains.compose:compose-gradle-plugin`) and `buildSrc/build.gradle.kts` dropped
`implementation(libs.compose)` / added `implementation(libs.kotlinJvm)`. The replacement is the new
`[plugins]` table: `composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose" }`
and `composeMultiplatformSample` (pinned to an alpha so the sample can use Material 3 Expressive
without dragging the library's published dependencies to an alpha). **A fork that still writes
`id("org.jetbrains.compose")` in `vico/compose/build.gradle.kts` while taking upstream's buildSrc
will fail to resolve the plugin** — hence `alias(libs.plugins.composeMultiplatform)` is not optional
cosmetics, it is part of the same change as dropping the pin.

**UNRESOLVED — context parameters.** `v3.3.1:vico/compose/.../Modifier.kt` uses
`context(pointerEventScope: AwaitPointerEventScope)` with no `-Xcontext-parameters` flag anywhere in
`v3.3.1:buildSrc`, `v3.3.1:vico`, `v3.3.1:gradle.properties`. Upstream's own CI builds it, so it is
presumably on by default at Kotlin 2.4.10, but this has not been observed.
**Check that settles it:** in the migration clone (Part 4), `./gradlew :vico:compose:compileKotlinAndroid`.
If it fails on the context receiver, add `freeCompilerArgs.add("-Xcontext-parameters")` and open an
upstream issue — do not downgrade Kotlin.

### B-11. `DmdBrandsTestChart.kt` and the four `sample/app` edits — DELETE, replace with previews

**Decision: delete the fork's sample code. Replace its purpose with `@Preview` + `@PreviewTest`
entries in upstream's new sample structure.**

**Why deletion is safe.** The sample **already does not compile against our own HEAD**. S1-A §7
found `DmdBrandsTestChart.kt:89` calls `Scroll.Absolute.xWithPadding(80.0, startPaddingXStep)` and
line 150 passes `scrollPaddingXStep = …` to `SnapBehaviorConfig` — both removed by `b323662e6` during
MOB-2953. We have been carrying a 270-line file that has not built for months. Its Gradle modules
(`sample:charts:compose`, `sample:app`) are both deleted at v3.3.1
(`git diff HEAD v3.3.1 -- settings.gradle.kts`), so "port it" means rewriting it anyway.

**Why "replace its purpose" rather than just drop it.** Its purpose — exercise our fork's APIs
outside meApp — is real, and losing it silently is worse than the port cost (S1-E §2.5 item 3). But
upstream has since built a *better* vehicle for exactly that, inside the fork range:
`8f2aa9e17 test: add sample chart screenshot tests` added
`sample/android/src/screenshotTest/kotlin/.../SampleChartScreenshotTests.kt` plus **11 committed
reference PNGs** under `sample/android/src/screenshotTestDebug/reference/…`, driven by the AGP
Compose Screenshot Testing plugin (`com.android.compose.screenshot`, catalogue `[plugins]` entry;
`android.experimental.enableScreenshotTest=true` in `gradle.properties`).

**Do.**
1. Delete `sample/charts/compose/.../DmdBrandsTestChart.kt` and revert the four one-to-three-line
   wiring edits in `sample/app/{Charts.kt, Charts.android.kt, Chart.kt, ChartScreen.kt}` by taking
   upstream's `sample/` tree whole.
2. Add a small number of fork-API previews to `sample/shared/src/commonMain/.../charts/` — one per
   fork feature that has no upstream equivalent (scroll-aware Y range, snap fling, scrub marker,
   start inset, `visibleLabelsCount`) — each with a `@PreviewTest @Preview` entry in
   `sample/android/src/screenshotTest/…`, following upstream's own file as the template.
3. Commit the reference PNGs the same way upstream does.

**Why this is the long-term fix.** It converts an unbuilt sample into a CI-checkable harness for the
exact 13 adaptations the fork exists for, using machinery upstream maintains. It is also the one
place the fork can prove its own API before meApp ever sees it.

### B-8. Git remote still says `dmdbrands` — RE-POINT

**Do.** `git remote set-url origin git@github.com:gg-engineering/vico.git`, and delete the dead
`Version-2.5.0` remote (`https://github.com/dmdbrands/vico.git`).

**Why.** `gh api repos/dmdbrands/vico` returns `full_name: gg-engineering/vico` — the **repo API
follows the org-rename redirect**, which is why fetch/push still work and nobody noticed. The **Maven
registry does not follow it**: that is precisely the 401 that `0eca6f8b5` fixed by changing both
publish URLs to `gg-engineering`. Redirects are a courtesy GitHub can withdraw, and the mismatch has
already cost one debugging session.

**Do not** rename the group `com.dmdbrands.lib` or the namespace `com.dmdbrands.lib.compose`. Both
carry the old org's name and both are cosmetic; renaming invalidates 37 published `compose-android`
versions and every `libs.versions.toml` entry in meApp for no functional gain.

Current state, for the record (`git remote -v` in `/Users/selvakumar/Projects/vico`):
```
Version-2.5.0  https://github.com/dmdbrands/vico.git
origin         git@github.com:dmdbrands/vico.git
upstream       https://github.com/patrykandpatrick/vico.git
```

### B-credentials. `gpr.token` vs `gpr.key` — DOCUMENT, DO NOT UNIFY

**Fact.** The fork reads `gpr.user` / **`gpr.token`** from `gradle-local.properties` at the vico repo
root (`HEAD:vico/compose/build.gradle.kts:97,101`). meApp reads `gpr.user` / **`gpr.key`** from
`local.properties` (`meApp-bug/Android/settings.gradle.kts:10–11`). Both fall back to
`GITHUB_USERNAME` / `GITHUB_TOKEN`.

**Do.** Record both spellings in the fork's README/AGENTS entry. Do **not** rename either: the fork's
`gpr.token` is read in one file and meApp's `gpr.key` in one file, both have live values on this
machine, and a rename silently degrades to the empty-string default — the fork's password fallback is
`?: ""`, which produces a 401 at publish time with no hint that a property name changed.

**Better, if one thing is unified:** set `GITHUB_USERNAME` / `GITHUB_TOKEN` in the shell profile.
Both repos already prefer or accept the env pair, so one setting serves both without touching either
file. (meApp prefers env over `local.properties`; the fork prefers `gradle-local.properties` over env
— so with both present the fork still uses the file. Note that asymmetry rather than "fix" it.)

### B-publish-task. The publish command — RECORD IT

**Fact.** No README, AGENTS, docs entry or CI job records how the fork is published. Upstream's
`.github/workflows/publish.yml` is unmodified and guarded by
`if: github.repository == 'patrykandpatrick/vico'`, so it never runs for us. Publishing is a manual
local Gradle invocation and is currently tribal knowledge.

**Derived, not observed.** The repository is declared with `name = "GitHubPackages"`, so Gradle's
`maven-publish` generates `publishAllPublicationsToGitHubPackagesRepository` on `:vico:compose`.

**UNRESOLVED — the exact task name and the full set of per-publication tasks.**
**Check that settles it:** in the migration clone, `./gradlew :vico:compose:tasks --all | grep -i publish`.
Do not run this in the shared checkout — it writes to `.gradle/`.

**Do, during the migration:** write the task into `docs/` and add a `workflow_dispatch` CI job that
runs it with `GITHUB_TOKEN`, so the next publish is not a local-machine-only capability.

### B-version. The fork's version number — **DECISION: `5.0.0`, plus a recorded upstream base**

**The problem.** `VICO = "4.0.46"` (`buildSrc/src/main/kotlin/Versions.kt:20`) against upstream's
`3.3.1`. Our 4.x line never tracked upstream's 3.x line; it started at 4.0.1 as a publishing
experiment and incremented 46 times, 11 of those as version-bump-only commits. Nothing in the
artifact says which upstream it is built from. That has already caused confusion.

**Decision.** Set `Versions.VICO = "5.0.0"` and add, in the same file:
```kotlin
object Versions {
  const val COMPILE_SDK = 37
  const val MIN_SDK = 23
  const val VICO = "5.0.0"
  /** The upstream Vico release this fork is rebased onto. */
  const val UPSTREAM_BASE = "3.3.1"
}
```
and stamp `UPSTREAM_BASE` into the published POM description and/or a `pom.properties` entry so the
*artifact itself* answers "which upstream?" without reading our git history.

**Why a major bump rather than continuing 4.0.x.** The rebase is genuinely source-breaking for
consumers of the fork's own API, not just a patch:
`CartesianChartHost(animateIn = …)` becomes a deprecated overload; `getXStep` changes arity;
`CartesianLayerDrawingModel.transform` now throws by default; `Entry.equals` widens; the
`compose-iosx64` publication disappears. SemVer says major. A 46th patch bump would understate it.

**Why not `3.3.1-gg.1` (upstream version + fork qualifier), which is the more honest-looking scheme.**
It is tempting and I considered it the leading alternative. It loses on two concrete points:
1. **It is a version *decrease* in our own coordinate.** Gradle orders `3.3.1-gg.1` below `4.0.46`
   (a hyphen-qualified version sorts below its base, and 3 < 4 regardless). meApp pins exactly
   (`vico = "4.0.46"` → `libs.vico.gg`), so resolution is unaffected today — but any future dynamic
   range, `latest.release`, Renovate/Dependabot rule, or Gradle version-catalog update plugin would
   treat the migrated artifact as older than what it replaces, and never offer it.
2. **It reads as an upstream artifact.** `com.dmdbrands.lib:compose-android:3.3.1-gg.1` invites the
   belief that the content equals upstream 3.3.1. It does not — it is upstream 3.3.1 plus 13
   adaptations and ~8 new files.

`UPSTREAM_BASE` gives the honesty without the ordering break.

**Also do:** record in the migration docs that `4.0.46` is the last pre-rebase release, so the 4.x
line reads as a closed era rather than an abandoned one.

**UNRESOLVED — registry display order.** GitHub Packages lists Maven versions by publish time, not
SemVer, so the ordering argument above is a Gradle-resolution concern only, not a UI one. Not
verified against the GPR UI for this package.
**Check that settles it:** `gh api orgs/gg-engineering/packages/maven/com.dmdbrands.lib.compose-android/versions`
and inspect the ordering of the returned array.

## 1.2 Part 1 checklist, in execution order

1. Clone the fork into the migration clone (Part 4). Do not touch `/Users/selvakumar/Projects/vico`.
2. Take verbatim from `v3.3.1`: `settings.gradle.kts`, `gradle/libs.versions.toml`,
   `gradle/wrapper/*`, `gradle.properties`, `buildSrc/build.gradle.kts`, `sample/**`.
3. Take `v3.3.1:buildSrc/src/main/kotlin/Versions.kt`; set `VICO = "5.0.0"`, add `UPSTREAM_BASE`.
   Keep upstream's `COMPILE_SDK = 37`, `MIN_SDK = 23`.
4. Take `v3.3.1:vico/build.gradle.kts`; change `group` to `com.dmdbrands.lib`.
5. Take `v3.3.1:vico/compose/build.gradle.kts`; swap `` `publishing-convention` `` →
   `` `maven-publish` ``; re-add the `Properties` import and the GPR `publishing { }` block.
6. Delete `buildSrc/src/main/kotlin/gpr-publishing-convention.gradle.kts`.
7. Keep `.gitignore`'s `gradle-local.properties` line (upstream file unchanged in that region).
8. `git remote set-url origin git@github.com:gg-engineering/vico.git`; drop `Version-2.5.0`.
9. Port the 13 library adaptations (other dossiers' scope), then build:
   `./gradlew :vico:compose:assemble` — this is where the context-parameters question is settled.
10. Record the publish task; add the `workflow_dispatch` CI job.
11. Publish `5.0.0`; verify the POM/`.module` set matches §B-5/6/7's expected shape.
12. Bump meApp's `vico = "5.0.0"` and fix the consumer breaks (Part 2).

---

# PART 2 — Consumer impact on meApp

Consumer: `/Users/selvakumar/Projects/meApp-bug/Android`, **23** files importing
`com.patrykandpatrick.vico` (`grep -rln` over `app/src`). Of those, 20 are `main`, 2 are `test`,
1 is `androidTest`.

## 2.0 Correction to the premise: "exactly four places on day one"

**S1-E's four-break list is correct only under an assumption it does not state: that the port carries
the fork's entire added API surface forward unchanged.** Against *stock* `v3.3.1`, meApp does not
break in four places — it breaks in roughly thirty, because meApp depends on a large body of API that
exists only in our fork.

This matters for the locked "minimise the fork" decision (BASELINE §Decisions 2): **every fork API we
decide to delete rather than port becomes a meApp compile break.** The list below is therefore the
real risk register for the consumer side, and should be read before any "drop this adaptation"
decision is taken in the other dossiers.

### 2.0.1 Fork-only API that meApp consumes (absent at `v3.3.1`)

Each verified by comparing the declaration at `HEAD` against `v3.3.1`, and at `e67e49520` where
provenance was in doubt.

| Fork API | Declared at `HEAD` | At `v3.3.1` | meApp call sites |
|---|---|---|---|
| `CartesianChartHost(flingBehavior =)` | `CartesianChartHost.kt:73` | absent | `GraphView.kt:103` |
| `CartesianChartHost(initialMarkerX =)` | `CartesianChartHost.kt:76` | absent | `GraphView.kt:104` |
| `rememberCartesianChart(visibleLabelsCount =)` | `CartesianChart.kt` | absent | `ProductChart.kt:101`, `SnapshotLineChart.kt:147` |
| `rememberCartesianChart(startInsetDp =)` | `CartesianChart.kt:70` | absent | `ProductChart.kt:104`, `SnapshotLineChart.kt:148` |
| `CartesianMarkerController.rememberNonInteractive()` | present | absent | `SnapshotLineChart.kt:145` |
| `ScrubMarkerController` / `rememberScrubMarkerController` | present | absent | `ProductChart.kt:49,73`, `GraphView.kt:46` |
| `VicoScrollState.isUserScrolling` | present | absent | `GraphView.kt:334` |
| `SnapBehaviorConfig`, `rememberChartSnapFlingBehavior` | present | absent | `GraphView.kt:41,47` |
| `InterpolationType` | present | absent | `GraphView.kt:39` |
| `LineCartesianLayer.Interpolator.monotone()` | present | absent | `DataLine.kt:47`, `PercentileBand.kt:39`, `PrimaryLayer.kt:50` |
| `rememberLineCartesianLayer(yTransform =)` | `LineCartesianLayer.kt:1238` | absent (`v3.3.1:1062–1080`) | `PrimaryLayer.kt:72,97`, `ProductChart.kt:324` |
| `rememberLineCartesianLayer(markerTargetsEnabled =)` | `LineCartesianLayer.kt:1243` | absent | `PercentileBand.kt:47` |
| `rememberLineCartesianLayer(alwaysUseLiveRange =)` | `LineCartesianLayer.kt:1244` | absent | `PrimaryLayer.kt:73,98` |
| `BaseAxis.Size.Scroll` | `BaseAxis.kt:214` | absent (upstream `Size` ends at `Text`, `v3.3.1:232`) | `VerticalAxis.kt:94` |
| `ListItemPlacer` | present | absent | `VerticalAxis.kt:16,91` |
| `VerticalAxis.MarkerDecoration` + `rememberEnd(markerDecoration =)` | present | absent (`v3.3.1:833–851`) | `VerticalAxis.kt:72,100`, `ProductChart.kt:93` |
| `HorizontalAxis.Separators` | present | absent | `Horizontalxis.kt:75` |
| `HorizontalAxis.LabelVerticalMode` | present | absent | `Horizontalxis.kt:82,110` |
| `rememberBottom(labelHorizontalPosition =)` | `HorizontalAxis.kt:844` | absent (`v3.3.1:1015–1029`) | `Horizontalxis.kt:83,111` |
| `ScrollAwareRangeProvider`, `rememberScrollAwareRangeProvider`, `WindowSettleSignal` | present | absent | `ProductChart.kt:39,40,42,142,156,282`; `BaseDashboardViewModel.kt:19,41,51,60–62,170`; `BabyDashboardViewModel.kt:28` |
| `InlineUpdateContext` | present | absent | `BaseDashboardViewModel.kt:25`, `ViewModelTestExtensions.kt:10` |
| **`CartesianChartModelProducer.runTransaction(animate =)`** | `CartesianChartModelProducer.kt:174` | **absent** — `v3.3.1:153` is `runTransaction(block)` | `WeightDashboardViewModel.kt:241`, `BabyDashboardViewModel.kt:218`, `BaseDashboardViewModel.kt:240,268`, `DashboardSnapshotViewModel.kt:345,414,479` |
| `CartesianChartRanges.getTargetYRange` | `CartesianChartRanges.kt:41` | absent | (fork-internal; meApp references only `YRange`) |

**`runTransaction(animate = …)` is a seventh break site S1-E's consumer table missed.** It is a fork
addition — `e67e49520:CartesianChartModelProducer.kt:153` is `runTransaction(block)`, same as
`v3.3.1` — and meApp uses it at **seven** call sites across four ViewModels. Any decision to drop it
in favour of upstream's `initialAnimationSpec = null` must change all seven.

**Confirmed upstream, not fork** (so safe): `markerController` on `rememberCartesianChart`
(`v3.3.1:709`), `CartesianMarkerController.rememberShowOnPress()`, the full `Interaction` hierarchy
including `Interaction.Tap` (`v3.3.1:Interaction.kt:32`, byte-identical to `HEAD`), `FadingEdges` /
`rememberFadingEdges`, `placeholder` on the host (`v3.3.1:96`),
`HorizontalAxis.ItemPlacer.getLabelValues` (identical signature at both refs — meApp's `object : …
by defaultPlacer` override in `HorizontalItemPlacer.kt:42–47` compiles unchanged).

## 2.1 COMPILE BREAKS (assuming a complete port of the fork API)

Four sites, all trivially fixable. Two are upstream signature changes; two are the same change seen twice.

| # | Site | What breaks | Fix |
|---|---|---|---|
| 1 | `GraphView.kt:101` | `animateIn = false` | `initialAnimationSpec = null` |
| 2 | `SnapshotLineChart.kt:166` | `animateIn = false` | `initialAnimationSpec = null` |
| 3 | `ProductChart.kt:99` | `getXStep = remember(segment) { { _: CartesianChartModel -> … } }` | `{ _, _, _ -> … }` |
| 4 | `SnapshotLineChart.kt:149` | `getXStep = { GraphUtil.calculateXStep(GraphSegment.WEEK) }` | `{ _, _, _ -> … }` |

(Note the line numbers: S1-E cited `ProductChart.kt:97` and `SnapshotLineChart.kt:150`; the actual
`getXStep` arguments are at `ProductChart.kt:99` and `SnapshotLineChart.kt:149`.)

**Why these are breaks and not merely deprecation warnings.** Both `@Deprecated` overloads exist at
`v3.3.1` at `DeprecationLevel.WARNING` — `CartesianChartHost` at `v3.3.1:138–149` and
`rememberCartesianChart` at `v3.3.1:787–805` — so in isolation each call would compile with a
warning. They break because **our fork's added parameters will live on the current overload, not the
deprecated one**:

- `GraphView.kt:94–109` passes `flingBehavior` and `initialMarkerX` alongside `animateIn`. The
  deprecated host overload has neither, and the current one has no `animateIn` — so the call is
  unresolvable either way.
- `ProductChart.kt:87–105` passes `visibleLabelsCount`, `startInsetDp` and `markerController`
  alongside the one-arg `getXStep`. Same shape of failure.
- `SnapshotLineChart.kt:132–150` likewise (`visibleLabelsCount`, `startInsetDp`).

**Explicit instruction for the port:** re-apply the fork's added parameters **only to the
non-deprecated overloads**, and leave upstream's deprecated overloads untouched. Doing it the other
way (patching both) would make these four sites compile silently on a deprecated path and defer the
work.

**Lambda-arity note.** `SnapshotLineChart.kt:149`'s lambda declares no parameters. A no-parameter
lambda literal is assignable to a one-parameter function type (via unused implicit `it`) but not to a
three-parameter one, so overload resolution can only reach the deprecated overload. S2-A listed this
as an open question; the reasoning above resolves which overload it *would* pick, but the outcome is
the same either way — once the fork's extra parameters sit on the new overload, it fails to resolve.
**Only a compile confirms it;** this is one of the first things the Part 4 composite build will tell us.

## 2.2 SILENT BEHAVIOUR CHANGES — the list that matters

Checked each of the seven S2-A candidates against meApp's actual call sites. Three reach meApp, two
reach it only through the fork's own code, two do not reach it at all.

### S-1. `HorizontalAxis` automatic-height cap: `canvasSize.height / 3` → flat `64.dp` — **REACHES MEAPP, EVERY SURFACE**

**The change.** `HEAD:HorizontalAxis.kt:568` `.coerceAtMost(canvasSize.height / MAX_HEIGHT_DIVISOR)`
with `MAX_HEIGHT_DIVISOR = 3` (`:794`) → `v3.3.1:HorizontalAxis.kt:707`
`.coerceAtMost(MAX_AUTO_HEIGHT.pixels)` with `MAX_AUTO_HEIGHT = 64.dp` (`:958`).

**Why it reaches meApp.** meApp's bottom axes take the default `size = Size.Auto()`
(`Horizontalxis.kt:53–84` and `:95–112` pass no `size`), so the cap applies. meApp's chart heights:

| Surface | Height | Old cap (h/3) | New cap |
|---|---|---|---|
| Dashboard, Tablet | `400.dp` (`GraphView.kt:154`) | ~133 dp | **64 dp** |
| Dashboard, Phone | `275.dp` (`GraphView.kt:155`) | ~92 dp | **64 dp** |
| Dashboard, Fold | `250.dp` (`GraphView.kt:156`) | ~83 dp | **64 dp** |
| Dashboard, Baby | `babyChartHeight(viewport)` (`GraphView.kt:151`) | viewport/3 | **64 dp** |
| Snapshot card | `199.dp + CHART_RESTING_LABEL_BAND` (= `10.dp + 20.dp`) = `229.dp` (`SnapshotCardContainer.kt:28`, `GraphMarker.kt:36,43`) | ~76 dp | **64 dp** |

The cap tightens on every surface. Whether it *bites* depends on the measured height of
`tickLength = SeparatorTickLength (16.dp)` + the `labelMediumRegular` label +
`labelVerticalMode = Inside` (our own fork behaviour). That total is plausibly under 64 dp, but it is
measured, not declared, and the measurement path is one of the files upstream rewrote hardest
(HorizontalAxis: ours 152+/149-, theirs 277+/77-).

**This is the single highest-value golden.** A 2 dp axis-height shift moves the whole plot area and
every label with it, and it is exactly the regression an eye misses.

**UNRESOLVED — does the cap actually bind?**
**Check that settles it:** golden images of the bottom axis band at all five heights, before and
after (Part 3). Failing that, log `HorizontalAxis.getHeight`'s pre-coerce value once on each surface.

### S-2. `ShapeComponent` margins now respected; `outline()` → `createOutline()`/`drawOutline` — **REACHES MEAPP, LOW RISK**

**The change** (S2-A §2.9, commits `bb9cfed7e` #1418, `2fbd1ab51` #1563). The drawn rect becomes
`adjustedLeft/Top/Right/Bottom` (margin- and stroke-inset), and the draw path changes from
`shape.outline(density, layoutDirection, path, …)` + `canvas.drawPath` to
`shape.createOutline(Size(w, h), …)` + `canvas.drawOutline`.

**Margin half: no effect.** Checked every meApp `ShapeComponent` / `rememberShapeComponent`
construction — `ProductChart.kt:52`, `PrimaryLayer.kt:53`, `DataLine.kt:50`, `GraphMarker.kt:144`,
`GraphDecoration.kt:42`, `SnapshotLineChart.kt:51`. **None passes `margins`.** The two `Insets` uses
in meApp are on *text* components, not shapes: `GraphMarker.kt:60` `margins = Insets(start = …)` on
`rememberTextComponent`, and `GraphDecoration.kt:40` `padding = Insets(horizontal = 10.dp, vertical = 2.dp)`
on `rememberTextComponent`. So nothing of meApp's relies on margins having been ignored.

**Draw-path half: a real rendering change, small blast radius.** Every data point meApp draws is a
`ShapeComponent(CircleShape)` — `DataLine.kt:49–56`, `PrimaryLayer.kt:51–59`, `GraphMarker.kt:144–149`
— plus the goal pill's circle background (`GraphDecoration.kt:42–45`). Antialiasing of a
`drawOutline(Outline.Rounded)` need not be byte-identical to `drawPath` of the same geometry.

**Do.** Include a close-crop golden of a data point and of the goal pill. Expect sub-pixel diffs;
set the comparison threshold accordingly rather than demanding byte equality.

### S-3. Single offscreen layer for all series + reveal-sweep clip — **REACHES MEAPP, BABY CHART SPECIFICALLY**

**The change** (S2-A §2.5, commits `72a0ccb87`, `5cbc0b24c`, `632d413f0`). The
`saveLayer(opacity)`/`restore()` pair moved from *inside* the `model.series.forEachIndexed` loop to
*outside* it — one offscreen buffer per frame instead of one per series — and a `sweepFraction`
reveal clip now wraps the whole draw.

**Why it reaches meApp.** The baby chart draws **seven** translucent percentile lines
(`PercentileBand.kt:20` `PERCENTILE_BAND_COUNT = 7`, `:37` `Fill(color.copy(alpha = 0.4f))`) *behind*
the data line, in two stacked layers (`ProductChart.kt:318` `arrayOf(percentileLayer, primaryLayer)`).
Seven overlapping 40%-alpha strokes compositing onto one buffer instead of seven is a different
result wherever they cross: with per-series buffers each stroke composites against the backdrop
independently; with one shared buffer they composite against each other first. The weight chart's
primary + secondary overlay (`ProductChart.kt:345`) is the same situation with two series.

**Do.** Baby chart goldens are mandatory, in both themes, and at a window where percentile curves
cross (early months). This is the one silent change with a predictable, visible outcome.

### S-4. Drawing-model `transform` hooks now throw; default interpolator changed identity — **REACHES THE FORK, NOT MEAPP DIRECTLY**

**The change** (S2-A §2.6). `CartesianLayerDrawingModel.transform(entries, from, fraction)` and
`Entry.transform(from, fraction)` are `@Deprecated` **and now throw `UnsupportedOperationException`
by default**. The default interpolator for line layers moved from
`CartesianLayerDrawingModelInterpolator.default()` to `.line()`
(`v3.3.1:rememberLineCartesianLayer:1078`; `HEAD:1236` still `.default()`), and `.line()` does not
call `transform()`.

**Why it is here.** meApp never names a drawing model or an interpolator — `grep` over `app/src` finds
no `CartesianLayerDrawingModel` or `DrawingModelInterpolator` import. But meApp's **y-transform
feature depends on the fork's layer draw path**: `PrimaryLayer.kt:36–40,72` and `ProductChart.kt:324–343`
pass a `yTransform` lambda that remaps the secondary metric onto the primary axis
(`GraphUtil.normalizeYValues`, `GraphUtil.kt:258`). If the port lands `yTransform` on a layer whose
default interpolator has silently changed, and if any part of that path goes through `transform()`,
it throws at runtime with no compile warning.

**Do.** When porting `yTransform`, explicitly confirm which `CartesianLayerDrawingModelInterpolator`
the fork's `LineCartesianLayer` ends up with, and that nothing on the y-transform path calls the
deprecated `transform`. Treat an `UnsupportedOperationException` from a chart draw as this change
until proven otherwise.

**UNRESOLVED.** Whether the fork's `yTransform` implementation touches
`CartesianLayerDrawingModel.transform`. **Check that settles it:**
`git grep -n 'fun transform\|\.transform(' HEAD -- vico/compose/src/commonMain/.../layer/ .../data/`
and read the `yTransform` call path in `HEAD:LineCartesianLayer.kt`.

### S-5. `Entry.equals`/`hashCode` now include `seriesKey`/`seriesIndex` — **DOES NOT REACH MEAPP; DOES REACH THE FORK**

**The change.** `v3.3.1:LineCartesianLayerModel.kt:123–137`: `Entry`'s internal constructor gained
`seriesKey: Any = 0` and `seriesIndex: Int = 0`, and `equals`/`hashCode`/`toString` now include both.

**meApp is safe, and this corrects the brief's concern.** The public `Entry(x: Number, y: Number)`
constructor **survives** (`v3.3.1:130`, identical to `HEAD:109`), so
`NormalizeYValuesTest.kt:20`'s `LineCartesianLayerModel.Entry(it.first.toDouble(), it.second)`
still compiles. And meApp never compares entries: every meApp site reads `.x`/`.y` positionally —
`GraphUtil.normalizeYValues` (`GraphUtil.kt:258–295`) indexes `series[i].y`, `metricScaleIn`
(`GraphUtil.kt:837–862`) reads `entry.x`/`entry.y` in a loop. A tree-wide grep for
`distinct`/`indexOf`/`contains`/`toSet` over entry lists in `GraphUtil.kt` returns one unrelated hit
(`:314`, on label strings). **No meApp behaviour change.**

**But the fork is exposed.** Entries built outside the builder DSL get `seriesKey = 0, seriesIndex = 0`
and compare unequal to builder-produced entries with the same `(x, y)`. Any fork-side memoisation or
`remember(...)` key that holds a `List<Entry>` — and the fork's y-transform caching explicitly keys
off series identity — changes behaviour. Also note `CartesianLayerDrawingModel` gained a same-size /
uniqueness `require` on `seriesKeys`, i.e. **a new runtime throw**.

### S-6. `VerticalAxis` `Size.Text` now measures with `label`, not `titleComponent` — **DOES NOT REACH MEAPP**

Confirmed independently of S2-A: `grep -rn 'Size\.' app/src` finds two `BaseAxis.Size` uses in meApp,
`VerticalAxis.kt:56` `size = BaseAxis.Size.Fixed(0.dp)` and `VerticalAxis.kt:94`
`size = BaseAxis.Size.Scroll(YGutterWidth)`. Neither `Size.Text` nor `Size.Fraction` appears. The
`Size.Fraction` deprecation is likewise unreached. **No action.**

(`Size.Scroll` is our own and is covered in §2.0.1 — it is a port obligation, not a behaviour change.)

### S-7. Marker-target narrowing tolerance (`MARKER_POSITION_TOLERANCE = 0.5f`) — **DOES NOT REACH MEAPP'S DATA SHAPE**

**The change** (S2-A §2.8, commit `4278255a4`). `targets.distinctBy { it.canvasX }.size > 1` →
`narrowMarkerTargets(targets, pointX)` keeping all targets whose canvasX span is ≤ 0.5 px.

**Why it does not bite meApp.** The widened behaviour matters for combo charts, where two different
layers put targets at the same x. meApp's layers are all `LineCartesianLayer`, and the one layer that
could add coincident targets — the 7-line percentile band — is explicitly excluded from marker
targets (`PercentileBand.kt:47` `markerTargetsEnabled = false`, with the comment "Not marker targets:
they are context for the reading"). The weight chart's primary + secondary overlay could in principle
produce two targets within 0.5 px; blood pressure draws three series (systolic, diastolic, pulse,
`DataLine.kt:36`) at the **same x by construction**, which is the case that changes.

**Do.** One BP-chart golden with the marker at rest, and one mid-scrub. If the marker starts showing
three values where it showed one, this is why. Our fork's `DefaultCartesianMarker` is heavily
patched (288+/19-), so the fork's own narrowing may already differ — a second reason to golden it.

### Not reaching meApp at all, recorded so they are not re-checked

- **Chart sizing model / `chartAreaHeight`** (S2-A §2.1): both meApp hosts pin a fixed height
  (`GraphView.kt:98` `.height(chartHeight)`, `SnapshotLineChart.kt:158` `.height(SNAPSHOT_CHART_HEIGHT)`),
  so `constraints.hasFixedHeight` is true and the height is honoured. Worth one golden anyway
  because *where the extras come out of* changed.
- **`rememberDefaultVicoZoomState` saver / `VicoZoomState.zoom` un-scaling** (S2-A §2.11): meApp calls
  `rememberVicoZoomState(zoomEnabled = false)` (`GraphView.kt:102`, `SnapshotLineChart.kt:171`). No
  programmatic zoom anywhere.
- **`CartesianValueFormatter.Decimal` equality widening** (S2-A §2.10): meApp constructs
  `CartesianValueFormatter { … }` lambdas (`VerticalAxis.kt:83`, `Horizontalxis.kt:55,98`), not
  `Decimal`/`DefaultValueFormatter` instances.
- **`LineProvider.getLine` leading `seriesKey` parameter** (S2-A §1.5, the only un-deprecated removal
  in 236 commits): meApp never implements `LineProvider`; it only calls
  `LineCartesianLayer.LineProvider.series(listOf(...))` (`DataLine.kt:60`, `PrimaryLayer.kt:65`,
  `PercentileBand.kt:44`). The fork must handle it; meApp need not.
- **`lineSeries` → `lineModel`**: deprecated alias retained at `v3.3.1:LineCartesianLayerModel.kt:223`
  with `ReplaceWith`. meApp's four imports (`WeightDashboardViewModel.kt:37`,
  `BabyDashboardViewModel.kt:27`, `BaseDashboardViewModel.kt:18`, `DashboardSnapshotViewModel.kt:33`)
  compile with a warning. Rename them opportunistically; it is not a break.
- **`MeasuringContext.canvasSize` deprecation** (S2-A §1.6): meApp touches no measuring context
  except through `HorizontalItemPlacer.kt`'s `CartesianDrawingContext` parameter, which it passes
  through without reading `canvasSize`.

## 2.3 `@SuppressLint("RestrictedApi")` — **both are vestigial; delete them**

Two sites: `ProductChart.kt:63` (on `rememberProductChart`) and `PrimaryLayer.kt:29` (on
`rememberLineLayerWithConnection`).

**Which restricted API does each use? Neither uses one.**

The complete set of `@RestrictTo` declarations in the module meApp consumes
(`git grep -n 'RestrictTo' HEAD -- 'vico/compose/src'` — six declarations in six files):

| Declaration | Location at `HEAD` |
|---|---|
| `CartesianMeasuringContext.markerSeriesIndex` (getter) | `cartesian/CartesianMeasuringContext.kt:59` |
| `CartesianChartRanges.Empty` | `cartesian/data/CartesianChartRanges.kt:63` |
| `DefaultColors` (class) | `common/Defaults.kt:75` |
| `getDefaultColors()` | `common/DefaultColors.kt:24` |
| `MeasuringContext.pointerPositionToX()` | `common/MeasuringContext.kt:66` |
| `VicoTheme.CandlestickCartesianLayerColors.Companion` | `common/VicoTheme.kt:65` |

Cross-checked against every symbol the two functions reference: `rememberCartesianChart`,
`CartesianMarkerController.rememberShowOnPress`, `rememberScrollAwareRangeProvider`,
`CartesianLayerRangeProvider`, `WindowSettleSignal`, `ExtraStore`, `CartesianChartRanges.YRange`,
`LineCartesianLayer.{rememberLine, LineFill.single, LineStroke.Continuous, Interpolator.monotone,
PointProvider.single, Point, LineProvider.series}`, `rememberLineCartesianLayer`,
`rememberShapeComponent`, `Fill`, `Axis.Position.Vertical.End`. **None of them is on the list.**
`CartesianChartRanges.YRange` in particular is *not* restricted — the `@RestrictTo` at
`CartesianChartRanges.kt:63` sits on the sibling `Empty` object (`HEAD` lines 62–71).

**Where the suppressions came from.** `git log -S'RestrictedApi' --all --reverse` in meApp points at
`291601de62` (2025-09-25, "Refactor graph view to improve snapping and scrolling behavior"), which
introduced `@SuppressLint("RestrictedApi")` on `PrimaryLayer.kt` **in the same commit that upgraded
Vico `2.1.8` → `2.2.8`**. At that time the file imported
`com.patrykandpatrick.vico.core.common.shape.CorneredShape` and used `CorneredShape.Pill`
(`291601de62:PrimaryLayer.kt:17,64`) — and vico 2.x had a far larger restricted surface:
`git grep -l 'RestrictTo' v2.2.1` returns **37 files**, including
`vico/core/.../common/shape/CorneredShape.kt`, whose `outline(density, path, …)` is
`@RestrictTo(LIBRARY_GROUP)` at `v2.2.1:CorneredShape.kt:60`. The `ProductChart.kt` copy was carried
forward wholesale by `78b5fce2c8` (MA-3487, 2026-04-08, vico `4.0.15`).

The 2.x `core`/`compose` split is gone, `CorneredShape` was replaced in meApp by Compose's own
`CircleShape` (`DataLine.kt:52`, `PrimaryLayer.kt:54`), and the restricted `outline()` member no
longer exists — S2-A §2.9's `outline()` → `createOutline()` change is literally the removal of the
member that caused this.

**Does v3.3.1 change it?** The set changes slightly and still contains nothing either file touches:
`v3.3.1` has **eight** declarations — the same six (with `CartesianChartRanges.Empty` moved to
`:57` and `Defaults.kt:74`) plus two new ones, `CartesianChartRenderer.kt:33` and
`pie/PieChartRenderer.kt:32`. meApp references neither renderer.

**Do.** Delete both annotations as part of the migration, in the same PR that bumps the vico version,
and keep `MOB-3042`'s comment only as history if at all. They are dead weight that has already caused
one bug (the annotation silently rebinding to a `Dp` val when two `val`s were inserted above the
function — `ee7fd65029`).

**UNRESOLVED — lint's actual verdict.** Lint's `RestrictedApiDetector` can flag paths that reading
declarations does not reveal (e.g. a restricted member reached through a default-argument expression
or an inherited member). I did not run lint.
**Check that settles it:** remove both annotations and run
`./gradlew :app:lintDebug` in `meApp-bug/Android` against the current `4.0.46`. Clean → delete.
Not clean → the error message names the exact API, which is the answer this section could not read
out of the source, and that API joins the S2-D "sealed doors" list as an upstream-PR or
public-wrapper candidate.

---

# PART 3 — The verification harness

## 3.0 Ordering, stated first because it is the constraint

```
   T0  Capture goldens from the CURRENT fork, 4.0.46, unmigrated.   ← blocking
   T1  Review + commit the goldens. They are now the specification.
   T2  Begin the port (Part 1 build changes + the 13 library adaptations).
   T3  Re-render against the migrated fork via the composite build (Part 4). Diff.
   T4  Adjudicate each diff: intended / upstream-silent-change / regression.
   T5  Publish 5.0.0; re-render against the published artifact; diff must be empty.
```

**T0 blocks T2.** A golden captured after any migration work has begun is not a baseline, it is a
record of a half-migrated state. S4-A says this; it is restated here as a hard gate because it is the
one piece of this plan that cannot be recovered later — once `4.0.46` is off the branch tip, the
baseline is gone.

**Practical consequence:** the golden harness lands in meApp as its own PR, against the current
`vico = "4.0.46"`, before the vico migration clone is created.

## 3.1 Tool choice: **Paparazzi**, with AGP Compose Screenshot Testing as the documented fallback

**Choose Paparazzi.** Reasons, in order of weight:

1. **It runs on the JVM with no device and no emulator.** meApp's existing chart coverage is all JVM
   (`GraphUtilTest`, `GraphYAxisTest`, `GraphReducerTest`, …, S4-A) and runs in CI today. Paparazzi
   goldens join that suite. Roborazzi runs under Robolectric, which meApp does not currently use for
   the chart path at all — adopting it means adopting Robolectric's shadow model as well as a
   screenshot library.
2. **The chart is a pure composable, so there is nothing to fake.**
   `rememberProductChart(config, window, separators, defaultMarker, segment, horizontalItemPlacer,
   fadingEdges, scrubController, percentileBands, onYRangeSettled)` (`ProductChart.kt:65–76`) takes
   plain data and **no ViewModel** — confirmed, `grep ViewModel ProductChart.kt` is empty. Neither
   tool's integration advantages apply.
3. **Roborazzi's advantage is interaction**, and we are capturing static frames. Where we *do* want
   a mid-scrub frame, we will get it by constructing the state, not by dispatching a gesture.

**The fallback, if Paparazzi proves unclean:** AGP's own Compose Screenshot Testing plugin
(`com.android.compose.screenshot`), because **upstream vico already uses it and ships passing
goldens** — see §3.2. It is also Layoutlib-based, so it is not an independent escape hatch from a
Layoutlib problem, but it *is* the configuration upstream validates against, so it is the known-good
one. Cost: it needs `android.experimental.enableScreenshotTest=true` and a `screenshotTest` source
set in meApp, and its golden workflow (`updateDebugScreenshotTest`) is less ergonomic than
Paparazzi's `recordPaparazziDebug`.

**Roborazzi stays the third option**, and becomes first choice only if a GPU/RenderEffect dependency
turns up that Layoutlib cannot do at all — in which case both Paparazzi and AGP screenshot tests fail
together and instrumented capture is the honest answer.

## 3.2 Is vico's Compose rendering Paparazzi-clean? — **strong positive evidence, still an assumption**

S4-A flagged this as an assumption. It is now better than that, but not settled.

**The evidence that it very likely is.** Inside the fork range, upstream added
`8f2aa9e17 test: add sample chart screenshot tests`. At `v3.3.1`:
- `sample/android/src/screenshotTest/kotlin/com/patrykandpatrick/vico/sample/SampleChartScreenshotTests.kt`
  declares eleven `@PreviewTest @Preview` composables wrapping real chart previews
  (`ComposeBasicLineChartPreview`, `ComposeBasicComboChartPreview`, `ComposeTemperatureAnomaliesPreview`, …).
- `sample/android/src/screenshotTestDebug/reference/…/SampleChartScreenshotTestsKt/` contains
  **eleven committed reference PNGs**.
- `gradle.properties` carries `android.experimental.enableScreenshotTest=true`; the catalogue gained
  `screenshot = { id = "com.android.compose.screenshot", version.ref = "screenshot" }` and
  `screenshotValidationApi`.

AGP's Compose Screenshot Testing renders through **Layoutlib** — the same engine Paparazzi uses. So
upstream has demonstrated that vico Compose charts render, deterministically enough to commit
reference images, under Layoutlib.

**Why that is not proof for us.**
1. **Different ref.** Those goldens are at `v3.3.1`. Our T0 capture is at `4.0.46`, which carries 13
   adaptations in exactly the draw path — including `CartesianChartHostBox`, the two-pass prepare,
   the marker resting pass, and the fading-edge draw order.
2. **Different harness.** Paparazzi uses its own vendored Layoutlib build and its own font/resource
   loading. "Layoutlib can render it" and "Paparazzi renders it identically twice" are different
   claims.
3. **Different composables.** Upstream's previews are simple, non-scrolling, single-layer charts.
   meApp's are scrolling, two-layer, marker-bearing, with a fork-only range provider that settles
   asynchronously.

**Four concrete risk factors, each with its mitigation:**

| Risk | Where it comes from | Mitigation |
|---|---|---|
| **Coroutines / `LaunchedEffect` do not run** | The model arrives via `CartesianChartModelProducer.runTransaction`, a `suspend` function, called from `LaunchedEffect`. Upstream hit this exactly: `v3.3.1:sample/shared/.../BasicLineChart.kt:65` wraps it in `runBlocking?.invoke { … }` with the comment *"Use `runBlocking` only for previews, which don't support asynchronous execution."* | Push the model **synchronously before composition** in the test, following upstream's pattern. Do not rely on `LaunchedEffect`. |
| **Animations do not advance** | The reveal/diff animation (`animationSpec`, `sweepFraction`) and the fork's animated Y-range overlay (H-2) | Capture with the animation disabled — `animateIn = false` at `4.0.46`, `initialAnimationSpec = null` after migration. This is also what the golden *should* show. |
| **Offscreen layers (`saveLayer`) and `ImageBitmap`** | `git grep -c 'saveLayer\|BlendMode\|PathEffect\|ImageBitmap' HEAD -- vico/compose/src/commonMain` → `LineCartesianLayer.kt`:9, `DrawingContext.kt`:8, `PieChart.kt`:6, `AreaFills.kt`/`LineFills.kt`:3 each, `ImageBitmap.kt`:2, `ShapeComponent.kt`:2, `FadingEdges.kt`:1 | Layoutlib supports `saveLayer`, but alpha compositing of translucent strokes is where host/device renderers most often diverge. Validate on the **baby chart** (7× 40%-alpha lines) first — if anything is going to differ, it is that. |
| **Font metrics differ from a device** | Layoutlib's font stack is not the device's; axis label widths feed back into axis sizing, which feeds back into plot geometry | Accept it. Goldens are for *regression*, not absolute fidelity. But it means a golden diff after the migration cannot be blamed on fonts — fonts are constant across T0 and T3. |
| **The fork's Y range settles asynchronously** | `ScrollAwareRangeProvider` + `WindowSettleSignal` recompute Y from the visible window; `onYRangeSettled` is a callback | A single static frame may capture the pre-settle axis. Verify at T0 that the golden shows settled ticks; if not, the harness must drive one extra frame. |

**The spike that settles it — scope it to one day, run it before anything else.**
Write **one** Paparazzi test in meApp at `vico = "4.0.46"`:
`rememberProductChart(ChartConfig for MyWeight, a fixed PlotWindow, GraphSegment.WEEK)` inside
`CartesianChartHost(animateIn = false)`, with the model pushed via `runBlocking { producer.runTransaction(animate = false) { lineSeries { … } } }`
before composition, at a fixed device config and density.

**Pass criteria, all three:**
1. The rendered PNG is a recognisable chart — line, points, both axes, labels — not a blank or
   partially drawn frame.
2. Two consecutive `recordPaparazziDebug` runs produce byte-identical output.
3. A `verifyPaparazziDebug` run on a clean checkout, on another machine or a CI runner, passes
   against the committed golden. (This is the one that catches font/JDK/OS drift, and it is the one
   that most often fails.)

**If (1) fails** → try the AGP screenshot plugin, mirroring upstream's configuration exactly, before
concluding Layoutlib cannot render vico.
**If (2) fails** → an animation or a time source is still live; find it and pin it.
**If (3) fails** → pin the JDK and the Paparazzi/Layoutlib version in CI, and capture goldens only on
CI, never locally.

Until that spike passes, **everything in §3.3 is contingent.** Do not schedule the migration's
verification around a harness that has not rendered one chart.

## 3.3 Where the tests live

```
meApp-bug/Android/app/src/test/java/com/dmdbrands/gurus/weight/
  features/common/components/chart/golden/
      ProductChartGoldenTest.kt        — the 3 × 4 × 2 matrix
      ChartEdgeStateGoldenTest.kt      — empty, single point, marker states
      ChartFixtures.kt                 — fixed ChartConfig / PlotWindow / entry data
app/src/test/snapshots/images/         — Paparazzi's default golden directory, committed
```

In `app/src/test` (JVM unit tests), beside the existing `GraphUtilTest` family, **not** in
`androidTest` — the whole point of Paparazzi is that this runs without a device, in the same task as
the logic tests that already guard the data feeding these charts.

`ChartFixtures.kt` is load-bearing: the goldens are only as stable as the data behind them. Fix every
timestamp explicitly (no `System.currentTimeMillis()`, no `Calendar.getInstance()` — note
`ProductChart.kt:118–119` falls back to `Calendar.getInstance().timeInMillis` when the window has no
bounds, which must never happen in a fixture), fix the locale, fix the time zone.

## 3.4 The golden set

**Matrix (24).** 3 configs × 4 segments × 2 themes.
- Configs: `ProductSelection.MyWeight`, `BloodPressure`, `Baby`/`BabyScale` (`ChartConfig.kt:73,85,94`).
- Segments: `WEEK`, `MONTH`, `YEAR`, `TOTAL` (`GraphSegment`).
- Themes: light, dark.

**Edge and state cases (12), each chosen to pin a specific risk from §2.2:**

| # | Case | Pins |
|---|---|---|
| 25 | Empty window, no entries | the `"–"` formatter paths (`VerticalAxis.kt:84`, `Horizontalxis.kt:59`) and the null-guideline branch (`VerticalAxis.kt:101`) |
| 26 | Single entry | degenerate Y range; `MetricRange.isSingle` (`GraphUtil.kt:286`) |
| 27 | Baby, percentile bands crossing (early months) | **S-3**, the one-offscreen-layer compositing change |
| 28 | Baby, bands outside the child's own readings | `expandedForPercentiles` (`ProductChart.kt:180`) |
| 29 | BP, marker at rest | **S-7**, marker narrowing with three coincident series |
| 30 | BP, mid-scrub | **S-7** again, plus the fork's scrub marker |
| 31 | Weight with secondary overlay (body fat) | **S-4**, the `yTransform` path |
| 32 | Weight, goal pill visible | **S-2**, `ShapeComponent` draw-path change on the pill |
| 33 | Snapshot card chart (`SnapshotLineChart`, `scrollEnabled=false`) | **S-1** at 229 dp; `rememberNonInteractive` |
| 34 | Tablet height (400 dp) | **S-1** at the largest old cap (~133 dp → 64 dp) |
| 35 | Fold height (250 dp) | **S-1** at the smallest dashboard height |
| 36 | Scrolled away from the start edge | `startInsetDp` / start-strip clipping (C-2, C-3) |

**36 images.** Close-crop variants of (32) and of a data point are worth adding for **S-2** if the
full-frame diff proves too noisy at the pixel level.

**What each golden must have disabled:** the initial animation, any system clock read, any locale or
time-zone dependence. **What it must not have:** a `ViewModel`, a `ModelProducer` populated from a
`LaunchedEffect`, or a `Dispatchers.Main` dependency.

---

# PART 4 — Composite-build workflow

## 4.1 The problem, stated precisely

`meApp-bug/Android/settings.gradle.kts:67–72` carries, commented out:
```kotlin
// Local vico development — uncomment to use local vico source
//includeBuild("/Users/selvakumar/Projects/vico") {
//  dependencySubstitution {
//    substitute(module("com.dmdbrands.lib:compose-android")).using(project(":vico:compose"))
//  }
//}
```
That absolute path is **the shared checkout this migration must not disturb** (`BASELINE.md`,
HARD RULE). `git worktree list` confirms it is a single checkout, one working tree, on branch
`MOB-2953-week-range-spans-eight-days` at `73e068afb`. Uncommenting those lines points meApp's build
at it and makes Gradle write into its `.gradle/` and `build/` directories — which is a working-tree
write, and which also means every meApp build is pinned to whatever branch that checkout happens to
be on.

Publishing a new `5.0.0-SNAPSHOT` to GPR for every iteration is the other obvious option and is
worse: a network round trip plus a Gradle cache invalidation per change, on an artifact whose
coordinate is already crowded with 37 versions and 7 dead ones.

## 4.2 Recommendation: a **second full clone** of vico, and a `gradle.properties`-switched include

**Not** a git worktree. Per the user's standing rule (`no-git-worktrees`), do not create a worktree of
the vico repository, and do not run worktree-isolated agents against it.

### Step 1 — one dedicated clone for the migration

```
git clone git@github.com:gg-engineering/vico.git /Users/selvakumar/Projects/vico-migration
cd /Users/selvakumar/Projects/vico-migration
git remote add upstream https://github.com/patrykandpatrick/vico.git
git fetch upstream --tags
git switch -c MOB-XXXX-vico-v3.3.1-migration v3.3.1
```

A full clone, not a worktree: independent `.git`, independent working tree, independent
`.gradle/`/`build/`. Nothing a build does there can reach
`/Users/selvakumar/Projects/vico`. Disk cost is a few hundred MB; it buys the guarantee the BASELINE
rule demands.

Copy `gradle-local.properties` (gitignored, holds `gpr.user`/`gpr.token`) across by hand so the clone
can publish when T5 arrives.

The shared checkout stays exactly as it is: the read-only reference for
`git diff e67e49520..HEAD -- <file>`, which is how the port is applied (BASELINE: hand-applied
per-file diff, never a rebase or cherry-pick).

### Step 2 — make the composite include **opt-in and path-configurable** in meApp

Replace the commented block with live, guarded code so the switch is a property, not an edit:

```kotlin
// Local vico development. Enable by putting these in local.properties (gitignored):
//   vico.local=true
//   vico.local.path=/Users/selvakumar/Projects/vico-migration
// Leave unset to resolve com.dmdbrands.lib:compose-android from GitHub Packages as usual.
val vicoLocal = localProperties["vico.local"]?.toString().toBoolean()
val vicoLocalPath = localProperties["vico.local.path"]?.toString()
if (vicoLocal && !vicoLocalPath.isNullOrBlank()) {
  val vicoDir = file(vicoLocalPath)
  require(vicoDir.resolve("settings.gradle.kts").isFile) {
    "vico.local.path does not point at a vico checkout: $vicoLocalPath"
  }
  logger.lifecycle("vico: building from local source at $vicoLocalPath")
  includeBuild(vicoLocalPath) {
    dependencySubstitution {
      substitute(module("com.dmdbrands.lib:compose-android")).using(project(":vico:compose"))
    }
  }
}
```

**Why this shape, point by point:**

- **No absolute path in version control.** The current comment hardcodes one developer's machine and
  one specific checkout. The path moves to `local.properties`, which meApp already reads at
  `settings.gradle.kts:4–8` for `gpr.user`/`gpr.key` and which is gitignored.
- **It cannot be left on by accident.** Committing `vico.local=true` is impossible (the file is
  ignored), and the `logger.lifecycle` line prints on every configuration so the state is never a
  surprise. A substituted build that silently stays enabled is how "works on my machine" bugs are
  made.
- **It fails loudly on a wrong path.** `require(...)` on `settings.gradle.kts` turns a typo into a
  message instead of an unresolved-dependency wall of text.
- **It cannot be pointed at the shared checkout by default.** The documented value is
  `vico-migration`. Nothing stops someone typing the other path, but nothing in the repo suggests it
  either — which is the opposite of today.

### Step 3 — the iteration loop

```
edit in  ~/Projects/vico-migration/vico/compose/...
run      ./gradlew :app:assembleDebug   (in meApp-bug/Android, vico.local=true)
```

Gradle's composite build recompiles only `:vico:compose` and relinks. No publish, no network, no
cache invalidation, no version bump. This is what makes the 13 adaptations tractable: each one can be
ported, compiled against the real consumer, and visually checked in a single loop.

**Verify the substitution is actually in effect** before trusting a green build:
```
./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath \
          --dependency com.dmdbrands.lib:compose-android
```
It should report `project :vico:compose` with a `dependencySubstitution` reason, not a module
version. Run this once at the start of each session — a silently-not-substituted build that resolves
`4.0.46` from GPR looks identical to a working one until the API changes.

**UNRESOLVED — whether the substitution resolves cleanly at v3.3.1's module layout.** The existing
comment substitutes the *platform* artifact `compose-android` with the *multiplatform project*
`:vico:compose`; Gradle bridges that through variant matching. That works today at AGP 9.1.0 /
`kotlin { androidLibrary { } }`. At v3.3.1 the block is `kotlin { android { } }` under AGP 9.3.2,
while meApp is on AGP 9.5.1 — three different AGP versions in one composite build.
**Check that settles it:** run the `dependencyInsight` command above in the composite build at
`v3.3.1`, before porting any library code. If variant matching fails, the fallbacks in order are
(a) `substitute(module("com.dmdbrands.lib:compose")).using(project(":vico:compose"))` against the
root module instead of the platform one, (b) `includeBuild` without substitution plus an explicit
`implementation(project(...))`, (c) publish to `mavenLocal()` and add it as a repository — still far
cheaper than a GPR round trip, though it needs `repositoriesMode` relaxed from
`FAIL_ON_PROJECT_REPOS`.

### Step 4 — what the composite build must **not** be used for

- **Not for the T0 goldens.** Those are captured against the published `4.0.46`, resolved from GPR,
  with `vico.local` unset. A golden captured against a local source tree records whatever was in that
  tree.
- **Not for the final verification.** T5 re-renders against the **published** `5.0.0`. A composite
  build bypasses the POM, the `.module` metadata and the AAR packaging entirely — the three things
  Part 1 is about. The configuration that produced `4.0.9` through `4.0.46` was arrived at by six
  failed attempts (S1-E §2.2) precisely because publishing behaviour is not observable from a project
  dependency.
- **Not for CI.** CI resolves from GPR. The local switch defaults off, which is why it must default
  off.

### Step 5 — cutover

1. Port complete, composite build green, goldens re-rendered and adjudicated (T3/T4).
2. Publish `5.0.0` from `vico-migration` (Part 1 §1.2 step 11).
3. Set `vico.local=false` in `local.properties`; set `vico = "5.0.0"` in
   `meApp-bug/Android/gradle/libs.versions.toml`; fix the four Part 2 compile breaks.
4. Re-render goldens against the published artifact. Diff must be empty against T3's.
5. Merge the vico migration branch; merge the meApp bump.
6. Only then is `/Users/selvakumar/Projects/vico` updated — by a normal `git pull` on a branch nobody
   is mid-read on, not by this migration's working tree.

### Why not the alternatives

| Alternative | Why not |
|---|---|
| Uncomment the existing `includeBuild` | Writes into the shared checkout's `.gradle/`/`build/`; pins meApp to whatever branch it is on; violates the BASELINE hard rule |
| `git worktree add` on the vico repo | Prohibited by the user's standing rule; also shares the parent's `.git`, so a stray `git` command in the worktree can still affect the parent's refs |
| Publish `5.0.0-SNAPSHOT` per iteration | Network round trip + cache invalidation per change; pollutes a registry that already has 7 dead coordinates; GPR does not overwrite snapshots the way a snapshot repo would |
| `mavenLocal()` per iteration | Better than GPR but still a full `publishToMavenLocal` per change, and needs `FAIL_ON_PROJECT_REPOS` relaxed. Keep as fallback (c) above |
| Develop in meApp against a copied source set | Loses `explicitApi()`, loses the module boundary, and the port's whole purpose is to produce a *library* |

---

## Appendix — things this dossier could not settle

| # | Question | Exact check |
|---|---|---|
| 1 | Does the fork's build environment enable Kotlin context parameters by default at 2.4.10? | `./gradlew :vico:compose:compileKotlinAndroid` in the migration clone. Failure → add `-Xcontext-parameters`, do not downgrade Kotlin |
| 2 | The exact publish task name and the full publish task set | `./gradlew :vico:compose:tasks --all \| grep -i publish` in the migration clone (not the shared checkout) |
| 3 | Does the 64 dp `HorizontalAxis` auto-height cap actually bind on any meApp surface? | Golden images of the bottom-axis band at 400/275/250/229 dp, before and after |
| 4 | Does the fork's `yTransform` path touch the now-throwing `CartesianLayerDrawingModel.transform`? | `git grep -n 'fun transform\|\.transform(' HEAD -- vico/compose/src/commonMain` plus reading the `yTransform` call path in `HEAD:LineCartesianLayer.kt` |
| 5 | Does lint actually flag anything in `ProductChart.kt` / `PrimaryLayer.kt` once the suppressions are removed? | Remove both; `./gradlew :app:lintDebug` at `vico = "4.0.46"` |
| 6 | Is vico Compose rendering Paparazzi-clean at 4.0.46? | The one-test spike in §3.2, with all three pass criteria |
| 7 | Does `substitute(module("com.dmdbrands.lib:compose-android")).using(project(":vico:compose"))` resolve at v3.3.1's layout, across AGP 9.3.2 (lib) vs 9.5.1 (app)? | `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency com.dmdbrands.lib:compose-android` in the composite build |
| 8 | Does GitHub Packages order Maven versions by SemVer or publish time? | `gh api orgs/gg-engineering/packages/maven/com.dmdbrands.lib.compose-android/versions` |
| 9 | Which overload does `SnapshotLineChart.kt:149`'s parameterless `getXStep` lambda bind to? | A compile. Expected to be moot — once the fork's extra parameters sit only on the non-deprecated overload, it fails to resolve either way |
