package com.esusic.recoverytracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.ui.presentation.recoveryStatus
import com.esusic.recoverytracker.ui.theme.AppColors
import com.esusic.recoverytracker.ui.theme.recoveryAccent
import java.util.Locale

// The widget cannot host a Compose gauge, so the three rings are rasterised here with the same
// geometry CircularGauge draws: a dim full-circle track, a soft wide glow under the progress arc,
// and the arc itself sweeping clockwise from twelve o'clock.
object RecoveryRingsRenderer {

    private const val RING = 148f
    private const val GAP = 26f
    private const val PAD_H = 22f
    private const val PAD_TOP = 20f
    private const val PAD_BOTTOM = 18f
    private const val STROKE = 12f
    private const val LABEL_GAP = 14f
    private const val LABEL_SIZE = 19f
    private const val SUB_GAP = 6f
    private const val SUB_SIZE = 17f

    private const val WIDTH = 3 * RING + 2 * GAP + 2 * PAD_H
    private const val HEIGHT = PAD_TOP + RING + LABEL_GAP + LABEL_SIZE + SUB_GAP + SUB_SIZE + PAD_BOTTOM

    fun render(context: Context, data: RecoveryWidgetData): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH.toInt(), HEIGHT.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unknown = context.getString(R.string.widget_no_value)
        val locale = Locale.getDefault()

        val recoveryKnown = data.recoveryValue != null && !data.isCalibrating
        gauge(
            canvas = canvas,
            index = 0,
            accent = recoveryAccent(data.recoveryValue.takeIf { recoveryKnown }).toArgb(),
            fraction = if (recoveryKnown) data.recoveryValue!! / 100f else null,
            value = if (recoveryKnown) data.recoveryValue.toString() else unknown,
            unit = if (recoveryKnown) "%" else null,
            label = context.getString(R.string.widget_recovery_label).uppercase(locale),
            sub = context.getString(
                if (recoveryKnown) {
                    recoveryStatus(data.recoveryValue, false).label
                } else {
                    R.string.widget_calibrating
                }
            )
        )

        val strain = data.strainValue
        gauge(
            canvas = canvas,
            index = 1,
            accent = AppColors.ActivityCyan.toArgb(),
            fraction = strain?.let { (it / 21.0).toFloat() },
            value = formatWidgetEffort(context, strain),
            unit = null,
            label = context.getString(R.string.widget_effort_label).uppercase(locale),
            sub = context.getString(R.string.widget_effort_of_max)
        )

        val performance = data.sleepPerformance
        gauge(
            canvas = canvas,
            index = 2,
            accent = AppColors.SleepViolet.toArgb(),
            fraction = performance?.let { it / 100f },
            value = performance?.toString() ?: unknown,
            unit = if (performance != null) "%" else null,
            label = context.getString(R.string.widget_sleep_label).uppercase(locale),
            sub = formatWidgetDuration(context, data.sleepMinutes)
        )

        return bitmap
    }

    private fun gauge(
        canvas: Canvas,
        index: Int,
        accent: Int,
        fraction: Float?,
        value: String,
        unit: String?,
        label: String,
        sub: String
    ) {
        val left = PAD_H + index * (RING + GAP)
        val centreX = left + RING / 2f
        val centreY = PAD_TOP + RING / 2f
        // Inset by a full stroke so the double-width glow stops at the ring's bounds.
        val bounds = RectF(
            left + STROKE,
            PAD_TOP + STROKE,
            left + RING - STROKE,
            PAD_TOP + RING - STROKE
        )

        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        arc.strokeWidth = STROKE
        arc.color = withAlpha(accent, 0.15f)
        canvas.drawArc(bounds, -90f, 360f, false, arc)

        if (fraction != null) {
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            arc.strokeWidth = STROKE * 2f
            arc.color = withAlpha(accent, 0.20f)
            canvas.drawArc(bounds, -90f, sweep, false, arc)

            arc.strokeWidth = STROKE
            arc.color = accent
            canvas.drawArc(bounds, -90f, sweep, false, arc)
        }

        val valuePaint = textPaint(RING * 0.30f, Typeface.DEFAULT_BOLD, accent)
        val unitPaint = textPaint(RING * 0.14f, Typeface.DEFAULT_BOLD, AppColors.TextSecondary.toArgb())
        val valueWidth = valuePaint.measureText(value)
        val unitWidth = unit?.let { unitPaint.measureText(it) + RING * 0.02f } ?: 0f
        val start = centreX - (valueWidth + unitWidth) / 2f
        val baseline = centreY - (valuePaint.fontMetrics.ascent + valuePaint.fontMetrics.descent) / 2f
        canvas.drawText(value, start, baseline, valuePaint)
        unit?.let {
            canvas.drawText(it, start + valueWidth + RING * 0.02f, baseline, unitPaint)
        }

        val labelPaint = textPaint(LABEL_SIZE, Typeface.DEFAULT_BOLD, AppColors.TextSecondary.toArgb())
        val labelBaseline = PAD_TOP + RING + LABEL_GAP + LABEL_SIZE
        drawCentredEllipsized(canvas, label, centreX, labelBaseline, RING, labelPaint)

        val subPaint = textPaint(SUB_SIZE, Typeface.DEFAULT, AppColors.TextMuted.toArgb())
        drawCentredEllipsized(canvas, sub, centreX, labelBaseline + SUB_GAP + SUB_SIZE, RING, subPaint)
    }

    private fun textPaint(size: Float, face: Typeface, colour: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = face
            color = colour
        }

    private fun drawCentredEllipsized(
        canvas: Canvas,
        text: String,
        centreX: Float,
        baseline: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        var shown = text
        if (paint.measureText(shown) > maxWidth) {
            val ellipsis = "…"
            val room = maxWidth - paint.measureText(ellipsis)
            val fits = paint.breakText(shown, true, room, null)
            shown = shown.take(fits.coerceAtLeast(0)).trimEnd() + ellipsis
        }
        canvas.drawText(shown, centreX - paint.measureText(shown) / 2f, baseline, paint)
    }

    private fun withAlpha(colour: Int, alpha: Float): Int =
        (colour and 0x00FFFFFF) or (((alpha * 255).toInt().coerceIn(0, 255)) shl 24)
}
