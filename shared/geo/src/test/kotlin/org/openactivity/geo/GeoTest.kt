// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.geo

import org.openactivity.model.LatLng
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SphericalTest {
    @Test
    fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.08, Spherical.distance(LatLng(0.0, 0.0), LatLng(1.0, 0.0)), 0.01)
    }

    @Test
    fun `known city pair distance`() {
        // Paris (48.8566, 2.3522) to London (51.5074, -0.1278): ~343.5 km on the mean sphere.
        val d = Spherical.distance(LatLng(48.8566, 2.3522), LatLng(51.5074, -0.1278))
        assertEquals(343_556.0, d, 500.0)
    }

    @Test
    fun `antimeridian neighbours are close, not a world apart`() {
        val d = Spherical.distance(LatLng(0.0, 179.9995), LatLng(0.0, -179.9995))
        assertEquals(111.2, d, 0.5)
    }

    @Test
    fun `bearing cardinal directions`() {
        val o = LatLng(10.0, 10.0)
        assertEquals(0.0, Spherical.initialBearing(o, LatLng(11.0, 10.0)), 1e-9)
        assertEquals(90.0, Spherical.initialBearing(LatLng(0.0, 0.0), LatLng(0.0, 1.0)), 1e-9)
        assertEquals(180.0, Spherical.initialBearing(o, LatLng(9.0, 10.0)), 1e-9)
        assertEquals(270.0, Spherical.initialBearing(LatLng(0.0, 0.0), LatLng(0.0, -1.0)), 1e-9)
        assertEquals(90.0, Spherical.initialBearing(LatLng(0.0, 179.9), LatLng(0.0, -179.9)), 1e-9)
    }

    @Test
    fun `destination inverts distance and bearing`() {
        val start = LatLng(47.0, 8.0)
        for (bearing in listOf(0.0, 37.0, 90.0, 181.0, 300.0)) {
            val end = Spherical.destination(start, bearing, 1234.5)
            assertEquals(1234.5, Spherical.distance(start, end), 1e-6)
            assertEquals(bearing, Spherical.initialBearing(start, end), 1e-6)
        }
    }

    @Test
    fun `destination wraps across the antimeridian`() {
        val end = Spherical.destination(LatLng(0.0, 179.99), 90.0, 2000.0)
        assertTrue(end.longitude < -179.9, "got ${end.longitude}")
    }

    @Test
    fun `longitude normalisation`() {
        assertEquals(-170.0, Spherical.normalizeLongitude(190.0), 1e-12)
        assertEquals(170.0, Spherical.normalizeLongitude(-190.0), 1e-12)
        assertEquals(180.0, Spherical.normalizeLongitude(540.0), 1e-12)
        assertEquals(10.0, Spherical.normalizeLongitude(370.0), 1e-12)
    }
}

class WebMercatorTest {
    @Test
    fun `origin and round trip`() {
        val o = WebMercator.project(LatLng(0.0, 0.0))
        assertEquals(0.0, o.x, 1e-9)
        assertEquals(0.0, o.y, 1e-6)
        val p = LatLng(52.52, 13.405)
        val back = WebMercator.unproject(WebMercator.project(p))
        assertEquals(p.latitude, back.latitude, 1e-9)
        assertEquals(p.longitude, back.longitude, 1e-9)
    }

    @Test
    fun `world edge and polar clamp`() {
        assertEquals(WebMercator.HALF_WORLD_M, WebMercator.project(LatLng(0.0, 180.0)).x, 1e-6)
        val pole = WebMercator.project(LatLng(90.0, 0.0))
        assertTrue(pole.y.isFinite())
        assertEquals(WebMercator.HALF_WORLD_M, pole.y, 1e-3)
        val svalbard = WebMercator.unproject(WebMercator.project(LatLng(78.2, 15.6)))
        assertEquals(78.2, svalbard.latitude, 1e-9)
    }

    @Test
    fun `normalised world coordinates`() {
        val (x, y) = WebMercator.toWorld(LatLng(0.0, 0.0))
        assertEquals(0.5, x, 1e-12)
        assertEquals(0.5, y, 1e-12)
        val (_, top) = WebMercator.toWorld(LatLng(WebMercator.MAX_LATITUDE, -180.0))
        assertEquals(0.0, top, 1e-9)
    }
}

