# Steps in Strain, Full-Height Sheets, Vital Age Recalibration, Score Backfill — Design

Date: 2026-07-18. Decisions confirmed via question round: 15k steps ≈ 8–9 strain;
vital age gets an estimated-VO2max component plus harder calibration; stored scores
from the last 30 days are recomputed once with the new formulas.

## Problem

1. The three gauge bottom sheets open partially expanded; the 30-day chart is only
   visible after dragging the sheet up.
2. Strain is pure heart-rate-zone load (Karvonen). A 15,407-step day with no
   training scored 4.7 — everyday walking below 50% HRR contributes nothing, which
   understates load for an overweight user.
3. Vital age reads 22.7 vs Whoop's 32. Whoop's driver was poor VO2max, which we
   don't measure. The current model also lets an excellent HRV (−5 yrs at full
   0.40 weight) mask a poor resting HR.
4. Sleep-score changes (7h floor + quality) only apply from the day they shipped;
   old days (e.g. Jul 15: 100% at 6h44) keep stale scores and pollute the new
   30-day charts.

## Design

### 1. Bottom sheets open fully expanded

`GaugeInfoSheet` passes `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`
to `ModalBottomSheet`. No other change.

### 2. Steps feed strain

`StrainScoreCalculator.calculate` gains `steps: Int = 0`. Steps convert to raw load
at `STEP_LOAD_PER_STEP = 0.0016` and add to the HR-zone load *before* the log scale:

```
totalLoad = hrZoneLoad + steps × 0.0016
strain    = ln(1 + totalLoad) × 2.6, clamped to [0, 21]
```

Calibration: 15,407 steps alone → raw 24.7 → strain 8.4 (chosen target ~8–9).
7,000 steps alone → ~6.5. Real training still dominates because zone minutes carry
weights 1–16. The `samples.size < 2` early-return is removed so a day with steps
but no usable HR pairs still scores; all existing zero-strain tests keep passing
because the default is `steps = 0`.

`HealthRepository.syncDate` passes `aggregate.steps`.

### 3. Vital age recalibration

New fifth component **FITNESS** — estimated VO2max via Uth et al. (2004):
`VO2max ≈ 15.3 × maxHR / restingHR`. Fitness age from a linear fit through male
population norms (~47.5 ml/kg/min at 25 → ~33.5 at 60):

```
fitnessAge = (57.5 − vo2max) / 0.4, clamped to [18, 80]
offset     = fitnessAge − chronologicalAge, clamped to ±15
```

Needs `maxHrBpm` (new nullable field on `VitalAgeInputs`; `VitalAgeSeries.build`
gains `maxHrBpm: Double? = null`; the view model passes
`userPreferences.maxHeartRate.first()`). Missing maxHR or RHR → component absent,
weights renormalize as before.

Recalibrated base weights (sum 1.0):

| Component | old | new |
|---|---|---|
| FITNESS | — | 0.35 |
| HRV | 0.40 | 0.25 |
| RHR | 0.25 | 0.15 |
| SLEEP | 0.20 | 0.15 |
| ACTIVITY | 0.15 | 0.10 |

RHR's base weight drops because resting HR now also drives the VO2max estimate.

Harder curves:
- HRV offset clamp becomes **[−4, +10]** (bonus capped; penalty unchanged).
- RHR offset becomes **(rhr − 60) × 0.5**, still clamped ±10.

Expected for the user (chrono 23, RHR 74, maxHR ~192, HRV ~79, high steps):
VO2max ≈ 39.7 → fitness age ~44.6 → offset +15 (clamped) × 0.35, HRV −4 × 0.25,
RHR +7 × 0.15 → vital age ≈ **28–29**, in line with Whoop's 32 from two months ago.

UI: `VitalAgeResult` gains `fitness: VitalAgeComponent?`; breakdown card shows
"Cardio fitness" as the first row. Diverging bar keeps its ±10 scale (a ±15
fitness offset clamps the bar at full width, which is fine).

### 4. One-time score backfill

`SCORE_FORMULA_VERSION = 2` (public const on `HealthRepository`).
`UserPreferencesRepository` gains `scoreFormulaVersion: Flow<Int>` (default 1) and
`setScoreFormulaVersion(Int)`.

On `syncToday()`, before the normal yesterday+today sync: if the stored version is
older, re-run `syncDate` (ascending, so baselines/prior-day strain stay correct)
over every stored aggregate date within the last 29 days (Health Connect's
read-back limit; older dates would re-aggregate empty and wipe good data),
excluding yesterday/today which sync anyway. Then persist the current version.
Fresh installs run the loop over an empty set — harmless.

## Testing

- Strain: 15,407 steps alone in 8.0–9.0; steps add on top of a workout's HR load;
  existing tests unchanged via default parameter.
- Vital age: fitness offset for the user's numbers (+15 clamped); absent without
  maxHR; elite RHR yields a negative offset; HRV bonus caps at −4; updated
  renormalization expectations.
- Repository: corrupted past score is recomputed by `syncToday` when the version
  is stale, untouched when current; version persisted after backfill.
- Prefs: `scoreFormulaVersion` defaults to 1 and round-trips.
- Sheets: compile + on-device check (fully expanded on open).
