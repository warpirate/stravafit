// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.record

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.openactivity.app.data.GpsFix
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.model.Sport
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.testing.FakeGpsStatusSource
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.core.designsystem.component.RecordButtonState

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val preferences = FakePreferencesRepository(UserPreferences(lastSport = Sport.Ride))
    private val gps = FakeGpsStatusSource()

    @Test
    fun `preselects the last used sport with ride defaults`() = runTest(main.dispatcher) {
        val vm = RecordViewModel(preferences, gps)
        runCurrent()
        val state = vm.uiState.value
        assertEquals(Sport.Ride, state.sport)
        assertTrue("auto-pause defaults on for rides", state.autoPause)
        assertEquals(Sport.Ride, state.recentSports.first())
    }

    @Test
    fun `gps becomes ready at 20 m or better`() = runTest(main.dispatcher) {
        val vm = RecordViewModel(preferences, gps)
        runCurrent()
        assertEquals(GpsState.Acquiring(null), vm.uiState.value.gps)
        assertEquals(RecordButtonState.Acquiring, vm.uiState.value.startButtonState)

        gps.state.value = GpsFix(24f)
        runCurrent()
        assertEquals(GpsState.Acquiring(24f), vm.uiState.value.gps)

        gps.state.value = GpsFix(20f)
        runCurrent()
        assertEquals(GpsState.Ready(20f), vm.uiState.value.gps)
        assertEquals(RecordButtonState.Ready, vm.uiState.value.startButtonState)
    }

    @Test
    fun `slow fix hint appears after 20 seconds and clears on fix`() = runTest(main.dispatcher) {
        val vm = RecordViewModel(preferences, gps)
        runCurrent()
        advanceTimeBy(RecordViewModel.SLOW_FIX_HINT_MS - 1)
        runCurrent()
        assertFalse(vm.uiState.value.showSlowFixHint)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(vm.uiState.value.showSlowFixHint)

        gps.state.value = GpsFix(8f)
        runCurrent()
        assertFalse(vm.uiState.value.showSlowFixHint)
    }

    @Test
    fun `indoor sport turns gps off and start is ready`() = runTest(main.dispatcher) {
        val vm = RecordViewModel(preferences, gps)
        runCurrent()
        vm.selectSport(Sport.Yoga)
        runCurrent()
        assertEquals(GpsState.Indoor, vm.uiState.value.gps)
        assertEquals(RecordButtonState.Ready, vm.uiState.value.startButtonState)
        assertFalse(vm.uiState.value.showSlowFixHint)
    }

    @Test
    fun `selecting a sport closes the picker and is remembered`() = runTest(main.dispatcher) {
        val vm = RecordViewModel(preferences, gps)
        runCurrent()
        vm.setSportPickerOpen(true)
        assertTrue(vm.uiState.value.isSportPickerOpen)

        vm.selectSport(Sport.Hike)
        runCurrent()
        assertFalse(vm.uiState.value.isSportPickerOpen)
        assertEquals(Sport.Hike, vm.uiState.value.sport)
        assertFalse("auto-pause defaults off for hikes", vm.uiState.value.autoPause)
        assertEquals(Sport.Hike, preferences.state.value.lastSport)
    }

    @Test
    fun `quick settings toggle`() = runTest(main.dispatcher) {
        val vm = RecordViewModel(preferences, gps)
        runCurrent()
        val audio = vm.uiState.value.audioCues
        vm.toggleAudioCues()
        assertEquals(!audio, vm.uiState.value.audioCues)
        val autoPause = vm.uiState.value.autoPause
        vm.toggleAutoPause()
        assertEquals(!autoPause, vm.uiState.value.autoPause)
    }

    @Test
    fun `a sport picked before stored preferences load is not overwritten`() = runTest(main.dispatcher) {
        val slowPreferences = object : PreferencesRepository by preferences {
            override val preferences: Flow<UserPreferences> = flow {
                delay(PREFERENCES_LOAD_MS)
                emit(UserPreferences(lastSport = Sport.Ride))
            }
        }
        val vm = RecordViewModel(slowPreferences, gps)
        runCurrent()
        vm.selectSport(Sport.Swim)
        advanceTimeBy(PREFERENCES_LOAD_MS + 1)
        runCurrent()
        assertEquals(Sport.Swim, vm.uiState.value.sport)
    }

    private companion object {
        const val PREFERENCES_LOAD_MS = 500L
    }
}
