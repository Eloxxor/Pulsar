package com.esusic.recoverytracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.model.DeltaKind
import com.esusic.recoverytracker.ui.common.DetailTopBar
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.common.TrendCard
import com.esusic.recoverytracker.ui.common.TrendRangeSwitcher
import com.esusic.recoverytracker.ui.theme.AppColors

@Composable
fun MetricDetailScreen(
    viewModel: MetricDetailViewModel,
    metricType: MetricType,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val color = when (metricType) {
        MetricType.HEART_RATE -> AppColors.Rhr
        MetricType.HRV -> AppColors.Hrv
        MetricType.ACTIVITY -> AppColors.Movement
    }
    val status = uiState.delta?.let { statusFor(it.kind, color) }
    val metricLabel = stringResource(metricType.label)
    val metricUnit = metricType.unit?.let { stringResource(it) }.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetailTopBar(metricLabel, onBack)
        TrendRangeSwitcher(
            selected = uiState.range,
            onSelect = viewModel::selectRange,
            accent = color
        )
        TrendCard(
            title = stringResource(R.string.metric_detail_last_days, uiState.range.days),
            chartName = metricLabel,
            points = uiState.history,
            color = color,
            unit = metricUnit,
            latest = uiState.todayValue?.toFloat(),
            delta = uiState.delta,
            baseline = uiState.baselineMean?.toFloat(),
            deltaSuffix = R.string.trend_delta_suffix_baseline,
            statusLabel = status?.let { stringResource(it.first) },
            statusColor = status?.second ?: color,
            accent = color,
            glowAlpha = 0.14f,
            valueStyle = MaterialTheme.typography.displayMedium,
            emptyMessage = R.string.metric_detail_empty,
            supportingText = uiState.baselineMean?.let { mean ->
                val value = "%.0f".format(mean)
                val withUnit = if (metricUnit.isEmpty()) value else "$value $metricUnit"
                stringResource(R.string.metric_detail_baseline_average, withUnit)
            }
        )
        SectionCard(title = stringResource(R.string.metric_detail_meaning_title)) {
            Text(
                stringResource(metricType.about),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.metric_detail_meaning_body),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

private fun statusFor(kind: DeltaKind, accent: Color): Pair<Int, Color> = when (kind) {
    DeltaKind.BETTER -> R.string.metric_detail_status_better to AppColors.Recovery
    DeltaKind.WORSE -> R.string.metric_detail_status_worse to AppColors.Danger
    DeltaKind.NEUTRAL -> R.string.metric_detail_status_neutral to accent
}
