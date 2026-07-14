package com.esusic.recoverytracker.data

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HealthRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var userPreferences: UserPreferencesRepository
    private val date = LocalDate.of(2026, 7, 13)
    private val fixedClock = Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher())
        ) { tempFolder.newFile("test.preferences_pb") }
        userPreferences = UserPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun repositoryWith(source: FakeHealthDataSource) = HealthRepository(
        aggregationService = AggregationService(source),
        scoreDao = db.dailyScoreDao(),
        aggregateDao = db.dailyAggregateDao(),
        userPreferences = userPreferences,
        clock = fixedClock
    )

    @Test
    fun `syncDate persists both an aggregate row and a score row`() = runTest {
        val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val source = FakeHealthDataSource(
            heartRate = listOf(HeartRateSample(dayStart.plusSeconds(60), 70L)),
            hrv = listOf(HrvSample(dayStart.plusSeconds(60), 45.0)),
            restingHr = listOf(RestingHrSample(dayStart.plusSeconds(60), 50L)),
            sleep = listOf(
                SleepSession(
                    dayStart, dayStart.plusSeconds(6 * 3600),
                    listOf(SleepStagePeriod(SleepStageType.DEEP, dayStart, dayStart.plusSeconds(3600)))
                )
            )
        )
        val repository = repositoryWith(source)

        val result = repository.syncDate(date)

        assertEquals(date.toString(), result.date)
        assertEquals(result, db.dailyScoreDao().getByDate(date.toString()))
        assertTrue(db.dailyAggregateDao().getByDate(date.toString()) != null)
    }

    @Test
    fun `syncDate marks the result calibrating with fewer than 7 days of aggregate history`() = runTest {
        val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val source = FakeHealthDataSource(
            heartRate = listOf(HeartRateSample(dayStart.plusSeconds(60), 70L)),
            hrv = listOf(HrvSample(dayStart.plusSeconds(60), 45.0))
        )
        val result = repositoryWith(source).syncDate(date)
        assertTrue(result.recoveryIsCalibrating)
    }

    @Test
    fun `syncToday uses the injected clock to determine the date`() = runTest {
        val repository = repositoryWith(FakeHealthDataSource())
        val result = repository.syncToday()
        assertEquals(LocalDate.now(fixedClock).toString(), result.date)
    }

    @Test
    fun `syncDate computes and persists sleepPerformance for a day with a sleep session`() = runTest {
        val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val source = FakeHealthDataSource(
            heartRate = listOf(HeartRateSample(dayStart.plusSeconds(60), 70L)),
            sleep = listOf(SleepSession(dayStart, dayStart.plusSeconds(8 * 3600), emptyList()))
        )
        val result = repositoryWith(source).syncDate(date)
        assertTrue(result.sleepPerformance != null)
    }

    @Test
    fun `sleepDetailForDate returns null when there is no sleep session`() = runTest {
        val result = repositoryWith(FakeHealthDataSource()).sleepDetailForDate(date)
        assertNull(result)
    }

    @Test
    fun `sleepDetailForDate returns the session and its heart rate samples when sleep exists`() = runTest {
        val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val session = SleepSession(dayStart, dayStart.plusSeconds(6 * 3600), emptyList())
        val hrSample = HeartRateSample(dayStart.plusSeconds(60), 55L)
        val source = FakeHealthDataSource(sleep = listOf(session), heartRate = listOf(hrSample))

        val result = repositoryWith(source).sleepDetailForDate(date)

        assertEquals(session, result?.session)
        assertEquals(listOf(hrSample), result?.heartRateSamples)
    }
}
