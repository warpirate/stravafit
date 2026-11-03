// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.segment

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.recording.route.RouteFollower
import org.openactivity.recording.route.RouteFollowerConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * A starred segment watched while recording.
 *
 * @property prElapsedMillis your fastest time on it, or null before a first effort.
 */
public class LiveSegment(
    public val id: Long,
    public val name: String,
    public val shape: SegmentShape,
    public val prElapsedMillis: Long?,
)

/**
 * Thresholds for live segments.
 *
 * @property approachMeters the "segment ahead" banner shows from this far from the start.
 * @property resetMeters moving this far from a start allows its banner (and cue) again.
 * @property resultMillis how long the result stays on screen after the finish.
 */
public data class LiveSegmentConfig(
    val approachMeters: Double = 200.0,
    val resetMeters: Double = 300.0,
    val gateMeters: Double = 25.0,
    val maxLengthFactor: Double = 1.5,
    val maxExtraMeters: Double = 100.0,
    val resultMillis: Long = 30_000L,
    val follower: RouteFollowerConfig = RouteFollowerConfig(
        offRouteMeters = 50.0,
        offRouteSeconds = 10.0,
        backOnRouteMeters = 30.0,
        finishMeters = 35.0,
    ),
)

/** What a live segment is doing. */
public enum class LiveSegmentPhase {
    /** Within [LiveSegmentConfig.approachMeters] of the start. */
    APPROACHING,

    /** Through the start gate: the clock is running. */
    ACTIVE,

    /** Through the finish gate: the result is shown. */
    FINISHED,
}

/**
 * The live segment to show, ready to draw.
 *
 * @property distanceToStartMeters while approaching, the straight-line distance to the start.
 * @property elapsedMillis time since the start gate (while active), or the final time (finished).
 * @property deltaMillis elapsed time minus your PR's time at the same share of the segment: negative is
 *   ahead of your PR. Null without a PR.
 * @property isNewPr finished faster than the PR (or a first effort when [prElapsedMillis] is null: false).
 */
public data class LiveSegmentStatus(
    val segmentId: Long,
    val name: String,
    val phase: LiveSegmentPhase,
    val lengthMeters: Double,
    val distanceToStartMeters: Double? = null,
    val progressMeters: Double = 0.0,
    val elapsedMillis: Long = 0L,
    val prElapsedMillis: Long? = null,
    val deltaMillis: Long? = null,
    val isNewPr: Boolean = false,
) {
    /** Share of the segment done, 0 to 1. */
    public val fraction: Double get() = if (lengthMeters > 0.0) (progressMeters / lengthMeters).coerceIn(0.0, 1.0) else 0.0
}

/** Something to say about a live segment. */
public sealed interface LiveSegmentEvent {
    public val segmentId: Long
    public val name: String

    public data class Approaching(override val segmentId: Long, override val name: String, val distanceMeters: Double) : LiveSegmentEvent

    /** Through the start gate; [prElapsedMillis] is the time to beat. */
    public data class Started(override val segmentId: Long, override val name: String, val prElapsedMillis: Long?) : LiveSegmentEvent

    /** Through the finish; [deltaMillis] is against the PR (negative is faster), null without one. */
    public data class Finished(
        override val segmentId: Long,
        override val name: String,
        val elapsedMillis: Long,
        val deltaMillis: Long?,
        val isNewPr: Boolean,
    ) : LiveSegmentEvent

    /** Left the segment before its finish. */
    public data class Abandoned(override val segmentId: Long, override val name: String) : LiveSegmentEvent
}

/**
 * Live personal segments while recording (parity Tier C): a banner when a starred segment's start is
 * near, a running clock against your PR once through its start gate, and the result at its finish.
 *
 * Progress along the active segment is a [RouteFollower] on the segment line, so the same in-order
 * matching and off-route hysteresis as route following apply: leaving the line abandons the attempt.
 * Only one segment runs at a time. The final time on the saved activity comes from the post-save matcher
 * (interpolated gates), so the live one may differ by a second.
 *
 * Deterministic and free of clocks: time comes in with each fix, so a restored recording can be replayed.
 */
