# Day Browsing + Gauge History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Arrow-based day navigation on the Today screen (data from Room) and 30-day history charts inside the three gauge bottom sheets.

**Architecture:** `TodayViewModel` gets a selected-date concept and exposes score histories; `TodayScreen` header gets chevrons; `GaugeInfoSheets` render `MetricLineChart` from the new state lists. No DB or repository-API changes beyond reads.

**Tech Stack:** Kotlin, Compose, Room (read-only), JUnit4 + Robolectric.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-17-day-browsing-and-gauge-history-design.md`.
- `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17"` per Gradle call; commits via `git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit`; `master`.

---

### Task 1: TodayViewModel day selection + histories

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt`
- Test: `app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt`

**Interfaces:**
- Produces: `TodayUiState` + `selectedDate: LocalDate = LocalDate.now()`, `isToday: Boolean = true`, `canGoBack: Boolean = false`, `canGoForward: Boolean = false`, `recoveryHistory/sleepPerformanceHistory/strainHistory: List<ChartPoint> = emptyList()`; `TodayViewModel(repository, healthDataSource, clock = Clock.systemDefaultZone())` with `selectDate(LocalDate)`, `goToPreviousDay()`, `goToNextDay()`.

- [ ] **Step 1: Failing tests** (append to `TodayViewModelTest`)

```kotlin
    @Test
    fun `selectDate shows the persisted values of a past day without re-syncing`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        val today = LocalDate.now()
        val source = FakeHealthDataSource(
            restingHr = listOf(
                RestingHrSample(yesterday.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(60), 50L),
                RestingHrSample(today.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(60), 46L)
            )
        )
        val repository = repositoryWith(source)
        repository.syncDate(yesterday)

        val viewModel = TodayViewModel(repository, source)
        viewModel.refresh()
        viewModel.selectDate(yesterday)

        assertEquals(yesterday, viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.isToday)
        assertEquals(50L, viewModel.uiState.value.restingHeartRate)
        assertEquals(true, viewModel.uiState.value.canGoForward)
    }

    @Test
    fun `score histories are exposed ascending for the gauge sheets`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        val source = FakeHealthDataSource(heartRate = listOf(HeartRateSample(Instant.now(), 65L)))
        val repository = repositoryWith(source)
        repository.syncDate(yesterday)

        val viewModel = TodayViewModel(repository, source)
        viewModel.refresh()

        assertEquals(2, viewModel.uiState.value.strainHistory.size)
        assertEquals(yesterday, viewModel.uiState.value.strainHistory.first().date)
        assertEquals(2, viewModel.uiState.value.recoveryHistory.size)
    }
```

- [ ] **Step 2: RED** — `Unresolved reference 'selectDate'` etc.

Run: `export JAVA_HOME="C:/Users/Elvedin/dev-tools/jdk17" && ./gradlew :app:testDebugUnitTest --tests "com.esusic.recoverytracker.ui.today.TodayViewModelTest"`

- [ ] **Step 3: Implement** — rewrite `TodayViewModel.kt`:

```kotlin
package com.esusic.recoverytracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.model.metricDelta
import com.esusic.recoverytracker.scoring.BaselineCalculator
import com.esusic.recoverytracker.ui.common.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class TodayUiState(
    val recoveryValue: Int? = null,
    val isCalibrating: Boolean = false,
    val strainValue: Double? = null,
    val sleepPerformance: Int? = null,
    val sleepDurationMinutes: Long? = null,
    val restingHeartRate: Long? = null,
    val avgHrvMillis: Double? = null,
    val steps: Int? = null,
    val avgRespiratoryRate: Double? = null,
    val avgOxygenSaturationPercent: Double? = null,
    val avgSkinTemperatureDeltaCelsius: Double? = null,
    val latestHeartRateBpm: Long? = null,
    val latestHeartRateEpochMillis: Long? = null,
    val lastSyncedAtEpochMillis: Long? = null,
    val hasAllPermissions: Boolean = true,
    val isRefreshing: Boolean = false,
    val hrvSubScore: Double? = null,
    val rhrSubScore: Double? = null,
    val sleepSubScore: Double? = null,
    val rhrDelta: MetricDelta? = null,
    val hrvDelta: MetricDelta? = null,
    val respRateDelta: MetricDelta? = null,
    val spo2Delta: MetricDelta? = null,
    val skinTempDelta: MetricDelta? = null,
    val sleepDelta: MetricDelta? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val isToday: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val recoveryHistory: List<ChartPoint> = emptyList(),
    val sleepPerformanceHistory: List<ChartPoint> = emptyList(),
    val strainHistory: List<ChartPoint> = emptyList()
)

