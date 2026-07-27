package com.esusic.recoverytracker.ui.presentation

import androidx.annotation.StringRes
import com.esusic.recoverytracker.R

// Resource ids rather than strings: these bands are read from Composables, from the widget's
// RemoteViews and from a notification, none of which share a way to resolve text.
data class StatusCopy(@get:StringRes val label: Int, @get:StringRes val message: Int)

fun recoveryStatus(score: Int?, calibrating: Boolean): StatusCopy = when {
    calibrating -> StatusCopy(
        R.string.status_recovery_calibrating_label,
        R.string.status_recovery_calibrating_message
    )
    score == null -> StatusCopy(
        R.string.status_recovery_none_label,
        R.string.status_recovery_none_message
    )
    score >= 80 -> StatusCopy(
        R.string.status_recovery_optimal_label,
        R.string.status_recovery_optimal_message
    )
    score >= 60 -> StatusCopy(
        R.string.status_recovery_ready_label,
        R.string.status_recovery_ready_message
    )
    score >= 40 -> StatusCopy(
        R.string.status_recovery_balanced_label,
        R.string.status_recovery_balanced_message
    )
    else -> StatusCopy(
        R.string.status_recovery_low_label,
        R.string.status_recovery_low_message
    )
}

fun effortStatus(strain: Double?): StatusCopy = when {
    strain == null -> StatusCopy(
        R.string.status_effort_none_label,
        R.string.status_effort_none_message
    )
    strain >= 16.0 -> StatusCopy(
        R.string.status_effort_high_label,
        R.string.status_effort_high_message
    )
    strain >= 10.0 -> StatusCopy(
        R.string.status_effort_productive_label,
        R.string.status_effort_productive_message
    )
    strain >= 5.0 -> StatusCopy(
        R.string.status_effort_light_label,
        R.string.status_effort_light_message
    )
    else -> StatusCopy(
        R.string.status_effort_resting_label,
        R.string.status_effort_resting_message
    )
}

fun sleepStatus(performance: Int?): StatusCopy = when {
    performance == null -> StatusCopy(
        R.string.status_sleep_none_label,
        R.string.status_sleep_none_message
    )
    performance >= 85 -> StatusCopy(
        R.string.status_sleep_excellent_label,
        R.string.status_sleep_excellent_message
    )
    performance >= 70 -> StatusCopy(
        R.string.status_sleep_good_label,
        R.string.status_sleep_good_message
    )
    performance >= 50 -> StatusCopy(
        R.string.status_sleep_fair_label,
        R.string.status_sleep_fair_message
    )
    else -> StatusCopy(
        R.string.status_sleep_low_label,
        R.string.status_sleep_low_message
    )
}

@StringRes
fun todayRecommendation(
    recovery: Int?,
    strain: Double?,
    sleep: Int?,
    calibrating: Boolean
): Int = when {
    calibrating -> R.string.recommendation_calibrating
    recovery == null -> R.string.recommendation_no_data
    recovery < 40 -> R.string.recommendation_rest
    (strain ?: 0.0) >= 16.0 -> R.string.recommendation_load_already_high
    recovery >= 80 && (sleep ?: 0) >= 70 -> R.string.recommendation_challenge
    recovery >= 60 -> R.string.recommendation_normal_day
    else -> R.string.recommendation_controlled
}
