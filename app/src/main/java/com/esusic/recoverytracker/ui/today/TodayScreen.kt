package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.detail.MetricType
import com.esusic.recoverytracker.ui.theme.warning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenSettings: () -> Unit, onMetricClick: (MetricType) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullRefreshState(uiState.isRefreshing, { viewModel.refresh() })

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularGauge(
                    value = (uiState.sleepPerformance ?: 0).toFloat(),
                    maxValue = 100f,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(90.dp)
                ) {
                    Text(
                        text = uiState.sleepPerformance?.toString() ?: "--",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                CircularGauge(
                    value = (uiState.recoveryValue ?: 0).toFloat(),
                    maxValue = 100f,
                    color = recoveryColor(uiState.recoveryValue),
                    isIndeterminate = uiState.isCalibrating,
                    modifier = Modifier.size(140.dp)
                ) {
                    Text(
                        text = when {
                            uiState.isCalibrating -> "Calibrating"
                            uiState.recoveryValue != null -> "${uiState.recoveryValue}"
                            else -> "--"
                        },
                        style = if (uiState.isCalibrating) MaterialTheme.typography.bodySmall else MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )
                }

                CircularGauge(
                    value = (uiState.strainValue ?: 0.0).toFloat(),
                    maxValue = 21f,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(90.dp)
                ) {
                    Text(
                        text = uiState.strainValue?.let { "%.1f".format(it) } ?: "--",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            HealthMonitorCard(uiState)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sleep", style = MaterialTheme.typography.titleMedium)
                    Text(uiState.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "No sleep data yet")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    QuickStat(
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        label = "Steps",
                        value = uiState.steps?.toString() ?: "--",
                        onClick = { onMetricClick(MetricType.ACTIVITY) }
                    )
                }
            }

            uiState.lastSyncedAtEpochMillis?.let {
                val formatted = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(it))
                Text("Last synced $formatted", style = MaterialTheme.typography.bodySmall)
            }
        }

        PullRefreshIndicator(
            refreshing = uiState.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HealthMonitorCard(uiState: TodayUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Health Monitor", style = MaterialTheme.typography.titleMedium)

            VitalRow("RHR", uiState.restingHeartRate?.let { "$it bpm" } ?: "--")
            VitalRow("HRV", uiState.avgHrvMillis?.let { "%.0f ms".format(it) } ?: "--")
            VitalRow(
                "Respiratory Rate",
                uiState.avgRespiratoryRate?.let { "%.1f breaths/min".format(it) } ?: "--"
            )
            VitalRow(
                "SpO2",
                uiState.avgOxygenSaturationPercent?.let { "%.0f%%".format(it) } ?: "--"
            )
            VitalRow(
                "Skin Temp Variation",
                uiState.avgSkinTemperatureDeltaCelsius?.let { "%+.1f°C".format(it) } ?: "--"
            )
        }
    }
}

@Composable
private fun VitalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun QuickStat(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun recoveryColor(score: Int?): Color = when {
    score == null -> MaterialTheme.colorScheme.primary
    score >= 67 -> MaterialTheme.colorScheme.primary
    score >= 34 -> MaterialTheme.colorScheme.warning
    else -> MaterialTheme.colorScheme.error
}