class TodayViewModel(
    private val repository: HealthRepository,
    private val healthDataSource: HealthDataSource,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState(selectedDate = LocalDate.now(clock)))
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private var selectedDate: LocalDate = LocalDate.now(clock)

    init {
        refresh()
    }

    fun refresh() = load(sync = true)

    fun selectDate(date: LocalDate) {
        selectedDate = date.coerceAtMost(LocalDate.now(clock))
        load(sync = false)
    }

    fun goToPreviousDay() = selectDate(selectedDate.minusDays(1))

    fun goToNextDay() = selectDate(selectedDate.plusDays(1))

    private fun load(sync: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            val hasPermissions = healthDataSource.hasAllPermissions()
            if (!hasPermissions) {
                _uiState.value = _uiState.value.copy(hasAllPermissions = false, isRefreshing = false)
                return@launch
            }

            if (sync) repository.syncToday()

            val today = LocalDate.now(clock)
            val dateString = selectedDate.toString()
            val aggregates = repository.observeRecentAggregates(HISTORY_LIMIT).first()
            val scores = repository.observeRecentScores(HISTORY_LIMIT).first()
            val aggregate = aggregates.find { it.date == dateString }
            val score = scores.find { it.date == dateString }
            val history = aggregates.filter { it.date < dateString }.take(BaselineCalculator.WINDOW_DAYS)

            fun baselineMean(values: List<Double>): Double? =
                BaselineCalculator.calculate(values).takeIf { it.sampleCount > 0 }?.mean

            val rhrBaseline = baselineMean(history.mapNotNull { it.restingHeartRate?.toDouble() })
            val hrvBaseline = baselineMean(history.mapNotNull { it.avgHrvMillis })
            val respBaseline = baselineMean(history.mapNotNull { it.avgRespiratoryRate })
            val spo2Baseline = baselineMean(history.mapNotNull { it.avgOxygenSaturationPercent })
            val skinTempBaseline = baselineMean(history.mapNotNull { it.avgSkinTemperatureDeltaCelsius })
            val sleepBaseline = baselineMean(history.mapNotNull { it.sleepDurationMinutes?.toDouble() })

            val ascendingScores = scores.sortedBy { it.date }

            _uiState.value = TodayUiState(
                recoveryValue = score?.recoveryValue,
                isCalibrating = score?.recoveryIsCalibrating ?: false,
                strainValue = score?.strainValue,
                sleepPerformance = score?.sleepPerformance,
                sleepDurationMinutes = aggregate?.sleepDurationMinutes,
                restingHeartRate = aggregate?.restingHeartRate,
                avgHrvMillis = aggregate?.avgHrvMillis,
                steps = aggregate?.steps,
                avgRespiratoryRate = aggregate?.avgRespiratoryRate,
                avgOxygenSaturationPercent = aggregate?.avgOxygenSaturationPercent,
                avgSkinTemperatureDeltaCelsius = aggregate?.avgSkinTemperatureDeltaCelsius,
                latestHeartRateBpm = aggregate?.latestHeartRateBpm,
                latestHeartRateEpochMillis = aggregate?.latestHeartRateEpochMillis,
                lastSyncedAtEpochMillis = score?.lastSyncedAtEpochMillis,
                hasAllPermissions = true,
                isRefreshing = false,
                hrvSubScore = score?.hrvSubScore,
                rhrSubScore = score?.rhrSubScore,
                sleepSubScore = score?.sleepSubScore,
                rhrDelta = metricDelta(aggregate?.restingHeartRate?.toDouble(), rhrBaseline, higherIsBetter = false),
                hrvDelta = metricDelta(aggregate?.avgHrvMillis, hrvBaseline, higherIsBetter = true),
                respRateDelta = metricDelta(aggregate?.avgRespiratoryRate, respBaseline, higherIsBetter = null),
                spo2Delta = metricDelta(aggregate?.avgOxygenSaturationPercent, spo2Baseline, higherIsBetter = true, neutralBandFraction = 0.01),
                skinTempDelta = metricDelta(aggregate?.avgSkinTemperatureDeltaCelsius, skinTempBaseline, higherIsBetter = null),
                sleepDelta = metricDelta(aggregate?.sleepDurationMinutes?.toDouble(), sleepBaseline, higherIsBetter = true, neutralBandFraction = 0.05),
                selectedDate = selectedDate,
                isToday = selectedDate == today,
                canGoBack = aggregates.any { it.date < dateString },
                canGoForward = selectedDate < today,
                recoveryHistory = ascendingScores.map { ChartPoint(LocalDate.parse(it.date), it.recoveryValue.toFloat()) },
                sleepPerformanceHistory = ascendingScores.mapNotNull { s ->
                    s.sleepPerformance?.let { ChartPoint(LocalDate.parse(s.date), it.toFloat()) }
                },
                strainHistory = ascendingScores.map { ChartPoint(LocalDate.parse(it.date), it.strainValue.toFloat()) }
            )
        }
    }

    companion object {
        const val HISTORY_LIMIT = 60
    }
}
```

- [ ] **Step 4: GREEN** — same test command; all `TodayViewModelTest` pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/today/TodayViewModel.kt app/src/test/java/com/esusic/recoverytracker/ui/today/TodayViewModelTest.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: browse past days in TodayViewModel and expose score histories"
```

