package com.esusic.recoverytracker.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class JournalEntryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: JournalEntryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.journalEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `observeForDate returns only that day's tags`() = runTest {
        dao.upsert(JournalEntryEntity("2026-07-13", JournalTag.ALCOHOL.name))
        dao.upsert(JournalEntryEntity("2026-07-13", JournalTag.LATE_MEAL.name))
        dao.upsert(JournalEntryEntity("2026-07-14", JournalTag.MEDITATED.name))

        assertEquals(
            setOf(JournalTag.ALCOHOL.name, JournalTag.LATE_MEAL.name),
            dao.observeForDate("2026-07-13").first().map { it.tag }.toSet()
        )
    }

    @Test
    fun `upserting the same date and tag twice keeps one row`() = runTest {
        dao.upsert(JournalEntryEntity("2026-07-13", JournalTag.ALCOHOL.name))
        dao.upsert(JournalEntryEntity("2026-07-13", JournalTag.ALCOHOL.name))

        assertEquals(1, dao.observeForDate("2026-07-13").first().size)
    }

    @Test
    fun `delete removes a single tag and leaves the rest`() = runTest {
        dao.upsert(JournalEntryEntity("2026-07-13", JournalTag.ALCOHOL.name))
        dao.upsert(JournalEntryEntity("2026-07-13", JournalTag.LATE_MEAL.name))

        dao.delete("2026-07-13", JournalTag.ALCOHOL.name)

        assertEquals(
            listOf(JournalTag.LATE_MEAL.name),
            dao.observeForDate("2026-07-13").first().map { it.tag }
        )
    }

    @Test
    fun `observeBetween is inclusive of both bounds and excludes the rest`() = runTest {
        listOf("2026-07-10", "2026-07-11", "2026-07-12", "2026-07-13").forEach {
            dao.upsert(JournalEntryEntity(it, JournalTag.ALCOHOL.name))
        }

        assertEquals(
            listOf("2026-07-11", "2026-07-12"),
            dao.observeBetween("2026-07-11", "2026-07-12").first().map { it.date }
        )
    }

    @Test
    fun `observeForDate emits an empty list for an unjournalled day`() = runTest {
        assertTrue(dao.observeForDate("2099-01-01").first().isEmpty())
    }
}
