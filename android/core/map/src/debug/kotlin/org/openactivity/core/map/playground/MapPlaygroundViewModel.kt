// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.playground

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.core.map.MapLocation
import org.openactivity.core.map.overlay.ActivityRouteOverlay
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.core.map.style.MapStyleKind
import org.openactivity.model.LatLng

/** What the playground shows. Everything comes from real fixes or the user's own taps. */
internal data class PlaygroundUiState(
    val hasPermission: Boolean = false,
    val locationAvailable: Boolean = true,
    val location: MapLocation? = null,
    val style: MapStyleKind? = null,
    val recording: Boolean = false,
    val track: List<LatLng> = emptyList(),
    val trackSpeeds: List<Double?> = emptyList(),
    val finished: ActivityRouteOverlay? = null,
    val planned: List<LatLng> = emptyList(),
    val colorByPace: Boolean = true,
) {
    val overlays: MapOverlays
        get() = MapOverlays(
            liveTrack = track,
            plannedRoute = planned,
            activityRoute = finished?.copy(colorByPace = colorByPace),
        )
}

/** Debug playground state holder: real location in, overlays and camera out. */
internal class MapPlaygroundViewModel(application: Application) : AndroidViewModel(application) {
    private val source = PlaygroundLocationSource(application)
    private val _state = MutableStateFlow(PlaygroundUiState())
    val state: StateFlow<PlaygroundUiState> = _state.asStateFlow()
    private var locationJob: Job? = null
    private var visible = false

    /** Records the current permission; location runs only while granted and the screen is visible. */
    fun onPermission(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (!granted) stopLocation() else if (visible) startLocation()
    }

    /** The screen became visible: start fixes if permitted (re-checking that location is switched on). */
    fun onScreenStarted(granted: Boolean) {
        visible = true
        onPermission(granted)
    }

    /** The screen is no longer visible: stop fixes so the GPS is not held in the background. */
    fun onScreenStopped() {
        visible = false
        stopLocation()
    }

    fun selectStyle(style: MapStyleKind) = _state.update { it.copy(style = style) }

    fun toggleRecording() = _state.update { s ->
        if (s.recording) s.copy(recording = false) else s.copy(recording = true, track = emptyList(), trackSpeeds = emptyList())
    }

    /** Turns the recorded track into a finished activity with start/finish markers. */
    fun finishTrack() = _state.update { s ->
        if (s.track.size < 2) {
            s.copy(recording = false)
        } else {
            s.copy(
                recording = false,
                finished = ActivityRouteOverlay(s.track, s.trackSpeeds),
                track = emptyList(),
                trackSpeeds = emptyList(),
            )
        }
    }

    fun setColorByPace(enabled: Boolean) = _state.update { it.copy(colorByPace = enabled) }

    fun addPlannedPoint(point: LatLng) = _state.update { it.copy(planned = it.planned + point) }

    fun undoPlannedPoint() = _state.update { it.copy(planned = it.planned.dropLast(1)) }

    fun clearAll() = _state.update { it.copy(planned = emptyList(), finished = null, track = emptyList(), trackSpeeds = emptyList()) }

    private fun startLocation() {
        if (locationJob != null) return
        if (source.provider() == null) {
            _state.update { it.copy(locationAvailable = false) }
            return
        }
        _state.update { it.copy(locationAvailable = true) }
        locationJob = viewModelScope.launch {
            source.fixes().collect(::onFix)
        }
    }

    private fun stopLocation() {
        locationJob?.cancel()
        locationJob = null
    }

    private fun onFix(fix: Location) {
        val point = LatLng(fix.latitude, fix.longitude)
        val mapLocation = MapLocation(
            position = point,
            accuracyMeters = if (fix.hasAccuracy()) fix.accuracy else null,
            bearingDegrees = if (fix.hasBearing()) fix.bearing else null,
            speedMetersPerSecond = if (fix.hasSpeed()) fix.speed else null,
            timeMillis = fix.time,
        )
        _state.update { s ->
            val usable = s.recording && (!fix.hasAccuracy() || fix.accuracy <= MAX_TRACK_ACCURACY_M)
            if (!usable) {
                s.copy(location = mapLocation, locationAvailable = true)
            } else {
                s.copy(
                    location = mapLocation,
                    locationAvailable = true,
                    track = s.track + point,
                    trackSpeeds = s.trackSpeeds + (if (fix.hasSpeed()) fix.speed.toDouble() else null),
                )
            }
        }
    }

    private companion object {
        /** Fixes worse than this are shown as the puck but not added to the track. */
        const val MAX_TRACK_ACCURACY_M = 30f
    }
}
