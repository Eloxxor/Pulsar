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
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.R
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
    val resources = LocalContext.current.resources
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(44.dp).height(112.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                lanes.forEach { lane ->
                    Text(
                        stringResource(stageLabel(lane)),
                        fontSize = 10.sp,
                        color = stageColor(lane)
                    )
                }
            }
            val spokenTimeline = remember(stagePeriods, sessionStart, sessionEnd, resources) {
                hypnogramDescription(resources, stagePeriods, sessionStart, sessionEnd, zone)
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(112.dp)
                    .semantics { contentDescription = spokenTimeline }
            ) {
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

internal fun hypnogramDescription(
    resources: Resources,
    stagePeriods: List<SleepStagePeriod>,
    sessionStart: Instant,
    sessionEnd: Instant,
    zone: ZoneId
): String {
    val formatter = timeFormatter.withZone(zone)
    val window = resources.getString(
        R.string.sleep_hypnogram_window,
        formatter.format(sessionStart),
        formatter.format(sessionEnd)
    )
    if (stagePeriods.isEmpty()) {
        return resources.getString(R.string.sleep_hypnogram_no_detail, window)
    }
    val perStage = lanes.mapNotNull { lane ->
        val minutes = stagePeriods
            .filter { it.stage == lane }
            .sumOf { Duration.between(it.start, it.end).toMinutes() }
        if (minutes <= 0L) {
            null
        } else {
            resources.getString(
                R.string.sleep_hypnogram_stage,
                resources.getString(stageLabel(lane)),
                minutes / 60,
                minutes % 60
            )
        }
    }
    return resources.getString(
        R.string.sleep_hypnogram_summary,
        window,
        perStage.joinToString(", ")
    )
}
