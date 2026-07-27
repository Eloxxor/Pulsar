package com.esusic.recoverytracker.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val MORNING_HOUR = 7

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SyncWorker.UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun triggerImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    // KEEP rather than UPDATE: re-enqueueing on every launch would push the initial
    // delay forward each time and the morning run could never come due.
    fun scheduleMorningNotification(context: Context) {
        val request = PeriodicWorkRequestBuilder<MorningNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(minutesUntilNextMorning(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MorningNotificationWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun minutesUntilNextMorning(now: LocalDateTime = LocalDateTime.now()): Long {
        val todayMorning = now.toLocalDate().atTime(MORNING_HOUR, 0)
        val next = if (now.isBefore(todayMorning)) todayMorning else todayMorning.plusDays(1)
        return Duration.between(now, next).toMinutes()
    }
}
