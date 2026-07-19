package com.esusic.recoverytracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
    BottomTab("today", "Overview", Icons.Filled.Home),
    BottomTab("trends", "Trends", Icons.Filled.ShowChart),
    BottomTab("settings", "Settings", Icons.Filled.Settings)
)

@Composable
fun RecoveryTrackerNavHost(startDestination: String) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination
            NavigationBar(containerColor = com.esusic.recoverytracker.ui.theme.AppColors.NavBar) {
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
                        label = { Text(tab.label) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            indicatorColor = androidx.compose.ui.graphics.Color(0xFF1E1E26),
                            selectedIconColor = com.esusic.recoverytracker.ui.theme.AppColors.TextPrimary,
                            selectedTextColor = com.esusic.recoverytracker.ui.theme.AppColors.TextPrimary,
                            unselectedIconColor = com.esusic.recoverytracker.ui.theme.AppColors.TextMuted,
                            unselectedTextColor = com.esusic.recoverytracker.ui.theme.AppColors.TextMuted
                        )
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
                        com.esusic.recoverytracker.ServiceLocator.healthDataSource,
                        com.esusic.recoverytracker.ServiceLocator.userPreferencesRepository
                    )
                }
                com.esusic.recoverytracker.ui.today.TodayScreen(
                    viewModel,
                    onMetricClick = { metricType -> navController.navigate("detail/${metricType.name}") },
                    onOpenSleep = { navController.navigate("sleep") },
                    onOpenAge = { navController.navigate("age") }
                )
            }
            composable("trends") {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.trends.TrendsViewModel> {
                    com.esusic.recoverytracker.ui.trends.TrendsViewModel(com.esusic.recoverytracker.ServiceLocator.repository)
                }
                com.esusic.recoverytracker.ui.trends.TrendsScreen(viewModel)
            }
            composable("sleep") {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.sleep.SleepViewModel> {
                    com.esusic.recoverytracker.ui.sleep.SleepViewModel(com.esusic.recoverytracker.ServiceLocator.repository)
                }
                com.esusic.recoverytracker.ui.sleep.SleepScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable("age") {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.vitalage.VitalAgeViewModel> {
                    com.esusic.recoverytracker.ui.vitalage.VitalAgeViewModel(
                        com.esusic.recoverytracker.ServiceLocator.repository,
                        com.esusic.recoverytracker.ServiceLocator.userPreferencesRepository
                    )
                }
                com.esusic.recoverytracker.ui.vitalage.VitalAgeScreen(viewModel, onBack = { navController.popBackStack() })
            }
            composable("settings") {
                com.esusic.recoverytracker.ui.settings.SettingsScreen()
            }
            composable(
                "detail/{metricType}",
                arguments = listOf(androidx.navigation.navArgument("metricType") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val metricType = com.esusic.recoverytracker.ui.detail.MetricType.valueOf(
                    backStackEntry.arguments?.getString("metricType") ?: com.esusic.recoverytracker.ui.detail.MetricType.HEART_RATE.name
                )
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<com.esusic.recoverytracker.ui.detail.MetricDetailViewModel>(
                    key = metricType.name
                ) {
                    com.esusic.recoverytracker.ui.detail.MetricDetailViewModel(com.esusic.recoverytracker.ServiceLocator.repository, metricType)
                }
                com.esusic.recoverytracker.ui.detail.MetricDetailScreen(viewModel, metricType)
            }
        }
    }
}
