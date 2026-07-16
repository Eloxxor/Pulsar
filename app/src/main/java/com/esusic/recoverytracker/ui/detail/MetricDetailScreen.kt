package com.esusic.recoverytracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.common.Eyebrow
import com.esusic.recoverytracker.ui.common.MetricLineChart
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.theme.AppColors

@Composable
fun MetricDetailScreen(viewModel: MetricDetailViewModel, metricType: MetricType) {
    val uiState by viewModel.uiState.collectAsState()
    val color = when (metricType) {
        MetricType.HEART_RATE -> AppColors.Rhr
        MetricType.HRV -> AppColors.Hrv
        MetricType.ACTIVITY -> AppColors.Strain
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Eyebrow("Last 30 days")
            Text(metricType.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                uiState.todayValue?.let { "%.0f".format(it) } ?: "--",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            uiState.baselineMean?.let {
                Text(
                    "30-day average: %.0f".format(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        }
        SectionCard {
            MetricLineChart(points = uiState.history, lineColor = color)
        }
    }
}
