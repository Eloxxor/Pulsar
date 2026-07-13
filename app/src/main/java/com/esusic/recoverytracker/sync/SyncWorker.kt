package com.esusic.recoverytracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.esusic.recoverytracker.data.HealthRepository

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: HealthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        repository.syncToday()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "recovery_sync_periodic"
    }
}
