package com.esusic.recoverytracker.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.ui.theme.AppColors

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = AppColors.TextMuted) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium
    )
}
