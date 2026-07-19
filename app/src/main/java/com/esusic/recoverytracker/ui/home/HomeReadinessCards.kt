package com.esusic.recoverytracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.presentation.effortStatus
import com.esusic.recoverytracker.ui.presentation.recoveryStatus
import com.esusic.recoverytracker.ui.presentation.sleepStatus
import com.esusic.recoverytracker.ui.presentation.todayRecommendation
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.today.TodayUiState

@Composable
fun ReadinessCluster(
    state: TodayUiState,
    onRecoveryClick: () -> Unit,
    onEffortClick: () -> Unit,
    onSleepClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(360.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RecoveryHeroCard(
            state = state,
            onClick = onRecoveryClick,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EffortCard(
                state = state,
                onClick = onEffortClick,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            SleepCard(
                state = state,
                onClick = onSleepClick,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RecoveryHeroCard(
    state: TodayUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = recoveryStatus(state.recoveryValue, state.isCalibrating)
    Card(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "Recovery card" },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.GridLine)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Recovery",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextSecondary
                )
                StatusPill(status.label, recoveryColor(state.recoveryValue))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = when {
                        state.isCalibrating -> "…"
                        state.recoveryValue != null -> state.recoveryValue.toString()
                        else -> "—"
                    },
                    fontSize = 62.sp,
                    lineHeight = 62.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = recoveryColor(state.recoveryValue)
                )
                if (!state.isCalibrating && state.recoveryValue != null) {
                    Text(
                        "%",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = todayRecommendation(
                        recovery = state.recoveryValue,
                        strain = state.strainValue,
                        sleep = state.sleepPerformance,
                        calibrating = state.isCalibrating
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun EffortCard(
    state: TodayUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = effortStatus(state.strainValue)
    CompactReadinessCard(
        label = "Effort",
        value = state.strainValue?.let { "%.1f".format(it) } ?: "—",
        unit = if (state.strainValue != null) "/ 21" else null,
        status = status.label,
        supporting = status.message,
        accent = AppColors.Strain,
        semanticsLabel = "Effort card",
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun SleepCard(
    state: TodayUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = sleepStatus(state.sleepPerformance)
    CompactReadinessCard(
        label = "Sleep",
        value = state.sleepDurationMinutes?.let(::formatMinutes) ?: "—",
        unit = state.sleepPerformance?.let { "$it%" },
        status = status.label,
        supporting = status.message,
        accent = AppColors.SleepViolet,
        semanticsLabel = "Sleep card",
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun CompactReadinessCard(
    label: String,
    value: String,
    unit: String?,
    status: String,
    supporting: String,
    accent: androidx.compose.ui.graphics.Color,
    semanticsLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = semanticsLabel },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = BorderStroke(1.dp, AppColors.GridLine)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                StatusPill(status, accent)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
                unit?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                maxLines = 2
            )
        }
    }
}

private fun recoveryColor(score: Int?) = when {
    score == null -> AppColors.TextMuted
    score >= 60 -> AppColors.Recovery
    score >= 40 -> AppColors.Warning
    else -> AppColors.Danger
}

internal fun formatMinutes(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"
