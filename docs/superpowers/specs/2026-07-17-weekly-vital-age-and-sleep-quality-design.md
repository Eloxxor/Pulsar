# Weekly Vital Age + Sleep Quality — Design Spec (2026-07-17)

Two refinements approved by the user:

## 1. Vital Age updates weekly (Mondays)

**Problem:** The hero number recomputes on every sync; two sick days visibly
move it. User wants a stable number.

**Rule:** The displayed vital age is anchored to the most recent Monday.

- `anchor = today` floored to Monday (`with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))`).
- Display point = last daily-series point with `date <= anchor`.
- Fallback while calibrating (no point at/before the anchor yet): use the
  latest series point so the feature works from day 3; it stabilizes once
  the first Monday passes.
- Weekly trend = display point vs. last series point with
  `date <= displayDate − 7 days`; none → no trend yet.
- History chart shows Monday points only (`dayOfWeek == MONDAY`); hidden
  below 2 points (existing rule).
- `VitalAgeUiState` gains `asOfDate: LocalDate?`; hero shows caption
  "As of <d MMM> · updates every Monday".
- `VitalAgeSeries` (daily) is unchanged; anchor selection lives in
  `VitalAgeViewModel`.

## 2. Sleep performance: 7 h floor + 30 % quality

**Problem:** Score = duration / need where need is the user's own average —
sleeping a poor personal average scores 100%, and stage quality is ignored.

**New formula** (`SleepPerformanceCalculator.calculate`):

- `need = max(typicalSleepMinutes, 420) + strainAdjustment + debtRepayment`
  (strain/debt terms unchanged).
- `durationScore = min(1, actual / need)`.
- `qualityScore = min(1, (deepRemMinutes / actual) / 0.40)` — restorative
  share measured against a 40 % deep+REM target.
- With stage data: `performance = round(100 × (0.7 × durationScore + 0.3 × qualityScore))`.
- Without stage data (`deepRemMinutes == null` or `actual == 0`):
  `performance = round(100 × durationScore)` (old behavior plus floor).
- New parameter: `deepRemMinutes: Long? = null` appended to `calculate`.
- Call site `HealthRepository.syncDate` passes
  `aggregate.sleep?.let { it.minutesIn(SleepStageType.DEEP) + it.minutesIn(SleepStageType.REM) }`.
- `GaugeInfoSheets` sleep sheet copy updated: need has a 7 h floor; 30 % of
  the score is deep+REM share vs a 40 % target.

## Testing

- `SleepPerformanceCalculatorTest`: existing cases keep passing (they use
  typical = 480 ≥ floor and no stage data); new cases: floor kicks in for
  low typical; quality drags a full-duration night; missing stages falls
  back to duration-only; quality capped at the 40 % target.
- `VitalAgeViewModelTest`: display value equals the Monday series value on a
  Friday clock; history contains Monday points only; weekly delta compares
  Monday vs previous Monday; calibration fallback uses latest point.
