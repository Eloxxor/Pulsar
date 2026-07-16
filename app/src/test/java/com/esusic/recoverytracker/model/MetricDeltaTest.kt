package com.esusic.recoverytracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricDeltaTest {

    @Test
    fun `returns null when baseline is missing`() {
        assertNull(metricDelta(50.0, null, higherIsBetter = true))
    }

    @Test
    fun `returns null when current value is missing`() {
        assertNull(metricDelta(null, 50.0, higherIsBetter = true))
    }

    @Test
    fun `higher value is BETTER when higher is better`() {
        assertEquals(DeltaKind.BETTER, metricDelta(55.0, 50.0, higherIsBetter = true)!!.kind)
    }

    @Test
    fun `higher value is WORSE when lower is better`() {
        assertEquals(DeltaKind.WORSE, metricDelta(55.0, 50.0, higherIsBetter = false)!!.kind)
    }

    @Test
    fun `deviation inside the neutral band is NEUTRAL`() {
        assertEquals(DeltaKind.NEUTRAL, metricDelta(50.5, 50.0, higherIsBetter = true)!!.kind)
    }

    @Test
    fun `informational metrics without a good direction are always NEUTRAL`() {
        assertEquals(DeltaKind.NEUTRAL, metricDelta(20.0, 14.0, higherIsBetter = null)!!.kind)
    }

    @Test
    fun `diff carries the signed difference`() {
        assertEquals(-3.0, metricDelta(47.0, 50.0, higherIsBetter = false)!!.diff, 0.0001)
    }
}
