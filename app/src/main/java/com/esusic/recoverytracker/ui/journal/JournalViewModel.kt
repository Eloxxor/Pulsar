package com.esusic.recoverytracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esusic.recoverytracker.data.local.DailyScoreDao
import com.esusic.recoverytracker.data.local.JournalEntryDao
import com.esusic.recoverytracker.data.local.JournalEntryEntity
import com.esusic.recoverytracker.data.local.JournalTag
import com.esusic.recoverytracker.scoring.JournalCorrelationCalculator
import com.esusic.recoverytracker.scoring.JournalTagImpact
import com.esusic.recoverytracker.ui.date.SelectedDayStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class JournalUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val canGoForward: Boolean = false,
    val selectedTags: Set<JournalTag> = emptySet(),
    val impacts: List<JournalTagImpact> = emptyList(),
    val journaledDays: Int = 0
)

class JournalViewModel(
    private val journalDao: JournalEntryDao,
    private val scoreDao: DailyScoreDao,
    private val selectedDayStore: SelectedDayStore = SelectedDayStore()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        JournalUiState(selectedDate = selectedDayStore.selectedDate.value)
    )
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectedDayStore.selectedDate
                .flatMapLatest { date ->
                    journalDao.observeForDate(date.toString()).map { date to it }
                }
                .collectLatest { (date, entries) -> publishDay(date, entries) }
        }
        viewModelScope.launch {
            val today = selectedDayStore.today()
            combine(
                journalDao.observeBetween(
                    today.minusDays(WINDOW_DAYS - 1L).toString(),
                    today.toString()
                ),
                scoreDao.observeRecent(WINDOW_DAYS)
            ) { entries, scores ->
                val tagsByDate = entries
                    .groupBy { LocalDate.parse(it.date) }
                    .mapValues { (_, rows) -> rows.mapNotNull { JournalTag.fromIdOrNull(it.tag) }.toSet() }
                    .filterValues { it.isNotEmpty() }
                val recoveryByDate = scores
                    .filterNot { it.recoveryIsCalibrating }
                    .associate { LocalDate.parse(it.date) to it.recoveryValue }
                JournalCorrelationCalculator.calculate(tagsByDate, recoveryByDate) to tagsByDate.size
            }.collectLatest { (impacts, journaledDays) ->
                _uiState.update { it.copy(impacts = impacts, journaledDays = journaledDays) }
            }
        }
    }

    fun goToPreviousDay() = selectedDayStore.previous()

    fun goToNextDay() = selectedDayStore.next()

    fun toggleTag(tag: JournalTag) {
        val date = _uiState.value.selectedDate.toString()
        val wasSelected = _uiState.value.selectedTags.contains(tag)
        viewModelScope.launch {
            if (wasSelected) {
                journalDao.delete(date, tag.name)
            } else {
                journalDao.upsert(JournalEntryEntity(date, tag.name))
            }
        }
    }

    private fun publishDay(date: LocalDate, entries: List<JournalEntryEntity>) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                canGoForward = date < selectedDayStore.today(),
                selectedTags = entries.mapNotNull { entry -> JournalTag.fromIdOrNull(entry.tag) }.toSet()
            )
        }
    }

    private companion object {
        const val WINDOW_DAYS = 90
    }
}
