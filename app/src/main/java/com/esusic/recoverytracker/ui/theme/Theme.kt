package com.esusic.recoverytracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RecoveryGreen = Color(0xFF17C97E)
private val StrainBlue = Color(0xFF2E6BFF)
private val WarningAmber = Color(0xFFE8A33D)
private val DangerRed = Color(0xFFE8503D)
private val NearBlackBackground = Color(0xFF0A0A0A)
private val CardSurface = Color(0xFF161616)
private val CardSurfaceVariant = Color(0xFF1F1F1F)

private val DarkColors = darkColorScheme(
    primary = RecoveryGreen,
    secondary = StrainBlue,
    error = DangerRed,
    background = NearBlackBackground,
    surface = CardSurface,
    surfaceVariant = CardSurfaceVariant
)

val ColorScheme.warning get() = WarningAmber

@Composable
fun RecoveryTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
