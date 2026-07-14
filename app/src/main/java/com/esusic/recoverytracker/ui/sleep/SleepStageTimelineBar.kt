package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.model.SleepStageType
import java.time.Duration
import java.time.Instant

@Composable
fun SleepStageTimelineBar(
    stagePeriods: List<SleepStagePeriod>,
    sessionStart: Instant,
    sessionEnd: Instant,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(24.dp)) {
        val totalDurationMillis = Duration.between(sessionStart, sessionEnd).toMillis().toFloat()
        if (totalDurationMillis <= 0f) return@Canvas

        stagePeriods.forEach { period ->
            val startX = (Duration.between(sessionStart, period.start).toMillis() / totalDurationMillis) * size.width
            val endX = (Duration.between(sessionStart, period.end).toMillis() / totalDurationMillis) * size.width
            drawRect(
                color = stageColor(period.stage),
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, size.height)
            )
        }
    }
}

private fun stageColor(stage: SleepStageType): Color = when (stage) {
    SleepStageType.DEEP -> Color(0xFF3B3BB0)
    SleepStageType.REM -> Color(0xFF9B6BFF)
    SleepStageType.LIGHT -> Color(0xFF6BAFFF)
    SleepStageType.AWAKE -> Color(0xFFE8A33D)
    SleepStageType.UNKNOWN -> Color(0xFF444444)
}
