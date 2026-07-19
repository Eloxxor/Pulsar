package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import java.time.Duration
import kotlin.math.roundToInt

// Minutes spent at or above the strain zone-1 floor (50% heart-rate reserve). Uses the
// same segment attribution as StrainScoreCalculator: each pair of consecutive samples
// contributes its duration to the leading sample, gaps over 15 minutes are dropped.
object ZoneMinutesCalculator {
    private const val MAX_GAP_MINUTES = 15.0
    private const val ZONE_FLOOR_FRACTION = 0.5

    fun calculate(samples: List<HeartRateSample>, maxHr: Double, restingHr: Double): Int {
        if (samples.size < 2 || maxHr <= restingHr) return 0
        val threshold = restingHr + ZONE_FLOOR_FRACTION * (maxHr - restingHr)
        val sorted = samples.sortedBy { it.time }
        var minutes = 0.0
        for (i in 0 until sorted.size - 1) {
            val gapMinutes = Duration.between(sorted[i].time, sorted[i + 1].time).toMillis() / 60000.0
            if (gapMinutes > MAX_GAP_MINUTES) continue
            if (sorted[i].bpm >= threshold) minutes += gapMinutes
        }
        return minutes.roundToInt()
    }
}
