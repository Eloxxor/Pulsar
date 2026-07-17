package com.esusic.recoverytracker.ui.vitalage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.model.DeltaKind
import com.esusic.recoverytracker.scoring.VitalAgeComponent
import com.esusic.recoverytracker.scoring.VitalAgeResult
import com.esusic.recoverytracker.ui.common.ChartPoint
import com.esusic.recoverytracker.ui.common.DeltaChip
import com.esusic.recoverytracker.ui.common.Eyebrow
import com.esusic.recoverytracker.ui.common.MetricLineChart
import com.esusic.recoverytracker.ui.common.SectionCard
import com.esusic.recoverytracker.ui.theme.AppColors
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VitalAgeScreen(viewModel: VitalAgeViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Eyebrow("Vital age")

        val result = uiState.result
        when {
            uiState.isLoading -> Unit
            result == null -> CalibratingCard(uiState.daysUntilReady)
            else -> {
                HeroSection(result, uiState)
                AgingPaceCard(uiState)
                BreakdownCard(result)
                if (uiState.history.size >= 2) HistoryCard(uiState.history)
            }
        }
    }
}

@Composable
private fun HeroSection(result: VitalAgeResult, uiState: VitalAgeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "%.1f".format(result.vitalAge),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.VitalAge
            )
            Text(
                "yrs",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextMuted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        val diff = result.vitalAge - result.chronologicalAge
        val (text, color) = when {
            abs(diff) < 0.5 -> "Matches your age (${result.chronologicalAge})" to AppColors.TextSecondary
            diff < 0 -> "%.1f yrs younger than your age (%d)".format(-diff, result.chronologicalAge) to AppColors.Recovery
            else -> "%.1f yrs older than your age (%d)".format(diff, result.chronologicalAge) to AppColors.Danger
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
        DeltaChip(delta = uiState.weeklyDelta, unit = "yr", decimals = 1, suffix = "vs last week")
    }
}

@Composable
private fun AgingPaceCard(uiState: VitalAgeUiState) {
    SectionCard(title = "Aging pace") {
        val delta = uiState.weeklyDelta
        if (delta == null) {
            Text(
                "Needs about a week more data to show how fast you're aging.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        } else {
            val color = when (delta.kind) {
                DeltaKind.BETTER -> AppColors.Recovery
                DeltaKind.WORSE -> AppColors.Danger
                DeltaKind.NEUTRAL -> AppColors.TextSecondary
            }
            Text(
                "%+.1f yrs / week".format(delta.diff),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                when (delta.kind) {
                    DeltaKind.BETTER -> "Your vitals are trending younger than last week."
                    DeltaKind.WORSE -> "Your vitals are trending older than last week."
                    DeltaKind.NEUTRAL -> "Your vital age is holding steady."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
private fun BreakdownCard(result: VitalAgeResult) {
    SectionCard(title = "What drives it") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ComponentRow("HRV", result.hrv)
            ComponentRow("Resting HR", result.rhr)
            ComponentRow("Sleep", result.sleep)
            ComponentRow("Activity", result.activity)
            Text(
                "Each bar shows how that metric shifts your vital age, in years. Left of centre = younger.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextMuted
            )
        }
    }
}

@Composable
private fun ComponentRow(label: String, component: VitalAgeComponent?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (component != null) "$label · ${(component.weight * 100).roundToInt()}%" else label,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
            Text(
                component?.let { "%+.1f yr".format(it.offsetYears) } ?: "no data",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    component == null -> AppColors.TextMuted
                    component.offsetYears <= -0.05 -> AppColors.Recovery
                    component.offsetYears >= 0.05 -> AppColors.Danger
                    else -> AppColors.TextSecondary
                }
            )
        }
        DivergingBar(component?.offsetYears)
    }
}

@Composable
private fun DivergingBar(offsetYears: Double?) {
    val fraction = ((offsetYears ?: 0.0) / 10.0).toFloat().coerceIn(-1f, 1f)
    Row(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (fraction < 0f) {
                Box(
                    modifier = Modifier.fillMaxWidth(-fraction).fillMaxHeight()
                        .background(AppColors.Recovery)
                )
            }
        }
        Spacer(modifier = Modifier.width(2.dp))
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterStart
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(AppColors.Danger)
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(history: List<ChartPoint>) {
    SectionCard {
        var selected by remember { mutableStateOf<ChartPoint?>(null) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Eyebrow("History")
            val shown = selected
            Text(
                if (shown != null) {
                    "%.1f · %s".format(shown.value, shown.date.format(DateTimeFormatter.ofPattern("d MMM")))
                } else {
                    "%.1f today".format(history.last().value)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        MetricLineChart(
            points = history,
            lineColor = AppColors.VitalAge,
            valueFormatter = { "%.1f".format(it) },
            onSelect = { selected = it }
        )
    }
}

@Composable
private fun CalibratingCard(daysUntilReady: Int?) {
    SectionCard(title = "Calibrating") {
        Text(
            "Vital age needs a few days of vitals to be meaningful." +
                (daysUntilReady?.let { " About $it more day${if (it == 1) "" else "s"} to go." } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
    }
}
