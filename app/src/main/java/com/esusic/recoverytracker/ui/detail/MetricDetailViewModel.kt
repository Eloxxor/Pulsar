package com.esusic.recoverytracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.local.DailyAggregateEntity
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.model.metricDelta
import com.esusic.recoverytracker.scoring.BaselineCalculator
import com.esusic.recoverytracker.ui.common.ChartPoint
import com.esusic.recoverytracker.ui.common.TrendRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MetricDetailUiState(
    val range: TrendRange = TrendRange.MONTH,
    val todayValue: Double? = null,
    val baselineMean: Double? = null,
    val delta: MetricDelta? = null,
    val history: List<ChartPoint> = emptyList()
)

class MetricDetailViewModel(
    private val repository: HealthRepository,
    private val metricType: MetricType
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TrendRange.MONTH)
    private val _uiState = MutableStateFlow(MetricDetailUiState())
    val uiState: StateFlow<MetricDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                selectedRange,
                repository.observeRecentAggregates(TrendRange.QUARTER.days)
            ) { range, aggregates -> range to aggregates }
                .collectLatest { (range, aggregates) -> publish(range, aggregates) }
        }
    }

    fun selectRange(range: TrendRange) {
        selectedRange.value = range
    }

    private fun publish(range: TrendRange, aggregates: List<DailyAggregateEntity>) {
        val chronological = aggregates.take(range.days).reversed()
        val points = chronological.mapNotNull { entity ->
            valueFor(entity)?.let { ChartPoint(LocalDate.parse(entity.date), it.toFloat()) }
        }
        val today = chronological.lastOrNull()?.let { valueFor(it) }
        val baseline = BaselineCalculator.calculate(points.dropLast(1).map { it.value.toDouble() })
        val baselineMean = baseline.mean.takeIf { baseline.sampleCount > 0 }

        _uiState.value = MetricDetailUiState(
            range = range,
            todayValue = today,
            baselineMean = baselineMean,
            delta = metricDelta(today, baselineMean, metricType.higherIsBetter),
            history = points
        )
    }

    private fun valueFor(entity: DailyAggregateEntity): Double? = when (metricType) {
        MetricType.HEART_RATE -> entity.restingHeartRate?.toDouble()
        MetricType.ACTIVITY -> entity.steps.toDouble()
        MetricType.HRV -> entity.avgHrvMillis
    }
}
