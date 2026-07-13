package com.esusic.recoverytracker.model

import java.time.LocalDate

data class RecoveryScore(
    val date: LocalDate,
    val value: Int,
    val isCalibrating: Boolean,
    val hrvSubScore: Double,
    val rhrSubScore: Double,
    val sleepSubScore: Double
)
