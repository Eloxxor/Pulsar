package com.esusic.recoverytracker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

@Composable
fun MetricLineChart(
    points: List<ChartPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { "%.0f".format(it) },
    onSelect: (ChartPoint?) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = AppColors.TextMuted)
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(points) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            val index = nearestPointIndex(points, change.position.x, size.width.toFloat())
                            selectedIndex = index
                            onSelect(index?.let { points[it] })
                        },
                        onDragEnd = {
                            selectedIndex = null
                            onSelect(null)
                        }
                    )
                }
        ) {
            if (points.size < 2) return@Canvas

            val minValue = points.minOf { it.value }
            val maxValue = points.maxOf { it.value }
            val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
            val verticalInset = 8.dp.toPx()
            val plotHeight = size.height - 2 * verticalInset
            val firstDay = points.first().date.toEpochDay()
            val daySpan = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1).toFloat()

            fun xFor(point: ChartPoint): Float =
                ((point.date.toEpochDay() - firstDay) / daySpan) * size.width

            fun yFor(value: Float): Float =
                verticalInset + (1f - (value - minValue) / range) * plotHeight

            listOf(maxValue, minValue + range / 2f, minValue).forEach { gridValue ->
                val y = yFor(gridValue)
                drawLine(color = AppColors.GridLine, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                val measured = textMeasurer.measure(AnnotatedString(valueFormatter(gridValue)), labelStyle)
                drawText(measured, topLeft = Offset(0f, y - measured.size.height - 2f))
            }

            val linePath = Path()
            points.forEachIndexed { index, point ->
                val offset = Offset(xFor(point), yFor(point.value))
                if (index == 0) linePath.moveTo(offset.x, offset.y) else linePath.lineTo(offset.x, offset.y)
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(xFor(points.last()), size.height)
                lineTo(xFor(points.first()), size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                    startY = 0f,
                    endY = size.height
                )
            )
            drawPath(path = linePath, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            val latest = Offset(xFor(points.last()), yFor(points.last().value))
            drawCircle(color = lineColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = latest)
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = latest)

            selectedIndex?.let { index ->
                val point = points.getOrNull(index) ?: return@let
                val center = Offset(xFor(point), yFor(point.value))
                drawLine(
                    color = AppColors.TextMuted,
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = center)
            }
        }

        if (points.size >= 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().date.format(dateFormatter), fontSize = 10.sp, color = AppColors.TextMuted)
                Text(points.last().date.format(dateFormatter), fontSize = 10.sp, color = AppColors.TextMuted)
            }
        }
    }
}

private fun nearestPointIndex(points: List<ChartPoint>, x: Float, width: Float): Int? {
    if (points.size < 2 || width <= 0f) return null
    val firstDay = points.first().date.toEpochDay()
    val daySpan = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1).toFloat()
    return points.indices.minByOrNull { index ->
        abs(((points[index].date.toEpochDay() - firstDay) / daySpan) * width - x)
    }
}
