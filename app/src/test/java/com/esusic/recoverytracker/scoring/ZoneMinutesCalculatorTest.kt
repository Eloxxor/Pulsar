package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class ZoneMinutesCalculatorTest {
    private val t0 = Instant.parse("2026-07-18T10:00:00Z")
    private fun at(min: Long, bpm: Long) = HeartRateSample(t0.plus(min, ChronoUnit.MINUTES), bpm)

    @Test
    fun `fewer than two samples is zero`() {
        assertEquals(0, ZoneMinutesCalculator.calculate(listOf(at(0, 140)), 190.0, 60.0))
        assertEquals(0, ZoneMinutesCalculator.calculate(emptyList(), 190.0, 60.0))
    }

    @Test
    fun `minutes above threshold count`() {
        // threshold = 60 + 0.5*130 = 125; three 10-min segments, all leading samples >= 125
        val samples = listOf(at(0, 130), at(10, 135), at(20, 140), at(30, 130))
        assertEquals(30, ZoneMinutesCalculator.calculate(samples, 190.0, 60.0))
    }

    @Test
    fun `minutes below threshold do not count`() {
        // only the 20->30 segment has a leading sample >= 125
        val samples = listOf(at(0, 80), at(10, 90), at(20, 130), at(30, 60))
        assertEquals(10, ZoneMinutesCalculator.calculate(samples, 190.0, 60.0))
    }

    @Test
    fun `gaps over fifteen minutes are ignored`() {
        // 0->60 gap dropped; only 60->70 counts
        val samples = listOf(at(0, 130), at(60, 130), at(70, 130))
        assertEquals(10, ZoneMinutesCalculator.calculate(samples, 190.0, 60.0))
    }

    @Test
    fun `degenerate hr range is zero`() {
        assertEquals(0, ZoneMinutesCalculator.calculate(listOf(at(0, 130), at(10, 130)), 60.0, 60.0))
    }
}
