package com.esusic.recoverytracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.model.metricDelta
import com.esusic.recoverytracker.scoring.BaselineCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val sleepDelta: MetricDelta? = null
)

class TodayViewModel(
    private val repository: HealthRepository,
    private val healthDataSource: HealthDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            val hasPermissions = healthDataSource.hasAllPermissions()
            if (!hasPermissions) {
                _uiState.value = _uiState.value.copy(hasAllPermissions = false, isRefreshing = false)
                return@launch
            }

            val score = repository.syncToday()
            val aggregates = repository.observeRecentAggregates(BaselineCalculator.WINDOW_DAYS + 1).first()
            val aggregate = aggregates.firstOrNull()
            val history = aggregates.drop(1)

            fun baselineMean(values: List<Double>): Double? =
                BaselineCalculator.calculate(values).takeIf { it.sampleCount > 0 }?.mean

            val rhrBaseline = baselineMean(history.mapNotNull { it.restingHeartRate?.toDouble() })
            val hrvBaseline = baselineMean(history.mapNotNull { it.avgHrvMillis })
            val respBaseline = baselineMean(history.mapNotNull { it.avgRespiratoryRate })
            val spo2Baseline = baselineMean(history.mapNotNull { it.avgOxygenSaturationPercent })
            val skinTempBaseline = baselineMean(history.mapNotNull { it.avgSkinTemperatureDeltaCelsius })
            val sleepBaseline = baselineMean(history.mapNotNull { it.sleepDurationMinutes?.toDouble() })

            _uiState.value = TodayUiState(
                recoveryValue = score.recoveryValue,
                isCalibrating = score.recoveryIsCalibrating,
                strainValue = score.strainValue,
                sleepPerformance = score.sleepPerformance,
                sleepDurationMinutes = aggregate?.sleepDurationMinutes,
                restingHeartRate = aggregate?.restingHeartRate,
                avgHrvMillis = aggregate?.avgHrvMillis,
                steps = aggregate?.steps,
                avgRespiratoryRate = aggregate?.avgRespiratoryRate,
                avgOxygenSaturationPercent = aggregate?.avgOxygenSaturationPercent,
                avgSkinTemperatureDeltaCelsius = aggregate?.avgSkinTemperatureDeltaCelsius,
                latestHeartRateBpm = aggregate?.latestHeartRateBpm,
                latestHeartRateEpochMillis = aggregate?.latestHeartRateEpochMillis,
                lastSyncedAtEpochMillis = score.lastSyncedAtEpochMillis,
                hasAllPermissions = true,
                isRefreshing = false,
                hrvSubScore = score.hrvSubScore,
                rhrSubScore = score.rhrSubScore,
                sleepSubScore = score.sleepSubScore,
                rhrDelta = metricDelta(aggregate?.restingHeartRate?.toDouble(), rhrBaseline, higherIsBetter = false),
                hrvDelta = metricDelta(aggregate?.avgHrvMillis, hrvBaseline, higherIsBetter = true),
                respRateDelta = metricDelta(aggregate?.avgRespiratoryRate, respBaseline, higherIsBetter = null),
                spo2Delta = metricDelta(aggregate?.avgOxygenSaturationPercent, spo2Baseline, higherIsBetter = true, neutralBandFraction = 0.01),
                skinTempDelta = metricDelta(aggregate?.avgSkinTemperatureDeltaCelsius, skinTempBaseline, higherIsBetter = null),
                sleepDelta = metricDelta(aggregate?.sleepDurationMinutes?.toDouble(), sleepBaseline, higherIsBetter = true, neutralBandFraction = 0.05)
            )
        }
    }
}
