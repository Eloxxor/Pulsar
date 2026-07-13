package com.esusic.recoverytracker.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DailyAggregateDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DailyAggregateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dailyAggregateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(date: String, hrv: Double?) = DailyAggregateEntity(
        date = date, avgHrvMillis = hrv, restingHeartRate = 55L,
        sleepDurationMinutes = 420L, deepSleepMinutes = 80L, remSleepMinutes = 90L,
        lightSleepMinutes = 250L, steps = 8000, activeCaloriesKcal = 300.0, isComplete = true
    )

    @Test
    fun `recentBefore returns rows strictly before date, newest first, limited`() = runTest {
        dao.upsert(entity("2026-07-10", 40.0))
        dao.upsert(entity("2026-07-11", 42.0))
        dao.upsert(entity("2026-07-12", 44.0))
        dao.upsert(entity("2026-07-13", 46.0))

        val result = dao.recentBefore("2026-07-13", limit = 2)

        assertEquals(listOf("2026-07-12", "2026-07-11"), result.map { it.date })
    }
}
