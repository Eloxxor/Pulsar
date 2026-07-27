package com.esusic.recoverytracker.ui.vitalage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.model.DeltaKind
import com.esusic.recoverytracker.scoring.VitalAgeComponent
import com.esusic.recoverytracker.scoring.VitalAgeResult
import com.esusic.recoverytracker.ui.common.ChartPoint
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.DetailTopBar
import com.esusic.recoverytracker.ui.common.Eyebrow
import com.esusic.recoverytracker.ui.common.MetricLineChart
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.SkeletonBox
import com.esusic.recoverytracker.ui.common.SkeletonLine
import com.esusic.recoverytracker.ui.common.animatedValue
import com.esusic.recoverytracker.ui.common.rememberDateFormatter
import com.esusic.recoverytracker.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VitalAgeScreen(viewModel: VitalAgeViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        DetailTopBar(stringResource(R.string.vital_age_title), onBack)

        val result = uiState.result
        when {
            uiState.isLoading -> VitalAgeSkeleton()
            result == null -> CalibratingCard(uiState.daysUntilReady)
            else -> {
                HeroSection(result, uiState)
                AgingPaceCard(uiState)
                BreakdownCard(result)
                if (uiState.history.size >= 2) HistoryCard(uiState.history)
            }
        }
    }
}

@Composable
private fun VitalAgeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBox(
            modifier = Modifier.fillMaxWidth(0.55f).height(64.dp),
            shape = RoundedCornerShape(18.dp)
        )
        SkeletonLine(widthFraction = 0.75f, height = 16.dp)
        SkeletonLine(widthFraction = 0.4f, height = 12.dp)
    }
    SkeletonBox(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        shape = RoundedCornerShape(24.dp)
    )
    SkeletonBox(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun HeroSection(result: VitalAgeResult, uiState: VitalAgeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "%.1f".format(animatedValue(result.vitalAge.toFloat())),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.VitalAge
            )
            Text(
                stringResource(R.string.unit_years_short),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        val diff = result.vitalAge - result.chronologicalAge
        val (text, color) = when {
            abs(diff) < 0.5 ->
                stringResource(R.string.vital_age_matches, result.chronologicalAge) to
                    AppColors.TextSecondary
            diff < 0 ->
                stringResource(R.string.vital_age_younger, -diff, result.chronologicalAge) to
                    AppColors.Recovery
            else ->
                stringResource(R.string.vital_age_older, diff, result.chronologicalAge) to
                    AppColors.Danger
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
        DeltaChip(
            delta = uiState.weeklyDelta,
            unit = stringResource(R.string.unit_year_short),
            decimals = 1,
            suffix = R.string.trend_delta_suffix_last_week
        )
        val asOfFormatter = rememberDateFormatter(R.string.date_pattern_day_month_short)
        uiState.asOfDate?.let {
            Text(
                stringResource(R.string.vital_age_as_of, it.format(asOfFormatter)),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted
            )
        }
    }
}

@Composable
private fun AgingPaceCard(uiState: VitalAgeUiState) {
    SectionCard(title = stringResource(R.string.vital_age_pace_title)) {
        val delta = uiState.weeklyDelta
        if (delta == null) {
            Text(
                stringResource(R.string.vital_age_pace_needs_data),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        } else {
            val color = when (delta.kind) {
                DeltaKind.BETTER -> AppColors.Recovery
                DeltaKind.WORSE -> AppColors.Danger
                DeltaKind.NEUTRAL -> AppColors.TextSecondary
            }
            Text(
                stringResource(R.string.vital_age_pace_value, delta.diff),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                stringResource(
                    when (delta.kind) {
                        DeltaKind.BETTER -> R.string.vital_age_pace_better
                        DeltaKind.WORSE -> R.string.vital_age_pace_worse
                        DeltaKind.NEUTRAL -> R.string.vital_age_pace_neutral
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun BreakdownCard(result: VitalAgeResult) {
    SectionCard(title = stringResource(R.string.vital_age_breakdown_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ComponentRow(R.string.vital_age_component_fitness, result.fitness)
            ComponentRow(R.string.vital_age_component_hrv, result.hrv)
            ComponentRow(R.string.vital_age_component_rhr, result.rhr)
            ComponentRow(R.string.vital_age_component_sleep, result.sleep)
            ComponentRow(R.string.vital_age_component_activity, result.activity)
            Text(
                stringResource(R.string.vital_age_breakdown_note),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted
            )
        }
    }
}

@Composable
private fun ComponentRow(@StringRes label: Int, component: VitalAgeComponent?) {
    val presentation = vitalAgeComponentPresentation(label, component)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                stringResource(presentation.label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
            Text(
                presentation.impactYears
                    ?.let { stringResource(R.string.vital_age_component_impact, it) }
                    ?: stringResource(R.string.vital_age_component_no_data),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    component == null -> AppColors.TextMuted
                    component.impactYears <= -0.05 -> AppColors.Recovery
                    component.impactYears >= 0.05 -> AppColors.Danger
                    else -> AppColors.TextSecondary
                }
            )
        }
        if (presentation.weightPercent != null && presentation.confidencePercent != null) {
            Text(
                stringResource(
                    R.string.vital_age_component_metadata,
                    presentation.weightPercent,
                    presentation.confidencePercent
                ),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted
            )
        }
        DivergingBar(component?.impactYears)
    }
}

internal data class VitalAgeComponentPresentation(
    @get:StringRes val label: Int,
    val weightPercent: Int?,
    val confidencePercent: Int?,
    val impactYears: Double?
)

internal fun vitalAgeComponentPresentation(
    @StringRes label: Int,
    component: VitalAgeComponent?
): VitalAgeComponentPresentation = VitalAgeComponentPresentation(
    label = label,
    weightPercent = component?.let { (it.weight * 100).roundToInt() },
    confidencePercent = component?.let { (it.confidence * 100).roundToInt() },
    impactYears = component?.impactYears
)

@Composable
private fun DivergingBar(offsetYears: Double?) {
    val fraction = ((offsetYears ?: 0.0) / 10.0).toFloat().coerceIn(-1f, 1f)
    Row(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (fraction < 0f) {
                Box(
                    modifier = Modifier.fillMaxWidth(-fraction).fillMaxHeight()
                        .background(AppColors.Recovery)
                )
            }
        }
        Spacer(modifier = Modifier.width(2.dp))
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterStart
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(AppColors.Danger)
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<ChartPoint>) {
    SectionCard {
        var selected by remember { mutableStateOf<ChartPoint?>(null) }
        val historyFormatter = rememberDateFormatter(R.string.date_pattern_day_month_short)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Eyebrow(stringResource(R.string.vital_age_history))
            val shown = selected
            Text(
                if (shown != null) {
                    stringResource(
                        R.string.vital_age_history_selected,
                        shown.value,
                        shown.date.format(historyFormatter)
                    )
                } else {
                    stringResource(R.string.vital_age_history_today, history.last().value)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        MetricLineChart(
            points = history,
            lineColor = AppColors.VitalAge,
            valueFormatter = { "%.1f".format(it) },
            metricName = stringResource(R.string.vital_age_title),
            onSelect = { selected = it }
        )
    }
}

@Composable
private fun CalibratingCard(daysUntilReady: Int?) {
    SectionCard(title = stringResource(R.string.vital_age_calibrating_title)) {
        Text(
            daysUntilReady?.let {
                pluralStringResource(R.plurals.vital_age_calibrating_body_days, it, it)
            } ?: stringResource(R.string.vital_age_calibrating_body),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
    }
}
