package com.esusic.recoverytracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.ui.theme.AppColors

enum class TrendRange(val days: Int) {
    WEEK(7),
    MONTH(30),
    QUARTER(90)
}

@Composable
fun TrendRangeSwitcher(
    selected: TrendRange,
    onSelect: (TrendRange) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.TextPrimary
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrendRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelect(range) },
                label = { Text(stringResource(R.string.trend_range_days, range.days)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.16f),
                    selectedLabelColor = accent
                )
            )
        }
    }
}

@Composable
fun TrendCard(
    title: String,
    points: List<ChartPoint>,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    chartName: String = title,
    latest: Float? = points.lastOrNull()?.value,
    delta: MetricDelta? = null,
    baseline: Float? = null,
    decimals: Int = 0,
    @StringRes deltaSuffix: Int = R.string.trend_delta_suffix_average,
    statusLabel: String? = null,
    statusColor: Color = color,
    accent: Color? = null,
    glowAlpha: Float = 0.18f,
    valueStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    @StringRes emptyMessage: Int = R.string.trend_empty,
    supportingText: String? = null
) {
    var selected by remember(points) { mutableStateOf<ChartPoint?>(null) }
    val formatter: (Float) -> String = { value -> "%.${decimals}f".format(value) }
    val trendDateFormatter = rememberDateFormatter(R.string.date_pattern_month_day)

    SectionCard(modifier = modifier, accent = accent, glowAlpha = glowAlpha) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Eyebrow(title)
            statusLabel?.let { StatusPill(it, statusColor) }
        }
        if (points.size < 2) {
            Text(
                stringResource(emptyMessage),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shown = selected
                Text(
                    shown?.value?.let(formatter) ?: latest?.let(formatter) ?: "—",
                    style = valueStyle,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
                if (unit.isNotEmpty()) {
                    Text(unit, color = AppColors.TextSecondary)
                }
                if (shown == null) {
                    DeltaChip(delta, unit, decimals = decimals, suffix = deltaSuffix)
                } else {
                    Text(shown.date.format(trendDateFormatter), color = AppColors.TextSecondary)
                }
            }
            MetricLineChart(
                points = points,
                lineColor = color,
                valueFormatter = formatter,
                baseline = baseline,
                metricName = chartName,
                onSelect = { selected = it }
            )
        }
        supportingText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}
