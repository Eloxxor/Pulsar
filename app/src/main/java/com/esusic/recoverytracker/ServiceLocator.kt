package com.esusic.recoverytracker

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.room.Room
import com.esusic.recoverytracker.data.AggregationService
import com.esusic.recoverytracker.data.HealthRepository
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.healthconnect.HealthConnectDataSource
import com.esusic.recoverytracker.data.healthconnect.HealthDataSource
import com.esusic.recoverytracker.data.healthconnect.checkHealthConnectAvailability
import com.esusic.recoverytracker.data.healthconnect.HealthConnectAvailability
import com.esusic.recoverytracker.data.local.AppDatabase
import com.esusic.recoverytracker.data.local.DailyScoreDao
import com.esusic.recoverytracker.data.local.JournalEntryDao
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import com.esusic.recoverytracker.data.prefs.userPreferencesDataStore

object ServiceLocator {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "recovery_tracker.db")
            .addMigrations(
                com.esusic.recoverytracker.data.local.MIGRATION_3_4,
                com.esusic.recoverytracker.data.local.MIGRATION_4_5
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    val journalEntryDao: JournalEntryDao by lazy { database.journalEntryDao() }

    val dailyScoreDao: DailyScoreDao by lazy { database.dailyScoreDao() }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext.userPreferencesDataStore)
    }

    val healthDataSource: HealthDataSource by lazy {
        if (checkHealthConnectAvailability(appContext) is HealthConnectAvailability.Available) {
            HealthConnectDataSource(HealthConnectClient.getOrCreate(appContext))
        } else {
            FakeHealthDataSource()
        }
    }

    val repository: HealthRepository by lazy {
        HealthRepository(
            aggregationService = AggregationService(healthDataSource),
            scoreDao = database.dailyScoreDao(),
            aggregateDao = database.dailyAggregateDao(),
            userPreferences = userPreferencesRepository
        )
    }
}
