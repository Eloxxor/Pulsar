package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.StrainScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SleepPerformanceCalculatorTest {
    private val date = LocalDate.of(2026, 7, 14)

    @Test
    fun `calculateSleepDebtMinutes sums shortfall across trailing window, capped at 180`() {
        val recent = List(10) { 420L } // 60 min short every night for 10 nights
        val debt = SleepPerformanceCalculator.calculateSleepDebtMinutes(480.0, recent)
        assertEquals(180.0, debt, 0.0001)
    }

    @Test
    fun `calculateSleepDebtMinutes ignores nights that met or exceeded baseline`() {
        val recent = listOf(480L, 500L, 480L)
        val debt = SleepPerformanceCalculator.calculateSleepDebtMinutes(480.0, recent)
        assertEquals(0.0, debt, 0.0001)
    }

    @Test
    fun `calculateSleepDebtMinutes only considers the trailing 7 days`() {
        val oldShortNight = listOf(0L) // would add 480 min of shortfall if counted
        val recentFullNights = List(7) { 480L }
        val debt = SleepPerformanceCalculator.calculateSleepDebtMinutes(480.0, oldShortNight + recentFullNights)
        assertEquals(0.0, debt, 0.0001)
    }

    @Test
    fun `calculate returns null when actual sleep is null`() {
        val result = SleepPerformanceCalculator.calculate(null, 480.0, null, 0.0)
        assertNull(result)
    }

    @Test
    fun `calculate returns 100 when actual sleep exactly meets need with no adjustments`() {
        val result = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0)
        assertEquals(100, result)
    }

    @Test
    fun `high prior-day strain increases need and lowers performance for the same actual sleep`() {
        val noStrain = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0)
        val maxStrain = SleepPerformanceCalculator.calculate(480L, 480.0, StrainScore(date, 21.0), 0.0)
        assertEquals(100, noStrain)
        // need = 480 + (21/21 * 60) = 540; 480/540*100 = 88.89 -> rounds to 89
        assertEquals(89, maxStrain)
    }

    @Test
    fun `debt repayment is capped at 60 minutes regardless of larger accumulated debt`() {
        val result = SleepPerformanceCalculator.calculate(480L, 480.0, null, 180.0)
        // need = 480 + min(180, 60) = 540; 480/540*100 = 88.89 -> rounds to 89
        assertEquals(89, result)
    }

    @Test
    fun `performance is clamped at 100 when actual sleep exceeds need`() {
        val result = SleepPerformanceCalculator.calculate(600L, 480.0, null, 0.0)
        assertEquals(100, result)
    }
}
