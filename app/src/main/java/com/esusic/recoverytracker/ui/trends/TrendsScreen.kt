package com.esusic.recoverytracker.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TrendsScreen(viewModel: TrendsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Trends", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendRange.entries.forEach { range ->
                FilterChip(
                    selected = uiState.range == range,
                    onClick = { viewModel.selectRange(range) },
                    label = { Text(range.name.lowercase().replaceFirstChar(Char::uppercase)) }
                )
            }
        }

        Text("Recovery", style = MaterialTheme.typography.titleMedium)
        LineChart(values = uiState.recoveryValues)

        Text("Strain", style = MaterialTheme.typography.titleMedium)
        LineChart(values = uiState.strainValues, lineColor = MaterialTheme.colorScheme.secondary)

        Text("HRV", style = MaterialTheme.typography.titleMedium)
        LineChart(values = uiState.hrvValues)

        Text("Resting Heart Rate", style = MaterialTheme.typography.titleMedium)
        LineChart(values = uiState.rhrValues)
    }
}
