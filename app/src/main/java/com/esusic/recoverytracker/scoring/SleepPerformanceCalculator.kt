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
    private const val DEEP_REM_TARGET_SHARE = 0.45
    private const val DURATION_WEIGHT = 0.45
    private const val QUALITY_WEIGHT = 0.35
    private const val RESTFULNESS_WEIGHT = 0.20

    // Asleep share of the session: 95%+ earns full restfulness, 85% or less earns none.
    private const val ASLEEP_SHARE_FLOOR = 0.85
    private const val ASLEEP_SHARE_TARGET = 0.95

    fun calculateSleepDebtMinutes(typicalSleepMinutes: Double, recentActualSleepMinutes: List<Long>): Double {
        val windowed = recentActualSleepMinutes.takeLast(DEBT_WINDOW_DAYS)
        val totalShortfall = windowed.sumOf { (typicalSleepMinutes - it).coerceAtLeast(0.0) }
        return totalShortfall.coerceAtMost(MAX_ACCUMULATED_DEBT_MINUTES)
    }

    fun calculateNeedMinutes(
        typicalSleepMinutes: Double,
        priorDayStrain: StrainScore?,
        accumulatedDebtMinutes: Double
    ): Double {
        val strainAdjustment = ((priorDayStrain?.value ?: 0.0) / MAX_STRAIN) * MAX_STRAIN_ADJUSTMENT_MINUTES
        val debtRepayment = accumulatedDebtMinutes.coerceIn(0.0, MAX_NIGHTLY_DEBT_REPAYMENT_MINUTES)
        // A personal average below 7h is a chronic-deprivation pattern, not a lower
        // need — the floor keeps the score honest for habitual short sleepers.
        return typicalSleepMinutes.coerceAtLeast(MIN_NEED_MINUTES) + strainAdjustment + debtRepayment
    }

    fun calculate(
        actualSleepMinutes: Long?,
        typicalSleepMinutes: Double,
        priorDayStrain: StrainScore?,
        accumulatedDebtMinutes: Double,
        deepRemMinutes: Long? = null,
        asleepMinutes: Long? = null
    ): Int? {
        if (actualSleepMinutes == null || typicalSleepMinutes <= 0.0) return null

        val sleepNeedMinutes = calculateNeedMinutes(typicalSleepMinutes, priorDayStrain, accumulatedDebtMinutes)

        val durationScore = (actualSleepMinutes / sleepNeedMinutes).coerceIn(0.0, 1.0)
        val qualityScore = if (deepRemMinutes != null && actualSleepMinutes > 0) {
            ((deepRemMinutes.toDouble() / actualSleepMinutes) / DEEP_REM_TARGET_SHARE).coerceIn(0.0, 1.0)
        } else {
            null
        }
        val restfulnessScore = if (asleepMinutes != null && actualSleepMinutes > 0) {
            val asleepShare = asleepMinutes.toDouble() / actualSleepMinutes
            ((asleepShare - ASLEEP_SHARE_FLOOR) / (ASLEEP_SHARE_TARGET - ASLEEP_SHARE_FLOOR)).coerceIn(0.0, 1.0)
        } else {
            null
        }

        // Weights renormalize over the available components so a night without stage
        // data still scores on duration alone instead of being penalised for it.
        val weighted = buildList {
            add(DURATION_WEIGHT to durationScore)
            qualityScore?.let { add(QUALITY_WEIGHT to it) }
            restfulnessScore?.let { add(RESTFULNESS_WEIGHT to it) }
        }
        val totalWeight = weighted.sumOf { it.first }
        val performance = weighted.sumOf { (weight, score) -> weight * score } / totalWeight * 100.0
        return performance.coerceIn(0.0, 100.0).roundToInt()
    }
}
