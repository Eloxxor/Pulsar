package com.esusic.recoverytracker.export

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CsvExporterTest {

    private fun row(
        date: LocalDate = LocalDate.of(2026, 7, 27),
        recovery: Int? = 72,
        effort: Double? = 12.42,
        sleepPerformance: Int? = 88,
        sleepMinutes: Long? = 462L,
        hrvMillis: Double? = 54.28,
        restingHeartRate: Long? = 51L,
        steps: Int? = 8421
    ) = ExportRow(date, recovery, effort, sleepPerformance, sleepMinutes, hrvMillis, restingHeartRate, steps)

    @Test
    fun `empty input still writes the header row`() {
        assertEquals(
            "date,recovery,effort,sleep_performance,sleep_minutes,hrv_ms,resting_hr,steps\n",
            CsvExporter.buildCsv(emptyList())
        )
    }

    @Test
    fun `header order matches the documented columns`() {
        assertEquals(
            listOf(
                "date",
                "recovery",
                "effort",
                "sleep_performance",
                "sleep_minutes",
                "hrv_ms",
                "resting_hr",
                "steps"
            ),
            CsvExporter.HEADER
        )
    }

    @Test
    fun `a full row is serialised with a stable ISO date and one decimal`() {
        val csv = CsvExporter.buildCsv(listOf(row()))

        assertEquals("2026-07-27,72,12.4,88,462,54.3,51,8421", csv.lines()[1])
    }

    @Test
    fun `single digit month and day are zero padded`() {
        val csv = CsvExporter.buildCsv(listOf(row(date = LocalDate.of(2026, 1, 5))))

        assertEquals("2026-01-05", csv.lines()[1].substringBefore(','))
    }

    @Test
    fun `null fields render as empty rather than the string null`() {
        val csv = CsvExporter.buildCsv(
            listOf(
                row(
                    recovery = null,
                    effort = null,
                    sleepPerformance = null,
                    sleepMinutes = null,
                    hrvMillis = null,
                    restingHeartRate = null,
                    steps = null
                )
            )
        )

        assertEquals("2026-07-27,,,,,,,", csv.lines()[1])
    }

    @Test
    fun `every row ends with a newline`() {
        val csv = CsvExporter.buildCsv(listOf(row(), row(date = LocalDate.of(2026, 7, 26))))

        assertEquals(3, csv.lines().size - 1)
        assertEquals("", csv.lines().last())
    }

    @Test
    fun `values containing a comma are quoted`() {
        assertEquals(
            "a\n\"one,two\"\n",
            CsvExporter.buildCsv(listOf("a"), listOf(listOf("one,two")))
        )
    }

    @Test
    fun `quotes are doubled and the value is wrapped`() {
        assertEquals(
            "a\n\"say \"\"hi\"\"\"\n",
            CsvExporter.buildCsv(listOf("a"), listOf(listOf("say \"hi\"")))
        )
    }

    @Test
    fun `newlines inside a value are quoted`() {
        assertEquals("\"one\ntwo\"", CsvExporter.escape("one\ntwo"))
        assertEquals("\"one\rtwo\"", CsvExporter.escape("one\rtwo"))
    }

    @Test
    fun `plain values are not quoted`() {
        assertEquals("plain", CsvExporter.escape("plain"))
        assertEquals("", CsvExporter.escape(null))
        assertEquals("", CsvExporter.escape(""))
    }

    @Test
    fun `header cells are escaped too`() {
        assertEquals(
            "\"a,b\",c\n",
            CsvExporter.buildCsv(listOf("a,b", "c"), emptyList())
        )
    }

    @Test
    fun `default file name carries the export date`() {
        assertEquals("pulsar-export-2026-07-27.csv", CsvExporter.defaultFileName(LocalDate.of(2026, 7, 27)))
    }
}
