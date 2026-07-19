package com.esusic.recoverytracker.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.common.ChartPoint
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.MetricLineChart
import com.esusic.recoverytracker.ui.common.MetricTile
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.presentation.effortStatus
import com.esusic.recoverytracker.ui.presentation.todayRecommendation
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.today.CircularGauge
import java.time.format.DateTimeFormatter
import java.util.Locale

private val activityDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)

@Composable
fun ActivityScreen(viewModel: ActivityViewModel) {
    val state by viewModel.uiState.collectAsState()
    val effort = effortStatus(state.strainValue)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        RootPageHeader(
            title = "Activity",
            subtitle = state.selectedDate.format(activityDateFormatter),
            previousEnabled = state.canGoBack,
            nextEnabled = state.canGoForward,
            onPrevious = viewModel::goToPreviousDay,
            onNext = viewModel::goToNextDay
        )

        SectionCard(accent = AppColors.ActivityCyan, glowAlpha = 0.20f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Effort", style = MaterialTheme.typography.titleMedium)
                StatusPill(effort.label, AppColors.ActivityCyan)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                CircularGauge(
                    value = state.strainValue?.toFloat() ?: 0f,
                    maxValue = 21f,
                    color = AppColors.ActivityCyan,
                    strokeWidth = 10.dp,
                    modifier = Modifier.size(138.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            state.strainValue?.let { "%.1f".format(it) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppColors.ActivityCyan
                        )
                        state.strainValue?.let {
                            Text(
                                "/21",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }
                    }
                }
                Text(
                    effort.message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        SectionCard(title = "Training load") {
            Text(
                todayRecommendation(
                    recovery = state.recoveryValue,
                    strain = state.strainValue,
                    sleep = null,
                    calibrating = false
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Your load combines heart-rate zones and daily movement.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                label = "Steps",
                value = state.steps?.toString() ?: "—",
                accent = AppColors.Strain,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Zone minutes",
                value = state.zoneMinutes?.toString() ?: "—",
                unit = "min",
                accent = AppColors.Strain,
                modifier = Modifier.weight(1f)
            )
        }
        MetricTile(
            label = "Heart rate",
            value = state.latestHeartRateBpm?.toString() ?: "—",
            unit = "bpm",
            accent = AppColors.HeartRate,
            glowAlpha = 0.10f,
            modifier = Modifier.fillMaxWidth()
        )

        SectionCard(title = "Activity timeline") {
            ActivityTimeline(state.hourlySteps)
            Text(
                if (state.hourlySteps.any { it > 0 }) {
                    "Steps recorded across the selected day."
                } else {
                    "Hourly detail is not available for this day."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }

        SectionCard(title = "This week") {
            val points = state.strain.points.takeLast(7)
            val average = points.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
            Text(
                average?.let { "Average effort %.1f".format(it) } ?: "Building your activity history",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Daily load is most useful when read alongside recovery and sleep.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }

        Text("Trends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityRange.entries.forEach { range ->
                FilterChip(
                    selected = state.range == range,
                    onClick = { viewModel.selectRange(range) },
                    label = { Text(rangeLabel(range)) }
                )
            }
        }
        ActivityTrendCard("Recovery", state.recovery, AppColors.Recovery, "%")
        ActivityTrendCard("Effort", state.strain, AppColors.Strain, "", decimals = 1)
        ActivityTrendCard("HRV", state.hrv, AppColors.Hrv, "ms")
        ActivityTrendCard("Resting HR", state.rhr, AppColors.Rhr, "bpm")
    }
}

@Composable
private fun ActivityTrendCard(
    title: String,
    trend: MetricTrend,
    color: Color,
    unit: String,
    decimals: Int = 0
) {
    var selected by remember(trend.points) { mutableStateOf<ChartPoint?>(null) }
    val formatter: (Float) -> String = { value -> "%.${decimals}f".format(value) }
    SectionCard(title = title) {
        if (trend.points.size < 2) {
            Text(
                "At least two recorded days are needed for this trend.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shown = selected
                Text(
                    shown?.value?.let(formatter) ?: trend.latest?.let(formatter) ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
                if (unit.isNotEmpty()) {
                    Text(unit, color = AppColors.TextSecondary)
                }
                if (shown == null) {
                    DeltaChip(trend.delta, unit, decimals = decimals, suffix = "vs avg")
                } else {
                    Text(
                        shown.date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)),
                        color = AppColors.TextSecondary
                    )
                }
            }
            MetricLineChart(
                points = trend.points,
                lineColor = color,
                valueFormatter = formatter,
                onSelect = { selected = it }
            )
        }
    }
}

private fun rangeLabel(range: ActivityRange): String = when (range) {
    ActivityRange.WEEK -> "7 days"
    ActivityRange.MONTH -> "30 days"
    ActivityRange.QUARTER -> "90 days"
}
