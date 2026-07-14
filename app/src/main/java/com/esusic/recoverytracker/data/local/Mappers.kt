package com.esusic.recoverytracker.data.local

import com.esusic.recoverytracker.model.DailyAggregate
import com.esusic.recoverytracker.model.RecoveryScore
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.model.StrainScore

fun DailyAggregate.toEntity(): DailyAggregateEntity = DailyAggregateEntity(
    date = date.toString(),
    avgHrvMillis = avgHrvMillis,
    restingHeartRate = restingHeartRate,
    sleepDurationMinutes = sleep?.durationMinutes,
    deepSleepMinutes = sleep?.minutesIn(SleepStageType.DEEP),
    remSleepMinutes = sleep?.minutesIn(SleepStageType.REM),
    lightSleepMinutes = sleep?.minutesIn(SleepStageType.LIGHT),
    steps = steps,
    activeCaloriesKcal = activeCaloriesKcal,
    isComplete = isComplete,
    avgRespiratoryRate = avgRespiratoryRate,
    avgOxygenSaturationPercent = avgOxygenSaturationPercent,
    avgSkinTemperatureDeltaCelsius = avgSkinTemperatureDeltaCelsius,
    latestHeartRateBpm = latestHeartRateSample?.bpm,
    latestHeartRateEpochMillis = latestHeartRateSample?.time?.toEpochMilli()
)

fun DailyScoreEntity.toRecoveryScore(): RecoveryScore = RecoveryScore(
    date = java.time.LocalDate.parse(date),
    value = recoveryValue,
    isCalibrating = recoveryIsCalibrating,
    hrvSubScore = hrvSubScore,
    rhrSubScore = rhrSubScore,
    sleepSubScore = sleepSubScore
)

fun DailyScoreEntity.toStrainScore(): StrainScore = StrainScore(
    date = java.time.LocalDate.parse(date),
    value = strainValue
)
