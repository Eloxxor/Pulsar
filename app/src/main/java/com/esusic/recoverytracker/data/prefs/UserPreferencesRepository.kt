// app/src/main/java/com/esusic/recoverytracker/data/prefs/UserPreferencesRepository.kt
package com.esusic.recoverytracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val birthYear: Flow<Int?> = dataStore.data.map { it[BIRTH_YEAR_KEY] }

    val maxHeartRate: Flow<Double> = birthYear.map { year ->
        if (year == null) {
            DEFAULT_MAX_HR
        } else {
            val age = LocalDate.now().year - year
            (208.0 - 0.7 * age).coerceAtLeast(MIN_MAX_HR)
        }
    }

    suspend fun setBirthYear(year: Int) {
        dataStore.edit { it[BIRTH_YEAR_KEY] = year }
    }

    companion object {
        const val DEFAULT_MAX_HR = 190.0
        const val MIN_MAX_HR = 120.0
        private val BIRTH_YEAR_KEY = intPreferencesKey("birth_year")
    }
}
