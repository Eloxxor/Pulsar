package com.esusic.recoverytracker.ui.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthStatusPresentationTest {
    @Test
    fun `recovery bands use approved labels at boundaries`() {
        assertEquals("Recover", recoveryStatus(39, false).label)
        assertEquals("Balanced", recoveryStatus(40, false).label)
        assertEquals("Ready", recoveryStatus(60, false).label)
        assertEquals("Optimal", recoveryStatus(80, false).label)
    }

    @Test
    fun `calibration overrides numeric recovery`() {
        assertEquals("Learning your baseline", recoveryStatus(90, true).label)
    }

    @Test
    fun `calibration recommendation stays compact`() {
        assertEquals(
            "Keep wearing your tracker to build a reliable baseline.",
            todayRecommendation(recovery = null, strain = null, sleep = null, calibrating = true)
        )
    }

    @Test
    fun `effort preserves the real zero to twenty-one scale`() {
        assertEquals("Resting", effortStatus(0.0).label)
        assertEquals("Light", effortStatus(5.0).label)
        assertEquals("Productive", effortStatus(10.0).label)
        assertEquals("High", effortStatus(16.0).label)
    }

    @Test
    fun `recommendation prioritizes recovery before current effort`() {
        assertEquals(
            "Prioritize rest and easy movement today.",
            todayRecommendation(recovery = 30, strain = 2.0, sleep = 90, calibrating = false)
        )
    }

    @Test
    fun `missing values have honest copy`() {
        assertEquals("No score yet", sleepStatus(null).label)
    }
}
