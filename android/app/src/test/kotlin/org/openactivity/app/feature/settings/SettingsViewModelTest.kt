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
import org.openactivity.app.data.backup.AutoBackupConfig
import org.openactivity.app.data.backup.AutoBackupFailure
import org.openactivity.app.data.backup.AutoBackupFrequency
import org.openactivity.app.data.backup.AutoBackupState
import org.openactivity.app.data.backup.AutoBackupStatus
import org.openactivity.app.testing.FakeAutoBackupSettings
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakePrivacyZoneRepository
import org.openactivity.app.testing.FakeSettingsRepository
import org.openactivity.app.testing.FakeSuggestionSettings
import org.openactivity.app.testing.MainDispatcherRule
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant
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

        override suspend fun backUp(output: java.io.OutputStream): BackupManifest {
            calls += "backUpStream"
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

    private val autoBackup = FakeAutoBackupSettings()
    private val zones = FakePrivacyZoneRepository()

    private fun TestScope.viewModel(): SettingsViewModel {
        val vm = SettingsViewModel(
            settings,
            preferences,
            exporter,
            distribution,
            FakeSuggestionSettings(),
            backup,
            { recording },
            autoBackup,
            zones,
        )
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

    @Test
    fun `automatic backup settings reach the store and show in state`() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertFalse(vm.uiState.value.autoBackup.enabled)
        assertFalse(vm.uiState.value.autoBackup.hasFolder)
        assertEquals(SettingsViewModel.NO_AUTO_BACKUP_YET, vm.uiState.value.autoBackup.status)

        vm.chooseAutoBackupFolder(Uri.parse("content://tree/backups"), enable = true)
        advanceUntilIdle()
        val on = vm.uiState.value.autoBackup
        assertTrue(on.enabled)
        assertEquals("Backups", on.folderName)
        assertEquals(AutoBackupFrequency.WEEKLY, on.frequency)

        vm.setAutoBackupFrequency(AutoBackupFrequency.DAILY)
        vm.setAutoBackupEnabled(false)
        advanceUntilIdle()
        assertEquals(AutoBackupFrequency.DAILY, vm.uiState.value.autoBackup.frequency)
        assertFalse(vm.uiState.value.autoBackup.enabled)
        // The folder stays chosen, so switching back on needs no picker.
        assertTrue(vm.uiState.value.autoBackup.hasFolder)
    }

    @Test
    fun `back up now needs a folder and refuses a second run`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.backUpNowToFolder()
        advanceUntilIdle()
        assertEquals("Choose a folder for automatic backups first", vm.uiState.value.message)
        assertTrue(autoBackup.calls.none { it == "now" })
        vm.messageShown()

        autoBackup.store.configState.value = AutoBackupConfig(enabled = true, folderUri = "content://tree/x", folderName = "Drive")
        advanceUntilIdle()
        vm.backUpNowToFolder()
        advanceUntilIdle()
        assertEquals("Backing up to Drive", vm.uiState.value.message)
        assertEquals(1, autoBackup.calls.count { it == "now" })
        vm.messageShown()

        autoBackup.running.value = true
        advanceUntilIdle()
        assertTrue(vm.uiState.value.autoBackup.running)
        vm.backUpNowToFolder()
        advanceUntilIdle()
        assertEquals(SettingsViewModel.ALREADY_RUNNING, vm.uiState.value.message)
        assertEquals(1, autoBackup.calls.count { it == "now" })
    }

    @Test
    fun `automatic backup status says when it worked and what to do when it did not`() {
        val utc = ZoneOffset.UTC
        val folder = AutoBackupConfig(enabled = true, folderUri = "content://tree/x", folderName = "Backups")
        val ok = SettingsViewModel.autoBackupUi(
            AutoBackupState(folder, AutoBackupStatus(lastSuccessAt = Instant.parse("2026-11-03T08:12:00Z"), lastActivityCount = 12)),
            utc,
        )
        assertFalse(ok.failed)
        assertTrue(ok.status, ok.status.startsWith("Last backup 3 Nov"))
        assertTrue(ok.status, ok.status.endsWith("12 activities"))

        val failed = SettingsViewModel.autoBackupUi(
            AutoBackupState(
                folder,
                AutoBackupStatus(
                    lastSuccessAt = Instant.parse("2026-10-27T08:00:00Z"),
                    lastFailureAt = Instant.parse("2026-11-03T08:00:00Z"),
                    lastFailure = AutoBackupFailure.FOLDER_REVOKED,
                ),
            ),
            utc,
        )
        assertTrue(failed.failed)
        assertTrue(failed.status, failed.status.startsWith("Last backup failed on 3 Nov"))
        assertTrue(failed.status, failed.status.endsWith(AutoBackupFailure.FOLDER_REVOKED.message))

        val running = SettingsViewModel.autoBackupUi(AutoBackupState(folder, AutoBackupStatus(lastFailure = AutoBackupFailure.NO_SPACE), running = true), utc)
        assertFalse(running.failed)
        assertEquals("Backing up now…", running.status)

        // A folder grant without a stored name still shows that a folder is chosen.
        assertEquals("Chosen folder", SettingsViewModel.autoBackupUi(AutoBackupState(folder.copy(folderName = null)), utc).folderName)
    }

    @Test
    fun `per-sport audience and the zone count show in state`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.setVisibilityOverride(SportType.RIDE, Visibility.ONLY_ME)
        zones.add("Home", org.openactivity.model.LatLng(18.5, 73.8), 400)
        advanceUntilIdle()
        assertEquals(Visibility.ONLY_ME, vm.uiState.value.settings.defaultVisibility(SportType.RIDE))
        assertEquals(1, vm.uiState.value.privacyZoneCount)
        vm.setVisibilityOverride(SportType.RIDE, null)
        advanceUntilIdle()
        assertEquals(vm.uiState.value.settings.defaultVisibility, vm.uiState.value.settings.defaultVisibility(SportType.RIDE))
    }
}
