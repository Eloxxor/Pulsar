package com.esusic.recoverytracker.ui.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.theme.AppColors

@Composable
fun ActivityTimeline(hourlySteps: List<Int>, modifier: Modifier = Modifier) {
    val normalized = List(24) { index -> hourlySteps.getOrElse(index) { 0 }.coerceAtLeast(0) }
    val total = normalized.sum()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
            .semantics { contentDescription = "Hourly activity, $total steps" }
    ) {
        val maximum = normalized.maxOrNull()?.coerceAtLeast(1) ?: 1
        val slotWidth = size.width / normalized.size
        val barWidth = slotWidth * 0.58f
        normalized.forEachIndexed { index, steps ->
            val height = (steps.toFloat() / maximum) * size.height
            val left = index * slotWidth + (slotWidth - barWidth) / 2f
            drawRoundRect(
                color = if (steps > 0) AppColors.Strain else AppColors.GridLine,
                topLeft = Offset(left, size.height - height.coerceAtLeast(3.dp.toPx())),
                size = Size(barWidth, height.coerceAtLeast(3.dp.toPx())),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
