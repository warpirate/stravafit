// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng

/** Parses a real FOSSGIS OSRM foot response recorded in Pune (test resource, test-only). */
class OsrmClientTest {

    private fun fixture(): String =
        checkNotNull(javaClass.classLoader?.getResource("osrm_foot_pune_loop.json")).readText()

    @Test
    fun `a recorded response parses into distance, geometry and road names`() {
        val route = checkNotNull(OsrmClient.parse(fixture()))
        assertEquals(6_981.6, route.distanceMeters, 0.01)
        assertEquals(5_589.9, route.durationSeconds, 0.01)
        assertTrue(route.points.size > 50)
        // Starts and ends at the requested point, snapped to the nearest way.
        assertEquals(0.0, Spherical.distance(route.points.first(), route.points.last()), 30.0)
        assertEquals(18.5314, route.points.first().latitude, 0.001)
        assertEquals(route.distanceMeters, Spherical.pathLength(route.points), route.distanceMeters * 0.02)
        val names = route.roadNames.map { it.first }
        assertTrue("K. B. Joshi Path" in names)
        assertTrue(names.none { it.isBlank() })
    }

    @Test
    fun `the recorded loop backtracks and is rejected by the reuse filter`() {
        val route = checkNotNull(OsrmClient.parse(fixture()))
        // Two waypoints snapped onto dead ends: the same stretches are walked out and back.
        assertTrue(RouteShape.reuseFraction(route.points) > 0.25)
    }

    @Test
    fun `the recorded loop is named after its main road`() {
        val route = checkNotNull(OsrmClient.parse(fixture()))
        assertEquals("Chaturshringi Rd loop", RouteNamer.name(route.roadNames, route.distanceMeters, 5_000.0, 45.0))
    }

    @Test
    fun `a response without a route parses to null`() {
        assertNull(OsrmClient.parse("""{"code":"NoRoute","message":"Impossible route between points"}"""))
        assertNull(OsrmClient.parse("""{"code":"Ok","routes":[]}"""))
    }

    @Test(expected = RoutingServiceException::class)
    fun `garbage is an error, not an empty result`() {
        OsrmClient.parse("<html>Too many requests</html>")
    }

    @Test
    fun `url uses the sport's profile, lon-lat order and polyline6`() {
        val client = OsrmClient({ error("unused") }, RateLimiter(0))
        val url = client.url(SuggestionSport.RIDE, listOf(LatLng(18.5314, 73.8446), LatLng(18.54, 73.87)))
        assertEquals(
            "https://routing.openstreetmap.de/routed-bike/route/v1/bike/73.844600,18.531400;73.870000,18.540000" +
                "?overview=full&geometries=polyline6&steps=true&continue_straight=true",
            url,
        )
        assertTrue(client.url(SuggestionSport.WALK, listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))).contains("/routed-foot/route/v1/foot/"))
    }

    @Test
    fun `no route answers are null and server errors throw`() = runTest {
        val noRoute = OsrmClient({ HttpResponse(400, """{"code":"NoRoute"}""") }, RateLimiter(0))
        assertNull(noRoute.route(SuggestionSport.RUN, listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.01))))
        val down = OsrmClient({ HttpResponse(503, "Service Unavailable") }, RateLimiter(0))
        val error = runCatching { down.route(SuggestionSport.RUN, listOf(LatLng(0.0, 0.0), LatLng(0.01, 0.01))) }.exceptionOrNull()
        assertTrue(error is RoutingServiceException)
    }
}
