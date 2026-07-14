package com.esusic.recoverytracker.ui.sleep

import com.esusic.recoverytracker.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HeartRateBucketingTest {
    private val sessionStart = Instant.parse("2026-07-13T23:00:00Z")

    @Test
    fun `buckets evenly spaced samples into 2-minute averages`() {
        val samples = listOf(
            HeartRateSample(sessionStart, 60L),
            HeartRateSample(sessionStart.plusSeconds(30), 62L),
            HeartRateSample(sessionStart.plusSeconds(150), 70L),
            HeartRateSample(sessionStart.plusSeconds(170), 74L)
        )
        val result = bucketHeartRateSamples(samples, sessionStart, Duration.ofMinutes(2))
        assertEquals(2, result.size)
        assertEquals(61.0, result[0].avgBpm, 0.0001)
        assertEquals(72.0, result[1].avgBpm, 0.0001)
    }

    @Test
    fun `skips buckets with no samples rather than inserting gaps`() {
        val samples = listOf(
            HeartRateSample(sessionStart, 60L),
            HeartRateSample(sessionStart.plusSeconds(600), 80L)
        )
        val result = bucketHeartRateSamples(samples, sessionStart, Duration.ofMinutes(2))
        assertEquals(2, result.size)
    }

    @Test
    fun `single sample produces a single bucket`() {
        val samples = listOf(HeartRateSample(sessionStart.plusSeconds(500), 65L))
        val result = bucketHeartRateSamples(samples, sessionStart, Duration.ofMinutes(2))
        assertEquals(1, result.size)
        assertEquals(65.0, result.single().avgBpm, 0.0001)
    }

    @Test
    fun `empty input produces no buckets`() {
        val result = bucketHeartRateSamples(emptyList(), sessionStart, Duration.ofMinutes(2))
        assertEquals(0, result.size)
    }
}
