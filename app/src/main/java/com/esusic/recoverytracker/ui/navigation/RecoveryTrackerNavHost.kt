package com.esusic.recoverytracker.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
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
import com.esusic.recoverytracker.ui.journal.JournalScreen
import com.esusic.recoverytracker.ui.journal.JournalViewModel
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

private val ROOT_ENTER: EnterTransition = fadeIn(animationSpec = tween(durationMillis = 220))
private val ROOT_EXIT: ExitTransition = fadeOut(animationSpec = tween(durationMillis = 180))
private val DETAIL_ENTER: EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 260)) +
        slideInHorizontally(animationSpec = tween(durationMillis = 260)) { width -> width / 8 }
private val DETAIL_EXIT: ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 200)) +
        slideOutHorizontally(animationSpec = tween(durationMillis = 200)) { width -> -width / 12 }
private val DETAIL_POP_ENTER: EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 260)) +
        slideInHorizontally(animationSpec = tween(durationMillis = 260)) { width -> -width / 12 }
private val DETAIL_POP_EXIT: ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 200)) +
        slideOutHorizontally(animationSpec = tween(durationMillis = 200)) { width -> width / 8 }

@Composable
fun RecoveryTrackerNavHost(startDestination: String) {
    val navController = rememberNavController()
    val selectedDayStore = remember { SelectedDayStore() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = shouldShowBottomBar(currentDestination?.route)
    val haptics = LocalHapticFeedback.current

    Scaffold(
        containerColor = AppColors.Canvas,
        bottomBar = {
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        color = AppColors.NavBar,
                        border = BorderStroke(1.dp, AppColors.GridLine),
                        shadowElevation = 16.dp
                    ) {
                        NavigationBar(
                            modifier = Modifier.height(72.dp),
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            RootDestination.entries.forEach { destination ->
                                val selected = currentDestination?.hierarchy
                                    ?.any { it.route == destination.route } == true
                                val accent = rootAccent(destination)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        navController.navigateRoot(destination)
                                    },
                                    icon = {
                                        Icon(
                                            rootIcon(destination),
                                            contentDescription = stringResource(destination.label)
                                        )
                                    },
                                    label = { Text(stringResource(destination.label)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = accent.copy(alpha = 0.18f),
                                        selectedIconColor = accent,
                                        selectedTextColor = accent,
                                        unselectedIconColor = AppColors.TextMuted,
                                        unselectedTextColor = AppColors.TextMuted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        // The bottom inset is handed to each screen as extra scroll content padding instead of
        // being clipped off here, so root content glides under the translucent pill nav bar.
        val bottomInset = innerPadding.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(
                PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                )
            ),
            enterTransition = { ROOT_ENTER },
            exitTransition = { ROOT_EXIT },
            popEnterTransition = { ROOT_ENTER },
            popExitTransition = { ROOT_EXIT }
        ) {
            composable(ONBOARDING_ROUTE) {
                val onboardingViewModel = viewModel<OnboardingViewModel> {
                    OnboardingViewModel(
                        ServiceLocator.userPreferencesRepository,
                        ServiceLocator.healthDataSource
                    )
                }
                Box(modifier = Modifier.padding(bottom = bottomInset)) {
                    OnboardingScreen(onboardingViewModel) {
                        navController.navigate(RootDestination.HOME.route) {
                            popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                        }
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
                    onOpenAge = { navController.navigate(AGE_ROUTE) },
                    contentBottomPadding = bottomInset
                )
            }

            composable(RootDestination.SLEEP.route) {
                val sleepViewModel = viewModel<SleepViewModel> {
                    SleepViewModel(ServiceLocator.repository, selectedDayStore)
                }
                SleepScreen(sleepViewModel, contentBottomPadding = bottomInset)
            }

            composable(RootDestination.ACTIVITY.route) {
                val activityViewModel = viewModel<ActivityViewModel> {
                    ActivityViewModel(
                        ServiceLocator.repository,
                        ServiceLocator.healthDataSource,
                        selectedDayStore = selectedDayStore
                    )
                }
                ActivityScreen(activityViewModel, contentBottomPadding = bottomInset)
            }

            composable(RootDestination.JOURNAL.route) {
                val journalViewModel = viewModel<JournalViewModel> {
                    JournalViewModel(
                        ServiceLocator.journalEntryDao,
                        ServiceLocator.dailyScoreDao,
                        selectedDayStore = selectedDayStore
                    )
                }
                JournalScreen(journalViewModel, contentBottomPadding = bottomInset)
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
                    onOpenAge = { navController.navigate(AGE_ROUTE) },
                    contentBottomPadding = bottomInset
                )
            }

            composable(
                RECOVERY_ROUTE,
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
                enterTransition = { DETAIL_ENTER },
                exitTransition = { DETAIL_EXIT },
                popEnterTransition = { DETAIL_POP_ENTER },
                popExitTransition = { DETAIL_POP_EXIT }
            ) { entry ->
                val date = LocalDate.parse(requireNotNull(entry.arguments?.getString("date")))
                val recoveryViewModel = viewModel<RecoveryDetailViewModel>(key = date.toString()) {
                    RecoveryDetailViewModel(ServiceLocator.repository, date)
                }
                Box(modifier = Modifier.padding(bottom = bottomInset)) {
                    RecoveryDetailScreen(
                        viewModel = recoveryViewModel,
                        onBack = navController::popBackStack
                    )
                }
            }

            composable(
                AGE_ROUTE,
                enterTransition = { DETAIL_ENTER },
                exitTransition = { DETAIL_EXIT },
                popEnterTransition = { DETAIL_POP_ENTER },
                popExitTransition = { DETAIL_POP_EXIT }
            ) {
                val ageViewModel = viewModel<VitalAgeViewModel> {
                    VitalAgeViewModel(
                        ServiceLocator.repository,
                        ServiceLocator.userPreferencesRepository
                    )
                }
                Box(modifier = Modifier.padding(bottom = bottomInset)) {
                    VitalAgeScreen(ageViewModel, onBack = navController::popBackStack)
                }
            }

            composable(
                DETAIL_ROUTE,
                arguments = listOf(navArgument("metricType") { type = NavType.StringType }),
                enterTransition = { DETAIL_ENTER },
                exitTransition = { DETAIL_EXIT },
                popEnterTransition = { DETAIL_POP_ENTER },
                popExitTransition = { DETAIL_POP_EXIT }
            ) { entry ->
                val metricType = MetricType.valueOf(
                    entry.arguments?.getString("metricType") ?: MetricType.HEART_RATE.name
                )
                val detailViewModel = viewModel<MetricDetailViewModel>(key = metricType.name) {
                    MetricDetailViewModel(ServiceLocator.repository, metricType)
                }
                Box(modifier = Modifier.padding(bottom = bottomInset)) {
                    MetricDetailScreen(
                        viewModel = detailViewModel,
                        metricType = metricType,
                        onBack = navController::popBackStack
                    )
                }
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
    RootDestination.JOURNAL -> Icons.Filled.EditNote
    RootDestination.ACCOUNT -> Icons.Filled.Person
}

private fun rootAccent(destination: RootDestination) = when (destination) {
    RootDestination.HOME -> AppColors.Recovery
    RootDestination.SLEEP -> AppColors.SleepViolet
    RootDestination.ACTIVITY -> AppColors.ActivityCyan
    RootDestination.JOURNAL -> AppColors.Journal
    RootDestination.ACCOUNT -> AppColors.Hrv
}
