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
            VitalAgeInputs(
                avgHrvMillis = 70.0,
                avgRestingHr = 52.0,
                avgSleepMinutes = 460.0,
                avgSteps = 11000.0,
                avgStrain = 12.0
            )
        )!!
        assertTrue("expected vital age < $age, got ${result.vitalAge}", result.vitalAge < age)
    }

    @Test
    fun `weak vitals land above chronological age`() {
        val result = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(
                avgHrvMillis = 20.0,
                avgRestingHr = 78.0,
                avgSleepMinutes = 330.0,
                avgSteps = 2500.0,
                avgStrain = 1.0
            )
        )!!
        assertTrue("expected vital age > $age, got ${result.vitalAge}", result.vitalAge > age)
    }

    @Test
    fun `fewer than two available components yields null`() {
        assertNull(VitalAgeCalculator.calculate(age, VitalAgeInputs(avgRestingHr = 60.0)))
        assertNull(VitalAgeCalculator.calculate(age, VitalAgeInputs()))
    }

    @Test
    fun `missing components renormalize the remaining effective weights`() {
        val result = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgRestingHr = 72.0, avgSleepMinutes = 460.0)
        )!!

        assertEquals(0.4, result.rhr!!.weight, 1e-9)
        assertEquals(0.6, result.sleep!!.weight, 1e-9)
        assertEquals(2.4, result.rhr!!.impactYears, 1e-9)
        assertEquals(-0.6, result.sleep!!.impactYears, 1e-9)
        assertEquals(31.8, result.vitalAge, 1e-9)
        assertNull(result.hrv)
        assertNull(result.activity)
        assertNull(result.fitness)
    }

    @Test
    fun `raw HRV signal retains asymmetric bounds`() {
        val old = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgHrvMillis = 1.0, avgRestingHr = 62.0)
        )!!
        assertEquals(10.0, old.hrv!!.rawOffsetYears, 1e-9)

        val young = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgHrvMillis = 500.0, avgRestingHr = 62.0)
        )!!
        assertEquals(-4.0, young.hrv!!.rawOffsetYears, 1e-9)
    }

    @Test
    fun `sleep raw signal keeps its band edges`() {
        fun rawSleepOffset(minutes: Double): Double = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgRestingHr = 62.0, avgSleepMinutes = minutes)
        )!!.sleep!!.rawOffsetYears

        assertEquals(-1.0, rawSleepOffset(420.0), 1e-9)
        assertEquals(-1.0, rawSleepOffset(510.0), 1e-9)
        assertEquals(3.0, rawSleepOffset(360.0), 1e-9)
        assertEquals(0.75, rawSleepOffset(540.0), 1e-9)
        assertEquals(8.0, rawSleepOffset(0.0), 1e-9)
    }

    @Test
    fun `vital age never drops below 18`() {
        val result = VitalAgeCalculator.calculate(
            19,
            VitalAgeInputs(
                avgHrvMillis = 200.0,
                avgRestingHr = 45.0,
                avgSleepMinutes = 480.0,
                avgSteps = 15000.0,
                avgStrain = 15.0
            )
        )!!
        assertTrue(result.vitalAge >= 18.0)
        assertEquals(
            result.vitalAge - result.chronologicalAge,
            components(result).sumOf { it.impactYears },
            1e-9
        )
    }

    @Test
    fun `poor estimated vo2max reports weighted impact instead of raw fifteen years`() {
        val result = VitalAgeCalculator.calculate(
            23,
            VitalAgeInputs(
                avgRestingHr = 74.0,
                avgSleepMinutes = 460.0,
                maxHrBpm = 191.9
            )
        )!!

        assertEquals(15.0, result.fitness!!.rawOffsetYears, 1e-9)
        assertTrue(result.fitness!!.impactYears < result.fitness!!.rawOffsetYears)
        assertEquals(
            result.vitalAge - 23.0,
            components(result).sumOf { it.impactYears },
            1e-9
        )
    }

    @Test
    fun `elite resting heart rate retains negative raw fitness signal`() {
        val result = VitalAgeCalculator.calculate(
            23,
            VitalAgeInputs(
                avgRestingHr = 45.0,
                avgSleepMinutes = 460.0,
                maxHrBpm = 190.0
            )
        )!!
        assertEquals(-5.0, result.fitness!!.rawOffsetYears, 1e-9)
    }

    @Test
    fun `fitness component is absent without a max heart rate`() {
        val result = VitalAgeCalculator.calculate(
            age,
            VitalAgeInputs(avgRestingHr = 74.0, avgSleepMinutes = 460.0)
        )!!
        assertNull(result.fitness)
    }

    @Test
    fun `visible impacts sum exactly to final age delta`() {
        val result = VitalAgeCalculator.calculate(
            23,
            VitalAgeInputs(
                avgHrvMillis = 30.0,
                avgRestingHr = 74.0,
                avgSleepMinutes = 460.0,
                avgSteps = 5000.0,
                avgStrain = 5.0,
                maxHrBpm = 191.9
            )
        )!!

        assertEquals(
            result.vitalAge - result.chronologicalAge,
            components(result).sumOf { it.impactYears },
            1e-9
        )
    }

    @Test
    fun `components below three sample days are excluded`() {
        val result = VitalAgeCalculator.calculate(
            30,
            VitalAgeInputs(
                avgHrvMillis = 55.0,
                hrvSampleDays = 2,
                avgRestingHr = 62.0,
                restingHrSampleDays = 7,
                avgSleepMinutes = 450.0,
                sleepSampleDays = 7
            )
        )!!

        assertNull(result.hrv)
        assertEquals(1.0, result.rhr!!.confidence, 1e-9)
    }

    @Test
    fun `bounded result rescales impacts and never exceeds ten years`() {
        val result = VitalAgeCalculator.calculate(
            30,
            VitalAgeInputs(
                avgHrvMillis = 1.0,
                avgRestingHr = 100.0,
                avgSleepMinutes = 120.0,
                avgSteps = 0.0,
                avgStrain = 0.0,
                maxHrBpm = 200.0
            )
        )!!

        assertEquals(10.0, result.vitalAge - 30.0, 1e-9)
        assertEquals(10.0, components(result).sumOf { it.impactYears }, 1e-9)
    }

    @Test
    fun `calculator is unavailable below adult age`() {
        assertNull(
            VitalAgeCalculator.calculate(
                17,
                VitalAgeInputs(avgRestingHr = 62.0, avgSleepMinutes = 450.0)
            )
        )
    }

    private fun components(result: VitalAgeResult): List<VitalAgeComponent> = listOfNotNull(
        result.fitness,
        result.hrv,
        result.rhr,
        result.sleep,
        result.activity
    )
}
