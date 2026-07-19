package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.theme.AppColors
import kotlin.math.cos
import kotlin.math.sin

// Semicircular 0-3 stress gauge: green -> amber -> red arc with a needle at the value.
@Composable
fun StressGauge(value: Double?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        val diameter = size.width - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val arcSize = Size(diameter, diameter)
        val segments = listOf(
            AppColors.Recovery to (180f to 60f),
            AppColors.Warning to (240f to 60f),
            AppColors.Danger to (300f to 60f)
        )
        segments.forEach { (color, angles) ->
            drawArc(
                color = color.copy(alpha = 0.9f),
                startAngle = angles.first,
                sweepAngle = angles.second,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        value?.let {
            val fraction = (it / 3.0).coerceIn(0.0, 1.0)
            val angleRad = Math.toRadians(180.0 + 180.0 * fraction)
            val center = Offset(size.width / 2f, stroke / 2f + diameter / 2f)
            val needleLength = diameter / 2f - stroke
            val tip = Offset(
                center.x + (needleLength * cos(angleRad)).toFloat(),
                center.y + (needleLength * sin(angleRad)).toFloat()
            )
            drawLine(
                color = AppColors.TextPrimary,
                start = center,
                end = tip,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = AppColors.TextPrimary, radius = 4.dp.toPx(), center = center)
        }
    }
}
