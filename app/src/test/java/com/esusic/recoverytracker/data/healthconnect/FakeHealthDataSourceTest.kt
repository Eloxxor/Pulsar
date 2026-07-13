package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.HeartRateSample
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class FakeHealthDataSourceTest {
    @Test
    fun `filters samples to the requested time range`() = runTest {
        val base = Instant.parse("2026-07-13T00:00:00Z")
        val source = FakeHealthDataSource(
            heartRate = listOf(
                HeartRateSample(base.minus(1, ChronoUnit.DAYS), 60L),
                HeartRateSample(base.plus(1, ChronoUnit.HOURS), 70L),
                HeartRateSample(base.plus(1, ChronoUnit.DAYS), 80L)
            )
        )
        val result = source.heartRateSamples(base, base.plus(1, ChronoUnit.DAYS))
        assertEquals(listOf(70L), result.map { it.bpm })
    }

    @Test
    fun `hasAllPermissions reflects configured value`() = runTest {
        assertEquals(false, FakeHealthDataSource(permissionsGranted = false).hasAllPermissions())
        assertEquals(true, FakeHealthDataSource(permissionsGranted = true).hasAllPermissions())
    }
}
