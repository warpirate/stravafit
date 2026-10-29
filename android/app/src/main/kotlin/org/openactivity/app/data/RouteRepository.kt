// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.openactivity.app.data.sample.SampleRoutes
import org.openactivity.app.model.LatLng
import org.openactivity.app.model.Sport
import javax.inject.Inject
import javax.inject.Singleton

/** A suggested or saved route on the Explore tab. */
data class SuggestedRoute(
    val id: String,
    val name: String,
    val sport: Sport,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val estimatedSeconds: Long,
    val path: List<LatLng>,
)

/** Routes near the viewer. MapLibre and on-device routing (ADR-012) replace the sample source. */
interface RouteRepository {
    val nearbyRoutes: Flow<List<SuggestedRoute>>
}

/** Static nearby routes. */
@Singleton
class SampleRouteRepository @Inject constructor() : RouteRepository {
    private val pune = LatLng(18.5204, 73.8567)

    override val nearbyRoutes: Flow<List<SuggestedRoute>> = flowOf(
        listOf(
            SuggestedRoute("r1", "Riverside Loop", Sport.Run, 8_200.0, 45.0, 2_880, SampleRoutes.loop(pune, 1.2, seed = 71)),
            SuggestedRoute("r2", "Hill Repeats", Sport.Run, 5_100.0, 120.0, 1_980, SampleRoutes.outAndBack(pune, 2.5, 300.0, seed = 72)),
            SuggestedRoute("r3", "Lake Spin", Sport.Ride, 32_000.0, 210.0, 4_200, SampleRoutes.loop(pune, 4.5, seed = 73, lobes = 2)),
        ),
    )
}
