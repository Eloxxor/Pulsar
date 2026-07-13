package com.esusic.recoverytracker

import android.app.Application
import androidx.work.Configuration
import com.esusic.recoverytracker.sync.AppWorkerFactory

class RecoveryTrackerApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(AppWorkerFactory { ServiceLocator.repository })
            .build()
}
