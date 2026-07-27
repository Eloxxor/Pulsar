package com.esusic.recoverytracker.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private const val MAX_DATE_LABELS = 4

@Composable
fun MetricLineChart(
    points: List<ChartPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { "%.0f".format(it) },
    baseline: Float? = null,
    metricName: String = "",
    onSelect: (ChartPoint?) -> Unit = {}
) {
    val dateFormatter = rememberDateFormatter(R.string.date_pattern_month_day)
    val summary = chartSeriesSummary(points, dateFormatter, valueFormatter)
    val description = if (summary == null) {
        stringResource(R.string.trend_chart_empty_description, metricName)
    } else {
        stringResource(
            R.string.trend_chart_description,
            metricName,
            summary.dayCount,
            summary.firstDate,
            summary.lastDate,
            summary.latest,
            summary.lowest,
            summary.highest
        )
    }
    val baselineLabel = baseline
        ?.let { stringResource(R.string.trend_chart_baseline_marker, valueFormatter(it)) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = AppColors.TextMuted)
    val calloutStyle = TextStyle(
        fontSize = 11.sp,
        color = AppColors.TextPrimary,
        fontWeight = FontWeight.SemiBold
    )
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    fun select(index: Int?) {
        selectedIndex = index
        onSelect(index?.let { points[it] })
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    val tapRadius = 24.dp.toPx()
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        val nearest = nearestPointIndex(points, offset.x, width)
                            ?.takeIf { abs(pointX(points, it, width) - offset.x) <= tapRadius }
                        select(if (nearest == selectedIndex) null else nearest)
                    }
                }
                .pointerInput(points) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            select(nearestPointIndex(points, offset.x, size.width.toFloat()))
                        },
                        onDrag = { change, _ ->
                            select(nearestPointIndex(points, change.position.x, size.width.toFloat()))
                        },
                        onDragEnd = { select(null) },
                        onDragCancel = { select(null) }
                    )
                }
        ) {
            if (points.size < 2) return@Canvas

            val minValue = minOf(points.minOf { it.value }, baseline ?: Float.MAX_VALUE)
            val maxValue = maxOf(points.maxOf { it.value }, baseline ?: -Float.MAX_VALUE)
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

            baseline?.let { baselineValue ->
                val y = yFor(baselineValue)
                drawLine(
                    color = AppColors.TextMuted,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                )
                val measured = textMeasurer.measure(
                    AnnotatedString(baselineLabel.orEmpty()),
                    labelStyle
                )
                drawText(
                    measured,
                    topLeft = Offset(size.width - measured.size.width, y + 2f)
                )
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

                val callout = textMeasurer.measure(
                    AnnotatedString(
                        "${valueFormatter(point.value)} · ${point.date.format(dateFormatter)}"
                    ),
                    calloutStyle
                )
                val paddingX = 8.dp.toPx()
                val paddingY = 5.dp.toPx()
                val boxSize = Size(
                    callout.size.width + paddingX * 2,
                    callout.size.height + paddingY * 2
                )
                val left = (center.x - boxSize.width / 2f)
                    .coerceIn(0f, (size.width - boxSize.width).coerceAtLeast(0f))
                val top = (center.y - boxSize.height - 10.dp.toPx())
                    .coerceAtLeast(0f)
                    .coerceAtMost((size.height - boxSize.height).coerceAtLeast(0f))
                val corner = CornerRadius(10.dp.toPx())
                drawRoundRect(
                    color = AppColors.SurfaceElevated,
                    topLeft = Offset(left, top),
                    size = boxSize,
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = lineColor.copy(alpha = 0.45f),
                    topLeft = Offset(left, top),
                    size = boxSize,
                    cornerRadius = corner,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawText(callout, topLeft = Offset(left + paddingX, top + paddingY))
            }
        }

        if (points.size >= 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dateLabelIndices(points.size).forEach { index ->
                    Text(
                        points[index].date.format(dateFormatter),
                        fontSize = 10.sp,
                        color = AppColors.TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

internal fun dateLabelIndices(size: Int): List<Int> {
    if (size < 2) return List(size) { it }
    val count = minOf(MAX_DATE_LABELS, size)
    return List(count) { position -> position * (size - 1) / (count - 1) }
}

internal data class ChartSeriesSummary(
    val dayCount: Int,
    val firstDate: String,
    val lastDate: String,
    val latest: String,
    val lowest: String,
    val highest: String
)

internal fun chartSeriesSummary(
    points: List<ChartPoint>,
    dateFormatter: DateTimeFormatter,
    valueFormatter: (Float) -> String
): ChartSeriesSummary? {
    if (points.size < 2) return null
    return ChartSeriesSummary(
        dayCount = points.size,
        firstDate = points.first().date.format(dateFormatter),
        lastDate = points.last().date.format(dateFormatter),
        latest = valueFormatter(points.last().value),
        lowest = valueFormatter(points.minOf { it.value }),
        highest = valueFormatter(points.maxOf { it.value })
    )
}

private fun pointX(points: List<ChartPoint>, index: Int, width: Float): Float {
    val firstDay = points.first().date.toEpochDay()
    val daySpan = (points.last().date.toEpochDay() - firstDay).coerceAtLeast(1).toFloat()
    return ((points[index].date.toEpochDay() - firstDay) / daySpan) * width
}

private fun nearestPointIndex(points: List<ChartPoint>, x: Float, width: Float): Int? {
    if (points.size < 2 || width <= 0f) return null
    return points.indices.minByOrNull { index -> abs(pointX(points, index, width) - x) }
}
