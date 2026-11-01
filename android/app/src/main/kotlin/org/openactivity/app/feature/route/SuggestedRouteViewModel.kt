// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.route

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.route.RouteExporter
import org.openactivity.app.feature.explore.RouteEstimates
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.ui.common.toStat
import org.openactivity.core.data.model.RoutePoint
import org.openactivity.core.data.model.RoutePointInput
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.core.routing.RouteSuggestion
import org.openactivity.core.routing.RouteSuggestions
import org.openactivity.geo.Spherical
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/**
 * A suggested loop on the route detail screen. It lives only in the suggestion cache until the athlete
 * saves it; recording or exporting it saves it first, so it is a normal saved route from then on.
 */
@HiltViewModel
class SuggestedRouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val suggestions: RouteSuggestions,
    private val routeRepository: RouteRepository,
    preferencesRepository: PreferencesRepository,
    private val exporter: RouteExporter,
) : ViewModel() {

    private val suggestionId: String = checkNotNull(savedStateHandle.get<String>(ARG_SUGGESTION_ID)) { "suggestionId argument missing" }
    private val local = MutableStateFlow(Local())
    private val saveLock = Mutex()

    private val suggestion = flow { emit(Loaded(suggestions.find(suggestionId))) }

    val uiState: StateFlow<RouteDetailUiState> = combine(suggestion, preferencesRepository.preferences, local) { loaded, preferences, local ->
        val route = loaded.route
        if (route == null) {
            RouteDetailUiState(isLoading = false, notFound = true, isSuggestion = true, message = local.message)
        } else {
            toUi(route, UnitFormatter(preferences.unitSystem, Locale.getDefault())).copy(
                routeId = local.savedRouteId ?: 0,
                isSaved = local.savedRouteId != null,
                isSaving = local.saving,
                isExporting = local.exporting,
                message = local.message,
                recordRouteId = local.recordRouteId,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RouteDetailUiState(isSuggestion = true))

    /** Saves the loop into Your routes. */
    fun save() {
        viewModelScope.launch {
            val id = saveOnce() ?: return@launch
            local.update { it.copy(message = "Saved to Your routes", savedRouteId = id) }
        }
    }

    /** Saves the loop if needed, then asks the screen to open the recorder on it. */
    fun record() {
        viewModelScope.launch {
            val id = saveOnce() ?: return@launch
            local.update { it.copy(recordRouteId = id) }
        }
    }

    fun recordHandled() = local.update { it.copy(recordRouteId = null) }

    /** Saves the loop if needed and writes it as GPX with the saved-route exporter. */
    fun exportGpx(destination: Uri) {
        local.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val wasSaved = local.value.savedRouteId != null
            val id = saveOnce()
            val message = if (id == null) {
                null
            } else {
                try {
                    exporter.exportGpx(id, destination)
                    if (wasSaved) "Route exported as GPX" else "Saved to Your routes and exported as GPX"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    "Export failed: ${e.message ?: "the file could not be written"}"
                } catch (e: RuntimeException) {
                    "Export failed: the chosen file could not be written"
                }
            }
            local.update { it.copy(exporting = false, message = message ?: it.message) }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    /** The saved route id, saving at most once; null (with a message) when the loop is gone. */
    private suspend fun saveOnce(): Long? = saveLock.withLock {
        local.value.savedRouteId?.let { return@withLock it }
        val route = suggestions.find(suggestionId)
        if (route == null) {
            local.update { it.copy(message = "This suggestion is no longer available") }
            return@withLock null
        }
        local.update { it.copy(saving = true) }
        try {
            val id = routeRepository.importRoute(
                name = route.name,
                points = routePoints(route),
                description = "Suggested loop routed on OpenStreetMap data (FOSSGIS OSRM), elevation from Open-Meteo.",
                sport = route.sport.sportType,
            )
            local.update { it.copy(savedRouteId = id) }
            id
        } finally {
            local.update { it.copy(saving = false) }
        }
    }

    private data class Loaded(val route: RouteSuggestion?)

    private data class Local(
        val savedRouteId: Long? = null,
        val saving: Boolean = false,
        val exporting: Boolean = false,
        val message: String? = null,
        val recordRouteId: Long? = null,
    )

    internal companion object {
        /** Navigation argument name, matching `SuggestedRouteRoute.suggestionId`. */
        const val ARG_SUGGESTION_ID = "suggestionId"
        private const val STOP_TIMEOUT_MS = 5_000L
        const val ATTRIBUTION = "Loop routed on OpenStreetMap data by the FOSSGIS OSRM service (© OpenStreetMap contributors). " +
            "Elevation from Open-Meteo. Estimated moving time assumes an easy steady effort and adds time for climbing."

        fun toUi(route: RouteSuggestion, formatter: UnitFormatter): RouteDetailUiState {
            val gain = route.elevationGainMeters
            val loss = route.elevationLossMeters
            val estimate = RouteEstimates.estimatedSeconds(route.distanceMeters, gain ?: 0.0, route.sport.sportType)
            val profilePoints = route.profile.map { RoutePoint(0.0, 0.0, it.elevationMeters, it.distanceMeters) }
            return RouteDetailUiState(
                isLoading = false,
                name = route.name,
                sport = route.sport.sportType,
                stats = listOf(
                    formatter.distance(route.distanceMeters).toStat("Distance"),
                    gain?.let(formatter::elevation).toStat("Elev gain"),
                    loss?.let(formatter::elevation).toStat("Elev loss"),
                    Stat("Est. time", "~${UnitFormatter.compactDuration(estimate)}"),
                ),
                points = route.points,
                elevation = RouteDetailViewModel.elevationProfile(profilePoints, formatter),
                fileName = "${ExportFileStore.fileNameStem(route.name)}.gpx",
                isSuggestion = true,
                isSaved = false,
                note = ATTRIBUTION,
            )
        }

        /** The route geometry with elevations interpolated from the profile samples by distance. */
        fun routePoints(route: RouteSuggestion): List<RoutePointInput> {
            val points = route.points
            val profile = route.profile
            var travelled = 0.0
            var j = 0
            return points.mapIndexed { i, p ->
                if (i > 0) travelled += Spherical.distance(points[i - 1], p)
                val elevation = if (profile.size < 2) {
                    null
                } else {
                    while (j < profile.size - 2 && profile[j + 1].distanceMeters < travelled) j++
                    val a = profile[j]
                    val b = profile[j + 1]
                    val span = b.distanceMeters - a.distanceMeters
                    val t = if (span <= 0) 0.0 else ((travelled - a.distanceMeters) / span).coerceIn(0.0, 1.0)
                    a.elevationMeters + (b.elevationMeters - a.elevationMeters) * t
                }
                RoutePointInput(p.latitude, p.longitude, elevation)
            }
        }
    }
}
