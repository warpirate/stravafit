// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.model.LatLng
import java.io.IOException
import java.net.URLDecoder

/** Surfaces from a real Overpass answer around a real FOSSGIS OSRM loop in Pune (test resources). */
class SurfaceClientTest {

    private fun resource(name: String) = checkNotNull(javaClass.classLoader?.getResource(name)).readText()

    private val route = checkNotNull(OsrmClient.parse(resource("osrm_foot_pune_loop.json")))
    private val overpass = resource("overpass_pune_loop_ways.json")

    @Test
    fun `the recorded city loop is almost all paved and measured along its whole length`() {
        val mix = SurfaceClient.WayIndex(SurfaceClient.parse(overpass)).mix(route.points)
        val measured = mix.pavedMeters + mix.unpavedMeters + mix.unknownMeters
        assertEquals(route.distanceMeters, measured, route.distanceMeters * 0.03)
        assertTrue("paved share ${mix.pavedShare}", mix.pavedShare > 0.9)
        assertTrue("unpaved share ${mix.unpavedShare}", mix.unpavedShare < 0.05)
    }

    @Test
    fun `a surface tag wins, then the highway type decides`() {
        assertEquals(Surface.PAVED, SurfaceClient.classify(mapOf("highway" to "track", "surface" to "asphalt")))
        assertEquals(Surface.UNPAVED, SurfaceClient.classify(mapOf("highway" to "residential", "surface" to "gravel")))
        assertEquals(Surface.UNPAVED, SurfaceClient.classify(mapOf("highway" to "residential", "surface" to "dirt;grass")))
        assertEquals(Surface.UNPAVED, SurfaceClient.classify(mapOf("highway" to "path")))
        assertEquals(Surface.UNPAVED, SurfaceClient.classify(mapOf("highway" to "track", "tracktype" to "grade3")))
        assertEquals(Surface.PAVED, SurfaceClient.classify(mapOf("highway" to "track", "tracktype" to "grade1")))
        assertEquals(Surface.PAVED, SurfaceClient.classify(mapOf("highway" to "footway")))
        assertEquals(Surface.UNKNOWN, SurfaceClient.classify(mapOf("highway" to "construction")))
    }

    @Test
    fun `each stretch takes the surface of the nearest way, and nothing nearby is unknown`() {
        // A road east, then a dirt path further east, then open ground with no way at all.
        val road = way(mapOf("highway" to "residential"), 73.800, 73.802)
        val path = way(mapOf("highway" to "path", "surface" to "dirt"), 73.802, 73.804)
        val index = SurfaceClient.WayIndex(listOf(road, path))
        val route = listOf(LatLng(18.5, 73.800), LatLng(18.5, 73.804), LatLng(18.5, 73.806))
        val mix = index.mix(route)
        val third = (mix.pavedMeters + mix.unpavedMeters + mix.unknownMeters) / 3
        assertEquals(third, mix.pavedMeters, 30.0)
        assertEquals(third, mix.unpavedMeters, 30.0)
        assertEquals(third, mix.unknownMeters, 30.0)
    }

    @Test
    fun `a parallel way further than the match distance is not used`() {
        val farRoad = SurfaceClient.Way(1, listOf(SurfaceClient.Point(18.5003, 73.80), SurfaceClient.Point(18.5003, 73.81)), mapOf("highway" to "primary"))
        val index = SurfaceClient.WayIndex(listOf(farRoad))
        // 0.0003° of latitude is about 33 m.
        assertEquals(null, index.surfaceAt(LatLng(18.5, 73.805)))
        assertEquals(Surface.PAVED, index.surfaceAt(LatLng(18.50025, 73.805)))
    }

    @Test
    fun `one query for every route, then the next server when the first is busy`() = runTest {
        val asked = mutableListOf<String>()
        val transport = object : HttpTransport {
            override suspend fun get(url: String) = error("POST only")
            override suspend fun postForm(url: String, form: String): HttpResponse {
                asked += url
                val query = URLDecoder.decode(form.removePrefix("data="), "UTF-8")
                assertTrue(query.startsWith("[out:json]"))
                assertEquals("one around-filter per route", 2, Regex("way\\(around:").findAll(query).count())
                return if (url == "https://busy") HttpResponse(429, "<html>Too many requests</html>") else HttpResponse(200, overpass)
            }
        }
        val client = SurfaceClient(transport, RateLimiter(0), listOf("https://busy", "https://ok"))
        val result = client.surfaces(mapOf("a" to route.points, "b" to route.points.take(20), "point" to route.points.take(1)))
        assertEquals(listOf("https://busy", "https://ok"), asked)
        assertEquals(setOf("a", "b"), result.keys)
    }

    @Test(expected = IOException::class)
    fun `no server answering is an IOException`() = runTest {
        val client = SurfaceClient({ error("unused") }, RateLimiter(0), listOf("https://a"))
        client.surfaces(mapOf("a" to route.points))
    }

    /** A straight east-west way at latitude 18.5 from [fromLon] to [toLon]. */
    private fun way(tags: Map<String, String>, fromLon: Double, toLon: Double) =
        SurfaceClient.Way(0, listOf(SurfaceClient.Point(18.5, fromLon), SurfaceClient.Point(18.5, toLon)), tags)
}
