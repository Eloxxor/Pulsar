package com.esusic.recoverytracker.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeeklyReportCalculatorTest {

    private val weekEnd: LocalDate = LocalDate.of(2026, 7, 27)

    private fun row(
        daysBefore: Long,
        recovery: Int? = null,
        effort: Double? = null,
        sleepMinutes: Long? = null
    ) = ExportRow(
        date = weekEnd.minusDays(daysBefore),
        recovery = recovery,
        effort = effort,
        sleepPerformance = null,
        sleepMinutes = sleepMinutes,
        hrvMillis = null,
        restingHeartRate = null,
        steps = null
    )

    @Test
    fun `window covers the seven days ending on the given date`() {
        val report = WeeklyReportCalculator.calculate(emptyList(), weekEnd)

        assertEquals(LocalDate.of(2026, 7, 21), report.weekStart)
        assertEquals(weekEnd, report.weekEnd)
    }

    @Test
    fun `averages every metric over a full week`() {
        val rows = (0L..6L).map { row(it, recovery = 70, effort = 10.0, sleepMinutes = 420L) }

        val report = WeeklyReportCalculator.calculate(rows, weekEnd)

        assertEquals(7, report.daysWithData)
        assertEquals(70.0, report.recovery.average!!, 0.001)
        assertEquals(10.0, report.effort.average!!, 0.001)
        assertEquals(420.0, report.sleepMinutes.average!!, 0.001)
    }

    @Test
    fun `week over week change compares against the preceding seven days`() {
        val current = (0L..6L).map { row(it, recovery = 80, effort = 12.0, sleepMinutes = 480L) }
        val previous = (7L..13L).map { row(it, recovery = 60, effort = 15.0, sleepMinutes = 400L) }

        val report = WeeklyReportCalculator.calculate(current + previous, weekEnd)

        assertEquals(60.0, report.recovery.previousAverage!!, 0.001)
        assertEquals(20.0, report.recovery.change!!, 0.001)
        assertEquals(-3.0, report.effort.change!!, 0.001)
        assertEquals(80.0, report.sleepMinutes.change!!, 0.001)
    }

    @Test
    fun `a partial week averages only the days present`() {
        val rows = listOf(
            row(0, recovery = 90),
            row(1, recovery = 60),
            row(2, recovery = 30)
        )

        val report = WeeklyReportCalculator.calculate(rows, weekEnd)

        assertEquals(3, report.daysWithData)
        assertEquals(60.0, report.recovery.average!!, 0.001)
    }

    @Test
    fun `a day missing one metric does not drag that average toward zero`() {
        val rows = listOf(
            row(0, recovery = 80, effort = 10.0),
            row(1, recovery = 40, effort = null),
            row(2, recovery = null, effort = 20.0)
        )

        val report = WeeklyReportCalculator.calculate(rows, weekEnd)

        assertEquals(3, report.daysWithData)
        assertEquals(60.0, report.recovery.average!!, 0.001)
        assertEquals(15.0, report.effort.average!!, 0.001)
        assertNull(report.sleepMinutes.average)
    }

    @Test
    fun `change is null when the previous week has no data`() {
        val report = WeeklyReportCalculator.calculate(listOf(row(0, recovery = 75)), weekEnd)

        assertEquals(75.0, report.recovery.average!!, 0.001)
        assertNull(report.recovery.previousAverage)
        assertNull(report.recovery.change)
    }

    @Test
    fun `an empty week reports null averages and no highlights`() {
        val report = WeeklyReportCalculator.calculate(emptyList(), weekEnd)

        assertEquals(0, report.daysWithData)
        assertNull(report.recovery.average)
        assertNull(report.effort.average)
        assertNull(report.sleepMinutes.average)
        assertNull(report.bestDay)
        assertNull(report.worstDay)
    }

    @Test
    fun `best and worst day come from recovery inside the current week`() {
        val rows = listOf(
            row(0, recovery = 55),
            row(3, recovery = 91),
            row(5, recovery = 24),
            row(8, recovery = 99)
        )

        val report = WeeklyReportCalculator.calculate(rows, weekEnd)

        assertEquals(DayHighlight(weekEnd.minusDays(3), 91), report.bestDay)
        assertEquals(DayHighlight(weekEnd.minusDays(5), 24), report.worstDay)
    }

    @Test
    fun `rows outside both windows are ignored`() {
        val rows = listOf(
            row(0, recovery = 70),
            row(20, recovery = 10)
        )

        val report = WeeklyReportCalculator.calculate(rows, weekEnd)

        assertEquals(1, report.daysWithData)
        assertEquals(70.0, report.recovery.average!!, 0.001)
        assertNull(report.recovery.previousAverage)
    }
}
