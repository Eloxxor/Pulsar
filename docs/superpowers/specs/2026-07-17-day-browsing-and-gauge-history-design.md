# Day Browsing + Gauge History Charts — Design Spec (2026-07-17)

User decisions (2026-07-17): header arrows for day navigation; 30-day charts
inside the existing gauge bottom sheets.

## 1. Browse past days on the Today screen

- Header gains `‹` / `›` icon buttons around the date block.
  - `‹` enabled while an older aggregate exists in Room; `›` enabled while
    `selectedDate < today`.
  - Title: "Today" for today, "Yesterday" for today−1, else "EEE, MMM d".
  - Eyebrow shows the selected date.
- Past days render purely from Room (`daily_aggregates` + `daily_scores`) —
  no Health Connect re-sync when browsing.
- Pull-to-refresh keeps working on any day: it re-syncs today (+ yesterday
  backfill, existing behavior) and reloads the selected day.
- Vitals deltas for a past day are computed against the 30-day baseline
  *before that day* (same exclusion rule as today).
- "Last synced" shows the selected day's `lastSyncedAtEpochMillis`.

### State (`TodayUiState` additions)

`selectedDate: LocalDate`, `isToday: Boolean`, `canGoBack: Boolean`,
`canGoForward: Boolean`, `recoveryHistory: List<ChartPoint>`,
`sleepPerformanceHistory: List<ChartPoint>`, `strainHistory: List<ChartPoint>`.

### ViewModel (`TodayViewModel`)

- Gains `clock: Clock = Clock.systemDefaultZone()` constructor parameter
  (testability) and functions `selectDate(date)`, `goToPreviousDay()`,
  `goToNextDay()`; `refresh()` = load with sync, `selectDate` = load without.
- Loads `observeRecentAggregates(60)` + `observeRecentScores(60)`, picks the
  selected day's rows by date string, baseline history = rows dated before
  the selected day (first 30).

## 2. History charts in the gauge sheets

- `GaugeInfoSheets`: each sheet (Recovery / Sleep / Strain) shows a
  `MetricLineChart` of the last ~60 stored days under the headline value,
  only when ≥ 2 points exist. Colors: Recovery green, SleepViolet, Strain
  blue. Points come from the new `TodayUiState` history lists (ascending by
  date; recovery/strain from every score row, sleep performance where
  non-null).

## Testing

- `TodayViewModelTest` additions: `selectDate` on a synced past day exposes
  that day's persisted values (`selectedDate`, `isToday=false`,
  `restingHeartRate`, `canGoForward=true`); history lists are ascending and
  sized to the stored days.
- Screen/sheet changes verified by compile + on-device check (convention).
