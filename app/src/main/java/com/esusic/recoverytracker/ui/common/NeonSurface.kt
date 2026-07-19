package com.esusic.recoverytracker.ui.common

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.theme.AppColors

fun Modifier.neonSurface(
    shape: Shape,
    accent: Color? = null,
    glowAlpha: Float = 0.18f,
    baseColor: Color = AppColors.Surface
): Modifier = clip(shape)
    .drawWithCache {
        val center = Offset(size.width * 0.82f, size.height * 0.08f)
        val radius = size.maxDimension * 0.78f
        val glow = accent?.let {
            Brush.radialGradient(
                colors = listOf(it.copy(alpha = glowAlpha), Color.Transparent),
                center = center,
                radius = radius
            )
        }
        onDrawBehind {
            drawRect(baseColor)
            glow?.let { drawRect(it) }
        }
    }
    .border(
        width = 1.dp,
        color = accent?.copy(alpha = 0.30f) ?: AppColors.GridLine,
        shape = shape
    )
