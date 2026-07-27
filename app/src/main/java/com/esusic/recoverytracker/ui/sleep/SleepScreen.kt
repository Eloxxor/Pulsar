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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.model.SleepStageType
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.StatusPill
import com.esusic.recoverytracker.ui.common.rememberDateFormatter
import com.esusic.recoverytracker.ui.presentation.sleepStatus
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.today.CircularGauge
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val sleepTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SleepScreen(viewModel: SleepViewModel, contentBottomPadding: Dp = 0.dp) {
    val state by viewModel.uiState.collectAsState()
    val pullState = rememberPullRefreshState(state.isRefreshing, viewModel::refresh)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .pullRefresh(pullState)
    ) {
        SleepContent(state, viewModel, contentBottomPadding)
        PullRefreshIndicator(
            refreshing = state.isRefreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = AppColors.Surface,
            contentColor = AppColors.SleepViolet
        )
    }
}

@Composable
private fun SleepContent(
    state: SleepUiState,
    viewModel: SleepViewModel,
    contentBottomPadding: Dp
) {
    val sleepDateFormatter = rememberDateFormatter(R.string.date_pattern_weekday_month_day)
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
            title = stringResource(R.string.sleep_title),
            subtitle = stringResource(
                R.string.sleep_night_of,
                state.selectedDate.format(sleepDateFormatter)
            ),
            previousEnabled = state.canGoBack,
            nextEnabled = state.canGoForward,
            onPrevious = viewModel::goToPreviousDay,
            onNext = viewModel::goToNextDay
        )

        val total = state.totalMinutes
        if (total == null) {
            SectionCard {
                Text(
                    stringResource(R.string.sleep_none_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    stringResource(
                        R.string.sleep_none_body,
                        state.selectedDate.format(sleepDateFormatter)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
            return@Column
        }

        SleepHero(state, total)

        val sessionStart = state.sessionStart
        val sessionEnd = state.sessionEnd
        if (sessionStart != null && sessionEnd != null && state.stagePeriods.isNotEmpty()) {
            val inBed = state.inBedMinutes ?: total
            val restorative = if (inBed > 0) {
                (((state.deepMinutes ?: 0L) + (state.remMinutes ?: 0L)).toFloat() / inBed * 100)
                    .roundToInt()
            } else {
                0
            }
            SectionCard(title = stringResource(R.string.sleep_timeline_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    StatusPill(
                        stringResource(R.string.sleep_restorative, restorative),
                        AppColors.SleepViolet
                    )
                }
                SleepHypnogram(
                    stagePeriods = state.stagePeriods,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd
                )
            }
        }

        SectionCard(title = stringResource(R.string.sleep_stages_title)) {
            val inBed = state.inBedMinutes ?: total
            StageRow(SleepStageType.AWAKE, state.awakeMinutes, inBed)
            StageRow(SleepStageType.REM, state.remMinutes, inBed)
            StageRow(SleepStageType.LIGHT, state.lightMinutes, inBed)
            StageRow(SleepStageType.DEEP, state.deepMinutes, inBed)
            if (state.stagePeriods.isEmpty()) {
                Text(
                    stringResource(R.string.sleep_stages_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
        }

        state.needMinutes?.let { need ->
            SectionCard(title = stringResource(R.string.sleep_versus_need_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DurationStat(stringResource(R.string.sleep_asleep), total, Alignment.Start)
                    DurationStat(
                        stringResource(R.string.sleep_need),
                        need.roundToInt().toLong(),
                        Alignment.End
                    )
                }
                state.sleepPerformance?.let {
                    StatusPill(
                        stringResource(R.string.sleep_performance_share, it),
                        AppColors.SleepViolet
                    )
                }
                state.wakeEvents?.let { events ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.sleep_wake_events),
                            color = AppColors.TextSecondary
                        )
                        Text(events.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (sessionStart != null && sessionEnd != null && state.heartRateBuckets.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.sleep_heart_rate_title)) {
                SleepHeartRateChart(
                    buckets = state.heartRateBuckets,
                    sessionStart = sessionStart,
                    sessionEnd = sessionEnd
                )
            }
        }

        if (state.weeklyNights.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.sleep_week_title)) {
                state.weeklyAverageMinutes?.let {
                    Text(
                        stringResource(
                            R.string.sleep_week_average,
                            formatSleepMinutes(it.roundToInt().toLong())
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                SleepWeeklyChart(state.weeklyNights)
                state.weeklyInsight?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepHero(state: SleepUiState, total: Long) {
    val status = sleepStatus(state.sleepPerformance)
    SectionCard(accent = AppColors.SleepViolet, glowAlpha = 0.22f) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(stringResource(status.label), AppColors.SleepViolet)
            state.durationDelta?.let { DeltaChip(it, stringResource(R.string.unit_min)) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            CircularGauge(
                value = state.sleepPerformance?.toFloat() ?: 0f,
                maxValue = 100f,
                color = AppColors.SleepViolet,
                strokeWidth = 10.dp,
                modifier = Modifier.size(126.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.sleepPerformance?.toString() ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AppColors.SleepViolet
                    )
                    state.sleepPerformance?.let {
                        Text(
                            "%",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    formatSleepMinutes(total),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.TextPrimary
                )
                if (state.sessionStart != null && state.sessionEnd != null) {
                    val formatter = sleepTimeFormatter.withZone(ZoneId.systemDefault())
                    Text(
                        "${formatter.format(state.sessionStart)} – ${formatter.format(state.sessionEnd)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
        Text(stringResource(status.message), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DurationStat(label: String, minutes: Long, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Text(
            formatSleepMinutes(minutes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(stringResource(stageLabel(stage)), style = MaterialTheme.typography.bodyMedium)
                if (minutes != null) {
                    Text(
                        stringResource(R.string.sleep_stage_share, (fraction * 100).roundToInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                if (minutes == null) "—" else formatSleepMinutes(minutes),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(3.dp)).background(AppColors.GridLine)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp)).background(color)
            )
        }
    }
}

@Composable
private fun formatSleepMinutes(minutes: Long): String =
    stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
