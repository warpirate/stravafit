// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.geo.BoundingBox
import org.openactivity.model.LatLng
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraMathTest {
    private val pune = BoundingBox(south = 18.50, west = 73.80, north = 18.56, east = 73.90)

    @Test
    fun worldCoordinatesRoundTrip() {
        for (lat in listOf(-80.0, -33.9, 0.0, 18.52, 51.5, 84.0)) {
            assertEquals(lat, CameraMath.latitudeOf(CameraMath.worldY(lat)), 1e-9)
        }
        for (lon in listOf(-179.9, -0.1, 0.0, 73.85, 179.9)) {
            assertEquals(lon, CameraMath.longitudeOf(CameraMath.worldX(lon)), 1e-9)
        }
        assertEquals(0.5, CameraMath.worldY(0.0), 1e-12)
        assertEquals(0.0, CameraMath.worldX(-180.0), 1e-12)
    }

    @Test
    fun longitudeWrapsPastTheWorldEdge() {
        assertEquals(-179.0, CameraMath.longitudeOf(CameraMath.worldX(179.0) + 2.0 / 360.0), 1e-9)
        assertEquals(179.0, CameraMath.longitudeOf(-1.0 / 360.0), 1e-9)
    }

    @Test
    fun fitCentresTheBoxAndKeepsItInsideTheViewport() {
        val camera = CameraMath.fit(pune, 400f, 300f)
        assertEquals(73.85, camera.target.longitude, 1e-6)
        // Mercator: the centre in world space, not the arithmetic mean latitude.
        val midY = (CameraMath.worldY(18.50) + CameraMath.worldY(18.56)) / 2
        assertEquals(CameraMath.latitudeOf(midY), camera.target.latitude, 1e-9)
        assertCornersInside(pune, camera, 400f, 300f, MapPadding.None)
        assertTight(pune, camera, 400f, 300f, MapPadding.None)
    }

    @Test
    fun asymmetricPaddingCentresContentInTheRemainingArea() {
        val padding = MapPadding(left = 10f, top = 20f, right = 30f, bottom = 120f)
        val camera = CameraMath.fit(pune, 400f, 600f, padding)
        assertCornersInside(pune, camera, 400f, 600f, padding)
        assertTight(pune, camera, 400f, 600f, padding)
        val nw = CameraMath.project(LatLng(pune.north, pune.west), camera, 400f, 600f)
        val se = CameraMath.project(LatLng(pune.south, pune.east), camera, 400f, 600f)
        val contentCentreX = (padding.left + 400f - padding.right) / 2
        val contentCentreY = (padding.top + 600f - padding.bottom) / 2
        assertEquals(contentCentreX, (nw.x + se.x) / 2, 0.01f)
        assertEquals(contentCentreY, (nw.y + se.y) / 2, 0.01f)
    }

    @Test
    fun paddingThatLeavesNoRoomIsIgnored() {
        val withHugePadding = CameraMath.fit(pune, 200f, 200f, MapPadding.all(150f))
        val withoutPadding = CameraMath.fit(pune, 200f, 200f)
        assertEquals(withoutPadding, withHugePadding)
    }

    @Test
    fun singlePointUsesMaxZoom() {
        val p = LatLng(48.2, 16.37)
        val camera = CameraMath.fit(listOf(p), 300f, 300f, maxZoom = 15.0)!!
        assertEquals(15.0, camera.zoom, 0.0)
        assertEquals(p.latitude, camera.target.latitude, 1e-9)
        assertEquals(p.longitude, camera.target.longitude, 1e-9)
    }

    @Test
    fun zoomIsClampedToTheRange() {
        val world = BoundingBox(-80.0, -179.0, 80.0, 179.0)
        assertEquals(2.0, CameraMath.fit(world, 100f, 100f, minZoom = 2.0).zoom, 0.0)
    }

    @Test
    fun emptyPointListHasNoCamera() {
        assertNull(CameraMath.fit(emptyList<LatLng>(), 100f, 100f))
    }

    @Test
    fun antimeridianBoxGetsANarrowView() {
        // Taveuni, Fiji: a ride crossing 180°.
        val points = listOf(LatLng(-16.80, 179.90), LatLng(-16.85, -179.95), LatLng(-16.90, 179.95))
        val camera = CameraMath.fit(points, 400f, 400f)!!
        assertTrue("zoomed in, not a world view: ${camera.zoom}", camera.zoom > 9)
        assertTrue("centred near 180°: ${camera.target.longitude}", abs(abs(camera.target.longitude) - 180.0) < 0.1)
        for (p in points) {
            val o = CameraMath.project(p, camera, 400f, 400f)
            assertTrue("$p at $o", o.x in -EPS..400f + EPS && o.y in -EPS..400f + EPS)
        }
    }

    @Test
    fun cameraTargetProjectsToViewportCentre() {
        val camera = MapCamera(LatLng(18.52, 73.85), zoom = 14.0)
        val o = CameraMath.project(camera.target, camera, 320f, 200f)
        assertEquals(160f, o.x, 1e-3f)
        assertEquals(100f, o.y, 1e-3f)
    }

    @Test
    fun metersPerDpMatchesKnownScale() {
        // Equator, zoom 0: the 40 075 km circumference spans 512 dp.
        assertEquals(78_271.5, CameraMath.metersPerDp(0.0, 0.0), 1.0)
        assertEquals(CameraMath.metersPerDp(0.0, 10.0) / 2, CameraMath.metersPerDp(60.0, 10.0), 1e-6)
    }

    private fun assertCornersInside(b: BoundingBox, c: MapCamera, w: Float, h: Float, pad: MapPadding) {
        for (p in listOf(LatLng(b.north, b.west), LatLng(b.south, b.east), LatLng(b.north, b.east), LatLng(b.south, b.west))) {
            val o = CameraMath.project(p, c, w, h)
            assertTrue("$p -> $o", o.x >= pad.left - EPS && o.x <= w - pad.right + EPS)
            assertTrue("$p -> $o", o.y >= pad.top - EPS && o.y <= h - pad.bottom + EPS)
        }
    }

    /** One dimension of the box fills the padded area exactly (the zoom is as high as it can be). */
    private fun assertTight(b: BoundingBox, c: MapCamera, w: Float, h: Float, pad: MapPadding) {
        val nw = CameraMath.project(LatLng(b.north, b.west), c, w, h)
        val se = CameraMath.project(LatLng(b.south, b.east), c, w, h)
        val fillX = (se.x - nw.x) / (w - pad.left - pad.right)
        val fillY = (se.y - nw.y) / (h - pad.top - pad.bottom)
        assertEquals(1f, max(fillX, fillY), 1e-3f)
        assertTrue(min(fillX, fillY) <= 1f + 1e-3f)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
