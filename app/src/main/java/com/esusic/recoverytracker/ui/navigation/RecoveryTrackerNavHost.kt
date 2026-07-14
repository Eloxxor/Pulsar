package com.esusic.recoverytracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class BottomTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab("today", "Today", Icons.Filled.Home),
    BottomTab("trends", "Trends", Icons.Filled.ShowChart),
    BottomTab("sleep", "Sleep", Icons.Filled.Bedtime)
)

@Composable
fun RecoveryTrackerNavHost(startDestination: String) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = startDestination, modifier = androidx.compose.ui.Modifier.padding(padding)) {
            composable("onboarding") {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.onboarding.OnboardingViewModel> {
                    com.esusic.recoverytracker.ui.onboarding.OnboardingViewModel(
                        com.esusic.recoverytracker.ServiceLocator.userPreferencesRepository,
                        com.esusic.recoverytracker.ServiceLocator.healthDataSource
                    )
                }
                com.esusic.recoverytracker.ui.onboarding.OnboardingScreen(viewModel) {
                    navController.navigate("today") { popUpTo("onboarding") { inclusive = true } }
                }
            }
            composable("today") {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.today.TodayViewModel> {
                    com.esusic.recoverytracker.ui.today.TodayViewModel(
                        com.esusic.recoverytracker.ServiceLocator.repository,
                        com.esusic.recoverytracker.ServiceLocator.healthDataSource
                    )
                }
                com.esusic.recoverytracker.ui.today.TodayScreen(viewModel) { navController.navigate("settings") }
            }
            composable("trends") {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.trends.TrendsViewModel> {
                    com.esusic.recoverytracker.ui.trends.TrendsViewModel(com.esusic.recoverytracker.ServiceLocator.repository)
                }
                com.esusic.recoverytracker.ui.trends.TrendsScreen(viewModel)
            }
            composable("sleep") { Text("Sleep placeholder") }
            composable("settings") { Text("Settings placeholder") }
            composable("detail/{metricType}") { Text("Detail placeholder") }
        }
    }
}
