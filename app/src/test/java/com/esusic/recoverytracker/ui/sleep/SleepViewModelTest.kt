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
import com.esusic.recoverytracker.ui.date.SelectedDayStore
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
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

    private fun storeFor(date: LocalDate): SelectedDayStore = SelectedDayStore(
        Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    )

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

        val viewModel = SleepViewModel(repository, storeFor(LocalDate.of(2026, 7, 13)))
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

        val viewModel = SleepViewModel(repository, storeFor(LocalDate.of(2026, 7, 13)))
        viewModel.uiState.filter { it.sessionStart != null }.first()

        assertEquals(start, viewModel.uiState.value.sessionStart)
        assertTrue(viewModel.uiState.value.heartRateBuckets.isNotEmpty())
    }

    @Test
    fun `uiState exposes in-bed and awake minutes wake events and sleep need`() = runTest {
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test4.preferences_pb")
            }
        )
        val night1 = Instant.parse("2026-07-12T00:00:00Z")
        val night2 = Instant.parse("2026-07-13T00:00:00Z")
        // Night 2: 480 min in bed, 80 deep + 90 rem + 230 light = 400 asleep, two awake periods.
        val session2 = SleepSession(
            night2, night2.plus(480, ChronoUnit.MINUTES),
            listOf(
                SleepStagePeriod(SleepStageType.AWAKE, night2, night2.plus(10, ChronoUnit.MINUTES)),
                SleepStagePeriod(SleepStageType.DEEP, night2.plus(10, ChronoUnit.MINUTES), night2.plus(90, ChronoUnit.MINUTES)),
                SleepStagePeriod(SleepStageType.REM, night2.plus(90, ChronoUnit.MINUTES), night2.plus(180, ChronoUnit.MINUTES)),
                SleepStagePeriod(SleepStageType.AWAKE, night2.plus(180, ChronoUnit.MINUTES), night2.plus(250, ChronoUnit.MINUTES)),
                SleepStagePeriod(SleepStageType.LIGHT, night2.plus(250, ChronoUnit.MINUTES), night2.plus(480, ChronoUnit.MINUTES))
            )
        )
        val source = FakeHealthDataSource(
            sleep = listOf(
                SleepSession(night1, night1.plus(400, ChronoUnit.MINUTES), emptyList()),
                session2
            )
        )
        val repository = HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
        repository.syncDate(LocalDate.of(2026, 7, 12))
        repository.syncDate(LocalDate.of(2026, 7, 13))

        val viewModel = SleepViewModel(repository, storeFor(LocalDate.of(2026, 7, 13)))
        viewModel.uiState.filter { it.wakeEvents != null }.first()

        assertEquals(480L, viewModel.uiState.value.inBedMinutes)
        assertEquals(2, viewModel.uiState.value.wakeEvents)
        val expectedAwake = 480L - (viewModel.uiState.value.deepMinutes!! +
            viewModel.uiState.value.remMinutes!! + viewModel.uiState.value.lightMinutes!!)
        assertEquals(expectedAwake, viewModel.uiState.value.awakeMinutes)
        assertTrue(viewModel.uiState.value.needMinutes != null && viewModel.uiState.value.needMinutes!! >= 420.0)
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

        val viewModel = SleepViewModel(repository, storeFor(LocalDate.of(2026, 7, 13)))
        viewModel.uiState.filter { it.durationDelta != null }.first()

        assertEquals(60.0, viewModel.uiState.value.durationDelta!!.diff, 0.0001)
        assertEquals(DeltaKind.BETTER, viewModel.uiState.value.durationDelta!!.kind)
    }

    @Test
    fun `weekly state exposes chronological nights average and insight`() = runTest {
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries()
            .build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher())
            ) {
                tempFolder.newFile("weekly.preferences_pb")
            }
        )
        val starts = listOf(
            Instant.parse("2026-07-11T00:00:00Z"),
            Instant.parse("2026-07-12T00:00:00Z"),
            Instant.parse("2026-07-13T00:00:00Z")
        )
        val durations = listOf(420L, 450L, 480L)
        val source = FakeHealthDataSource(
            sleep = starts.zip(durations).map { (start, minutes) ->
                SleepSession(start, start.plus(minutes, ChronoUnit.MINUTES), emptyList())
            }
        )
        val repository = HealthRepository(
            AggregationService(source),
            db.dailyScoreDao(),
            db.dailyAggregateDao(),
            userPreferences
        )
        repository.syncDate(LocalDate.of(2026, 7, 11))
        repository.syncDate(LocalDate.of(2026, 7, 12))
        repository.syncDate(LocalDate.of(2026, 7, 13))

        val viewModel = SleepViewModel(repository, storeFor(LocalDate.of(2026, 7, 13)))
        viewModel.uiState.filter { it.weeklyNights.size == 3 }.first()

        assertEquals(450.0, viewModel.uiState.value.weeklyAverageMinutes!!, 0.001)
        assertEquals(3, viewModel.uiState.value.weeklyNights.size)
        assertTrue(viewModel.uiState.value.weeklyInsight!!.isNotBlank())
        assertEquals(
            LocalDate.of(2026, 7, 11),
            viewModel.uiState.value.weeklyNights.first().date
        )
    }

    @Test
    fun `duration only sleep keeps unavailable stages unknown`() = runTest {
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries()
            .build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher())
            ) {
                tempFolder.newFile("duration-only.preferences_pb")
            }
        )
        val start = Instant.parse("2026-07-13T00:00:00Z")
        val source = FakeHealthDataSource(
            sleep = listOf(
                SleepSession(start, start.plus(420, ChronoUnit.MINUTES), emptyList())
            )
        )
        val repository = HealthRepository(
            AggregationService(source),
            db.dailyScoreDao(),
            db.dailyAggregateDao(),
            userPreferences
        )
        repository.syncDate(LocalDate.of(2026, 7, 13))

        val viewModel = SleepViewModel(repository, storeFor(LocalDate.of(2026, 7, 13)))
        val state = viewModel.uiState.filter { it.sessionStart != null }.first()

        assertEquals(420L, state.totalMinutes)
        assertNull(state.deepMinutes)
        assertNull(state.remMinutes)
        assertNull(state.lightMinutes)
        assertNull(state.awakeMinutes)
        assertNull(state.wakeEvents)
    }

    @Test
    fun `selected day loads the matching older sleep session`() = runTest {
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries()
            .build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher())
            ) {
                tempFolder.newFile("selected-night.preferences_pb")
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
        val repository = HealthRepository(
            AggregationService(source),
            db.dailyScoreDao(),
            db.dailyAggregateDao(),
            userPreferences
        )
        repository.syncDate(LocalDate.of(2026, 7, 12))
        repository.syncDate(LocalDate.of(2026, 7, 13))
        val clock = Clock.fixed(
            Instant.parse("2026-07-14T12:00:00Z"),
            ZoneOffset.UTC
        )
        val dayStore = SelectedDayStore(clock)
        val viewModel = SleepViewModel(repository, dayStore)

        dayStore.select(LocalDate.of(2026, 7, 12))
        val state = viewModel.uiState.filter {
            it.selectedDate == LocalDate.of(2026, 7, 12) && it.sessionStart != null
        }.first()

        assertEquals(LocalDate.of(2026, 7, 12), state.selectedDate)
        assertEquals(night1Start, state.sessionStart)
        assertEquals(400L, state.totalMinutes)
        assertTrue(state.weeklyNights.all { it.date <= state.selectedDate })
    }

    @Test
    fun `day without sleep clears detail from the previously selected night`() = runTest {
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries()
            .build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher())
            ) {
                tempFolder.newFile("empty-selected-night.preferences_pb")
            }
        )
        val start = Instant.parse("2026-07-13T00:00:00Z")
        val source = FakeHealthDataSource(
            sleep = listOf(
                SleepSession(
                    start,
                    start.plus(420, ChronoUnit.MINUTES),
                    listOf(
                        SleepStagePeriod(
                            SleepStageType.LIGHT,
                            start,
                            start.plus(420, ChronoUnit.MINUTES)
                        )
                    )
                )
            ),
            heartRate = listOf(HeartRateSample(start.plus(10, ChronoUnit.MINUTES), 58L))
        )
        val repository = HealthRepository(
            AggregationService(source),
            db.dailyScoreDao(),
            db.dailyAggregateDao(),
            userPreferences
        )
        repository.syncDate(LocalDate.of(2026, 7, 13))
        val clock = Clock.fixed(
            Instant.parse("2026-07-14T12:00:00Z"),
            ZoneOffset.UTC
        )
        val dayStore = SelectedDayStore(clock)
        val viewModel = SleepViewModel(repository, dayStore)

        dayStore.select(LocalDate.of(2026, 7, 13))
        viewModel.uiState.filter { it.sessionStart != null }.first()

        dayStore.select(LocalDate.of(2026, 7, 14))
        val empty = viewModel.uiState.filter {
            it.selectedDate == LocalDate.of(2026, 7, 14) && it.totalMinutes == null
        }.first()

        assertNull(empty.sessionStart)
        assertTrue(empty.heartRateBuckets.isEmpty())
        assertTrue(empty.stagePeriods.isEmpty())
        assertNull(empty.sleepPerformance)
    }
}
