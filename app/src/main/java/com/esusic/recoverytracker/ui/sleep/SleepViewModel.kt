package com.esusic.recoverytracker.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.model.SleepStagePeriod
import kotlinx.coroutines.flow.MutableStateFlow
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
    val heartRateBuckets: List<HeartRateBucket> = emptyList(),
    val stagePeriods: List<SleepStagePeriod> = emptyList(),
    val sessionStart: Instant? = null,
    val sessionEnd: Instant? = null
)

class SleepViewModel(private val repository: HealthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeRecentAggregates(30).collect { aggregates ->
                val latest = aggregates.firstOrNull { it.sleepDurationMinutes != null }
                val history = aggregates.mapNotNull { it.sleepDurationMinutes }
                val typical = if (history.isEmpty()) null else history.average()

                if (latest?.sleepDurationMinutes == null || latest.sleepDurationMinutes <= 0) {
                    _uiState.value = _uiState.value.copy(
                        totalMinutes = null,
                        deepMinutes = null,
                        remMinutes = null,
                        lightMinutes = null,
                        deepPercent = 0f,
                        remPercent = 0f,
                        lightPercent = 0f,
                        typicalTotalMinutes = typical
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
                        typicalTotalMinutes = typical
                    )
                }
            }
        }

        viewModelScope.launch {
            val detail = repository.sleepDetailForDate(LocalDate.now())
            if (detail != null) {
                _uiState.value = _uiState.value.copy(
                    heartRateBuckets = bucketHeartRateSamples(detail.heartRateSamples, detail.session.start),
                    stagePeriods = detail.session.stages,
                    sessionStart = detail.session.start,
                    sessionEnd = detail.session.end
                )
            }
        }
    }
}
