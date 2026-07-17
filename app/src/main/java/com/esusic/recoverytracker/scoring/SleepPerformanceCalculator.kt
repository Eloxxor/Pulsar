package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.StrainScore
import kotlin.math.roundToInt

object SleepPerformanceCalculator {
    private const val DEBT_WINDOW_DAYS = 7
    private const val MAX_ACCUMULATED_DEBT_MINUTES = 180.0
    private const val MAX_STRAIN_ADJUSTMENT_MINUTES = 60.0
    private const val MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES = 60.0
    private const val MAX_STRAIN = 21.0
    private const val MIN_NEED_MINUTES = 420.0
    private const val DEEP_REM_TARGET_SHARE = 0.40
    private const val DURATION_WEIGHT = 0.7
    private const val QUALITY_WEIGHT = 0.3

    fun calculateSleepDebtMinutes(typicalSleepMinutes: Double, recentActualSleepMinutes: List<Long>): Double {
        val windowed = recentActualSleepMinutes.takeLast(DEBT_WINDOW_DAYS)
        val totalShortfall = windowed.sumOf { (typicalSleepMinutes - it).coerceAtLeast(0.0) }
        return totalShortfall.coerceAtMost(MAX_ACCUMULATED_DEBT_MINUTES)
    }

    fun calculate(
        actualSleepMinutes: Long?,
        typicalSleepMinutes: Double,
        priorDayStrain: StrainScore?,
        accumulatedDebtMinutes: Double,
        deepRemMinutes: Long? = null
    ): Int? {
        if (actualSleepMinutes == null || typicalSleepMinutes <= 0.0) return null

        val strainAdjustment = ((priorDayStrain?.value ?: 0.0) / MAX_STRAIN) * MAX_STRAIN_ADJUSTMENT_MINUTES
        val debtRepayment = accumulatedDebtMinutes.coerceIn(0.0, MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES)
        // A personal average below 7h is a chronic-deprivation pattern, not a lower
        // need — the floor keeps the score honest for habitual short sleepers.
        val sleepNeedMinutes = typicalSleepMinutes.coerceAtLeast(MIN_NEED_MINUTES) +
            strainAdjustment + debtRepayment

        val durationScore = (actualSleepMinutes / sleepNeedMinutes).coerceIn(0.0, 1.0)
        val qualityScore = if (deepRemMinutes != null && actualSleepMinutes > 0) {
            ((deepRemMinutes.toDouble() / actualSleepMinutes) / DEEP_REM_TARGET_SHARE).coerceIn(0.0, 1.0)
        } else {
            null
        }

        val performance = if (qualityScore != null) {
            (DURATION_WEIGHT * durationScore + QUALITY_WEIGHT * qualityScore) * 100.0
        } else {
            durationScore * 100.0
        }
        return performance.coerceIn(0.0, 100.0).roundToInt()
    }
}
