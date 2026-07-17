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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

data class VitalAgeUiState(
    val isLoading: Boolean = true,
    val chronologicalAge: Int? = null,
    val result: VitalAgeResult? = null,
    val weeklyDelta: MetricDelta? = null,
    val history: List<ChartPoint> = emptyList(),
    val daysUntilReady: Int? = null,
    val asOfDate: LocalDate? = null
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

            val series = VitalAgeSeries.build(days, birthYear, maxHrBpm = userPreferences.maxHeartRate.first())
            val anchor = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            // The shown number only changes on Mondays; before the first qualified
            // Monday fall back to the newest point so calibration still shows something.
            val display = series.lastOrNull { it.first <= anchor } ?: series.lastOrNull()
            val weekBefore = display?.let { (date, _) -> series.lastOrNull { it.first <= date.minusDays(7) } }
            val weeklyDelta = if (display != null && weekBefore != null) {
                val diff = display.second.vitalAge - weekBefore.second.vitalAge
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
                result = display?.second,
                weeklyDelta = weeklyDelta,
                history = series.filter { it.first.dayOfWeek == DayOfWeek.MONDAY }
                    .map { (date, result) -> ChartPoint(date, result.vitalAge.toFloat()) },
                daysUntilReady = if (display == null) {
                    (VitalAgeSeries.MIN_WINDOW_DAYS - days.size).coerceAtLeast(1)
                } else {
                    null
                },
                asOfDate = display?.first
            )
        }
    }

    companion object {
        const val HISTORY_DAYS = 44
    }
}
