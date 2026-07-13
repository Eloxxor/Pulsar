package com.esusic.recoverytracker.data.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import com.esusic.recoverytracker.model.SleepStageType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectRecordMapperTest {
    private val base = Instant.parse("2026-07-13T00:00:00Z")

    @Test
    fun `flattens heart rate record samples`() {
        val record = HeartRateRecord(
            startTime = base, startZoneOffset = null,
            endTime = base.plus(1, ChronoUnit.HOURS), endZoneOffset = null,
            samples = listOf(
                HeartRateRecord.Sample(base, 60L),
                HeartRateRecord.Sample(base.plus(30, ChronoUnit.MINUTES), 65L)
            ),
            metadata = Metadata.manualEntry()
        )
        val result = HealthConnectRecordMapper.toHeartRateSamples(listOf(record))
        assertEquals(listOf(60L, 65L), result.map { it.bpm })
    }

    @Test
    fun `maps HRV RMSSD records`() {
        val record = HeartRateVariabilityRmssdRecord(
            time = base, zoneOffset = null, heartRateVariabilityMillis = 45.0,
            metadata = Metadata.manualEntry()
        )
        val result = HealthConnectRecordMapper.toHrvSamples(listOf(record))
        assertEquals(45.0, result.single().rmssdMillis, 0.0001)
    }

    @Test
    fun `maps resting heart rate records`() {
        val record = RestingHeartRateRecord(
            time = base, zoneOffset = null, beatsPerMinute = 52L,
            metadata = Metadata.manualEntry()
        )
        val result = HealthConnectRecordMapper.toRestingHrSamples(listOf(record))
        assertEquals(52L, result.single().bpm)
    }

    @Test
    fun `maps sleep stages to domain stage types`() {
        val deepEnd = base.plus(2, ChronoUnit.HOURS)
        val remEnd = deepEnd.plus(1, ChronoUnit.HOURS)
        val record = SleepSessionRecord(
            startTime = base, startZoneOffset = null,
            endTime = remEnd, endZoneOffset = null,
            stages = listOf(
                SleepSessionRecord.Stage(base, deepEnd, SleepSessionRecord.STAGE_TYPE_DEEP),
                SleepSessionRecord.Stage(deepEnd, remEnd, SleepSessionRecord.STAGE_TYPE_REM)
            ),
            metadata = Metadata.manualEntry()
        )
        val result = HealthConnectRecordMapper.toSleepSessions(listOf(record)).single()
        assertEquals(listOf(SleepStageType.DEEP, SleepStageType.REM), result.stages.map { it.stage })
    }

    @Test
    fun `sums steps across records`() {
        val records = listOf(
            StepsRecord(base, null, base.plus(1, ChronoUnit.HOURS), null, count = 1000L, metadata = Metadata.manualEntry()),
            StepsRecord(base.plus(1, ChronoUnit.HOURS), null, base.plus(2, ChronoUnit.HOURS), null, count = 500L, metadata = Metadata.manualEntry())
        )
        assertEquals(1500, HealthConnectRecordMapper.toTotalSteps(records))
    }

    @Test
    fun `sums active calories across records`() {
        val records = listOf(
            ActiveCaloriesBurnedRecord(base, null, base.plus(1, ChronoUnit.HOURS), null, Energy.kilocalories(120.0), metadata = Metadata.manualEntry()),
            ActiveCaloriesBurnedRecord(base.plus(1, ChronoUnit.HOURS), null, base.plus(2, ChronoUnit.HOURS), null, Energy.kilocalories(80.0), metadata = Metadata.manualEntry())
        )
        assertEquals(200.0, HealthConnectRecordMapper.toTotalActiveCalories(records), 0.0001)
    }
}
