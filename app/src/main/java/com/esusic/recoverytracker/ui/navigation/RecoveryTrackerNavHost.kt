package com.esusic.recoverytracker.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.esusic.recoverytracker.ServiceLocator
import com.esusic.recoverytracker.ui.account.AccountScreen
import com.esusic.recoverytracker.ui.account.AccountViewModel
import com.esusic.recoverytracker.ui.activity.ActivityScreen
import com.esusic.recoverytracker.ui.activity.ActivityViewModel
import com.esusic.recoverytracker.ui.detail.MetricDetailScreen
import com.esusic.recoverytracker.ui.detail.MetricDetailViewModel
import com.esusic.recoverytracker.ui.detail.MetricType
import com.esusic.recoverytracker.ui.date.SelectedDayStore
import com.esusic.recoverytracker.ui.home.HomeScreen
import com.esusic.recoverytracker.ui.onboarding.OnboardingScreen
import com.esusic.recoverytracker.ui.onboarding.OnboardingViewModel
import com.esusic.recoverytracker.ui.recovery.RecoveryDetailScreen
import com.esusic.recoverytracker.ui.recovery.RecoveryDetailViewModel
import com.esusic.recoverytracker.ui.sleep.SleepScreen
import com.esusic.recoverytracker.ui.sleep.SleepViewModel
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.today.TodayViewModel
import com.esusic.recoverytracker.ui.vitalage.VitalAgeScreen
import com.esusic.recoverytracker.ui.vitalage.VitalAgeViewModel
import java.time.LocalDate

private const val ONBOARDING_ROUTE = "onboarding"
private const val AGE_ROUTE = "age"
private const val RECOVERY_ROUTE = "recovery/{date}"
private const val DETAIL_ROUTE = "detail/{metricType}"

@Composable
fun RecoveryTrackerNavHost(startDestination: String) {
    val navController = rememberNavController()
    val selectedDayStore = remember { SelectedDayStore() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = shouldShowBottomBar(currentDestination?.route)

    Scaffold(
        containerColor = AppColors.Canvas,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(color = AppColors.GridLine)
                    NavigationBar(
                        containerColor = AppColors.NavBar,
                        tonalElevation = 0.dp
                    ) {
                        RootDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy
                                    ?.any { it.route == destination.route } == true,
                                onClick = { navController.navigateRoot(destination) },
                                icon = {
                                    Icon(
                                        rootIcon(destination),
                                        contentDescription = destination.label
                                    )
                                },
                                label = { Text(destination.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = AppColors.Recovery.copy(alpha = 0.12f),
                                    selectedIconColor = AppColors.TextPrimary,
                                    selectedTextColor = AppColors.TextPrimary,
                                    unselectedIconColor = AppColors.TextMuted,
                                    unselectedTextColor = AppColors.TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ONBOARDING_ROUTE) {
                val onboardingViewModel = viewModel<OnboardingViewModel> {
                    OnboardingViewModel(
                        ServiceLocator.userPreferencesRepository,
                        ServiceLocator.healthDataSource
                    )
                }
                OnboardingScreen(onboardingViewModel) {
                    navController.navigate(RootDestination.HOME.route) {
                        popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                    }
                }
            }

            composable(RootDestination.HOME.route) {
                val todayViewModel = viewModel<TodayViewModel> {
                    TodayViewModel(
                        ServiceLocator.repository,
                        ServiceLocator.healthDataSource,
                        ServiceLocator.userPreferencesRepository,
                        selectedDayStore = selectedDayStore
                    )
                }
                HomeScreen(
                    viewModel = todayViewModel,
                    onOpenRecovery = { date -> navController.navigate("recovery/$date") },
                    onOpenSleep = { navController.navigateRoot(RootDestination.SLEEP) },
                    onOpenActivity = { navController.navigateRoot(RootDestination.ACTIVITY) },
                    onOpenMetric = { metric -> navController.navigate("detail/${metric.name}") },
                    onOpenAge = { navController.navigate(AGE_ROUTE) }
                )
            }

            composable(RootDestination.SLEEP.route) {
                val sleepViewModel = viewModel<SleepViewModel> {
                    SleepViewModel(ServiceLocator.repository)
                }
                SleepScreen(sleepViewModel)
            }

            composable(RootDestination.ACTIVITY.route) {
                val activityViewModel = viewModel<ActivityViewModel> {
                    ActivityViewModel(
                        ServiceLocator.repository,
                        ServiceLocator.healthDataSource,
                        selectedDayStore = selectedDayStore
                    )
                }
                ActivityScreen(activityViewModel)
            }

            composable(RootDestination.ACCOUNT.route) {
                val accountViewModel = viewModel<AccountViewModel> {
                    AccountViewModel(
                        ServiceLocator.userPreferencesRepository,
                        ServiceLocator.healthDataSource
                    )
                }
                AccountScreen(
                    viewModel = accountViewModel,
                    onOpenAge = { navController.navigate(AGE_ROUTE) }
                )
            }

            composable(
                RECOVERY_ROUTE,
                arguments = listOf(navArgument("date") { type = NavType.StringType })
            ) { entry ->
                val date = LocalDate.parse(requireNotNull(entry.arguments?.getString("date")))
                val recoveryViewModel = viewModel<RecoveryDetailViewModel>(key = date.toString()) {
                    RecoveryDetailViewModel(ServiceLocator.repository, date)
                }
                RecoveryDetailScreen(
                    viewModel = recoveryViewModel,
                    onBack = navController::popBackStack
                )
            }

            composable(AGE_ROUTE) {
                val ageViewModel = viewModel<VitalAgeViewModel> {
                    VitalAgeViewModel(
                        ServiceLocator.repository,
                        ServiceLocator.userPreferencesRepository
                    )
                }
                VitalAgeScreen(ageViewModel, onBack = navController::popBackStack)
            }

            composable(
                DETAIL_ROUTE,
                arguments = listOf(navArgument("metricType") { type = NavType.StringType })
            ) { entry ->
                val metricType = MetricType.valueOf(
                    entry.arguments?.getString("metricType") ?: MetricType.HEART_RATE.name
                )
                val detailViewModel = viewModel<MetricDetailViewModel>(key = metricType.name) {
                    MetricDetailViewModel(ServiceLocator.repository, metricType)
                }
                MetricDetailScreen(
                    viewModel = detailViewModel,
                    metricType = metricType,
                    onBack = navController::popBackStack
                )
            }
        }
    }
}

private fun NavHostController.navigateRoot(destination: RootDestination) {
    navigate(destination.route) {
        popUpTo(RootDestination.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun rootIcon(destination: RootDestination): ImageVector = when (destination) {
    RootDestination.HOME -> Icons.Filled.Home
    RootDestination.SLEEP -> Icons.Filled.Bedtime
    RootDestination.ACTIVITY -> Icons.AutoMirrored.Filled.DirectionsRun
    RootDestination.ACCOUNT -> Icons.Filled.Person
}
