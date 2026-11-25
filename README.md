# Vico

![](https://img.shields.io/maven-central/v/com.patrykandpatrick.vico/core)
![](https://img.shields.io/github/actions/workflow/status/patrykandpatrick/vico/build.yml?branch=master)
![](https://kotlin-version.aws.icerock.dev/kotlin-version?group=com.patrykandpatrick.vico&name=core)

![](https://patrykandpatrick.com/vico/images/cover.png)

Vico is a powerful and extensible multiplatform chart library. It offers native support for Jetpack
Compose, Compose Multiplatform, and the Android view system.

Learn more in [the guide](https://patrykandpatrick.com/vico/guide).

## About this fork

This is Greater Goods' fork of [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico),
used for the charts in meApp (Weight Gurus) Android.

- **Where our changes live:** the `version-X.Y.Z` / release branches (for example `version-3.0.0`,
  `version-4.0.0`, `5.0.2`), not `master`. `master` tracks upstream; `develop` (branched from `master`) is where repo housekeeping PRs land.
- **Publishing:** each library module is published by hand from its branch to GitHub Packages as
  `com.dmdbrands.lib:vico-*` (for example `vico-core`, `vico-compose`, `vico-compose-m3`), using the
  `publishGprPublicationToGitHubPackagesRepository` Gradle task with a `gpr.user` / `gpr.token`
  that has `write:packages`. Bump `Versions.VICO` in `buildSrc` first. meApp reads it from
  `https://maven.pkg.github.com/gg-engineering/vico`. The upstream `release-update.yml` workflow
  only runs in the upstream repository and does not publish this fork.
- **Upstream sync:** merge (or rebase onto) an upstream release tag on a new branch, re-apply our
  changes, bump the version, and publish (see the `update-from-upstream` branch and
  `docs/migration-v3.3.1/` on `5.0.2`).

## Git hooks setup

Run once after cloning to enable the secret-scan and commit-message hooks:

```bash
git config core.hooksPath .githooks
```

## Quality checks

CircleCI (`.circleci/config.yml`) runs these jobs: `build`, `test`, `check-formatting`, `scan`,
`lint` and `coverage`. Run the same checks locally with JDK 17:

```bash
# Lint: detekt + Android lint on vico/core and vico/compose
./gradlew :vico:core:detekt :vico:compose:detekt :vico:core:lintDebug :vico:compose:lintDebug

# Unit tests + coverage report (vico/build/reports/kover)
./gradlew test :vico:koverHtmlReportCoverage :vico:koverLogCoverage

# Formatting (ktfmt 0.55)
java -jar ktfmt-0.55-with-dependencies.jar --dry-run --google-style --set-exit-if-changed .
```

**Coverage scope:** `vico/core` + `vico/compose`. These modules are excluded because meApp does
not use them: `vico/views`, `vico/compose-m2`, `vico/compose-m3`, `vico/multiplatform`,
`vico/multiplatform-m2`, `vico/multiplatform-m3` and the `sample` apps. Inside the scope, the
Compose compiler's generated `ComposableSingletons` classes are excluded (generated code).

**detekt rules turned off**, because satisfying them means restructuring upstream Vico code or
changing its public API, which this fork avoids so upstream merges stay cheap:

| Rule | Why it is off |
|---|---|
| `LongParameterList` | Public constructors and `remember*` functions take every option as a parameter; changing them breaks the API meApp calls. |
| `TooManyFunctions` | Flags public API classes (`CartesianChart`, the axes, the layers). Splitting them changes the API. |
| `LongMethod`, `CyclomaticComplexMethod`, `ComplexCondition`, `NestedBlockDepth`, `ReturnCount`, `LoopWithTooManyJumpStatements` | Flag upstream drawing and label-placement algorithms. Splitting them is a behaviour risk with no test benefit, and every split is a merge conflict on the next upstream sync. |
