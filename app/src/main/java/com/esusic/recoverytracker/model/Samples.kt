package com.esusic.recoverytracker.model

import java.time.Duration
import java.time.Instant

data class HeartRateSample(val time: Instant, val bpm: Long)

data class HrvSample(val time: Instant, val rmssdMillis: Double)

data class RestingHrSample(val time: Instant, val bpm: Long)

enum class SleepStageType { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

data class SleepStagePeriod(val stage: SleepStageType, val start: Instant, val end: Instant)

data class SleepSession(val start: Instant, val end: Instant, val stages: List<SleepStagePeriod>) {
    val durationMinutes: Long get() = Duration.between(start, end).toMinutes()

    fun minutesIn(stage: SleepStageType): Long =
        stages.filter { it.stage == stage }.sumOf { Duration.between(it.start, it.end).toMinutes() }
}

data class ExerciseSession(val start: Instant, val end: Instant, val exerciseType: Int)
