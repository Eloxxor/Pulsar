package com.esusic.recoverytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RecoveryGreen = Color(0xFF17C97E)
private val StrainBlue = Color(0xFF2E6BFF)
private val WarningAmber = Color(0xFFE8A33D)
private val DangerRed = Color(0xFFE8503D)

private val DarkColors = darkColorScheme(primary = RecoveryGreen, secondary = StrainBlue, error = DangerRed)
private val LightColors = lightColorScheme(primary = RecoveryGreen, secondary = StrainBlue, error = DangerRed)

val ColorScheme.warning get() = WarningAmber

@Composable
fun RecoveryTrackerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
