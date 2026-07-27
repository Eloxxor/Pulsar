package com.esusic.recoverytracker.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ui.common.DetailTopBar
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.common.rememberDateFormatter
import com.esusic.recoverytracker.ui.presentation.recoveryStatus
import com.esusic.recoverytracker.ui.presentation.todayRecommendation
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.theme.recoveryAccent

@Composable
fun RecoveryDetailScreen(
    viewModel: RecoveryDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val status = recoveryStatus(state.recoveryValue, state.isCalibrating)
    val accent = recoveryAccent(state.recoveryValue)
    val dateFormatter = rememberDateFormatter(R.string.date_pattern_weekday_month_day)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        DetailTopBar(title = stringResource(R.string.recovery_detail_title), onBack = onBack)
        Text(
            state.date.format(dateFormatter),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        SectionCard {
            StatusPill(stringResource(status.label), accent)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = when {
                        state.isLoading -> "…"
                        state.isCalibrating -> "…"
                        state.recoveryValue != null -> state.recoveryValue.toString()
                        else -> "—"
                    },
                    fontSize = 72.sp,
                    lineHeight = 74.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
                if (!state.isLoading && !state.isCalibrating && state.recoveryValue != null) {
                    Text(
                        "%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 9.dp)
                    )
                }
            }
            Text(
                stringResource(status.message),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            stringResource(R.string.recovery_detail_why),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        ComponentScoreCard(
            label = R.string.recovery_component_hrv,
            score = state.hrvSubScore,
            accent = AppColors.Hrv,
            explanation = R.string.recovery_component_hrv_explanation
        )
        ComponentScoreCard(
            label = R.string.recovery_component_rhr,
            score = state.rhrSubScore,
            accent = AppColors.Rhr,
            explanation = R.string.recovery_component_rhr_explanation
        )
        ComponentScoreCard(
            label = R.string.recovery_component_sleep,
            score = state.sleepSubScore,
            accent = AppColors.SleepViolet,
            explanation = R.string.recovery_component_sleep_explanation
        )

        SectionCard(title = stringResource(R.string.recovery_recommendation_title)) {
            Text(
                stringResource(
                    todayRecommendation(
                        recovery = state.recoveryValue,
                        strain = null,
                        sleep = null,
                        calibrating = state.isCalibrating
                    )
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.recovery_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ComponentScoreCard(
    @StringRes label: Int,
    score: Double?,
    accent: Color,
    @StringRes explanation: Int
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                score?.let { stringResource(R.string.recovery_component_score, it) } ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
        }
        LinearProgressIndicator(
            progress = { ((score ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = accent,
            trackColor = AppColors.GridLine
        )
        Text(
            stringResource(explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
    }
}

