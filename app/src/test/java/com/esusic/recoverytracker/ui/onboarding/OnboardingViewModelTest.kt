package com.esusic.recoverytracker.ui.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newUserPreferences() = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
            tempFolder.newFile("test.preferences_pb")
        }
    )

    @Test
    fun `onBirthYearEntered persists the year and updates uiState`() = runTest {
        val userPreferences = newUserPreferences()
        val viewModel = OnboardingViewModel(userPreferences, FakeHealthDataSource())

        viewModel.onBirthYearEntered(1996)

        assertEquals(1996, userPreferences.birthYear.first())
        assertEquals(1996, viewModel.uiState.value.birthYear)
    }

    @Test
    fun `refreshPermissionStatus reflects the data source's granted state`() = runTest {
        val viewModel = OnboardingViewModel(newUserPreferences(), FakeHealthDataSource(permissionsGranted = true))
        viewModel.refreshPermissionStatus()
        assertTrue(viewModel.uiState.value.hasAllPermissions)
    }
}
