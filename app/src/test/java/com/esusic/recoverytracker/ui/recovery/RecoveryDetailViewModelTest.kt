package com.esusic.recoverytracker.ui.recovery

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
import java.time.LocalDate
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecoveryDetailViewModelTest {
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
    fun `selected date maps stored score and component scores`() = runTest {
        val date = LocalDate.of(2026, 7, 13)
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
                tempFolder.newFile("recovery-detail.preferences_pb")
            }
        )
        val repository = HealthRepository(
            AggregationService(FakeHealthDataSource()),
            database.dailyScoreDao(),
            database.dailyAggregateDao(),
            preferences
        )
        val saved = repository.syncDate(date)

        val viewModel = RecoveryDetailViewModel(repository, date)
        viewModel.uiState.filter { !it.isLoading }.first()

        assertEquals(saved.recoveryValue, viewModel.uiState.value.recoveryValue)
        assertEquals(saved.hrvSubScore, viewModel.uiState.value.hrvSubScore!!, 0.001)
        assertEquals(date, viewModel.uiState.value.date)
    }
}
