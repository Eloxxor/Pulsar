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
import com.esusic.recoverytracker.ui.common.Eyebrow
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SleepScreen(viewModel: SleepViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val zone = ZoneId.systemDefault()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            val sessionStart = uiState.sessionStart
            val sessionEnd = uiState.sessionEnd
            if (sessionStart != null && sessionEnd != null) {
                val start = timeFormatter.withZone(zone).format(sessionStart)
                val end = timeFormatter.withZone(zone).format(sessionEnd)
                Eyebrow("Last night · $start – $end")
            } else {
                Eyebrow("Last night")
            }
            Text("Sleep", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        val total = uiState.totalMinutes
        if (total == null) {
            SectionCard {
                Text("No sleep data synced yet.", color = AppColors.TextSecondary)
                Text(
                    "Pull to refresh on the Today tab after your watch has synced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted
                )
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${total / 60}h ${total % 60}m",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            DeltaChip(uiState.durationDelta, "min")
        }

        val sessionStart = uiState.sessionStart
        val sessionEnd = uiState.sessionEnd
        if (sessionStart != null && sessionEnd != null && uiState.heartRateBuckets.isNotEmpty()) {
            SectionCard(title = "Heart rate") {
                SleepHeartRateChart(
                    buckets = uiState.heartRateBuckets,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd
                )
            }
        }

        SectionCard(title = "Stages") {
            if (sessionStart != null && sessionEnd != null && uiState.stagePeriods.isNotEmpty()) {
                SleepStageTimelineBar(
                    stagePeriods = uiState.stagePeriods,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd
                )
            }
            StageLegendRow(SleepStageType.DEEP, uiState.deepMinutes, uiState.deepPercent)
            StageLegendRow(SleepStageType.REM, uiState.remMinutes, uiState.remPercent)
            StageLegendRow(SleepStageType.LIGHT, uiState.lightMinutes, uiState.lightPercent)
        }
    }
}

@Composable
private fun StageLegendRow(stage: SleepStageType, minutes: Long?, percent: Float) {
    val color = stageColor(stage)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(stageLabel(stage), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                minutes?.let { "${it / 60}h ${it % 60}m · ${(percent * 100).roundToInt()}%" } ?: "--",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(percent.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp)).background(color)
            )
        }
    }
}
