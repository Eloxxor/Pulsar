package com.esusic.recoverytracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.model.metricDelta
import com.esusic.recoverytracker.scoring.BaselineCalculator
import com.esusic.recoverytracker.scoring.DailyVitals
import com.esusic.recoverytracker.scoring.VitalAgeSeries
import com.esusic.recoverytracker.ui.common.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class TodayUiState(
    val recoveryValue: Int? = null,
    val isCalibrating: Boolean = false,
    val strainValue: Double? = null,
    val sleepPerformance: Int? = null,
    val sleepDurationMinutes: Long? = null,
    val deepSleepMinutes: Long? = null,
    val remSleepMinutes: Long? = null,
    val lightSleepMinutes: Long? = null,
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
    val strainHistory: List<ChartPoint> = emptyList(),
    val stressValue: Double? = null,
    val zoneMinutes: Int? = null,
    val zoneMinutesYesterday: Int? = null,
    val hourlySteps: List<Int> = List(24) { 0 },
    val stepsLast6h: Int? = null,
    val heartRateSpark: List<Float> = emptyList(),
    val vitalAge: Double? = null,
    val chronologicalAge: Int? = null
)

class TodayViewModel(
    private val repository: HealthRepository,
    private val healthDataSource: HealthDataSource,
    private val userPreferences: UserPreferencesRepository,
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
            // Baseline for delta chips: the 30 days before the selected day, so past
            // days are judged against what was normal back then.
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

            // Live Health Connect reads for the mini charts — empty for days outside
            // the 30-day read window; the cards fall back to number-only.
            val zone = ZoneId.systemDefault()
            val dayStart = selectedDate.atStartOfDay(zone).toInstant()
            val dayEnd = selectedDate.plusDays(1).atStartOfDay(zone).toInstant()
            val hourly = IntArray(24)
            runCatching { healthDataSource.stepsIntervals(dayStart, dayEnd) }
                .getOrDefault(emptyList())
                .forEach { interval -> hourly[interval.start.atZone(zone).hour] += interval.count }
            val nowHour = ZonedDateTime.now(clock).hour
            val stepsLast6h = if (selectedDate == today) {
                ((nowHour - 5).coerceAtLeast(0)..nowHour).sumOf { hourly[it] }
            } else {
                null
            }
            val spark = runCatching { healthDataSource.heartRateSamples(dayStart, dayEnd) }
                .getOrDefault(emptyList())
                .groupBy { Duration.between(dayStart, it.time).toMinutes() / 30 }
                .toSortedMap()
                .values.map { bucket -> bucket.map { it.bpm }.average().toFloat() }

            val birthYear = userPreferences.birthYear.first()
            val chronologicalAge = birthYear?.let { selectedDate.year - it }
            val vitalAge = birthYear?.let { year ->
                val days = aggregates.map { a ->
                    DailyVitals(
                        date = LocalDate.parse(a.date),
                        hrvMillis = a.avgHrvMillis,
                        restingHr = a.restingHeartRate?.toDouble(),
                        sleepMinutes = a.sleepDurationMinutes?.toDouble(),
                        steps = a.steps.takeIf { s -> s > 0 }?.toDouble(),
                        strain = scores.find { s -> s.date == a.date }?.strainValue
                    )
                }
                VitalAgeSeries.build(days, year, maxHrBpm = userPreferences.maxHeartRate.first())
                    .lastOrNull()?.second?.vitalAge
            }

            _uiState.value = TodayUiState(
                recoveryValue = score?.recoveryValue,
                isCalibrating = score?.recoveryIsCalibrating ?: false,
                strainValue = score?.strainValue,
                sleepPerformance = score?.sleepPerformance,
                sleepDurationMinutes = aggregate?.sleepDurationMinutes,
                deepSleepMinutes = aggregate?.deepSleepMinutes,
                remSleepMinutes = aggregate?.remSleepMinutes,
                lightSleepMinutes = aggregate?.lightSleepMinutes,
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
                strainHistory = ascendingScores.map { ChartPoint(LocalDate.parse(it.date), it.strainValue.toFloat()) },
                stressValue = score?.stressValue,
                zoneMinutes = score?.zoneMinutes,
                zoneMinutesYesterday = scores.find { it.date == selectedDate.minusDays(1).toString() }?.zoneMinutes,
                hourlySteps = hourly.toList(),
                stepsLast6h = stepsLast6h,
                heartRateSpark = spark,
                vitalAge = vitalAge,
                chronologicalAge = chronologicalAge
            )
        }
    }

    companion object {
        const val HISTORY_LIMIT = 60
    }
}
