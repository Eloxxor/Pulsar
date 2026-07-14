# Sleep Heart Rate Chart — Design Spec

Date: 2026-07-14

## Summary

Replace the Sleep screen's current bare progress-bar stage breakdown with a
proper heart-rate-over-time chart for the most recent sleep session,
synchronized with a sleep-stage timeline bar below it — the standard
"hypnogram + HR overlay" visualization used by Whoop/Oura/Fitbit's own apps.
Existing summary numbers (total duration, baseline comparison, per-stage
minutes) stay, presented below the new charts.

## Goals

- A heart-rate line chart spanning the full sleep session window (bedtime to
  wake time, not the calendar day — sleep sessions cross midnight), with an
  interactive drag/tap tooltip showing exact bpm + time at any point.
- A time-synchronized stage timeline bar directly below the HR chart, color
  coded per stage (Deep/Light/REM/Awake), using the same time axis so the
  two visually align.
- No new sleep-stage detection logic — stage periods already come directly
  from Fitbit via `SleepSessionRecord.stages` (already mapped to
  `SleepStagePeriod` in this app since the original build). This spec is
  presentation-only for stages; only the heart-rate-during-sleep data path
  is new.

## Non-goals

- No historical/multi-night view in this pass — only the most recent sleep
  session, matching the current Sleep screen's scope.
- No new Room schema/persistence — heart rate samples for the chart are
  fetched fresh from Health Connect each time the Sleep screen loads/syncs,
  the same way `AggregationService` already fetches daily aggregates fresh
  each sync. Charting data doesn't belong in the daily-summary aggregate
  table.
- No changes to the Recovery/Strain/Sleep Performance scoring engine — this
  is purely a Sleep screen visualization upgrade.

## Data Flow

Current gap: `AggregationService` already computes the correct sleep
session (with real stage periods, cross-midnight-aware) as part of
`DailyAggregate.sleep`, but `HealthRepository.syncDate` immediately
flattens it down to just duration/stage-minutes when persisting to
`DailyAggregateEntity` — the full `SleepSession` object (with per-stage
start/end `Instant`s) and any heart-rate-during-sleep data are discarded.
Also, `AggregationService.aggregateForDate`'s heart-rate fetch is scoped to
the calendar day, not the sleep session's actual window, so it would miss
the portion of the night before midnight even if reused directly.

New pieces (additive, no existing behavior changes):
- `AggregationService.heartRateSamplesForSession(session: SleepSession): List<HeartRateSample>`
  — queries Health Connect directly for `[session.start, session.end)`,
  reusing the same `HealthDataSource.heartRateSamples` the class already
  calls elsewhere, just with a session-scoped window instead of a
  calendar-day one.
- `SleepDetail(session: SleepSession, heartRateSamples: List<HeartRateSample>)`
  — a small new model, not persisted, purely a return type for the fresh
  detail fetch.
- `HealthRepository.sleepDetailForDate(date: LocalDate): SleepDetail?` —
  calls `aggregationService.aggregateForDate(date)` fresh (a live call, not
  a Room read), and if it has a sleep session, follows up with
  `heartRateSamplesForSession` and bundles both.

`SleepViewModel` calls `sleepDetailForDate` once (or on each refresh
trigger) alongside its existing reactive `observeRecentAggregates(30)`
collection (unchanged, still drives the duration/baseline/stage-minutes
summary at the bottom of the screen).

**Downsampling:** raw heart-rate samples across a full night can number in
the thousands (Fitbit samples every few seconds during sleep). Before
handing samples to the chart, average them into 2-minute buckets — enough
resolution to see real trend/variability without rendering thousands of
points or looking jittery. This bucketing happens in `SleepViewModel`
(pure data transformation, easy to unit test), not in the chart component
itself (which should stay a dumb renderer of whatever points it's given).

## UI

**HR chart:** a new Canvas-drawn line chart component (same hand-rolled
approach as the existing `LineChart`/`CircularGauge` — no new charting
dependency), with:
- X axis: time, spanning the sleep session's `start` to `end`.
- Y axis: bpm, auto-scaled to the night's actual min/max range (with some
  padding) rather than a fixed 0-based scale, since resting sleep bpm
  ranges are narrow and a 0-based axis would flatten the visible variation.
- Drag/tap gesture (`pointerInput` + `detectDragGestures`/`detectTapGestures`)
  shows a vertical crosshair line at the touched X position plus a small
  label with the nearest bucket's bpm value and formatted time.

**Stage timeline bar:** a second, shorter Canvas component directly below
the HR chart, same X-axis time range (so the two visually line up when
stacked), rendering each `SleepStagePeriod` as a colored horizontal segment
proportional to its duration. Stage colors: Deep = a darker/saturated tone,
REM = a distinct accent, Light = a muted tone, Awake = a clearly
different/warning-adjacent tone — exact palette chosen at implementation
time to stay legible against the app's existing dark theme, not prescribed
here.

**Below the charts:** the existing summary content (total duration vs.
baseline, per-stage minutes) stays as-is, unchanged from the current Sleep
screen.

**Empty state:** unchanged — "No sleep data synced yet." when there's no
sleep session, matching current behavior.

## Testing

- `AggregationService.heartRateSamplesForSession`: unit test verifying it
  queries the session's exact window (not the calendar day), using
  `FakeHealthDataSource`.
- `HealthRepository.sleepDetailForDate`: unit test verifying it returns
  `null` when there's no sleep session, and a populated `SleepDetail` with
  both the session and heart-rate samples when there is one.
- The 2-minute bucketing/downsampling function in `SleepViewModel`: unit
  tests for bucket averaging with a few representative sample sets (evenly
  spaced samples, sparse/gapped samples, exactly-one-sample edge case).
- Chart components (drawing, gesture handling): no automated test —
  Compose Canvas + gesture presentation, consistent with how `LineChart`/
  `CircularGauge` are already handled. Verified manually on-device.
