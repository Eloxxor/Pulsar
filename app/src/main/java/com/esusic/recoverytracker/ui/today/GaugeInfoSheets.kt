package com.esusic.recoverytracker.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.ui.common.ChartPoint
import com.esusic.recoverytracker.ui.common.Eyebrow
import com.esusic.recoverytracker.ui.common.MetricLineChart
import com.esusic.recoverytracker.ui.theme.AppColors
import kotlin.math.roundToInt

enum class GaugeSheet { SLEEP, RECOVERY, STRAIN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaugeInfoSheet(sheet: GaugeSheet, uiState: TodayUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (sheet) {
                GaugeSheet.RECOVERY -> RecoverySheetContent(uiState)
                GaugeSheet.SLEEP -> SleepSheetContent(uiState)
                GaugeSheet.STRAIN -> StrainSheetContent(uiState)
            }
        }
    }
}

@Composable
private fun RecoverySheetContent(uiState: TodayUiState) {
    val score = uiState.recoveryValue
    Eyebrow("Recovery")
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            score?.toString() ?: "--",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = recoveryColor(score)
        )
        recoveryStatusLabel(score)?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = recoveryColor(score),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
    Text(
        "Your readiness to take on strain today, scored 0–100 against your 30-day baseline.",
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextSecondary
    )
    SubScoreBar("HRV vs baseline", "35%", uiState.hrvSubScore)
    SubScoreBar("Resting HR vs baseline", "25%", uiState.rhrSubScore)
    SubScoreBar("Sleep duration & quality", "30%", uiState.sleepSubScore)
    Text(
        "Remaining 10% rewards a low-strain previous day.",
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.TextMuted
    )
    BandRow(AppColors.Recovery, "67–100", "Recovered — ready for high strain")
    BandRow(AppColors.Warning, "34–66", "Adequate — moderate strain is fine")
    BandRow(AppColors.Danger, "0–33", "Run down — prioritise rest")
    HistoryChart(uiState.recoveryHistory, AppColors.Recovery)
}

@Composable
private fun SleepSheetContent(uiState: TodayUiState) {
    Eyebrow("Sleep performance")
    Text(
        uiState.sleepPerformance?.let { "$it%" } ?: "--",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = AppColors.SleepViolet
    )
    uiState.sleepDurationMinutes?.let {
        Text("Slept ${it / 60}h ${it % 60}m last night.", style = MaterialTheme.typography.bodyMedium)
    }
    Text(
        "How well last night covered your sleep need. Need = your typical sleep (floored at 7h), plus extra after high-strain days and repayment of recent sleep debt. 30% of the score is quality: deep + REM share of the night vs a 40% target.",
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextSecondary
    )
    HistoryChart(uiState.sleepPerformanceHistory, AppColors.SleepViolet)
}

@Composable
private fun StrainSheetContent(uiState: TodayUiState) {
    val strain = uiState.strainValue
    Eyebrow("Strain")
    Text(
        strain?.let { "%.1f".format(it) } ?: "--",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = AppColors.Strain
    )
    Text(
        "Cardiovascular load today, 0–21 (logarithmic). Time in higher heart-rate zones counts much more.",
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextSecondary
    )
    StrainBandRow("Light", "0 – 9.9", strain != null && strain < 10.0)
    StrainBandRow("Moderate", "10 – 13.9", strain != null && strain >= 10.0 && strain < 14.0)
    StrainBandRow("High", "14 – 17.9", strain != null && strain >= 14.0 && strain < 18.0)
    StrainBandRow("All out", "18 – 21", strain != null && strain >= 18.0)
    HistoryChart(uiState.strainHistory, AppColors.Strain) { "%.1f".format(it) }
}

@Composable
private fun HistoryChart(
    points: List<ChartPoint>,
    color: Color,
    valueFormatter: (Float) -> String = { "%.0f".format(it) }
) {
    if (points.size < 2) return
    Eyebrow("Last 30 days")
    MetricLineChart(points = points, lineColor = color, valueFormatter = valueFormatter)
}

@Composable
private fun SubScoreBar(label: String, weight: String, score: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$label · $weight", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Text(
                score?.roundToInt()?.toString() ?: "--",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        val fraction = ((score ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
        val barColor = recoveryColor(score?.roundToInt())
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp)).background(barColor)
            )
        }
    }
}

@Composable
private fun BandRow(color: Color, range: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(range, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@Composable
private fun StrainBandRow(name: String, range: String, isCurrent: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(if (isCurrent) AppColors.Strain else MaterialTheme.colorScheme.surfaceVariant)
            )
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
        Text(range, style = MaterialTheme.typography.bodySmall, color = AppColors.TextMuted)
    }
}
