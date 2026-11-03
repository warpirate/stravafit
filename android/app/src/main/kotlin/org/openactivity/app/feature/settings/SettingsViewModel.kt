// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.BuildConfig
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.SuggestionSettings
import org.openactivity.app.data.backup.BackupException
import org.openactivity.app.data.backup.BackupManifest
import org.openactivity.app.data.backup.DataBackup
import org.openactivity.app.data.backup.UnsavedRecordingCheck
import org.openactivity.app.data.export.ActivityArchiveExporter
import org.openactivity.app.data.export.ArchiveSummary
import org.openactivity.app.distribution.Distribution
import org.openactivity.app.model.Sport
import org.openactivity.app.model.ThemeMode
import org.openactivity.app.model.toPreference
import org.openactivity.app.model.toSportType
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.datastore.LaunchTab
import org.openactivity.core.datastore.AutoLapChoice
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject

/**
 * State of the Settings screen.
 *
 * @property settings every stored setting (units, theme, recording, audio cues, privacy).
 * @property message one-shot snackbar text, cleared with [SettingsViewModel.messageShown].
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val reduceMotion: Boolean = false,
    /** Online route suggestions on Home and Maps. */
    val routeSuggestions: Boolean = true,
    val distribution: Distribution,
    val versionName: String,
    val isExporting: Boolean = false,
    /** A backup is being written or a chosen backup is being read. */
    val isBackupBusy: Boolean = false,
    /** A checked backup waiting for the user to confirm the restore. */
    val restorePreview: RestorePreview? = null,
    val message: String? = null,
) {
    /** Suggested name for the data export archive. */
    val exportFileName: String get() = "activities-${LocalDate.now()}.zip"

    /** Suggested name for a full backup. */
    val backupFileName: String get() = "stravafit-backup-${LocalDate.now()}.zip"

    companion object {
        /** Sports offered as the default sport, the same set the Record sport picker offers. */
        val SPORTS: List<SportType> = Sport.entries.map { it.toSportType() }

        /** GPS sports that get an auto-pause switch. */
        val AUTO_PAUSE_SPORTS: List<SportType> = SPORTS.filter { it.usesGps }

        /** Audio cue intervals in kilometres or miles. */
        val CUE_INTERVALS: List<Double> = AudioCueSettings.DISTANCE_INTERVALS

        /** Time readout intervals in minutes; 0 is off. */
        val CUE_TIME_INTERVALS: List<Int> = AudioCueSettings.TIME_INTERVALS

        /** Distance auto-lap choices in metres; 0 is off (400 m is one track lap). */
        val AUTO_LAP_METERS: List<Double> = listOf(0.0, 400.0, AutoLapChoice.KILOMETER_METERS, AutoLapChoice.MILE_METERS, 5_000.0)

        /** Start/end hiding radii in metres (settings-privacy-units 7.2: 0 to 1,600 m). */
        val HIDE_RADII: List<Double> = listOf(0.0, 100.0, 200.0, 400.0, 800.0, 1600.0)
    }
}

/**
 * What the restore confirmation shows about a chosen backup.
 *
 * @property createdOn local date the backup was made, e.g. "3 Nov 2026".
 */
data class RestorePreview(val createdOn: String, val activityCount: Int)

