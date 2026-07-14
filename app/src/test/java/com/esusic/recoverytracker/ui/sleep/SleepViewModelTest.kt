package com.esusic.recoverytracker.ui.sleep

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SleepViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState derives stage percentages from the most recent aggregate`() = runTest {
        // Force Room's query/transaction executors synchronous so DAO flows emit on the same
        // UnconfinedTestDispatcher used by viewModelScope, avoiding a cross-thread race with the
        // ViewModel's init{} collector (see TodayViewModelTest for the same pattern).
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test.preferences_pb")
            }
        )
        val start = Instant.parse("2026-07-13T00:00:00Z")
        val sleep = SleepSession(
            start, start.plus(400, ChronoUnit.MINUTES),
            listOf(
                SleepStagePeriod(SleepStageType.DEEP, start, start.plus(80, ChronoUnit.MINUTES)),
                SleepStagePeriod(SleepStageType.REM, start.plus(80, ChronoUnit.MINUTES), start.plus(170, ChronoUnit.MINUTES)),
                SleepStagePeriod(SleepStageType.LIGHT, start.plus(170, ChronoUnit.MINUTES), start.plus(400, ChronoUnit.MINUTES))
            )
        )
        val source = FakeHealthDataSource(sleep = listOf(sleep))
        val repository = HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
        repository.syncDate(LocalDate.of(2026, 7, 13))

        val viewModel = SleepViewModel(repository)
        viewModel.uiState.filter { it.totalMinutes != null }.first()

        assertEquals(400L, viewModel.uiState.value.totalMinutes)
        assertEquals(80L, viewModel.uiState.value.deepMinutes)
    }
}
