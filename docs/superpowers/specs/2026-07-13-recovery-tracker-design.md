# Recovery Tracker — Design Spec

Date: 2026-07-13

## Summary

A personal Android app that replaces the "Google Health" app as the primary
way to view Fitbit data. It reads activity, heart rate, HRV, and sleep data
from Health Connect (synced there by the Fitbit/Google Health app) and
presents it with a Whoop-inspired UI: a daily Recovery score, a live Strain
score, and trend views — instead of Google Health's dated, cluttered
presentation.

Single user, local-only, no backend, no accounts.

## Goals

- Replace Google Health as the daily-use app for viewing Fitbit data.
- Provide a personalized daily Recovery score (0-100) and Strain score
  (0-21), calculated from the user's own rolling baseline rather than fixed
  thresholds.
- Clean, modern, Whoop-inspired visual presentation.
- All data stays on-device.

## Non-goals (v1)

- No cloud sync/backup, no accounts/login.
- No iOS/cross-platform support.
- No manual logging of workouts/food/etc. — read-only consumer of Health
  Connect data.
- No coaching/recommendations engine — scores and trends only.
- No support for other wearable brands beyond whatever syncs through Health
  Connect (though nothing prevents it, since the app reads Health Connect
  generically).

## Architecture

Native Android, Kotlin, Jetpack Compose, MVVM.

- **Health Connect client** — thin wrapper around
  `androidx.health.connect.client`. Owns permission requests and all reads of
  `HeartRateRecord`, `HeartRateVariabilityRmssdRecord`, `RestingHeartRateRecord`,
  `SleepSessionRecord`, `StepsRecord`, `ExerciseSessionRecord`,
  `ActiveCaloriesBurnedRecord`. No other layer talks to Health Connect
  directly.
- **Room database** — stores (a) computed daily Recovery/Strain scores
  (Health Connect has no concept of a score, so this is the app's own
  derived data) and (b) a cache of daily aggregates for fast dashboard
  loads without re-querying Health Connect on every screen open.
- **Sync/aggregation worker (WorkManager)** — runs on app open and via a
  periodic background job. Pulls new Health Connect records since last
  sync, computes daily aggregates, invokes the scoring engine, writes
  results to Room.
- **Scoring engine** — plain Kotlin, no Android dependencies, fully unit
  testable in isolation. Input: daily aggregates (+ rolling baseline
  history). Output: `RecoveryScore`, `StrainScore`.
- **UI (Compose)** — screens observe Room via `Flow`. No direct Health
  Connect access from UI code.

Rationale: isolates the scoring math (the app's core value) so it's
testable independent of Android/UI, and centralizes Health Connect access
so permission handling and record-type mapping live in one place.

## Screens & Navigation

Bottom nav, 3 tabs:

1. **Today (home)** — Recovery score (ring/gauge, color-coded
   green/yellow/red), live Strain accumulating through the day, sleep
   summary card, quick stats row (RHR, HRV, steps). Cards tap through to
   detail screens.
2. **Trends** — historical charts (7/30/90 day) for Recovery, Strain, HRV,
   RHR, sleep, to visualize baseline drift over time.
3. **Sleep** — sleep stage breakdown (light/deep/REM/awake), sleep score,
   bedtime/wake consistency vs. personal baseline.

Detail screens (reached from Today's cards, not in bottom nav): Heart Rate,
Activity/Steps, HRV — each shows today's value plotted against personal
baseline.

**Onboarding (first launch):** Health Connect permission request +
explanation of what's read and why + note that scores need ~1-2 weeks of
data before they're meaningful.

**Settings:** manage Health Connect permissions, score calculation
preferences (future), theme.

## Scoring

### Recovery score (0-100, computed each morning from the prior night)

Inputs:
- HRV (RMSSD)
- Resting heart rate
- Sleep quality (duration + stage composition vs. personal baseline)
- Prior day's Strain (high strain lowers next-day recovery expectation)

Personal baseline approach (chosen over fixed thresholds): for HRV and RHR,
maintain a rolling mean and standard deviation over the trailing 30 days
(minimum 7 days of history required before scores activate). Each morning,
compute a z-score for today's reading against that baseline. HRV above
baseline is a positive signal; RHR above baseline is a negative signal
(inverse). Clamp z ∈ [-2, +2] and scale to a 0-100 sub-score.

Sleep sub-score: duration vs. personal typical need, and % time in
deep/REM vs. personal baseline.

Composite weights (constants in the scoring engine, retunable once real
data is observed):
- HRV: 35%
- RHR: 25%
- Sleep: 30%
- Prior-day strain: 10%

### Strain score (0-21, Whoop's scale, accumulates through the day)

Based on time spent in heart-rate zones (relative to an estimated max HR)
plus logged Health Connect exercise sessions. Zone contribution is
non-linear — time in higher zones counts disproportionately more than time
in lower zones. Recomputed live while the app is open; finalized at
midnight.

### Baseline bootstrapping

First ~7-14 days: scores are hidden or shown with a "calibrating" label,
since a z-score against a near-empty baseline is meaningless.

## Error Handling & Edge Cases

- **Health Connect not installed** — prompt to install from Play Store.
- **Permissions denied/revoked** — show an explicit "reconnect" state;
  never silently show a zero/empty score that could be mistaken for a real
  reading.
- **Missing data for a day** (device not worn, sync lag, offline overnight)
  — mark the day "incomplete," exclude it from baseline calculations.
  Never treat missing HRV/RHR as 0.
- **Fitbit → Health Connect sync delay** — show a "last synced" timestamp
  on Today; support pull-to-refresh.
- **Insufficient baseline data** — calibrating state, see above.

## Testing

- **Scoring engine**: pure Kotlin unit tests with known input aggregates
  and expected score ranges, including missing-data days, first-week
  bootstrapping, and outlier values.
- **Health Connect client wrapper**: tested against Health Connect's test
  fixtures/fakes rather than requiring a physical device per test run.
- **UI**: manual verification on the developer's own Fitbit-synced device.
  This is a personal, single-device app — no broad device-matrix testing
  needed.

## Open Questions / Future Ideas (explicitly out of scope for v1)

- Retunable/config-driven scoring weights (v1 hardcodes weights as
  constants; revisit if manual tuning against real data proves necessary).
- Support for additional wearable brands.
- Cloud backup.