/** Settings over the `:core:data` [SettingsRepository], plus reduced motion, the data export and backups. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val archiveExporter: ActivityArchiveExporter,
    distribution: Distribution,
    private val suggestionSettings: SuggestionSettings,
    private val backup: DataBackup,
    private val unsavedRecording: UnsavedRecordingCheck,
) : ViewModel() {

    private val local = MutableStateFlow(Local())
    private val initial = SettingsUiState(distribution = distribution, versionName = BuildConfig.VERSION_NAME)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        preferencesRepository.preferences,
        local,
        suggestionSettings.state,
    ) { settings, preferences, local, suggestions ->
        initial.copy(
            settings = settings,
            reduceMotion = preferences.reduceMotion,
            routeSuggestions = suggestions.enabled,
            isExporting = local.exporting,
            isBackupBusy = local.backupBusy,
            restorePreview = local.restorePreview,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    fun setUnits(units: UnitSystem) = launch { settingsRepository.setUnits(units) }

    fun setTheme(mode: ThemeMode) = launch { settingsRepository.setTheme(mode.toPreference()) }

    fun setReduceMotion(enabled: Boolean) = launch { preferencesRepository.setReduceMotion(enabled) }

    fun setLaunchTab(tab: LaunchTab) = launch { settingsRepository.setLaunchTab(tab) }
    /** Turning suggestions off also deletes the cached ones. */
    fun setRouteSuggestions(enabled: Boolean) = launch { suggestionSettings.setEnabled(enabled) }

    fun setDefaultSport(sport: SportType) = launch { settingsRepository.setDefaultSport(sport) }

    /** The Monday weekly recap (notification and inbox entry); on by default. */
    fun setWeeklyRecap(enabled: Boolean) = launch {
        val current = settingsRepository.settings.first().notifications
        settingsRepository.setNotifications(current.copy(weeklySummary = enabled))
    }

    fun setAutoPause(sport: SportType, enabled: Boolean) = launch { settingsRepository.setAutoPause(sport, enabled) }

    fun setAudioCuesEnabled(enabled: Boolean) = updateCues { it.copy(enabled = enabled) }

    fun setCueInterval(interval: Double) = updateCues { it.copy(distanceInterval = interval) }

    fun setAnnounceAutoPause(enabled: Boolean) = updateCues { it.copy(announceAutoPause = enabled) }

    fun setAnnounceStatus(enabled: Boolean) = updateCues { it.copy(announceStatus = enabled) }

    fun setAnnounceSplits(enabled: Boolean) = updateCues { it.copy(announceSplits = enabled) }

    fun setAnnounceLaps(enabled: Boolean) = updateCues { it.copy(announceLaps = enabled) }

    fun setAnnounceRoute(enabled: Boolean) = updateCues { it.copy(announceRoute = enabled) }

    fun setDistanceCues(enabled: Boolean) = updateCues { it.copy(distanceCues = enabled) }

    fun setCueTimeInterval(minutes: Int) = updateCues { it.copy(timeIntervalMinutes = minutes) }

    fun setAutoLap(meters: Double) = launch { settingsRepository.setAutoLap(meters) }

    fun setOffRouteAlerts(enabled: Boolean) = launch { settingsRepository.setOffRouteAlerts(enabled) }

    fun setDefaultVisibility(visibility: Visibility) = launch { settingsRepository.setDefaultVisibility(visibility) }

    fun setHideStart(meters: Double) = updatePrivacy { it.copy(hideStartMeters = meters) }

    fun setHideEnd(meters: Double) = updatePrivacy { it.copy(hideEndMeters = meters) }

    /** Writes every activity as GPX into a ZIP at [destination] (settings 1.5, immediate). */
    fun exportAll(destination: Uri) {
        if (local.value.exporting) return
        local.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val message = try {
                exportMessage(archiveExporter.exportAll(destination))
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                "Export failed: ${e.message ?: "the file could not be written"}"
            } catch (e: RuntimeException) {
                // SecurityException / IllegalArgumentException from a revoked or invalid document.
                "Export failed: the chosen file could not be written"
            }
            local.update { it.copy(exporting = false, message = message) }
        }
    }

    /** Writes a full backup (activities, photos, goals, profile, settings) to [destination]. */
    fun backUp(destination: Uri) = runBackupTask {
        try {
            backupMessage(backup.backUp(destination))
        } catch (e: IOException) {
            "Backup failed: ${e.message ?: "the file could not be written"}"
        } catch (e: SQLiteException) {
            "Backup failed: the activity database could not be read"
        } catch (e: RuntimeException) {
            // SecurityException / IllegalArgumentException from a revoked or invalid document.
            "Backup failed: the chosen file could not be written"
        }
    }

    /** Reads and checks the backup at [source], then asks for confirmation through [SettingsUiState.restorePreview]. */
    fun chooseRestore(source: Uri) {
        if (unsavedRecording.hasUnsavedRecording()) {
            local.update { it.copy(message = RECORDING_BLOCKS_RESTORE) }
            return
        }
        runBackupTask {
            try {
                val manifest = backup.stageRestore(source)
                local.update { it.copy(restorePreview = preview(manifest)) }
                null
            } catch (e: BackupException) {
                e.message
            }
        }
    }

    /** Replaces everything on this phone with the confirmed backup; the app restarts. */
    fun confirmRestore() {
        if (local.value.restorePreview == null) return
        if (unsavedRecording.hasUnsavedRecording()) {
            local.update { it.copy(restorePreview = null, message = RECORDING_BLOCKS_RESTORE) }
            viewModelScope.launch { backup.discardRestore() }
            return
        }
        local.update { it.copy(restorePreview = null) }
        runBackupTask {
            try {
                backup.restoreAndRestart()
                null
            } catch (e: IOException) {
                "Restore failed: ${e.message ?: "the backup could not be applied"}"
            }
        }
    }

    fun cancelRestore() {
        local.update { it.copy(restorePreview = null) }
        viewModelScope.launch { backup.discardRestore() }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    /** Runs one backup or restore step at a time; [block] returns the snackbar text, if any. */
    private fun runBackupTask(block: suspend () -> String?) {
        if (local.value.backupBusy) return
        local.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            val message = block()
            local.update { it.copy(backupBusy = false, message = message ?: it.message) }
        }
    }

    private fun updateCues(change: (AudioCueSettings) -> AudioCueSettings) =
        launch { settingsRepository.setAudioCues(change(settingsRepository.settings.first().audioCues)) }

    private fun updatePrivacy(change: (PrivacySettings) -> PrivacySettings) =
        launch { settingsRepository.setPrivacy(change(settingsRepository.settings.first().privacy)) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private data class Local(
        val exporting: Boolean = false,
        val backupBusy: Boolean = false,
        val restorePreview: RestorePreview? = null,
        val message: String? = null,
    )

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        const val RECORDING_BLOCKS_RESTORE = "Finish or discard the current recording before restoring a backup"

        private val BACKUP_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

        /** "Backed up 12 activities with photos, goals and settings". */
        fun backupMessage(manifest: BackupManifest): String = when (manifest.activityCount) {
            0 -> "Backed up your profile and settings"
            1 -> "Backed up 1 activity with photos, goals and settings"
            else -> "Backed up ${manifest.activityCount} activities with photos, goals and settings"
        }

        fun preview(manifest: BackupManifest, zone: ZoneId = ZoneId.systemDefault()): RestorePreview {
            val date = try {
                BACKUP_DATE.format(Instant.parse(manifest.createdAt).atZone(zone))
            } catch (e: DateTimeParseException) {
                "an unknown date"
            }
            return RestorePreview(date, manifest.activityCount)
        }

        /** "Exported 12 activities", noting indoor ones that GPX cannot hold. */
        fun exportMessage(summary: ArchiveSummary): String {
            val exported = when (summary.exported) {
                0 -> "No activities with a GPS track to export"
                1 -> "Exported 1 activity"
                else -> "Exported ${summary.exported} activities"
            }
            return when (summary.skipped) {
                0 -> exported
                1 -> "$exported. 1 indoor activity has no track and was skipped"
                else -> "$exported. ${summary.skipped} indoor activities have no track and were skipped"
            }
        }
    }
}
