package com.esusic.recoverytracker.model

import java.time.LocalDate

data class DailyAggregate(
    val date: LocalDate,
    val avgHrvMillis: Double?,
    val restingHeartRate: Long?,
    val sleep: SleepSession?,
    val steps: Int,
    val activeCaloriesKcal: Double,
    val heartRateSamples: List<HeartRateSample>,
    val exerciseSessions: List<ExerciseSession>,
    val isComplete: Boolean
)
