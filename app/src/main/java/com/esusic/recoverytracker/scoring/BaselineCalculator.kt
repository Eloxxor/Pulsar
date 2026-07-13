package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.Baseline
import kotlin.math.sqrt

object BaselineCalculator {
    const val WINDOW_DAYS = 30

    fun calculate(recentValues: List<Double>): Baseline {
        val windowed = recentValues.takeLast(WINDOW_DAYS)
        if (windowed.isEmpty()) return Baseline(mean = 0.0, stdDev = 0.0, sampleCount = 0)

        val mean = windowed.average()
        val variance = windowed.sumOf { (it - mean) * (it - mean) } / windowed.size
        return Baseline(mean = mean, stdDev = sqrt(variance), sampleCount = windowed.size)
    }
}
