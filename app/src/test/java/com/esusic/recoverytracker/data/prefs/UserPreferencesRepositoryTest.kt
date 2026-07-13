// app/src/test/java/com/esusic/recoverytracker/data/prefs/UserPreferencesRepositoryTest.kt
package com.esusic.recoverytracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class UserPreferencesRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newDataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        return PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("test.preferences_pb")
        }
    }

    @Test
    fun `birthYear defaults to null`() = runTest {
        val repo = UserPreferencesRepository(newDataStore())
        assertNull(repo.birthYear.first())
    }

    @Test
    fun `setBirthYear persists and is readable`() = runTest {
        val repo = UserPreferencesRepository(newDataStore())
        repo.setBirthYear(1996)
        assertEquals(1996, repo.birthYear.first())
    }

    @Test
    fun `maxHeartRate uses default when birthYear unset`() = runTest {
        val repo = UserPreferencesRepository(newDataStore())
        assertEquals(UserPreferencesRepository.DEFAULT_MAX_HR, repo.maxHeartRate.first(), 0.001)
    }

    @Test
    fun `maxHeartRate derives from birthYear via Tanaka formula`() = runTest {
        val repo = UserPreferencesRepository(newDataStore())
        val currentYear = LocalDate.now().year
        val age = 30
        repo.setBirthYear(currentYear - age)
        val expected = 208.0 - 0.7 * age
        assertEquals(expected, repo.maxHeartRate.first(), 0.001)
    }
}
