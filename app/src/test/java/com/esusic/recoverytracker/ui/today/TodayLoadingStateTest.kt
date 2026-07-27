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
class TodayLoadingStateTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var userPreferences: UserPreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repositoryWith(source: FakeHealthDataSource): HealthRepository {
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
    fun `state starts loading so the first frame can show skeletons`() {
        assertTrue(TodayUiState().isLoading)
    }

    @Test
    fun `loading clears once the first emission of data lands`() = runTest {
        val source = FakeHealthDataSource(heartRate = listOf(HeartRateSample(Instant.now(), 65L)))
        val viewModel = TodayViewModel(repositoryWith(source), source, userPreferences)

        viewModel.refresh()

        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loading clears when permissions are missing so the skeleton cannot stick`() = runTest {
        val source = FakeHealthDataSource(permissionsGranted = false)
        val viewModel = TodayViewModel(repositoryWith(source), source, userPreferences)

        viewModel.refresh()

        assertEquals(false, viewModel.uiState.value.isLoading)
    }
}
