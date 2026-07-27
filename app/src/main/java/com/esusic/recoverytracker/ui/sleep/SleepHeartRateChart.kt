package com.esusic.recoverytracker.ui.sleep

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SleepHeartRateChart(
    buckets: List<HeartRateBucket>,
    sessionStart: Instant,
    sessionEnd: Instant,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(buckets) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = AppColors.TextMuted)
    val zone = ZoneId.systemDefault()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val selectedBucket = selectedIndex?.let { buckets.getOrNull(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (selectedBucket != null) {
                SummaryStat(
                    stringResource(
                        R.string.sleep_heart_rate_bpm,
                        selectedBucket.avgBpm.roundToInt()
                    ),
                    timeFormatter.withZone(zone).format(selectedBucket.time)
                )
            } else {
                val low = buckets.minOfOrNull { it.avgBpm }?.roundToInt()
                val avg = buckets.takeIf { it.isNotEmpty() }?.map { it.avgBpm }?.average()?.roundToInt()
                SummaryStat(
                    low?.let { stringResource(R.string.sleep_heart_rate_bpm, it) } ?: "--",
                    stringResource(R.string.sleep_heart_rate_low)
                )
                SummaryStat(
                    avg?.let { stringResource(R.string.sleep_heart_rate_bpm, it) } ?: "--",
                    stringResource(R.string.sleep_heart_rate_avg)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(buckets) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            selectedIndex = nearestBucketIndex(buckets, change.position.x, size.width.toFloat())
                        }
                    )
                }
                .pointerInput(buckets) {
                    detectTapGestures(
                        onTap = { offset ->
                            selectedIndex = nearestBucketIndex(buckets, offset.x, size.width.toFloat())
                        }
                    )
                }
        ) {
            if (buckets.size < 2) return@Canvas

            val minBpm = buckets.minOf { it.avgBpm }
            val maxBpm = buckets.maxOf { it.avgBpm }
            val bpmRange = (maxBpm - minBpm).takeIf { it > 0.0 } ?: 1.0
            val totalDurationMillis = Duration.between(sessionStart, sessionEnd).toMillis().toFloat()
            if (totalDurationMillis <= 0f) return@Canvas
            val verticalInset = 8.dp.toPx()
            val plotHeight = size.height - 2 * verticalInset

            fun xFor(time: Instant): Float =
                (Duration.between(sessionStart, time).toMillis() / totalDurationMillis) * size.width

            fun yFor(bpm: Double): Float =
                verticalInset + (1f - ((bpm - minBpm) / bpmRange).toFloat()) * plotHeight

            listOf(maxBpm, minBpm + bpmRange / 2.0, minBpm).forEach { gridBpm ->
                val y = yFor(gridBpm)
                drawLine(color = AppColors.GridLine, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                val measured = textMeasurer.measure(AnnotatedString(gridBpm.roundToInt().toString()), labelStyle)
                drawText(measured, topLeft = Offset(0f, y - measured.size.height - 2f))
            }

            val linePath = Path()
            buckets.forEachIndexed { index, bucket ->
                val offset = Offset(xFor(bucket.time), yFor(bucket.avgBpm))
                if (index == 0) linePath.moveTo(offset.x, offset.y) else linePath.lineTo(offset.x, offset.y)
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(xFor(buckets.last().time), size.height)
                lineTo(xFor(buckets.first().time), size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(AppColors.HeartRate.copy(alpha = 0.20f), Color.Transparent),
                    startY = 0f,
                    endY = size.height
                )
            )
            drawPath(path = linePath, color = AppColors.HeartRate, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            selectedIndex?.let { index ->
                val bucket = buckets.getOrNull(index) ?: return@let
                val center = Offset(xFor(bucket.time), yFor(bucket.avgBpm))
                drawLine(
                    color = AppColors.TextMuted,
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(color = AppColors.HeartRate, radius = 5.dp.toPx(), center = center)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeFormatter.withZone(zone).format(sessionStart), fontSize = 10.sp, color = AppColors.TextMuted)
            Text(timeFormatter.withZone(zone).format(sessionEnd), fontSize = 10.sp, color = AppColors.TextMuted)
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        Text(label.uppercase(), fontSize = 10.sp, color = AppColors.TextMuted, letterSpacing = 1.sp)
    }
}

private fun nearestBucketIndex(buckets: List<HeartRateBucket>, x: Float, width: Float): Int {
    if (buckets.isEmpty()) return 0
    val fraction = (x / width).coerceIn(0f, 1f)
    val index = (fraction * (buckets.size - 1)).roundToInt()
    return index.coerceIn(0, buckets.size - 1)
}
