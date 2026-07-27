package com.esusic.recoverytracker.ui.navigation

import com.esusic.recoverytracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootDestinationTest {
    @Test
    fun `root destinations expose the approved five tabs in order`() {
        assertEquals(
            listOf("home", "sleep", "activity", "journal", "account"),
            RootDestination.entries.map { it.route }
        )
        assertEquals(
            listOf(
                R.string.nav_home,
                R.string.nav_sleep,
                R.string.nav_activity,
                R.string.nav_journal,
                R.string.nav_account
            ),
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
