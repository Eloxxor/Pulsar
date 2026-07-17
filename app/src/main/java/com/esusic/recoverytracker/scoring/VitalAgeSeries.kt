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
            val inputs = VitalAgeInputs(
                avgHrvMillis = window.mapNotNull { it.hrvMillis }.averageOrNull(),
                avgRestingHr = window.mapNotNull { it.restingHr }.averageOrNull(),
                avgSleepMinutes = window.mapNotNull { it.sleepMinutes }.averageOrNull(),
                avgSteps = window.mapNotNull { it.steps }.averageOrNull(),
                avgStrain = window.mapNotNull { it.strain }.averageOrNull(),
                maxHrBpm = maxHrBpm
            )
            VitalAgeCalculator.calculate(date.year - birthYear, inputs)?.let { date to it }
        }
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
