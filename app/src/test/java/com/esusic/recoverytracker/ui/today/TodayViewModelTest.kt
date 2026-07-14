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
import com.esusic.recoverytracker.model.HeartRateSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        val userPreferences = UserPreferencesRepository(
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
        val viewModel = TodayViewModel(repository, source)

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.recoveryValue != null)
        assertEquals(true, viewModel.uiState.value.hasAllPermissions)
        assertEquals(false, viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `uiState reflects missing permissions without showing a zero score as real`() = runTest {
        val source = FakeHealthDataSource(permissionsGranted = false)
        val viewModel = TodayViewModel(repositoryWith(source), source)

        viewModel.refresh()

        assertEquals(false, viewModel.uiState.value.hasAllPermissions)
    }
}
