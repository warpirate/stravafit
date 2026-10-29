// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import org.openactivity.app.data.SuggestionSettings
import org.openactivity.app.data.SuggestionSettingsState
import org.openactivity.core.location.CurrentLocationProvider
import org.openactivity.core.location.CurrentLocationResult
import org.openactivity.core.location.LocationAvailability
import org.openactivity.core.routing.ProfileSample
import org.openactivity.core.routing.RouteSuggestion
import org.openactivity.core.routing.RouteSuggestions
import org.openactivity.core.routing.SuggestionProgress
import org.openactivity.core.routing.SuggestionRequest
import org.openactivity.core.routing.SuggestionSport
import org.openactivity.model.LatLng
import org.openactivity.polyline.PolylineCodec

class FakeSuggestionSettings(initial: SuggestionSettingsState = SuggestionSettingsState()) : SuggestionSettings {
    val value = MutableStateFlow(initial)
    override val state: Flow<SuggestionSettingsState> = value

    override suspend fun setEnabled(enabled: Boolean) = value.update { it.copy(enabled = enabled, introSeen = true) }

    override suspend fun markIntroSeen() = value.update { it.copy(introSeen = true) }
}

class FakeCurrentLocation(var result: CurrentLocationResult = CurrentLocationResult.Found(PUNE)) : CurrentLocationProvider {
    override fun availability(): LocationAvailability =
        (result as? CurrentLocationResult.Unavailable)?.reason ?: LocationAvailability.AVAILABLE

    override suspend fun current(): CurrentLocationResult = result
}

/**
 * [RouteSuggestions] answering with [routes] per requested distance, emitted one distance at a time as
 * the real suggester does; [failed] simulates an unreachable service.
 */
class FakeRouteSuggestions(var routes: (SuggestionRequest, Double) -> List<RouteSuggestion> = { r, d -> listOf(testSuggestion("${r.sport}-$d", r.sport, d)) }) : RouteSuggestions {
    var failed = false
    val requests = mutableListOf<Pair<SuggestionRequest, Int>>()
    var cleared = false

    override fun suggest(request: SuggestionRequest, refresh: Int): Flow<SuggestionProgress> = flow {
        requests += request to refresh
        val all = mutableListOf<RouteSuggestion>()
        for (d in request.distancesMeters) {
            if (!failed) all += routes(request, d)
            emit(SuggestionProgress(all.toList(), done = false, failed = failed))
        }
        emit(SuggestionProgress(all.toList(), done = true, failed = failed))
    }

    override suspend fun find(id: String): RouteSuggestion? =
        SuggestionSport.entries.flatMap { s -> s.defaultDistancesMeters.flatMap { routes(SuggestionRequest(PUNE, s), it) } }.firstOrNull { it.id == id }

    override suspend fun clear() {
        cleared = true
    }
}

val PUNE = LatLng(18.5314, 73.8446)

/** A loop around a square near Pune, for view-model and screenshot tests only. */
fun testSuggestion(
    id: String,
    sport: SuggestionSport = SuggestionSport.RUN,
    target: Double = 5_000.0,
    name: String = "Bund Garden Rd loop",
    gain: Double? = 38.0,
    points: List<LatLng> = listOf(
        PUNE, LatLng(18.5350, 73.8470), LatLng(18.5390, 73.8530), LatLng(18.5360, 73.8600),
        LatLng(18.5300, 73.8570), LatLng(18.5280, 73.8500), PUNE,
    ),
) = RouteSuggestion(
    id = id,
    name = name,
    sport = sport,
    targetDistanceMeters = target,
    distanceMeters = target * 1.04,
    elevationGainMeters = gain,
    elevationLossMeters = gain,
    polyline = PolylineCodec.encode(points, 6),
    profile = List(20) { ProfileSample(it * target * 1.04 / 19, 560.0 + if (it < 10) it * 3.0 else (19 - it) * 3.0) },
    startLatitude = PUNE.latitude,
    startLongitude = PUNE.longitude,
    bearingDegrees = 45.0,
)
