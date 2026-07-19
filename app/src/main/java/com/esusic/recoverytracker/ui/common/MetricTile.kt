package com.esusic.recoverytracker.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.model.MetricDelta
import com.esusic.recoverytracker.ui.theme.AppColors

@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.TextPrimary,
    glowAlpha: Float = 0.08f,
    supportingText: String? = null,
    delta: MetricDelta? = null,
    deltaUnit: String = "",
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(22.dp)
    val spokenValue = buildString {
        append(label)
        append(", ")
        append(value)
        if (unit != null) {
            append(" ")
            append(unit)
        }
    }
    Card(
        modifier = modifier
            .neonSurface(shape, accent, glowAlpha)
            .semantics(mergeDescendants = true) { contentDescription = spokenValue }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.TextSecondary
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
                unit?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            if (delta != null) {
                DeltaChip(delta = delta, unit = deltaUnit)
            }
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
            content()
        }
    }
}
