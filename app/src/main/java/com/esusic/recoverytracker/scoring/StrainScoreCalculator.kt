package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.StrainScore
import java.time.Duration
import java.time.LocalDate
import kotlin.math.ln

object StrainScoreCalculator {
    private val ZONE_WEIGHTS = mapOf(1 to 1.0, 2 to 2.0, 3 to 4.0, 4 to 8.0, 5 to 16.0)
    private const val SCALE = 2.6
    private const val MAX_STRAIN = 21.0
    private const val MAX_GAP_MINUTES = 15.0

    // Everyday steps sit below 50% of heart-rate reserve and never reach the HR zones,
    // yet a 15k-step day is real load. Calibrated so ~15k steps alone land around 8.5.
    private const val STEP_LOAD_PER_STEP = 0.0016

    fun calculate(
        date: LocalDate,
        samples: List<HeartRateSample>,
        maxHr: Double,
        restingHr: Double,
        steps: Int = 0
    ): StrainScore {
        val sorted = samples.sortedBy { it.time }
        var rawLoad = 0.0

        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val minutes = Duration.between(a.time, b.time).toMillis() / 60000.0
            if (minutes <= 0.0 || minutes > MAX_GAP_MINUTES) continue

            val avgBpm = (a.bpm + b.bpm) / 2.0
            val zone = zoneFor(avgBpm, maxHr, restingHr)
            if (zone > 0) rawLoad += (ZONE_WEIGHTS[zone] ?: 0.0) * minutes
        }

        rawLoad += steps.coerceAtLeast(0) * STEP_LOAD_PER_STEP

        val strain = (ln(1.0 + rawLoad) * SCALE).coerceIn(0.0, MAX_STRAIN)
        return StrainScore(date, strain)
    }

    // Zones use heart-rate reserve (Karvonen) instead of a plain percentage of max HR.
    // With a high resting HR, everyday walking-around heart rates sit well above 50% of
    // max HR and used to accumulate load all day, saturating the score at 21.
    private fun zoneFor(bpm: Double, maxHr: Double, restingHr: Double): Int {
        val reserve = maxHr - restingHr
        if (reserve <= 0.0) return 0
        val pct = (bpm - restingHr) / reserve * 100.0
        return when {
            pct < 50.0 -> 0
            pct < 60.0 -> 1
            pct < 70.0 -> 2
            pct < 80.0 -> 3
            pct < 90.0 -> 4
            else -> 5
        }
    }
}
