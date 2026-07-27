package com.esusic.recoverytracker.widget

import android.content.Context
import com.esusic.recoverytracker.R
import com.esusic.recoverytracker.data.HealthRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Locale

data class RecoveryWidgetData(
    val recoveryValue: Int?,
    val isCalibrating: Boolean,
    val sleepMinutes: Long?,
    val sleepPerformance: Int?,
    val strainValue: Double?
) {
    companion object {
        val EMPTY = RecoveryWidgetData(null, false, null, null, null)

        // The widget only ever shows today, so a stale row from an earlier day is
        // treated as no data rather than silently presenting yesterday's score.
        suspend fun load(repository: HealthRepository, today: LocalDate = LocalDate.now()): RecoveryWidgetData {
            val date = today.toString()
            val score = repository.observeRecentScores(1).first().firstOrNull()?.takeIf { it.date == date }
            val aggregate = repository.observeRecentAggregates(1).first().firstOrNull()?.takeIf { it.date == date }
            return RecoveryWidgetData(
                recoveryValue = score?.recoveryValue,
                isCalibrating = score?.recoveryIsCalibrating ?: false,
                sleepMinutes = aggregate?.sleepDurationMinutes,
                sleepPerformance = score?.sleepPerformance,
                strainValue = score?.strainValue
            )
        }
    }
}

fun formatWidgetDuration(context: Context, minutes: Long?): String {
    if (minutes == null || minutes <= 0L) return context.getString(R.string.widget_no_value)
    return context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

fun formatWidgetEffort(context: Context, strain: Double?): String {
    if (strain == null) return context.getString(R.string.widget_no_value)
    return String.format(Locale.getDefault(), "%.1f", strain)
}
