package com.esusic.recoverytracker.ui.date

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate

class SelectedDayStore(
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun today(): LocalDate = LocalDate.now(clock)

    fun select(date: LocalDate) {
        _selectedDate.value = date.coerceAtMost(today())
    }

    fun previous() = select(_selectedDate.value.minusDays(1))

    fun next() = select(_selectedDate.value.plusDays(1))
}
