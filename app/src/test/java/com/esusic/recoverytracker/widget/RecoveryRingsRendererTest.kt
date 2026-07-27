package com.esusic.recoverytracker.widget

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecoveryRingsRendererTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val populated = RecoveryWidgetData(
        recoveryValue = 42,
        isCalibrating = false,
        sleepMinutes = 373,
        sleepPerformance = 82,
        strainValue = 6.6
    )

    @Test
    fun `renders three rings wide enough to sit side by side`() {
        val bitmap = RecoveryRingsRenderer.render(context, populated)
        assertEquals(540, bitmap.width)
        assertEquals(242, bitmap.height)
    }

    @Test
    fun `each ring paints into its own third of the canvas`() {
        val bitmap = RecoveryRingsRenderer.render(context, populated)
        // The arc starts at twelve o'clock, so the top of every ring must carry pixels.
        listOf(96, 270, 444).forEach { centreX ->
            assertTrue("no arc drawn above centre $centreX", columnHasInk(bitmap, centreX))
        }
    }

    @Test
    fun `missing data still renders without throwing`() {
        val bitmap = RecoveryRingsRenderer.render(context, RecoveryWidgetData.EMPTY)
        assertEquals(540, bitmap.width)
    }

    private fun columnHasInk(bitmap: Bitmap, x: Int): Boolean =
        (20 until 60).any { y -> bitmap.getPixel(x, y) != 0 }
}

