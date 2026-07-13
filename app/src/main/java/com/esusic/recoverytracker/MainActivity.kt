package com.esusic.recoverytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.esusic.recoverytracker.ui.navigation.RecoveryTrackerNavHost
import com.esusic.recoverytracker.ui.theme.RecoveryTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RecoveryTrackerTheme {
                RecoveryTrackerNavHost(startDestination = "onboarding")
            }
        }
    }
}
