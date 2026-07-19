package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val lanes = listOf(
    SleepStageType.AWAKE,
    SleepStageType.REM,
    SleepStageType.LIGHT,
    SleepStageType.DEEP
)

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// OpenFit-style stage timeline: one horizontal lane per stage (Awake/REM/Light/Deep),
// each stage period drawn as a rounded block at its time position.
@Composable
fun SleepHypnogram(
    stagePeriods: List<SleepStagePeriod>,
    sessionStart: Instant,
    sessionEnd: Instant,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(44.dp).height(112.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                lanes.forEach { lane ->
                    Text(stageLabel(lane), fontSize = 10.sp, color = stageColor(lane))
                }
            }
            Canvas(modifier = Modifier.weight(1f).height(112.dp)) {
                val totalMillis = Duration.between(sessionStart, sessionEnd).toMillis().toFloat()
                if (totalMillis <= 0f) return@Canvas
                val laneHeight = size.height / lanes.size
                val blockHeight = laneHeight * 0.55f
                stagePeriods.forEach { period ->
                    val laneIndex = lanes.indexOf(period.stage)
                    if (laneIndex < 0) return@forEach // UNKNOWN periods are skipped
                    val x = Duration.between(sessionStart, period.start).toMillis() / totalMillis * size.width
                    val w = (Duration.between(period.start, period.end).toMillis() / totalMillis * size.width)
                        .coerceAtLeast(3f)
                    val y = laneIndex * laneHeight + (laneHeight - blockHeight) / 2f
                    drawRoundRect(
                        color = stageColor(period.stage),
                        topLeft = Offset(x, y),
                        size = Size(w, blockHeight),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(timeFormatter.withZone(zone).format(sessionStart), fontSize = 10.sp, color = AppColors.TextMuted)
            Text(timeFormatter.withZone(zone).format(sessionEnd), fontSize = 10.sp, color = AppColors.TextMuted)
        }
    }
}
