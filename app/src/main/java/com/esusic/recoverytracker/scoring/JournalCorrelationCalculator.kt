package com.esusic.recoverytracker.scoring

import com.esusic.recoverytracker.data.local.JournalTag
import java.time.LocalDate
import kotlin.math.abs

data class JournalTagImpact(
    val tag: JournalTag,
    val taggedDays: Int,
    val untaggedDays: Int,
    val taggedAverage: Double?,
    val untaggedAverage: Double?,
    val impactPoints: Double?
) {
    val hasEnoughData: Boolean get() = impactPoints != null
}

// Difference of means between tagged and untagged days, not a correlation coefficient:
// "alcohol costs you 9 points" is the claim the screen makes, and points are the unit
// users already read everywhere else. The comparison population is clipped to the span
// the user actually journaled, so days before they ever opened the journal are not
// silently counted as evidence that a tag was absent.
object JournalCorrelationCalculator {
    const val MIN_TAGGED_DAYS = 5
    const val MIN_UNTAGGED_DAYS = 5

    fun calculate(
        entries: Map<LocalDate, Set<JournalTag>>,
        recoveryByDate: Map<LocalDate, Int>
    ): List<JournalTagImpact> {
        val journaledDates = entries.filterValues { it.isNotEmpty() }.keys
        if (journaledDates.isEmpty()) return emptyList()
        val firstJournaled = journaledDates.min()
        val lastJournaled = journaledDates.max()
        val population = recoveryByDate.filterKeys {
            it >= firstJournaled && it <= lastJournaled
        }
        if (population.isEmpty()) return emptyList()

        val loggedTags = entries.values.flatten().toSet()
        return JournalTag.entries
            .filter { it in loggedTags }
            .map { tag -> impactOf(tag, entries, population) }
            .sortedWith(
                compareByDescending<JournalTagImpact> { it.hasEnoughData }
                    .thenByDescending { it.impactPoints?.let(::abs) ?: 0.0 }
            )
    }

    private fun impactOf(
        tag: JournalTag,
        entries: Map<LocalDate, Set<JournalTag>>,
        population: Map<LocalDate, Int>
    ): JournalTagImpact {
        val (tagged, untagged) = population.entries.partition { (date, _) ->
            entries[date]?.contains(tag) == true
        }
        val enough = tagged.size >= MIN_TAGGED_DAYS && untagged.size >= MIN_UNTAGGED_DAYS
        if (!enough) {
            return JournalTagImpact(tag, tagged.size, untagged.size, null, null, null)
        }
        val taggedAverage = tagged.map { it.value.toDouble() }.average()
        val untaggedAverage = untagged.map { it.value.toDouble() }.average()
        return JournalTagImpact(
            tag = tag,
            taggedDays = tagged.size,
            untaggedDays = untagged.size,
            taggedAverage = taggedAverage,
            untaggedAverage = untaggedAverage,
            impactPoints = taggedAverage - untaggedAverage
        )
    }
}
