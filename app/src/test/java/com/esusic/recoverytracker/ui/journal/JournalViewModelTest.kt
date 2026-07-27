package com.esusic.recoverytracker.ui.journal

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.local.DailyScoreEntity
import com.esusic.recoverytracker.data.local.JournalEntryEntity
import com.esusic.recoverytracker.data.local.JournalTag
import com.esusic.recoverytracker.ui.date.SelectedDayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class JournalViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 7, 19)

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val synchronousExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun newViewModel(store: SelectedDayStore = SelectedDayStore(clock)) =
        JournalViewModel(db.journalEntryDao(), db.dailyScoreDao(), store)

    private suspend fun saveScore(date: LocalDate, recovery: Int, calibrating: Boolean = false) {
        db.dailyScoreDao().upsert(
            DailyScoreEntity(
                date = date.toString(),
                recoveryValue = recovery,
                recoveryIsCalibrating = calibrating,
                hrvSubScore = 0.0,
                rhrSubScore = 0.0,
                sleepSubScore = 0.0,
                strainValue = 0.0,
                lastSyncedAtEpochMillis = 0L,
                isComplete = true
            )
        )
    }

    private suspend fun saveTag(date: LocalDate, tag: JournalTag) {
        db.journalEntryDao().upsert(JournalEntryEntity(date.toString(), tag.name))
    }

    @Test
    fun `toggling a tag persists it for the selected day`() = runTest {
        val viewModel = newViewModel()

        viewModel.toggleTag(JournalTag.ALCOHOL)
        viewModel.uiState.filter { JournalTag.ALCOHOL in it.selectedTags }.first()

        assertEquals(
            listOf(JournalTag.ALCOHOL.name),
            db.journalEntryDao().observeForDate(today.toString()).first().map { it.tag }
        )
    }

    @Test
    fun `toggling a selected tag clears it`() = runTest {
        saveTag(today, JournalTag.ALCOHOL)
        val viewModel = newViewModel()
        viewModel.uiState.filter { JournalTag.ALCOHOL in it.selectedTags }.first()

        viewModel.toggleTag(JournalTag.ALCOHOL)
        viewModel.uiState.filter { it.selectedTags.isEmpty() }.first()

        assertTrue(db.journalEntryDao().observeForDate(today.toString()).first().isEmpty())
    }

    @Test
    fun `tags follow the shared selected day`() = runTest {
        val yesterday = today.minusDays(1)
        saveTag(today, JournalTag.ALCOHOL)
        saveTag(yesterday, JournalTag.MEDITATED)
        val store = SelectedDayStore(clock)
        val viewModel = newViewModel(store)

        store.select(yesterday)
        val state = viewModel.uiState.filter { it.selectedDate == yesterday }.first()

        assertEquals(setOf(JournalTag.MEDITATED), state.selectedTags)
        assertTrue(state.canGoForward)
    }

    @Test
    fun `correlations surface once both samples are large enough`() = runTest {
        repeat(5) { offset ->
            val date = today.minusDays(offset.toLong())
            saveScore(date, 50)
            saveTag(date, JournalTag.ALCOHOL)
        }
        repeat(5) { offset ->
            val date = today.minusDays(offset + 5L)
            saveScore(date, 70)
            saveTag(date, JournalTag.MEDITATED)
        }
        val viewModel = newViewModel()

        val state = viewModel.uiState.filter { it.impacts.any { impact -> impact.hasEnoughData } }
            .first()

        val alcohol = state.impacts.single { it.tag == JournalTag.ALCOHOL }
        assertEquals(-20.0, alcohol.impactPoints!!, 0.001)
        assertEquals(10, state.journaledDays)
    }

    @Test
    fun `calibrating recovery days are left out of the comparison`() = runTest {
        repeat(5) { offset ->
            val date = today.minusDays(offset.toLong())
            saveScore(date, 50, calibrating = true)
            saveTag(date, JournalTag.ALCOHOL)
        }
        repeat(5) { offset ->
            val date = today.minusDays(offset + 5L)
            saveScore(date, 70)
            saveTag(date, JournalTag.MEDITATED)
        }
        val viewModel = newViewModel()

        val state = viewModel.uiState.filter { it.impacts.isNotEmpty() }.first()

        assertTrue(state.impacts.none { it.hasEnoughData })
    }
}
