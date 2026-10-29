// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import java.io.IOException
import java.time.LocalDate
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
    val distribution: Distribution,
    val versionName: String,
    val isExporting: Boolean = false,
    val message: String? = null,
) {
    /** Suggested name for the data export archive. */
    val exportFileName: String get() = "activities-${LocalDate.now()}.zip"

    companion object {
        /** Sports offered as the default sport, the same set the Record sport picker offers. */
        val SPORTS: List<SportType> = Sport.entries.map { it.toSportType() }

        /** GPS sports that get an auto-pause switch. */
        val AUTO_PAUSE_SPORTS: List<SportType> = SPORTS.filter { it.usesGps }

        /** Audio cue intervals in kilometres or miles. */
        val CUE_INTERVALS: List<Double> = listOf(0.5, 1.0, 2.0, 5.0)

        /** Start/end hiding radii in metres (settings-privacy-units 7.2: 0 to 1,600 m). */
        val HIDE_RADII: List<Double> = listOf(0.0, 100.0, 200.0, 400.0, 800.0, 1600.0)
    }
}

/** Settings over the `:core:data` [SettingsRepository], plus reduced motion and the data export. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val archiveExporter: ActivityArchiveExporter,
    distribution: Distribution,
) : ViewModel() {

    private val local = MutableStateFlow(Local())
    private val initial = SettingsUiState(distribution = distribution, versionName = BuildConfig.VERSION_NAME)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        preferencesRepository.preferences,
        local,
    ) { settings, preferences, local ->
        initial.copy(
            settings = settings,
            reduceMotion = preferences.reduceMotion,
            isExporting = local.exporting,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    fun setUnits(units: UnitSystem) = launch { settingsRepository.setUnits(units) }

    fun setTheme(mode: ThemeMode) = launch { settingsRepository.setTheme(mode.toPreference()) }

    fun setReduceMotion(enabled: Boolean) = launch { preferencesRepository.setReduceMotion(enabled) }

    fun setDefaultSport(sport: SportType) = launch { settingsRepository.setDefaultSport(sport) }

    fun setAutoPause(sport: SportType, enabled: Boolean) = launch { settingsRepository.setAutoPause(sport, enabled) }

    fun setAudioCuesEnabled(enabled: Boolean) = updateCues { it.copy(enabled = enabled) }

    fun setCueInterval(interval: Double) = updateCues { it.copy(distanceInterval = interval) }

    fun setAnnounceAutoPause(enabled: Boolean) = updateCues { it.copy(announceAutoPause = enabled) }

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
            } catch (e: IOException) {
                "Export failed: ${e.message ?: "the file could not be written"}"
            }
            local.update { it.copy(exporting = false, message = message) }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private fun updateCues(change: (AudioCueSettings) -> AudioCueSettings) =
        launch { settingsRepository.setAudioCues(change(settingsRepository.settings.first().audioCues)) }

    private fun updatePrivacy(change: (PrivacySettings) -> PrivacySettings) =
        launch { settingsRepository.setPrivacy(change(settingsRepository.settings.first().privacy)) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private data class Local(val exporting: Boolean = false, val message: String? = null)

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

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
