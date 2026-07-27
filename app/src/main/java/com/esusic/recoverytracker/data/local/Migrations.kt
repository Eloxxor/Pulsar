package com.esusic.recoverytracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Additive only — stored aggregates older than the 30-day Health Connect window are
// irreplaceable, so this bump must never fall through to the destructive fallback.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_scores ADD COLUMN stressValue REAL")
        db.execSQL("ALTER TABLE daily_scores ADD COLUMN zoneMinutes INTEGER")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_entries` (" +
                "`date` TEXT NOT NULL, `tag` TEXT NOT NULL, PRIMARY KEY(`date`, `tag`))"
        )
    }
}
