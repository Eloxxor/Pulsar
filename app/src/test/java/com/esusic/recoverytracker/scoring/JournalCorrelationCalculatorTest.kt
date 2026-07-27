package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.data.local.JournalTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JournalCorrelationCalculatorTest {
    private val start = LocalDate.of(2026, 6, 1)

    private fun day(offset: Int) = start.plusDays(offset.toLong())

    private fun tags(vararg blocks: Pair<IntRange, Set<JournalTag>>): Map<LocalDate, Set<JournalTag>> {
        val result = mutableMapOf<LocalDate, MutableSet<JournalTag>>()
        blocks.forEach { (range, tags) ->
            range.forEach { offset ->
                result.getOrPut(day(offset)) { mutableSetOf() } += tags
            }
        }
        return result
    }

    private fun recovery(vararg blocks: Pair<IntRange, Int>): Map<LocalDate, Int> =
        blocks.flatMap { (range, value) -> range.map { day(it) to value } }.toMap()

    private fun List<JournalTagImpact>.of(tag: JournalTag) = single { it.tag == tag }

    @Test
    fun `no journal entries yields no results`() {
        assertTrue(
            JournalCorrelationCalculator.calculate(
                emptyMap(),
                recovery(0..29 to 60)
            ).isEmpty()
        )
    }

    @Test
    fun `entries with empty tag sets are not journalled days`() {
        assertTrue(
            JournalCorrelationCalculator.calculate(
                mapOf(day(0) to emptySet(), day(1) to emptySet()),
                recovery(0..29 to 60)
            ).isEmpty()
        )
    }

    @Test
    fun `no recovery scores inside the journalled span yields no results`() {
        val entries = tags(0..9 to setOf(JournalTag.ALCOHOL))
        assertTrue(
            JournalCorrelationCalculator.calculate(entries, recovery(20..40 to 60)).isEmpty()
        )
    }

    @Test
    fun `tag average is compared against untagged days in the same span`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED)
        )
        val scores = recovery(0..4 to 50, 5..9 to 70)

        val alcohol = JournalCorrelationCalculator.calculate(entries, scores).of(JournalTag.ALCOHOL)

        assertEquals(5, alcohol.taggedDays)
        assertEquals(5, alcohol.untaggedDays)
        assertEquals(50.0, alcohol.taggedAverage!!, 0.001)
        assertEquals(70.0, alcohol.untaggedAverage!!, 0.001)
        assertEquals(-20.0, alcohol.impactPoints!!, 0.001)
        assertTrue(alcohol.hasEnoughData)
    }

    @Test
    fun `a helpful tag reports a positive impact`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED)
        )
        val scores = recovery(0..4 to 50, 5..9 to 70)

        val meditated = JournalCorrelationCalculator
            .calculate(entries, scores)
            .of(JournalTag.MEDITATED)

        assertEquals(20.0, meditated.impactPoints!!, 0.001)
    }

    @Test
    fun `results are ranked by absolute impact`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED),
            10..14 to setOf(JournalTag.HYDRATED)
        )
        val scores = recovery(0..4 to 40, 5..9 to 66, 10..14 to 62)

        val ranked = JournalCorrelationCalculator.calculate(entries, scores)

        assertEquals(
            listOf(JournalTag.ALCOHOL, JournalTag.MEDITATED, JournalTag.HYDRATED),
            ranked.map { it.tag }
        )
        assertEquals(-24.0, ranked[0].impactPoints!!, 0.001)
        assertEquals(15.0, ranked[1].impactPoints!!, 0.001)
        assertEquals(9.0, ranked[2].impactPoints!!, 0.001)
    }

    @Test
    fun `equal absolute impacts keep declaration order`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED)
        )
        val scores = recovery(0..4 to 50, 5..9 to 70)

        val ranked = JournalCorrelationCalculator.calculate(entries, scores)

        assertEquals(listOf(JournalTag.ALCOHOL, JournalTag.MEDITATED), ranked.map { it.tag })
        assertEquals(20.0, kotlin.math.abs(ranked[0].impactPoints!!), 0.001)
        assertEquals(20.0, kotlin.math.abs(ranked[1].impactPoints!!), 0.001)
    }

    @Test
    fun `too few tagged days reports needs more data`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED),
            0..3 to setOf(JournalTag.TRAVEL)
        )
        val scores = recovery(0..9 to 60)

        val travel = JournalCorrelationCalculator.calculate(entries, scores).of(JournalTag.TRAVEL)

        assertEquals(4, travel.taggedDays)
        assertEquals(6, travel.untaggedDays)
        assertNull(travel.taggedAverage)
        assertNull(travel.untaggedAverage)
        assertNull(travel.impactPoints)
        assertFalse(travel.hasEnoughData)
    }

    @Test
    fun `too few untagged days reports needs more data`() {
        val entries = tags(0..8 to setOf(JournalTag.ALCOHOL))
        val scores = recovery(0..8 to 60)

        val alcohol = JournalCorrelationCalculator.calculate(entries, scores).of(JournalTag.ALCOHOL)

        assertEquals(9, alcohol.taggedDays)
        assertEquals(0, alcohol.untaggedDays)
        assertFalse(alcohol.hasEnoughData)
    }

    @Test
    fun `exactly the minimum sample size is reported`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED)
        )
        val scores = recovery(0..9 to 60)

        val alcohol = JournalCorrelationCalculator.calculate(entries, scores).of(JournalTag.ALCOHOL)

        assertEquals(JournalCorrelationCalculator.MIN_TAGGED_DAYS, alcohol.taggedDays)
        assertEquals(JournalCorrelationCalculator.MIN_UNTAGGED_DAYS, alcohol.untaggedDays)
        assertEquals(0.0, alcohol.impactPoints!!, 0.001)
    }

    @Test
    fun `insufficient tags are ranked after every reported tag`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED),
            0..1 to setOf(JournalTag.TRAVEL)
        )
        val scores = recovery(0..4 to 50, 5..9 to 70)

        val ranked = JournalCorrelationCalculator.calculate(entries, scores)

        assertEquals(JournalTag.TRAVEL, ranked.last().tag)
        assertTrue(ranked.dropLast(1).all { it.hasEnoughData })
    }

    @Test
    fun `tags that were never logged are absent from the results`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED)
        )
        val scores = recovery(0..9 to 60)

        val ranked = JournalCorrelationCalculator.calculate(entries, scores)

        assertEquals(setOf(JournalTag.ALCOHOL, JournalTag.MEDITATED), ranked.map { it.tag }.toSet())
    }

    @Test
    fun `days outside the journalled span are not counted as untagged`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL),
            5..9 to setOf(JournalTag.MEDITATED)
        )
        val scores = recovery(-40..40 to 60)

        val alcohol = JournalCorrelationCalculator.calculate(entries, scores).of(JournalTag.ALCOHOL)

        assertEquals(5, alcohol.taggedDays)
        assertEquals(5, alcohol.untaggedDays)
    }

    @Test
    fun `tagged days without a recovery score do not count towards the sample`() {
        val entries = tags(
            0..5 to setOf(JournalTag.ALCOHOL),
            6..11 to setOf(JournalTag.MEDITATED)
        )
        val scores = recovery(2..11 to 60)

        val alcohol = JournalCorrelationCalculator.calculate(entries, scores).of(JournalTag.ALCOHOL)

        assertEquals(4, alcohol.taggedDays)
        assertFalse(alcohol.hasEnoughData)
    }

    @Test
    fun `a day carrying several tags feeds every one of them`() {
        val entries = tags(
            0..4 to setOf(JournalTag.ALCOHOL, JournalTag.LATE_MEAL),
            5..9 to setOf(JournalTag.MEDITATED)
        )
        val scores = recovery(0..4 to 45, 5..9 to 75)

        val ranked = JournalCorrelationCalculator.calculate(entries, scores)

        assertEquals(-30.0, ranked.of(JournalTag.ALCOHOL).impactPoints!!, 0.001)
        assertEquals(-30.0, ranked.of(JournalTag.LATE_MEAL).impactPoints!!, 0.001)
        assertEquals(30.0, ranked.of(JournalTag.MEDITATED).impactPoints!!, 0.001)
    }
}
