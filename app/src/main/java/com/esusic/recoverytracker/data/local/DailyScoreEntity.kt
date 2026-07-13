package com.esusic.recoverytracker.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_scores")
data class DailyScoreEntity(
    @PrimaryKey val date: String,
    val recoveryValue: Int,
    val recoveryIsCalibrating: Boolean,
    val hrvSubScore: Double,
    val rhrSubScore: Double,
    val sleepSubScore: Double,
    val strainValue: Double,
    val lastSyncedAtEpochMillis: Long,
    val isComplete: Boolean
)

@Dao
interface DailyScoreDao {
    @Upsert
    suspend fun upsert(entity: DailyScoreEntity)

    @Query("SELECT * FROM daily_scores WHERE date = :date")
    suspend fun getByDate(date: String): DailyScoreEntity?

    @Query("SELECT * FROM daily_scores ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DailyScoreEntity>>
}
