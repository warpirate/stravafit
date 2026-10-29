// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.recording.route

import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng

/**
 * Thresholds for following a route.
 *
 * Off-route uses hysteresis (record detail 10.2): the athlete is off route after more than [offRouteMeters]
 * from the line for at least [offRouteSeconds], and back on it once within [backOnRouteMeters]. GPS drift
 * between the two never flips the state.
 *
 * @property matchMeters a fix this close to the route near the last matched place counts as progress.
 * @property rejoinMeters a fix this close to the route elsewhere (after a shortcut or a detour) rejoins it.
 * @property backtrackMeters going back along the route up to this far keeps progress where it was instead
 *   of matching a later pass through the same place (out-and-back, figure-eight).
 * @property maxAccuracyMeters fixes worse than this neither move progress nor change the off-route state.
 * @property finishMeters progress this close to the end counts as the route completed.
 */
public data class RouteFollowerConfig(
    val offRouteMeters: Double = 40.0,
    val offRouteSeconds: Double = 10.0,
    val backOnRouteMeters: Double = 25.0,
    val matchMeters: Double = 40.0,
    val rejoinMeters: Double = 25.0,
    val backtrackMeters: Double = 500.0,
    val maxAccuracyMeters: Double = 35.0,
    val finishMeters: Double = 30.0,
) {
    init {
        require(backOnRouteMeters < offRouteMeters) { "hysteresis needs back-on < off-route" }
        require(offRouteSeconds >= 0.0) { "off-route delay must not be negative" }
    }
}

/**
 * Everything a [RouteFollower] needs to carry on after the process died, journalled with the recording.
 *
 * @property progressMeters distance along the route reached so far; it never goes down.
 * @property joined the athlete has been on the route at least once.
 * @property offRoute an off-route alert is active.
 * @property farSinceNanos when the current stretch beyond the off-route distance began, if one is running.
 * @property travelledAtMatch recorded distance when progress was last matched (bounds how far ahead the
 *   next match may be).
 * @property distanceToRouteMeters cross-track distance at the last usable fix.
 * @property completed the end of the route was reached.
 */
public data class RouteFollowerState(
    val progressMeters: Double = 0.0,
    val joined: Boolean = false,
    val offRoute: Boolean = false,
    val farSinceNanos: Long? = null,
    val travelledAtMatch: Double = 0.0,
    val distanceToRouteMeters: Double? = null,
    val completed: Boolean = false,
)

/** Something worth telling the athlete about while following a route. */
public sealed interface RouteEvent {
    /** Left the route; [distanceMeters] is the way back to it. */
    public data class OffRoute(val distanceMeters: Double) : RouteEvent

    /** Back on the route after being off it. */
    public data object BackOnRoute : RouteEvent

    /** Reached the end of the route. */
    public data object Completed : RouteEvent
}

/**
 * Progress along a followed route, ready to show.
 *
 * @property remainingMeters route distance still to go.
 * @property distanceToRouteMeters cross-track distance, null before the first usable fix.
 */
public data class RouteProgress(
    val routeLengthMeters: Double,
    val progressMeters: Double,
    val distanceToRouteMeters: Double?,
    val joined: Boolean,
    val offRoute: Boolean,
    val completed: Boolean,
) {
    public val remainingMeters: Double get() = (routeLengthMeters - progressMeters).coerceAtLeast(0.0)

    /** Share of the route done, 0 to 1. */
    public val fraction: Double get() = if (routeLengthMeters > 0.0) (progressMeters / routeLengthMeters).coerceIn(0.0, 1.0) else 0.0
}

/** The result of one fix: the new progress and any events it caused. */
public data class RouteUpdate(val progress: RouteProgress, val events: List<RouteEvent>)

/**
 * Follows a route from successive positions: matches each fix to the route, keeps progress monotonic,
 * and decides when the athlete is off route.
 *
 * Matching searches near the last matched place first: from a little behind the current progress to as far
 * ahead as the athlete could have gone (the recorded distance since the last match, with slack). That keeps
 * a loop's finish, the return leg of an out-and-back or the second pass of a figure-eight from being taken
 * for the current place. Only when nothing nearby matches is the whole route searched, preferring the
 * earliest place ahead (a shortcut or a rejoin after a detour); a short backtrack keeps progress as it was.
 *
 * Deterministic and free of I/O and clocks: time comes in with each fix, so it is replayable in tests and
 * restorable from [state] after the process died.
 */
