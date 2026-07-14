package com.esusic.recoverytracker.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier.fillMaxWidth().height(120.dp),
    lineColor: Color = Color.Unspecified
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minValue = values.min()
        val maxValue = values.max()
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val color = if (lineColor == Color.Unspecified) Color(0xFF17C97E) else lineColor

        val points = values.mapIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minValue) / range) * size.height
            Offset(x, y)
        }

        for (i in 0 until points.size - 1) {
            drawLine(color = color, start = points[i], end = points[i + 1], strokeWidth = 4f)
        }
    }
}
