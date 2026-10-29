// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.openactivity.core.data.di.DataClock
import org.openactivity.core.data.mapper.toDomain
import org.openactivity.core.data.model.RouteDetail
import org.openactivity.core.data.model.RoutePointInput
import org.openactivity.core.data.model.SavedRoute
import org.openactivity.core.database.dao.RouteDao
import org.openactivity.core.database.entity.RouteEntity
import org.openactivity.core.database.entity.RoutePointEntity
import org.openactivity.geo.BoundingBox
import org.openactivity.geo.Spherical
import org.openactivity.metrics.ElevationMetrics
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.polyline.DouglasPeucker
import org.openactivity.polyline.PolylineCodec
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Saved routes (imported GPX files and routes built in the app). */
interface RouteRepository {
    /** All routes, newest first; empty when there are none. */
    fun observeRoutes(): Flow<List<SavedRoute>>

    /** One route with its points, or null when it does not exist. */
    fun observeRoute(id: Long): Flow<RouteDetail?>

    suspend fun getRoute(id: Long): RouteDetail?

    /**
     * Saves a route from parsed points (for example a GPX track) and returns its id. Distance, elevation,
     * bounding box and overview polyline are computed here.
     *
     * @throws IllegalArgumentException with fewer than two points.
     */
    suspend fun importRoute(
        name: String,
        points: List<RoutePointInput>,
        description: String = "",
        sport: SportType? = null,
    ): Long

    /** Deletes a route. Returns false when it did not exist. */
    suspend fun delete(id: Long): Boolean
}

/** [RouteRepository] over the Room database. */
@Singleton
class LocalRouteRepository @Inject constructor(
    private val dao: RouteDao,
    @DataClock private val clock: Clock,
) : RouteRepository {

    override fun observeRoutes(): Flow<List<SavedRoute>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRoute(id: Long): Flow<RouteDetail?> = dao.observeById(id).flatMapLatest { route ->
        if (route == null) flowOf(null) else dao.observePoints(id).map { pts -> RouteDetail(route.toDomain(), pts.map { it.toDomain() }) }
    }

    override suspend fun getRoute(id: Long): RouteDetail? {
        val route = dao.getById(id) ?: return null
        return RouteDetail(route.toDomain(), dao.getPoints(id).map { it.toDomain() })
    }

    override suspend fun importRoute(
        name: String,
        points: List<RoutePointInput>,
        description: String,
        sport: SportType?,
    ): Long {
        require(points.size >= 2) { "a route needs at least two points" }
        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        var cumulative = 0.0
        val pointRows = points.mapIndexed { i, p ->
            if (i > 0) cumulative += Spherical.distance(latLngs[i - 1], latLngs[i])
            RoutePointEntity(0, i, p.latitude, p.longitude, p.elevationMeters, cumulative)
        }
        val altitudes = points.mapNotNull { it.elevationMeters }
        val elevation = if (altitudes.size >= 2) {
            ElevationMetrics.compute(altitudes.toDoubleArray(), ELEVATION_HYSTERESIS_METERS)
        } else {
            null
        }
        val bbox = checkNotNull(BoundingBox.of(latLngs))
        val route = RouteEntity(
            uuid = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { DEFAULT_NAME },
            description = description,
            sport = sport?.name,
            distanceMeters = cumulative,
            elevationGainMeters = elevation?.gain ?: 0.0,
            elevationLossMeters = elevation?.loss ?: 0.0,
            polyline = PolylineCodec.encode(DouglasPeucker.simplify(latLngs, POLYLINE_TOLERANCE_METERS)),
            bboxSouth = bbox.south,
            bboxWest = bbox.west,
            bboxNorth = bbox.north,
            bboxEast = bbox.east,
            createdAtMillis = clock.millis(),
        )
        return dao.insertRouteWithPoints(route, pointRows)
    }

    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    private companion object {
        const val DEFAULT_NAME = "Route"
        const val POLYLINE_TOLERANCE_METERS = 3.0
        const val ELEVATION_HYSTERESIS_METERS = 3.0
    }
}
