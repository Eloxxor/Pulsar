package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.StrainScore
import java.time.Duration
import java.time.LocalDate
import kotlin.math.ln

object StrainScoreCalculator {
    private val ZONE_WEIGHTS = mapOf(1 to 1.0, 2 to 2.0, 3 to 4.0, 4 to 8.0, 5 to 16.0)
    private const val SCALE = 3.5
    private const val MAX_STRAIN = 21.0
    private const val MAX_GAP_MINUTES = 15.0

    fun calculate(date: LocalDate, samples: List<HeartRateSample>, maxHr: Double): StrainScore {
        if (samples.size < 2) return StrainScore(date, 0.0)

        val sorted = samples.sortedBy { it.time }
        var rawLoad = 0.0

        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val minutes = Duration.between(a.time, b.time).toMillis() / 60000.0
            if (minutes <= 0.0 || minutes > MAX_GAP_MINUTES) continue

            val avgBpm = (a.bpm + b.bpm) / 2.0
            val zone = zoneFor(avgBpm, maxHr)
            if (zone > 0) rawLoad += (ZONE_WEIGHTS[zone] ?: 0.0) * minutes
        }

        val strain = (ln(1.0 + rawLoad) * SCALE).coerceIn(0.0, MAX_STRAIN)
        return StrainScore(date, strain)
    }

    private fun zoneFor(bpm: Double, maxHr: Double): Int {
        val pct = bpm / maxHr * 100.0
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
