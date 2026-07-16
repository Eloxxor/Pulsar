package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.model.SleepStagePeriod
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.Duration
import java.time.Instant

@Composable
fun SleepStageTimelineBar(
    stagePeriods: List<SleepStagePeriod>,
    sessionStart: Instant,
    sessionEnd: Instant,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp))) {
        val totalDurationMillis = Duration.between(sessionStart, sessionEnd).toMillis().toFloat()
        if (totalDurationMillis <= 0f) return@Canvas

        drawRect(color = AppColors.StageUnknown, size = size)

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
