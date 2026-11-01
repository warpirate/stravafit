// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.model.LatLng

class ElevationTest {

    @Test
    fun `gain and loss over a profile, including a final descent`() {
        val totals = checkNotNull(RouteElevation.totals((0..20).map { 560.0 + it } + (19 downTo 0).map { 560.0 + it }))
        assertEquals(20.0, totals.gain, 1.0)
        assertEquals(20.0, totals.loss, 1.0)
    }

    @Test
    fun `flat profiles have no climb and too-short ones none at all`() {
        val flat = checkNotNull(RouteElevation.totals(List(50) { 560.0 }))
        assertEquals(0.0, flat.gain, 0.0)
        assertNull(RouteElevation.totals(listOf(560.0)))
    }

    @Test
    fun `DEM step noise below a metre is not counted as climbing`() {
        val noisy = List(100) { if (it % 2 == 0) 560.0 else 560.5 }
        assertEquals(0.0, checkNotNull(RouteElevation.totals(noisy)).gain, 0.0)
    }

    @Test
    fun `an Open-Meteo response parses, missing values read as sea level`() {
        assertEquals(listOf(564.0, 552.0, 0.0), ElevationClient.parse("""{"elevation":[564.0, 552.0, null]}"""))
    }

    @Test
    fun `requests are batched by 100 and positions remembered`() = runTest {
        val urls = mutableListOf<String>()
        val client = ElevationClient({ url ->
            urls += url
            val count = url.substringAfter("latitude=").substringBefore("&").split(',').size
            HttpResponse(200, """{"elevation":[${List(count) { "500.0" }.joinToString(",")}]}""")
        }, RateLimiter(0))
        val points = List(150) { LatLng(18.5 + it * 0.001, 73.8) }
        assertEquals(List(150) { 500.0 }, client.elevations(points))
        assertEquals(2, urls.size)
        assertTrue(urls.first().startsWith("https://api.open-meteo.com/v1/elevation?latitude=18.50000,18.50100"))
        client.elevations(points.take(10))
        assertEquals(2, urls.size)
    }

    @Test
    fun `an error status throws`() = runTest {
        val client = ElevationClient({ HttpResponse(429, "{}") }, RateLimiter(0))
        assertTrue(runCatching { client.elevations(listOf(LatLng(1.0, 1.0))) }.exceptionOrNull() is RoutingServiceException)
    }
}
