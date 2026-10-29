// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import kotlin.math.PI

class LoopGeometryTest {

    private val pune = LatLng(18.5314, 73.8446)

    @Test
    fun `radius makes the circle circumference the target divided by the route factor`() {
        val r = LoopGeometry.radiusFor(5_000.0, 1.25)
        assertEquals(5_000.0 / 1.25, 2 * PI * r, 1e-6)
    }

    @Test
    fun `route factor is clamped to a sane range`() {
        assertEquals(LoopGeometry.radiusFor(5_000.0, LoopGeometry.MAX_ROUTE_FACTOR), LoopGeometry.radiusFor(5_000.0, 9.0), 1e-9)
        assertEquals(1.0, LoopGeometry.observedFactor(10.0, 1_000.0), 1e-9)
        assertEquals(1.5, LoopGeometry.observedFactor(1.5 * 2 * PI * 600.0, 600.0), 1e-9)
    }

    @Test
    fun `start and waypoints all lie on the circle`() {
        val radius = 640.0
        for (count in 2..4) {
            val center = LoopGeometry.center(pune, radius, 45.0)
            assertEquals(radius, Spherical.distance(center, pune), 0.5)
            LoopGeometry.waypoints(pune, radius, 45.0, count).forEach { assertEquals(radius, Spherical.distance(center, it), 0.5) }
        }
    }

    @Test
    fun `waypoints are evenly spaced so the polygon perimeter approaches the circle`() {
        val radius = 1_000.0
        val loop = LoopGeometry.loop(pune, radius, 90.0, 3)
        assertEquals(5, loop.size)
        assertEquals(pune, loop.first())
        assertEquals(pune, loop.last())
        // A square inscribed in the circle: each side is r * sqrt(2).
        for (i in 1 until loop.size) assertEquals(radius * Math.sqrt(2.0), Spherical.distance(loop[i - 1], loop[i]), 1.0)
    }

    @Test
    fun `the loop lies towards the bearing`() {
        val north = LoopGeometry.waypoints(pune, 800.0, 0.0, 3)
        // The waypoint opposite the start is due north, two radii away.
        assertEquals(1_600.0, Spherical.distance(pune, north[1]), 1.0)
        assertEquals(0.0, Spherical.initialBearing(pune, north[1]), 0.1)
    }

    @Test
    fun `compass names the eight directions`() {
        assertEquals("N", LoopGeometry.compass(0.0))
        assertEquals("N", LoopGeometry.compass(350.0))
        assertEquals("NE", LoopGeometry.compass(40.0))
        assertEquals("SE", LoopGeometry.compass(135.0))
        assertEquals("W", LoopGeometry.compass(-90.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero waypoints are refused`() {
        LoopGeometry.waypoints(pune, 500.0, 0.0, 0)
    }
}
