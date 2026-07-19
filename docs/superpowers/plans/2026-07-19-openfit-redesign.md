# OpenFit-Style Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the app to match the OpenFit reference: 3-tab nav, Overview with three equal rings + 2×2 metric grid (new Stress score, Zone minutes), redesigned Sleep page with stage list, hypnogram and hours-vs-need.

**Architecture:** New pure calculators (`StressScoreCalculator`, `ZoneMinutesCalculator`) feed two new persisted columns on `DailyScoreEntity` (Room 3→4 with a real migration — destructive fallback must not fire). `TodayViewModel` gains live Health Connect reads (hourly steps, HR sparkline) and vital age. UI is rebuilt in `TodayScreen`/`SleepScreen`; navigation collapses to 3 tabs with Sleep/Age as pushed routes.

**Tech Stack:** Kotlin, Jetpack Compose M3, Room, Health Connect, JUnit4 + Robolectric.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-19-openfit-redesign-design.md`
- Build: Git Bash, `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"` inline per Gradle call.
- Commits: `git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit` + `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` trailer. Work on master.
- TDD: RED (confirm failure) → implement → GREEN → commit per task.
- Existing score formulas unchanged (recovery, strain, sleep v3, vital age v2).
- Room migration 3→4 must be additive `ALTER TABLE`; never let `fallbackToDestructiveMigration()` handle this bump.

---

