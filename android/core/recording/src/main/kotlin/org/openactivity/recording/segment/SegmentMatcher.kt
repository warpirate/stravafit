// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.segment

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.recording.route.RoutePath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * Thresholds for matching an activity to a segment (segments design §3, Strava's published behaviour).
 *
 * @property gateMeters the track must pass this close to the segment's start and its finish.
 * @property corridorMeters a track point this close to the segment line counts as on it.
 * @property minOnTrackFraction share of the effort's points that must be on the line.
 * @property minLengthFactor the effort's recorded distance must be at least this share of the segment
 *   (no shortcuts across a loop).
 * @property maxLengthFactor and at most this multiple of it, plus [maxExtraMeters], or the attempt is given
 *   up (a detour, or the finish was never reached).
 */
public data class SegmentMatchConfig(
    val gateMeters: Double = 25.0,
    val corridorMeters: Double = 30.0,
    val minOnTrackFraction: Double = 0.9,
    val minLengthFactor: Double = 0.8,
    val maxLengthFactor: Double = 1.5,
    val maxExtraMeters: Double = 100.0,
)

/**
 * A segment prepared for matching: its line, length and an expanded bounding box for the prefilter.
 *
 * @throws IllegalArgumentException when fewer than two distinct points remain.
 */
public class SegmentShape(points: List<LatLng>) {
    /** The segment line, with along-track distances. */
    public val path: RoutePath = RoutePath(points)

    public val start: LatLng get() = path.start

    public val finish: LatLng get() = path.finish

    public val lengthMeters: Double get() = path.lengthMeters

    public val south: Double
    public val west: Double
    public val north: Double
    public val east: Double

    init {
        var s = Double.POSITIVE_INFINITY
        var w = Double.POSITIVE_INFINITY
        var n = Double.NEGATIVE_INFINITY
        var e = Double.NEGATIVE_INFINITY
        for (p in path.points) {
            s = minOf(s, p.latitude)
            n = maxOf(n, p.latitude)
            w = minOf(w, p.longitude)
            e = maxOf(e, p.longitude)
        }
        south = s
        west = w
        north = n
        east = e
    }

    /** True when the box around the segment, grown by [marginMeters], overlaps the given box. */
    public fun overlaps(south: Double, west: Double, north: Double, east: Double, marginMeters: Double): Boolean {
        val dLat = marginMeters / METERS_PER_DEGREE
        val dLon = dLat / cos(this.south * PI / 180.0).coerceAtLeast(MIN_COS)
        return this.south - dLat <= north && this.north + dLat >= south && this.west - dLon <= east && this.east + dLon >= west
    }
}

/**
 * An activity's track as parallel arrays (fast to scan for thousands of points).
 *
 * @property offsetMillis activity time of each point on the monotonic clock.
 * @property distanceMeters cumulative recorded distance at each point.
 * @property paused points taken while paused (they never count as moving time).
 */
public class SegmentTrack(
    public val latitudes: DoubleArray,
    public val longitudes: DoubleArray,
    public val offsetMillis: LongArray,
    public val distanceMeters: DoubleArray,
    public val paused: BooleanArray,
) {
    init {
        val n = latitudes.size
        require(longitudes.size == n && offsetMillis.size == n && distanceMeters.size == n && paused.size == n) { "arrays must have one entry per point" }
    }

    public val size: Int get() = latitudes.size

    public val south: Double = latitudes.minOrNull() ?: 0.0
    public val north: Double = latitudes.maxOrNull() ?: 0.0
    public val west: Double = longitudes.minOrNull() ?: 0.0
    public val east: Double = longitudes.maxOrNull() ?: 0.0

    public fun point(i: Int): LatLng = LatLng(latitudes[i], longitudes[i])
}

/**
 * One effort found on a segment.
 *
 * @property startIndex first track point inside the effort (just after the start gate).
 * @property endIndex last track point inside the effort (just before the finish gate).
 * @property startOffsetMillis activity time at the start gate, interpolated between two points.
 * @property endOffsetMillis activity time at the finish gate, interpolated between two points.
 * @property movingMillis [elapsedMillis] less the time spent paused.
 * @property distanceMeters recorded distance between the gates.
 * @property onTrackFraction share of the effort's points within the corridor.
 */
public data class SegmentMatch(
    val startIndex: Int,
    val endIndex: Int,
    val startOffsetMillis: Double,
    val endOffsetMillis: Double,
    val movingMillis: Double,
    val distanceMeters: Double,
    val onTrackFraction: Double,
) {
    public val elapsedMillis: Double get() = endOffsetMillis - startOffsetMillis
}

/**
 * Finds every effort on a segment in an activity's track.
 *
 * An effort starts where the track passes within [SegmentMatchConfig.gateMeters] of the segment's start
 * (the closest point of that pass) and ends at the first later pass within the gate of its finish, once
 * the athlete has come along the whole line. In between, at least [SegmentMatchConfig.minOnTrackFraction]
 * of the points must lie within [SegmentMatchConfig.corridorMeters] of the line, matched in order (the
 * along-track position only moves forward within a window), so going the wrong way, cutting across or
 * leaving the line part way never counts. Gate times are interpolated on the track leg closest to the
 * gate. After an effort the search carries on from its finish, so laps of a loop each count.
 *
 * Fast enough to run after every save: a box test drops segments far from the track, and the gate search
 * is a cheap degree comparison before any distance is computed. Pure and deterministic.
 */
public object SegmentMatcher {
    /** All efforts of [track] on [shape], in track order. */
    public fun match(track: SegmentTrack, shape: SegmentShape, config: SegmentMatchConfig = SegmentMatchConfig()): List<SegmentMatch> {
        val n = track.size
        if (n < 2) return emptyList()
        if (!shape.overlaps(track.south, track.west, track.north, track.east, config.gateMeters)) return emptyList()
        val startGate = Gate(shape.start, config.gateMeters)
        val finishGate = Gate(shape.finish, config.gateMeters)
        val results = ArrayList<SegmentMatch>(1)
        var i = 0
        while (i < n) {
            if (!startGate.contains(track, i)) {
                i++
                continue
            }
            // The pass through the start gate: the closest of its points starts the attempt.
            var runEnd = i
            var best = i
            var bestDistance = startGate.distance(track, i)
            while (runEnd + 1 < n && startGate.contains(track, runEnd + 1)) {
                runEnd++
                val d = startGate.distance(track, runEnd)
                if (d < bestDistance) {
                    bestDistance = d
                    best = runEnd
                }
            }
            val effort = attempt(track, shape, best, finishGate, config)
            if (effort != null) {
                results += effort
                // A loop's finish is the next lap's start, so the search resumes at the finish.
                i = maxOf(effort.endIndex, best + 1)
            } else {
                i = runEnd + 1
            }
        }
        return results
    }

    /** Follows the track from [s] along the segment; the effort, or null when it does not count. */
    private fun attempt(track: SegmentTrack, shape: SegmentShape, s: Int, finish: Gate, config: SegmentMatchConfig): SegmentMatch? {
        val n = track.size
        val path = shape.path
        val length = shape.lengthMeters
        if (!headsAlong(track, s, shape)) return null
        val (startOffset, startDistance) = gateCrossing(track, s, shape.start)
        val giveUpAt = startDistance + length * config.maxLengthFactor + config.maxExtraMeters
        val window = Window(path)
        var progress = 0.0
        var travelledAtMatch = track.distanceMeters[s]
        var onTrack = 0
        var total = 0
        var j = s
        while (j < n) {
            if (track.distanceMeters[j] > giveUpAt) return null
            total++
            val p = track.point(j)
            val ahead = progress + FORWARD_SLACK_METERS + FORWARD_FACTOR * (track.distanceMeters[j] - travelledAtMatch).coerceAtLeast(0.0)
            val projection = window.nearest(p, progress - BACK_WINDOW_METERS, ahead)
            if (projection != null && projection.second <= config.corridorMeters) {
                onTrack++
                progress = max(progress, projection.first)
                travelledAtMatch = track.distanceMeters[j]
            }
            if (j > s && progress >= length - config.gateMeters - FINISH_SLACK_METERS && finish.contains(track, j)) {
                // The pass through the finish gate: its closest point ends the effort.
                var e = j
                var bestDistance = finish.distance(track, j)
                var k = j
                while (k + 1 < n && finish.contains(track, k + 1)) {
                    k++
                    total++
                    onTrack++
                    val d = finish.distance(track, k)
                    if (d < bestDistance) {
                        bestDistance = d
                        e = k
                    }
                }
                // Points after the chosen one were counted but are not part of the effort.
                total -= k - e
                onTrack -= k - e
                return finishEffort(track, shape, s, e, startOffset, startDistance, onTrack.toDouble() / total, config)
            }
            j++
        }
        return null
    }

    private fun finishEffort(
        track: SegmentTrack,
        shape: SegmentShape,
        s: Int,
        e: Int,
        startOffset: Double,
        startDistance: Double,
        fraction: Double,
        config: SegmentMatchConfig,
    ): SegmentMatch? {
        if (fraction < config.minOnTrackFraction) return null
        val (endOffset, endDistance) = gateCrossing(track, e, shape.finish)
        val distance = endDistance - startDistance
        if (distance < shape.lengthMeters * config.minLengthFactor) return null
        if (endOffset <= startOffset) return null
        return SegmentMatch(
            startIndex = s,
            endIndex = e,
            startOffsetMillis = startOffset,
            endOffsetMillis = endOffset,
            movingMillis = moving(track, s, e, startOffset, endOffset),
            distanceMeters = distance,
            onTrackFraction = fraction,
        )
    }

    /**
     * Whether the track leaves point [s] in the segment's direction (within 90° of its first metres), so a
     * pass through the start gate the wrong way (the return leg of an out-and-back) never starts an attempt.
     */
    private fun headsAlong(track: SegmentTrack, s: Int, shape: SegmentShape): Boolean {
        var k = s + 1
        while (k < track.size && track.distanceMeters[k] - track.distanceMeters[s] < HEADING_METERS) k++
        if (k >= track.size) return false
        val trackBearing = Spherical.initialBearing(track.point(s), track.point(k))
        val segmentBearing = Spherical.initialBearing(shape.start, shape.path.pointAt(minOf(HEADING_METERS * 2, shape.lengthMeters)))
        val diff = abs(((trackBearing - segmentBearing) % 360.0 + 540.0) % 360.0 - 180.0)
        return diff <= MAX_HEADING_DEGREES
    }

    /**
     * Where the track crosses the gate at [gate] near point [i]: the foot of the perpendicular from the
     * gate centre on the closer of the legs into and out of [i], as (activity time, recorded distance).
     */
    internal fun gateCrossing(track: SegmentTrack, i: Int, gate: LatLng): Pair<Double, Double> {
        var bestT = 0.0
        var bestLeg = -1
        var bestD = Double.POSITIVE_INFINITY
        for (leg in intArrayOf(i - 1, i)) {
            if (leg < 0 || leg + 1 >= track.size) continue
            val (t, d) = footOnLeg(track, leg, gate)
            if (d < bestD) {
                bestD = d
                bestT = t
                bestLeg = leg
            }
        }
        if (bestLeg < 0) return track.offsetMillis[i].toDouble() to track.distanceMeters[i]
        val time = track.offsetMillis[bestLeg] + bestT * (track.offsetMillis[bestLeg + 1] - track.offsetMillis[bestLeg])
        val distance = track.distanceMeters[bestLeg] + bestT * (track.distanceMeters[bestLeg + 1] - track.distanceMeters[bestLeg])
        return time to distance
    }

    /** Position (0..1) of the foot of [p] on track leg [leg], and the distance to it, in a local plane. */
    private fun footOnLeg(track: SegmentTrack, leg: Int, p: LatLng): Pair<Double, Double> {
        val k = cos(p.latitude * PI / 180.0).coerceAtLeast(MIN_COS)
        val ax = (track.longitudes[leg] - p.longitude) * k * METERS_PER_DEGREE
        val ay = (track.latitudes[leg] - p.latitude) * METERS_PER_DEGREE
        val bx = (track.longitudes[leg + 1] - p.longitude) * k * METERS_PER_DEGREE
        val by = (track.latitudes[leg + 1] - p.latitude) * METERS_PER_DEGREE
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        return t to hypot(ax + t * dx, ay + t * dy)
    }

    /** Elapsed time between the gates minus the time spent paused (legs ending at a paused point). */
    private fun moving(track: SegmentTrack, s: Int, e: Int, startOffset: Double, endOffset: Double): Double {
        var pausedMillis = 0.0
        for (k in maxOf(s, 1)..e) {
            if (track.paused[k]) pausedMillis += (track.offsetMillis[k] - track.offsetMillis[k - 1]).toDouble()
        }
        return (endOffset - startOffset - pausedMillis).coerceAtLeast(0.0)
    }

    /** A circle around a gate, tested with a cheap degree box before the real distance. */
    private class Gate(val center: LatLng, val radius: Double) {
        private val dLat = radius / METERS_PER_DEGREE
        private val dLon = dLat / cos(center.latitude * PI / 180.0).coerceAtLeast(MIN_COS)

        fun contains(track: SegmentTrack, i: Int): Boolean {
            if (abs(track.latitudes[i] - center.latitude) > dLat) return false
            if (abs(track.longitudes[i] - center.longitude) > dLon) return false
            return distance(track, i) <= radius
        }

        fun distance(track: SegmentTrack, i: Int): Double =
            Spherical.distance(track.latitudes[i], track.longitudes[i], center.latitude, center.longitude)
    }

    /** The closest point of the segment to a position, among the segment legs reaching into a window. */
    private class Window(val path: RoutePath) {
        private var cursor = 0

        /** (along-track distance, cross-track distance) of the best leg in [from, to], or null. */
        fun nearest(p: LatLng, from: Double, to: Double): Pair<Double, Double>? {
            // Move the cursor back to the first leg reaching into the window, then scan forward.
            while (cursor > 0 && path.alongAtSegmentEnd(cursor - 1) >= from) cursor--
            while (cursor < path.segmentCount - 1 && path.alongAtSegmentEnd(cursor) < from) cursor++
            var bestAlong = 0.0
            var bestDistance = Double.POSITIVE_INFINITY
            var leg = cursor
            while (leg < path.segmentCount && path.alongAtSegmentStart(leg) <= to) {
                val projection = path.project(p, leg)
                if (projection.distanceMeters < bestDistance) {
                    bestDistance = projection.distanceMeters
                    bestAlong = projection.alongMeters
                }
                leg++
            }
            return if (bestDistance.isFinite()) bestAlong to bestDistance else null
        }
    }

    /** The direction test looks this far along the track and the segment. */
    private const val HEADING_METERS = 10.0

    /** The track must leave the start within this angle of the segment's direction. */
    private const val MAX_HEADING_DEGREES = 90.0

    /** Matches this far behind the current progress still count (GPS noise around a corner). */
    private const val BACK_WINDOW_METERS = 30.0

    /** The along-track position may run this far ahead of the recorded distance. */
    private const val FORWARD_SLACK_METERS = 60.0

    /** And grows by this factor of the distance recorded since the last match. */
    private const val FORWARD_FACTOR = 1.5

    /** The finish counts once progress is within the gate plus this of the end of the line. */
    private const val FINISH_SLACK_METERS = 10.0
}

internal const val METERS_PER_DEGREE: Double = Spherical.EARTH_RADIUS_M * PI / 180.0
internal const val MIN_COS: Double = 1e-6
