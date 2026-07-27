package com.esusic.recoverytracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.esusic.recoverytracker.data.HealthRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class MorningNotificationWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: HealthRepository
) : CoroutineWorker(context, params) {

    // Every skip path is a success: retrying would either re-check a permission the
    // user has not granted or re-read a score the overnight sync has not written yet.
    override suspend fun doWork(): Result {
        if (!RecoveryNotifications.canPost(applicationContext)) return Result.success()
        val today = LocalDate.now().toString()
        val score = repository.observeRecentScores(1).first().firstOrNull()
        if (score == null || score.date != today) return Result.success()
        RecoveryNotifications.postMorningSummary(
            applicationContext,
            score.recoveryValue,
            score.recoveryIsCalibrating
        )
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "recovery_morning_notification_periodic"
    }
}
