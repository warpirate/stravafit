// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng

class PaceGradientTest {
    private val ramp = listOf(SLOW, FAST)

    /** A straight 2 km line north with points every 20 m. */
    private val line: List<LatLng> = (0..100).map { Spherical.destination(LatLng(45.0, 7.0), 0.0, it * 20.0) }

    @Test
    fun nothingToColour() {
        assertNull(PaceGradient.stops(listOf(LatLng(0.0, 0.0)), listOf(1.0), ramp))
        assertNull(PaceGradient.stops(line, line.map { null }, ramp))
        val same = List(3) { LatLng(1.0, 1.0) }
        assertNull(PaceGradient.stops(same, listOf(1.0, 2.0, 3.0), ramp))
    }

    @Test
    fun stopsAreStrictlyIncreasingFromZeroToOne() {
        val speeds = line.indices.map { 2.0 + (it % 7) }
        val stops = PaceGradient.stops(line, speeds, ramp)!!
        assertEquals(0.0, stops.first().progress, 0.0)
        assertEquals(1.0, stops.last().progress, 0.0)
        stops.zipWithNext().forEach { (a, b) -> assertTrue("${a.progress} < ${b.progress}", a.progress < b.progress) }
        assertTrue(stops.size <= PaceGradient.DEFAULT_MAX_STOPS + 2)
    }

    @Test
    fun slowFirstHalfAndFastSecondHalfGetTheRampEnds() {
        val speeds = line.indices.map { if (it < 50) 2.0 else 5.0 }
        val stops = PaceGradient.stops(line, speeds, ramp, maxStops = 10)!!
        assertEquals(SLOW, stops.first { it.progress in 0.01..0.3 }.color)
        assertEquals(FAST, stops.first { it.progress in 0.7..0.99 }.color)
    }

    @Test
    fun constantSpeedIsOneColour() {
        val stops = PaceGradient.stops(line, line.map { 3.0 }, ramp)!!
        assertEquals(2, stops.size)
        assertEquals(stops[0].color, stops[1].color)
    }

    @Test
    fun missingSpeedsAreFilledFromNeighbours() {
        val speeds: List<Double?> = line.indices.map { if (it % 3 == 0) null else if (it < 50) 2.0 else 5.0 }
        val stops = PaceGradient.stops(line, speeds, ramp, maxStops = 4)!!
        assertEquals(6, stops.size)
        assertEquals(SLOW, stops[1].color)
        assertEquals(FAST, stops[4].color)
    }

    @Test
    fun outliersDoNotFlattenTheRamp() {
        // One absurd GPS spike must not push everything else to the slow end.
        val speeds = line.indices.map { if (it == 10) 80.0 else if (it < 50) 2.0 else 5.0 }
        val stops = PaceGradient.stops(line, speeds, ramp, maxStops = 10)!!
        assertEquals(FAST, stops.first { it.progress in 0.7..0.99 }.color)
    }

    @Test
    fun colorLerpAndSample() {
        assertEquals(0xFF808080.toInt(), ColorMath.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5))
        assertEquals(SLOW, ColorMath.sample(ramp, -1.0))
        assertEquals(FAST, ColorMath.sample(ramp, 2.0))
        val three = listOf(0xFF000000.toInt(), 0xFF0000FF.toInt(), 0xFFFF0000.toInt())
        assertEquals(0xFF0000FF.toInt(), ColorMath.sample(three, 0.5))
    }

    private companion object {
        val SLOW = 0xFFFFFF00.toInt()
        val FAST = 0xFF0000FF.toInt()
    }
}
