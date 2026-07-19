package com.esusic.recoverytracker.ui.navigation

enum class RootDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    SLEEP("sleep", "Sleep"),
    ACTIVITY("activity", "Activity"),
    ACCOUNT("account", "Account")
}

fun shouldShowBottomBar(route: String?): Boolean =
    RootDestination.entries.any { it.route == route }
