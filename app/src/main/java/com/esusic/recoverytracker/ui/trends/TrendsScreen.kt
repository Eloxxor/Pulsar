package com.esusic.recoverytracker.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.common.ChartPoint
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.Eyebrow
import com.esusic.recoverytracker.ui.common.MetricLineChart
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val selectedDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

@Composable
fun TrendsScreen(viewModel: TrendsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Eyebrow("Last ${uiState.range.days} days")
            Text("Trends", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendRange.entries.forEach { range ->
                FilterChip(
                    selected = uiState.range == range,
                    onClick = { viewModel.selectRange(range) },
                    label = { Text(range.name.lowercase().replaceFirstChar(Char::uppercase)) }
                )
            }
        }

        TrendCard(title = "Recovery", trend = uiState.recovery, color = AppColors.Recovery, unit = "%")
        TrendCard(title = "Strain", trend = uiState.strain, color = AppColors.Strain, unit = "", decimals = 1)
        TrendCard(title = "HRV", trend = uiState.hrv, color = AppColors.Hrv, unit = "ms")
        TrendCard(title = "Resting heart rate", trend = uiState.rhr, color = AppColors.Rhr, unit = "bpm")
    }
}

@Composable
private fun TrendCard(title: String, trend: MetricTrend, color: Color, unit: String, decimals: Int = 0) {
    var selected by remember { mutableStateOf<ChartPoint?>(null) }
    val formatter: (Float) -> String = { "%.${decimals}f".format(it) }

    SectionCard(title = title) {
        if (trend.points.size < 2) {
            Text(
                "Not enough data yet — check back after a couple of days.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = selected
                if (shown == null) {
                    Text(
                        trend.latest?.let(formatter) ?: "--",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (unit.isNotEmpty()) {
                        Text(unit, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                    DeltaChip(trend.delta, unit, decimals = decimals, suffix = "vs avg")
                } else {
                    Text(
                        formatter(shown.value),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (unit.isNotEmpty()) {
                        Text(unit, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                    Text(
                        shown.date.format(selectedDateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
            MetricLineChart(
                points = trend.points,
                lineColor = color,
                valueFormatter = formatter,
                onSelect = { selected = it }
            )
        }
    }
}
