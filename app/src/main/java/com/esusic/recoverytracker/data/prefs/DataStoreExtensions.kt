// app/src/main/java/com/esusic/recoverytracker/data/prefs/DataStoreExtensions.kt
package com.esusic.recoverytracker.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")
