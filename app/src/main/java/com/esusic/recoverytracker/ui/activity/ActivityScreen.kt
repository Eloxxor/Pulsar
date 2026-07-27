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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ui.common.MetricTile
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.common.TrendCard
import com.esusic.recoverytracker.ui.common.TrendRangeSwitcher
import com.esusic.recoverytracker.ui.common.rememberDateFormatter
import com.esusic.recoverytracker.ui.presentation.effortStatus
import com.esusic.recoverytracker.ui.presentation.todayRecommendation
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.today.CircularGauge

@Composable
fun ActivityScreen(viewModel: ActivityViewModel, contentBottomPadding: Dp = 0.dp) {
    val state by viewModel.uiState.collectAsState()
    val effort = effortStatus(state.strainValue)
    val activityDateFormatter = rememberDateFormatter(R.string.date_pattern_weekday_month_day)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
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
            title = stringResource(R.string.activity_title),
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
                Text(
                    stringResource(R.string.activity_effort_title),
                    style = MaterialTheme.typography.titleMedium
                )
                StatusPill(stringResource(effort.label), AppColors.ActivityCyan)
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
                                stringResource(R.string.activity_effort_scale),
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextSecondary,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }
                    }
                }
                Text(
                    stringResource(effort.message),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        SectionCard(title = stringResource(R.string.activity_training_load_title)) {
            Text(
                stringResource(
                    todayRecommendation(
                        recovery = state.recoveryValue,
                        strain = state.strainValue,
                        sleep = null,
                        calibrating = false
                    )
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.activity_training_load_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(
                label = stringResource(R.string.activity_steps),
                value = state.steps?.toString() ?: "—",
                accent = AppColors.Movement,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = stringResource(R.string.activity_zone_minutes),
                value = state.zoneMinutes?.toString() ?: "—",
                unit = stringResource(R.string.unit_min),
                accent = AppColors.Movement,
                modifier = Modifier.weight(1f)
            )
        }
        MetricTile(
            label = stringResource(R.string.activity_heart_rate),
            value = state.latestHeartRateBpm?.toString() ?: "—",
            unit = stringResource(R.string.unit_bpm),
            accent = AppColors.HeartRate,
            glowAlpha = 0.10f,
            modifier = Modifier.fillMaxWidth()
        )

        SectionCard(title = stringResource(R.string.activity_timeline_title)) {
            ActivityTimeline(state.hourlySteps)
            Text(
                if (state.hourlySteps.any { it > 0 }) {
                    stringResource(R.string.activity_timeline_recorded)
                } else {
                    stringResource(R.string.activity_timeline_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.activity_trends_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            val average = state.strain.points
                .map { it.value.toDouble() }
                .takeIf { it.isNotEmpty() }
                ?.average()
            Text(
                average?.let {
                    stringResource(R.string.activity_trends_summary, it, state.range.days)
                } ?: stringResource(R.string.activity_trends_building),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            TrendRangeSwitcher(
                selected = state.range,
                onSelect = viewModel::selectRange,
                accent = AppColors.ActivityCyan
            )
        }
        ActivityTrendCard(
            stringResource(R.string.activity_trend_recovery),
            state.recovery,
            AppColors.Recovery,
            "%"
        )
        ActivityTrendCard(
            stringResource(R.string.activity_trend_effort),
            state.strain,
            AppColors.ActivityCyan,
            "",
            decimals = 1
        )
        ActivityTrendCard(
            stringResource(R.string.activity_trend_hrv),
            state.hrv,
            AppColors.Hrv,
            stringResource(R.string.unit_ms)
        )
        ActivityTrendCard(
            stringResource(R.string.activity_trend_rhr),
            state.rhr,
            AppColors.Rhr,
            stringResource(R.string.unit_bpm)
        )
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
    TrendCard(
        title = title,
        points = trend.points,
        color = color,
        unit = unit,
        latest = trend.latest,
        delta = trend.delta,
        decimals = decimals
    )
}
