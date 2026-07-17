package com.esusic.recoverytracker.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VitalAgeSeriesTest {
    private val birthYear = 1996

    private fun vitals(date: LocalDate, hrv: Double? = 50.0, rhr: Double? = 60.0) =
        DailyVitals(date = date, hrvMillis = hrv, restingHr = rhr, sleepMinutes = 450.0, steps = 8000.0, strain = 8.0)

    @Test
    fun `days whose trailing window has fewer than 3 data days are skipped`() {
        val d = LocalDate.of(2026, 7, 15)
        val series = VitalAgeSeries.build(listOf(vitals(d), vitals(d.plusDays(1))), birthYear)
        assertTrue(series.isEmpty())
    }

    @Test
    fun `series is ascending and starts at the first day with a full-enough window`() {
        val start = LocalDate.of(2026, 7, 10)
        val days = (0L..5L).map { vitals(start.plusDays(it)) }
        val series = VitalAgeSeries.build(days, birthYear)
        // Days 0 and 1 have <3 days in window; days 2..5 qualify
        assertEquals(4, series.size)
        assertEquals(start.plusDays(2), series.first().first)
        assertEquals(start.plusDays(5), series.last().first)
        assertTrue(series.zipWithNext().all { (a, b) -> a.first < b.first })
    }

    @Test
    fun `window averages skip null metrics per day`() {
        val start = LocalDate.of(2026, 7, 10)
        val days = listOf(
            vitals(start, hrv = 40.0),
            vitals(start.plusDays(1), hrv = null),
            vitals(start.plusDays(2), hrv = 60.0)
        )
        val series = VitalAgeSeries.build(days, birthYear)
        val result = series.single { it.first == start.plusDays(2) }.second
        // HRV average over non-null values = 50; identical to all-50 case
        val reference = VitalAgeCalculator.calculate(
            start.plusDays(2).year - birthYear,
            VitalAgeInputs(avgHrvMillis = 50.0, avgRestingHr = 60.0, avgSleepMinutes = 450.0, avgSteps = 8000.0, avgStrain = 8.0)
        )!!
        assertEquals(reference.vitalAge, result.vitalAge, 1e-9)
    }

    @Test
    fun `window only reaches back 14 days`() {
        val old = LocalDate.of(2026, 6, 1)
        val recent = LocalDate.of(2026, 7, 10)
        val days = listOf(vitals(old), vitals(old.plusDays(1)), vitals(old.plusDays(2))) +
            (0L..2L).map { vitals(recent.plusDays(it), hrv = 90.0) }
        val series = VitalAgeSeries.build(days, birthYear)
        // recent+2 window must not include June days: avg HRV stays 90
        val result = series.single { it.first == recent.plusDays(2) }.second
        val reference = VitalAgeCalculator.calculate(
            recent.plusDays(2).year - birthYear,
            VitalAgeInputs(avgHrvMillis = 90.0, avgRestingHr = 60.0, avgSleepMinutes = 450.0, avgSteps = 8000.0, avgStrain = 8.0)
        )!!
        assertEquals(reference.vitalAge, result.vitalAge, 1e-9)
    }
}