---

### Task 2: Header arrows + sheet charts

**Files:**
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt`
- Modify: `app/src/main/java/com/esusic/recoverytracker/ui/today/GaugeInfoSheets.kt`

**Interfaces:**
- Consumes: Task 1 state fields; `MetricLineChart(points, lineColor, modifier, valueFormatter, onSelect)`.

- [ ] **Step 1: Header** — replace the date/title `Column` in `TodayScreen` with chevron row:

```kotlin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.goToPreviousDay() }, enabled = uiState.canGoBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous day",
                            tint = if (uiState.canGoBack) AppColors.TextSecondary else AppColors.TextMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Eyebrow(uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEEE · MMM d", Locale.ENGLISH)))
                        Text(
                            when {
                                uiState.isToday -> "Today"
                                uiState.selectedDate == LocalDate.now().minusDays(1) -> "Yesterday"
                                else -> uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH))
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = { viewModel.goToNextDay() }, enabled = uiState.canGoForward) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next day",
                            tint = if (uiState.canGoForward) AppColors.TextSecondary else AppColors.TextMuted
                        )
                    }
                }
```

(import `androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft`)

- [ ] **Step 2: Sheet charts** — in `GaugeInfoSheets.kt` add a private composable and call it at the end of each sheet content:

```kotlin
@Composable
private fun HistoryChart(points: List<ChartPoint>, color: Color, valueFormatter: (Float) -> String = { "%.0f".format(it) }) {
    if (points.size < 2) return
    Eyebrow("Last 30 days")
    MetricLineChart(points = points, lineColor = color, valueFormatter = valueFormatter)
}
```

Calls: Recovery sheet → `HistoryChart(uiState.recoveryHistory, AppColors.Recovery)`; Sleep sheet → `HistoryChart(uiState.sleepPerformanceHistory, AppColors.SleepViolet)`; Strain sheet → `HistoryChart(uiState.strainHistory, AppColors.Strain) { "%.1f".format(it) }`.
(imports: `ChartPoint`, `MetricLineChart`, `Eyebrow` already imported.)

- [ ] **Step 3: Compile + full suite** — `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` → PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/esusic/recoverytracker/ui/today/TodayScreen.kt app/src/main/java/com/esusic/recoverytracker/ui/today/GaugeInfoSheets.kt
git -c user.name="Elvedin" -c user.email="elv.susic@gmail.com" commit -m "feat: day navigation header and 30-day charts in gauge sheets"
```

---

### Task 3: Install + device check

- [ ] `./gradlew :app:installDebug` → "Installed on 1 device."
- [ ] Force-stop, relaunch, uiautomator-dump: chevrons present; tap `‹` shows Yesterday with that day's vitals; recovery sheet shows "LAST 30 DAYS"; no AndroidRuntime crash.
