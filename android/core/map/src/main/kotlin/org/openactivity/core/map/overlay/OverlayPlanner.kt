// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.overlay

import org.openactivity.core.map.overlay.OverlayIds.ACTIVITY_CASING
import org.openactivity.core.map.overlay.OverlayIds.ACTIVITY_GRADIENT_CASING
import org.openactivity.core.map.overlay.OverlayIds.ACTIVITY_GRADIENT_LINE
import org.openactivity.core.map.overlay.OverlayIds.ACTIVITY_GRADIENT_SOURCE
import org.openactivity.core.map.overlay.OverlayIds.ACTIVITY_LINE
import org.openactivity.core.map.overlay.OverlayIds.ACTIVITY_SOURCE
import org.openactivity.core.map.overlay.OverlayIds.FINISH_MARKER
import org.openactivity.core.map.overlay.OverlayIds.LIVE_CASING
import org.openactivity.core.map.overlay.OverlayIds.LIVE_LINE
import org.openactivity.core.map.overlay.OverlayIds.LIVE_SOURCE
import org.openactivity.core.map.overlay.OverlayIds.MARKER_SOURCE
import org.openactivity.core.map.overlay.OverlayIds.PLANNED_CASING
import org.openactivity.core.map.overlay.OverlayIds.PLANNED_LINE
import org.openactivity.core.map.overlay.OverlayIds.PLANNED_SOURCE
import org.openactivity.core.map.overlay.OverlayIds.START_MARKER

/**
 * Turns [MapOverlays] into an [OverlayPlan]. Pure: no MapLibre calls, so it can run off the main
 * thread and be unit tested.
 *
 * Draw order, bottom to top: planned route, finished activity, live track, then start and finish
 * markers. Each line sits on a wider casing so it stays legible on any base map.
 */
object OverlayPlanner {
    /** Line widths in dp. */
    const val LINE_WIDTH: Float = 4f
    const val CASING_EXTRA: Float = 3f
    const val PLANNED_WIDTH: Float = 5f
    const val MARKER_RADIUS: Float = 6f
    const val MARKER_STROKE: Float = 3f
    /** The chequered flag is a little larger than the start dot so its squares read. */
    const val FLAG_RADIUS: Float = 9f
    const val FLAG_RING: Float = 2.5f

    /** Builds the plan for [overlays] coloured with [palette]. */
    fun plan(overlays: MapOverlays, palette: MapPalette): OverlayPlan {
        val activity = overlays.activityRoute
        val gradient = activity
            ?.takeIf { it.colorByPace }
            ?.speedsMetersPerSecond
            ?.let { PaceGradient.stops(activity.points, it, palette.paceRamp) }
        val activityJson = activity?.let { GeoJson.lineString(it.points) } ?: GeoJson.EMPTY

        val markers = activity
            ?.takeIf { it.showEndpoints && it.points.size >= 2 }
            ?.let { listOf(it.points.first() to OverlayIds.ROLE_START, it.points.last() to OverlayIds.ROLE_FINISH) }
            // While recording, the live track gets its green start marker (the puck marks its head).
            ?: overlays.liveTrack.firstOrNull()?.takeIf { overlays.showLiveStart }?.let { listOf(it to OverlayIds.ROLE_START) }
            ?: emptyList()

        val sources = listOf(
            SourceSpec(PLANNED_SOURCE, GeoJson.lineString(overlays.plannedRoute)),
            SourceSpec(ACTIVITY_SOURCE, if (gradient == null) activityJson else GeoJson.EMPTY),
            SourceSpec(ACTIVITY_GRADIENT_SOURCE, if (gradient != null) activityJson else GeoJson.EMPTY, lineMetrics = true),
            SourceSpec(LIVE_SOURCE, GeoJson.lineString(overlays.liveTrack)),
            SourceSpec(MARKER_SOURCE, GeoJson.rolePoints(markers)),
        )

        val casingWidth = LINE_WIDTH + CASING_EXTRA
        val layers = listOf(
            LineLayerSpec(PLANNED_CASING, PLANNED_SOURCE, palette.casing, PLANNED_WIDTH + CASING_EXTRA, opacity = 0.9f),
            LineLayerSpec(PLANNED_LINE, PLANNED_SOURCE, palette.plannedRoute, PLANNED_WIDTH, opacity = 0.9f),
            LineLayerSpec(ACTIVITY_CASING, ACTIVITY_SOURCE, palette.casing, casingWidth),
            LineLayerSpec(ACTIVITY_LINE, ACTIVITY_SOURCE, palette.activityRoute, LINE_WIDTH),
            LineLayerSpec(ACTIVITY_GRADIENT_CASING, ACTIVITY_GRADIENT_SOURCE, palette.casing, casingWidth),
            LineLayerSpec(
                ACTIVITY_GRADIENT_LINE,
                ACTIVITY_GRADIENT_SOURCE,
                palette.activityRoute,
                LINE_WIDTH,
                gradient = gradient,
            ),
            LineLayerSpec(LIVE_CASING, LIVE_SOURCE, palette.casing, casingWidth),
            LineLayerSpec(LIVE_LINE, LIVE_SOURCE, palette.liveTrack, LINE_WIDTH),
            // A green start and a chequered finish flag, each with a white ring (strava direction).
            CircleLayerSpec(START_MARKER, MARKER_SOURCE, OverlayIds.ROLE_START, MARKER_RADIUS, palette.start, palette.markerRing, MARKER_STROKE),
            FlagLayerSpec(FINISH_MARKER, MARKER_SOURCE, OverlayIds.ROLE_FINISH, FLAG_RADIUS, palette.finish, palette.markerRing, FLAG_RING),
        )
        return OverlayPlan(sources, layers)
    }
}
