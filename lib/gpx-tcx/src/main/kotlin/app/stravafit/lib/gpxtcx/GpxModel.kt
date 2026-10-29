// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import java.time.Instant

/**
 * An in-memory GPX document. Only the parts relevant to activities and routes are modelled;
 * waypoints (`<wpt>`) and unknown extensions are ignored by the reader.
 *
 * @property creator Value of the `creator` attribute (device or app name), if any.
 * @property metadata Document-level metadata.
 * @property tracks Recorded tracks (`<trk>`).
 * @property routes Planned routes (`<rte>`).
 * @property truncated `true` when the reader recovered a partial document from a file that broke off part-way.
 */
public data class GpxDocument(
    val creator: String? = null,
    val metadata: GpxMetadata = GpxMetadata(),
    val tracks: List<GpxTrack> = emptyList(),
    val routes: List<GpxRoute> = emptyList(),
    val truncated: Boolean = false,
) {
    /** All track points of all tracks and segments, in document order. */
    val allTrackPoints: List<TrackPoint>
        get() = tracks.flatMap { track -> track.segments.flatMap { it.points } }
}

/** GPX `<metadata>`: [name], [description] and creation [time]. */
public data class GpxMetadata(
    val name: String? = null,
    val description: String? = null,
    val time: Instant? = null,
)

/**
 * A GPX `<trk>`.
 *
 * @property type Free-text activity type such as `running` or `cycling` (no standard vocabulary).
 */
public data class GpxTrack(
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
    val segments: List<TrackSegment> = emptyList(),
)

/** A GPX `<rte>`; route points rarely carry time or sensor data. */
public data class GpxRoute(
    val name: String? = null,
    val description: String? = null,
    val points: List<TrackPoint> = emptyList(),
)
