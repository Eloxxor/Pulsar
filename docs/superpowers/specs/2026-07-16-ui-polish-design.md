# UI Polish Pass — Design Spec

**Date:** 2026-07-16
**Status:** Approved (user delegated design decisions: "erarbeite komplett von alleine eine deutlich more polished version der app")

## Goal

Make the app read as a designed product instead of a raw prototype. Four concrete user complaints drive this:

1. Trends charts have no labels at all (no axes, no values, no dates).
2. The Sleep tab looks raw and unstyled.
3. Vitals (RHR, HRV, …) are useless without an immediate better/worse comparison against the personal baseline.
4. The three Today gauges are neither tappable nor self-explanatory.

## Non-Goals

- No new fonts or external libraries (system font, hand-drawn Canvas charts stay).
- No light theme, no tablet layouts, no animations beyond Material defaults.
- No new Room schema fields, no changes to score math.

## Design Language

**Direction:** instrument panel, not dashboard-template. One hero moment per screen (Today: the recovery gauge; Sleep: the night's duration + heart-rate curve; Trends: the labeled metric charts). Everything else stays quiet: hairline-bordered cards, tiny uppercase eyebrow labels, muted grays, one accent per metric.

### Color tokens (`ui/theme/AppColors.kt`)

| Token | Hex | Use |
|---|---|---|
| Recovery | `#17C97E` | recovery score, positive deltas |
| Strain | `#2E6BFF` | strain score |
| SleepViolet | `#9B6BFF` | sleep performance gauge, REM stage |
| Hrv | `#35D0C4` | HRV trend line |
| Rhr | `#FF8A5C` | RHR trend line |
| HeartRate | `#FF7B6B` | sleep heart-rate curve |
| Warning | `#E8A33D` | mid recovery band, AWAKE stage |
| Danger | `#E8503D` | low recovery band, negative deltas |
| TextSecondary | `#9BA1AA` | secondary text |
| TextMuted | `#5F6670` | eyebrows, axis labels |
| GridLine | `#26262B` | chart gridlines, card borders |
| StageDeep | `#4E4EDB` | deep sleep |
| StageRem | `#9B6BFF` | REM |
| StageLight | `#6BAFFF` | light sleep |
| StageAwake | `#E8A33D` | awake |
| StageUnknown | `#3A3A42` | unknown |

Material scheme additions: `onBackground/onSurface #F2F3F5`, `onSurfaceVariant #9BA1AA`, `outline #26262B`. NavigationBar container `#101014`.

Chart identity rule (dataviz): each metric keeps its one color everywhere; status colors (warning/danger) never double as series colors. Every chart is single-series, so titles carry identity — no legends needed except the sleep-stage legend, where color + name + minutes appear together (never color alone).

### Shared components (`ui/common/`)

- `Eyebrow(text)` — 11sp, uppercase, 1.5sp letter-spacing, TextMuted.
- `SectionCard(title?) { content }` — RoundedCornerShape(20dp), surface container, 1dp GridLine border, 16dp padding.
- `DeltaChip(delta, unit, decimals, suffix)` — pill showing `▲ 4 ms vs 30d` / `▼ 3 bpm vs 30d` / `— typical`. Arrow from sign of diff, color from judgement: BETTER green, WORSE red, NEUTRAL gray. Renders nothing when delta is null (no baseline yet).

### Delta semantics (`model/MetricDelta.kt`)

`metricDelta(current, baseline, higherIsBetter, neutralBandFraction = 0.03)` returns `MetricDelta(diff, kind)` or null when either input is missing. `|diff| <= band × baseline` → NEUTRAL. `higherIsBetter = null` → always NEUTRAL (informational metrics).

| Metric | higherIsBetter | Notes |
|---|---|---|
| HRV | true | |
| RHR | false | |
| SpO2 | true | band 1% |
| Respiratory rate | null | deviation is informational |
| Skin temp variation | null | value is already a delta |
| Sleep duration | true | band 5% |
| Steps | — | no chip: mid-day value vs daily baseline is misleading |

Baselines: mean over the last 30 days **excluding today** (BaselineCalculator over `aggregates.drop(1)`).

## Screen: Today

```
WEDNESDAY · JUL 16                              [⚙]
Today
[permissions banner if revoked]

   (82%)        ( 74 )        (8.4)
   SLEEP      RECOVERY       STRAIN
              Recovered
```

- Three gauges keep sizes (90/140/90dp) but each gets an eyebrow label beneath, and the recovery gauge a status word: ≥67 "Recovered" (green), 34–66 "Adequate" (amber), <34 "Run down" (red).
- **Every gauge is tappable** → Material3 `ModalBottomSheet`:
  - **Recovery sheet:** score + status, copy "Your readiness to take on strain today, scored 0–100 against your 30-day baseline." Three labeled sub-score bars from `DailyScoreEntity`: HRV (35%), Resting HR (25%), Sleep (30%), plus note "Remaining 10%: yesterday's strain." Band legend 0–33 / 34–66 / 67–100.
  - **Sleep sheet:** performance %, copy "How much of last night's sleep need you covered. Need = your typical sleep, plus extra after high-strain days, plus repayment of recent sleep debt."
  - **Strain sheet:** value, copy "Cardiovascular load today, 0–21 (logarithmic). Time in higher heart-rate zones counts much more." Bands: <10 Light, 10–14 Moderate, 14–18 High, >18 All out.
- **Vitals card:** each row gets a `DeltaChip` vs 30-day baseline (semantics table above). Row: label left, value + chip right.
- **Sleep card:** duration + DeltaChip vs typical.
- **Steps card:** restyled row (icon, value, eyebrow, chevron) still navigating to the detail screen.
- Settings `Button` → gear `IconButton`.

## Screen: Trends

Each metric becomes a `SectionCard`:

```
RECOVERY
74        ▲ 6 vs 30d avg          ← swaps to "Jul 12 · 61" while dragging
[chart: 3 gridlines, y labels, gradient fill, latest-point dot]
Jun 17                    Jul 16
```

- **`MetricLineChart`** (replaces `LineChart`, which is deleted): takes `List<ChartPoint>` (`date` + `value`), positions x **by date** so gaps don't distort the shape; 2dp line with soft vertical gradient fill; 3 horizontal gridlines with min/mid/max value labels drawn in-canvas (TextMeasurer, 10sp, TextMuted); dot on the latest point; drag crosshair selects nearest point.
- Header per card: latest value (large) + `DeltaChip` (latest vs average of the visible range). While dragging, header shows the selected date + value instead.
- First/last date labels beneath the canvas.
- Metric colors: Recovery green, Strain blue, HRV teal, RHR coral. Delta judgement: recovery/HRV higher better, RHR lower better, strain neutral.
- Empty state per card when < 2 points: "Not enough data yet — check back after a couple of days."
- `TrendsViewModel` emits `MetricTrend(points, latest, delta)` per metric; the old `mapNotNull`-into-floats alignment bug (values losing their dates) disappears with `ChartPoint`.
- `MetricDetailScreen`/`MetricDetailViewModel` migrate to the same chart + `ChartPoint`.

## Screen: Sleep

```
Sleep
LAST NIGHT · 23:41 – 07:12
7h 31m    ▲ 26 min vs 30d avg

[HEART RATE card]
LOW 48 · AVG 55 BPM               ← swaps to "55 bpm · 03:12" when a point is selected
[chart with y bpm labels + gridlines + gradient fill]
23:41                        07:12

[STAGES card]
[timeline bar, 28dp, rounded]
● Deep   1h 12m · 16%   [colored bar]
● REM    1h 30m · 20%   [colored bar]
● Light  4h 02m · 54%   [colored bar]
```

- Whole screen scrollable, cards via `SectionCard`.
- HR chart: selection becomes **sticky** (tap or drag sets it; it stays after release; content-swap in the summary row shows the value). Y-axis min/max/mid labels, bedtime/wake labels beneath.
- Stage timeline gets the shared stage colors (moved to `ui/theme/AppColors.kt` access via `stageColor()` helper in `ui/sleep/SleepStageColors.kt`), plus a legend where each row = colored dot + name + minutes + percent + a stage-colored progress bar (replaces the theme-green `LinearProgressIndicator`s).
- `SleepViewModel`: `typicalTotalMinutes` now excludes the current night; new `durationDelta: MetricDelta?`.

## Architecture / Files

| File | Change |
|---|---|
| `ui/theme/AppColors.kt` | new — color tokens |
| `ui/theme/Theme.kt` | extend color scheme |
| `ui/common/Eyebrow.kt`, `ui/common/SectionCard.kt`, `ui/common/DeltaChip.kt` | new shared components |
| `ui/common/ChartPoint.kt`, `ui/common/MetricLineChart.kt` | new chart; `ui/trends/LineChart.kt` deleted |
| `model/MetricDelta.kt` (+ test) | new pure logic |
| `ui/today/TodayViewModel.kt` (+ test) | baselines, deltas, sub-scores |
| `ui/today/TodayScreen.kt`, new `ui/today/GaugeInfoSheets.kt` | redesign + bottom sheets |
| `ui/trends/TrendsViewModel.kt` (+ test) | `MetricTrend` / `ChartPoint` |
| `ui/trends/TrendsScreen.kt` | redesign |
| `ui/detail/MetricDetailViewModel.kt` (+ test), `MetricDetailScreen.kt` | migrate to ChartPoint |
| `ui/sleep/SleepViewModel.kt` (+ test) | duration delta, typical excl. today |
| `ui/sleep/SleepScreen.kt`, `SleepHeartRateChart.kt`, `SleepStageTimelineBar.kt`, new `SleepStageColors.kt` | redesign |
| `ui/navigation/RecoveryTrackerNavHost.kt` | NavigationBar colors |

**Testing:** TDD for all logic (MetricDelta, all ViewModel changes). Canvas/gesture composables: compile verification + on-device check, matching the project's established convention.
