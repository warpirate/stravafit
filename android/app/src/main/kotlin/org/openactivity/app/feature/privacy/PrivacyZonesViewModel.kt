// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.privacy

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.core.data.model.PrivacyZoneError
import org.openactivity.core.data.model.PrivacyZones
import org.openactivity.core.data.model.SavedPrivacyZone
import org.openactivity.core.data.repository.PrivacyZoneException
import org.openactivity.core.data.repository.PrivacyZoneRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.location.CurrentLocationProvider
import org.openactivity.core.location.CurrentLocationResult
import org.openactivity.core.location.LocationAvailability
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.UnitSystem
import javax.inject.Inject

/** Circle outlines for maps of privacy zones. */
object PrivacyZoneGeometry {
    /** A closed ring of [segments] + 1 points around [center] at [radiusMeters], drawn as a line. */
    fun ring(center: LatLng, radiusMeters: Double, segments: Int = 72): List<LatLng> =
        (0..segments).map { Spherical.destination(center, 360.0 * it / segments, radiusMeters) }

    /** "200 m", "1.6 km"; in imperial units the Strava-style "⅛ mi" to "1 mi". */
    fun radiusLabel(meters: Int, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> if (meters >= 1000) "${meters / 1000}.${meters % 1000 / 100} km" else "$meters m"
        UnitSystem.IMPERIAL -> when (meters) {
            200 -> "⅛ mi"
            400 -> "¼ mi"
            800 -> "½ mi"
            else -> "1 mi"
        }
    }

    /** "Home" or, for an unnamed zone, its place in the list. */
    fun displayName(zone: SavedPrivacyZone, index: Int): String = zone.name.ifBlank { "Zone ${index + 1}" }
}

/** The privacy zones list. */
data class PrivacyZonesUiState(
    val isLoading: Boolean = true,
    val zones: List<SavedPrivacyZone> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
) {
    /** True while another zone may be added. */
    val canAdd: Boolean get() = zones.size < PrivacyZones.MAX_ZONES
}

/** Lists the privacy zones; adding and editing happen in [PrivacyZoneEditorViewModel]. */
@HiltViewModel
class PrivacyZonesViewModel @Inject constructor(
    zones: PrivacyZoneRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<PrivacyZonesUiState> = combine(zones.observeZones(), settings.settings) { list, s ->
        PrivacyZonesUiState(isLoading = false, zones = list, units = s.units)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PrivacyZonesUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The zone editor.
 *
 * @property center the zone's centre, or null until one is picked (long-press or current location).
 * @property locating looking up the current location.
 * @property locationProblem why the current location could not be used, shown under the map.
 * @property confirmDelete the delete confirmation is open.
 */
data class PrivacyZoneEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val notFound: Boolean = false,
    val name: String = "",
    val center: LatLng? = null,
    val radiusMeters: Int = PrivacyZones.DEFAULT_RADIUS_METERS,
    val units: UnitSystem = UnitSystem.METRIC,
    val locating: Boolean = false,
    val locationProblem: LocationProblem? = null,
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
    val message: String? = null,
) {
    val canSave: Boolean get() = center != null && !saving && !isLoading
}

/** Why "Use my location" did not give a position. */
enum class LocationProblem(val text: String) {
    NO_PERMISSION("Allow location to use where you are, or long-press the map."),
    DISABLED("Location is off. Turn it on, or long-press the map."),
    NO_FIX("No location fix yet. Try again outside, or long-press the map."),
}

/** One-shot effects of the editor. */
sealed interface PrivacyZoneEditorEffect {
    /** The zone was saved or deleted: go back to the list. */
    data object Done : PrivacyZoneEditorEffect
}

/** Adds or edits one privacy zone. A new zone needs a centre: the current location or a long-pressed point. */
@HiltViewModel
class PrivacyZoneEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val zones: PrivacyZoneRepository,
    private val location: CurrentLocationProvider,
    settings: SettingsRepository,
) : ViewModel() {
    private val zoneId: Long = savedStateHandle.get<Long>(ARG_ZONE_ID) ?: 0L

    private val _uiState = MutableStateFlow(PrivacyZoneEditorUiState(isNew = zoneId == 0L))
    val uiState: StateFlow<PrivacyZoneEditorUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<PrivacyZoneEditorEffect>(Channel.BUFFERED)
    val effects: Flow<PrivacyZoneEditorEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            settings.settings.collect { s -> _uiState.update { it.copy(units = s.units) } }
        }
        viewModelScope.launch {
            if (zoneId == 0L) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val zone = zones.get(zoneId)
            _uiState.update {
                if (zone == null) {
                    it.copy(isLoading = false, notFound = true)
                } else {
                    it.copy(isLoading = false, name = zone.name, center = zone.center, radiusMeters = zone.radiusMeters)
                }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name.take(PrivacyZones.MAX_NAME_LENGTH)) }

    fun setRadius(meters: Int) = _uiState.update { it.copy(radiusMeters = meters) }

    /** A long-press on the map: the zone is centred there. */
    fun setCenter(point: LatLng) = _uiState.update { it.copy(center = point, locationProblem = null) }

    /** Centres the zone where the phone is now. */
    fun useCurrentLocation() {
        if (_uiState.value.locating) return
        _uiState.update { it.copy(locating = true, locationProblem = null) }
        viewModelScope.launch {
            val result = try {
                location.current()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CurrentLocationResult.NoFix
            }
            _uiState.update {
                when (result) {
                    is CurrentLocationResult.Found -> it.copy(locating = false, center = result.position)
                    is CurrentLocationResult.Unavailable -> it.copy(
                        locating = false,
                        locationProblem = if (result.reason == LocationAvailability.NO_PERMISSION) LocationProblem.NO_PERMISSION else LocationProblem.DISABLED,
                    )
                    CurrentLocationResult.NoFix -> it.copy(locating = false, locationProblem = LocationProblem.NO_FIX)
                }
            }
        }
    }

    /** Whether location may be asked for straight away (permission already granted). */
    fun hasLocationPermission(): Boolean = location.availability() != LocationAvailability.NO_PERMISSION

    fun save() {
        val state = _uiState.value
        val center = state.center ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = if (zoneId == 0L) {
                zones.add(state.name, center, state.radiusMeters)
            } else {
                zones.update(zoneId, state.name, center, state.radiusMeters)
            }
            result.fold(
                onSuccess = { effectChannel.send(PrivacyZoneEditorEffect.Done) },
                onFailure = { e ->
                    val text = when ((e as? PrivacyZoneException)?.error) {
                        PrivacyZoneError.LIMIT_REACHED -> "You have ${PrivacyZones.MAX_ZONES} privacy zones, the most there can be. Delete one first."
                        PrivacyZoneError.NOT_FOUND -> "This zone was deleted."
                        null -> "Could not save the zone: ${e.message ?: e.javaClass.simpleName}"
                    }
                    _uiState.update { it.copy(saving = false, message = text) }
                },
            )
        }
    }

    fun requestDelete() = _uiState.update { if (it.isNew) it else it.copy(confirmDelete = true) }

    fun dismissDelete() = _uiState.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        _uiState.update { it.copy(confirmDelete = false, saving = true) }
        viewModelScope.launch {
            zones.delete(zoneId)
            effectChannel.send(PrivacyZoneEditorEffect.Done)
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    internal companion object {
        const val ARG_ZONE_ID = "zoneId"
    }
}
