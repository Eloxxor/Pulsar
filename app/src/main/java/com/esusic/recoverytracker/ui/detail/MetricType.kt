package com.esusic.recoverytracker.ui.detail

import androidx.annotation.StringRes
import com.esusic.recoverytracker.R

enum class MetricType(
    @get:StringRes val label: Int,
    @get:StringRes val unit: Int?,
    val higherIsBetter: Boolean,
    @get:StringRes val about: Int
) {
    HEART_RATE(
        R.string.metric_heart_rate_label,
        R.string.unit_bpm,
        false,
        R.string.metric_heart_rate_about
    ),
    ACTIVITY(
        R.string.metric_activity_label,
        null,
        true,
        R.string.metric_activity_about
    ),
    HRV(
        R.string.metric_hrv_label,
        R.string.unit_ms,
        true,
        R.string.metric_hrv_about
    )
}
