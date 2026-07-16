package com.esusic.recoverytracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.local.DailyAggregateEntity
import com.esusic.recoverytracker.scoring.BaselineCalculator
import com.esusic.recoverytracker.ui.common.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MetricDetailUiState(
    val todayValue: Double? = null,
    val baselineMean: Double? = null,
    val history: List<ChartPoint> = emptyList()
)

class MetricDetailViewModel(
    private val repository: HealthRepository,
    private val metricType: MetricType
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetricDetailUiState())
    val uiState: StateFlow<MetricDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecentAggregates(BaselineCalculator.WINDOW_DAYS).collect { aggregates ->
                val chronological = aggregates.reversed()
                val points = chronological.mapNotNull { entity ->
                    valueFor(entity)?.let { ChartPoint(LocalDate.parse(entity.date), it.toFloat()) }
                }
                val today = chronological.lastOrNull()?.let { valueFor(it) }
                val baseline = BaselineCalculator.calculate(points.dropLast(1).map { it.value.toDouble() })

                _uiState.value = MetricDetailUiState(
                    todayValue = today,
                    baselineMean = baseline.mean.takeIf { baseline.sampleCount > 0 },
                    history = points
                )
            }
        }
    }

    private fun valueFor(entity: DailyAggregateEntity): Double? = when (metricType) {
        MetricType.HEART_RATE -> entity.restingHeartRate?.toDouble()
        MetricType.ACTIVITY -> entity.steps.toDouble()
        MetricType.HRV -> entity.avgHrvMillis
    }
}
