// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.polyline.PolylineCodec
import java.io.IOException
import kotlin.math.abs

/**
 * The suggester over a stand-in routing server that draws straight lines through the requested waypoints
 * and reports them [factor] times longer, as winding streets would be.
 */
class RouteSuggesterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val start = LatLng(18.5314, 73.8446)
    private var osrmCalls = 0
    private var elevationCalls = 0
    private var offline = false
    private var factor = 1.0
    private var backtrack = false

    private val transport = HttpTransport { url ->
        if (offline) throw IOException("no network")
        if (url.contains("open-meteo")) {
            elevationCalls++
            val count = url.substringAfter("latitude=").substringBefore("&").split(',').size
            HttpResponse(200, """{"elevation":[${List(count) { i -> 560.0 + (i % 20) }.joinToString(",")}]}""")
        } else {
            osrmCalls++
            HttpResponse(200, osrmBody(url))
        }
    }

    private fun osrmBody(url: String): String {
        val coords = url.substringAfter("/foot/").substringBefore("?").split(';').map {
            val (lon, lat) = it.split(',').map(String::toDouble)
            LatLng(lat, lon)
        }
        val path = if (backtrack) listOf(coords.first(), coords[1], coords.first()) else coords
        val length = Spherical.pathLength(path) * factor
        return """{"code":"Ok","routes":[{"distance":$length,"duration":${length / 3},"geometry":"${escape(PolylineCodec.encode(path, 6))}",""" +
            """"legs":[{"steps":[{"name":"Bund Garden Road","distance":${length * 0.6}},{"name":"","distance":${length * 0.4}}]}]}]}"""
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun suggester(config: RouteSuggester.Config = RouteSuggester.Config()) = RouteSuggester(
        OsrmClient(transport, RateLimiter(0)),
        ElevationClient(transport, RateLimiter(0)),
        SuggestionCache(folder.root),
        config,
    )

    private val request = SuggestionRequest(start, SuggestionSport.RUN, listOf(3_000.0, 5_000.0))

    @Test
    fun `loops close to each target distance are suggested and named after their main road`() = runTest {
        val result = suggester().suggest(request).last()
        assertTrue(result.done)
        assertFalse(result.failed)
        assertEquals(4, result.routes.size)
        result.routes.forEach { route ->
            assertTrue(abs(route.distanceMeters / route.targetDistanceMeters - 1) <= 0.15)
            assertEquals(start, route.points.first().let { LatLng(Math.round(it.latitude * 1e4) / 1e4, Math.round(it.longitude * 1e4) / 1e4) })
            assertTrue(route.profile.size in 90..110)
            assertTrue(checkNotNull(route.elevationGainMeters) > 0)
        }
        assertEquals("Bund Garden Rd loop", result.routes.first().name)
        // Names stay unique per distance: the second loop gets its direction.
        result.routes.groupBy { it.targetDistanceMeters }.values.forEach { group ->
            assertEquals(group.size, group.map { it.name }.toSet().size)
        }
    }

    @Test
    fun `a loop far off the target is retried once with a corrected radius`() = runTest {
        factor = 2.0 // streets twice as long as the default factor expects
        val result = suggester(RouteSuggester.Config(candidatesPerDistance = 1)).suggest(request.copy(distancesMeters = listOf(5_000.0))).last()
        assertEquals(2, osrmCalls)
        assertEquals(5_000.0, result.routes.single().distanceMeters, 5_000.0 * 0.05)
    }

    @Test
    fun `out-and-back routes are never suggested`() = runTest {
        backtrack = true
        factor = 1.0
        val result = suggester().suggest(request).last()
        assertTrue(result.routes.isEmpty())
        assertFalse(result.failed)
    }

    @Test
    fun `cached distances come back without routing again, refresh routes anew`() = runTest {
        val suggester = suggester()
        val first = suggester.suggest(request).last()
        val calls = osrmCalls
        val again = suggester.suggest(request).last()
        assertEquals(calls, osrmCalls)
        assertEquals(first.routes, again.routes)
        val refreshed = suggester.suggest(request, refresh = 1).last()
        assertTrue(osrmCalls > calls)
        assertTrue(refreshed.routes.map { it.id }.none { it in first.routes.map { r -> r.id } })
    }

    @Test
    fun `progress is emitted per distance`() = runTest {
        val emissions = suggester().suggest(request).toList()
        assertEquals(listOf(2, 4, 4), emissions.map { it.routes.size })
        assertEquals(listOf(false, false, true), emissions.map { it.done })
    }

    @Test
    fun `offline returns cached routes, even old ones, and says it failed`() = runTest {
        val suggester = suggester()
        val online = suggester.suggest(request).last()
        offline = true
        val cachedOnly = suggester.suggest(request, refresh = 1).last()
        assertTrue(cachedOnly.failed)
        assertEquals(online.routes, cachedOnly.routes)
        val nothing = RouteSuggester(OsrmClient(transport, RateLimiter(0)), ElevationClient(transport, RateLimiter(0)), SuggestionCache(folder.newFolder()))
            .suggest(request).last()
        assertTrue(nothing.failed)
        assertTrue(nothing.routes.isEmpty())
    }

    @Test
    fun `suggestions can be found by id afterwards`() = runTest {
        val suggester = suggester()
        val route = suggester.suggest(request).last().routes.first()
        assertEquals(route, suggester.find(route.id))
        // A new process finds it in the cache.
        assertEquals(route, RouteSuggester(OsrmClient(transport, RateLimiter(0)), ElevationClient(transport, RateLimiter(0)), SuggestionCache(folder.root)).find(route.id))
    }
}
