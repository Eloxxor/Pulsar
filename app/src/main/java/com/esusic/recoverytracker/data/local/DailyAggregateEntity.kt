package com.esusic.recoverytracker.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_aggregates")
data class DailyAggregateEntity(
    @PrimaryKey val date: String,
    val avgHrvMillis: Double?,
    val restingHeartRate: Long?,
    val sleepDurationMinutes: Long?,
    val deepSleepMinutes: Long?,
    val remSleepMinutes: Long?,
    val lightSleepMinutes: Long?,
    val steps: Int,
    val activeCaloriesKcal: Double,
    val isComplete: Boolean
)

@Dao
interface DailyAggregateDao {
    @Upsert
    suspend fun upsert(entity: DailyAggregateEntity)

    @Query("SELECT * FROM daily_aggregates WHERE date = :date")
    suspend fun getByDate(date: String): DailyAggregateEntity?

    @Query("SELECT * FROM daily_aggregates ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailyAggregateEntity>>

    @Query("SELECT * FROM daily_aggregates WHERE date < :date ORDER BY date DESC LIMIT :limit")
    suspend fun recentBefore(date: String, limit: Int): List<DailyAggregateEntity>
}