public class RouteFollower(
    public val path: RoutePath,
    initial: RouteFollowerState = RouteFollowerState(),
    private val config: RouteFollowerConfig = RouteFollowerConfig(),
) {
    /** The state to journal. */
    public var state: RouteFollowerState = initial
        private set

    /** Progress as of the last fix. */
    public val progress: RouteProgress get() = progressOf(state)

    /**
     * Processes one fix.
     *
     * @param position the (smoothed) position.
     * @param monotonicNanos when it was taken, on a clock that never jumps.
     * @param travelledMeters the recording's distance so far.
     * @param accuracyMeters the fix's reported accuracy, if any.
     */
    public fun onFix(position: LatLng, monotonicNanos: Long, travelledMeters: Double, accuracyMeters: Double? = null): RouteUpdate {
        if (accuracyMeters != null && (!accuracyMeters.isFinite() || accuracyMeters > config.maxAccuracyMeters)) {
            return RouteUpdate(progress, emptyList())
        }
        val projections = path.projectAll(position)
        val nearest = projections.minBy { it.distanceMeters }
        val events = ArrayList<RouteEvent>(2)
        var s = match(position, projections, travelledMeters).copy(distanceToRouteMeters = nearest.distanceMeters)
        s = offRoute(s, nearest.distanceMeters, monotonicNanos, events)
        if (!s.completed && s.joined && s.progressMeters >= path.lengthMeters - config.finishMeters) {
            s = s.copy(completed = true)
            events += RouteEvent.Completed
        }
        state = s
        return RouteUpdate(progressOf(s), events)
    }

    private fun match(position: LatLng, projections: List<RouteProjection>, travelled: Double): RouteFollowerState {
        val s = state
        if (!s.joined) {
            // First contact: the earliest place within reach (the start of a loop, not its finish).
            val first = projections.firstOrNull { it.distanceMeters <= config.matchMeters } ?: return s
            return s.copy(progressMeters = first.alongMeters, joined = true, travelledAtMatch = travelled)
        }
        val behind = s.progressMeters - BACK_WINDOW_METERS
        val ahead = s.progressMeters + FORWARD_SLACK_METERS +
            FORWARD_FACTOR * (travelled - s.travelledAtMatch).coerceAtLeast(0.0)
        val candidates = projections
            .filter { path.alongAtSegmentEnd(it.segment) >= behind && path.alongAtSegmentStart(it.segment) <= ahead }
            .map { p ->
                // A long segment reaching into the window: only its part inside the window counts.
                if (p.alongMeters in behind..ahead) {
                    p
                } else {
                    val along = p.alongMeters.coerceIn(behind, ahead)
                    p.copy(alongMeters = along, distanceMeters = Spherical.distance(position, path.pointAt(along)))
                }
            }
        val closest = candidates.minByOrNull { it.distanceMeters }
        // Where two stretches lie on top of each other (the turn of an out-and-back), take the earlier one
        // until the athlete is clearly on the later one.
        val local = closest?.let { c -> candidates.filter { it.distanceMeters <= c.distanceMeters + TIE_METERS }.minBy { it.alongMeters } }
        if (local != null && closest.distanceMeters <= config.matchMeters) {
            return s.copy(progressMeters = maxOf(s.progressMeters, local.alongMeters), travelledAtMatch = travelled)
        }
        val close = projections.filter { it.distanceMeters <= config.rejoinMeters }
        if (close.isEmpty()) return s
        // Back on a stretch already covered (a short backtrack): stay where progress was.
        if (close.any { it.alongMeters in (s.progressMeters - config.backtrackMeters)..s.progressMeters }) {
            return s.copy(travelledAtMatch = travelled)
        }
        val forward = close.filter { it.alongMeters >= s.progressMeters }.minByOrNull { it.alongMeters } ?: return s
        return s.copy(progressMeters = forward.alongMeters, travelledAtMatch = travelled)
    }

    private fun offRoute(s: RouteFollowerState, distance: Double, now: Long, events: MutableList<RouteEvent>): RouteFollowerState = when {
        distance > config.offRouteMeters -> {
            val since = s.farSinceNanos ?: now
            if (!s.offRoute && (now - since) / NANOS_PER_SECOND >= config.offRouteSeconds) {
                events += RouteEvent.OffRoute(distance)
                s.copy(offRoute = true, farSinceNanos = since)
            } else {
                s.copy(farSinceNanos = since)
            }
        }
        distance < config.backOnRouteMeters -> {
            if (s.offRoute) events += RouteEvent.BackOnRoute
            s.copy(offRoute = false, farSinceNanos = null)
        }
        // Between the two thresholds: an alert stays on, a countdown towards one starts over.
        else -> if (s.offRoute) s else s.copy(farSinceNanos = null)
    }

    private fun progressOf(s: RouteFollowerState) = RouteProgress(
        routeLengthMeters = path.lengthMeters,
        progressMeters = s.progressMeters.coerceIn(0.0, path.lengthMeters),
        distanceToRouteMeters = s.distanceToRouteMeters,
        joined = s.joined,
        offRoute = s.offRoute,
        completed = s.completed,
    )

    private companion object {
        const val NANOS_PER_SECOND = 1e9

        /** Matches this far behind the current progress still count (GPS noise around a corner). */
        const val BACK_WINDOW_METERS = 30.0

        /** Progress may run this far ahead of the recorded distance (route vs track geometry differ). */
        const val FORWARD_SLACK_METERS = 60.0

        /** And grows by this factor of the distance recorded since the last match. */
        const val FORWARD_FACTOR = 1.5

        /** Candidates this much further away than the closest still count as equally close. */
        const val TIE_METERS = 5.0
    }
}
