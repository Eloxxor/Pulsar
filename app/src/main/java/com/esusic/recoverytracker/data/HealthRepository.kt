package com.esusic.recoverytracker.data

import com.esusic.recoverytracker.data.local.DailyAggregateDao
import com.esusic.recoverytracker.data.local.DailyAggregateEntity
import com.esusic.recoverytracker.data.local.DailyScoreDao
import com.esusic.recoverytracker.data.local.DailyScoreEntity
import com.esusic.recoverytracker.data.local.toEntity
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.model.StrainScore
import com.esusic.recoverytracker.scoring.BaselineCalculator
import com.esusic.recoverytracker.scoring.RecoveryScoreCalculator
import com.esusic.recoverytracker.scoring.StrainScoreCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

class HealthRepository(
    private val aggregationService: AggregationService,
    private val scoreDao: DailyScoreDao,
    private val aggregateDao: DailyAggregateDao,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    fun observeRecentScores(days: Int): Flow<List<DailyScoreEntity>> = scoreDao.observeRecent(days)

    fun observeRecentAggregates(days: Int): Flow<List<DailyAggregateEntity>> = aggregateDao.observeRecent(days)

    suspend fun syncToday(): DailyScoreEntity = syncDate(LocalDate.now(clock))

    suspend fun syncDate(date: LocalDate): DailyScoreEntity {
        val aggregate = aggregationService.aggregateForDate(date)
        aggregateDao.upsert(aggregate.toEntity())

        val history = aggregateDao.recentBefore(date.toString(), BaselineCalculator.WINDOW_DAYS).reversed()
        val hrvBaseline = BaselineCalculator.calculate(history.mapNotNull { it.avgHrvMillis })
        val rhrBaseline = BaselineCalculator.calculate(history.mapNotNull { it.restingHeartRate?.toDouble() })
        val typicalSleepMinutes = history.mapNotNull { it.sleepDurationMinutes }
            .map { it.toDouble() }
            .let { if (it.isEmpty()) 480.0 else it.average() }
        val typicalDeepRemPercent = history.mapNotNull { entity ->
            val duration = entity.sleepDurationMinutes ?: return@mapNotNull null
            if (duration <= 0) return@mapNotNull null
            val deepRem = (entity.deepSleepMinutes ?: 0L) + (entity.remSleepMinutes ?: 0L)
            deepRem.toDouble() / duration * 100.0
        }.let { if (it.isEmpty()) 0.0 else it.average() }

        val maxHr = userPreferences.maxHeartRate.first()
        val strain = StrainScoreCalculator.calculate(date, aggregate.heartRateSamples, maxHr)

        val priorDayEntity = scoreDao.getByDate(date.minusDays(1).toString())
        val priorDayStrain = priorDayEntity?.let { StrainScore(date.minusDays(1), it.strainValue) }

        val recovery = RecoveryScoreCalculator.calculate(
            date = date,
            today = aggregate,
            hrvBaseline = hrvBaseline,
            rhrBaseline = rhrBaseline,
            typicalSleepMinutes = typicalSleepMinutes,
            typicalDeepRemPercent = typicalDeepRemPercent,
            priorDayStrain = priorDayStrain
        )

        val entity = DailyScoreEntity(
            date = date.toString(),
            recoveryValue = recovery.value,
            recoveryIsCalibrating = recovery.isCalibrating,
            hrvSubScore = recovery.hrvSubScore,
            rhrSubScore = recovery.rhrSubScore,
            sleepSubScore = recovery.sleepSubScore,
            strainValue = strain.value,
            lastSyncedAtEpochMillis = clock.millis(),
            isComplete = aggregate.isComplete
        )
        scoreDao.upsert(entity)
        return entity
    }
}
