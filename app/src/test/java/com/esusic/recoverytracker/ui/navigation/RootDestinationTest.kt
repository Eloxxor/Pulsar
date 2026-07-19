package com.esusic.recoverytracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootDestinationTest {
    @Test
    fun `root destinations expose the approved four tabs in order`() {
        assertEquals(
            listOf("home", "sleep", "activity", "account"),
            RootDestination.entries.map { it.route }
        )
        assertEquals(
            listOf("Home", "Sleep", "Activity", "Account"),
            RootDestination.entries.map { it.label }
        )
    }

    @Test
    fun `bottom bar is limited to root destinations`() {
        RootDestination.entries.forEach { assertTrue(shouldShowBottomBar(it.route)) }

        assertFalse(shouldShowBottomBar("onboarding"))
        assertFalse(shouldShowBottomBar("age"))
        assertFalse(shouldShowBottomBar("recovery/{date}"))
        assertFalse(shouldShowBottomBar("detail/{metricType}"))
        assertFalse(shouldShowBottomBar(null))
    }
}
