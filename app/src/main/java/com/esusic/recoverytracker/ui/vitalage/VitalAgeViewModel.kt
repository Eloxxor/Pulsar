package com.esusic.recoverytracker.ui.vitalage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.DeltaKind
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.scoring.DailyVitals
import com.esusic.recoverytracker.scoring.VitalAgeResult
import com.esusic.recoverytracker.scoring.VitalAgeSeries
import com.esusic.recoverytracker.ui.common.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.math.abs

data class VitalAgeUiState(
    val isLoading: Boolean = true,
    val chronologicalAge: Int? = null,
    val result: VitalAgeResult? = null,
    val weeklyDelta: MetricDelta? = null,
    val history: List<ChartPoint> = emptyList(),
    val daysUntilReady: Int? = null
)

class VitalAgeViewModel(
    private val repository: HealthRepository,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VitalAgeUiState())
    val uiState: StateFlow<VitalAgeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val birthYear = userPreferences.birthYear.first()
            if (birthYear == null) {
                _uiState.value = VitalAgeUiState(isLoading = false)
                return@launch
            }

            val aggregates = repository.observeRecentAggregates(HISTORY_DAYS).first()
            val strainByDate = repository.observeRecentScores(HISTORY_DAYS).first()
                .associate { it.date to it.strainValue }
            val days = aggregates.map { aggregate ->
                DailyVitals(
                    date = LocalDate.parse(aggregate.date),
                    hrvMillis = aggregate.avgHrvMillis,
                    restingHr = aggregate.restingHeartRate?.toDouble(),
                    sleepMinutes = aggregate.sleepDurationMinutes?.toDouble(),
                    steps = aggregate.steps.takeIf { it > 0 }?.toDouble(),
                    strain = strainByDate[aggregate.date]
                )
            }

            val series = VitalAgeSeries.build(days, birthYear)
            val latest = series.lastOrNull()
            val weekBefore = latest?.let { (date, _) -> series.find { it.first == date.minusDays(7) } }
            val weeklyDelta = if (latest != null && weekBefore != null) {
                val diff = latest.second.vitalAge - weekBefore.second.vitalAge
                MetricDelta(
                    diff,
                    when {
                        abs(diff) < 0.1 -> DeltaKind.NEUTRAL
                        diff < 0 -> DeltaKind.BETTER
                        else -> DeltaKind.WORSE
                    }
                )
            } else {
                null
            }

            _uiState.value = VitalAgeUiState(
                isLoading = false,
                chronologicalAge = LocalDate.now(clock).year - birthYear,
                result = latest?.second,
                weeklyDelta = weeklyDelta,
                history = series.map { (date, result) -> ChartPoint(date, result.vitalAge.toFloat()) },
                daysUntilReady = if (latest == null) {
                    (VitalAgeSeries.MIN_WINDOW_DAYS - days.size).coerceAtLeast(1)
                } else {
                    null
                }
            )
        }
    }

    companion object {
        const val HISTORY_DAYS = 44
    }
}
