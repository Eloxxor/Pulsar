package com.esusic.recoverytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.local.DailyAggregateEntity
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.model.metricDelta
import com.esusic.recoverytracker.ui.date.SelectedDayStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class SleepNightPoint(val date: LocalDate, val durationMinutes: Long)

data class SleepUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val totalMinutes: Long? = null,
    val deepMinutes: Long? = null,
    val remMinutes: Long? = null,
    val lightMinutes: Long? = null,
    val deepPercent: Float = 0f,
    val remPercent: Float = 0f,
    val lightPercent: Float = 0f,
    val typicalTotalMinutes: Double? = null,
    val durationDelta: MetricDelta? = null,
    val heartRateBuckets: List<HeartRateBucket> = emptyList(),
    val stagePeriods: List<SleepStagePeriod> = emptyList(),
    val sessionStart: Instant? = null,
    val sessionEnd: Instant? = null,
    val inBedMinutes: Long? = null,
    val awakeMinutes: Long? = null,
    val wakeEvents: Int? = null,
    val needMinutes: Double? = null,
    val sleepPerformance: Int? = null,
    val weeklyNights: List<SleepNightPoint> = emptyList(),
    val weeklyAverageMinutes: Double? = null,
    val weeklyInsight: String? = null
)

class SleepViewModel(
    private val repository: HealthRepository,
    private val selectedDayStore: SelectedDayStore = SelectedDayStore()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SleepUiState(selectedDate = selectedDayStore.selectedDate.value)
    )
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                selectedDayStore.selectedDate,
                repository.observeRecentAggregates(30)
            ) { date, aggregates -> date to aggregates }
                .collectLatest { (date, aggregates) -> publishDate(date, aggregates) }
        }
    }

    fun goToPreviousDay() = selectedDayStore.previous()

    fun goToNextDay() = selectedDayStore.next()

    private suspend fun publishDate(
        date: LocalDate,
        aggregates: List<DailyAggregateEntity>
    ) {
        val dateString = date.toString()
        val selected = aggregates.find { it.date == dateString }
        val priorNights = aggregates.filter { it.date < dateString }
        val weeklyNights = aggregates
            .filter { it.date <= dateString }
            .mapNotNull { aggregate ->
                aggregate.sleepDurationMinutes?.let { duration ->
                    SleepNightPoint(LocalDate.parse(aggregate.date), duration)
                }
            }
            .take(7)
            .reversed()
        val history = priorNights.mapNotNull { it.sleepDurationMinutes }
        val typical = history.takeIf { it.isNotEmpty() }
            ?.map { it.toDouble() }
            ?.average()
        val base = SleepUiState(
            selectedDate = date,
            canGoBack = aggregates.any { it.date < dateString },
            canGoForward = date < selectedDayStore.today(),
            typicalTotalMinutes = typical,
            weeklyNights = weeklyNights,
            weeklyAverageMinutes = weeklyNights.takeIf { it.isNotEmpty() }
                ?.map { it.durationMinutes.toDouble() }
                ?.average(),
            weeklyInsight = weeklyInsight(weeklyNights)
        )
        val total = selected?.sleepDurationMinutes
        if (total == null || total <= 0) {
            _uiState.value = base
            return
        }

        val deep = selected.deepSleepMinutes
        val rem = selected.remSleepMinutes
        val light = selected.lightSleepMinutes
        var state = base.copy(
            totalMinutes = total,
            deepMinutes = deep,
            remMinutes = rem,
            lightMinutes = light,
            deepPercent = deep?.toFloat()?.div(total) ?: 0f,
            remPercent = rem?.toFloat()?.div(total) ?: 0f,
            lightPercent = light?.toFloat()?.div(total) ?: 0f,
            durationDelta = metricDelta(
                total.toDouble(),
                typical,
                higherIsBetter = true,
                neutralBandFraction = 0.05
            )
        )
        val detail = repository.sleepDetailForDate(date)
        val need = repository.sleepNeedMinutesFor(date)
        val performance = repository.observeRecentScores(30).first()
            .find { it.date == dateString }
            ?.sleepPerformance
        if (detail != null) {
            val inBed = Duration.between(detail.session.start, detail.session.end).toMinutes()
            val hasStageDetail = detail.session.stages.isNotEmpty()
            val awake = detail.session.stages
                .filter { it.stage == SleepStageType.AWAKE }
                .sumOf { Duration.between(it.start, it.end).toMinutes() }
            state = state.copy(
                heartRateBuckets = bucketHeartRateSamples(
                    detail.heartRateSamples,
                    detail.session.start
                ),
                stagePeriods = detail.session.stages,
                sessionStart = detail.session.start,
                sessionEnd = detail.session.end,
                inBedMinutes = inBed,
                deepMinutes = deep.takeIf { hasStageDetail },
                remMinutes = rem.takeIf { hasStageDetail },
                lightMinutes = light.takeIf { hasStageDetail },
                awakeMinutes = awake.takeIf { hasStageDetail },
                wakeEvents = detail.session.stages
                    .count { it.stage == SleepStageType.AWAKE }
                    .takeIf { hasStageDetail }
            )
        }
        _uiState.value = state.copy(
            needMinutes = need,
            sleepPerformance = performance
        )
    }

    private fun weeklyInsight(nights: List<SleepNightPoint>): String? {
        if (nights.size < 2) return null
        val previousAverage = nights.dropLast(1).map { it.durationMinutes }.average()
        val latest = nights.last().durationMinutes
        return when {
            latest >= previousAverage + 30.0 -> "Your sleep duration improved this week."
            latest <= previousAverage - 30.0 ->
                "Your latest night was shorter than your recent average."
            else -> "Your sleep duration is holding steady."
        }
    }
}
