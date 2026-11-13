# P3 — formatting normalisation (MOB-4096)

## What was done

Upstream formats with **ktfmt 0.64 `--google-style`** (`.github/workflows/check-formatting.yml`);
our fork is on **0.59**. Both sides of the port were exported with `git archive`-style `git show`
into scratch trees — **neither checkout was touched** — and formatted with 0.64, so the per-file
patches in `ports/` contain our changes and not the formatter's.

`ports/` holds **24 patches** (the files we modified) and **8 whole files** (the ones we added).
These are the inputs for P4 onward. The port is applied from these, by hand, per file — never by
rebasing or cherry-picking the 105 commits, for the reason in BASELINE.

## Correction: the formatting noise is 18–26%, not "about half"

I reported earlier that roughly half the diff on the two hottest files was formatting churn. That
was my error in summarising, not the research's. Measured:

| File | diff lines | imports | removed by normalising | non-semantic |
|---|---|---|---|---|
| `LineCartesianLayer.kt` | 374 | 48 (12%) | 14% | **~26%** |
| `HorizontalAxis.kt` | 301 | 1 (0%) | 18% | **~18%** |
| `VerticalAxis.kt` | 236 | 1 | — | — |
| `CartesianChartHost.kt` | 462 | 13 (2%) | — | — |

**S1-C was right and I misread it.** It said ~56 of HorizontalAxis's **149 removed lines** are
argument-list reformatting — 37% of the removals, which is 19% of the 301-line diff. That matches
the 18% measured here almost exactly. I applied a removals-only ratio to the whole diff.

**S1-D's "roughly half of LineCartesianLayer is whitespace and imports" is overstated.** Imports are
48 lines and normalising removes ~55 more: 27%, not 50%.

Two further facts worth having:

- **ktfmt does not touch star imports.** Four remain in the formatted base, so the 48 import lines on
  `LineCartesianLayer.kt` are a real diff that normalisation cannot remove. Our `a2a9a7690` expanded
  them, and the port carries that expansion or reverts it deliberately — it is not noise.
- **Normalising made four files' diffs slightly larger** (`VerticalAxis` −17%, `CartesianLayerMargins`
  −11%, `CartesianChartModelProducer` −8%, `CartesianChartHost` −4%). ktfmt's output is
  content-dependent, so where our change altered a line's length, 0.64 rewraps it differently than it
  rewraps the base. Those are genuine differences, not artefacts — but they mean normalisation is not
  a pure reduction and should not be sold as one.

## Consequence for the estimate

The hand-port on the two hottest files is **larger than the plan assumed**. PLAN's P8–P10 ranges
rested partly on the "about half is noise" figure. They are not invalidated — the correction is
roughly 25% more real lines on two files out of twenty-six — but the optimistic end of those phases
is less likely than it looked.
