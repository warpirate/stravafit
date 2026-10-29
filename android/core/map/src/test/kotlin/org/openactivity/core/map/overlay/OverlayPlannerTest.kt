// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.model.LatLng

class OverlayPlannerTest {
    private val palette = MapPalette(
        liveTrack = 0xFF0F7E8A.toInt(),
        plannedRoute = 0xFF2F6FB3.toInt(),
        activityRoute = 0xFF0B6A75.toInt(),
        casing = 0xFFFFFFFF.toInt(),
        marker = 0xFF0F7E8A.toInt(),
        puck = 0xFF0F7E8A.toInt(),
        puckAccuracy = 0xFF0F7E8A.toInt(),
        land = 0xFFE8EEEB.toInt(),
    )
    private val track = listOf(LatLng(18.50, 73.80), LatLng(18.51, 73.81), LatLng(18.52, 73.80))

    @Test
    fun emptyOverlaysStillDeclareEverySourceAndLayer() {
        val plan = OverlayPlanner.plan(MapOverlays.Empty, palette)
        assertEquals(5, plan.sources.size)
        assertTrue(plan.sources.all { it.geoJson == GeoJson.EMPTY })
        assertEquals(10, plan.layers.size)
        assertEquals("layer ids are unique", plan.layers.size, plan.layers.map { it.id }.toSet().size)
        val sourceIds = plan.sources.map { it.id }.toSet()
        assertTrue("every layer has a source", plan.layers.all { it.sourceId in sourceIds })
    }

    @Test
    fun drawOrderIsPlannedThenActivityThenLiveThenMarkers() {
        val ids = OverlayPlanner.plan(MapOverlays.Empty, palette).layers.map { it.id }
        fun at(id: String) = ids.indexOf(id)
        assertTrue(at(OverlayIds.PLANNED_LINE) < at(OverlayIds.ACTIVITY_CASING))
        assertTrue(at(OverlayIds.ACTIVITY_GRADIENT_LINE) < at(OverlayIds.LIVE_CASING))
        assertTrue(at(OverlayIds.LIVE_LINE) < at(OverlayIds.START_MARKER))
        // Each casing sits directly under its line.
        assertEquals(at(OverlayIds.PLANNED_CASING) + 1, at(OverlayIds.PLANNED_LINE))
        assertEquals(at(OverlayIds.LIVE_CASING) + 1, at(OverlayIds.LIVE_LINE))
    }

    @Test
    fun casingIsWiderThanItsLine() {
        val lines = OverlayPlanner.plan(MapOverlays.Empty, palette).layers.filterIsInstance<LineLayerSpec>().associateBy { it.id }
        assertTrue(lines.getValue(OverlayIds.LIVE_CASING).widthDp > lines.getValue(OverlayIds.LIVE_LINE).widthDp)
        assertTrue(lines.getValue(OverlayIds.PLANNED_CASING).widthDp > lines.getValue(OverlayIds.PLANNED_LINE).widthDp)
    }

    @Test
    fun plannedRouteHasItsOwnColour() {
        val plan = OverlayPlanner.plan(MapOverlays(liveTrack = track, plannedRoute = track), palette)
        val lines = plan.layers.filterIsInstance<LineLayerSpec>().associateBy { it.id }
        assertNotEquals(lines.getValue(OverlayIds.LIVE_LINE).color, lines.getValue(OverlayIds.PLANNED_LINE).color)
        assertEquals(palette.plannedRoute, lines.getValue(OverlayIds.PLANNED_LINE).color)
        assertTrue(plan.source(OverlayIds.LIVE_SOURCE).geoJson.contains("LineString"))
        assertTrue(plan.source(OverlayIds.PLANNED_SOURCE).geoJson.contains("LineString"))
    }

