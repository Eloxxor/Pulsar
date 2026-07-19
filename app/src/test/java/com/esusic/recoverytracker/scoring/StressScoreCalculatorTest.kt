package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class StressScoreCalculatorTest {
    private fun samples(vararg bpm: Long) = bpm.mapIndexed { i, b ->
        HeartRateSample(Instant.parse("2026-07-18T10:00:00Z").plusSeconds(i * 60L), b)
    }

    @Test
    fun `no samples or no resting hr returns null`() {
        assertNull(StressScoreCalculator.calculate(emptyList(), 60.0, null, null))
        assertNull(StressScoreCalculator.calculate(samples(70), null, null, null))
    }

    @Test
    fun `median at resting hr is zero stress`() {
        assertEquals(0.0, StressScoreCalculator.calculate(samples(60, 60, 60), 60.0, null, null)!!, 0.001)
    }

    @Test
    fun `fifteen bpm above resting is one point without hrv data`() {
        assertEquals(1.0, StressScoreCalculator.calculate(samples(75, 75, 75), 60.0, null, null)!!, 0.001)
    }

    @Test
    fun `low hrv vs baseline amplifies stress`() {
        // base 1.0, modifier = 40/32 = 1.25
        assertEquals(1.25, StressScoreCalculator.calculate(samples(75, 75, 75), 60.0, 40.0, 32.0)!!, 0.001)
    }

    @Test
    fun `hrv modifier clamps at one point five`() {
        // 40/10 = 4.0 -> clamped to 1.5
        assertEquals(1.5, StressScoreCalculator.calculate(samples(75, 75, 75), 60.0, 40.0, 10.0)!!, 0.001)
    }

    @Test
    fun `high hrv can dampen stress but only down to three quarters`() {
        // 40/80 = 0.5 -> clamped to 0.75
        assertEquals(0.75, StressScoreCalculator.calculate(samples(75, 75, 75), 60.0, 40.0, 80.0)!!, 0.001)
    }

    @Test
    fun `stress clamps at three`() {
        assertEquals(3.0, StressScoreCalculator.calculate(samples(150, 150, 150), 60.0, null, null)!!, 0.001)
    }

    @Test
    fun `median resists workout spikes`() {
        // median of 60,62,64,150,155 = 64 -> base 4/15
        assertEquals(4.0 / 15.0, StressScoreCalculator.calculate(samples(60, 62, 64, 150, 155), 60.0, null, null)!!, 0.001)
    }

    @Test
    fun `even sample count uses mean of middle pair`() {
        // sorted 60,70,80,90 -> median 75 -> base 1.0
        assertEquals(1.0, StressScoreCalculator.calculate(samples(80, 60, 90, 70), 60.0, null, null)!!, 0.001)
    }
}
