package com.esusic.recoverytracker.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MetricLineChartTest {

    private val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    @Test
    fun `date labels stay evenly spaced and capped at four`() {
        assertEquals(listOf(0, 9, 19, 29), dateLabelIndices(30))
        assertEquals(listOf(0, 29, 59, 89), dateLabelIndices(90))
        assertEquals(listOf(0, 2, 4, 6), dateLabelIndices(7))
    }

    @Test
    fun `date labels never repeat an index on short series`() {
        assertEquals(listOf(0, 1), dateLabelIndices(2))
        assertEquals(listOf(0, 1, 2), dateLabelIndices(3))
        assertEquals(listOf(0), dateLabelIndices(1))
    }

    @Test
    fun `series summary names its range and its extremes`() {
        val start = LocalDate.of(2026, 7, 10)
        val points = listOf(
            ChartPoint(start, 48f),
            ChartPoint(start.plusDays(1), 55f),
            ChartPoint(start.plusDays(2), 51f)
        )

        val summary = chartSeriesSummary(points, formatter) { "%.0f".format(it) }!!

        assertEquals(3, summary.dayCount)
        assertEquals(start.format(formatter), summary.firstDate)
        assertEquals(start.plusDays(2).format(formatter), summary.lastDate)
        assertEquals("51", summary.latest)
        assertEquals("48", summary.lowest)
        assertEquals("55", summary.highest)
    }

    @Test
    fun `series summary falls back when there is not enough data`() {
        val points = listOf(ChartPoint(LocalDate.of(2026, 7, 10), 48f))

        assertNull(chartSeriesSummary(points, formatter) { "%.0f".format(it) })
    }
}