### Task 1: StressScoreCalculator

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/scoring/StressScoreCalculator.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/StressScoreCalculatorTest.kt`

**Interfaces:**
- Produces: `StressScoreCalculator.calculate(samples: List<HeartRateSample>, restingHr: Double?, hrvBaselineMillis: Double?, todayHrvMillis: Double?): Double?` — 0.0..3.0 or null.

- [ ] **Step 1: Failing tests**

```kotlin
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class StressScoreCalculatorTest {
    private fun samples(vararg bpm: Long) = bpm.mapIndexed { i, b ->
        HeartRateSample(Instant.parse("2026-07-18T10:00:00Z").plusSeconds(i * 60L), b)
    }

    @Test
    fun `no samples or no resting hr returns null`() {
        assertNull(StressScoreCalculator.calculate(emptyList(), 60.0, null, null))
        assertNull(StressScoreCalculator.calculate(samples(70), null, null, null))
    }

    @Test
    fun `median at resting hr is zero stress`() {
        assertEquals(0.0, StressScoreCalculator.calculate(samples(60, 60, 60), 60.0, null, null)!!, 0.001)
    }

    @Test
    fun `fifteen bpm above resting is one point without hrv data`() {
        assertEquals(1.0, StressScoreCalculator.calculate(samples(75, 75, 75), 60.0, null, null)!!, 0.001)
    }

    @Test
    fun `low hrv vs baseline amplifies stress`() {
        // base 1.0, modifier = 40/32 = 1.25
        assertEquals(1.25, StressScoreCalculator.calculate(samples(75, 75, 75), 60.0, 40.0, 32.0)!!, 0.001)
    }

    @Test
    fun `hrv modifier clamps to one point five`() {
        // 40/10 = 4.0 -> 1.5
        assertEquals(1.5, StressScoreCalculator.calculate(samples(75, 75, 75), 60.0, 40.0, 10.0)!!, 0.001)
    }

    @Test
    fun `stress clamps at three`() {
        assertEquals(3.0, StressScoreCalculator.calculate(samples(150, 150, 150), 60.0, null, null)!!, 0.001)
    }

    @Test
    fun `median resists workout spikes`() {
        // median of 60,62,64,150,155 = 64 -> base 4/15
        assertEquals(4.0 / 15.0, StressScoreCalculator.calculate(samples(60, 62, 64, 150, 155), 60.0, null, null)!!, 0.001)
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`Unresolved reference 'StressScoreCalculator'`)

`export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17" && ./gradlew testDebugUnitTest --tests "*StressScoreCalculatorTest*"`

- [ ] **Step 3: Implement**

```kotlin
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample

// Daytime stress estimate 0-3, modeled after consumer stress monitors: how far the
// typical (median) heart rate sits above the resting baseline, amplified when HRV is
// suppressed vs its 30-day baseline. Median, not mean, so workouts don't dominate.
object StressScoreCalculator {
    private const val BPM_PER_POINT = 15.0
    private const val MAX_STRESS = 3.0
    private const val HRV_MODIFIER_MIN = 0.75
    private const val HRV_MODIFIER_MAX = 1.5

    fun calculate(
        samples: List<HeartRateSample>,
        restingHr: Double?,
        hrvBaselineMillis: Double?,
        todayHrvMillis: Double?
    ): Double? {
        if (samples.isEmpty() || restingHr == null || restingHr <= 0.0) return null
        val sorted = samples.map { it.bpm.toDouble() }.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        val base = (median - restingHr).coerceAtLeast(0.0) / BPM_PER_POINT
        val modifier = if (hrvBaselineMillis != null && hrvBaselineMillis > 0.0 &&
            todayHrvMillis != null && todayHrvMillis > 0.0
        ) {
            (hrvBaselineMillis / todayHrvMillis).coerceIn(HRV_MODIFIER_MIN, HRV_MODIFIER_MAX)
        } else {
            1.0
        }
        return (base * modifier).coerceIn(0.0, MAX_STRESS)
    }
}
```

- [ ] **Step 4: Run — expect PASS**
- [ ] **Step 5: Commit** `feat: stress score calculator (0-3, HR elevation modulated by HRV)`

---

### Task 2: ZoneMinutesCalculator

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/scoring/ZoneMinutesCalculator.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/scoring/ZoneMinutesCalculatorTest.kt`

**Interfaces:**
- Produces: `ZoneMinutesCalculator.calculate(samples: List<HeartRateSample>, maxHr: Double, restingHr: Double): Int`
- Mirrors strain's minute attribution: minutes between consecutive samples, gap > 15 min ignored, minute counts when the segment's leading sample bpm ≥ threshold `restingHr + 0.5 × (maxHr − restingHr)`.

- [ ] **Step 1: Failing tests**

```kotlin
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ZoneMinutesCalculatorTest {
    private val t0 = Instant.parse("2026-07-18T10:00:00Z")
    private fun at(min: Long, bpm: Long) = HeartRateSample(t0.plus(min, ChronoUnit.MINUTES), bpm)

    @Test
    fun `fewer than two samples is zero`() {
        assertEquals(0, ZoneMinutesCalculator.calculate(listOf(at(0, 140)), 190.0, 60.0))
    }

    @Test
    fun `minutes above threshold count`() {
        // threshold = 60 + 0.5*130 = 125; 3 segments of 10 min, all >= 125
        val samples = listOf(at(0, 130), at(10, 135), at(20, 140), at(30, 130))
        assertEquals(30, ZoneMinutesCalculator.calculate(samples, 190.0, 60.0))
    }

    @Test
    fun `minutes below threshold do not count`() {
        val samples = listOf(at(0, 80), at(10, 90), at(20, 130), at(30, 60))
        assertEquals(10, ZoneMinutesCalculator.calculate(samples, 190.0, 60.0))
    }

    @Test
    fun `gaps over fifteen minutes are ignored`() {
        val samples = listOf(at(0, 130), at(60, 130), at(70, 130))
        assertEquals(10, ZoneMinutesCalculator.calculate(samples, 190.0, 60.0))
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved reference)
- [ ] **Step 3: Implement**

```kotlin
package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import java.time.Duration
import kotlin.math.roundToInt

// Minutes spent at or above the strain zone-1 floor (50% heart-rate reserve). Uses the
// same segment attribution as StrainScoreCalculator: each pair of consecutive samples
// contributes its duration to the leading sample, gaps over 15 minutes are dropped.
object ZoneMinutesCalculator {
    private const val MAX_GAP_MINUTES = 15L
    private const val ZONE_FLOOR_FRACTION = 0.5

    fun calculate(samples: List<HeartRateSample>, maxHr: Double, restingHr: Double): Int {
        if (samples.size < 2 || maxHr <= restingHr) return 0
        val threshold = restingHr + ZONE_FLOOR_FRACTION * (maxHr - restingHr)
        val sorted = samples.sortedBy { it.time }
        var minutes = 0.0
        for (i in 0 until sorted.size - 1) {
            val gapMinutes = Duration.between(sorted[i].time, sorted[i + 1].time).toMillis() / 60000.0
            if (gapMinutes > MAX_GAP_MINUTES) continue
            if (sorted[i].bpm >= threshold) minutes += gapMinutes
        }
        return minutes.roundToInt()
    }
}
```

- [ ] **Step 4: Run — expect PASS**
- [ ] **Step 5: Commit** `feat: zone minutes calculator (time at 50%+ heart-rate reserve)`

---

### Task 3: Persist stress + zone minutes (Room 3→4, formula v4)

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/local/DailyScoreEntity.kt` (add columns)
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/local/AppDatabase.kt` (version 4)
- Create: `app/src/main/java/com/esusic/recoverytracker/data/local/Migrations.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ServiceLocator.kt` (addMigrations)
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt` (compute+store, version 4)
- Test: `app/src/test/java/com/esusic/recoverytracker/data/HealthRepositoryTest.kt`

**Interfaces:**
- Produces: `DailyScoreEntity.stressValue: Double?`, `DailyScoreEntity.zoneMinutes: Int?`, `MIGRATION_3_4: Migration`.
- Consumes: Task 1 + Task 2 calculators.

- [ ] **Step 1: Failing test** (add to `HealthRepositoryTest`)

```kotlin
@Test
fun `syncDate stores stress and zone minutes`() = runTest {
    // Fake source with resting HR 60 and steady 130 bpm for 30 min (2 samples every 10 min)
    // maxHeartRate pref default applies; assert stressValue != null and zoneMinutes > 0
    val start = Instant.parse("2026-07-13T10:00:00Z")
    val source = FakeHealthDataSource(
        heartRate = (0..3).map { HeartRateSample(start.plus(it * 10L, ChronoUnit.MINUTES), 130L) },
        restingHr = listOf(RestingHrSample(start, 60L))
    )
    val repository = HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
    val entity = repository.syncDate(LocalDate.of(2026, 7, 13))
    assertNotNull(entity.stressValue)
    assertTrue(entity.zoneMinutes!! > 0)
}
```

(Adapt to the file's existing fixture style — it already builds in-memory Room + prefs.)

- [ ] **Step 2: Run — expect FAIL** (unresolved `stressValue`)
- [ ] **Step 3: Implement**

`DailyScoreEntity`: append `val stressValue: Double? = null, val zoneMinutes: Int? = null`.

`AppDatabase`: `version = 4`.

`Migrations.kt`:

```kotlin
package com.esusic.recoverytracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Additive only — stored aggregates older than the 30-day Health Connect window are
// irreplaceable, so this bump must never fall through to the destructive fallback.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_scores ADD COLUMN stressValue REAL")
        db.execSQL("ALTER TABLE daily_scores ADD COLUMN zoneMinutes INTEGER")
    }
}
```

`ServiceLocator`: `.addMigrations(com.esusic.recoverytracker.data.local.MIGRATION_3_4)` before `.fallbackToDestructiveMigration()`.

`HealthRepository.syncDate` (after strain computation):

```kotlin
val zoneMinutes = ZoneMinutesCalculator.calculate(aggregate.heartRateSamples, maxHr, restingHrForStrain)
val stress = StressScoreCalculator.calculate(
    samples = aggregate.heartRateSamples,
    restingHr = aggregate.restingHeartRate?.toDouble()
        ?: rhrBaseline.mean.takeIf { rhrBaseline.sampleCount > 0 },
    hrvBaselineMillis = hrvBaseline.mean.takeIf { hrvBaseline.sampleCount > 0 },
    todayHrvMillis = aggregate.avgHrvMillis
)
```

and in the entity: `stressValue = stress, zoneMinutes = zoneMinutes`. Bump `SCORE_FORMULA_VERSION = 4` with comment `// v4: stress + zone minutes columns`.

