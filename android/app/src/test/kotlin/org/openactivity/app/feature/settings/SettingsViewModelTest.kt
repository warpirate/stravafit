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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.data.backup.BackupException
import org.openactivity.app.data.backup.BackupManifest
import org.openactivity.app.data.backup.DataBackup
import org.openactivity.app.data.export.ActivityArchiveExporter
import org.openactivity.app.data.export.ArchiveSummary
import org.openactivity.app.distribution.Distribution
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.FakeSuggestionSettings
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.ZoneOffset

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

    private val manifest = BackupManifest(1, "0.2.0", 2, "2026-11-03T06:00:00Z", 12, "/data/user/0/x/files")
    private var recording = false
    private val backup = object : DataBackup {
        val calls = mutableListOf<String>()
        var stageResult: Result<BackupManifest> = Result.success(manifest)

        override suspend fun backUp(destination: Uri): BackupManifest {
            calls += "backUp"
            return manifest
        }

        override suspend fun stageRestore(source: Uri): BackupManifest {
            calls += "stage"
            return stageResult.getOrThrow()
        }

        override suspend fun discardRestore() {
            calls += "discard"
        }

        override suspend fun restoreAndRestart() {
            calls += "restore"
        }
    }

    private fun TestScope.viewModel(): SettingsViewModel {
        val vm = SettingsViewModel(
            settings,
            preferences,
            exporter,
            distribution,
            FakeSuggestionSettings(),
            backup,
        ) { recording }
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

    @Test
    fun `back up reports what was saved`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.backUp(Uri.parse("content://docs/backup.zip"))
        advanceUntilIdle()
        assertEquals(listOf("backUp"), backup.calls)
        assertEquals("Backed up 12 activities with photos, goals and settings", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isBackupBusy)
    }

    @Test
    fun `restore asks first and only replaces data on confirm`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.chooseRestore(Uri.parse("content://docs/backup.zip"))
        advanceUntilIdle()
        assertEquals(12, vm.uiState.value.restorePreview?.activityCount)
        assertEquals(listOf("stage"), backup.calls)

        vm.cancelRestore()
        advanceUntilIdle()
        assertNull(vm.uiState.value.restorePreview)
        assertEquals(listOf("stage", "discard"), backup.calls)

        vm.chooseRestore(Uri.parse("content://docs/backup.zip"))
        advanceUntilIdle()
        vm.confirmRestore()
        advanceUntilIdle()
        assertEquals(listOf("stage", "discard", "stage", "restore"), backup.calls)
    }

    @Test
    fun `a bad backup shows why and asks nothing`() = runTest(main.dispatcher) {
        backup.stageResult = Result.failure(BackupException("This file is not a StravaFit backup"))
        val vm = viewModel()
        vm.chooseRestore(Uri.parse("content://docs/run.zip"))
        advanceUntilIdle()
        assertNull(vm.uiState.value.restorePreview)
        assertEquals("This file is not a StravaFit backup", vm.uiState.value.message)
    }

    @Test
    fun `restore is blocked while a recording is unsaved`() = runTest(main.dispatcher) {
        recording = true
        val vm = viewModel()
        vm.chooseRestore(Uri.parse("content://docs/backup.zip"))
        advanceUntilIdle()
        assertEquals(SettingsViewModel.RECORDING_BLOCKS_RESTORE, vm.uiState.value.message)
        assertTrue(backup.calls.isEmpty())

        // A recording started while the confirmation was open also wins.
        recording = false
        vm.chooseRestore(Uri.parse("content://docs/backup.zip"))
        advanceUntilIdle()
        recording = true
        vm.confirmRestore()
        advanceUntilIdle()
        assertEquals(listOf("stage", "discard"), backup.calls)
        assertNull(vm.uiState.value.restorePreview)
    }

    @Test
    fun `restore preview shows the local backup date`() {
        val preview = SettingsViewModel.preview(manifest, ZoneOffset.ofHoursMinutes(5, 30))
        assertEquals(12, preview.activityCount)
        assertTrue(preview.createdOn, preview.createdOn.startsWith("3 "))
        assertEquals("an unknown date", SettingsViewModel.preview(manifest.copy(createdAt = "?")).createdOn)
    }
}
