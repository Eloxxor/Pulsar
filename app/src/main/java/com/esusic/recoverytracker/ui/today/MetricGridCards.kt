package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.LocalTime
import java.util.Locale

// The four tiles of the Overview metric grid, OpenFit style: header row with icon and
// label, one big value, a mini chart or caption underneath.

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.heightIn(min = 148.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AppColors.GridLine),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                Spacer(Modifier.weight(1f))
                if (showChevron) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun BigValue(value: String, unit: String? = null, color: Color = AppColors.TextPrimary) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        unit?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
    }
}

@Composable
fun HeartRateCard(
    bpm: Long?,
    spark: List<Float>,
    restingHr: Long?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    MetricCard(Icons.Filled.Favorite, "Heart rate", modifier, showChevron = true, onClick = onClick) {
        BigValue(bpm?.toString() ?: "--", "bpm")
        if (spark.size >= 2) {
            Sparkline(spark, AppColors.HeartRate, Modifier.fillMaxWidth().height(36.dp))
        }
        Text(
            restingHr?.let { "resting $it bpm" } ?: "no resting hr yet",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted
        )
    }
}

@Composable
fun StepsCard(
    steps: Int?,
    hourlySteps: List<Int>,
    stepsLast6h: Int?,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    MetricCard(Icons.AutoMirrored.Filled.DirectionsWalk, "Steps", modifier, showChevron = true, onClick = onClick) {
        BigValue(steps?.let { String.format(Locale.US, "%,d", it) } ?: "--")
        // Six-hour window: ending now for today, the evening hours for past days.
        val endHour = if (isToday) LocalTime.now().hour else 23
        val window = ((endHour - 5).coerceAtLeast(0)..endHour).map { hourlySteps.getOrElse(it) { 0 } }
        HourBars(window, Modifier.fillMaxWidth().height(36.dp))
        Text(
            stepsLast6h?.let { String.format(Locale.US, "%,d in last 6h", it) } ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted
        )
    }
}

@Composable
fun ZoneMinutesCard(minutes: Int?, yesterday: Int?, modifier: Modifier = Modifier) {
    MetricCard(Icons.Filled.Timelapse, "Zone minutes", modifier) {
        BigValue(minutes?.toString() ?: "--", "min")
        val caption = if (minutes != null && yesterday != null) {
            val diff = minutes - yesterday
            String.format(Locale.US, "%+d vs yesterday", diff)
        } else {
            "time at 50%+ heart-rate reserve"
        }
        Text(caption, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
    }
}

@Composable
fun StressCard(stress: Double?, restingHr: Long?, nowBpm: Long?, modifier: Modifier = Modifier) {
    MetricCard(Icons.Filled.Speed, "Stress", modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StressGauge(stress, Modifier.size(width = 72.dp, height = 40.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stress?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    stressLabel(stress)?.let { (label, color) ->
                        Text(
                            "· $label",
                            style = MaterialTheme.typography.bodySmall,
                            color = color,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
        Text(
            if (restingHr != null && nowBpm != null) "rest $restingHr bpm · now $nowBpm bpm" else "estimated from HR vs baseline",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted
        )
    }
}

fun stressLabel(stress: Double?): Pair<String, Color>? = when {
    stress == null -> null
    stress < 1.0 -> "Low" to AppColors.Recovery
    stress < 2.0 -> "Moderate" to AppColors.Warning
    else -> "High" to AppColors.Danger
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun HourBars(values: List<Int>, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        values.forEach { v ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(((v.toFloat() / max) * 36f).coerceAtLeast(2f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (v > 0) AppColors.Strain.copy(alpha = 0.85f) else AppColors.GridLine)
            )
        }
    }
}
