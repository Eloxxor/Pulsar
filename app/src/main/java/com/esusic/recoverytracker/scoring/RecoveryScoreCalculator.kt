package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.Baseline
import com.esusic.recoverytracker.model.DailyAggregate
import com.esusic.recoverytracker.model.RecoveryScore
import com.esusic.recoverytracker.model.SleepSession
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.model.StrainScore
import java.time.LocalDate
import kotlin.math.roundToInt

object RecoveryScoreCalculator {
    private const val HRV_WEIGHT = 0.35
    private const val RHR_WEIGHT = 0.25
    private const val SLEEP_WEIGHT = 0.30
    private const val STRAIN_WEIGHT = 0.10
    private const val NEUTRAL_SUB_SCORE = 50.0
    private const val MAX_STRAIN = 21.0

    fun calculate(
        date: LocalDate,
        today: DailyAggregate,
        hrvBaseline: Baseline,
        rhrBaseline: Baseline,
        typicalSleepMinutes: Double,
        typicalDeepRemPercent: Double,
        priorDayStrain: StrainScore?
    ): RecoveryScore {
        val isCalibrating = hrvBaseline.sampleCount < Baseline.MIN_SAMPLES_FOR_SCORING ||
            rhrBaseline.sampleCount < Baseline.MIN_SAMPLES_FOR_SCORING

        val hrvSub = today.avgHrvMillis
            ?.let { subScoreFromZ(hrvBaseline.zScore(it), invert = false) }
            ?: NEUTRAL_SUB_SCORE
        val rhrSub = today.restingHeartRate
            ?.let { subScoreFromZ(rhrBaseline.zScore(it.toDouble()), invert = true) }
            ?: NEUTRAL_SUB_SCORE
        val sleepSub = sleepSubScore(today.sleep, typicalSleepMinutes, typicalDeepRemPercent)
        val strainSub = strainSubScore(priorDayStrain)

        val composite = hrvSub * HRV_WEIGHT + rhrSub * RHR_WEIGHT +
            sleepSub * SLEEP_WEIGHT + strainSub * STRAIN_WEIGHT

        return RecoveryScore(
            date = date,
            value = composite.roundToInt().coerceIn(0, 100),
            isCalibrating = isCalibrating,
            hrvSubScore = hrvSub,
            rhrSubScore = rhrSub,
            sleepSubScore = sleepSub
        )
    }

    private fun subScoreFromZ(z: Double, invert: Boolean): Double {
        val signed = if (invert) -z else z
        val clamped = signed.coerceIn(-2.0, 2.0)
        return ((clamped + 2.0) / 4.0) * 100.0
    }

    private fun strainSubScore(priorDayStrain: StrainScore?): Double {
        val strainPenalty = priorDayStrain?.let { (it.value / MAX_STRAIN).coerceIn(0.0, 1.0) } ?: 0.0
        return (1.0 - strainPenalty) * 100.0
    }

    private fun sleepSubScore(
        sleep: SleepSession?,
        typicalMinutes: Double,
        typicalDeepRemPercent: Double
    ): Double {
        if (sleep == null || typicalMinutes <= 0.0) return NEUTRAL_SUB_SCORE

        val durationScore = (sleep.durationMinutes / typicalMinutes).coerceIn(0.0, 1.0) * 100.0

        val deepRemMinutes = sleep.minutesIn(SleepStageType.DEEP) + sleep.minutesIn(SleepStageType.REM)
        val deepRemPercent = if (sleep.durationMinutes > 0) {
            deepRemMinutes.toDouble() / sleep.durationMinutes * 100.0
        } else {
            0.0
        }
        val qualityScore = if (typicalDeepRemPercent <= 0.0) {
            100.0
        } else {
            (deepRemPercent / typicalDeepRemPercent * 100.0).coerceIn(0.0, 100.0)
        }

        return durationScore * 0.6 + qualityScore * 0.4
    }
}
