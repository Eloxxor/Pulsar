package com.esusic.recoverytracker

import android.app.Application
import androidx.work.Configuration
import com.esusic.recoverytracker.sync.AppWorkerFactory
import com.esusic.recoverytracker.sync.RecoveryNotifications
import com.esusic.recoverytracker.sync.SyncScheduler

class RecoveryTrackerApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        RecoveryNotifications.createChannels(this)
        // Onboarding schedules the sync, but the morning notification also has to reach
        // users who onboarded before it existed, so it is enqueued on every launch.
        SyncScheduler.scheduleMorningNotification(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory { ServiceLocator.repository })
            .build()
}
