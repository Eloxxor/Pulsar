package com.esusic.recoverytracker.ui.presentation

data class StatusCopy(val label: String, val message: String)

fun recoveryStatus(score: Int?, calibrating: Boolean): StatusCopy = when {
    calibrating -> StatusCopy(
        "Learning your baseline",
        "A few more days will improve this score."
    )
    score == null -> StatusCopy(
        "No score yet",
        "Sync Health Connect to calculate recovery."
    )
    score >= 80 -> StatusCopy("Optimal", "You are well recovered.")
    score >= 60 -> StatusCopy("Ready", "Good capacity for a normal day.")
    score >= 40 -> StatusCopy("Balanced", "Keep intensity controlled.")
    else -> StatusCopy("Recover", "Prioritize rest and easy movement.")
}

fun effortStatus(strain: Double?): StatusCopy = when {
    strain == null -> StatusCopy(
        "No load yet",
        "Activity will appear after the next sync."
    )
    strain >= 16.0 -> StatusCopy("High", "A demanding day. Leave room for recovery.")
    strain >= 10.0 -> StatusCopy(
        "Productive",
        "Meaningful load without reaching the top range."
    )
    strain >= 5.0 -> StatusCopy("Light", "Light activity is accumulating.")
    else -> StatusCopy("Resting", "Your current load is low.")
}

fun sleepStatus(performance: Int?): StatusCopy = when {
    performance == null -> StatusCopy(
        "No score yet",
        "Sleep detail will appear after a recorded night."
    )
    performance >= 85 -> StatusCopy("Excellent", "Strong duration and sleep quality.")
    performance >= 70 -> StatusCopy("Good", "A solid night with some room to improve.")
    performance >= 50 -> StatusCopy(
        "Fair",
        "Protect recovery and aim for more sleep tonight."
    )
    else -> StatusCopy("Low", "A lighter day may help you recover.")
}

fun todayRecommendation(
    recovery: Int?,
    strain: Double?,
    sleep: Int?,
    calibrating: Boolean
): String = when {
    calibrating -> "Keep wearing your tracker while Recovery Tracker learns your baseline."
    recovery == null -> "Sync your latest health data to get a daily recommendation."
    recovery < 40 -> "Prioritize rest and easy movement today."
    (strain ?: 0.0) >= 16.0 -> "Your load is already high. Keep the rest of today easy."
    recovery >= 80 && (sleep ?: 0) >= 70 ->
        "You have capacity for a challenging session today."
    recovery >= 60 -> "A normal training day fits your current recovery."
    else -> "Choose controlled intensity and reassess how you feel."
}
