package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.Baseline
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class BaselineCalculatorTest {
    @Test
    fun `empty input yields zeroed baseline`() {
        val baseline = BaselineCalculator.calculate(emptyList())
        assertEquals(Baseline(0.0, 0.0, 0), baseline)
    }

    @Test
    fun `computes mean and population stdDev`() {
        val values = listOf(10.0, 12.0, 14.0)
        val baseline = BaselineCalculator.calculate(values)
        val expectedMean = 12.0
        val expectedStdDev = sqrt(((10.0 - 12.0).let { it * it } + 0.0 + (14.0 - 12.0).let { it * it }) / 3.0)
        assertEquals(expectedMean, baseline.mean, 0.0001)
        assertEquals(expectedStdDev, baseline.stdDev, 0.0001)
        assertEquals(3, baseline.sampleCount)
    }

    @Test
    fun `only uses the trailing WINDOW_DAYS values`() {
        val oldOutlier = listOf(1000.0)
        val recent = List(BaselineCalculator.WINDOW_DAYS) { 50.0 }
        val baseline = BaselineCalculator.calculate(oldOutlier + recent)
        assertEquals(50.0, baseline.mean, 0.0001)
        assertEquals(BaselineCalculator.WINDOW_DAYS, baseline.sampleCount)
    }
}
