package com.esusic.recoverytracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NearBlackBackground = Color(0xFF0A0A0A)
private val CardSurface = Color(0xFF161616)
private val CardSurfaceVariant = Color(0xFF1F1F1F)

private val DarkColors = darkColorScheme(
    primary = AppColors.Recovery,
    secondary = AppColors.Strain,
    tertiary = AppColors.SleepViolet,
    error = AppColors.Danger,
    background = NearBlackBackground,
    onBackground = AppColors.TextPrimary,
    surface = CardSurface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = CardSurfaceVariant,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.GridLine
)

val ColorScheme.warning get() = AppColors.Warning

@Composable
fun RecoveryTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
