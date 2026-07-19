package com.esusic.recoverytracker.data.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.esusic.recoverytracker.model.ExerciseSession
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.StepsInterval
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType

object HealthConnectRecordMapper {

    fun toHeartRateSamples(records: List<HeartRateRecord>): List<HeartRateSample> =
        records.flatMap { record -> record.samples.map { HeartRateSample(it.time, it.beatsPerMinute) } }

    fun toHrvSamples(records: List<HeartRateVariabilityRmssdRecord>): List<HrvSample> =
        records.map { HrvSample(it.time, it.heartRateVariabilityMillis) }

    fun toRestingHrSamples(records: List<RestingHeartRateRecord>): List<RestingHrSample> =
        records.map { RestingHrSample(it.time, it.beatsPerMinute) }

    fun toSleepSessions(records: List<SleepSessionRecord>): List<SleepSession> =
        records.map { record ->
            SleepSession(
                start = record.startTime,
                end = record.endTime,
                stages = record.stages.map { toStagePeriod(it) }
            )
        }

    private fun toStagePeriod(stage: SleepSessionRecord.Stage): SleepStagePeriod =
        SleepStagePeriod(mapStageType(stage.stage), stage.startTime, stage.endTime)

    private fun mapStageType(stageType: Int): SleepStageType = when (stageType) {
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStageType.AWAKE
        else -> SleepStageType.UNKNOWN
    }

    fun toTotalSteps(records: List<StepsRecord>): Int = records.sumOf { it.count }.toInt()

    fun toStepsIntervals(records: List<StepsRecord>): List<StepsInterval> =
        records.map { StepsInterval(it.startTime, it.endTime, it.count.toInt()) }

    fun toTotalActiveCalories(records: List<ActiveCaloriesBurnedRecord>): Double =
        records.sumOf { it.energy.inKilocalories }

    fun toExerciseSessions(records: List<ExerciseSessionRecord>): List<ExerciseSession> =
        records.map { ExerciseSession(it.startTime, it.endTime, it.exerciseType) }

    fun toRespiratoryRateSamples(records: List<RespiratoryRateRecord>): List<RespiratoryRateSample> =
        records.map { RespiratoryRateSample(it.time, it.rate) }

    fun toOxygenSaturationSamples(records: List<OxygenSaturationRecord>): List<OxygenSaturationSample> =
        records.map { OxygenSaturationSample(it.time, it.percentage.value) }

    fun toSkinTemperatureDeltaSamples(records: List<SkinTemperatureRecord>): List<SkinTemperatureDeltaSample> =
        records.flatMap { record -> record.deltas.map { SkinTemperatureDeltaSample(it.time, it.delta.inCelsius) } }
}
