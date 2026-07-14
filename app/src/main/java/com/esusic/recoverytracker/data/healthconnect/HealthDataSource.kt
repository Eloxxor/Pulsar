package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
import com.esusic.recoverytracker.model.SleepSession
import java.time.Instant

interface HealthDataSource {
    suspend fun heartRateSamples(start: Instant, end: Instant): List<HeartRateSample>
    suspend fun hrvSamples(start: Instant, end: Instant): List<HrvSample>
    suspend fun restingHeartRate(start: Instant, end: Instant): List<RestingHrSample>
    suspend fun sleepSessions(start: Instant, end: Instant): List<SleepSession>
    suspend fun totalSteps(start: Instant, end: Instant): Int
    suspend fun totalActiveCalories(start: Instant, end: Instant): Double
    suspend fun exerciseSessions(start: Instant, end: Instant): List<ExerciseSession>
    suspend fun respiratoryRateSamples(start: Instant, end: Instant): List<RespiratoryRateSample>
    suspend fun oxygenSaturationSamples(start: Instant, end: Instant): List<OxygenSaturationSample>
    suspend fun skinTemperatureDeltaSamples(start: Instant, end: Instant): List<SkinTemperatureDeltaSample>
    suspend fun hasAllPermissions(): Boolean
}
