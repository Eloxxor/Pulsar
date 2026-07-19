package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
fun TodayScreen(
    viewModel: TodayViewModel,
    onMetricClick: (MetricType) -> Unit,
    onOpenSleep: () -> Unit,
    onOpenAge: () -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppColors.Recovery))
                    Text(
                        "Recovery Tracker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                DatePill(
                    label = when {
                        uiState.isToday -> "Today"
                        uiState.selectedDate == LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> uiState.selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH))
                    },
                    canGoBack = uiState.canGoBack,
                    canGoForward = uiState.canGoForward,
                    onPrev = { viewModel.goToPreviousDay() },
                    onNext = { viewModel.goToNextDay() }
                )
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
                verticalAlignment = Alignment.Top
            ) {
                RingColumn(
                    label = "Sleep",
                    icon = Icons.Filled.Bedtime,
                    color = AppColors.SleepViolet,
                    value = (uiState.sleepPerformance ?: 0).toFloat(),
                    maxValue = 100f,
                    centerText = uiState.sleepPerformance?.let { "$it%" } ?: "--",
                    onClick = onOpenSleep
                )
                RingColumn(
                    label = "Recovery",
                    icon = Icons.Filled.Favorite,
                    color = recoveryColor(uiState.recoveryValue),
                    value = (uiState.recoveryValue ?: 0).toFloat(),
                    maxValue = 100f,
                    centerText = when {
                        uiState.isCalibrating -> "…"
                        uiState.recoveryValue != null -> "${uiState.recoveryValue}"
                        else -> "--"
                    },
                    isIndeterminate = uiState.isCalibrating,
                    onClick = { openSheet = GaugeSheet.RECOVERY }
                )
                RingColumn(
                    label = "Strain",
                    icon = Icons.Filled.Bolt,
                    color = AppColors.Strain,
                    value = (uiState.strainValue ?: 0.0).toFloat(),
                    maxValue = 21f,
                    centerText = uiState.strainValue?.let { "%.1f".format(it) } ?: "--",
                    onClick = { openSheet = GaugeSheet.STRAIN }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeartRateCard(
                    bpm = uiState.latestHeartRateBpm,
                    spark = uiState.heartRateSpark,
                    restingHr = uiState.restingHeartRate,
                    modifier = Modifier.weight(1f),
                    onClick = { onMetricClick(MetricType.HEART_RATE) }
                )
                StepsCard(
                    steps = uiState.steps,
                    hourlySteps = uiState.hourlySteps,
                    stepsLast6h = uiState.stepsLast6h,
                    isToday = uiState.isToday,
                    modifier = Modifier.weight(1f),
                    onClick = { onMetricClick(MetricType.ACTIVITY) }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZoneMinutesCard(
                    minutes = uiState.zoneMinutes,
                    yesterday = uiState.zoneMinutesYesterday,
                    modifier = Modifier.weight(1f)
                )
                StressCard(
                    stress = uiState.stressValue,
                    restingHr = uiState.restingHeartRate,
                    nowBpm = uiState.latestHeartRateBpm,
                    modifier = Modifier.weight(1f)
                )
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
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSleep),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null, tint = AppColors.SleepViolet)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        uiState.sleepDurationMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "No sleep data yet",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(10.dp))
                    DeltaChip(uiState.sleepDelta, "min")
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open sleep detail",
                        tint = AppColors.TextMuted
                    )
                }
            }

            SectionCard(title = "Vital age") {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAge),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Cake, contentDescription = null, tint = AppColors.VitalAge)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        uiState.vitalAge?.let { "%.1f yrs".format(it) } ?: "Calibrating",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open vital age",
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
private fun DatePill(
    label: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, enabled = canGoBack, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day",
                tint = if (canGoBack) AppColors.TextSecondary else AppColors.TextMuted
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        IconButton(onClick = onNext, enabled = canGoForward, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next day",
                tint = if (canGoForward) AppColors.TextSecondary else AppColors.TextMuted
            )
        }
    }
}

@Composable
private fun RingColumn(
    label: String,
    icon: ImageVector,
    color: Color,
    value: Float,
    maxValue: Float,
    centerText: String,
    onClick: () -> Unit,
    isIndeterminate: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        CircularGauge(
            value = value,
            maxValue = maxValue,
            color = color,
            isIndeterminate = isIndeterminate,
            strokeWidth = 8.dp,
            modifier = Modifier.size(104.dp)
        ) {
            Text(
                text = centerText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
        }
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
