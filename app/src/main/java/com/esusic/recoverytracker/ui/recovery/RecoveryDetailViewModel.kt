package com.esusic.recoverytracker.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecoveryDetailUiState(
    val isLoading: Boolean = true,
    val date: LocalDate,
    val recoveryValue: Int? = null,
    val isCalibrating: Boolean = false,
    val hrvSubScore: Double? = null,
    val rhrSubScore: Double? = null,
    val sleepSubScore: Double? = null
)

class RecoveryDetailViewModel(
    private val repository: HealthRepository,
    private val date: LocalDate
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecoveryDetailUiState(date = date))
    val uiState: StateFlow<RecoveryDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val score = repository.observeRecentScores(HISTORY_DAYS).first()
                .find { it.date == date.toString() }
            _uiState.value = RecoveryDetailUiState(
                isLoading = false,
                date = date,
                recoveryValue = score?.recoveryValue,
                isCalibrating = score?.recoveryIsCalibrating ?: false,
                hrvSubScore = score?.hrvSubScore,
                rhrSubScore = score?.rhrSubScore,
                sleepSubScore = score?.sleepSubScore
            )
        }
    }

    companion object {
        private const val HISTORY_DAYS = 60
    }
}
