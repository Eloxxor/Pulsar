# Health Monitor Widget — Design Spec

Date: 2026-07-14

## Summary

Add a "Gesundheits-Monitor" (Health Monitor) card to the Today screen, below
the Sleep/Recovery/Strain ring row, showing six vitals: RHR, HRV, respiratory
rate, SpO2, skin temperature variation, and the most recent synced heart
rate reading (with a purely cosmetic pulsing animation — never a fabricated
or simulated number).

## Goals

- Read three new Health Connect record types not currently used by the app:
  `RespiratoryRateRecord`, `OxygenSaturationRecord`, `SkinTemperatureRecord`.
- Surface RHR and HRV (already read and displayed elsewhere on Today) inside
  this same widget too, so all six vitals live in one place.
- Show the most recent heart rate sample from today's already-fetched
  `heartRateSamples` (no new Health Connect query needed) with a pulsing
  heart icon and an explicit "as of HH:mm" timestamp — never labeled "live"
  in copy, since Health Connect cannot provide true real-time data (see
  **Why not real "live" heart rate** below).

## Non-goals

- No true real-time/live-streaming heart rate — technically not achievable
  through Health Connect (confirmed via Android's own Health Connect "vitals
  experiences" documentation: writes are recommended in batches up to every
  15 minutes, there is no push/streaming API for third-party apps). The
  animation is cosmetic polish on a real, timestamped value, not a
  simulation of live measurement.
- No historical trend view for the three new vitals in this pass (no new
  Trends screen chart) — Today's widget shows today's value only. A future
  pass can add them to Trends the same way HRV/RHR already are.
- No new scoring engine changes — none of these three new vitals feed into
  Recovery, Strain, or Sleep Performance in this pass.

## Why Not Real "Live" Heart Rate

Investigated directly against Android's official Health Connect
documentation: Health Connect is a batch-oriented, on-device data store, not
a streaming architecture. Even apps that write frequently are guided to
batch at up to 15-minute intervals. Google Health's own "live" BPM display
almost certainly bypasses Health Connect entirely, using Google's direct
ownership-level integration with Fitbit's backend — access no third-party
app (including this one) has. The most honest, useful thing this app can
show is the most recent heart rate sample already present in the data it
reads for today, clearly timestamped, with a pulsing icon purely as a visual
"this app is alive" cue — not a claim of real-time measurement.

## New Health Connect Record Types

Verified directly against the resolved `connect-client:1.1.0` API (not
assumed from memory):

- **`RespiratoryRateRecord`** (instantaneous): `.time: Instant`,
  `.rate: Double` (breaths per minute).
- **`OxygenSaturationRecord`** (instantaneous): `.time: Instant`,
  `.percentage: Percentage` (`Percentage.value: Double`).
- **`SkinTemperatureRecord`** (interval, like `SleepSessionRecord`):
  `.startTime`/`.endTime: Instant`, `.deltas: List<Delta>` where each
  `Delta` has `.time: Instant` and `.delta: TemperatureDelta`
  (`TemperatureDelta.getCelsius(): Double`), plus `.baseline: Temperature`
  (not used by this app — only the deltas, matching what Google Health's
  "skin temperature variation" figure represents).

Corresponding new manifest permissions:
`android.permission.health.READ_RESPIRATORY_RATE`,
`android.permission.health.READ_OXYGEN_SATURATION`,
`android.permission.health.READ_SKIN_TEMPERATURE`.

## Aggregation

For a given day:
- **Respiratory rate**: average of all `RespiratoryRateRecord.rate` values
  within the day's window. `null` if none.
- **Oxygen saturation**: average of all `OxygenSaturationRecord.percentage.value`
  values within the day's window. `null` if none.
- **Skin temperature variation**: average of all `Delta.delta.celsius` values
  across every `SkinTemperatureRecord` whose window overlaps the day (in
  practice, per Health Connect's own session semantics, this is normally one
  record per night — same "pick the session, then average its samples"
  shape as the existing sleep-stage aggregation). `null` if none.
- **Latest heart rate**: `heartRateSamples.maxByOrNull { it.time }`, reusing
  the same `HeartRateSample` list `AggregationService` already fetches for
  the strain calculation — no new query. `null` if no samples today.
- RHR and HRV: unchanged, already computed (`restingHeartRate`,
  `avgHrvMillis` on `DailyAggregate`).

## Database

New nullable columns on `DailyAggregateEntity`:
`avgRespiratoryRate: Double?`, `avgOxygenSaturationPercent: Double?`,
`avgSkinTemperatureDeltaCelsius: Double?`, `latestHeartRateBpm: Long?`,
`latestHeartRateEpochMillis: Long?`. `AppDatabase` bumps to `version = 3`
(the existing `.fallbackToDestructiveMigration()` from the Sleep Performance
change already handles this — no new migration work needed, same
pre-release/single-user trade-off as before).

## UI

New `Card` on the Today screen, placed below the Sleep/Recovery/Strain ring
row and above the existing Sleep duration card. Title "Health Monitor".
Six rows/entries: RHR, HRV, respiratory rate ("x.x breaths/min"), SpO2
("xx%"), skin temperature variation ("±x.x°C"), heart rate (pulsing heart
icon + "xx bpm" + "as of HH:mm" from `latestHeartRateEpochMillis`, or "--"
with no timestamp if there's no sample today). Any missing vital (no data
synced yet) renders "--", consistent with how every other metric on this
screen already handles missing data — never a misleading zero.

The pulsing heart icon: a simple Compose `InfiniteTransition`-driven scale
or alpha animation on the icon only, purely decorative — the BPM number and
timestamp next to it are always the real last-synced values, never altered
by the animation.

## Testing

- `HealthConnectRecordMapper`: unit tests for the three new mapping
  functions, following the existing pattern (construct real HC record
  objects, assert domain mapping) — same approach already used for the
  existing five mapped record types.
- `AggregationService`: unit tests for the four new aggregate fields
  (average computation, `null` when no samples, latest-heart-rate
  selection), following the existing test file's pattern.
- UI: no new automated test (Compose presentation only, including the
  pulsing animation) — verified manually on-device, consistent with the
  rest of the Today screen.
