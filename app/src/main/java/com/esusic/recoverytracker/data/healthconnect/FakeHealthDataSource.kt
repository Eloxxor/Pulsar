package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.StepsInterval
import java.time.Instant

class FakeHealthDataSource(
    private val heartRate: List<HeartRateSample> = emptyList(),
    private val hrv: List<HrvSample> = emptyList(),
    private val restingHr: List<RestingHrSample> = emptyList(),
    private val sleep: List<SleepSession> = emptyList(),
    private val steps: Int = 0,
    private val stepsIntervalList: List<StepsInterval> = emptyList(),
    private val activeCalories: Double = 0.0,
    private val exercise: List<ExerciseSession> = emptyList(),
    private val respiratoryRate: List<RespiratoryRateSample> = emptyList(),
    private val oxygenSaturation: List<OxygenSaturationSample> = emptyList(),
    private val skinTemperatureDeltas: List<SkinTemperatureDeltaSample> = emptyList(),
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

    override suspend fun stepsIntervals(start: Instant, end: Instant) =
        stepsIntervalList.filter { it.start >= start && it.start < end }

    override suspend fun totalActiveCalories(start: Instant, end: Instant) = activeCalories

    override suspend fun exerciseSessions(start: Instant, end: Instant) =
        exercise.filter { it.start >= start && it.start < end }

    override suspend fun respiratoryRateSamples(start: Instant, end: Instant) =
        respiratoryRate.filter { it.time >= start && it.time < end }

    override suspend fun oxygenSaturationSamples(start: Instant, end: Instant) =
        oxygenSaturation.filter { it.time >= start && it.time < end }

    override suspend fun skinTemperatureDeltaSamples(start: Instant, end: Instant) =
        skinTemperatureDeltas.filter { it.time >= start && it.time < end }

    override suspend fun hasAllPermissions() = permissionsGranted
}
