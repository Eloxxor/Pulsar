package com.esusic.recoverytracker.ui.account

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.esusic.recoverytracker.data.healthconnect.FakeHealthDataSource
import com.esusic.recoverytracker.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccountViewModelTest {
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

    @Test
    fun `uiState exposes birth year derived age and Health Connect status`() = runTest {
        val preferences = preferences("account-state.preferences_pb")
        preferences.setBirthYear(1996)
        val viewModel = AccountViewModel(
            userPreferences = preferences,
            healthDataSource = FakeHealthDataSource(permissionsGranted = true),
            clock = fixedClock()
        )

        val state = viewModel.uiState.filter { !it.isLoading }.first()

        assertEquals(1996, state.birthYear)
        assertEquals(30, state.age)
        assertTrue(state.healthConnectConnected)
    }

    @Test
    fun `saveBirthYear persists valid value and ignores future years`() = runTest {
        val preferences = preferences("account-save.preferences_pb")
        val viewModel = AccountViewModel(
            userPreferences = preferences,
            healthDataSource = FakeHealthDataSource(permissionsGranted = false),
            clock = fixedClock()
        )

        viewModel.saveBirthYear(1990)
        assertEquals(1990, preferences.birthYear.filter { it != null }.first())

        viewModel.saveBirthYear(2030)
        assertEquals(1990, preferences.birthYear.first())
        assertFalse(viewModel.uiState.value.healthConnectConnected)
    }

    private fun preferences(fileName: String) = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher())) {
            tempFolder.newFile(fileName)
        }
    )

    private fun fixedClock(): Clock = Clock.fixed(
        Instant.parse("2026-07-19T12:00:00Z"),
        ZoneOffset.UTC
    )
}
