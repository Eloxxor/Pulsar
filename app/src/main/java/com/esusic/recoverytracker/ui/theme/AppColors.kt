package com.esusic.recoverytracker.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val Canvas = Color(0xFF05060B)
    val Surface = Color(0xFF11131B)
    val SurfaceElevated = Color(0xFF151927)
    val TextPrimary = Color(0xFFF5F7FB)
    val TextSecondary = Color(0xFFA7AEC4)
    val TextMuted = Color(0xFF747C96)
    val GridLine = Color(0xFF282D3C)
    val NavBar = Color(0xF2111420)

    val Recovery = Color(0xFF00D89F)

    // Effort/strain is always cyan; Movement (steps, hourly bars) is the warm counterpart.
    // Keep these two apart - the same metric must not change colour between screens.
    val ActivityCyan = Color(0xFF13C8E8)
    val Movement = Color(0xFFFF6B35)
    val SleepViolet = Color(0xFF8A5CFF)
    val Hrv = Color(0xFF3478FF)
    val Rhr = Color(0xFFF13B91)
    val HeartRate = Color(0xFFFF477E)
    val VitalAge = Color(0xFFF7B84B)
    val Journal = Color(0xFFB8C24C)
    val Warning = Color(0xFFFFB43A)
    val Danger = Color(0xFFFF5A68)

    val StageDeep = Color(0xFF8A5CFF)
    val StageRem = Color(0xFF13C8E8)
    val StageLight = Color(0xFF3478FF)
    val StageAwake = Color(0xFFF5F7FB)
    val StageUnknown = Color(0xFF747C96)
}
