package com.esusic.recoverytracker.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutPolicyTest {
    @Test
    fun `readiness cards keep asymmetric layout on the connected phone size`() {
        assertEquals(
            ReadinessLayout.ASYMMETRIC,
            readinessLayoutFor(maxWidthDp = 371, fontScale = 1f)
        )
    }

    @Test
    fun `readiness cards stack on small phones and with large text`() {
        assertEquals(
            ReadinessLayout.STACKED,
            readinessLayoutFor(maxWidthDp = 335, fontScale = 1f)
        )
        assertEquals(
            ReadinessLayout.STACKED,
            readinessLayoutFor(maxWidthDp = 371, fontScale = 1.3f)
        )
    }

    @Test
    fun `sleep stage summary omits unknown zero-only detail`() {
        assertEquals("", sleepStageSummaryFor(deep = 0, rem = 0, light = 0))
        assertEquals(
            "REM 1h 30m",
            sleepStageSummaryFor(deep = 0, rem = 90, light = null)
        )
    }
}
