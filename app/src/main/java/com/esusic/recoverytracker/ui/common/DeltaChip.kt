package com.esusic.recoverytracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.model.DeltaKind
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.ui.theme.AppColors
import kotlin.math.abs

@Composable
fun DeltaChip(
    delta: MetricDelta?,
    unit: String,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    suffix: String = "vs 30d"
) {
    if (delta == null) return
    val color = when (delta.kind) {
        DeltaKind.BETTER -> AppColors.Recovery
        DeltaKind.WORSE -> AppColors.Danger
        DeltaKind.NEUTRAL -> AppColors.TextSecondary
    }
    val arrow = when {
        delta.diff > 0 -> "▲"
        delta.diff < 0 -> "▼"
        else -> "—"
    }
    val magnitude = "%.${decimals}f".format(abs(delta.diff))
    val valueText = if (unit.isEmpty()) magnitude else "$magnitude $unit"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$arrow $valueText $suffix",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
