# OpenFit-Style Redesign — Design Spec

Date: 2026-07-19
Status: approved by user (brainstorm round in session)

## Goal

Restructure the app to closely match the OpenFit reference screenshots: 3-tab navigation
(Overview / Trends / Settings), an Overview screen with three equal ring gauges and a 2×2
metric card grid (including a new Stress score), and a redesigned Sleep detail page with
stage list, hypnogram, and hours-vs-need section.

## User decisions (AskUserQuestion round)

1. **Navigation**: 3 tabs like the screenshot. Sleep and Vital Age become drill-in pages
   opened from the Overview; Settings becomes a tab.
2. **Fourth metric card**: build a real Stress estimate (0–3, like the screenshot), from
   HR elevation above resting baseline modulated by HRV.
3. **Vitals list**: stays as a compact card below the metric grid on the Overview.

## 1. Navigation

- Bottom tabs: **Overview** (`today` route, Home icon), **Trends**, **Settings**. The
  `sleep` and `age` tabs are removed from the bar but keep their routes as pushed
  destinations.
- Sleep page opens from: tap on the Sleep ring, or tap on the Sleep card.
- Vital Age page opens from: tap on the new Vital Age card.
- Sleep and Age screens get a top bar: back arrow (left) + centered title, like the
  screenshot. `SettingsScreen` loses its "Back" button when shown as a tab (parameter
  becomes optional/no-op; bottom bar stays visible).
- The settings gear icon in the Overview header is removed.

## 2. Overview screen (matches screenshot 1)

Order top to bottom:

1. **Header**: app name "Recovery Tracker" with small colored dot (left); date pill
   (right) containing `‹  <label>  ›` chevrons for the existing prev/next-day browsing.
   Label: "Today" / "Yesterday" / "EEE, MMM d".
2. **Three equal rings** (~104dp each, thin stroke ~8dp, rounded caps), order **Sleep %,
   Recovery, Strain**. Below each: small icon + label. Recovery keeps its status color
   (green/amber/red); Sleep violet; Strain blue. Taps: Sleep ring → Sleep page;
   Recovery/Strain rings → existing info bottom sheets.
3. **2×2 metric card grid** (two `Row`s of two cards, equal height):
   - **Heart rate**: header icon+label+chevron, large "NN bpm", red day-HR sparkline,
     caption "resting NN bpm". Tap → existing `MetricType.HEART_RATE` detail.
   - **Steps**: large step count (thousands separator), hourly bar mini-chart of the
     last 6 full hours, caption "N in last 6h" (today) or omitted for past days when
     the hourly read has no data. Tap → existing `MetricType.ACTIVITY` detail.
   - **Zone minutes**: large "NN min", caption "±N vs yesterday". No chart.
   - **Stress**: semicircular gradient gauge (green→amber→red, needle), value "N.N" +
     label (Low/Moderate/High), caption "rest NN bpm · now NN bpm".
4. **Vitals card**: existing 5 rows (Resting HR, HRV, Respiratory rate, SpO2, Skin
   temp) with delta chips — restyled spacing only.
5. **Sleep card**: "NN h NN m" + delta chip + chevron; whole card taps to Sleep page.
6. **Vital Age card**: "NN.N yrs" + chevron; taps to Age page.
7. "Last synced" footer text (existing).

Pull-to-refresh, day browsing, permission warning card: unchanged behavior.

## 3. New scores (computed at sync, stored on `DailyScoreEntity`)

### Stress (0–3)

`StressScoreCalculator.calculate(heartRateSamples, restingHr, hrvBaselineMean, todayHrvMillis): Double?`

- `null` when no HR samples or `restingHr == null`.
- `medianHr` = median of all day HR sample bpm values.
- `elevation = (medianHr − restingHr).coerceAtLeast(0.0)`
- `base = elevation / 15.0` (15 bpm above resting = 1.0)
- `hrvModifier = (hrvBaselineMean / todayHrvMillis).coerceIn(0.75, 1.5)` when both
  present and > 0, else `1.0`. Low HRV vs baseline amplifies stress.
- `stress = (base × hrvModifier).coerceIn(0.0, 3.0)`
- Labels (UI): `< 1.0` Low, `1.0–1.9` Moderate, `>= 2.0` High.

### Zone minutes

`ZoneMinutesCalculator.calculate(samples, maxHr, restingHr): Int`

- Threshold = `restingHr + 0.5 × (maxHr − restingHr)` (same zone-1 floor as strain).
- Same minute-attribution approach as strain: minutes between consecutive samples
  (gaps > 15 min ignored) count toward the total when the sample bpm ≥ threshold.
- Returns whole minutes; 0 when fewer than 2 samples.

### Persistence & backfill

- `DailyScoreEntity` gains `stressValue: Double? = null` and
  `zoneMinutes: Int? = null`.
- Room version 3 → 4 with a **real migration** (`ALTER TABLE daily_scores ADD COLUMN
  stressValue REAL` / `ADD COLUMN zoneMinutes INTEGER`) added via `addMigrations` in
  `ServiceLocator` — the existing `fallbackToDestructiveMigration()` must not fire for
  this bump (stored aggregates older than the 30-day Health Connect window are
  irreplaceable).
