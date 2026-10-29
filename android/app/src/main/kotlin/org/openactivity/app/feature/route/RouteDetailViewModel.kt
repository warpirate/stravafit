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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.route.RouteExporter
import org.openactivity.app.feature.explore.RouteEstimates
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.ui.common.toStat
import org.openactivity.core.data.model.RouteDetail
import org.openactivity.core.data.model.RoutePoint
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.export.file.ExportFileStore
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/**
 * Elevation along a route, normalised for drawing: [points] are (distance 0..1, elevation 0..1).
 *
 * @property minLabel lowest elevation, e.g. "12 m".
 * @property maxLabel highest elevation.
 */
data class ElevationProfileUi(
    val points: List<Pair<Float, Float>>,
    val minLabel: String,
    val maxLabel: String,
    val summary: String,
)

/**
 * State of the route detail screen.
 *
 * @property notFound true when the route does not exist (deleted elsewhere).
 * @property deleted true once the user deleted it; the screen then closes.
 * @property fileName suggested GPX file name for the export picker.
 */
data class RouteDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val routeId: Long = 0,
    val name: String = "",
    val sport: SportType? = null,
    val stats: List<Stat> = emptyList(),
    val points: List<LatLng> = emptyList(),
    val elevation: ElevationProfileUi? = null,
    val fileName: String = "route.gpx",
    val isExporting: Boolean = false,
    val deleted: Boolean = false,
    val message: String? = null,
)

/** Saved route detail: map, distance, elevation profile, estimated time; record, export or delete it. */
@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    preferencesRepository: PreferencesRepository,
    private val exporter: RouteExporter,
) : ViewModel() {

    private val routeId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_ROUTE_ID)) { "routeId argument missing" }
    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<RouteDetailUiState> = combine(
        routeRepository.observeRoute(routeId),
        preferencesRepository.preferences,
        local,
    ) { detail, preferences, local ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        if (detail == null) {
            RouteDetailUiState(isLoading = false, notFound = !local.deleted, routeId = routeId, deleted = local.deleted, message = local.message)
        } else {
            detail.toUi(formatter).copy(isExporting = local.exporting, deleted = local.deleted, message = local.message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RouteDetailUiState(routeId = routeId))

    /** Writes the route as GPX to the document the user picked. */
    fun exportGpx(destination: Uri) {
        local.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val message = try {
                exporter.exportGpx(routeId, destination)
                "Route exported as GPX"
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

    /** Deletes the route. Activities recorded while following it keep their own tracks. */
    fun delete() {
        viewModelScope.launch {
            routeRepository.delete(routeId)
            local.update { it.copy(deleted = true) }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private fun RouteDetail.toUi(formatter: UnitFormatter): RouteDetailUiState {
        // Recomputed from the profile so routes imported before the end-of-profile fix agree with their chart.
        val totals = org.openactivity.core.data.repository.LocalRouteRepository.routeElevation(points.mapNotNull { it.elevationMeters })
        val gain = totals?.gain ?: route.elevationGainMeters
        val loss = totals?.loss ?: route.elevationLossMeters
        val estimate = RouteEstimates.estimatedSeconds(route.distanceMeters, gain, route.sport)
        return RouteDetailUiState(
            isLoading = false,
            routeId = route.id,
            name = route.name,
            sport = route.sport,
            stats = listOf(
                formatter.distance(route.distanceMeters).toStat("Distance"),
                formatter.elevation(gain).toStat("Elev gain"),
                formatter.elevation(loss).toStat("Elev loss"),
                Stat("Est. time", "~${UnitFormatter.compactDuration(estimate)}"),
            ),
            points = points.map { LatLng(it.latitude, it.longitude) },
            elevation = elevationProfile(points, formatter),
            fileName = "${ExportFileStore.fileNameStem(route.name)}.gpx",
        )
    }

    private data class Local(val exporting: Boolean = false, val deleted: Boolean = false, val message: String? = null)

    internal companion object {
        /** Navigation argument name, matching `RouteDetailRoute.routeId`. */
        const val ARG_ROUTE_ID = "routeId"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MAX_PROFILE_POINTS = 200

        /** The profile over at most 200 samples, or null when fewer than two points have an elevation. */
        fun elevationProfile(points: List<RoutePoint>, formatter: UnitFormatter): ElevationProfileUi? {
            val withElevation = points.filter { it.elevationMeters != null }
            if (withElevation.size < 2) return null
            val step = maxOf(1, withElevation.size / MAX_PROFILE_POINTS)
            val sampled = withElevation.filterIndexed { i, _ -> i % step == 0 || i == withElevation.lastIndex }
            val total = sampled.last().distanceMeters.takeIf { it > 0 } ?: return null
            val elevations = sampled.map { it.elevationMeters!! }
            val min = elevations.min()
            val max = elevations.max()
            val span = (max - min).takeIf { it > 0 } ?: 1.0
            val minMeasure = formatter.elevation(min)
            val maxMeasure = formatter.elevation(max)
            return ElevationProfileUi(
                points = sampled.map { (it.distanceMeters / total).toFloat() to ((it.elevationMeters!! - min) / span).toFloat() },
                minLabel = "${minMeasure.value} ${minMeasure.unit}",
                maxLabel = "${maxMeasure.value} ${maxMeasure.unit}",
                summary = "Elevation from ${minMeasure.value} to ${maxMeasure.value} ${maxMeasure.spokenUnit}",
            )
        }
    }
}
