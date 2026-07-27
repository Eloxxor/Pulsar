package com.esusic.recoverytracker.export

import com.esusic.recoverytracker.data.HealthRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class HealthDataExporter(private val repository: HealthRepository) {

    suspend fun collectRows(days: Int = DEFAULT_DAYS): List<ExportRow> {
        val scores = repository.observeRecentScores(days).first().associateBy { it.date }
        val aggregates = repository.observeRecentAggregates(days).first().associateBy { it.date }
        return (scores.keys + aggregates.keys).sortedDescending().map { date ->
            val score = scores[date]
            val aggregate = aggregates[date]
            ExportRow(
                date = LocalDate.parse(date),
                recovery = score?.recoveryValue,
                effort = score?.strainValue,
                sleepPerformance = score?.sleepPerformance,
                sleepMinutes = aggregate?.sleepDurationMinutes,
                hrvMillis = aggregate?.avgHrvMillis,
                restingHeartRate = aggregate?.restingHeartRate,
                steps = aggregate?.steps
            )
        }
    }

    suspend fun buildCsv(days: Int = DEFAULT_DAYS): String = CsvExporter.buildCsv(collectRows(days))

    suspend fun weeklyReport(weekEnd: LocalDate = LocalDate.now()): WeeklyReport =
        WeeklyReportCalculator.calculate(
            collectRows(WeeklyReportCalculator.REQUIRED_DAYS),
            weekEnd
        )

    companion object {
        const val DEFAULT_DAYS = 365
    }
}
