// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import android.net.Uri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.data.export.ActivityArchiveExporter
import org.openactivity.app.data.export.ArchiveSummary
import org.openactivity.app.distribution.Distribution
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val preferences = FakePreferencesRepository()
    private val distribution = Distribution("foss", "F-Droid build", "Android LocationManager", "UnifiedPush")
    private var exportResult: Result<ArchiveSummary> = Result.success(ArchiveSummary(3, 1))
    private val exports = mutableListOf<Uri>()
    private val exporter = object : ActivityArchiveExporter {
        override suspend fun exportAll(destination: Uri): ArchiveSummary {
            exports += destination
            return exportResult.getOrThrow()
        }
    }

    private fun TestScope.viewModel(): SettingsViewModel {
        val vm = SettingsViewModel(settings, preferences, exporter, distribution)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `changes are written to the settings store and reflected in state`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.setUnits(UnitSystem.IMPERIAL)
        vm.setTheme(ThemeMode.Dark)
        vm.setReduceMotion(true)
        vm.setDefaultSport(SportType.RIDE)
        vm.setAutoPause(SportType.RUN, true)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(UnitSystem.IMPERIAL, s.settings.units)
        assertEquals(ThemePreference.DARK, s.settings.theme)
        assertTrue(s.reduceMotion)
        assertEquals(SportType.RIDE, s.settings.defaultSport)
        assertTrue(s.settings.autoPauseEnabled(SportType.RUN))
    }

    @Test
    fun `audio cues and privacy keep the other fields`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.setCueInterval(2.0)
        advanceUntilIdle()
        vm.setAudioCuesEnabled(false)
        vm.setHideStart(400.0)
        advanceUntilIdle()
        vm.setHideEnd(0.0)
        vm.setDefaultVisibility(Visibility.ONLY_ME)
        advanceUntilIdle()
        val s = vm.uiState.value.settings
        assertFalse(s.audioCues.enabled)
        assertEquals(2.0, s.audioCues.distanceInterval, 0.0)
        assertEquals(400.0, s.privacy.hideStartMeters, 0.0)
        assertEquals(0.0, s.privacy.hideEndMeters, 0.0)
        assertEquals(Visibility.ONLY_ME, s.defaultVisibility)
    }

    @Test
    fun `export all reports exported and skipped activities`() = runTest(main.dispatcher) {
        val vm = viewModel()
        val uri = Uri.parse("content://docs/activities.zip")
        vm.exportAll(uri)
        advanceUntilIdle()
        assertEquals(listOf(uri), exports)
        assertEquals("Exported 3 activities. 1 indoor activity has no track and was skipped", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isExporting)

        exportResult = Result.failure(IOException("no space"))
        vm.exportAll(uri)
        advanceUntilIdle()
        assertEquals("Export failed: no space", vm.uiState.value.message)
    }

    @Test
    fun `export messages`() {
        assertEquals("No activities with a GPS track to export", SettingsViewModel.exportMessage(ArchiveSummary(0, 0)))
        assertEquals("Exported 1 activity", SettingsViewModel.exportMessage(ArchiveSummary(1, 0)))
    }
}
