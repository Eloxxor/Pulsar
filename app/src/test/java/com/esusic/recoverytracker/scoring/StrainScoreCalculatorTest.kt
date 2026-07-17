package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StrainScoreCalculatorTest {
    private val date = LocalDate.of(2026, 7, 13)
    private val maxHr = 190.0
    private val restingHr = 60.0

    private fun samplesAt(bpmSequence: List<Long>, minutesApart: Long = 1): List<HeartRateSample> {
        val start = Instant.parse("2026-07-13T08:00:00Z")
        return bpmSequence.mapIndexed { index, bpm ->
            HeartRateSample(start.plus(index * minutesApart, ChronoUnit.MINUTES), bpm)
        }
    }

    @Test
    fun `fewer than 2 samples yields zero strain`() {
        val result = StrainScoreCalculator.calculate(date, samplesAt(listOf(70)), maxHr, restingHr)
        assertEquals(0.0, result.value, 0.0001)
    }

    @Test
    fun `resting heart rate all day yields zero strain`() {
        val samples = samplesAt(List(60) { 60L }) // 0% of heart-rate reserve
        val result = StrainScoreCalculator.calculate(date, samples, maxHr, restingHr)
        assertEquals(0.0, result.value, 0.0001)
    }

    @Test
    fun `everyday slightly-elevated heart rate stays at zero strain`() {
        // 8 hours at 95 bpm: well above 50% of max HR, but only ~27% of heart-rate
        // reserve — walking around, not cardiovascular load. This is the case that
        // used to saturate the score at 21 for people with a high resting HR.
        val samples = samplesAt(List(480) { 95L })
        val result = StrainScoreCalculator.calculate(date, samples, maxHr, restingHr)
        assertEquals(0.0, result.value, 0.0001)
    }

    @Test
    fun `one hard hour lands in the mid-teens instead of maxing out`() {
        val samples = samplesAt(List(60) { 165L }) // ~81% of heart-rate reserve, zone 4
        val result = StrainScoreCalculator.calculate(date, samples, maxHr, restingHr)
        assertTrue("expected mid-teens strain, got ${result.value}", result.value in 13.0..18.0)
    }

    @Test
    fun `sustained high heart rate yields higher strain than sustained moderate`() {
        val moderate = samplesAt(List(60) { 130L }) // ~54% of reserve, zone 1
        val high = samplesAt(List(60) { 175L }) // ~88% of reserve, zone 4
        val moderateStrain = StrainScoreCalculator.calculate(date, moderate, maxHr, restingHr)
        val highStrain = StrainScoreCalculator.calculate(date, high, maxHr, restingHr)
        assertTrue(highStrain.value > moderateStrain.value)
    }

    @Test
    fun `strain is clamped to a max of 21`() {
        val allOutSamples = samplesAt(List(500) { 189L })
        val result = StrainScoreCalculator.calculate(date, allOutSamples, maxHr, restingHr)
        assertTrue(result.value <= 21.0)
    }

    @Test
    fun `gaps longer than 15 minutes are excluded from load accumulation`() {
        val start = Instant.parse("2026-07-13T08:00:00Z")
        val samples = listOf(
            HeartRateSample(start, 175L),
            HeartRateSample(start.plus(30, ChronoUnit.MINUTES), 175L) // 30-minute gap, likely device removed
        )
        val result = StrainScoreCalculator.calculate(date, samples, maxHr, restingHr)
        assertEquals(0.0, result.value, 0.0001)
    }

    @Test
    fun `degenerate reserve when resting HR meets max HR yields zero strain`() {
        val samples = samplesAt(List(60) { 150L })
        val result = StrainScoreCalculator.calculate(date, samples, maxHr = 100.0, restingHr = 100.0)
        assertEquals(0.0, result.value, 0.0001)
    }
}
