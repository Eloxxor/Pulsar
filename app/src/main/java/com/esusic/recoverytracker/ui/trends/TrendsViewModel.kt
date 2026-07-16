package com.esusic.recoverytracker.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.model.metricDelta
import com.esusic.recoverytracker.ui.common.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TrendRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

data class MetricTrend(
    val points: List<ChartPoint> = emptyList(),
    val latest: Float? = null,
    val delta: MetricDelta? = null
)

data class TrendsUiState(
    val range: TrendRange = TrendRange.WEEK,
    val recovery: MetricTrend = MetricTrend(),
    val strain: MetricTrend = MetricTrend(),
    val hrv: MetricTrend = MetricTrend(),
    val rhr: MetricTrend = MetricTrend()
)

class TrendsViewModel(private val repository: HealthRepository) : ViewModel() {

    private val selectedRange = MutableStateFlow(TrendRange.WEEK)
    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectedRange.flatMapLatest { range ->
                combine(
                    repository.observeRecentScores(range.days),
                    repository.observeRecentAggregates(range.days)
                ) { scores, aggregates ->
                    val chronologicalScores = scores.reversed()
                    val chronologicalAggregates = aggregates.reversed()
                    TrendsUiState(
                        range = range,
                        recovery = buildTrend(
                            chronologicalScores.map { ChartPoint(LocalDate.parse(it.date), it.recoveryValue.toFloat()) },
                            higherIsBetter = true
                        ),
                        strain = buildTrend(
                            chronologicalScores.map { ChartPoint(LocalDate.parse(it.date), it.strainValue.toFloat()) },
                            higherIsBetter = null
                        ),
                        hrv = buildTrend(
                            chronologicalAggregates.mapNotNull { aggregate ->
                                aggregate.avgHrvMillis?.let { ChartPoint(LocalDate.parse(aggregate.date), it.toFloat()) }
                            },
                            higherIsBetter = true
                        ),
                        rhr = buildTrend(
                            chronologicalAggregates.mapNotNull { aggregate ->
                                aggregate.restingHeartRate?.let { ChartPoint(LocalDate.parse(aggregate.date), it.toFloat()) }
                            },
                            higherIsBetter = false
                        )
                    )
                }
            }.collect { _uiState.value = it }
        }
    }

    private fun buildTrend(points: List<ChartPoint>, higherIsBetter: Boolean?): MetricTrend {
        val latest = points.lastOrNull()?.value
        val priorAverage = points.dropLast(1)
            .map { it.value.toDouble() }
            .takeIf { it.isNotEmpty() }
            ?.average()
        return MetricTrend(
            points = points,
            latest = latest,
            delta = metricDelta(latest?.toDouble(), priorAverage, higherIsBetter)
        )
    }

    fun selectRange(range: TrendRange) {
        selectedRange.value = range
    }
}
