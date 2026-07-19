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
    fun `need minutes is typical plus strain and debt adjustments`() {
        // typical 400 -> floored to 420; strain 21 adds 60; debt 200 capped at 60
        val need = SleepPerformanceCalculator.calculateNeedMinutes(
            400.0, StrainScore(date, 21.0), 200.0
        )
        assertEquals(540.0, need, 0.001)
    }

    @Test
    fun `need minutes without strain or debt is the floored typical`() {
        assertEquals(420.0, SleepPerformanceCalculator.calculateNeedMinutes(300.0, null, 0.0), 0.001)
        assertEquals(500.0, SleepPerformanceCalculator.calculateNeedMinutes(500.0, null, 0.0), 0.001)
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

    @Test
    fun `need is floored at 7 hours even when the personal average is lower`() {
        // typical 390 -> need floored to 420; 390/420*100 = 92.86 -> 93
        val result = SleepPerformanceCalculator.calculate(390L, 390.0, null, 0.0)
        assertEquals(93, result)
    }

    @Test
    fun `low deep and rem share drags the score even when duration is met`() {
        // duration 480/480 = 1.0; quality (120/480)/0.45 = 0.5556
        // weights renormalize over duration+quality: 0.45/0.8 and 0.35/0.8
        // 0.5625*1.0 + 0.4375*0.5556 = 0.8056 -> 81
        val result = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0, deepRemMinutes = 120L)
        assertEquals(81, result)
    }

    @Test
    fun `deep and rem share at or above 45 percent scores full quality`() {
        val atTarget = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0, deepRemMinutes = 216L)
        val aboveTarget = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0, deepRemMinutes = 300L)
        assertEquals(100, atTarget)
        assertEquals(100, aboveTarget)
    }

    @Test
    fun `missing stage data falls back to duration-only scoring`() {
        val result = SleepPerformanceCalculator.calculate(480L, 480.0, null, 0.0, deepRemMinutes = null)
        assertEquals(100, result)
    }

    @Test
    fun `sleeping through 95 percent of the night scores full restfulness`() {
        // duration 1.0; quality (216/480)/0.45 = 1.0; asleep share 456/480 = 0.95 -> 1.0
        val result = SleepPerformanceCalculator.calculate(
            480L, 480.0, null, 0.0, deepRemMinutes = 216L, asleepMinutes = 456L
        )
        assertEquals(100, result)
    }

    @Test
    fun `a restless night caps the score even with perfect duration and stages`() {
        // asleep share 408/480 = 0.85 -> restfulness 0; 0.45 + 0.35 + 0 = 0.80 -> 80
        val result = SleepPerformanceCalculator.calculate(
            480L, 480.0, null, 0.0, deepRemMinutes = 216L, asleepMinutes = 408L
        )
        assertEquals(80, result)
    }

    @Test
    fun `restfulness scales linearly between 85 and 95 percent asleep`() {
        // asleep share 432/480 = 0.90 -> restfulness 0.5; 0.45 + 0.35 + 0.2*0.5 = 0.90 -> 90
        val result = SleepPerformanceCalculator.calculate(
            480L, 480.0, null, 0.0, deepRemMinutes = 216L, asleepMinutes = 432L
        )
        assertEquals(90, result)
    }
}
