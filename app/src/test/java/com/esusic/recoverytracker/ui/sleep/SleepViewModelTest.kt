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
import com.esusic.recoverytracker.model.DeltaKind
import com.esusic.recoverytracker.model.HeartRateSample
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
import org.junit.Assert.assertTrue
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

    @Test
    fun `heart rate chart data loads for the latest night with sleep even when today has none`() = runTest {
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test3.preferences_pb")
            }
        )
        // Sleep session on a past date — LocalDate.now() has no sleep, like right after midnight.
        val start = Instant.parse("2026-07-13T00:00:00Z")
        val session = SleepSession(start, start.plus(400, ChronoUnit.MINUTES), emptyList())
        val source = FakeHealthDataSource(
            sleep = listOf(session),
            heartRate = listOf(
                HeartRateSample(start.plus(10, ChronoUnit.MINUTES), 58L),
                HeartRateSample(start.plus(20, ChronoUnit.MINUTES), 56L)
            )
        )
        val repository = HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
        repository.syncDate(LocalDate.of(2026, 7, 13))

        val viewModel = SleepViewModel(repository)
        viewModel.uiState.filter { it.sessionStart != null }.first()

        assertEquals(start, viewModel.uiState.value.sessionStart)
        assertTrue(viewModel.uiState.value.heartRateBuckets.isNotEmpty())
    }

    @Test
    fun `durationDelta compares the latest night against prior nights only`() = runTest {
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test2.preferences_pb")
            }
        )
        val night1Start = Instant.parse("2026-07-12T00:00:00Z")
        val night2Start = Instant.parse("2026-07-13T00:00:00Z")
        val source = FakeHealthDataSource(
            sleep = listOf(
                SleepSession(night1Start, night1Start.plus(400, ChronoUnit.MINUTES), emptyList()),
                SleepSession(night2Start, night2Start.plus(460, ChronoUnit.MINUTES), emptyList())
            )
        )
        val repository = HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
        repository.syncDate(LocalDate.of(2026, 7, 12))
        repository.syncDate(LocalDate.of(2026, 7, 13))

        val viewModel = SleepViewModel(repository)
        viewModel.uiState.filter { it.durationDelta != null }.first()

        assertEquals(60.0, viewModel.uiState.value.durationDelta!!.diff, 0.0001)
        assertEquals(DeltaKind.BETTER, viewModel.uiState.value.durationDelta!!.kind)
    }
}
