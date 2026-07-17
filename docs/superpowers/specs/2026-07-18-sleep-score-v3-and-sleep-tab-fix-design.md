# Sleep Score v3 + Sleep-Tab HR Chart Fix — Design

Date: 2026-07-18. User report: sleep score runs ~10 points above Fitbit's Health
app; duration alone makes 90% too easy. Separately, the Sleep tab lost its BPM
chart (axis, session times, stage timeline).

## Sleep tab bug (root cause found)

`SleepViewModel` loads chart data via `sleepDetailForDate(LocalDate.now())`. After
midnight — or any time before the watch has synced today's sleep — that date has
no session, so heart-rate buckets, session times, and the stage timeline never
load, while the stages card still renders from the latest aggregate. Not a
regression from yesterday's changes; it is date-dependent and only shows up
after midnight.

**Fix:** load the detail for the date of the latest aggregate *with sleep* inside
the existing collector (guarded so each date loads once), and delete the separate
`LocalDate.now()` load.

## Sleep score v3

Previous (v2): `0.7 × duration + 0.3 × quality(deep+REM vs 40%)`. A met need plus
mediocre stages still lands ~88–93.

New: three components; weights renormalize over the available ones (duration is
always present; stages missing → duration-only, as before):

| Component | Weight | Score |
|---|---|---|
| Duration | 0.45 | `min(actual / need, 1)`; need unchanged: `max(typical, 7h) + strain adj + debt repayment` |
| Quality | 0.35 | `(deepRem / actual) / 0.45`, capped at 1 — target raised from 40% to 45% |
| Restfulness | 0.20 | asleep share `(deep+rem+light)/actual` mapped `(share − 0.85) / 0.10`, clamped [0,1] — 95%+ asleep = full, ≤85% = 0 |

Both stage-based inputs are passed as `null` when the session has no stage data
(guard added in `HealthRepository`; previously an empty stage list produced a
false `deepRem = 0`).

Calibration on the user's Jul 17 night (7h52, deep+REM 31%, asleep share 91%):
duration ~0.97, quality 0.70, restfulness 0.60 → **~80** instead of 88 — in line
with Fitbit's number.

`SCORE_FORMULA_VERSION` bumps to 3 so the existing backfill recomputes stored
days once.

Sheet copy in `GaugeInfoSheets` updated to describe the three parts.

## Testing

- Calculator: updated expectations (quality-only night 81; 45% target = 100);
  new restfulness tests (95% asleep → 100, 85% → 80, duration-only unchanged).
- ViewModel: chart data loads for the latest night with sleep even when today
  has none.
- Formula version: existing backfill test keeps passing (version constant only).
- Device: Sleep tab shows BPM axis/chart again; sleep scores drop ~8 points.
