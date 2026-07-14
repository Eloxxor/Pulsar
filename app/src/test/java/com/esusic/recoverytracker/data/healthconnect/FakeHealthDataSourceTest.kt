package com.esusic.recoverytracker.data.healthconnect

import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
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

    @Test
    fun `filters respiratory rate samples to the requested time range`() = runTest {
        val base = Instant.parse("2026-07-13T00:00:00Z")
        val source = FakeHealthDataSource(
            respiratoryRate = listOf(
                RespiratoryRateSample(base.minus(1, ChronoUnit.DAYS), 12.0),
                RespiratoryRateSample(base.plus(1, ChronoUnit.HOURS), 14.0)
            )
        )
        val result = source.respiratoryRateSamples(base, base.plus(1, ChronoUnit.DAYS))
        assertEquals(listOf(14.0), result.map { it.breathsPerMinute })
    }

    @Test
    fun `filters oxygen saturation samples to the requested time range`() = runTest {
        val base = Instant.parse("2026-07-13T00:00:00Z")
        val source = FakeHealthDataSource(
            oxygenSaturation = listOf(
                OxygenSaturationSample(base.minus(1, ChronoUnit.DAYS), 90.0),
                OxygenSaturationSample(base.plus(1, ChronoUnit.HOURS), 97.0)
            )
        )
        val result = source.oxygenSaturationSamples(base, base.plus(1, ChronoUnit.DAYS))
        assertEquals(listOf(97.0), result.map { it.percentage })
    }

    @Test
    fun `filters skin temperature delta samples to the requested time range`() = runTest {
        val base = Instant.parse("2026-07-13T00:00:00Z")
        val source = FakeHealthDataSource(
            skinTemperatureDeltas = listOf(
                SkinTemperatureDeltaSample(base.minus(1, ChronoUnit.DAYS), -1.0),
                SkinTemperatureDeltaSample(base.plus(1, ChronoUnit.HOURS), 0.3)
            )
        )
        val result = source.skinTemperatureDeltaSamples(base, base.plus(1, ChronoUnit.DAYS))
        assertEquals(listOf(0.3), result.map { it.deltaCelsius })
    }
}
