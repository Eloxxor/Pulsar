package com.esusic.recoverytracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.MetricTile
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.detail.MetricType
import com.esusic.recoverytracker.ui.presentation.todayRecommendation
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.today.TodayUiState
import com.esusic.recoverytracker.ui.today.TodayViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val homeDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: TodayViewModel,
    onOpenRecovery: (LocalDate) -> Unit,
    onOpenSleep: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMetric: (MetricType) -> Unit,
    onOpenAge: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val pullState = rememberPullRefreshState(state.isRefreshing, viewModel::refresh)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .pullRefresh(pullState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            RootPageHeader(
                title = if (state.isToday) "Today" else state.selectedDate.dayOfWeek
                    .name.lowercase().replaceFirstChar(Char::uppercase),
                subtitle = state.selectedDate.format(homeDateFormatter),
                previousEnabled = state.canGoBack,
                nextEnabled = state.canGoForward,
                onPrevious = viewModel::goToPreviousDay,
                onNext = viewModel::goToNextDay
            )

            if (!state.hasAllPermissions) {
                SectionCard {
                    Text(
                        "Health Connect access is off",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.Danger
                    )
                    Text(
                        "Stored history is still available. Reconnect from Account to refresh it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }

            ReadinessCluster(
                state = state,
                onRecoveryClick = { onOpenRecovery(state.selectedDate) },
                onEffortClick = onOpenActivity,
                onSleepClick = onOpenSleep
            )

            SectionHeading("Core metrics")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    label = "HRV",
                    value = state.avgHrvMillis?.let { "%.0f".format(it) } ?: "—",
                    unit = "ms",
                    accent = AppColors.Hrv,
                    supportingText = baselineText(state.hrvDelta, higherIsBetter = true),
                    delta = state.hrvDelta,
                    deltaUnit = "ms",
                    onClick = { onOpenMetric(MetricType.HRV) },
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Resting HR",
                    value = state.restingHeartRate?.toString() ?: "—",
                    unit = "bpm",
                    accent = AppColors.Rhr,
                    supportingText = baselineText(state.rhrDelta, higherIsBetter = false),
                    delta = state.rhrDelta,
                    deltaUnit = "bpm",
                    onClick = { onOpenMetric(MetricType.HEART_RATE) },
                    modifier = Modifier.weight(1f)
                )
            }

            SectionCard(title = "Today's recommendation") {
                Text(
                    todayRecommendation(
                        recovery = state.recoveryValue,
                        strain = state.strainValue,
                        sleep = state.sleepPerformance,
                        calibrating = state.isCalibrating
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight
                )
                Text(
                    "Use this as training context, not medical advice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }

            SectionCard(
                title = "Vital Age",
                modifier = Modifier
                    .semantics { contentDescription = "Vital Age card" }
                    .clickable(onClick = onOpenAge)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.vitalAge?.let { "%.1f".format(it) } ?: "Learning",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.VitalAge
                    )
                    if (state.vitalAge != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "years",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                Text(
                    vitalAgeContext(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }

            SectionCard(title = "More vitals") {
                VitalLine("Respiratory rate", state.avgRespiratoryRate?.let { "%.1f /min".format(it) } ?: "—", state.respRateDelta, "")
                VitalLine("SpO₂", state.avgOxygenSaturationPercent?.let { "%.0f%%".format(it) } ?: "—", state.spo2Delta, "pp")
                VitalLine("Skin temperature", state.avgSkinTemperatureDeltaCelsius?.let { "%+.1f°C".format(it) } ?: "—", state.skinTempDelta, "°C")
                VitalLine("Steps", state.steps?.toString() ?: "—", null, "")
                VitalLine("Zone minutes", state.zoneMinutes?.let { "$it min" } ?: "—", null, "")
            }

            state.lastSyncedAtEpochMillis?.let { epochMillis ->
                Text(
                    "Updated ${DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(epochMillis))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        PullRefreshIndicator(
            refreshing = state.isRefreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = AppColors.Surface,
            contentColor = AppColors.Recovery
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun VitalLine(label: String, value: String, delta: MetricDelta?, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            DeltaChip(delta, unit)
        }
    }
}

private fun vitalAgeContext(state: TodayUiState): String {
    val vital = state.vitalAge ?: return "A few days of consistent data are needed."
    val chronological = state.chronologicalAge ?: return "Based on your recent recovery signals."
    val difference = vital - chronological
    return when {
        difference <= -0.5 -> "%.1f years below your chronological age.".format(-difference)
        difference >= 0.5 -> "%.1f years above your chronological age.".format(difference)
        else -> "Close to your chronological age."
    }
}

private fun baselineText(delta: MetricDelta?, higherIsBetter: Boolean): String = when {
    delta == null -> "Building your baseline"
    delta.diff == 0.0 -> "At baseline"
    (delta.diff > 0) == higherIsBetter -> "Favorable versus baseline"
    else -> "Outside your usual baseline"
}
