package com.esusic.recoverytracker.sync

import android.content.Context
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.esusic.recoverytracker.data.HealthRepository

class AppWorkerFactory(private val repositoryProvider: () -> HealthRepository) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, repositoryProvider())
        MorningNotificationWorker::class.java.name ->
            MorningNotificationWorker(appContext, workerParameters, repositoryProvider())
        else -> null
    }
}