- [ ] **Step 4: Run full `HealthRepositoryTest` — expect PASS**
- [ ] **Step 5: Commit** `feat: persist stress and zone minutes per day (room 3->4, formula v4 backfill)`

---

### Task 4: Hourly steps read + sleep need exposure

**Files:**
- Create: `app/src/main/java/com/esusic/recoverytracker/model/StepsInterval.kt`
- Modify: `HealthDataSource.kt`, `HealthConnectDataSource.kt`, `HealthConnectRecordMapper.kt`, `FakeHealthDataSource.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/scoring/SleepPerformanceCalculator.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/data/HealthRepository.kt`
- Test: `SleepPerformanceCalculatorTest.kt`, `HealthRepositoryTest.kt`

**Interfaces:**
- Produces: `data class StepsInterval(val start: Instant, val end: Instant, val count: Int)`;
  `HealthDataSource.stepsIntervals(start, end): List<StepsInterval>`;
  `SleepPerformanceCalculator.calculateNeedMinutes(typicalSleepMinutes: Double, priorDayStrain: StrainScore?, accumulatedDebtMinutes: Double): Double`;
  `HealthRepository.sleepNeedMinutesFor(date: LocalDate): Double?` (null when no aggregate history basis; uses stored aggregates/scores only, no Health Connect read).