- `HealthRepository.SCORE_FORMULA_VERSION` → 4 so the existing backfill recomputes the
  last 30 days once.
- `syncDate` computes both from `aggregate.heartRateSamples`, `maxHeartRate` pref, and
  the same `restingHrForStrain` already used for strain. Stress additionally uses
  `hrvBaseline.mean` (when `sampleCount > 0`) and `aggregate.avgHrvMillis`.

## 4. New data need: hourly steps

- `HealthDataSource.stepsIntervals(start, end): List<StepsInterval>` where
  `StepsInterval(start: Instant, end: Instant, count: Int)` is a new model.
- `HealthConnectDataSource`: map `StepsRecord` list (already Fitbit-filtered) to
  intervals. `FakeHealthDataSource`: constructor list, filtered by window.
- `TodayViewModel` loads intervals for the selected date and buckets them into hourly
  counts (interval assigned to the hour of its start). UI state gains
  `hourlySteps: List<Int>` (24 entries) and `stepsLast6h: Int?` (today only: sum of the
  6 hours before now).
- HR sparkline: `TodayViewModel` also loads the day's HR samples on selection and
  exposes `heartRateSpark: List<Float>` (mean bpm per 30-min bucket, gaps skipped).
  Loaded live from Health Connect — for days older than the HC window the sparkline and
  hourly bars are simply empty and the cards fall back to number-only.

## 5. Sleep page (matches screenshot 2)

Top to bottom:

1. Top bar: back arrow + centered "Sleep".
2. Eyebrow "LAST NIGHT'S SLEEP" (left) + "Nh Nm in bed · vs 30 nights" (right, muted).
   "In bed" = session end − start. Delta chip vs 30-night average stays next to the
   duration headline.
3. **Heart rate chart** (existing `SleepHeartRateChart`), line color switched to the
   HR red/pink (`AppColors.HeartRate`).
4. **Stage list** — rows in order Awake, Light, Deep (SWS), REM. Each row: colored
   moon/stage dot, name + percent, horizontal progress bar (share of session), duration
   right-aligned. Awake minutes = (session end − start) − (deep+rem+light); clamp ≥ 0.
   Percent base = in-bed minutes (session duration), so percentages including Awake sum
   to ~100.
5. **Stage timeline (hypnogram)** — new `SleepHypnogram` composable replacing the
   single-row `SleepStageTimelineBar` on this screen: 4 lanes top-to-bottom Awake, REM,
   Light, Deep; each stage period drawn as a rounded block in its lane at its time
   position; lane labels on the left; time axis start/end below. Eyebrow shows
   "STAGE TIMELINE" + "NN% restorative" (deep+rem share of in-bed time) on the right.
6. **Hours vs need** — eyebrow + right-aligned "NN%" (= stored sleepPerformance).
   Card with two columns: "Asleep Nh Nm" and "Need Nh Nm". Need comes from
   `SleepPerformanceCalculator.calculateNeedMinutes(typicalSleepMinutes,
   priorDayStrain, accumulatedDebtMinutes): Double` — extracted from the existing
   private need computation (same formula: `max(typical, 420) + strainAdj + debtRepay`)
   and reused by `calculate(...)` internally. `SleepViewModel` computes it with the
   same inputs the repository uses (typical from prior nights, prior-day strain score,
   debt from stored aggregates) — exposed via repository helper
   `sleepNeedMinutesFor(date): Double?` to avoid duplicating that wiring.
7. **Wake events** row: count of `AWAKE` stage periods in the session.

Removed from Sleep page: nothing else — existing no-data empty state stays.

Out of scope (explicitly): the "Sleep stress" section from screenshot 3 (needs
pre-sleep continuous data we don't have).

## 6. Unchanged

Trends content, Settings content (minus back button), Vital Age content, all existing
score formulas (recovery, strain, sleep v3, vital age v2). Color semantics stay
(Recovery green/amber/red status, Sleep violet, Strain blue).

## Testing

- Unit: `StressScoreCalculatorTest` (median/elevation/HRV modifier/clamps/nulls),
  `ZoneMinutesCalculatorTest` (threshold, gap handling, <2 samples),
  `SleepPerformanceCalculatorTest` (need extraction stays consistent),
  `HealthRepositoryTest` (stress+zone minutes persisted; formula-v4 backfill),
  `TodayViewModelTest` (hourly buckets, spark buckets, stepsLast6h),
  `SleepViewModelTest` (awake minutes, wake events, need exposure),
  migration test optional (manual on-device check acceptable for personal app).
- Device (uiautomator dumps): 3 tabs visible; rings render; 4 grid cards with values;
  stress value plausible; Sleep page reachable from ring and card, hypnogram + stage
  rows + hours-vs-need render; Age reachable from card; day browsing still works;
  logcat 0 FATAL EXCEPTION; DB survives update install (no wipe).
