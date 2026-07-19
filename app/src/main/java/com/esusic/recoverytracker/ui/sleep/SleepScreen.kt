package com.esusic.recoverytracker.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.presentation.sleepStatus
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val sleepTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SleepScreen(viewModel: SleepViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        RootPageHeader(title = "Sleep", subtitle = "Last night")

        val total = state.totalMinutes
        if (total == null) {
            SectionCard {
                Text("No sleep recorded", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Refresh Home after your watch has synced with Health Connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
            return@Column
        }

        SleepHero(state, total)

        val sessionStart = state.sessionStart
        val sessionEnd = state.sessionEnd
        if (sessionStart != null && sessionEnd != null && state.stagePeriods.isNotEmpty()) {
            val inBed = state.inBedMinutes ?: total
            val restorative = if (inBed > 0) {
                (((state.deepMinutes ?: 0L) + (state.remMinutes ?: 0L)).toFloat() / inBed * 100)
                    .roundToInt()
            } else {
                0
            }
            SectionCard(title = "Sleep timeline") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    StatusPill("$restorative% restorative", AppColors.SleepViolet)
                }
                SleepHypnogram(
                    stagePeriods = state.stagePeriods,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd
                )
            }
        }

        SectionCard(title = "Sleep stages") {
            val inBed = state.inBedMinutes ?: total
            StageRow(SleepStageType.AWAKE, state.awakeMinutes, inBed)
            StageRow(SleepStageType.REM, state.remMinutes, inBed)
            StageRow(SleepStageType.LIGHT, state.lightMinutes, inBed)
            StageRow(SleepStageType.DEEP, state.deepMinutes, inBed)
            if (state.stagePeriods.isEmpty()) {
                Text(
                    "Your source recorded duration without detailed sleep stages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
        }

        state.needMinutes?.let { need ->
            SectionCard(title = "Hours versus need") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DurationStat("Asleep", total, Alignment.Start)
                    DurationStat("Need", need.roundToInt().toLong(), Alignment.End)
                }
                state.sleepPerformance?.let {
                    StatusPill("$it% of sleep performance", AppColors.SleepViolet)
                }
                state.wakeEvents?.let { events ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wake events", color = AppColors.TextSecondary)
                        Text(events.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (sessionStart != null && sessionEnd != null && state.heartRateBuckets.isNotEmpty()) {
            SectionCard(title = "Sleeping heart rate") {
                SleepHeartRateChart(
                    buckets = state.heartRateBuckets,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd
                )
            }
        }

        if (state.weeklyNights.isNotEmpty()) {
            SectionCard(title = "This week") {
                state.weeklyAverageMinutes?.let {
                    Text(
                        "Average ${formatSleepMinutes(it.roundToInt().toLong())}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                SleepWeeklyChart(state.weeklyNights)
                state.weeklyInsight?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepHero(state: SleepUiState, total: Long) {
    val status = sleepStatus(state.sleepPerformance)
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(status.label, AppColors.SleepViolet)
            state.durationDelta?.let { DeltaChip(it, "min") }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                state.sleepPerformance?.toString() ?: "—",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold,
                color = AppColors.SleepViolet
            )
            state.sleepPerformance?.let {
                Text(
                    "%",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }
        Text(
            formatSleepMinutes(total),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        if (state.sessionStart != null && state.sessionEnd != null) {
            val formatter = sleepTimeFormatter.withZone(ZoneId.systemDefault())
            Text(
                "${formatter.format(state.sessionStart)} – ${formatter.format(state.sessionEnd)}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
        Text(status.message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DurationStat(label: String, minutes: Long, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Text(
            formatSleepMinutes(minutes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun StageRow(stage: SleepStageType, minutes: Long?, inBedMinutes: Long) {
    val color = stageColor(stage)
    val fraction = if (minutes != null && inBedMinutes > 0) {
        (minutes.toFloat() / inBedMinutes).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(stageLabel(stage), style = MaterialTheme.typography.bodyMedium)
                if (minutes != null) {
                    Text(
                        "${(fraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                minutes?.let(::formatSleepMinutes) ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(3.dp)).background(AppColors.GridLine)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp)).background(color)
            )
        }
    }
}

private fun formatSleepMinutes(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"
