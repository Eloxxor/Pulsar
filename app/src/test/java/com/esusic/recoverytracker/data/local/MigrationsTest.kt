package com.esusic.recoverytracker.data.local

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationsTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `journal migration recreates exactly the table Room generates`() {
        val support = db.openHelper.writableDatabase
        val expected = createSqlOf(support, "journal_entries")

        support.execSQL("DROP TABLE journal_entries")
        MIGRATION_4_5.migrate(support)

        assertEquals(expected, createSqlOf(support, "journal_entries"))
    }

    @Test
    fun `journal migration bridges version four to five`() {
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
    }

    private fun createSqlOf(support: SupportSQLiteDatabase, table: String): String =
        support.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '$table'")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
}