public class LiveSegmentTracker(
    private val segments: List<LiveSegment>,
    private val config: LiveSegmentConfig = LiveSegmentConfig(),
) {
    private class Active(
        val segment: LiveSegment,
        val follower: RouteFollower,
        val startOffsetMillis: Long,
        val startTravelled: Double,
    )

    private var active: Active? = null
    private val approached = HashSet<Long>()
    private var finishedAt: Long? = null

    /** What to show, or null when no segment is near. */
    public var status: LiveSegmentStatus? = null
        private set

    /** True when there is nothing to watch. */
    public val isEmpty: Boolean get() = segments.isEmpty()

    /**
     * Processes one fix.
     *
     * @param offsetMillis recording time of the fix on the monotonic clock.
     * @param travelledMeters the recording's distance so far.
     */
    public fun onFix(position: LatLng, offsetMillis: Long, travelledMeters: Double, accuracyMeters: Double? = null): List<LiveSegmentEvent> {
        if (segments.isEmpty()) return emptyList()
        val events = ArrayList<LiveSegmentEvent>(1)
        val running = active
        if (running != null) {
            follow(running, position, offsetMillis, travelledMeters, accuracyMeters, events)
            if (active != null) return events
        }
        lookForStart(position, offsetMillis, travelledMeters, events)
        return events
    }

    private fun follow(a: Active, p: LatLng, offset: Long, travelled: Double, accuracy: Double?, events: MutableList<LiveSegmentEvent>) {
        val seg = a.segment
        val length = seg.shape.lengthMeters
        val progress = a.follower.onFix(p, offset * NANOS_PER_MILLI, travelled, accuracy).progress
        val elapsed = offset - a.startOffsetMillis
        val tooFar = travelled - a.startTravelled > length * config.maxLengthFactor + config.maxExtraMeters
        if (progress.offRoute || tooFar) {
            active = null
            status = null
            events += LiveSegmentEvent.Abandoned(seg.id, seg.name)
            return
        }
        val atFinish = progress.progressMeters >= length - config.gateMeters - FINISH_SLACK_METERS &&
            Spherical.distance(p, seg.shape.finish) <= config.gateMeters
        val pr = seg.prElapsedMillis
        if (atFinish) {
            val delta = pr?.let { elapsed - it }
            val isPr = pr != null && elapsed < pr
            active = null
            finishedAt = offset
            approached.add(seg.id)
            status = LiveSegmentStatus(seg.id, seg.name, LiveSegmentPhase.FINISHED, length, progressMeters = length, elapsedMillis = elapsed, prElapsedMillis = pr, deltaMillis = delta, isNewPr = isPr)
            events += LiveSegmentEvent.Finished(seg.id, seg.name, elapsed, delta, isPr)
            return
        }
        status = LiveSegmentStatus(
            segmentId = seg.id,
            name = seg.name,
            phase = LiveSegmentPhase.ACTIVE,
            lengthMeters = length,
            progressMeters = progress.progressMeters,
            elapsedMillis = elapsed,
            prElapsedMillis = pr,
            deltaMillis = pr?.let { elapsed - (it * progress.fraction).toLong() },
        )
    }

    private fun lookForStart(p: LatLng, offset: Long, travelled: Double, events: MutableList<LiveSegmentEvent>) {
        var nearest: LiveSegment? = null
        var nearestDistance = Double.POSITIVE_INFINITY
        for (seg in segments) {
            val d = roughDistance(p, seg.shape.start)
            if (d == null) {
                approached.remove(seg.id)
                continue
            }
            if (d > config.resetMeters) approached.remove(seg.id)
            if (d < nearestDistance) {
                nearestDistance = d
                nearest = seg
            }
        }
        val seg = nearest
        if (seg != null && nearestDistance <= config.gateMeters) {
            val follower = RouteFollower(seg.shape.path, config = config.follower)
            follower.onFix(p, offset * NANOS_PER_MILLI, travelled)
            active = Active(seg, follower, offset, travelled)
            finishedAt = null
            approached.add(seg.id)
            status = LiveSegmentStatus(seg.id, seg.name, LiveSegmentPhase.ACTIVE, seg.shape.lengthMeters, prElapsedMillis = seg.prElapsedMillis, deltaMillis = seg.prElapsedMillis?.let { 0L })
            events += LiveSegmentEvent.Started(seg.id, seg.name, seg.prElapsedMillis)
            return
        }
        val shownResult = status?.takeIf { it.phase == LiveSegmentPhase.FINISHED }
        if (shownResult != null && finishedAt?.let { offset - it < config.resultMillis } == true) return
        finishedAt = null
        if (seg != null && nearestDistance <= config.approachMeters) {
            if (seg.id !in approached) {
                approached.add(seg.id)
                events += LiveSegmentEvent.Approaching(seg.id, seg.name, nearestDistance)
            }
            status = LiveSegmentStatus(seg.id, seg.name, LiveSegmentPhase.APPROACHING, seg.shape.lengthMeters, distanceToStartMeters = nearestDistance, prElapsedMillis = seg.prElapsedMillis)
        } else {
            status = null
        }
    }

    /** Distance to [target], or null when it is clearly beyond the reset radius (no trigonometry). */
    private fun roughDistance(p: LatLng, target: LatLng): Double? {
        val dLat = config.resetMeters / METERS_PER_DEGREE
        if (abs(p.latitude - target.latitude) > dLat) return null
        if (abs(p.longitude - target.longitude) > dLat / cos(target.latitude * PI / 180.0).coerceAtLeast(MIN_COS)) return null
        return Spherical.distance(p, target)
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val FINISH_SLACK_METERS = 10.0
    }
}
