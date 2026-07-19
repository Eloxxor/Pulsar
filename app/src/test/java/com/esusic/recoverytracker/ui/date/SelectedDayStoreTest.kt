package com.esusic.recoverytracker.ui.date

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SelectedDayStoreTest {
    private val clock = Clock.fixed(
        Instant.parse("2026-07-19T12:00:00Z"),
        ZoneOffset.UTC
    )

    @Test
    fun `selection is shared and future dates clamp to today`() {
        val store = SelectedDayStore(clock)

        store.select(LocalDate.of(2026, 7, 18))
        assertEquals(LocalDate.of(2026, 7, 18), store.selectedDate.value)

        store.select(LocalDate.of(2026, 7, 21))
        assertEquals(LocalDate.of(2026, 7, 19), store.selectedDate.value)
    }

    @Test
    fun `previous and next mutate the same state flow`() {
        val store = SelectedDayStore(clock)

        store.previous()
        assertEquals(LocalDate.of(2026, 7, 18), store.selectedDate.value)

        store.next()
        assertEquals(LocalDate.of(2026, 7, 19), store.selectedDate.value)
    }
}
