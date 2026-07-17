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
        // Only RHR (0.25) and sleep (0.20) present: weights become 0.5555… and 0.4444…
        val result = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgRestingHr = 72.0, avgSleepMinutes = 460.0)
        )!!
        // rhrOffset = (72-62)*0.4 = 4.0; sleepOffset (in optimal band) = -1.0
        val expected = 30.0 + (0.25 / 0.45) * 4.0 + (0.20 / 0.45) * -1.0
        assertEquals(expected, result.vitalAge, 1e-9)
        assertEquals(0.25 / 0.45, result.rhr!!.weight, 1e-9)
        assertEquals(0.20 / 0.45, result.sleep!!.weight, 1e-9)
        assertNull(result.hrv)
        assertNull(result.activity)
    }

    @Test
    fun `hrv offset is clamped to plus minus 10 years`() {
        // Absurdly low HRV: hrvAge clamps to 80, offset clamps to +10
        val result = VitalAgeCalculator.calculate(age, VitalAgeInputs(avgHrvMillis = 1.0, avgRestingHr = 62.0))!!
        assertEquals(10.0, result.hrv!!.offsetYears, 1e-9)
        // Absurdly high HRV: hrvAge clamps to 18, offset = -12 clamps to -10
        val young = VitalAgeCalculator.calculate(age, VitalAgeInputs(avgHrvMillis = 500.0, avgRestingHr = 62.0))!!
        assertEquals(-10.0, young.hrv!!.offsetYears, 1e-9)
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
}
