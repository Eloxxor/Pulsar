package com.esusic.recoverytracker.ui.navigation

import androidx.annotation.StringRes
import com.esusic.recoverytracker.R

enum class RootDestination(val route: String, @get:StringRes val label: Int) {
    HOME("home", R.string.nav_home),
    SLEEP("sleep", R.string.nav_sleep),
    ACTIVITY("activity", R.string.nav_activity),
    JOURNAL("journal", R.string.nav_journal),
    ACCOUNT("account", R.string.nav_account)
}

fun shouldShowBottomBar(route: String?): Boolean =
    RootDestination.entries.any { it.route == route }
