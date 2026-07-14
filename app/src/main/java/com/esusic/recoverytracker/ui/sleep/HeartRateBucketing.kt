package com.esusic.recoverytracker.ui.sleep

import com.esusic.recoverytracker.model.HeartRateSample
import java.time.Duration
import java.time.Instant

data class HeartRateBucket(val time: Instant, val avgBpm: Double)

fun bucketHeartRateSamples(
    samples: List<HeartRateSample>,
    sessionStart: Instant,
    bucketSize: Duration = Duration.ofMinutes(2)
): List<HeartRateBucket> {
    if (samples.isEmpty()) return emptyList()
    val bucketSizeMillis = bucketSize.toMillis()
    return samples
        .groupBy { Duration.between(sessionStart, it.time).toMillis() / bucketSizeMillis }
        .toSortedMap()
        .map { (bucketIndex, bucketSamples) ->
            val bucketTime = sessionStart.plusMillis(bucketIndex * bucketSizeMillis)
            HeartRateBucket(bucketTime, bucketSamples.map { it.bpm }.average())
        }
}
