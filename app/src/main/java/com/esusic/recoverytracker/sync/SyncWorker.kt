package com.esusic.recoverytracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.widget.RecoveryWidgetProvider

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: HealthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        repository.syncToday()
        RecoveryWidgetProvider.refresh(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "recovery_sync_periodic"
    }
}
