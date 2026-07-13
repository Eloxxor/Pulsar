package com.esusic.recoverytracker.model

data class Baseline(val mean: Double, val stdDev: Double, val sampleCount: Int) {
    fun zScore(value: Double): Double {
        if (stdDev == 0.0 || sampleCount < MIN_SAMPLES_FOR_SCORING) return 0.0
        return (value - mean) / stdDev
    }

    companion object {
        const val MIN_SAMPLES_FOR_SCORING = 7
    }
}
