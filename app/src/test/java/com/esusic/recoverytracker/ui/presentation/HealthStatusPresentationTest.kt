package com.esusic.recoverytracker.ui.presentation

import com.esusic.recoverytracker.R
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthStatusPresentationTest {
    @Test
    fun `recovery bands use approved labels at boundaries`() {
        assertEquals(R.string.status_recovery_low_label, recoveryStatus(39, false).label)
        assertEquals(R.string.status_recovery_balanced_label, recoveryStatus(40, false).label)
        assertEquals(R.string.status_recovery_ready_label, recoveryStatus(60, false).label)
        assertEquals(R.string.status_recovery_optimal_label, recoveryStatus(80, false).label)
    }

    @Test
    fun `calibration overrides numeric recovery`() {
        assertEquals(R.string.status_recovery_calibrating_label, recoveryStatus(90, true).label)
    }

    @Test
    fun `calibration recommendation stays compact`() {
        assertEquals(
            R.string.recommendation_calibrating,
            todayRecommendation(recovery = null, strain = null, sleep = null, calibrating = true)
        )
    }

    @Test
    fun `effort preserves the real zero to twenty-one scale`() {
        assertEquals(R.string.status_effort_resting_label, effortStatus(0.0).label)
        assertEquals(R.string.status_effort_light_label, effortStatus(5.0).label)
        assertEquals(R.string.status_effort_productive_label, effortStatus(10.0).label)
        assertEquals(R.string.status_effort_high_label, effortStatus(16.0).label)
    }

    @Test
    fun `recommendation prioritizes recovery before current effort`() {
        assertEquals(
            R.string.recommendation_rest,
            todayRecommendation(recovery = 30, strain = 2.0, sleep = 90, calibrating = false)
        )
    }

    @Test
    fun `missing values have honest copy`() {
        assertEquals(R.string.status_sleep_none_label, sleepStatus(null).label)
        assertEquals(R.string.status_effort_none_label, effortStatus(null).label)
        assertEquals(R.string.status_recovery_none_label, recoveryStatus(null, false).label)
    }
}