- [ ] **Step 1: Failing tests**

`SleepPerformanceCalculatorTest`:

```kotlin
@Test
fun `need minutes is typical plus strain and debt adjustments`() {
    // typical 400 -> floored to 420; strain 21 adds 60; debt 200 capped at 60
    val need = SleepPerformanceCalculator.calculateNeedMinutes(
        400.0, StrainScore(LocalDate.of(2026, 7, 17), 21.0), 200.0
    )
    assertEquals(540.0, need, 0.001)
}
```

`HealthRepositoryTest`:

```kotlin
@Test
fun `sleepNeedMinutesFor uses stored history`() = runTest {
    // sync two nights (400 min each), then need for day 3 >= 420 (floor)
    // assert sleepNeedMinutesFor(day3) != null && >= 420.0
}
```

(Write the repository test fully in-session following the file's existing two-night fixture pattern.)

- [ ] **Step 2: Run — expect FAIL**
- [ ] **Step 3: Implement**

`SleepPerformanceCalculator`: extract the need computation used inside `calculate` into

```kotlin
fun calculateNeedMinutes(
    typicalSleepMinutes: Double,
    priorDayStrain: StrainScore?,
    accumulatedDebtMinutes: Double
): Double {
    val strainAdjustment = ((priorDayStrain?.value ?: 0.0) / MAX_STRAIN) * MAX_STRAIN_ADJUSTMENT_MINUTES
    val debtRepayment = accumulatedDebtMinutes.coerceIn(0.0, MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES)
    return typicalSleepMinutes.coerceAtLeast(MIN_NEED_MINUTES) + strainAdjustment + debtRepayment
}
```

and call it from `calculate(...)` (formula unchanged — existing tests must stay green).

`HealthRepository`:

```kotlin
suspend fun sleepNeedMinutesFor(date: LocalDate): Double? {
    val history = aggregateDao.recentBefore(date.toString(), BaselineCalculator.WINDOW_DAYS).reversed()
    val typical = history.mapNotNull { it.sleepDurationMinutes }.map { it.toDouble() }
        .let { if (it.isEmpty()) return null else it.average() }
    val priorDayStrain = scoreDao.getByDate(date.minusDays(1).toString())
        ?.let { StrainScore(date.minusDays(1), it.strainValue) }
    val debt = SleepPerformanceCalculator.calculateSleepDebtMinutes(
        typical, history.mapNotNull { it.sleepDurationMinutes }
    )
    return SleepPerformanceCalculator.calculateNeedMinutes(typical, priorDayStrain, debt)
}
```

`StepsInterval` model + `stepsIntervals` in the interface; HC implementation maps `readRecords<StepsRecord>` via new mapper `toStepsIntervals` (`StepsInterval(it.startTime, it.endTime, it.count.toInt())`); Fake gets `stepsIntervalList: List<StepsInterval> = emptyList()` constructor param filtered by `it.start >= start && it.start < end`.

- [ ] **Step 4: Run scoring + repository suites — expect PASS**
- [ ] **Step 5: Commit** `feat: hourly steps read and public sleep-need computation`

---

### Task 5: TodayViewModel — grid data + vital age

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/navigation/RecoveryTrackerNavHost.kt` (pass `userPreferencesRepository`)
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Produces on `TodayUiState`: `stressValue: Double?`, `zoneMinutes: Int?`, `zoneMinutesYesterday: Int?`, `hourlySteps: List<Int>` (24), `stepsLast6h: Int?`, `heartRateSpark: List<Float>`, `vitalAge: Double?`.
- Constructor gains `userPreferences: UserPreferencesRepository`.

- [ ] **Step 1: Failing test** (follow the file's existing Robolectric fixture)

```kotlin
@Test
fun `uiState exposes hourly steps spark and stress`() = runTest {
    // Fake source: stepsIntervals with 500 steps at 09:00-09:30 and 300 at 14:00-14:15,
    // HR samples through the day, resting HR present. After sync + load:
    // hourlySteps[9] == 500, hourlySteps[14] == 300, heartRateSpark.isNotEmpty(),
    // stressValue != null, zoneMinutes != null, vitalAge maybe null (few days) — assert no crash.
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved uiState fields)
- [ ] **Step 3: Implement**

In `load(...)` after the existing queries (zone from local timezone, selected date):

```kotlin
val dayStart = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
val dayEnd = selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
val stepsIntervals = runCatching { healthDataSource.stepsIntervals(dayStart, dayEnd) }.getOrDefault(emptyList())
val hourly = IntArray(24)
stepsIntervals.forEach { interval ->
    val hour = interval.start.atZone(ZoneId.systemDefault()).hour
    hourly[hour] += interval.count
}
val nowHour = java.time.ZonedDateTime.now(clock).hour
val stepsLast6h = if (selectedDate == today) {
    ((nowHour - 5).coerceAtLeast(0)..nowHour).sumOf { hourly[it] }
} else null

val daySamples = runCatching { healthDataSource.heartRateSamples(dayStart, dayEnd) }.getOrDefault(emptyList())
val spark = daySamples.groupBy { Duration.between(dayStart, it.time).toMinutes() / 30 }
    .toSortedMap().values.map { bucket -> bucket.map { it.bpm }.average().toFloat() }

val birthYear = userPreferences.birthYear.first()
val vitalAge = birthYear?.let {
    val days = aggregates.map { a -> DailyVitals(
        date = LocalDate.parse(a.date), hrvMillis = a.avgHrvMillis,
        restingHr = a.restingHeartRate?.toDouble(), sleepMinutes = a.sleepDurationMinutes?.toDouble(),
        steps = a.steps.takeIf { s -> s > 0 }?.toDouble(),
        strain = scores.find { s -> s.date == a.date }?.strainValue
    ) }
    VitalAgeSeries.build(days, it, maxHrBpm = userPreferences.maxHeartRate.first())
        .lastOrNull()?.second?.vitalAge
}
```

Wire into state: `stressValue = score?.stressValue`, `zoneMinutes = score?.zoneMinutes`, `zoneMinutesYesterday = scores.find { it.date == selectedDate.minusDays(1).toString() }?.zoneMinutes`, plus the new lists. NavHost passes `ServiceLocator.userPreferencesRepository`.

- [ ] **Step 4: Run `TodayViewModelTest` — expect PASS**
- [ ] **Step 5: Commit** `feat: today view model data for metric grid (hourly steps, HR spark, stress, zones, vital age)`

---

### Task 6: Navigation + Overview UI

**Files:**
- Modify: `RecoveryTrackerNavHost.kt` — tabs `today("Overview", Icons.Filled.Home)`, `trends`, `settings`; `sleep` + `age` stay as `composable` routes (not tabs); `SleepScreen`/`VitalAgeScreen` get `onBack = { navController.popBackStack() }`; `TodayScreen` gets `onOpenSleep = { navController.navigate("sleep") }` and `onOpenAge = { navController.navigate("age") }`; settings composable called with no-op back.
- Modify: `SettingsScreen.kt` — remove the "Back" button (signature `SettingsScreen()`).
- Modify: `TodayScreen.kt` — full rebuild per spec §2.
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/today/StressGauge.kt`
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/today/MetricGridCards.kt`
- Modify: `VitalAgeScreen.kt` — add top back arrow row (param `onBack: () -> Unit`).

**Interfaces:**
- Consumes Task 5 uiState fields.
- `StressGauge(value: Double?, modifier: Modifier)` — semicircle track with green→amber→red sweep gradient + needle at `value/3` of 180°.
- `MetricGridCards.kt` holds `HeartRateCard`, `StepsCard`, `ZoneMinutesCard`, `StressCard` — each a `SectionCard`-style tile with header row (icon, label, optional chevron), big value, mini chart / caption per spec.

Key layout skeleton for `TodayScreen`:

```kotlin
// Header
Row(SpaceBetween) {
    Row { ColoredDot(AppColors.Recovery); Text("Recovery Tracker", titleLarge, Bold) }
    DatePill(uiState, onPrev = viewModel::goToPreviousDay, onNext = viewModel::goToNextDay)
}
// Rings — three equal, order Sleep / Recovery / Strain, 104.dp, stroke 8.dp
Row(SpaceEvenly) {
    RingColumn("Sleep", Icons.Filled.Bedtime, AppColors.SleepViolet, onClick = onOpenSleep) { ... CircularGauge(size 104.dp, strokeWidth = 8.dp) }
    RingColumn("Recovery", Icons.Filled.Favorite, recoveryColor(...), onClick = { openSheet = GaugeSheet.RECOVERY }) { ... }
    RingColumn("Strain", Icons.Filled.Bolt, AppColors.Strain, onClick = { openSheet = GaugeSheet.STRAIN }) { ... }
}
// Grid
Row(spacedBy(12.dp)) { HeartRateCard(Modifier.weight(1f), ...); StepsCard(Modifier.weight(1f), ...) }
Row(spacedBy(12.dp)) { ZoneMinutesCard(Modifier.weight(1f), ...); StressCard(Modifier.weight(1f), ...) }
// Vitals card (existing rows), Sleep card -> onOpenSleep, Vital Age card -> onOpenAge, footer
```

Sparkline in `HeartRateCard`: small `Canvas` polyline over normalized `heartRateSpark` (min..max), color `AppColors.HeartRate`, height 36.dp. Steps mini bars: `Row` of `Box`es for the last 6 hours with data (today: hours `nowHour-5..nowHour`; past days: the 6 busiest-contiguous… keep simple — last 6 hours of the day `18..23`), heights normalized. `StressCard` labels: `<1` "Low" (Recovery green), `1..<2` "Moderate" (Warning), `>=2` "High" (Danger).

- [ ] **Step 1: Implement UI (no unit tests — Compose screens are device-verified)**
- [ ] **Step 2: `assembleDebug` compiles + full test suite still green**
- [ ] **Step 3: Commit** `feat: openfit-style overview (3 tabs, ring row, metric grid) `

---

### Task 7: Sleep page rebuild

**Files:**
- Modify: `SleepViewModel.kt` — add to `SleepUiState`: `inBedMinutes: Long?`, `awakeMinutes: Long?`, `wakeEvents: Int?`, `needMinutes: Double?`, `sleepPerformance: Int?`; populate from detail (`inBed = Duration.between(start, end).toMinutes()`, `awake = (inBed - deep - rem - light).coerceAtLeast(0)`, `wakeEvents = stages.count { it.type == SleepStageType.AWAKE }`), need via `repository.sleepNeedMinutesFor(LocalDate.parse(latest.date))`, performance from `repository.observeRecentScores(...)` match on the latest sleep date.
- Create: `app/src/main/java/com/esusic/recoverytracker/ui/sleep/SleepHypnogram.kt`
- Modify: `SleepScreen.kt` — per spec §5 (top bar with back arrow, stage list incl. Awake with in-bed percent base, hypnogram with "NN% restorative", hours-vs-need card, wake events row).
- Test: `SleepViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthRepository.sleepNeedMinutesFor` (Task 4).
- `SleepHypnogram(stagePeriods, sessionStart, sessionEnd, modifier)` — 4 lanes Awake/REM/Light/Deep, rounded blocks per period, lane labels left, time labels below.

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `uiState exposes awake minutes wake events and need`() = runTest {
    // Session 480 min in bed with stages: DEEP 80, REM 90, LIGHT 230, AWAKE 20+? gaps;
    // two AWAKE periods. Assert inBedMinutes == 480, awakeMinutes == 480-400,
    // wakeEvents == 2, needMinutes != null (second synced night gives typical history).
}
```

- [ ] **Step 2: Run — expect FAIL**
- [ ] **Step 3: Implement VM + hypnogram + screen** (hypnogram lane mapping: `AWAKE→0, REM→1, LIGHT→2, DEEP→3`; block x from `Duration.between(sessionStart, period.start) / total`, width from period duration; `UNKNOWN` periods skipped)
- [ ] **Step 4: Run `SleepViewModelTest` — expect PASS; `assembleDebug` compiles**
- [ ] **Step 5: Commit** `feat: openfit-style sleep page (stage list, hypnogram, hours vs need)`

---

### Task 8: Full verification

- [ ] **Step 1:** `export JAVA_HOME=... && ./gradlew testDebugUnitTest` — all green
- [ ] **Step 2:** `./gradlew installDebug` on RFCY70GP8BH (update install — DB must survive, proves migration)
- [ ] **Step 3:** Force-stop, relaunch, uiautomator dumps:
  - Overview: 3 tabs (Overview/Trends/Settings), three rings with values, 4 grid cards (HR bpm, steps, zone min, stress value+label), vitals rows, sleep card, vital age card
  - Day browsing via pill chevrons still works (previous day shows stored scores — proves no DB wipe)
  - Tap sleep ring → Sleep page: back arrow, stage rows incl. Awake, hypnogram, hours vs need, wake events; back returns
  - Vital age card → Age page renders, back returns
  - `logcat -d | grep -c "FATAL EXCEPTION"` == 0
- [ ] **Step 4:** Final terse German report.

## Self-Review Notes

- Spec coverage: nav (T6), rings (T6), grid + stress + zone (T1-3, T5, T6), hourly steps (T4, T5), vitals/sleep/age cards (T6), sleep page (T4, T7), migration safety (T3, T8), backfill v4 (T3).
- Type consistency: `stepsIntervals` name used in T4/T5; `sleepNeedMinutesFor` in T4/T7; `stressValue`/`zoneMinutes` entity fields in T3/T5/T6.
- Steps mini-chart for past days simplified to fixed evening hours (18–23) — acceptable divergence noted in T6.
