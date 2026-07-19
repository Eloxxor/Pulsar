package com.esusic.recoverytracker.ui.today

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
import com.esusic.recoverytracker.model.RestingHrSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import java.time.ZoneOffset
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TodayViewModelTest {
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

    private lateinit var userPreferences: UserPreferencesRepository

    private fun repositoryWith(source: FakeHealthDataSource): HealthRepository {
        // Room's suspend DAO functions dispatch onto its own query/transaction executors by
        // default, which are real background threads outside the UnconfinedTestDispatcher used
        // for viewModelScope below. Since TodayViewModel.refresh() is fire-and-forget (launch,
        // not suspend), that cross-thread hop races against the test's assertions. Forcing Room
        // to execute synchronously on the calling thread keeps everything on the test dispatcher
        // so refresh() completes before viewModel.refresh() returns.
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries().build()
        userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile("test.preferences_pb")
            }
        )
        return HealthRepository(AggregationService(source), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
    }

    @Test
    fun `refresh syncs today and populates uiState from the persisted score`() = runTest {
        val source = FakeHealthDataSource(heartRate = listOf(HeartRateSample(Instant.now(), 65L)))
        val repository = repositoryWith(source)
        val viewModel = TodayViewModel(repository, source, userPreferences)

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.recoveryValue != null)
        assertEquals(true, viewModel.uiState.value.hasAllPermissions)
        assertEquals(false, viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh computes vitals deltas against the prior-days baseline`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        val today = LocalDate.now()
        val source = FakeHealthDataSource(
            restingHr = listOf(
                RestingHrSample(yesterday.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(60), 50L),
                RestingHrSample(today.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(60), 46L)
            )
        )
        val repository = repositoryWith(source)
        repository.syncDate(yesterday)

        val viewModel = TodayViewModel(repository, source, userPreferences)
        viewModel.refresh()

        assertEquals(DeltaKind.BETTER, viewModel.uiState.value.rhrDelta!!.kind)
        assertEquals(-4.0, viewModel.uiState.value.rhrDelta!!.diff, 0.0001)
        assertNotNull(viewModel.uiState.value.hrvSubScore)
    }

    @Test
    fun `selectDate shows the persisted values of a past day without re-syncing`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        val today = LocalDate.now()
        val source = FakeHealthDataSource(
            restingHr = listOf(
                RestingHrSample(yesterday.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(60), 50L),
                RestingHrSample(today.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(60), 46L)
            )
        )
        val repository = repositoryWith(source)
        repository.syncDate(yesterday)

        val viewModel = TodayViewModel(repository, source, userPreferences)
        viewModel.refresh()
        viewModel.selectDate(yesterday)

        assertEquals(yesterday, viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.isToday)
        assertEquals(50L, viewModel.uiState.value.restingHeartRate)
        assertEquals(true, viewModel.uiState.value.canGoForward)
    }

    @Test
    fun `score histories are exposed ascending for the gauge sheets`() = runTest {
        val yesterday = LocalDate.now().minusDays(1)
        val source = FakeHealthDataSource(heartRate = listOf(HeartRateSample(Instant.now(), 65L)))
        val repository = repositoryWith(source)
        repository.syncDate(yesterday)

        val viewModel = TodayViewModel(repository, source, userPreferences)
        viewModel.refresh()

        assertEquals(2, viewModel.uiState.value.strainHistory.size)
        assertEquals(yesterday, viewModel.uiState.value.strainHistory.first().date)
        assertEquals(2, viewModel.uiState.value.recoveryHistory.size)
    }

    @Test
    fun `uiState exposes hourly steps heart-rate spark and stress for the metric grid`() = runTest {
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.now()
        val nine = today.atTime(9, 0).atZone(zone).toInstant()
        val fourteen = today.atTime(14, 0).atZone(zone).toInstant()
        val source = FakeHealthDataSource(
            heartRate = (0..5).map { HeartRateSample(nine.plusSeconds(it * 600L), 95L) },
            restingHr = listOf(RestingHrSample(nine, 60L)),
            stepsIntervalList = listOf(
                com.esusic.recoverytracker.model.StepsInterval(nine, nine.plusSeconds(1800), 500),
                com.esusic.recoverytracker.model.StepsInterval(fourteen, fourteen.plusSeconds(900), 300)
            )
        )
        val repository = repositoryWith(source)
        val viewModel = TodayViewModel(repository, source, userPreferences)

        viewModel.refresh()

        assertEquals(500, viewModel.uiState.value.hourlySteps[9])
        assertEquals(300, viewModel.uiState.value.hourlySteps[14])
        assertTrue(viewModel.uiState.value.heartRateSpark.isNotEmpty())
        assertNotNull(viewModel.uiState.value.stressValue)
        assertNotNull(viewModel.uiState.value.zoneMinutes)
    }

    @Test
    fun `uiState reflects missing permissions without showing a zero score as real`() = runTest {
        val source = FakeHealthDataSource(permissionsGranted = false)
        val viewModel = TodayViewModel(repositoryWith(source), source, userPreferences)

        viewModel.refresh()

        assertEquals(false, viewModel.uiState.value.hasAllPermissions)
    }

    @Test
    fun `home state exposes chronological age and stored sleep stages`() = runTest {
        val today = LocalDate.now()
        val start = today.minusDays(1).atTime(22, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant()
        val sleep = com.esusic.recoverytracker.model.SleepSession(
            start = start,
            end = start.plusSeconds(8 * 3600L),
            stages = listOf(
                com.esusic.recoverytracker.model.SleepStagePeriod(
                    com.esusic.recoverytracker.model.SleepStageType.DEEP,
                    start,
                    start.plusSeconds(90 * 60L)
                )
            )
        )
        val source = FakeHealthDataSource(sleep = listOf(sleep))
        val repository = repositoryWith(source)
        userPreferences.setBirthYear(today.year - 30)

        val viewModel = TodayViewModel(repository, source, userPreferences)
        viewModel.refresh()

        assertEquals(30, viewModel.uiState.value.chronologicalAge)
        assertEquals(90L, viewModel.uiState.value.deepSleepMinutes)
    }
}
