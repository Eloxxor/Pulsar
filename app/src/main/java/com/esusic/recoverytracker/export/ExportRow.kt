package com.esusic.recoverytracker.export

import java.time.LocalDate

data class ExportRow(
    val date: LocalDate,
    val recovery: Int?,
    val effort: Double?,
    val sleepPerformance: Int?,
    val sleepMinutes: Long?,
    val hrvMillis: Double?,
    val restingHeartRate: Long?,
    val steps: Int?
)
