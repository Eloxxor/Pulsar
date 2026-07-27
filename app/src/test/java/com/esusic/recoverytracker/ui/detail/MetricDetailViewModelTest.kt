package com.esusic.recoverytracker.ui.detail

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.ui.common.TrendRange
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
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MetricDetailViewModelTest {
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
    fun `HEART_RATE metric surfaces today's resting heart rate as todayValue`() = runTest {
        val date = LocalDate.of(2026, 7, 13)
        val dayStart = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val source = FakeHealthDataSource(restingHr = listOf(RestingHrSample(dayStart.plusSeconds(60), 48L)))
        val repository = newRepository(source, "test.preferences_pb")
        repository.syncDate(date)

        val viewModel = MetricDetailViewModel(repository, MetricType.HEART_RATE)
        viewModel.uiState.filter { it.todayValue != null }.first()

        assertEquals(48.0, viewModel.uiState.value.todayValue!!, 0.0001)
    }

    @Test
    fun `selecting a range trims the history to that window`() = runTest {
        val source = FakeHealthDataSource(steps = 1234)
        val repository = newRepository(source, "metric-range.preferences_pb")
        val lastDay = LocalDate.of(2026, 7, 13)
        (0L until 9L).forEach { repository.syncDate(lastDay.minusDays(it)) }

        val viewModel = MetricDetailViewModel(repository, MetricType.ACTIVITY)
        val monthState = viewModel.uiState.filter { it.history.size == 9 }.first()
        assertEquals(TrendRange.MONTH, monthState.range)

        viewModel.selectRange(TrendRange.WEEK)
        val weekState = viewModel.uiState.filter { it.range == TrendRange.WEEK }.first()

        assertEquals(7, weekState.history.size)
        assertEquals(lastDay, weekState.history.last().date)
        assertEquals(weekState.history.sortedBy { it.date }, weekState.history)
    }

    private fun newRepository(
        source: FakeHealthDataSource,
        fileName: String
    ): HealthRepository {
        val synchronousExecutor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setQueryExecutor(synchronousExecutor)
            .setTransactionExecutor(synchronousExecutor)
            .allowMainThreadQueries().build()
        val userPreferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
                tempFolder.newFile(fileName)
            }
        )
        return HealthRepository(
            AggregationService(source),
            db.dailyScoreDao(),
            db.dailyAggregateDao(),
            userPreferences
        )
    }
}
