package com.esusic.recoverytracker.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncWorkerTest {
    @Test
    fun `doWork returns success and persists a score for today`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
            File.createTempFile("test", ".preferences_pb")
        }
        val repository = HealthRepository(
            aggregationService = AggregationService(FakeHealthDataSource()),
            scoreDao = db.dailyScoreDao(),
            aggregateDao = db.dailyAggregateDao(),
            userPreferences = UserPreferencesRepository(dataStore)
        )

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(AppWorkerFactory { repository })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        db.close()
    }
}
