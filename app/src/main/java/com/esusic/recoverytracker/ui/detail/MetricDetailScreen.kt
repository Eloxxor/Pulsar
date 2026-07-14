package com.esusic.recoverytracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.trends.LineChart

@Composable
fun MetricDetailScreen(viewModel: MetricDetailViewModel, metricType: MetricType) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(metricType.label, style = MaterialTheme.typography.headlineMedium)
        Text(uiState.todayValue?.let { "%.0f".format(it) } ?: "--", style = MaterialTheme.typography.displayMedium)
        uiState.baselineMean?.let { Text("Your typical: %.0f".format(it)) }
        LineChart(values = uiState.history)
    }
}
