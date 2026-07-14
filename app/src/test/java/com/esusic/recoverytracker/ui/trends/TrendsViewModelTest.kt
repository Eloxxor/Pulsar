package com.esusic.recoverytracker.ui.trends

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TrendsViewModelTest {
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

    private fun newRepository(): HealthRepository {
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
        return HealthRepository(AggregationService(FakeHealthDataSource()), db.dailyScoreDao(), db.dailyAggregateDao(), userPreferences)
    }

    @Test
    fun `selectRange updates the range and re-derives values from persisted scores`() = runTest {
        val repository = newRepository()
        repository.syncDate(java.time.LocalDate.of(2026, 7, 13))

        val viewModel = TrendsViewModel(repository)
        viewModel.selectRange(TrendRange.WEEK)

        assertEquals(TrendRange.WEEK, viewModel.uiState.value.range)
        assertEquals(1, viewModel.uiState.value.recoveryValues.size)
    }
}
