package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenSettings: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullRefreshState(uiState.isRefreshing, { viewModel.refresh() })

    Column(
        modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Today", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onOpenSettings) { Text("Settings") }
        }

        if (!uiState.hasAllPermissions) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Health Connect access was revoked.", color = MaterialTheme.colorScheme.error)
                    Text("Reconnect in Settings to keep scores up to date.")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Recovery", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        uiState.isCalibrating -> "Calibrating"
                        uiState.recoveryValue != null -> "${uiState.recoveryValue}"
                        else -> "--"
                    },
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Strain", style = MaterialTheme.typography.titleMedium)
                Text(uiState.strainValue?.let { "%.1f".format(it) } ?: "--", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Sleep", style = MaterialTheme.typography.titleMedium)
                Text(uiState.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "No sleep data yet")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RHR: ${uiState.restingHeartRate ?: "--"}")
            Text("HRV: ${uiState.avgHrvMillis?.let { "%.0f".format(it) } ?: "--"}")
            Text("Steps: ${uiState.steps ?: "--"}")
        }

        uiState.lastSyncedAtEpochMillis?.let {
            val formatted = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(it))
            Text("Last synced $formatted", style = MaterialTheme.typography.bodySmall)
        }

        PullRefreshIndicator(uiState.isRefreshing, pullRefreshState, Modifier)
    }
}
