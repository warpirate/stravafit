// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.distribution.Distribution
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.UnitSystem
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val preferences = FakePreferencesRepository()
    private val distribution = Distribution("foss", "F-Droid build", "Android LocationManager", "UnifiedPush")

    @Test
    fun `changes are written to preferences and reflected in state`() = runTest(main.dispatcher) {
        val vm = SettingsViewModel(preferences, distribution)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.setUnitSystem(UnitSystem.Imperial)
        vm.setThemeMode(ThemeMode.Dark)
        vm.setReduceMotion(true)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(UnitSystem.Imperial, state.preferences.unitSystem)
        assertEquals(ThemeMode.Dark, state.preferences.themeMode)
        assertTrue(state.preferences.reduceMotion)
        assertEquals(UnitSystem.Imperial, preferences.state.value.unitSystem)
        assertEquals("foss", state.distribution.id)
    }
}
