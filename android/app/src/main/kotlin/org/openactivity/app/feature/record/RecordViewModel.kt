// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.GpsFix
import org.openactivity.app.data.GpsStatusSource
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.model.Sport
import org.openactivity.app.model.SportGroup
import javax.inject.Inject

/**
 * Record pre-start screen: preselects the last-used sport, tracks GPS readiness and the quick
 * settings. Starting a recording is out of scope until `:core:recording` and the foreground
 * service exist.
 */
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val gpsStatusSource: GpsStatusSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var latestFix = GpsFix(null)
    private var slowFixJob: Job? = null

    /** Set once the user picks a sport, so the late-arriving stored preference cannot override it. */
    private var userChoseSport = false

    init {
        viewModelScope.launch {
            val lastSport = preferencesRepository.preferences.first().lastSport
            if (!userChoseSport) applySport(lastSport)
            gpsStatusSource.fixes.collect { fix ->
                latestFix = fix
                refreshGps()
            }
        }
    }

    /** Opens or closes the sport picker sheet. */
    fun setSportPickerOpen(open: Boolean) {
        _uiState.update { it.copy(isSportPickerOpen = open) }
    }

    /** Selects [sport], closes the picker and remembers it for next time (record checklist 2.2). */
    fun selectSport(sport: Sport) {
        userChoseSport = true
        applySport(sport)
        _uiState.update { it.copy(isSportPickerOpen = false) }
        viewModelScope.launch { preferencesRepository.setLastSport(sport) }
    }

    fun toggleAutoPause() = _uiState.update { it.copy(autoPause = !it.autoPause) }

    fun toggleAudioCues() = _uiState.update { it.copy(audioCues = !it.audioCues) }

    private fun applySport(sport: Sport) {
        _uiState.update { state ->
            state.copy(
                sport = sport,
                recentSports = (listOf(sport) + state.recentSports).distinct().take(RECENT_COUNT),
                // Record checklist 6.2: auto-pause defaults on for rides, off otherwise.
                autoPause = sport.group == SportGroup.Cycle,
            )
        }
        refreshGps()
    }

    private fun refreshGps() {
        val sport = _uiState.value.sport
        val accuracy = latestFix.accuracyMeters
        val gps = when {
            !sport.usesGps -> GpsState.Indoor
            accuracy != null && accuracy <= GpsState.GPS_READY_METERS -> GpsState.Ready(accuracy)
            else -> GpsState.Acquiring(accuracy)
        }
        _uiState.update { it.copy(gps = gps, showSlowFixHint = gps is GpsState.Acquiring && it.showSlowFixHint) }
        if (gps is GpsState.Acquiring) startSlowFixTimer() else cancelSlowFixTimer()
    }

    private fun startSlowFixTimer() {
        if (slowFixJob?.isActive == true) return
        slowFixJob = viewModelScope.launch {
            delay(SLOW_FIX_HINT_MS)
            _uiState.update { it.copy(showSlowFixHint = it.gps is GpsState.Acquiring) }
        }
    }

    private fun cancelSlowFixTimer() {
        slowFixJob?.cancel()
        slowFixJob = null
        _uiState.update { it.copy(showSlowFixHint = false) }
    }

    internal companion object {
        const val SLOW_FIX_HINT_MS = 20_000L
        private const val RECENT_COUNT = 4
    }
}
