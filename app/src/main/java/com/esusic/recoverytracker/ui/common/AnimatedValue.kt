package com.esusic.recoverytracker.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

// Callers keep their own null handling: a null target settles the animation at 0f so the
// placeholder text stays the caller's decision and never renders as "0".
@Composable
fun animatedValue(target: Float?, durationMillis: Int = 400): Float {
    val animated by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "animated value"
    )
    return animated
}
