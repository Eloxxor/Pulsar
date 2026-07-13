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

    private fun samplesAt(bpmSequence: List<Long>, minutesApart: Long = 1): List<HeartRateSample> {
        val start = Instant.parse("2026-07-13T08:00:00Z")
        return bpmSequence.mapIndexed { index, bpm ->
            HeartRateSample(start.plus(index * minutesApart, ChronoUnit.MINUTES), bpm)
        }
    }

    @Test
    fun `fewer than 2 samples yields zero strain`() {
        val result = StrainScoreCalculator.calculate(date, samplesAt(listOf(70)), maxHr)
        assertEquals(0.0, result.value, 0.0001)
    }

    @Test
    fun `resting heart rate all day yields low strain`() {
        val samples = samplesAt(List(60) { 60L }) // ~32% of maxHr, zone 0
        val result = StrainScoreCalculator.calculate(date, samples, maxHr)
        assertEquals(0.0, result.value, 0.0001)
    }

    @Test
    fun `sustained high heart rate yields higher strain than sustained moderate`() {
        val moderate = samplesAt(List(60) { 130L }) // ~68% of maxHr, zone 2
        val high = samplesAt(List(60) { 175L }) // ~92% of maxHr, zone 5
        val moderateStrain = StrainScoreCalculator.calculate(date, moderate, maxHr)
        val highStrain = StrainScoreCalculator.calculate(date, high, maxHr)
        assertTrue(highStrain.value > moderateStrain.value)
    }

    @Test
    fun `strain is clamped to a max of 21`() {
        val allOutSamples = samplesAt(List(500) { 189L })
        val result = StrainScoreCalculator.calculate(date, allOutSamples, maxHr)
        assertTrue(result.value <= 21.0)
    }

    @Test
    fun `gaps longer than 15 minutes are excluded from load accumulation`() {
        val start = Instant.parse("2026-07-13T08:00:00Z")
        val samples = listOf(
            HeartRateSample(start, 175L),
            HeartRateSample(start.plus(30, ChronoUnit.MINUTES), 175L) // 30-minute gap, likely device removed
        )
        val result = StrainScoreCalculator.calculate(date, samples, maxHr)
        assertEquals(0.0, result.value, 0.0001)
    }
}
