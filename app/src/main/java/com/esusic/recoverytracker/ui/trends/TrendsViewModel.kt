package com.esusic.recoverytracker.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

enum class TrendRange(val days: Int) { WEEK(7), MONTH(30), QUARTER(90) }

data class TrendsUiState(
    val range: TrendRange = TrendRange.WEEK,
    val recoveryValues: List<Float> = emptyList(),
    val strainValues: List<Float> = emptyList(),
    val hrvValues: List<Float> = emptyList(),
    val rhrValues: List<Float> = emptyList()
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
                    TrendsUiState(
                        range = range,
                        recoveryValues = scores.reversed().map { it.recoveryValue.toFloat() },
                        strainValues = scores.reversed().map { it.strainValue.toFloat() },
                        hrvValues = aggregates.reversed().mapNotNull { it.avgHrvMillis?.toFloat() },
                        rhrValues = aggregates.reversed().mapNotNull { it.restingHeartRate?.toFloat() }
                    )
                }
            }.collect { _uiState.value = it }
        }
    }

    fun selectRange(range: TrendRange) {
        selectedRange.value = range
    }
}