    @Test
    fun activityWithoutSpeedsUsesTheSolidLine() {
        val plan = OverlayPlanner.plan(MapOverlays(activityRoute = ActivityRouteOverlay(track)), palette)
        assertTrue(plan.source(OverlayIds.ACTIVITY_SOURCE).geoJson.contains("LineString"))
        assertEquals(GeoJson.EMPTY, plan.source(OverlayIds.ACTIVITY_GRADIENT_SOURCE).geoJson)
        assertNull(plan.line(OverlayIds.ACTIVITY_GRADIENT_LINE).gradient)
    }

    @Test
    fun activityWithSpeedsUsesTheGradientSourceWithLineMetrics() {
        val activity = ActivityRouteOverlay(track, speedsMetersPerSecond = listOf(2.0, 3.0, 4.0))
        val plan = OverlayPlanner.plan(MapOverlays(activityRoute = activity), palette)
        assertEquals(GeoJson.EMPTY, plan.source(OverlayIds.ACTIVITY_SOURCE).geoJson)
        val gradientSource = plan.source(OverlayIds.ACTIVITY_GRADIENT_SOURCE)
        assertTrue(gradientSource.lineMetrics)
        assertTrue(gradientSource.geoJson.contains("LineString"))
        assertNotNull(plan.line(OverlayIds.ACTIVITY_GRADIENT_LINE).gradient)
    }

    @Test
    fun paceColouringCanBeTurnedOff() {
        val activity = ActivityRouteOverlay(track, speedsMetersPerSecond = listOf(2.0, 3.0, 4.0), colorByPace = false)
        val plan = OverlayPlanner.plan(MapOverlays(activityRoute = activity), palette)
        assertTrue(plan.source(OverlayIds.ACTIVITY_SOURCE).geoJson.contains("LineString"))
        assertNull(plan.line(OverlayIds.ACTIVITY_GRADIENT_LINE).gradient)
    }

    @Test
    fun startIsGreenAndFinishIsAChequeredFlagWithWhiteRings() {
        val plan = OverlayPlanner.plan(MapOverlays(activityRoute = ActivityRouteOverlay(track)), palette)
        val markers = plan.source(OverlayIds.MARKER_SOURCE).geoJson
        assertTrue(markers.contains("\"role\":\"start\"") && markers.contains("\"role\":\"finish\""))
        val start = plan.layers.filterIsInstance<CircleLayerSpec>().single { it.role == OverlayIds.ROLE_START }
        val finish = plan.layers.filterIsInstance<FlagLayerSpec>().single { it.role == OverlayIds.ROLE_FINISH }
        assertEquals(palette.start, start.fillColor)
        assertEquals(palette.markerRing, start.strokeColor)
        assertEquals(palette.finish, finish.darkColor)
        assertEquals(palette.markerRing, finish.ringColor)
    }

    @Test
    fun liveTrackGetsAGreenStartMarker() {
        val plan = OverlayPlanner.plan(MapOverlays(liveTrack = track), palette)
        val markers = plan.source(OverlayIds.MARKER_SOURCE).geoJson
        assertTrue(markers.contains("\"role\":\"start\""))
        assertTrue(!markers.contains("\"role\":\"finish\""))
    }

    @Test
    fun endpointsCanBeHidden() {
        val plan = OverlayPlanner.plan(MapOverlays(activityRoute = ActivityRouteOverlay(track, showEndpoints = false)), palette)
        assertEquals(GeoJson.EMPTY, plan.source(OverlayIds.MARKER_SOURCE).geoJson)
    }

    @Test
    fun allPointsCombinesEveryOverlay() {
        val overlays = MapOverlays(liveTrack = track.take(1), plannedRoute = track, activityRoute = ActivityRouteOverlay(track))
        assertEquals(7, overlays.allPoints.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun speedsMustMatchPoints() {
        ActivityRouteOverlay(track, speedsMetersPerSecond = listOf(1.0))
    }

    private fun OverlayPlan.source(id: String) = sources.single { it.id == id }
    private fun OverlayPlan.line(id: String) = layers.filterIsInstance<LineLayerSpec>().single { it.id == id }
}
