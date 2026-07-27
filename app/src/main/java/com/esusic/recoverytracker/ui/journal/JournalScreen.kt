package com.esusic.recoverytracker.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.data.local.JournalTag
import com.esusic.recoverytracker.data.local.JournalTagExpectation
import com.esusic.recoverytracker.scoring.JournalCorrelationCalculator
import com.esusic.recoverytracker.scoring.JournalTagImpact
import com.esusic.recoverytracker.ui.common.RootPageHeader
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.rememberDateFormatter
import com.esusic.recoverytracker.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun JournalScreen(viewModel: JournalViewModel, contentBottomPadding: Dp = 0.dp) {
    val state by viewModel.uiState.collectAsState()
    val journalDateFormatter = rememberDateFormatter(R.string.date_pattern_weekday_month_day)

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
            title = stringResource(R.string.journal_title),
            subtitle = state.selectedDate.format(journalDateFormatter),
            previousEnabled = true,
            nextEnabled = state.canGoForward,
            onPrevious = viewModel::goToPreviousDay,
            onNext = viewModel::goToNextDay
        )

        SectionCard(title = stringResource(R.string.journal_today_title)) {
            Text(
                stringResource(R.string.journal_today_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JournalTag.entries.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = tag in state.selectedTags,
                        onToggle = { viewModel.toggleTag(tag) }
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.journal_impacts_title)) {
            val reported = state.impacts.filter { it.hasEnoughData }
            val headline = reported.firstOrNull()
            if (headline?.impactPoints != null) {
                Text(
                    impactSentence(headline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    stringResource(R.string.journal_impacts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }

            val scale = reported.mapNotNull { it.impactPoints?.let(::abs) }.maxOrNull()
                ?.coerceAtLeast(1.0)
                ?: 1.0
            state.impacts.forEach { impact ->
                if (impact.hasEnoughData) {
                    ImpactRow(impact, scale)
                } else {
                    NeedsMoreDataRow(impact)
                }
            }

            if (state.impacts.isNotEmpty()) {
                Text(
                    pluralStringResource(
                        R.plurals.journal_based_on_days,
                        state.journaledDays,
                        state.journaledDays
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun TagChip(tag: JournalTag, selected: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val color = tagColor(tag)
    Text(
        text = stringResource(tag.label),
        modifier = Modifier
            .clip(shape)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .background(if (selected) color.copy(alpha = 0.16f) else AppColors.SurfaceElevated)
            .border(
                width = 1.dp,
                color = if (selected) color.copy(alpha = 0.55f) else AppColors.GridLine,
                shape = shape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = if (selected) color else AppColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    )
}

@Composable
private fun ImpactRow(impact: JournalTagImpact, scale: Double) {
    val points = impact.impactPoints ?: return
    val color = if (points < 0) AppColors.Danger else AppColors.Recovery
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(impact.tag.label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                formatPoints(points),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        DivergingBar(fraction = (abs(points) / scale).toFloat(), positive = points >= 0, color = color)
        Text(
            stringResource(
                R.string.journal_days_with_without,
                impact.taggedDays,
                impact.untaggedDays
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted
        )
    }
}

@Composable
private fun NeedsMoreDataRow(impact: JournalTagImpact) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(impact.tag.label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
            Text(
                stringResource(R.string.journal_needs_more_data),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted
            )
        }
        Text(
            stringResource(
                R.string.journal_days_logged,
                impact.taggedDays,
                JournalCorrelationCalculator.MIN_TAGGED_DAYS
            ),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextMuted
        )
    }
}

@Composable
private fun DivergingBar(fraction: Float, positive: Boolean, color: Color) {
    val shape = RoundedCornerShape(5.dp)
    val safeFraction = fraction.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(AppColors.GridLine.copy(alpha = 0.5f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (!positive) {
                Box(
                    Modifier.fillMaxWidth(safeFraction).fillMaxHeight().clip(shape).background(color)
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(AppColors.TextMuted))
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            if (positive) {
                Box(
                    Modifier.fillMaxWidth(safeFraction).fillMaxHeight().clip(shape).background(color)
                )
            }
        }
    }
}

private fun tagColor(tag: JournalTag): Color = when (tag.expectation) {
    JournalTagExpectation.HELPS -> AppColors.Recovery
    JournalTagExpectation.HURTS -> AppColors.Warning
}

@Composable
private fun formatPoints(points: Double): String {
    val rounded = points.roundToInt()
    return if (rounded > 0) {
        stringResource(R.string.journal_points_gain, rounded)
    } else {
        stringResource(R.string.journal_points_loss, rounded)
    }
}

@Composable
private fun impactSentence(impact: JournalTagImpact): String {
    val points = abs(impact.impactPoints ?: 0.0).roundToInt()
    val tag = stringResource(impact.tag.label)
    val template = if ((impact.impactPoints ?: 0.0) < 0) {
        R.plurals.journal_impact_costs
    } else {
        R.plurals.journal_impact_adds
    }
    return pluralStringResource(template, points, tag, points)
}
