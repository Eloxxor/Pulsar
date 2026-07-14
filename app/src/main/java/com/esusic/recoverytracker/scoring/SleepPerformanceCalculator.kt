package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.StrainScore
import kotlin.math.roundToInt

object SleepPerformanceCalculator {
    private const val DEBT_WINDOW_DAYS = 7
    private const val MAX_ACCUMULATED_DEBT_MINUTES = 180.0
    private const val MAX_STRAIN_ADJUSTMENT_MINUTES = 60.0
    private const val MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES = 60.0
    private const val MAX_STRAIN = 21.0

    fun calculateSleepDebtMinutes(typicalSleepMinutes: Double, recentActualSleepMinutes: List<Long>): Double {
        val windowed = recentActualSleepMinutes.takeLast(DEBT_WINDOW_DAYS)
        val totalShortfall = windowed.sumOf { (typicalSleepMinutes - it).coerceAtLeast(0.0) }
        return totalShortfall.coerceAtMost(MAX_ACCUMULATED_DEBT_MINUTES)
    }

    fun calculate(
        actualSleepMinutes: Long?,
        typicalSleepMinutes: Double,
        priorDayStrain: StrainScore?,
        accumulatedDebtMinutes: Double
    ): Int? {
        if (actualSleepMinutes == null || typicalSleepMinutes <= 0.0) return null

        val strainAdjustment = ((priorDayStrain?.value ?: 0.0) / MAX_STRAIN) * MAX_STRAIN_ADJUSTMENT_MINUTES
        val debtRepayment = accumulatedDebtMinutes.coerceIn(0.0, MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES)
        val sleepNeedMinutes = typicalSleepMinutes + strainAdjustment + debtRepayment

        val performance = (actualSleepMinutes / sleepNeedMinutes) * 100.0
        return performance.coerceIn(0.0, 100.0).roundToInt()
    }
}
