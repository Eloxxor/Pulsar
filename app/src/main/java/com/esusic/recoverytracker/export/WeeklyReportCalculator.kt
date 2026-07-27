package com.esusic.recoverytracker.export

import java.time.LocalDate

data class MetricSummary(val average: Double?, val previousAverage: Double?) {
    val change: Double?
        get() = if (average != null && previousAverage != null) average - previousAverage else null
}

data class DayHighlight(val date: LocalDate, val recovery: Int)

data class WeeklyReport(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val daysWithData: Int,
    val recovery: MetricSummary,
    val effort: MetricSummary,
    val sleepMinutes: MetricSummary,
    val bestDay: DayHighlight?,
    val worstDay: DayHighlight?
)

object WeeklyReportCalculator {
    const val WEEK_LENGTH_DAYS = 7
    const val REQUIRED_DAYS = WEEK_LENGTH_DAYS * 2

    fun calculate(rows: List<ExportRow>, weekEnd: LocalDate): WeeklyReport {
        val weekStart = weekEnd.minusDays((WEEK_LENGTH_DAYS - 1).toLong())
        val previousEnd = weekStart.minusDays(1)
        val previousStart = previousEnd.minusDays((WEEK_LENGTH_DAYS - 1).toLong())

        val current = rows.filter { it.date in weekStart..weekEnd }.sortedBy { it.date }
        val previous = rows.filter { it.date in previousStart..previousEnd }

        val recoveries = current.mapNotNull { row -> row.recovery?.let { row.date to it } }

        return WeeklyReport(
            weekStart = weekStart,
            weekEnd = weekEnd,
            daysWithData = current.map { it.date }.distinct().size,
            recovery = summarise(current, previous) { it.recovery?.toDouble() },
            effort = summarise(current, previous) { it.effort },
            sleepMinutes = summarise(current, previous) { it.sleepMinutes?.toDouble() },
            bestDay = recoveries.maxByOrNull { it.second }?.let { DayHighlight(it.first, it.second) },
            worstDay = recoveries.minByOrNull { it.second }?.let { DayHighlight(it.first, it.second) }
        )
    }

    private fun summarise(
        current: List<ExportRow>,
        previous: List<ExportRow>,
        selector: (ExportRow) -> Double?
    ): MetricSummary = MetricSummary(
        average = average(current, selector),
        previousAverage = average(previous, selector)
    )

    // Missing days are skipped rather than counted as zero, so a partial week
    // averages only the days that actually have the metric.
    private fun average(rows: List<ExportRow>, selector: (ExportRow) -> Double?): Double? =
        rows.mapNotNull(selector).takeIf { it.isNotEmpty() }?.average()
}
