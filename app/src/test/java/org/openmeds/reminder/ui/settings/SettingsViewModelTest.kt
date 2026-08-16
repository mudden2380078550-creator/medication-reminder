package org.openmeds.reminder.ui.settings

import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.openmeds.reminder.settings.ReminderPreferences
import org.openmeds.reminder.settings.ReminderSettings

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun lowStockTimeDefaultsToNine() = runTest(dispatcher.scheduler) {
        val preferences = FakeReminderPreferences()
        assertEquals(LocalTime.of(9, 0), preferences.flow.first().lowStockTime)
    }

    @Test
    fun togglingSoundPersists() = runTest(dispatcher.scheduler) {
        val preferences = FakeReminderPreferences()
        val viewModel = SettingsViewModel(preferences)
        viewModel.setSound(false)
        assertEquals(false, preferences.flow.first().soundEnabled)
    }

    @Test
    fun togglingVibrationPersists() = runTest(dispatcher.scheduler) {
        val preferences = FakeReminderPreferences()
        val viewModel = SettingsViewModel(preferences)
        viewModel.setVibration(false)
        assertEquals(false, preferences.flow.first().vibrationEnabled)
    }
}

class FakeReminderPreferences : ReminderPreferences {
    private val mutableSettings = MutableStateFlow(ReminderSettings())

    override val flow: Flow<ReminderSettings> = mutableSettings

    override suspend fun updateSound(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(soundEnabled = enabled)
    }

    override suspend fun updateVibration(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(vibrationEnabled = enabled)
    }

    override suspend fun updateLowStockTime(time: LocalTime) {
        mutableSettings.value = mutableSettings.value.copy(lowStockTime = time)
    }
}
