# Port status (MOB-4096)

## Done, committed, compiling, 70 library tests green

| Phase | What |
|---|---|
| P2 | Build rebased on v3.3.1; publishes `com.dmdbrands.lib:compose-android:5.0.0` |
| P3 | Formatting normalised to ktfmt 0.64; per-file patches extracted to `ports/` |
| P4 | `TextComponent` "null" fix; `InlineUpdateContext` beside upstream's name; `DO-NOT-APPLY.md` |
| P5 | Clean grafts: ranges, window settle signal, fading edges, marker target flag, `isReady`, `animate` |
| P6 | `VicoScrollState`: freeze, visible x range, three conversions widened to public, label read-out |
| P7 | **The scroll-aware Y range**: provider, host recomputation, opening-range seed, window broadcast |
| P8a | `MonotoneInterpolator` with `visiblePadding = 1`; `xBehindStartInset`, `rememberNonInteractive`, `ListItemPlacer` into meApp |

meApp's unresolved references against the migrated library: **68 → 9 distinct.**

## Remaining — measured, not estimated

Applying the remaining patches and counting rejects:

| File | hunks rejected | why it is hard |
|---|---|---|
| `HorizontalAxis.kt` | **13 / 19** | the hottest file both sides; `A4a` is two changes, not one, and pairs with `A5` |
| `VerticalAxis.kt` | **13 / 20** | `MarkerDecoration` and the `Size.Scroll` machinery, half of which is `A7b` DELETE |
| `LineCartesianLayer.kt` | **8 / 22** | the `yTransform` seam — the project's biggest structural call |
| `CartesianChart.kt` | **4 / 13** | the start-of-chart unit; `D-2` must not be applied |
| `Modifier.kt` | **4 / 4** | upstream rewrote the gesture pipeline; `onInteraction` now returns `Boolean` |

**42 hunks of hand-derivation**, plus the two fork-only files that depend on them
(`ScrubMarkerController`, the snap behaviour — the latter renamed `ChartSnapFlingBehavior.kt`
because upstream added its own `SnapFlingBehavior`).

These are PLAN's P9–P14 and they carry most of its uncertain estimate. They are also where every
failure is silent, which is why they are not worth rushing.

## Correction to a claim made during this work

A dry run reported "0 hunks fail" on all of these. **That was a bad check** — it counted the string
`FAILED`, which `patch --dry-run` does not emit per hunk. The real counts are above. Normalising
formatting in P3 did help the low-churn files (six applied clean), but it does **not** rescue the
files upstream rewrote.

## Audit trap, hit and caught

Bulk-applying patches silently reintroduced three things `DO-NOT-APPLY.md` forbids: the
`CartesianLayerMargins` halving, `acceptsGestures`, and `Scroll.Absolute.xBehindStartInset` (which
now lives in meApp). All three applied **cleanly** — a clean apply is not a correct apply. Reverted.

**Any future bulk apply must be followed by a register audit**, not trusted because `patch` was happy.
