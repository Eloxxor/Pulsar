// app/src/test/java/com/esusic/recoverytracker/model/BaselineTest.kt
package com.esusic.recoverytracker.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BaselineTest {
    @Test
    fun `zScore returns 0 when stdDev is zero`() {
        val baseline = Baseline(mean = 50.0, stdDev = 0.0, sampleCount = 10)
        assertEquals(0.0, baseline.zScore(80.0), 0.0001)
    }

    @Test
    fun `zScore computes standard deviations from mean`() {
        val baseline = Baseline(mean = 50.0, stdDev = 10.0, sampleCount = 10)
        assertEquals(2.0, baseline.zScore(70.0), 0.0001)
        assertEquals(-1.0, baseline.zScore(40.0), 0.0001)
    }

    @Test
    fun `zScore returns 0 when sample count below minimum`() {
        val baseline = Baseline(mean = 50.0, stdDev = 10.0, sampleCount = 3)
        assertEquals(0.0, baseline.zScore(70.0), 0.0001)
    }
}
