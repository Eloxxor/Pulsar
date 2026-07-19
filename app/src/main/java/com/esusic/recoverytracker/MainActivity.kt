package com.esusic.recoverytracker

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.esusic.recoverytracker.ui.navigation.RecoveryTrackerNavHost
import com.esusic.recoverytracker.ui.theme.RecoveryTrackerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // Resolve the start destination synchronously (before first composition) so a returning,
        // already-onboarded user goes straight to Home with no onboarding flash and without
        // re-triggering an immediate sync on every cold launch. Onboarding is considered complete
        // once a birth year has been saved and Health Connect permissions are currently granted -
        // the same condition OnboardingScreen's own completion LaunchedEffect already uses.
        val startDestination = runBlocking {
            val hasBirthYear = ServiceLocator.userPreferencesRepository.birthYear.first() != null
            val onboardingComplete = hasBirthYear && ServiceLocator.healthDataSource.hasAllPermissions()
            if (onboardingComplete) "home" else "onboarding"
        }

        setContent {
            RecoveryTrackerTheme {
                RecoveryTrackerNavHost(startDestination = startDestination)
            }
        }
    }
}
