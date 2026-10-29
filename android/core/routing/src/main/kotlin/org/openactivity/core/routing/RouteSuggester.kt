// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.polyline.DouglasPeucker
import org.openactivity.polyline.PolylineCodec
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Route suggestions near a position. */
interface RouteSuggestions {
    /**
     * Loops for [request], emitted as each target distance completes (cached distances come first and
     * instantly). [refresh] > 0 skips the fresh cache and routes with other start bearings.
     *
     * Never emits made-up routes: when the services cannot be reached, the result holds what the cache
     * had (possibly nothing) and [SuggestionProgress.failed] is true.
     */
    fun suggest(request: SuggestionRequest, refresh: Int = 0): Flow<SuggestionProgress>

    /** A suggestion shown earlier, by id, or null when it is no longer known. */
    suspend fun find(id: String): RouteSuggestion?

    /** Forgets every cached suggestion. */
    suspend fun clear()
}

/**
 * Generates loop suggestions: for a few start bearings it puts waypoints on a circle sized for the target
 * distance, routes them with OSRM (correcting the radius once when the loop is more than 15 % off),
 * drops loops that run over themselves for more than a quarter of their length or that duplicate a
 * better one, adds the elevation profile from Open-Meteo, and names them after their main road.
 */
class RouteSuggester(
    private val osrm: OsrmClient,
    private val elevation: ElevationClient,
    private val cache: SuggestionCache,
    private val config: Config = Config(),
) : RouteSuggestions {

    /**
     * Tuning.
     *
     * @property candidatesPerDistance routing attempts (start bearings) per target distance.
     * @property keepPerDistance best loops kept per target distance.
     * @property retryError relative distance error that triggers one retry with a corrected radius.
     * @property maxError loops further off the target than this are dropped.
     * @property maxReuse loops reusing more than this share of their own length are dropped.
     * @property maxSimilarity loops sharing more than this share of their area with a better one are dropped.
     */
    data class Config(
        val candidatesPerDistance: Int = 3,
        val keepPerDistance: Int = 2,
        val retryError: Double = 0.15,
        val maxError: Double = 0.25,
        val maxReuse: Double = 0.25,
        val maxSimilarity: Double = 0.5,
        val profileSamples: Int = 100,
    )

    /** Learned route factor per sport, so later candidates start with a better radius. */
    private val routeFactors = ConcurrentHashMap<SuggestionSport, Double>()
    private val recent = ConcurrentHashMap<String, RouteSuggestion>()

    override fun suggest(request: SuggestionRequest, refresh: Int): Flow<SuggestionProgress> = flow {
        val routes = mutableListOf<RouteSuggestion>()
        var failed = false
        for (distance in request.distancesMeters) {
            val key = cache.key(request.start, request.sport, distance)
            val fresh = if (refresh == 0) cache.get(key) else null
            val found = when {
                fresh != null -> fresh
                // After one failure, don't wait for more timeouts: show what the cache has.
                failed -> cache.get(key, allowExpired = true).orEmpty()
                else -> try {
                    generate(request.start, request.sport, distance, refresh, key).also { cache.put(key, it) }
                } catch (e: IOException) {
                    failed = true
                    cache.get(key, allowExpired = true).orEmpty()
                }
            }
            found.forEach { recent[it.id] = it }
            routes += found
            emit(SuggestionProgress(routes.toList(), done = false, failed = failed))
        }
        emit(SuggestionProgress(routes.toList(), done = true, failed = failed))
    }

    override suspend fun find(id: String): RouteSuggestion? = recent[id] ?: cache.find(id)?.also { recent[id] = it }

    override suspend fun clear() {
        recent.clear()
        cache.clear()
    }

    /** Loops of about [target] metres from [start]; routing failures other than "no route" throw. */
    internal suspend fun generate(start: LatLng, sport: SuggestionSport, target: Double, refresh: Int, key: String): List<RouteSuggestion> {
        val count = config.candidatesPerDistance
        // Different distances and refreshes start from different bearings, so loops differ.
        val offset = refresh * REFRESH_BEARING_STEP + target / 1000.0 * DISTANCE_BEARING_STEP
        val candidates = (0 until count).mapNotNull { i ->
            currentCoroutineContext().ensureActive()
            val bearing = Spherical.normalizeBearing(offset + i * 360.0 / count)
            candidate(start, sport, target, bearing, WAYPOINT_COUNTS[(i + refresh) % WAYPOINT_COUNTS.size])
        }
        val kept = pickBest(candidates, config)
        val names = mutableSetOf<String>()
        return kept.map { c ->
            currentCoroutineContext().ensureActive()
            var name = RouteNamer.name(c.route.roadNames, c.route.distanceMeters, target, c.bearing)
            if (!names.add(name)) name = "$name · ${LoopGeometry.compass(c.bearing)}".also { names += it }
            toSuggestion(c, sport, target, start, name, "$key-${c.bearing.toInt()}-$refresh")
        }
    }

    private suspend fun candidate(start: LatLng, sport: SuggestionSport, target: Double, bearing: Double, waypoints: Int): Candidate? {
        val factor = routeFactors[sport] ?: LoopGeometry.DEFAULT_ROUTE_FACTOR
        val radius = LoopGeometry.radiusFor(target, factor)
        var route = osrm.route(sport, LoopGeometry.loop(start, radius, bearing, waypoints)) ?: return null
        learnFactor(sport, factor, LoopGeometry.observedFactor(route.distanceMeters, radius))
        if (error(route, target) > config.retryError && route.distanceMeters > 0) {
            val adjusted = radius * target / route.distanceMeters
            val retry = osrm.route(sport, LoopGeometry.loop(start, adjusted, bearing, waypoints))
            if (retry != null && error(retry, target) < error(route, target)) route = retry
        }
        val error = error(route, target)
        if (error > config.maxError) return null
        val reuse = RouteShape.reuseFraction(route.points)
        if (reuse > config.maxReuse) return null
        return Candidate(route, bearing, error, reuse)
    }

    private fun learnFactor(sport: SuggestionSport, current: Double, observed: Double) {
        routeFactors[sport] = (current + observed) / 2
    }

    private suspend fun toSuggestion(c: Candidate, sport: SuggestionSport, target: Double, start: LatLng, name: String, id: String): RouteSuggestion {
        val length = Spherical.pathLength(c.route.points)
        val step = (length / (config.profileSamples - 1)).coerceAtLeast(MIN_PROFILE_STEP_METERS)
        val samples = RouteShape.resample(c.route.points, step)
        // The route is real without elevation; only the climb is unknown then.
        val elevations = try {
            elevation.elevations(samples.map { it.second })
        } catch (e: IOException) {
            null
        }
        val totals = elevations?.let(RouteElevation::totals)
        return RouteSuggestion(
            id = id,
            name = name,
            sport = sport,
            targetDistanceMeters = target,
            distanceMeters = c.route.distanceMeters,
            elevationGainMeters = totals?.gain,
            elevationLossMeters = totals?.loss,
            polyline = PolylineCodec.encode(DouglasPeucker.simplify(c.route.points, SIMPLIFY_METERS), RouteSuggestion.POLYLINE_PRECISION),
            profile = elevations?.let { e -> samples.zip(e) { (d, _), h -> ProfileSample(d, h) } }.orEmpty(),
            startLatitude = start.latitude,
            startLongitude = start.longitude,
            bearingDegrees = c.bearing,
        )
    }

    /** A routed loop with its distance [error] (relative) and self-[reuse] share. */
    internal data class Candidate(val route: OsrmRoute, val bearing: Double, val error: Double, val reuse: Double) {
        /** Lower is better: distance accuracy counts twice as much as reuse. */
        val score: Double get() = error * 2 + reuse
    }

    internal companion object {
        private val WAYPOINT_COUNTS = listOf(3, 2, 4)
        private const val REFRESH_BEARING_STEP = 47.0
        private const val DISTANCE_BEARING_STEP = 29.0
        private const val SIMPLIFY_METERS = 2.0
        private const val MIN_PROFILE_STEP_METERS = 10.0

        fun error(route: OsrmRoute, target: Double): Double = abs(route.distanceMeters / target - 1)

        /** The best-scoring candidates, skipping any too similar to one already kept. */
        fun pickBest(candidates: List<Candidate>, config: Config): List<Candidate> {
            val kept = mutableListOf<Candidate>()
            for (c in candidates.sortedBy { it.score }) {
                if (kept.size >= config.keepPerDistance) break
                if (kept.any { RouteShape.similarity(it.route.points, c.route.points) > config.maxSimilarity }) continue
                kept += c
            }
            return kept
        }
    }
}
