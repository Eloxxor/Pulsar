package com.esusic.recoverytracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ui.common.SkeletonBox
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.common.animatedValue
import com.esusic.recoverytracker.ui.common.neonSurface
import com.esusic.recoverytracker.ui.presentation.effortStatus
import com.esusic.recoverytracker.ui.presentation.recoveryStatus
import com.esusic.recoverytracker.ui.presentation.sleepStatus
import com.esusic.recoverytracker.ui.presentation.todayRecommendation
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.theme.recoveryAccent
import com.esusic.recoverytracker.ui.today.TodayUiState
import com.esusic.recoverytracker.ui.today.CircularGauge
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class ReadinessLayout { ASYMMETRIC, STACKED }

internal fun readinessLayoutFor(maxWidthDp: Int, fontScale: Float): ReadinessLayout =
    if (maxWidthDp < 350 || fontScale > 1.15f) {
        ReadinessLayout.STACKED
    } else {
        ReadinessLayout.ASYMMETRIC
    }

@Composable
fun ReadinessCluster(
    state: TodayUiState,
    onRecoveryClick: () -> Unit,
    onEffortClick: () -> Unit,
    onSleepClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        if (readinessLayoutFor(maxWidth.value.toInt(), fontScale) == ReadinessLayout.STACKED) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecoveryHeroCard(
                    state = state,
                    onClick = onRecoveryClick,
                    modifier = Modifier.fillMaxWidth()
                )
                EffortCard(
                    state = state,
                    onClick = onEffortClick,
                    modifier = Modifier.fillMaxWidth()
                )
                SleepCard(
                    state = state,
                    onClick = onSleepClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Height follows the tallest card's own content. A fixed height silently clipped the
            // recovery recommendation once the copy ran past two lines.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
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
    }
}

@Composable
fun ReadinessClusterSkeleton(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        if (readinessLayoutFor(maxWidth.value.toInt(), fontScale) == ReadinessLayout.STACKED) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(stackedRecoveryHeight(fontScale)),
                    shape = RoundedCornerShape(26.dp)
                )
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(stackedCompactHeight(fontScale)),
                    shape = RoundedCornerShape(24.dp)
                )
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(stackedCompactHeight(fontScale)),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(26.dp)
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SkeletonBox(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )
                    SkeletonBox(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }
        }
    }
}

private fun stackedRecoveryHeight(fontScale: Float) = if (fontScale > 1.15f) 340.dp else 300.dp

private fun stackedCompactHeight(fontScale: Float) = if (fontScale > 1.15f) 210.dp else 180.dp

@Composable
private fun RecoveryHeroCard(
    state: TodayUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = recoveryStatus(state.recoveryValue, state.isCalibrating)
    val accent = recoveryAccent(state.recoveryValue)
    val animatedRecovery = animatedValue(state.recoveryValue?.toFloat())
    val shape = RoundedCornerShape(26.dp)
    val cardDescription = stringResource(R.string.home_recovery_card_description)
    Card(
        onClick = onClick,
        modifier = modifier
            .neonSurface(shape, accent, glowAlpha = 0.24f)
            .semantics { contentDescription = cardDescription },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.home_recovery_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
                StatusPill(stringResource(status.label), accent)
            }
            CircularGauge(
                value = state.recoveryValue?.toFloat() ?: 0f,
                maxValue = 100f,
                color = accent,
                isIndeterminate = state.isCalibrating,
                strokeWidth = 10.dp,
                modifier = Modifier.size(150.dp).align(Alignment.CenterHorizontally)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = when {
                            state.isCalibrating -> "…"
                            state.recoveryValue != null -> animatedRecovery.roundToInt().toString()
                            else -> "—"
                        },
                        fontSize = 48.sp,
                        lineHeight = 50.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                    if (!state.isCalibrating && state.recoveryValue != null) {
                        Text(
                            "%",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = stringResource(status.message),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        todayRecommendation(
                            recovery = state.recoveryValue,
                            strain = state.strainValue,
                            sleep = state.sleepPerformance,
                            calibrating = state.isCalibrating
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
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
    val animatedStrain = animatedValue(state.strainValue?.toFloat())
    CompactReadinessCard(
        label = stringResource(R.string.home_effort_title),
        value = state.strainValue?.let { "%.1f".format(animatedStrain) } ?: "—",
        unit = if (state.strainValue != null) stringResource(R.string.home_effort_scale) else null,
        status = stringResource(status.label),
        supporting = stringResource(status.message),
        accent = AppColors.ActivityCyan,
        semanticsLabel = stringResource(R.string.home_effort_card_description),
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
    val animatedSleep = animatedValue(state.sleepDurationMinutes?.toFloat())
    CompactReadinessCard(
        label = stringResource(R.string.home_sleep_title),
        value = state.sleepDurationMinutes?.let { formatMinutes(animatedSleep.roundToLong()) } ?: "—",
        unit = state.sleepPerformance?.let { stringResource(R.string.home_sleep_performance, it) },
        status = stringResource(status.label),
        supporting = stringResource(status.message),
        accent = AppColors.SleepViolet,
        semanticsLabel = stringResource(R.string.home_sleep_card_description),
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
    val shape = RoundedCornerShape(24.dp)
    Card(
        onClick = onClick,
        modifier = modifier
            .neonSurface(shape, accent, glowAlpha = 0.18f)
            .semantics { contentDescription = semanticsLabel },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
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
                Text(label, style = MaterialTheme.typography.titleMedium, color = accent)
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

@Composable
internal fun formatMinutes(minutes: Long): String =
    stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
