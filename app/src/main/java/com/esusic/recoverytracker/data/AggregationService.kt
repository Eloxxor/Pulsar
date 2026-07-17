package com.esusic.recoverytracker.data

import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.model.DailyAggregate
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.SleepSession
import java.time.LocalDate
import java.time.ZoneId

class AggregationService(private val dataSource: HealthDataSource) {

    suspend fun aggregateForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DailyAggregate {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()

        // Sleep sessions typically start the evening before the day they belong to (the day the
        // person wakes up). Query a wider window covering the prior evening through the end of
        // the target day, then keep only sessions that woke the person up on this calendar day.
        val sleepWindowStart = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()

        // Nocturnal metrics (skin temp, SpO2) are stamped during sleep — Fitbit stamps them at
        // bedtime, usually before midnight on the previous calendar day. Use an evening-to-evening
        // window so last night's values land on the wake-up day; the 18:00 cutoff separates
        // consecutive nights.
        val nocturnalEnd = date.atTime(18, 0).atZone(zone).toInstant()

        val heartRateSamples = dataSource.heartRateSamples(start, end)
        val hrvSamples = dataSource.hrvSamples(start, end)
        val restingHrSamples = dataSource.restingHeartRate(start, end)
        val sleep = dataSource.sleepSessions(sleepWindowStart, end)
            .filter { it.end >= start && it.end < end }
            .maxByOrNull { it.durationMinutes }
        val steps = dataSource.totalSteps(start, end)
        val activeCalories = dataSource.totalActiveCalories(start, end)
        val exerciseSessions = dataSource.exerciseSessions(start, end)
        val respiratoryRateSamples = dataSource.respiratoryRateSamples(start, end)
        val oxygenSaturationSamples = dataSource.oxygenSaturationSamples(sleepWindowStart, nocturnalEnd)
        val skinTemperatureDeltaSamples = dataSource.skinTemperatureDeltaSamples(sleepWindowStart, nocturnalEnd)

        val avgHrv = hrvSamples.takeIf { it.isNotEmpty() }?.map { it.rmssdMillis }?.average()
        val restingHeartRate = restingHrSamples.minByOrNull { it.bpm }?.bpm
        val isComplete = heartRateSamples.isNotEmpty() && sleep != null
        val avgRespiratoryRate = respiratoryRateSamples.takeIf { it.isNotEmpty() }
            ?.map { it.breathsPerMinute }?.average()
        val avgOxygenSaturationPercent = oxygenSaturationSamples.takeIf { it.isNotEmpty() }
            ?.map { it.percentage }?.average()
        val avgSkinTemperatureDeltaCelsius = skinTemperatureDeltaSamples.takeIf { it.isNotEmpty() }
            ?.map { it.deltaCelsius }?.average()
        val latestHeartRateSample = heartRateSamples.maxByOrNull { it.time }

        return DailyAggregate(
            date = date,
            avgHrvMillis = avgHrv,
            restingHeartRate = restingHeartRate,
            sleep = sleep,
            steps = steps,
            activeCaloriesKcal = activeCalories,
            heartRateSamples = heartRateSamples,
            exerciseSessions = exerciseSessions,
            isComplete = isComplete,
            avgRespiratoryRate = avgRespiratoryRate,
            avgOxygenSaturationPercent = avgOxygenSaturationPercent,
            avgSkinTemperatureDeltaCelsius = avgSkinTemperatureDeltaCelsius,
            latestHeartRateSample = latestHeartRateSample
        )
    }

    suspend fun heartRateSamplesForSession(session: SleepSession): List<HeartRateSample> =
        dataSource.heartRateSamples(session.start, session.end)
}
