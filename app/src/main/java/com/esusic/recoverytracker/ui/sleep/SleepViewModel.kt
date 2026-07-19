package com.esusic.recoverytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.model.metricDelta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class SleepUiState(
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
    val sleepPerformance: Int? = null
)

class SleepViewModel(private val repository: HealthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    // The chart detail must follow the latest night that actually has sleep — right
    // after midnight LocalDate.now() has none yet and the chart would never load.
    private var detailLoadedForDate: String? = null

    init {
        viewModelScope.launch {
            repository.observeRecentAggregates(30).collect { aggregates ->
                val latestIndex = aggregates.indexOfFirst { it.sleepDurationMinutes != null }
                val latest = if (latestIndex >= 0) aggregates[latestIndex] else null
                val priorNights = if (latestIndex >= 0) aggregates.drop(latestIndex + 1) else aggregates
                val history = priorNights.mapNotNull { it.sleepDurationMinutes }
                val typical = if (history.isEmpty()) null else history.map { it.toDouble() }.average()

                if (latest?.sleepDurationMinutes == null || latest.sleepDurationMinutes <= 0) {
                    _uiState.value = _uiState.value.copy(
                        totalMinutes = null,
                        deepMinutes = null,
                        remMinutes = null,
                        lightMinutes = null,
                        deepPercent = 0f,
                        remPercent = 0f,
                        lightPercent = 0f,
                        typicalTotalMinutes = typical,
                        durationDelta = null
                    )
                } else {
                    val total = latest.sleepDurationMinutes
                    val deep = latest.deepSleepMinutes ?: 0L
                    val rem = latest.remSleepMinutes ?: 0L
                    val light = latest.lightSleepMinutes ?: 0L
                    _uiState.value = _uiState.value.copy(
                        totalMinutes = total,
                        deepMinutes = deep,
                        remMinutes = rem,
                        lightMinutes = light,
                        deepPercent = deep.toFloat() / total,
                        remPercent = rem.toFloat() / total,
                        lightPercent = light.toFloat() / total,
                        typicalTotalMinutes = typical,
                        durationDelta = metricDelta(
                            total.toDouble(),
                            typical,
                            higherIsBetter = true,
                            neutralBandFraction = 0.05
                        )
                    )
                    if (latest.date != detailLoadedForDate) {
                        detailLoadedForDate = latest.date
                        val latestDate = LocalDate.parse(latest.date)
                        val detail = repository.sleepDetailForDate(latestDate)
                        val need = repository.sleepNeedMinutesFor(latestDate)
                        val performance = repository.observeRecentScores(30).first()
                            .find { it.date == latest.date }?.sleepPerformance
                        if (detail != null) {
                            val inBed = java.time.Duration.between(detail.session.start, detail.session.end).toMinutes()
                            _uiState.value = _uiState.value.copy(
                                heartRateBuckets = bucketHeartRateSamples(detail.heartRateSamples, detail.session.start),
                                stagePeriods = detail.session.stages,
                                sessionStart = detail.session.start,
                                sessionEnd = detail.session.end,
                                inBedMinutes = inBed,
                                awakeMinutes = (inBed - (deep + rem + light)).coerceAtLeast(0L),
                                wakeEvents = detail.session.stages.count { it.stage == SleepStageType.AWAKE },
                                needMinutes = need,
                                sleepPerformance = performance
                            )
                        }
                    }
                }
            }
        }
    }
}
