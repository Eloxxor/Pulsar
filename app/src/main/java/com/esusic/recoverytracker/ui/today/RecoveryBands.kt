package com.esusic.recoverytracker.ui.today

import androidx.compose.ui.graphics.Color
import com.esusic.recoverytracker.ui.theme.AppColors

fun recoveryColor(score: Int?): Color = when {
    score == null -> AppColors.Recovery
    score >= 67 -> AppColors.Recovery
    score >= 34 -> AppColors.Warning
    else -> AppColors.Danger
}

fun recoveryStatusLabel(score: Int?): String? = when {
    score == null -> null
    score >= 67 -> "Recovered"
    score >= 34 -> "Adequate"
    else -> "Run down"
}
