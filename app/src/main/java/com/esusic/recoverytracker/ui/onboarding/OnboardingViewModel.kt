package com.esusic.recoverytracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val hasAllPermissions: Boolean = false,
    val birthYear: Int? = null
)

class OnboardingViewModel(
    private val userPreferences: UserPreferencesRepository,
    private val healthDataSource: HealthDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(birthYear = userPreferences.birthYear.first())
        }
    }

    fun onBirthYearEntered(year: Int) {
        viewModelScope.launch {
            userPreferences.setBirthYear(year)
            _uiState.value = _uiState.value.copy(birthYear = year)
        }
    }

    suspend fun refreshPermissionStatus() {
        _uiState.value = _uiState.value.copy(hasAllPermissions = healthDataSource.hasAllPermissions())
    }
}