class PlanarGeometryTest {
    @Test
    fun `point to segment distance projects inside the segment`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0, 0.001) // ~111 m east
        val p = Spherical.destination(LatLng(0.0, 0.0005), 0.0, 20.0)
        assertEquals(20.0, PlanarGeometry.pointToSegmentDistance(p, a, b), 0.01)
    }

    @Test
    fun `point beyond an end measures to the endpoint`() {
        val a = LatLng(0.0, 0.0)
        val b = LatLng(0.0, 0.001)
        val p = LatLng(0.0, 0.0015)
        assertEquals(Spherical.distance(p, b), PlanarGeometry.pointToSegmentDistance(p, a, b), 0.01)
        assertEquals(Spherical.distance(p, a), PlanarGeometry.pointToSegmentDistance(p, a, a), 0.01)
    }

    @Test
    fun `tangent plane round trip across the antimeridian`() {
        val plane = LocalTangentPlane(LatLng(-16.0, 179.999))
        val p = LatLng(-16.001, -179.998)
        val enu = plane.toEnu(p)
        assertTrue(enu[0] > 0 && enu[0] < 400)
        val back = plane.toLatLng(enu[0], enu[1])
        assertEquals(p.latitude, back.latitude, 1e-12)
        assertEquals(p.longitude, back.longitude, 1e-9)
    }
}

class BoundingBoxTest {
    @Test
    fun `empty input has no box`() {
        assertNull(BoundingBox.of(emptyList()))
    }

    @Test
    fun `ordinary box`() {
        val box = assertNotNull(BoundingBox.of(listOf(LatLng(1.0, 2.0), LatLng(-1.0, 5.0), LatLng(0.5, 3.0))))
        assertEquals(BoundingBox(-1.0, 2.0, 1.0, 5.0), box)
        assertFalse(box.crossesAntimeridian)
        assertTrue(LatLng(0.0, 4.0) in box)
        assertFalse(LatLng(0.0, 6.0) in box)
    }

    @Test
    fun `antimeridian box is narrow`() {
        val box = assertNotNull(BoundingBox.of(listOf(LatLng(0.0, 179.8), LatLng(0.1, -179.9), LatLng(0.0, 179.95))))
        assertTrue(box.crossesAntimeridian)
        assertEquals(179.8, box.west, 1e-12)
        assertEquals(-179.9, box.east, 1e-12)
        assertEquals(0.3, box.widthDegrees, 1e-9)
        assertTrue(LatLng(0.05, 180.0) in box)
        assertFalse(LatLng(0.05, 0.0) in box)
    }
}

class PrivacyTrimmerTest {
    /** A straight 5 km track due east with a point every 10 m. */
    private val track: List<LatLng> = (0..500).map { Spherical.destination(LatLng(45.0, 7.0), 90.0, it * 10.0) }

    @Test
    fun `no options keeps the whole track`() {
        val r = PrivacyTrimmer.trim(track, PrivacyTrimOptions())
        assertEquals(PrivacyTrimResult(0, 500, false), r)
        assertEquals(track, r.visible(track))
    }

    @Test
    fun `hide first and last N metres`() {
        val r = PrivacyTrimmer.trim(track, PrivacyTrimOptions(hideStartMeters = 195.0, hideEndMeters = 305.0))
        assertEquals(20, r.firstVisibleIndex)
        assertEquals(469, r.lastVisibleIndex) // 4690 m from start = 310 m before the end
        assertFalse(r.hideEntireMap)
    }

    @Test
    fun `zone at the start trims until the track leaves radius plus margin`() {
        val home = track[3]
        val r = PrivacyTrimmer.trim(
            track,
            PrivacyTrimOptions(zones = listOf(PrivacyZone(home, 100.0)), zoneMarginMeters = 50.0),
        )
        val firstVisible = track[r.firstVisibleIndex]
        assertTrue(Spherical.distance(home, firstVisible) > 150.0)
        assertTrue(Spherical.distance(home, track[r.firstVisibleIndex - 1]) <= 150.0)
        assertEquals(500, r.lastVisibleIndex)
    }

    @Test
    fun `zone at the end trims from the end`() {
        val home = track[498]
        val r = PrivacyTrimmer.trim(track, PrivacyTrimOptions(zones = listOf(PrivacyZone(home, 120.0))))
        assertEquals(0, r.firstVisibleIndex)
        assertTrue(Spherical.distance(home, track[r.lastVisibleIndex]) > 120.0)
        assertTrue(Spherical.distance(home, track[r.lastVisibleIndex + 1]) <= 120.0)
    }

    @Test
    fun `zone in the middle of the track is ignored`() {
        val r = PrivacyTrimmer.trim(track, PrivacyTrimOptions(zones = listOf(PrivacyZone(track[250], 100.0))))
        assertEquals(PrivacyTrimResult(0, 500, false), r)
    }

    @Test
    fun `too much trimmed hides the whole map`() {
        assertTrue(PrivacyTrimmer.trim(track, PrivacyTrimOptions(hideStartMeters = 2000.0, hideEndMeters = 1500.0)).hideEntireMap)
        val short = track.take(40) // 390 m
        assertTrue(PrivacyTrimmer.trim(short, PrivacyTrimOptions()).hideEntireMap)
        assertTrue(PrivacyTrimmer.trim(emptyList(), PrivacyTrimOptions()).hideEntireMap)
        assertTrue(PrivacyTrimmer.trim(track, PrivacyTrimOptions()).visible(track).isNotEmpty())
        assertTrue(abs(Spherical.pathLength(track) - 5000.0) < 1e-6)
    }
}
