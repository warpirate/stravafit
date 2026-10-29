// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.explore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.route.RouteImporter
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.ui.common.decodeRoute
import org.openactivity.core.data.model.SavedRoute
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import java.util.Locale
import javax.inject.Inject

/** One saved route card. */
data class RouteCardUi(
    val id: Long,
    val name: String,
    /** "8.20 km · 45 m · ~48m" */
    val details: String,
    val sport: SportType?,
    val points: List<LatLng>,
)

/**
 * State of the Explore tab.
 *
 * @property selectedRouteId the route drawn on the map (the newest one until you pick another).
 * @property message one-shot snackbar text, cleared with [ExploreViewModel.messageShown].
 */
data class ExploreUiState(
    val isLoading: Boolean = true,
    val routes: List<RouteCardUi> = emptyList(),
    val selectedRouteId: Long? = null,
    val isImporting: Boolean = false,
    val message: String? = null,
) {
    /** The route highlighted on the map, if any. */
    val selectedRoute: RouteCardUi? get() = routes.firstOrNull { it.id == selectedRouteId } ?: routes.firstOrNull()
}

/** Explore: a real map with your saved routes, GPX import and the saved routes list. */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    routeRepository: RouteRepository,
    preferencesRepository: PreferencesRepository,
    private val importer: RouteImporter,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<ExploreUiState> = combine(
        routeRepository.observeRoutes(),
        preferencesRepository.preferences,
        local,
    ) { routes, preferences, local ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        ExploreUiState(
            isLoading = false,
            routes = routes.map { it.toCard(formatter) },
            selectedRouteId = local.selectedRouteId?.takeIf { id -> routes.any { it.id == id } } ?: routes.firstOrNull()?.id,
            isImporting = local.importing,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExploreUiState())

    /** Highlights route [id] on the map. */
    fun selectRoute(id: Long) = local.update { it.copy(selectedRouteId = id) }

    /** Imports picked GPX/TCX/FIT files as saved routes and highlights the first new one. */
    fun importRoutes(uris: List<Uri>) {
        if (uris.isEmpty()) return
        local.update { it.copy(importing = true) }
        viewModelScope.launch {
            val summary = importer.import(uris)
            local.update {
                it.copy(
                    importing = false,
                    selectedRouteId = summary.importedIds.firstOrNull() ?: it.selectedRouteId,
                    message = importMessage(summary.importedIds.size, summary.failures),
                )
            }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private data class Local(val selectedRouteId: Long? = null, val importing: Boolean = false, val message: String? = null)

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** "Imported 2 routes", plus the first problem when some files failed. */
        fun importMessage(imported: Int, failures: List<String>): String {
            val done = when (imported) {
                0 -> "No route imported"
                1 -> "Imported 1 route"
                else -> "Imported $imported routes"
            }
            return when (failures.size) {
                0 -> done
                1 -> "$done. ${failures.first()}"
                else -> "$done. ${failures.size} files could not be used, e.g. ${failures.first()}"
            }
        }

        /** "8.20 km · 45 m · ~48m". */
        fun details(route: SavedRoute, formatter: UnitFormatter): String {
            val distance = formatter.distance(route.distanceMeters)
            val elevation = formatter.elevation(route.elevationGainMeters)
            val seconds = RouteEstimates.estimatedSeconds(route.distanceMeters, route.elevationGainMeters, route.sport)
            return "${distance.value} ${distance.unit} · ${elevation.value} ${elevation.unit} · ~${UnitFormatter.compactDuration(seconds)}"
        }

        private fun SavedRoute.toCard(formatter: UnitFormatter) = RouteCardUi(
            id = id,
            name = name,
            details = details(this, formatter),
            sport = sport,
            points = decodeRoute(polyline).orEmpty(),
        )
    }
}
