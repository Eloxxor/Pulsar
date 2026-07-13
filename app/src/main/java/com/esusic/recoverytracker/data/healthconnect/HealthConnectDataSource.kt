package com.esusic.recoverytracker.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SleepSession
import java.time.Instant

class HealthConnectDataSource(private val client: HealthConnectClient) : HealthDataSource {

    override suspend fun heartRateSamples(start: Instant, end: Instant): List<HeartRateSample> =
        HealthConnectRecordMapper.toHeartRateSamples(readRecords<HeartRateRecord>(start, end))

    override suspend fun hrvSamples(start: Instant, end: Instant): List<HrvSample> =
        HealthConnectRecordMapper.toHrvSamples(readRecords<HeartRateVariabilityRmssdRecord>(start, end))

    override suspend fun restingHeartRate(start: Instant, end: Instant): List<RestingHrSample> =
        HealthConnectRecordMapper.toRestingHrSamples(readRecords<RestingHeartRateRecord>(start, end))

    override suspend fun sleepSessions(start: Instant, end: Instant): List<SleepSession> =
        HealthConnectRecordMapper.toSleepSessions(readRecords<SleepSessionRecord>(start, end))

    override suspend fun totalSteps(start: Instant, end: Instant): Int =
        HealthConnectRecordMapper.toTotalSteps(readRecords<StepsRecord>(start, end))

    override suspend fun totalActiveCalories(start: Instant, end: Instant): Double =
        HealthConnectRecordMapper.toTotalActiveCalories(readRecords<ActiveCaloriesBurnedRecord>(start, end))

    override suspend fun exerciseSessions(start: Instant, end: Instant): List<ExerciseSession> =
        HealthConnectRecordMapper.toExerciseSessions(readRecords<ExerciseSessionRecord>(start, end))

    override suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)

    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readRecords(
        start: Instant,
        end: Instant
    ): List<T> = client.readRecords(
        ReadRecordsRequest(recordType = T::class, timeRangeFilter = TimeRangeFilter.between(start, end))
    ).records

    companion object {
        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
    }
}
