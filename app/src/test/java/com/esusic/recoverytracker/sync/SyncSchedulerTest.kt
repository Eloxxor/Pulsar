package com.esusic.recoverytracker.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SyncSchedulerTest {
    @Test
    fun `before the morning hour the delay lands on today`() {
        val delay = SyncScheduler.minutesUntilNextMorning(LocalDateTime.of(2026, 7, 27, 5, 30))

        assertEquals(90L, delay)
    }

    @Test
    fun `after the morning hour the delay rolls over to tomorrow`() {
        val delay = SyncScheduler.minutesUntilNextMorning(LocalDateTime.of(2026, 7, 27, 8, 0))

        assertEquals(23L * 60, delay)
    }

    @Test
    fun `exactly at the morning hour waits a full day`() {
        val delay = SyncScheduler.minutesUntilNextMorning(LocalDateTime.of(2026, 7, 27, 7, 0))

        assertEquals(24L * 60, delay)
    }
}
