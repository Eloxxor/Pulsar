package com.esusic.recoverytracker.export

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExporter {
    const val MIME_TYPE = "text/csv"

    val HEADER = listOf(
        "date",
        "recovery",
        "effort",
        "sleep_performance",
        "sleep_minutes",
        "hrv_ms",
        "resting_hr",
        "steps"
    )

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun buildCsv(rows: List<ExportRow>): String = buildCsv(HEADER, rows.map(::cellsOf))

    fun buildCsv(header: List<String>, rows: List<List<String?>>): String {
        val builder = StringBuilder()
        builder.append(header.joinToString(",", transform = ::escape)).append('\n')
        rows.forEach { row ->
            builder.append(row.joinToString(",", transform = ::escape)).append('\n')
        }
        return builder.toString()
    }

    fun escape(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    fun defaultFileName(date: LocalDate): String = "pulsar-export-${DATE_FORMAT.format(date)}.csv"

    fun createDocumentIntent(fileName: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(MIME_TYPE)
            .putExtra(Intent.EXTRA_TITLE, fileName)

    fun writeTo(resolver: ContentResolver, uri: Uri, csv: String) {
        resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
    }

    private fun cellsOf(row: ExportRow): List<String?> = listOf(
        DATE_FORMAT.format(row.date),
        row.recovery?.toString(),
        decimal(row.effort),
        row.sleepPerformance?.toString(),
        row.sleepMinutes?.toString(),
        decimal(row.hrvMillis),
        row.restingHeartRate?.toString(),
        row.steps?.toString()
    )

    private fun decimal(value: Double?): String? =
        value?.let { String.format(Locale.US, "%.1f", it) }
}
