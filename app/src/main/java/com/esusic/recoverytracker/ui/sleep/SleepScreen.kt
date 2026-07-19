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
import com.esusic.recoverytracker.ui.common.DetailTopBar
import com.esusic.recoverytracker.ui.common.Eyebrow
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.theme.AppColors
import kotlin.math.roundToInt

private fun formatMinutes(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"

@Composable
fun SleepScreen(viewModel: SleepViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetailTopBar("Sleep", onBack)

        val total = uiState.totalMinutes
        if (total == null) {
            SectionCard {
                Text("No sleep data synced yet.", color = AppColors.TextSecondary)
                Text(
                    "Pull to refresh on the Overview tab after your watch has synced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Eyebrow("Last night's sleep")
            uiState.inBedMinutes?.let {
                Text(
                    "${formatMinutes(it)} in bed · vs 30 nights",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                formatMinutes(total),
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

        SectionCard {
            val inBed = uiState.inBedMinutes ?: total
            StageRow(SleepStageType.AWAKE, uiState.awakeMinutes, inBed)
            StageRow(SleepStageType.LIGHT, uiState.lightMinutes, inBed)
            StageRow(SleepStageType.DEEP, uiState.deepMinutes, inBed)
            StageRow(SleepStageType.REM, uiState.remMinutes, inBed)
        }

        if (sessionStart != null && sessionEnd != null && uiState.stagePeriods.isNotEmpty()) {
            val inBed = uiState.inBedMinutes ?: total
            val restorativePercent = if (inBed > 0) {
                (((uiState.deepMinutes ?: 0) + (uiState.remMinutes ?: 0)).toFloat() / inBed * 100).roundToInt()
            } else {
                0
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Eyebrow("Stage timeline")
                Text(
                    "$restorativePercent% restorative",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted
                )
            }
            SectionCard {
                SleepHypnogram(
                    stagePeriods = uiState.stagePeriods,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd
                )
            }
        }

        uiState.needMinutes?.let { need ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Eyebrow("Hours vs need")
                uiState.sleepPerformance?.let {
                    Text(
                        "$it%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.SleepViolet
                    )
                }
            }
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Asleep", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        Text(
                            formatMinutes(total),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                        Text("Need", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        Text(
                            formatMinutes(need.roundToInt().toLong()),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        uiState.wakeEvents?.let { events ->
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Wake events", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    Text(
                        "$events",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(stageLabel(stage), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${(fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                minutes?.let { formatMinutes(it) } ?: "--",
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
                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp)).background(color)
            )
        }
    }
}
