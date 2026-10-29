// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.geo

import org.openactivity.model.LatLng

/**
 * A circular area whose presence near the start or end of a track must not be revealed to other people
 * (a home or work address).
 *
 * @property center centre of the hidden place.
 * @property radiusMeters radius of the circle, in metres (the product caps this at about 1.6 km).
 */
public data class PrivacyZone(val center: LatLng, val radiusMeters: Double) {
    init {
        require(radiusMeters > 0.0) { "radius must be positive: $radiusMeters" }
    }

    /** True when [p] lies within the circle enlarged by [marginMeters]. */
    public fun contains(p: LatLng, marginMeters: Double = 0.0): Boolean =
        Spherical.distance(center, p) <= radiusMeters + marginMeters
}

/**
 * Settings for [PrivacyTrimmer.trim].
 *
 * @property hideStartMeters track distance hidden from the start, whatever the location ("hide start
 *   anywhere"). Callers add their deterministic per-activity jitter to this value.
 * @property hideEndMeters same, from the end.
 * @property zones hidden places; trimming continues until the track leaves each matched circle.
 * @property zoneMarginMeters extra distance beyond a zone's radius before the track becomes visible, so the
 *   cut does not land exactly on the circle (which would let repeated activities reveal its centre).
 * @property endpointWindowFraction zones are only checked within this fraction of the track distance from
 *   each end; a run that merely passes a hidden place in the middle is not trimmed.
 * @property minVisibleMeters if less than this remains visible, the whole map is hidden.
 * @property maxHiddenFraction if more than this fraction of the distance is trimmed, the whole map is hidden.
 */
public data class PrivacyTrimOptions(
    val hideStartMeters: Double = 0.0,
    val hideEndMeters: Double = 0.0,
    val zones: List<PrivacyZone> = emptyList(),
    val zoneMarginMeters: Double = 0.0,
    val endpointWindowFraction: Double = 0.2,
    val minVisibleMeters: Double = 500.0,
    val maxHiddenFraction: Double = 0.6,
) {
    init {
        require(hideStartMeters >= 0.0 && hideEndMeters >= 0.0) { "trim distances must not be negative" }
        require(zoneMarginMeters >= 0.0) { "zone margin must not be negative" }
        require(endpointWindowFraction in 0.0..0.5) { "endpoint window must be within [0, 0.5]" }
        require(maxHiddenFraction in 0.0..1.0) { "max hidden fraction must be within [0, 1]" }
    }
}

/**
 * Result of trimming a track for public display.
 *
 * @property firstVisibleIndex index of the first point others may see.
 * @property lastVisibleIndex index of the last point others may see (inclusive).
 * @property hideEntireMap true when nothing should be shown (too little left, or no points at all).
 */
public data class PrivacyTrimResult(
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
    val hideEntireMap: Boolean,
) {
    /** The visible sub-list of [points] (the list that was trimmed), or an empty list when hidden. */
    public fun <T> visible(points: List<T>): List<T> =
        if (hideEntireMap) emptyList() else points.subList(firstVisibleIndex, lastVisibleIndex + 1)
}

/**
 * Builds the public view of a track by hiding its start and end.
 *
 * Only the map is trimmed; distance, time and other stats stay those of the full activity. The raw track
 * stays private to the owner.
 */
public object PrivacyTrimmer {
    /** Computes which part of [points] may be shown to people other than the owner. */
    public fun trim(points: List<LatLng>, options: PrivacyTrimOptions): PrivacyTrimResult {
        if (points.isEmpty()) return PrivacyTrimResult(0, -1, hideEntireMap = true)
        val n = points.size
        val cum = DoubleArray(n)
        for (i in 1 until n) cum[i] = cum[i - 1] + Spherical.distance(points[i - 1], points[i])
        val total = cum[n - 1]

        var first = 0
        while (first < n - 1 && cum[first] < options.hideStartMeters) first++
        var last = n - 1
        while (last > 0 && total - cum[last] < options.hideEndMeters) last--

        val window = total * options.endpointWindowFraction
        val droppedAtStart = zoneCut(points, options, indices = 0 until n) { cum[it] <= window }
        val droppedAtEnd = zoneCut(points, options, indices = (n - 1) downTo 0) { total - cum[it] <= window }
        first = maxOf(first, droppedAtStart)
        last = minOf(last, n - 1 - droppedAtEnd)

        val visible = if (last >= first) cum[last] - cum[first] else 0.0
        val hiddenFraction = if (total > 0.0) 1.0 - visible / total else 1.0
        val hide = last <= first || visible < options.minVisibleMeters || hiddenFraction > options.maxHiddenFraction
        return PrivacyTrimResult(first, last, hide)
    }

    /**
     * Walking along [indices] from one end, returns how many points must be dropped so the track has left
     * every zone it touched within the endpoint window (0 when no zone is touched).
     */
    private inline fun zoneCut(
        points: List<LatLng>,
        options: PrivacyTrimOptions,
        indices: IntProgression,
        inWindow: (Int) -> Boolean,
    ): Int {
        if (options.zones.isEmpty()) return 0
        var steps = 0
        var lastInside = -1
        val matched = LinkedHashSet<PrivacyZone>()
        for (i in indices) {
            if (!inWindow(i)) break
            val zone = options.zones.firstOrNull { it.contains(points[i]) }
            if (zone != null) {
                lastInside = steps
                matched += zone
            }
            steps++
        }
        if (matched.isEmpty()) return 0
        // Keep dropping points until the track is outside radius + margin of every zone it touched, so the
        // cut never lands inside the margin of an earlier zone (e.g. two neighbouring hidden places).
        var dropped = lastInside + 1
        val ordered = indices.toList()
        while (dropped < ordered.size && matched.any { it.contains(points[ordered[dropped]], options.zoneMarginMeters) }) {
            dropped++
        }
        return dropped
    }
}
