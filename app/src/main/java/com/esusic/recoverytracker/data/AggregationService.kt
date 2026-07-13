package com.esusic.recoverytracker.data

import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.model.DailyAggregate
import java.time.LocalDate
import java.time.ZoneId

class AggregationService(private val dataSource: HealthDataSource) {

    suspend fun aggregateForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DailyAggregate {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()

        val heartRateSamples = dataSource.heartRateSamples(start, end)
        val hrvSamples = dataSource.hrvSamples(start, end)
        val restingHrSamples = dataSource.restingHeartRate(start, end)
        val sleep = dataSource.sleepSessions(start, end).maxByOrNull { it.durationMinutes }
        val steps = dataSource.totalSteps(start, end)
        val activeCalories = dataSource.totalActiveCalories(start, end)
        val exerciseSessions = dataSource.exerciseSessions(start, end)

        val avgHrv = hrvSamples.takeIf { it.isNotEmpty() }?.map { it.rmssdMillis }?.average()
        val restingHeartRate = restingHrSamples.minByOrNull { it.bpm }?.bpm
        val isComplete = heartRateSamples.isNotEmpty() && sleep != null

        return DailyAggregate(
            date = date,
            avgHrvMillis = avgHrv,
            restingHeartRate = restingHeartRate,
            sleep = sleep,
            steps = steps,
            activeCaloriesKcal = activeCalories,
            heartRateSamples = heartRateSamples,
            exerciseSessions = exerciseSessions,
            isComplete = isComplete
        )
    }
}
