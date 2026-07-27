package com.esusic.recoverytracker.ui.theme

import androidx.compose.ui.graphics.Color

// Single source for the recovery colour bands. Screens and the home-screen widget must not
// disagree about where "good" turns into "warning".
fun recoveryAccent(score: Int?): Color = when {
    score == null -> AppColors.TextMuted
    score >= 60 -> AppColors.Recovery
    score >= 40 -> AppColors.Warning
    else -> AppColors.Danger
}
