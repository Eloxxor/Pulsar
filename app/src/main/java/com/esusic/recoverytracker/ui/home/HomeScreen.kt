package com.esusic.recoverytracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.MetricTile
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.SkeletonBox
import com.esusic.recoverytracker.ui.common.currentLocale
import com.esusic.recoverytracker.ui.common.rememberDateFormatter
import com.esusic.recoverytracker.ui.detail.MetricType
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.today.TodayUiState
import com.esusic.recoverytracker.ui.today.TodayViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: TodayViewModel,
    onOpenRecovery: (LocalDate) -> Unit,
    onOpenSleep: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMetric: (MetricType) -> Unit,
    onOpenAge: () -> Unit,
    contentBottomPadding: Dp = 0.dp
) {
    val state by viewModel.uiState.collectAsState()
    val pullState = rememberPullRefreshState(state.isRefreshing, viewModel::refresh)
    val haptics = LocalHapticFeedback.current
    val entrance = remember { MutableTransitionState(false) }
    entrance.targetState = !state.isLoading
    val homeDateFormatter = rememberDateFormatter(R.string.date_pattern_weekday_month_day)
    val updatedFormatter = rememberDateFormatter(R.string.date_pattern_month_day_time)
    val locale = currentLocale()

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
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 24.dp,
                    bottom = 24.dp + contentBottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            RootPageHeader(
                title = if (state.isToday) {
                    stringResource(R.string.home_today)
                } else {
                    state.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                },
                subtitle = state.selectedDate.format(homeDateFormatter),
                previousEnabled = state.canGoBack,
                nextEnabled = state.canGoForward,
                onPrevious = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.goToPreviousDay()
                },
                onNext = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.goToNextDay()
                }
            )

            if (state.isLoading) {
                HomeSkeleton()
                return@Column
            }

            if (!state.hasAllPermissions) {
                SectionCard {
                    Text(
                        stringResource(R.string.home_permission_off_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.Danger
                    )
                    Text(
                        stringResource(R.string.home_permission_off_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }

            AnimatedVisibility(
                visibleState = entrance,
                enter = entranceEnter(delayMillis = 0),
                exit = ExitTransition.None
            ) {
                ReadinessCluster(
                    state = state,
                    onRecoveryClick = { onOpenRecovery(state.selectedDate) },
                    onEffortClick = onOpenActivity,
                    onSleepClick = onOpenSleep
                )
            }

            AnimatedVisibility(
                visibleState = entrance,
                enter = entranceEnter(delayMillis = 50),
                exit = ExitTransition.None
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SectionHeading(stringResource(R.string.home_core_metrics))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricTile(
                            label = stringResource(R.string.home_metric_hrv),
                            value = state.avgHrvMillis?.let { "%.0f".format(it) } ?: "—",
                            unit = stringResource(R.string.unit_ms),
                            accent = AppColors.Hrv,
                            supportingText = stringResource(
                                baselineText(state.hrvDelta, higherIsBetter = true)
                            ),
                            delta = state.hrvDelta,
                            deltaUnit = stringResource(R.string.unit_ms),
                            onClick = { onOpenMetric(MetricType.HRV) },
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = stringResource(R.string.home_metric_resting_hr),
                            value = state.restingHeartRate?.toString() ?: "—",
                            unit = stringResource(R.string.unit_bpm),
                            accent = AppColors.Rhr,
                            supportingText = stringResource(
                                baselineText(state.rhrDelta, higherIsBetter = false)
                            ),
                            delta = state.rhrDelta,
                            deltaUnit = stringResource(R.string.unit_bpm),
                            onClick = { onOpenMetric(MetricType.HEART_RATE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visibleState = entrance,
                enter = entranceEnter(delayMillis = 100),
                exit = ExitTransition.None
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    val vitalAgeCardDescription =
                        stringResource(R.string.home_vital_age_card_description)
                    SectionCard(
                        title = stringResource(R.string.home_vital_age_title),
                        modifier = Modifier
                            .semantics { contentDescription = vitalAgeCardDescription }
                            .clickable(onClick = onOpenAge)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                state.vitalAge?.let { "%.1f".format(it) }
                                    ?: stringResource(R.string.home_vital_age_learning),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = AppColors.VitalAge
                            )
                            if (state.vitalAge != null) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.home_vital_age_years),
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

                    SectionCard(title = stringResource(R.string.home_more_vitals)) {
                        VitalLine(
                            stringResource(R.string.home_vital_respiratory_rate),
                            state.avgRespiratoryRate?.let {
                                stringResource(R.string.home_vital_respiratory_rate_value, it)
                            } ?: "—",
                            state.respRateDelta,
                            ""
                        )
                        VitalLine(
                            stringResource(R.string.home_vital_spo2),
                            state.avgOxygenSaturationPercent?.let {
                                stringResource(R.string.home_vital_spo2_value, it)
                            } ?: "—",
                            state.spo2Delta,
                            stringResource(R.string.unit_percentage_points)
                        )
                        VitalLine(
                            stringResource(R.string.home_vital_skin_temperature),
                            state.avgSkinTemperatureDeltaCelsius?.let {
                                stringResource(R.string.home_vital_skin_temperature_value, it)
                            } ?: "—",
                            state.skinTempDelta,
                            stringResource(R.string.unit_celsius)
                        )
                        VitalLine(
                            stringResource(R.string.home_vital_steps),
                            state.steps?.toString() ?: "—",
                            null,
                            ""
                        )
                        VitalLine(
                            stringResource(R.string.home_vital_zone_minutes),
                            state.zoneMinutes?.let {
                                stringResource(R.string.home_vital_zone_minutes_value, it)
                            } ?: "—",
                            null,
                            ""
                        )
                    }

                    Text(
                        stringResource(R.string.home_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    state.lastSyncedAtEpochMillis?.let { epochMillis ->
                        Text(
                            stringResource(
                                R.string.home_last_updated,
                                updatedFormatter
                                    .withZone(ZoneId.systemDefault())
                                    .format(Instant.ofEpochMilli(epochMillis))
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
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

private fun entranceEnter(delayMillis: Int): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 260, delayMillis = delayMillis)) +
        slideInVertically(
            animationSpec = tween(durationMillis = 260, delayMillis = delayMillis)
        ) { height -> height / 10 }

@Composable
private fun HomeSkeleton() {
    ReadinessClusterSkeleton()
    SectionHeading(stringResource(R.string.home_core_metrics))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonBox(modifier = Modifier.weight(1f).height(150.dp))
        SkeletonBox(modifier = Modifier.weight(1f).height(150.dp))
    }
    SkeletonBox(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(24.dp)
    )
    SkeletonBox(
        modifier = Modifier.fillMaxWidth().height(190.dp),
        shape = RoundedCornerShape(24.dp)
    )
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

@Composable
private fun vitalAgeContext(state: TodayUiState): String {
    val vital = state.vitalAge
        ?: return stringResource(R.string.home_vital_age_needs_data)
    val chronological = state.chronologicalAge
        ?: return stringResource(R.string.home_vital_age_recent_signals)
    val difference = vital - chronological
    return when {
        difference <= -0.5 -> stringResource(R.string.home_vital_age_below, -difference)
        difference >= 0.5 -> stringResource(R.string.home_vital_age_above, difference)
        else -> stringResource(R.string.home_vital_age_close)
    }
}

@StringRes
private fun baselineText(delta: MetricDelta?, higherIsBetter: Boolean): Int = when {
    delta == null -> R.string.home_baseline_building
    delta.diff == 0.0 -> R.string.home_baseline_at
    (delta.diff > 0) == higherIsBetter -> R.string.home_baseline_favorable
    else -> R.string.home_baseline_outside
}
