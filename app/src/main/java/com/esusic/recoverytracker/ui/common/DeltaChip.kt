package com.esusic.recoverytracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esusic.recoverytracker.R
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
    @StringRes suffix: Int = R.string.trend_delta_suffix_month
) {
    if (delta == null) return
    val magnitude = "%.${decimals}f".format(abs(delta.diff))
    // A difference too small to survive rounding must not be dressed up as a direction:
    // "▲ 0" in danger red reads as a regression that the number itself contradicts.
    val belowPrecision = magnitude.none { it in '1'..'9' }
    val color = when {
        belowPrecision -> AppColors.TextSecondary
        delta.kind == DeltaKind.BETTER -> AppColors.Recovery
        delta.kind == DeltaKind.WORSE -> AppColors.Danger
        else -> AppColors.TextSecondary
    }
    val arrow = when {
        belowPrecision -> "—"
        delta.diff > 0 -> "▲"
        delta.diff < 0 -> "▼"
        else -> "—"
    }
    val valueText = if (unit.isEmpty()) magnitude else "$magnitude $unit"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$arrow $valueText ${stringResource(suffix)}",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
