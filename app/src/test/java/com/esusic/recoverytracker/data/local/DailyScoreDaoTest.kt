package com.esusic.recoverytracker.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DailyScoreDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DailyScoreDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dailyScoreDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then getByDate returns the row`() = runTest {
        val entity = DailyScoreEntity(
            date = "2026-07-13", recoveryValue = 72, recoveryIsCalibrating = false,
            hrvSubScore = 60.0, rhrSubScore = 70.0, sleepSubScore = 80.0,
            strainValue = 12.5, lastSyncedAtEpochMillis = 1000L, isComplete = true
        )
        dao.upsert(entity)
        assertEquals(entity, dao.getByDate("2026-07-13"))
    }

    @Test
    fun `getByDate returns null when missing`() = runTest {
        assertNull(dao.getByDate("2099-01-01"))
    }

    @Test
    fun `upsert overwrites existing row for same date`() = runTest {
        val original = DailyScoreEntity("2026-07-13", 50, true, 0.0, 0.0, 0.0, 0.0, 1L, false)
        val updated = original.copy(recoveryValue = 90, recoveryIsCalibrating = false)
        dao.upsert(original)
        dao.upsert(updated)
        assertEquals(90, dao.getByDate("2026-07-13")?.recoveryValue)
    }
}
