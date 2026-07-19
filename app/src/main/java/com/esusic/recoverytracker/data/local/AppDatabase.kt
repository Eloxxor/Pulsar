package com.esusic.recoverytracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyScoreEntity::class, DailyAggregateEntity::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyScoreDao(): DailyScoreDao
    abstract fun dailyAggregateDao(): DailyAggregateDao
}
