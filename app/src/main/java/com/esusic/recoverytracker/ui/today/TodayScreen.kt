package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.Eyebrow
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.detail.MetricType
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenSettings: () -> Unit, onMetricClick: (MetricType) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val pullRefreshState = rememberPullRefreshState(uiState.isRefreshing, { viewModel.refresh() })
    var openSheet by remember { mutableStateOf<GaugeSheet?>(null) }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Eyebrow(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · MMM d", Locale.ENGLISH)))
                    Text("Today", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = AppColors.TextSecondary)
                }
            }

            if (!uiState.hasAllPermissions) {
                SectionCard {
                    Text("Health Connect access was revoked.", color = MaterialTheme.colorScheme.error)
                    Text("Reconnect in Settings to keep scores up to date.", color = AppColors.TextSecondary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GaugeColumn(label = "Sleep", caption = null, onClick = { openSheet = GaugeSheet.SLEEP }) {
                    CircularGauge(
                        value = (uiState.sleepPerformance ?: 0).toFloat(),
                        maxValue = 100f,
                        color = AppColors.SleepViolet,
                        modifier = Modifier.size(90.dp)
                    ) {
                        Text(
                            text = uiState.sleepPerformance?.let { "$it%" } ?: "--",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                GaugeColumn(
                    label = "Recovery",
                    caption = if (uiState.isCalibrating) null else recoveryStatusLabel(uiState.recoveryValue),
                    captionColor = recoveryColor(uiState.recoveryValue),
                    onClick = { openSheet = GaugeSheet.RECOVERY }
                ) {
                    CircularGauge(
                        value = (uiState.recoveryValue ?: 0).toFloat(),
                        maxValue = 100f,
                        color = recoveryColor(uiState.recoveryValue),
                        isIndeterminate = uiState.isCalibrating,
                        modifier = Modifier.size(148.dp)
                    ) {
                        Text(
                            text = when {
                                uiState.isCalibrating -> "Calibrating"
                                uiState.recoveryValue != null -> "${uiState.recoveryValue}"
                                else -> "--"
                            },
                            style = if (uiState.isCalibrating) MaterialTheme.typography.bodySmall else MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                GaugeColumn(label = "Strain", caption = null, onClick = { openSheet = GaugeSheet.STRAIN }) {
                    CircularGauge(
                        value = (uiState.strainValue ?: 0.0).toFloat(),
                        maxValue = 21f,
                        color = AppColors.Strain,
                        modifier = Modifier.size(90.dp)
                    ) {
                        Text(
                            text = uiState.strainValue?.let { "%.1f".format(it) } ?: "--",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            SectionCard(title = "Vitals") {
                VitalRow("Resting HR", uiState.restingHeartRate?.let { "$it bpm" } ?: "--", uiState.rhrDelta, "bpm")
                VitalRow("HRV", uiState.avgHrvMillis?.let { "%.0f ms".format(it) } ?: "--", uiState.hrvDelta, "ms")
                VitalRow(
                    "Respiratory rate",
                    uiState.avgRespiratoryRate?.let { "%.1f /min".format(it) } ?: "--",
                    uiState.respRateDelta,
                    "",
                    decimals = 1
                )
                VitalRow("SpO2", uiState.avgOxygenSaturationPercent?.let { "%.0f%%".format(it) } ?: "--", uiState.spo2Delta, "pp")
                VitalRow(
                    "Skin temp",
                    uiState.avgSkinTemperatureDeltaCelsius?.let { "%+.1f°C".format(it) } ?: "--",
                    uiState.skinTempDelta,
                    "°C",
                    decimals = 1
                )
            }

            SectionCard(title = "Sleep") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        uiState.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "No sleep data yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    DeltaChip(uiState.sleepDelta, "min")
                }
            }

            SectionCard(title = "Activity") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onMetricClick(MetricType.ACTIVITY) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = AppColors.Strain)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        uiState.steps?.toString() ?: "--",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("steps", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open steps detail",
                        tint = AppColors.TextMuted
                    )
                }
            }

            uiState.lastSyncedAtEpochMillis?.let {
                val formatted = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(it))
                Text(
                    "Last synced $formatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
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

    openSheet?.let { sheet ->
        GaugeInfoSheet(sheet = sheet, uiState = uiState, onDismiss = { openSheet = null })
    }
}

@Composable
private fun GaugeColumn(
    label: String,
    caption: String?,
    onClick: () -> Unit,
    captionColor: Color = AppColors.TextSecondary,
    gauge: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        gauge()
        Eyebrow(label)
        caption?.let { Text(it, fontSize = 12.sp, color = captionColor, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun VitalRow(label: String, value: String, delta: MetricDelta?, unit: String, decimals: Int = 0) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            DeltaChip(delta, unit, decimals = decimals)
        }
    }
}
