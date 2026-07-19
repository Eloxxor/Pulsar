package com.esusic.recoverytracker.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.data.local.DailyAggregateEntity
import com.esusic.recoverytracker.data.local.DailyScoreEntity
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.model.metricDelta
import com.esusic.recoverytracker.ui.common.ChartPoint
import com.esusic.recoverytracker.ui.date.SelectedDayStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class ActivityRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

data class MetricTrend(
    val points: List<ChartPoint> = emptyList(),
    val latest: Float? = null,
    val delta: MetricDelta? = null
)

data class ActivityUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = LocalDate.now(),
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val strainValue: Double? = null,
    val recoveryValue: Int? = null,
    val steps: Int? = null,
    val zoneMinutes: Int? = null,
    val latestHeartRateBpm: Long? = null,
    val hourlySteps: List<Int> = List(24) { 0 },
    val range: ActivityRange = ActivityRange.WEEK,
    val recovery: MetricTrend = MetricTrend(),
    val strain: MetricTrend = MetricTrend(),
    val hrv: MetricTrend = MetricTrend(),
    val rhr: MetricTrend = MetricTrend()
)

private data class ActivitySnapshot(
    val date: LocalDate,
    val range: ActivityRange,
    val scores: List<DailyScoreEntity>,
    val aggregates: List<DailyAggregateEntity>
)

class ActivityViewModel(
    private val repository: HealthRepository,
    private val healthDataSource: HealthDataSource,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val selectedDayStore: SelectedDayStore = SelectedDayStore(clock)
) : ViewModel() {
    private val selectedRange = MutableStateFlow(ActivityRange.WEEK)
    private val _uiState = MutableStateFlow(
        ActivityUiState(selectedDate = selectedDayStore.selectedDate.value)
    )
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                selectedDayStore.selectedDate,
                selectedRange,
                repository.observeRecentScores(ActivityRange.QUARTER.days),
                repository.observeRecentAggregates(ActivityRange.QUARTER.days)
            ) { date, range, scores, aggregates ->
                ActivitySnapshot(date, range, scores, aggregates)
            }.collectLatest(::publishSnapshot)
        }
    }

    fun selectRange(range: ActivityRange) {
        selectedRange.value = range
    }

    fun selectDate(date: LocalDate) {
        selectedDayStore.select(date)
    }

    fun goToPreviousDay() = selectedDayStore.previous()

    fun goToNextDay() = selectedDayStore.next()

    private suspend fun publishSnapshot(snapshot: ActivitySnapshot) {
        val dateString = snapshot.date.toString()
        val score = snapshot.scores.find { it.date == dateString }
        val aggregate = snapshot.aggregates.find { it.date == dateString }
        val rangeScores = snapshot.scores
            .filter { it.date <= dateString }
            .take(snapshot.range.days)
            .reversed()
        val rangeAggregates = snapshot.aggregates
            .filter { it.date <= dateString }
            .take(snapshot.range.days)
            .reversed()

        val start = snapshot.date.atStartOfDay(clock.zone).toInstant()
        val end = snapshot.date.plusDays(1).atStartOfDay(clock.zone).toInstant()
        val hourly = IntArray(24)
        runCatching { healthDataSource.stepsIntervals(start, end) }
            .getOrDefault(emptyList())
            .forEach { interval -> hourly[interval.start.atZone(clock.zone).hour] += interval.count }

        _uiState.value = ActivityUiState(
            isLoading = false,
            selectedDate = snapshot.date,
            canGoBack = snapshot.aggregates.any { it.date < dateString },
            canGoForward = snapshot.date < LocalDate.now(clock),
            strainValue = score?.strainValue,
            recoveryValue = score?.recoveryValue,
            steps = aggregate?.steps,
            zoneMinutes = score?.zoneMinutes,
            latestHeartRateBpm = aggregate?.latestHeartRateBpm,
            hourlySteps = hourly.toList(),
            range = snapshot.range,
            recovery = buildTrend(
                rangeScores.map { ChartPoint(LocalDate.parse(it.date), it.recoveryValue.toFloat()) },
                higherIsBetter = true
            ),
            strain = buildTrend(
                rangeScores.map { ChartPoint(LocalDate.parse(it.date), it.strainValue.toFloat()) },
                higherIsBetter = null
            ),
            hrv = buildTrend(
                rangeAggregates.mapNotNull { aggregateEntity ->
                    aggregateEntity.avgHrvMillis?.let {
                        ChartPoint(LocalDate.parse(aggregateEntity.date), it.toFloat())
                    }
                },
                higherIsBetter = true
            ),
            rhr = buildTrend(
                rangeAggregates.mapNotNull { aggregateEntity ->
                    aggregateEntity.restingHeartRate?.let {
                        ChartPoint(LocalDate.parse(aggregateEntity.date), it.toFloat())
                    }
                },
                higherIsBetter = false
            )
        )
    }

    private fun buildTrend(points: List<ChartPoint>, higherIsBetter: Boolean?): MetricTrend {
        val latest = points.lastOrNull()?.value
        val priorAverage = points.dropLast(1).map { it.value.toDouble() }
            .takeIf { it.isNotEmpty() }?.average()
        return MetricTrend(
            points = points,
            latest = latest,
            delta = metricDelta(latest?.toDouble(), priorAverage, higherIsBetter)
        )
    }
}
