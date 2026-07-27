package com.esusic.recoverytracker.data.local

import androidx.annotation.StringRes
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import com.esusic.recoverytracker.R
import kotlinx.coroutines.flow.Flow

enum class JournalTagExpectation { HELPS, HURTS }

enum class JournalTag(@get:StringRes val label: Int, val expectation: JournalTagExpectation) {
    ALCOHOL(R.string.journal_tag_alcohol, JournalTagExpectation.HURTS),
    LATE_MEAL(R.string.journal_tag_late_meal, JournalTagExpectation.HURTS),
    CAFFEINE_LATE(R.string.journal_tag_late_caffeine, JournalTagExpectation.HURTS),
    SCREEN_BEFORE_BED(R.string.journal_tag_screens_before_bed, JournalTagExpectation.HURTS),
    HIGH_STRESS(R.string.journal_tag_high_stress, JournalTagExpectation.HURTS),
    TRAVEL(R.string.journal_tag_travel, JournalTagExpectation.HURTS),
    ILLNESS(R.string.journal_tag_feeling_ill, JournalTagExpectation.HURTS),
    SORE(R.string.journal_tag_sore, JournalTagExpectation.HURTS),
    MEDITATED(R.string.journal_tag_meditated, JournalTagExpectation.HELPS),
    HYDRATED(R.string.journal_tag_well_hydrated, JournalTagExpectation.HELPS),
    SICK_DAY_REST(R.string.journal_tag_full_rest_day, JournalTagExpectation.HELPS);

    companion object {
        // Rows written by a newer build can name tags this build does not know.
        fun fromIdOrNull(id: String): JournalTag? = entries.find { it.name == id }
    }
}

@Entity(tableName = "journal_entries", primaryKeys = ["date", "tag"])
data class JournalEntryEntity(
    val date: String,
    val tag: String
)

@Dao
interface JournalEntryDao {
    @Upsert
    suspend fun upsert(entity: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE date = :date AND tag = :tag")
    suspend fun delete(date: String, tag: String)

    @Query("SELECT * FROM journal_entries WHERE date = :date")
    fun observeForDate(date: String): Flow<List<JournalEntryEntity>>

    @Query(
        "SELECT * FROM journal_entries WHERE date >= :startDate AND date <= :endDate " +
            "ORDER BY date, tag"
    )
    fun observeBetween(startDate: String, endDate: String): Flow<List<JournalEntryEntity>>
}
