# Sleep Performance Score — Design Spec

Date: 2026-07-14

## Summary

Add a Whoop-style Sleep Performance score (0-100) as the third circular gauge
on the Today screen (Sleep — Recovery — Strain, left to right), completing
the Today screen redesign started in the
[2026-07-14 Today screen Whoop-style redesign](2026-07-14-today-screen-whoop-redesign.md)
spec. Unlike the existing `sleepSubScore` (an internal input to the Recovery
composite, weighted 60% duration / 40% deep+REM quality against a personal
baseline), Sleep Performance answers a different question: *did you get as
much sleep as your body needed last night*, accounting for extra need driven
by yesterday's exertion and any accumulated shortfall from recent nights.

## Goals

- A new, independent Sleep Performance score (0-100), following Whoop's
  publicly documented methodology (Total Sleep Time ÷ Sleep Need), simplified
  to omit nap detection (out of scope — this app doesn't distinguish naps
  from a main sleep session today) while keeping the two most impactful
  factors: strain-driven extra need, and multi-night sleep debt.
- Rendered as a third circular gauge on the Today screen, positioned first
  (leftmost) in the Sleep → Recovery → Strain row, colored purple/violet to
  stay visually distinct from Recovery (green/amber/red) and Strain (blue).

## Non-goals

- No nap detection or nap credit — out of scope; `AggregationService`
  already collapses each day to its single longest sleep session.
- No changes to the existing `sleepSubScore` / Recovery composite — that
  formula and its weighting stay exactly as they are. Sleep Performance is
  an additional, separate score, not a replacement.
- No user-visible "sleep debt" number/UI in this pass — debt is an internal
  input to the Sleep Performance formula, not its own displayed metric (may
  become one in a future pass).
- No Room migration path for the new column — see **Database Migration**
  below for why a destructive fallback is the deliberate, correct choice
  here.

## Formula

**Sleep Debt** (minutes): sum of each of the trailing 7 days' shortfall
(`max(0, typicalSleepMinutes - actualSleepMinutesThatDay)`), capped at 180
minutes total. Uses the *current* rolling `typicalSleepMinutes` baseline
(already computed in `HealthRepository.syncDate`) as the reference for every
day in the window, rather than recomputing a historical baseline per day —
a deliberate simplification consistent with the rest of the scoring engine's
approach to baselines.

**Sleep Need** (minutes) = `typicalSleepMinutes` (personal baseline, already
computed) + strain adjustment + debt repayment, where:
- Strain adjustment = `(priorDayStrain.value / 21.0) * 60.0` — up to 60 extra
  minutes of need after a maximum-strain (21.0) prior day.
- Debt repayment = `min(accumulatedDebtMinutes, 60.0)` — tonight's need
  increases by up to 60 minutes to help pay down debt; the rest of any debt
  beyond that carries forward implicitly, since debt is recomputed fresh
  from the trailing 7-day window on every sync rather than tracked as
  separate persisted state.

**Sleep Performance** = `(actualSleepMinutes / sleepNeedMinutes * 100)`,
clamped to `[0, 100]`. `null` when there's no sleep session for the day
(consistent with how the rest of the app treats missing sleep data — never
defaulted to 0).

## Database Migration

`AppDatabase` is currently `version = 1` with no `Migration` defined and no
`fallbackToDestructiveMigration()` on the `Room.databaseBuilder` call in
`ServiceLocator.kt`. Adding a column to `DailyScoreEntity` requires bumping
to `version = 2`. Since this app is pre-release, single-user, and has at
most a few days of accumulated calibration history on the one device it's
installed on, a full `Migration` is unwarranted engineering for the data at
stake: `ServiceLocator`'s `Room.databaseBuilder` call adds
`.fallbackToDestructiveMigration()`, and `AppDatabase`'s version bumps to 2.
This means the next install after this change wipes local history and
restarts the recovery-score calibration window — an acceptable, explicit
trade-off for a personal project at this stage, not something to carry
forward once there's real accumulated history worth preserving.

## Data Flow

`HealthRepository.syncDate` already computes `typicalSleepMinutes` and
`priorDayStrain` for the Recovery calculation — both are reused as-is for
Sleep Performance, no duplicate computation. `history` (the trailing-window
list already fetched via `aggregateDao.recentBefore`) already carries each
day's `sleepDurationMinutes`, reused for the debt window. A new
`SleepPerformanceCalculator` (pure Kotlin, package `scoring`, alongside
`BaselineCalculator`/`RecoveryScoreCalculator`/`StrainScoreCalculator`)
takes these plus today's actual sleep duration and returns the score.
`DailyScoreEntity` gains `sleepPerformance: Int?`. `TodayUiState` gains
`sleepPerformance: Int?`, mapped through `TodayViewModel` the same way
`recoveryValue` is today.

## UI

Today screen's ring row becomes three `CircularGauge` instances,
left to right: Sleep (violet, 0-100, `sleepPerformance`) — Recovery (large,
existing green/amber/red banding) — Strain (blue, existing 0-21). Given
three rings now share the row instead of two stacked vertically, Recovery's
size shrinks somewhat from the prior redesign's 200dp to keep all three
readable side by side; exact sizing is an implementation-time layout call,
not specified precisely here — the constraint is "three rings fit
comfortably in one row without horizontal scrolling on a typical phone
width," not an exact dp value. The existing Sleep card (duration + stage
breakdown context, referenced from `sleepDurationMinutes`) stays below the
ring row unchanged — the new ring supplements it with a performance number,
it doesn't replace the duration/stage detail.

## Testing

- `SleepPerformanceCalculator`: pure Kotlin unit tests — debt accumulation
  across a multi-day trailing window (including the 180-minute cap), strain
  adjustment scaling, debt-repayment capping at 60 min/night, null actual
  sleep → null result, and the full formula's clamping to `[0, 100]`.
- `HealthRepository.syncDate`: existing `HealthRepositoryTest` gets a case
  asserting `sleepPerformance` is populated and persisted alongside the
  other score fields for a day with sleep + history data.
- UI: no new automated test (Compose presentation only, consistent with the
  rest of the Today screen); verified manually on-device.
