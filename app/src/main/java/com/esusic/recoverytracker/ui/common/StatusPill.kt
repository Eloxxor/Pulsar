package com.esusic.recoverytracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.28f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}
