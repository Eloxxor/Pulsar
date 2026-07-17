package com.esusic.recoverytracker.ui.vitalage

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.local.DailyAggregateEntity
import com.esusic.recoverytracker.data.local.DailyScoreEntity
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.DeltaKind
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class VitalAgeViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val today = LocalDate.of(2026, 7, 17)
    private val fixedClock = Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneOffset.UTC)

    private lateinit var db: AppDatabase
    private lateinit var repository: HealthRepository
    private lateinit var userPreferences: UserPreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Synchronous Room executors so DAO flows emit on the same UnconfinedTestDispatcher
        // used by viewModelScope (same pattern as TodayViewModelTest / TrendsViewModelTest).
        val synchronousExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries().build()
        userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test.preferences_pb")
            }
        )
        repository = HealthRepository(
            AggregationService(FakeHealthDataSource()), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun insertDay(date: LocalDate, hrv: Double, rhr: Long, sleepMin: Long, steps: Int, strain: Double) {
        db.dailyAggregateDao().upsert(
            DailyAggregateEntity(
                date = date.toString(), avgHrvMillis = hrv, restingHeartRate = rhr,
                sleepDurationMinutes = sleepMin, deepSleepMinutes = 60, remSleepMinutes = 90,
                lightSleepMinutes = sleepMin - 150, steps = steps, activeCaloriesKcal = 300.0, isComplete = true
            )
        )
        db.dailyScoreDao().upsert(
            DailyScoreEntity(
                date = date.toString(), recoveryValue = 60, recoveryIsCalibrating = false,
                hrvSubScore = 60.0, rhrSubScore = 60.0, sleepSubScore = 60.0,
                strainValue = strain, lastSyncedAtEpochMillis = 0L, isComplete = true, sleepPerformance = 80
            )
        )
    }

    @Test
    fun `no birth year yields empty non-loading state`() = runTest {
        val viewModel = VitalAgeViewModel(repository, userPreferences, fixedClock)
        val state = viewModel.uiState.filter { !it.isLoading }.first()
        assertNull(state.result)
        assertNull(state.chronologicalAge)
    }

    @Test
    fun `display value anchors to the previous Monday and history holds Mondays only`() = runTest {
        userPreferences.setBirthYear(1996)
        // 20 consecutive days ending Friday 2026-07-17; HRV improves daily
        (19L downTo 0L).forEach { back ->
            insertDay(today.minusDays(back), hrv = 40.0 + (19 - back), rhr = 60, sleepMin = 450, steps = 9000, strain = 9.0)
        }
        val viewModel = VitalAgeViewModel(repository, userPreferences, fixedClock)
        val state = viewModel.uiState.filter { !it.isLoading }.first()

        assertEquals(30, state.chronologicalAge)
        val monday = LocalDate.of(2026, 7, 13)
        assertEquals(monday, state.asOfDate)
        // History: only Mondays inside the qualified series
        assertEquals(listOf(LocalDate.of(2026, 7, 6), monday), state.history.map { it.date })
        assertNotNull(state.weeklyDelta)
        // HRV rising means vital age falls week-over-week
        assertEquals(DeltaKind.BETTER, state.weeklyDelta!!.kind)
        // Displayed number equals the Monday value, not today's
        assertEquals(state.history.last().value, state.result!!.vitalAge.toFloat(), 1e-4f)
        assertNull(state.daysUntilReady)
    }

    @Test
    fun `before the first qualified Monday the latest point is shown as fallback`() = runTest {
        userPreferences.setBirthYear(1996)
        // Only Wed-Fri this week: no series point at or before Monday 2026-07-13
        (2L downTo 0L).forEach { back ->
            insertDay(today.minusDays(back), hrv = 45.0, rhr = 62, sleepMin = 430, steps = 7000, strain = 5.0)
        }
        val viewModel = VitalAgeViewModel(repository, userPreferences, fixedClock)
        val state = viewModel.uiState.filter { !it.isLoading }.first()
        assertNotNull(state.result)
        assertEquals(today, state.asOfDate)
        assertNull(state.weeklyDelta)
    }

    @Test
    fun `too little data reports calibrating with daysUntilReady`() = runTest {
        userPreferences.setBirthYear(1996)
        insertDay(today, hrv = 45.0, rhr = 62, sleepMin = 430, steps = 7000, strain = 5.0)
        val viewModel = VitalAgeViewModel(repository, userPreferences, fixedClock)
        val state = viewModel.uiState.filter { !it.isLoading }.first()
        assertNull(state.result)
        assertEquals(2, state.daysUntilReady)
    }
}
