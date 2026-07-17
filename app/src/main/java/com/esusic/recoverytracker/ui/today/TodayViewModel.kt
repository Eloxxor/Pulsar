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
