package com.esusic.recoverytracker.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.esusic.recoverytracker.ui.theme.AppColors

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp)
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    // Kept as State, not a snapshot read, so the pulse runs in the draw phase instead of
    // recomposing the whole placeholder tree every frame.
    val alpha = transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton pulse"
    )
    val brush = remember {
        Brush.horizontalGradient(
            listOf(AppColors.SurfaceElevated, AppColors.GridLine, AppColors.SurfaceElevated)
        )
    }
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 14.dp
) {
    SkeletonBox(
        modifier = modifier.fillMaxWidth(widthFraction).height(height),
        shape = RoundedCornerShape(height / 2)
    )
}
