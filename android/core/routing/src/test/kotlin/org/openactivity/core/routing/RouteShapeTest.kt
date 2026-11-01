// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng

class RouteShapeTest {

    private val start = LatLng(18.5314, 73.8446)

    /** A square loop with [side] metres per side, starting at [origin]. */
    private fun square(side: Double, origin: LatLng = start): List<LatLng> {
        val a = Spherical.destination(origin, 90.0, side)
        val b = Spherical.destination(a, 0.0, side)
        val c = Spherical.destination(origin, 0.0, side)
        return listOf(origin, a, b, c, origin)
    }

    @Test
    fun `a clean loop reuses nothing`() {
        assertEquals(0.0, RouteShape.reuseFraction(square(1_000.0)), 0.02)
    }

    @Test
    fun `an out-and-back reuses half its length`() {
        val end = Spherical.destination(start, 45.0, 2_000.0)
        val reuse = RouteShape.reuseFraction(listOf(start, end, start))
        assertEquals(0.5, reuse, 0.06)
    }

    @Test
    fun `a loop with a dead-end spur is measured by the spur's share`() {
        // 4 km square plus a 600 m spur out and back from its far corner: 600 / 4600 reused.
        val loop = square(1_000.0)
        val spurEnd = Spherical.destination(loop[2], 45.0, 600.0)
        val withSpur = loop.subList(0, 3) + spurEnd + loop.subList(2, loop.size)
        val reuse = RouteShape.reuseFraction(withSpur)
        assertEquals(600.0 / 4_600.0, reuse, 0.04)
        assertTrue(reuse < 0.25)
    }

    @Test
    fun `identical loops are fully similar and distant ones not at all`() {
        val a = square(1_000.0)
        assertEquals(1.0, RouteShape.similarity(a, a), 1e-9)
        val far = square(1_000.0, Spherical.destination(start, 180.0, 5_000.0))
        assertEquals(0.0, RouteShape.similarity(a, far), 1e-9)
        val mirrored = square(1_000.0, Spherical.destination(start, 270.0, 1_000.0))
        assertTrue(RouteShape.similarity(a, mirrored) < 0.5)
    }

    @Test
    fun `resampling keeps distances along the path`() {
        val samples = RouteShape.resample(square(100.0), 10.0)
        assertEquals(0.0, samples.first().first, 0.0)
        assertEquals(400.0, samples.last().first, 0.5)
        assertEquals(41, samples.size)
    }
}
