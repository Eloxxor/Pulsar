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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.ui.common.DetailTopBar
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.presentation.recoveryStatus
import com.esusic.recoverytracker.ui.presentation.todayRecommendation
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecoveryDetailScreen(
    viewModel: RecoveryDetailViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val status = recoveryStatus(state.recoveryValue, state.isCalibrating)
    val accent = recoveryColor(state.recoveryValue)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        DetailTopBar(title = "Recovery", onBack = onBack)
        Text(
            state.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        SectionCard {
            StatusPill(status.label, accent)
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
                status.message,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            "Why this score?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        ComponentScoreCard(
            label = "HRV",
            score = state.hrvSubScore,
            accent = AppColors.Hrv,
            explanation = "How your heart-rate variability compares with your personal baseline."
        )
        ComponentScoreCard(
            label = "Resting heart rate",
            score = state.rhrSubScore,
            accent = AppColors.Rhr,
            explanation = "A lower-than-usual resting rate generally supports recovery."
        )
        ComponentScoreCard(
            label = "Sleep",
            score = state.sleepSubScore,
            accent = AppColors.SleepViolet,
            explanation = "Duration and restorative stages compared with your recent nights."
        )

        SectionCard(title = "Today's recommendation") {
            Text(
                todayRecommendation(
                    recovery = state.recoveryValue,
                    strain = null,
                    sleep = null,
                    calibrating = state.isCalibrating
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Recovery is a training signal, not a medical diagnosis.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ComponentScoreCard(
    label: String,
    score: Double?,
    accent: Color,
    explanation: String
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                score?.let { "%.0f / 100".format(it) } ?: "—",
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
            explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
    }
}

private fun recoveryColor(score: Int?) = when {
    score == null -> AppColors.TextMuted
    score >= 60 -> AppColors.Recovery
    score >= 40 -> AppColors.Warning
    else -> AppColors.Danger
}
