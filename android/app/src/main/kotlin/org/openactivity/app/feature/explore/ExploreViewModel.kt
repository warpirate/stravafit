// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.RouteRepository
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.LatLng
import org.openactivity.app.model.SportGroup
import java.util.Locale
import javax.inject.Inject

/** Sport filter on the Explore tab. */
enum class ExploreFilter(val label: String, val group: SportGroup?) {
    All("All", null),
    Run("Run", SportGroup.Foot),
    Ride("Ride", SportGroup.Cycle),
}

/** One suggested route card. */
data class RouteCardUi(val id: String, val name: String, val details: String, val path: List<LatLng>)

/** State of the Explore tab. */
data class ExploreUiState(
    val filter: ExploreFilter = ExploreFilter.All,
    val routes: List<RouteCardUi> = emptyList(),
)

/** Explore placeholder: nearby routes until MapLibre and on-device routing (ADR-012) land. */
@HiltViewModel
class ExploreViewModel @Inject constructor(
    routeRepository: RouteRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(ExploreFilter.All)

    val uiState: StateFlow<ExploreUiState> = combine(
        routeRepository.nearbyRoutes,
        preferencesRepository.preferences,
        filter,
    ) { routes, preferences, selected ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        ExploreUiState(
            filter = selected,
            routes = routes
                .filter { selected.group == null || it.sport.group == selected.group }
                .map { route ->
                    val distance = formatter.distance(route.distanceMeters)
                    val elevation = formatter.elevation(route.elevationGainMeters)
                    RouteCardUi(
                        id = route.id,
                        name = route.name,
                        details = "${distance.value} ${distance.unit} · ${elevation.value} ${elevation.unit} · " +
                            "~${UnitFormatter.compactDuration(route.estimatedSeconds)}",
                        path = route.path,
                    )
                },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExploreUiState())

    fun selectFilter(newFilter: ExploreFilter) {
        filter.value = newFilter
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
