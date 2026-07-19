package com.esusic.recoverytracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun `canvas is dark and primary text exceeds seven to one contrast`() {
        assertTrue(AppColors.Canvas.luminance() < 0.02f)
        assertTrue(contrast(AppColors.TextPrimary, AppColors.Canvas) >= 7.0f)
    }

    @Test
    fun `secondary text exceeds four point five to one contrast`() {
        assertTrue(contrast(AppColors.TextSecondary, AppColors.Canvas) >= 4.5f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
