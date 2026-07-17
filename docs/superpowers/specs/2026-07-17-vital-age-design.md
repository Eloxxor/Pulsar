# Vital Age — Design Spec (2026-07-17)

## Goal

A "Whoop Age"-style physiological age, computed entirely from data the app
already stores. Shown on its own bottom-nav tab ("Age"). Answers three
questions: How old is my body according to my vitals? Am I trending younger
or older this week? What is driving the number?

User decision: own tab, not a card on Today (chosen 2026-07-17).

## Why not a Whoop clone

Whoop Age uses VO2max estimates, lean body mass, zone minutes and sleep
consistency — Fitbit exports none of these to Health Connect. Instead we use
a transparent offset model over the four signal groups we do have: HRV, RHR,
sleep duration, activity (steps + strain).

## Model

### Inputs

14-day rolling averages ending on the evaluated day, computed from
`daily_aggregates` (HRV, RHR, sleep minutes, steps) and `daily_scores`
(strain). Null days are skipped per metric. Chronological age =
`evaluatedDate.year − birthYear` (birth year already collected in
onboarding, stored in `UserPreferencesRepository`).

### Component offsets (years)

Each component maps its 14-day average to an offset in years relative to
chronological age. Negative = younger.

| Component | Weight | Formula | Clamp |
|---|---|---|---|
| HRV | 0.40 | `hrvAge = (4.18 − ln(rmssdMillis)) / 0.0148`, clamped to [18, 80]; offset = `hrvAge − chronoAge` | ±10 |
| RHR | 0.25 | `(rhr − 62) × 0.4` | ±10 |
| Sleep | 0.20 | 420–510 min → −1.0; below: `(420 − m)/30 × 1.5`; above 510: `(m − 510)/30 × 0.75` | [−1, +8] |
| Activity | 0.15 | `0.6 × stepsOffset + 0.4 × strainOffset` where `stepsOffset = clamp((7000 − steps)/2000 × 2, −5, +6)` and `strainOffset = clamp((8 − avgStrain) × 0.4, −3, +3)`; if only one of steps/strain present, use it alone | (inherited from parts) |

HRV curve rationale: population RMSSD declines roughly log-linearly with age
(Umetani et al. 1998 norms; ~45 ms in the 20s to ~25 ms in the 60s). The
constants 4.18 / 0.0148 are the log-linear fit through those norms.

### Combination

- A component is *available* when its 14-day average exists.
- Fewer than 2 available components → no result (calibrating).
- Weights of available components are renormalized to sum to 1.
- `vitalAge = chronoAge + Σ normalizedWeight × offset`, clamped to ≥ 18.

### Series, trend, pace

- `buildVitalAgeSeries(dailyVitals, birthYear)` computes one vital age per
  day for every stored day whose trailing 14-day window contains ≥ 3 days
  with any data. Powers the history chart.
- Weekly trend: `vitalAge(today) − vitalAge(today − 7 days)` using exact
  series dates; absent partner point → no trend yet ("needs ~1 more week").
  Rendered as a `MetricDelta` constructed directly (not via `metricDelta()`,
  whose 3%-of-baseline neutral band would swallow every realistic weekly
  change on a ~27 baseline): `|diff| < 0.1` yr → NEUTRAL, negative → BETTER,
  positive → WORSE.
- Aging pace: the weekly trend expressed as "±X.X yr / week".

## Architecture

Pure calculation lives in `scoring/`, presentation in new `ui/vitalage/`.

- `scoring/VitalAgeCalculator.kt` — pure object.
  - `data class VitalAgeInputs(avgHrvMillis, avgRestingHr, avgSleepMinutes, avgSteps, avgStrain)` (all `Double?`)
  - `data class VitalAgeComponent(offsetYears: Double, weight: Double)` (weight = renormalized)
  - `data class VitalAgeResult(vitalAge: Double, chronologicalAge: Int, hrv, rhr, sleep, activity: VitalAgeComponent?)`
  - `fun calculate(chronologicalAge: Int, inputs: VitalAgeInputs): VitalAgeResult?`
- `scoring/VitalAgeSeries.kt` — pure.
  - `data class DailyVitals(date: LocalDate, hrvMillis: Double?, restingHr: Double?, sleepMinutes: Double?, steps: Double?, strain: Double?)`
  - `fun buildVitalAgeSeries(days: List<DailyVitals>, birthYear: Int): List<Pair<LocalDate, VitalAgeResult>>` (ascending by date)
- `ui/vitalage/VitalAgeViewModel.kt` — pulls 44 days of aggregates + scores,
  maps to `DailyVitals`, builds series, exposes:
  `VitalAgeUiState(isLoading, chronologicalAge, result: VitalAgeResult?, weeklyDelta: MetricDelta?, history: List<ChartPoint>, daysUntilReady: Int?)`
- `ui/vitalage/VitalAgeScreen.kt` — screen (see UI below).
- `ui/theme/AppColors.kt` — new token `VitalAge = Color(0xFFD9B54A)` (gold).
- `ui/navigation/RecoveryTrackerNavHost.kt` — 4th tab "Age",
  `Icons.Filled.Cake`, route `"age"`, order: Today, Trends, Sleep, Age.

No DB changes, no migration, no new permissions.

## UI (existing design system: Eyebrow, SectionCard, DeltaChip, MetricLineChart)

1. **Header**: Eyebrow "VITAL AGE".
2. **Hero**: vital age, one decimal, display-size, gold. Below:
   "X.X yr younger than your age (NN)" — green when younger, red when older,
   secondary when within ±0.5 yr ("matches your age"). DeltaChip
   `unit = "yr"`, `decimals = 1`, `suffix = "vs last week"` when weekly
   trend exists.
3. **Aging pace card**: SectionCard "Aging pace" — "±X.X yr / week" line plus
   one explanatory sentence; when no trend yet: "Needs about a week more
   data to show your pace."
4. **Breakdown card**: SectionCard "What drives it" — one row per available
   component: label + weight ("HRV · 40%"), diverging bar centered at zero
   (left/green = younger, right/red = older, half-width = 10 yr scale),
   trailing "−2.3 yr" value. Unavailable components render muted "no data".
5. **History card**: SectionCard "History" — MetricLineChart of the series
   (gold), only when ≥ 2 series points.
6. **Calibrating state**: replaces hero + cards when `result == null`:
   explains it needs ~`daysUntilReady` more days of data.

Copy is English, matching the rest of the app.

## Error handling

- No birth year (cannot happen post-onboarding, but defensive): calibrating
  state with hint to redo onboarding.
- Missing metrics: handled by weight renormalization; SpO2 not used at all.
- < 2 components or < 3 window days: calibrating state.

## Testing

- `VitalAgeCalculatorTest` (pure JUnit): younger-than-chrono for strong
  vitals; older for weak vitals; single component → null; renormalization
  math verified against hand-computed value; HRV clamp at extremes; sleep
  band edges.
- `VitalAgeSeriesTest`: window averaging (nulls skipped), ≥3-day rule,
  ascending order, weekly-pair availability.
- `VitalAgeViewModelTest` (Robolectric, fake repository pattern as in
  `TodayViewModelTest`): state mapping, weekly delta wiring, calibrating
  state, history mapping to ChartPoint.
- UI verified by compile + on-device check (project convention for Canvas
  Compose).
