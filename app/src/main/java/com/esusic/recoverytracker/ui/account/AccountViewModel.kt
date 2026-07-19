package com.esusic.recoverytracker.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class AccountUiState(
    val isLoading: Boolean = true,
    val birthYear: Int? = null,
    val age: Int? = null,
    val healthConnectConnected: Boolean = false
)

class AccountViewModel(
    private val userPreferences: UserPreferencesRepository,
    private val healthDataSource: HealthDataSource,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.birthYear.collect { birthYear ->
                _uiState.value = AccountUiState(
                    isLoading = false,
                    birthYear = birthYear,
                    age = birthYear?.let(::ageForBirthYear),
                    healthConnectConnected = permissionStatus()
                )
            }
        }
    }

    fun saveBirthYear(year: Int) {
        if (year !in MIN_BIRTH_YEAR..LocalDate.now(clock).year) return
        viewModelScope.launch { userPreferences.setBirthYear(year) }
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            _uiState.update { it.copy(healthConnectConnected = permissionStatus()) }
        }
    }

    private fun ageForBirthYear(birthYear: Int): Int =
        (LocalDate.now(clock).year - birthYear).coerceAtLeast(0)

    private suspend fun permissionStatus(): Boolean =
        runCatching { healthDataSource.hasAllPermissions() }.getOrDefault(false)

    private companion object {
        const val MIN_BIRTH_YEAR = 1900
    }
}
