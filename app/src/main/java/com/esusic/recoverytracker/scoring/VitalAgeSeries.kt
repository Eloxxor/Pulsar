package com.esusic.recoverytracker.scoring

import java.time.LocalDate

data class DailyVitals(
    val date: LocalDate,
    val hrvMillis: Double? = null,
    val restingHr: Double? = null,
    val sleepMinutes: Double? = null,
    val steps: Double? = null,
    val strain: Double? = null
)

object VitalAgeSeries {
    const val WINDOW_DAYS = 14L
    const val MIN_WINDOW_DAYS = 3

    fun build(
        days: List<DailyVitals>,
        birthYear: Int,
        maxHrBpm: Double? = null
    ): List<Pair<LocalDate, VitalAgeResult>> {
        val byDate = days.associateBy { it.date }
        return days.map { it.date }.sorted().mapNotNull { date ->
            val window = (0 until WINDOW_DAYS).mapNotNull { byDate[date.minusDays(it)] }
            if (window.size < MIN_WINDOW_DAYS) return@mapNotNull null
            val hrv = window.mapNotNull { it.hrvMillis }
            val rhr = window.mapNotNull { it.restingHr }
            val sleep = window.mapNotNull { it.sleepMinutes }
            val steps = window.mapNotNull { it.steps }
            val strain = window.mapNotNull { it.strain }
            val inputs = VitalAgeInputs(
                avgHrvMillis = hrv.averageOrNull(),
                hrvSampleDays = hrv.size,
                avgRestingHr = rhr.averageOrNull(),
                restingHrSampleDays = rhr.size,
                avgSleepMinutes = sleep.averageOrNull(),
                sleepSampleDays = sleep.size,
                avgSteps = steps.averageOrNull(),
                stepsSampleDays = steps.size,
                avgStrain = strain.averageOrNull(),
                strainSampleDays = strain.size,
                maxHrBpm = maxHrBpm
            )
            VitalAgeCalculator.calculate(date.year - birthYear, inputs)?.let { date to it }
        }
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
