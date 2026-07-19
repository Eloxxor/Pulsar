package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample

// Daytime stress estimate 0-3, modeled after consumer stress monitors: how far the
// typical (median) heart rate sits above the resting baseline, amplified when HRV is
// suppressed vs its baseline. Median, not mean, so workouts don't dominate the score.
object StressScoreCalculator {
    private const val BPM_PER_POINT = 15.0
    private const val MAX_STRESS = 3.0
    private const val HRV_MODIFIER_MIN = 0.75
    private const val HRV_MODIFIER_MAX = 1.5

    fun calculate(
        samples: List<HeartRateSample>,
        restingHr: Double?,
        hrvBaselineMillis: Double?,
        todayHrvMillis: Double?
    ): Double? {
        if (samples.isEmpty() || restingHr == null || restingHr <= 0.0) return null
        val sorted = samples.map { it.bpm.toDouble() }.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
        val base = (median - restingHr).coerceAtLeast(0.0) / BPM_PER_POINT
        val modifier = if (hrvBaselineMillis != null && hrvBaselineMillis > 0.0 &&
            todayHrvMillis != null && todayHrvMillis > 0.0
        ) {
            (hrvBaselineMillis / todayHrvMillis).coerceIn(HRV_MODIFIER_MIN, HRV_MODIFIER_MAX)
        } else {
            1.0
        }
        return (base * modifier).coerceIn(0.0, MAX_STRESS)
    }
}
