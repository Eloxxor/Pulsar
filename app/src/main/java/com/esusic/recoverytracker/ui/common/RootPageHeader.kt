package com.esusic.recoverytracker.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.theme.AppColors

@Composable
fun RootPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = false,
    nextEnabled: Boolean = false,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
        if (onPrevious != null || onNext != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AppColors.Surface,
                border = BorderStroke(1.dp, AppColors.GridLine)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onPrevious?.invoke() },
                        enabled = previousEnabled && onPrevious != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous day"
                        )
                    }
                    VerticalDivider(
                        modifier = Modifier.height(24.dp),
                        color = AppColors.GridLine
                    )
                    IconButton(
                        onClick = { onNext?.invoke() },
                        enabled = nextEnabled && onNext != null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next day"
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.size(1.dp))
        }
    }
}
