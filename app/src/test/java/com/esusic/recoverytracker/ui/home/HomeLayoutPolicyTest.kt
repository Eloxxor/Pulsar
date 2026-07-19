package com.esusic.recoverytracker.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutPolicyTest {
    @Test
    fun `readiness cards keep asymmetric layout on the connected phone size`() {
        assertEquals(
            ReadinessLayout.ASYMMETRIC,
            readinessLayoutFor(maxWidthDp = 411, fontScale = 1f)
        )
    }

    @Test
    fun `readiness cards stack on small phones and with large text`() {
        assertEquals(
            ReadinessLayout.STACKED,
            readinessLayoutFor(maxWidthDp = 375, fontScale = 1f)
        )
        assertEquals(
            ReadinessLayout.STACKED,
            readinessLayoutFor(maxWidthDp = 411, fontScale = 1.3f)
        )
    }
}
