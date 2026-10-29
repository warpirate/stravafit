// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.polyline

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PolylineCodecTest {
    private val reference = listOf(LatLng(38.5, -120.2), LatLng(40.7, -120.95), LatLng(43.252, -126.453))

    @Test
    fun `encodes the reference example`() {
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", PolylineCodec.encode(reference))
    }

    @Test
    fun `decodes the reference example`() {
        val decoded = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(reference.size, decoded.size)
        reference.zip(decoded).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-9)
            assertEquals(a.longitude, b.longitude, 1e-9)
        }
    }

    @Test
    fun `precision 6 round trip`() {
        val pts = listOf(LatLng(47.123456, 8.654321), LatLng(-33.000001, 151.999999), LatLng(0.0, -179.999999))
        val back = PolylineCodec.decode(PolylineCodec.encode(pts, 6), 6)
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 5e-7)
            assertEquals(a.longitude, b.longitude, 5e-7)
        }
    }

    @Test
    fun `random round trip is within half a unit`() {
        val rnd = Random(42)
        val pts = List(500) { LatLng(rnd.nextDouble(-90.0, 90.0), rnd.nextDouble(-180.0, 180.0)) }
        for (precision in listOf(5, 6)) {
            val back = PolylineCodec.decode(PolylineCodec.encode(pts, precision), precision)
            val tol = 0.5 / Math.pow(10.0, precision.toDouble()) + 1e-12
            pts.zip(back).forEach { (a, b) ->
                assertEquals(a.latitude, b.latitude, tol)
                assertEquals(a.longitude, b.longitude, tol)
            }
        }
    }

    @Test
    fun `empty and invalid input`() {
        assertEquals("", PolylineCodec.encode(emptyList()))
        assertEquals(emptyList(), PolylineCodec.decode(""))
        assertFailsWith<IllegalArgumentException> { PolylineCodec.decode("_p~iF") }
        assertFailsWith<IllegalArgumentException> { PolylineCodec.decode("_p~iF~ps|") }
        assertFailsWith<IllegalArgumentException> { PolylineCodec.decode("ab\u0001c") }
        assertFailsWith<IllegalArgumentException> { PolylineCodec.encode(emptyList(), 0) }
    }
}

class DouglasPeuckerTest {
    @Test
    fun `collinear points collapse to the endpoints`() {
        val line = (0..100).map { Spherical.destination(LatLng(10.0, 10.0), 45.0, it * 5.0) }
        assertContentEquals(intArrayOf(0, 100), DouglasPeucker.simplifyIndices(line, 0.5))
    }

    @Test
    fun `corner is kept and small wiggles removed`() {
        val start = LatLng(0.0, 0.0)
        val east = (0..50).map { Spherical.destination(start, 90.0, it * 10.0) }
        val corner = east.last()
        val north = (1..50).map { Spherical.destination(corner, 0.0, it * 10.0) }
        val wiggly = (east + north).mapIndexed { i, p ->
            if (i % 2 == 1) Spherical.destination(p, 0.0, 1.0) else p
        }
        val kept = DouglasPeucker.simplifyIndices(wiggly, 3.0)
        assertEquals(0, kept.first())
        assertEquals(wiggly.lastIndex, kept.last())
        assertTrue(50 in kept.toList(), "corner must be kept: ${kept.toList()}")
        assertTrue(kept.size <= 4, "too many points kept: ${kept.toList()}")
        val fine = DouglasPeucker.simplifyIndices(wiggly, 0.5)
        assertTrue(fine.size > 40, "wiggles above tolerance must survive: ${fine.size}")
    }

    @Test
    fun `tiny inputs are returned unchanged`() {
        assertContentEquals(intArrayOf(), DouglasPeucker.simplifyIndices(emptyList(), 1.0))
        assertContentEquals(intArrayOf(0), DouglasPeucker.simplifyIndices(listOf(LatLng(0.0, 0.0)), 1.0))
        val two = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.0))
        assertEquals(two, DouglasPeucker.simplify(two, 1.0))
    }

    @Test
    fun `long track does not overflow`() {
        val rnd = Random(7)
        val pts = (0 until 100_000).map { LatLng(45.0 + rnd.nextDouble() * 1e-3, 7.0 + it * 1e-5) }
        val kept = DouglasPeucker.simplifyIndices(pts, 5.0)
        assertTrue(kept.size in 2 until pts.size)
    }
}
