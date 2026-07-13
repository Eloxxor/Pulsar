package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.Baseline
import com.esusic.recoverytracker.model.DailyAggregate
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.model.StrainScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RecoveryScoreCalculatorTest {
    private val date = LocalDate.of(2026, 7, 13)
    private val calibratedBaseline = Baseline(mean = 50.0, stdDev = 10.0, sampleCount = Baseline.MIN_SAMPLES_FOR_SCORING)
    private val uncalibratedBaseline = Baseline(mean = 50.0, stdDev = 10.0, sampleCount = 2)

    private fun sleepOf(durationMinutes: Long, deepMinutes: Long, remMinutes: Long): SleepSession {
        val start = Instant.parse("2026-07-13T00:00:00Z")
        val deepEnd = start.plus(deepMinutes, ChronoUnit.MINUTES)
        val remEnd = deepEnd.plus(remMinutes, ChronoUnit.MINUTES)
        val end = start.plus(durationMinutes, ChronoUnit.MINUTES)
        return SleepSession(
            start = start,
            end = end,
            stages = listOf(
                SleepStagePeriod(SleepStageType.DEEP, start, deepEnd),
                SleepStagePeriod(SleepStageType.REM, deepEnd, remEnd),
                SleepStagePeriod(SleepStageType.LIGHT, remEnd, end)
            )
        )
    }

    private fun aggregate(hrv: Double?, rhr: Long?, sleep: SleepSession?) = DailyAggregate(
        date = date, avgHrvMillis = hrv, restingHeartRate = rhr, sleep = sleep,
        steps = 5000, activeCaloriesKcal = 200.0, heartRateSamples = emptyList(),
        exerciseSessions = emptyList(), isComplete = true
    )

    @Test
    fun `isCalibrating true when either baseline below minimum samples`() {
        val result = RecoveryScoreCalculator.calculate(
            date, aggregate(50.0, 50L, sleepOf(420, 80, 90)),
            uncalibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        assertTrue(result.isCalibrating)
    }

    @Test
    fun `above-baseline HRV and below-baseline RHR raise the score`() {
        val goodDay = RecoveryScoreCalculator.calculate(
            date, aggregate(hrv = 70.0, rhr = 40L, sleep = sleepOf(420, 80, 90)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        val badDay = RecoveryScoreCalculator.calculate(
            date, aggregate(hrv = 30.0, rhr = 60L, sleep = sleepOf(420, 80, 90)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        assertTrue(goodDay.value > badDay.value)
    }

    @Test
    fun `high prior-day strain lowers the score relative to no prior strain`() {
        val noStrain = RecoveryScoreCalculator.calculate(
            date, aggregate(50.0, 50L, sleepOf(420, 80, 90)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, priorDayStrain = null
        )
        val highStrain = RecoveryScoreCalculator.calculate(
            date, aggregate(50.0, 50L, sleepOf(420, 80, 90)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0,
            priorDayStrain = StrainScore(date.minusDays(1), 21.0)
        )
        assertTrue(highStrain.value < noStrain.value)
    }

    @Test
    fun `score is always clamped between 0 and 100`() {
        val result = RecoveryScoreCalculator.calculate(
            date, aggregate(hrv = 200.0, rhr = 30L, sleep = sleepOf(600, 300, 200)),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        assertEquals(result.value, result.value.coerceIn(0, 100))
    }

    @Test
    fun `missing sleep falls back to neutral sleep sub-score`() {
        val result = RecoveryScoreCalculator.calculate(
            date, aggregate(50.0, 50L, sleep = null),
            calibratedBaseline, calibratedBaseline, 420.0, 40.0, null
        )
        assertEquals(50.0, result.sleepSubScore, 0.0001)
    }
}
