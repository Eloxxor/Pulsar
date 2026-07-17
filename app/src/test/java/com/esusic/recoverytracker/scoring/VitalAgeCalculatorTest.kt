package com.esusic.recoverytracker.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VitalAgeCalculatorTest {
    private val age = 30

    @Test
    fun `strong vitals land below chronological age`() {
        val result = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgHrvMillis = 70.0, avgRestingHr = 52.0, avgSleepMinutes = 460.0, avgSteps = 11000.0, avgStrain = 12.0)
        )!!
        assertTrue("expected vital age < $age, got ${result.vitalAge}", result.vitalAge < age)
    }

    @Test
    fun `weak vitals land above chronological age`() {
        val result = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgHrvMillis = 20.0, avgRestingHr = 78.0, avgSleepMinutes = 330.0, avgSteps = 2500.0, avgStrain = 1.0)
        )!!
        assertTrue("expected vital age > $age, got ${result.vitalAge}", result.vitalAge > age)
    }

    @Test
    fun `fewer than two available components yields null`() {
        assertNull(VitalAgeCalculator.calculate(age, VitalAgeInputs(avgRestingHr = 60.0)))
        assertNull(VitalAgeCalculator.calculate(age, VitalAgeInputs()))
    }

    @Test
    fun `missing components renormalize the remaining weights`() {
        // Only RHR (0.15) and sleep (0.15) present: weights become 0.5 each
        val result = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgRestingHr = 72.0, avgSleepMinutes = 460.0)
        )!!
        // rhrOffset = (72-60)*0.5 = 6.0; sleepOffset (in optimal band) = -1.0
        val expected = 30.0 + 0.5 * 6.0 + 0.5 * -1.0
        assertEquals(expected, result.vitalAge, 1e-9)
        assertEquals(0.5, result.rhr!!.weight, 1e-9)
        assertEquals(0.5, result.sleep!!.weight, 1e-9)
        assertNull(result.hrv)
        assertNull(result.activity)
        assertNull(result.fitness)
    }

    @Test
    fun `hrv penalty clamps at plus 10 and bonus caps at minus 4`() {
        // Absurdly low HRV: hrvAge clamps to 80, offset clamps to +10
        val result = VitalAgeCalculator.calculate(age, VitalAgeInputs(avgHrvMillis = 1.0, avgRestingHr = 62.0))!!
        assertEquals(10.0, result.hrv!!.offsetYears, 1e-9)
        // Excellent HRV may no longer mask other components: bonus caps at -4
        val young = VitalAgeCalculator.calculate(age, VitalAgeInputs(avgHrvMillis = 500.0, avgRestingHr = 62.0))!!
        assertEquals(-4.0, young.hrv!!.offsetYears, 1e-9)
    }

    @Test
    fun `sleep band edges`() {
        fun sleepOffsetOf(minutes: Double): Double =
            VitalAgeCalculator.calculate(age, VitalAgeInputs(avgRestingHr = 62.0, avgSleepMinutes = minutes))!!.sleep!!.offsetYears
        assertEquals(-1.0, sleepOffsetOf(420.0), 1e-9)
        assertEquals(-1.0, sleepOffsetOf(510.0), 1e-9)
        assertEquals(3.0, sleepOffsetOf(360.0), 1e-9) // (420-360)/30*1.5
        assertEquals(0.75, sleepOffsetOf(540.0), 1e-9) // (540-510)/30*0.75
        assertEquals(8.0, sleepOffsetOf(0.0), 1e-9) // clamped
    }

    @Test
    fun `vital age never drops below 18`() {
        val result = VitalAgeCalculator.calculate(
            19,
            VitalAgeInputs(avgHrvMillis = 200.0, avgRestingHr = 45.0, avgSleepMinutes = 480.0, avgSteps = 15000.0, avgStrain = 15.0)
        )!!
        assertTrue(result.vitalAge >= 18.0)
    }

    @Test
    fun `poor estimated vo2max drives a large positive fitness offset`() {
        // Uth: 15.3 * 191.9 / 74 = 39.7 ml/kg/min; fitness age (57.5-39.7)/0.4 = 44.6
        // Offset 44.6 - 23 = 21.6, clamped to +15.
        val result = VitalAgeCalculator.calculate(
            23,
            VitalAgeInputs(avgRestingHr = 74.0, avgSleepMinutes = 460.0, maxHrBpm = 191.9)
        )!!
        assertEquals(15.0, result.fitness!!.offsetYears, 1e-9)
        assertTrue("expected vital age well above 23, got ${result.vitalAge}", result.vitalAge > 27.0)
    }

    @Test
    fun `elite resting heart rate yields a negative fitness offset`() {
        // Uth: 15.3 * 190 / 45 = 64.6; fitness age clamps to 18; offset 18 - 23 = -5
        val result = VitalAgeCalculator.calculate(
            23,
            VitalAgeInputs(avgRestingHr = 45.0, avgSleepMinutes = 460.0, maxHrBpm = 190.0)
        )!!
        assertEquals(-5.0, result.fitness!!.offsetYears, 1e-9)
    }

    @Test
    fun `fitness component is absent without a max heart rate`() {
        val result = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgRestingHr = 74.0, avgSleepMinutes = 460.0)
        )!!
        assertNull(result.fitness)
    }
}
