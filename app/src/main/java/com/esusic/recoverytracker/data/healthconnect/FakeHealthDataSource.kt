package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SleepSession
import java.time.Instant

class FakeHealthDataSource(
    private val heartRate: List<HeartRateSample> = emptyList(),
    private val hrv: List<HrvSample> = emptyList(),
    private val restingHr: List<RestingHrSample> = emptyList(),
    private val sleep: List<SleepSession> = emptyList(),
    private val steps: Int = 0,
    private val activeCalories: Double = 0.0,
    private val exercise: List<ExerciseSession> = emptyList(),
    private val permissionsGranted: Boolean = true
) : HealthDataSource {
    override suspend fun heartRateSamples(start: Instant, end: Instant) =
        heartRate.filter { it.time >= start && it.time < end }

    override suspend fun hrvSamples(start: Instant, end: Instant) =
        hrv.filter { it.time >= start && it.time < end }

    override suspend fun restingHeartRate(start: Instant, end: Instant) =
        restingHr.filter { it.time >= start && it.time < end }

    override suspend fun sleepSessions(start: Instant, end: Instant) =
        sleep.filter { it.start >= start && it.start < end }

    override suspend fun totalSteps(start: Instant, end: Instant) = steps

    override suspend fun totalActiveCalories(start: Instant, end: Instant) = activeCalories

    override suspend fun exerciseSessions(start: Instant, end: Instant) =
        exercise.filter { it.start >= start && it.start < end }

    override suspend fun hasAllPermissions() = permissionsGranted
}
