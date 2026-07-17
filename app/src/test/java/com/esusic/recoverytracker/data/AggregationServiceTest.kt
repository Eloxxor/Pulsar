package com.esusic.recoverytracker.data

import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.model.HeartRateSample
import com.esusic.recoverytracker.model.HrvSample
import com.esusic.recoverytracker.model.OxygenSaturationSample
import com.esusic.recoverytracker.model.RespiratoryRateSample
import com.esusic.recoverytracker.model.RestingHrSample
import com.esusic.recoverytracker.model.SkinTemperatureDeltaSample
import com.esusic.recoverytracker.model.SleepSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AggregationServiceTest {
    private val date = LocalDate.of(2026, 7, 13)
    private val zone = ZoneOffset.UTC
    private val dayStart = date.atStartOfDay(zone).toInstant()

    @Test
    fun `averages HRV samples within the day`() = runTest {
        val source = FakeHealthDataSource(
            hrv = listOf(
                HrvSample(dayStart.plusSeconds(3600), 40.0),
                HrvSample(dayStart.plusSeconds(7200), 60.0)
            )
        )
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(50.0, result.avgHrvMillis!!, 0.0001)
    }

    @Test
    fun `avgHrvMillis is null when no HRV samples exist`() = runTest {
        val result = AggregationService(FakeHealthDataSource()).aggregateForDate(date, zone)
        assertNull(result.avgHrvMillis)
    }

    @Test
    fun `restingHeartRate takes the lowest reading of the day`() = runTest {
        val source = FakeHealthDataSource(
            restingHr = listOf(
                RestingHrSample(dayStart.plusSeconds(3600), 55L),
                RestingHrSample(dayStart.plusSeconds(7200), 48L)
            )
        )
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(48L, result.restingHeartRate)
    }

    @Test
    fun `sleep picks the longest session overlapping the day`() = runTest {
        val short = SleepSession(dayStart.plusSeconds(1000), dayStart.plusSeconds(2000), emptyList())
        val long = SleepSession(dayStart, dayStart.plusSeconds(20000), emptyList())
        val source = FakeHealthDataSource(sleep = listOf(short, long))
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(long, result.sleep)
    }

    @Test
    fun `cross-midnight sleep session is attributed to the wake-up day, not the day it started`() = runTest {
        // Person goes to bed at 23:30 the night before and wakes up at 07:00 on the target date.
        // The session's `start` instant falls on the previous calendar day, but it should still
        // be selected as the target date's sleep because that's the day the person woke up.
        val previousNightStart = dayStart.minusSeconds(30 * 60) // 23:30 the day before
        val wakeUpEnd = dayStart.plusSeconds(7 * 3600) // 07:00 on the target date
        val crossMidnightSession = SleepSession(previousNightStart, wakeUpEnd, emptyList())
        val source = FakeHealthDataSource(sleep = listOf(crossMidnightSession))

        val result = AggregationService(source).aggregateForDate(date, zone)

        assertEquals(crossMidnightSession, result.sleep)
    }

    @Test
    fun `isComplete is true only when both heart rate samples and sleep are present`() = runTest {
        val complete = AggregationService(
            FakeHealthDataSource(
                heartRate = listOf(HeartRateSample(dayStart, 60L)),
                sleep = listOf(SleepSession(dayStart, dayStart.plusSeconds(100), emptyList()))
            )
        ).aggregateForDate(date, zone)
        val incomplete = AggregationService(FakeHealthDataSource()).aggregateForDate(date, zone)

        assertTrue(complete.isComplete)
        assertTrue(!incomplete.isComplete)
    }

    @Test
    fun `averages respiratory rate samples within the day`() = runTest {
        val source = FakeHealthDataSource(
            respiratoryRate = listOf(
                RespiratoryRateSample(dayStart.plusSeconds(3600), 12.0),
                RespiratoryRateSample(dayStart.plusSeconds(7200), 16.0)
            )
        )
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(14.0, result.avgRespiratoryRate!!, 0.0001)
    }

    @Test
    fun `avgRespiratoryRate is null when no respiratory rate samples exist`() = runTest {
        val result = AggregationService(FakeHealthDataSource()).aggregateForDate(date, zone)
        assertNull(result.avgRespiratoryRate)
    }

    @Test
    fun `averages oxygen saturation samples within the day`() = runTest {
        val source = FakeHealthDataSource(
            oxygenSaturation = listOf(
                OxygenSaturationSample(dayStart.plusSeconds(3600), 96.0),
                OxygenSaturationSample(dayStart.plusSeconds(7200), 98.0)
            )
        )
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(97.0, result.avgOxygenSaturationPercent!!, 0.0001)
    }

    @Test
    fun `averages skin temperature delta samples within the day`() = runTest {
        val source = FakeHealthDataSource(
            skinTemperatureDeltas = listOf(
                SkinTemperatureDeltaSample(dayStart.plusSeconds(3600), -0.4),
                SkinTemperatureDeltaSample(dayStart.plusSeconds(7200), 0.2)
            )
        )
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(-0.1, result.avgSkinTemperatureDeltaCelsius!!, 0.0001)
    }

    @Test
    fun `skin temperature stamped at bedtime before midnight counts toward the wake-up day`() = runTest {
        // Fitbit writes one skin-temp record per night, stamped at bedtime — usually before
        // midnight, i.e. on the previous calendar day.
        val bedtimeSample = SkinTemperatureDeltaSample(dayStart.minusSeconds(2 * 3600), 0.5) // 22:00 the evening before
        val previousNightSample = SkinTemperatureDeltaSample(dayStart.minusSeconds(20 * 3600), -0.3) // 04:00 the previous day
        val source = FakeHealthDataSource(skinTemperatureDeltas = listOf(bedtimeSample, previousNightSample))

        val result = AggregationService(source).aggregateForDate(date, zone)

        assertEquals(0.5, result.avgSkinTemperatureDeltaCelsius!!, 0.0001)
    }

    @Test
    fun `oxygen saturation stamped at bedtime before midnight counts toward the wake-up day`() = runTest {
        val bedtimeSample = OxygenSaturationSample(dayStart.minusSeconds(2 * 3600), 95.0) // 22:00 the evening before
        val source = FakeHealthDataSource(oxygenSaturation = listOf(bedtimeSample))

        val result = AggregationService(source).aggregateForDate(date, zone)

        assertEquals(95.0, result.avgOxygenSaturationPercent!!, 0.0001)
    }

    @Test
    fun `latestHeartRateSample picks the most recent heart rate sample of the day`() = runTest {
        val earlier = HeartRateSample(dayStart.plusSeconds(3600), 65L)
        val later = HeartRateSample(dayStart.plusSeconds(7200), 72L)
        val source = FakeHealthDataSource(heartRate = listOf(earlier, later))
        val result = AggregationService(source).aggregateForDate(date, zone)
        assertEquals(later, result.latestHeartRateSample)
    }

    @Test
    fun `heartRateSamplesForSession queries the session's exact window, not the calendar day`() = runTest {
        val sessionStart = dayStart.minusSeconds(3600) // 23:00 the previous day, outside the calendar-day window
        val sessionEnd = dayStart.plusSeconds(3600)
        val sampleInWindow = HeartRateSample(sessionStart.plusSeconds(60), 55L)
        val sampleOutsideWindow = HeartRateSample(sessionEnd.plusSeconds(3600), 90L)
        val source = FakeHealthDataSource(heartRate = listOf(sampleInWindow, sampleOutsideWindow))
        val session = SleepSession(sessionStart, sessionEnd, emptyList())

        val result = AggregationService(source).heartRateSamplesForSession(session)

        assertEquals(listOf(sampleInWindow), result)
    }
}
