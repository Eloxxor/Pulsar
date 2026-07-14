package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SleepHeartRateChart(
    buckets: List<HeartRateBucket>,
    sessionStart: Instant,
    sessionEnd: Instant,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(buckets) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(buckets) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            selectedIndex = nearestBucketIndex(buckets, change.position.x, size.width.toFloat())
                        },
                        onDragEnd = { selectedIndex = null }
                    )
                }
                .pointerInput(buckets) {
                    detectTapGestures(
                        onPress = { offset ->
                            selectedIndex = nearestBucketIndex(buckets, offset.x, size.width.toFloat())
                            tryAwaitRelease()
                            selectedIndex = null
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

            fun xFor(time: Instant): Float =
                (Duration.between(sessionStart, time).toMillis() / totalDurationMillis) * size.width

            fun yFor(bpm: Double): Float =
                size.height - ((bpm - minBpm) / bpmRange).toFloat() * size.height

            val points = buckets.map { Offset(xFor(it.time), yFor(it.avgBpm)) }
            for (i in 0 until points.size - 1) {
                drawLine(color = Color(0xFFE8503D), start = points[i], end = points[i + 1], strokeWidth = 3f)
            }

            selectedIndex?.let { index ->
                val point = points.getOrNull(index) ?: return@let
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, size.height),
                    strokeWidth = 2f
                )
                drawCircle(color = Color(0xFFE8503D), radius = 6f, center = point)
            }
        }

        selectedIndex?.let { index ->
            val bucket = buckets.getOrNull(index)
            if (bucket != null) {
                val timeLabel = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(bucket.time)
                Text("${bucket.avgBpm.roundToInt()} bpm at $timeLabel", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun nearestBucketIndex(buckets: List<HeartRateBucket>, x: Float, width: Float): Int {
    if (buckets.isEmpty()) return 0
    val fraction = (x / width).coerceIn(0f, 1f)
    val index = (fraction * (buckets.size - 1)).roundToInt()
    return index.coerceIn(0, buckets.size - 1)
}
