package com.esusic.recoverytracker.ui.activity

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.RestingHrSample
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ActivityViewModelTest {
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
    fun `loads selected-day activity from persisted data`() = runTest {
        val instant = Instant.parse("2026-07-13T10:00:00Z")
        val source = FakeHealthDataSource(
            heartRate = listOf(HeartRateSample(instant, 90L)),
            restingHr = listOf(RestingHrSample(instant, 60L)),
            steps = 1234
        )
        val repository = newRepository(source, "activity-daily.preferences_pb")
        val date = LocalDate.of(2026, 7, 13)
        val saved = repository.syncDate(date)
        val fixedClock = Clock.fixed(
            Instant.parse("2026-07-13T12:00:00Z"),
            ZoneOffset.UTC
        )

        val viewModel = ActivityViewModel(repository, source, fixedClock)
        viewModel.uiState.filter { !it.isLoading }.first()

        assertEquals(saved.strainValue, viewModel.uiState.value.strainValue!!, 0.001)
        assertEquals(1234, viewModel.uiState.value.steps)
        assertEquals(saved.zoneMinutes, viewModel.uiState.value.zoneMinutes)
    }

    @Test
    fun `month range produces chronological recovery trends`() = runTest {
        val source = FakeHealthDataSource()
        val repository = newRepository(source, "activity-trends.preferences_pb")
        repository.syncDate(LocalDate.of(2026, 7, 12))
        repository.syncDate(LocalDate.of(2026, 7, 13))
        val fixedClock = Clock.fixed(
            Instant.parse("2026-07-13T12:00:00Z"),
            ZoneOffset.UTC
        )
        val viewModel = ActivityViewModel(repository, source, fixedClock)

        viewModel.selectRange(ActivityRange.MONTH)
        viewModel.uiState.filter { it.recovery.points.size == 2 }.first()

        assertEquals(ActivityRange.MONTH, viewModel.uiState.value.range)
        assertEquals(
            viewModel.uiState.value.recovery.points.sortedBy { it.date },
            viewModel.uiState.value.recovery.points
        )
    }

    private fun newRepository(
        source: FakeHealthDataSource,
        fileName: String
    ): HealthRepository {
        val synchronousExecutor = Executor { it.run() }
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries()
            .build()
        val preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher())
            ) {
                tempFolder.newFile(fileName)
            }
        )
        return HealthRepository(
            AggregationService(source),
            database.dailyScoreDao(),
            database.dailyAggregateDao(),
            preferences
        )
    }
}
