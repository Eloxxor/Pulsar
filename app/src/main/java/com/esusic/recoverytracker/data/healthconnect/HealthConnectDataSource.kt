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
    ): List<T> {
        val allRecords = mutableListOf<T>()
        var pageToken: String? = null
        do {
            // The pageToken parameter must be omitted (not passed as "") on the first
            // request: Kotlin's generated $default constructor takes a different code
            // path than explicitly passing the same "" default value, and at least one
            // real Health Connect implementation (the Android 14+ system module) throws
            // NumberFormatException trying to parse an explicitly-passed "" as a page
            // token. Only pass pageToken once we have a real, non-empty token from a
            // prior response.
            val currentToken = pageToken
            val request = if (currentToken == null) {
                ReadRecordsRequest(recordType = T::class, timeRangeFilter = TimeRangeFilter.between(start, end))
            } else {
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = currentToken
                )
            }
            val response = client.readRecords(request)
            allRecords.addAll(response.records)
            pageToken = response.pageToken?.ifEmpty { null }
        } while (pageToken != null)
        // On some devices, the phone's own built-in step/activity sensor also writes
        // records into Health Connect (observed origin package
        // "com.android.healthconnect.phone.*") alongside Fitbit's, covering overlapping
        // time windows. Naively summing across all sources double-counts. This app is a
        // Fitbit companion, so only Fitbit's own records are relevant here.
        return allRecords.filter { it.metadata.dataOrigin.packageName == FITBIT_PACKAGE_NAME }
    }

    companion object {
        private const val FITBIT_PACKAGE_NAME = "com.fitbit.FitbitMobile"

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
