// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx

import java.time.Instant

/**
 * One sample of a recorded track, shared by the GPX and TCX models.
 *
 * Every value except the position is optional because real files omit them freely. Units are SI:
 * metres, metres per second, degrees Celsius, beats/revolutions/steps per minute and watts.
 *
 * @property latitude WGS-84 latitude in degrees, or `null` for position-less samples (indoor TCX).
 * @property longitude WGS-84 longitude in degrees, or `null` when [latitude] is `null`.
 * @property elevation Elevation above mean sea level in metres.
 * @property time Sample time; missing in route-style GPX files.
 * @property heartRate Heart rate in beats per minute.
 * @property cadence Cadence in rpm (cycling) or steps per minute per foot (running, as devices write it).
 * @property power Power in watts.
 * @property temperature Ambient temperature in degrees Celsius.
 * @property speed Instantaneous speed in metres per second.
 * @property distance Cumulative distance from the start in metres.
 */
public data class TrackPoint(
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double? = null,
    val time: Instant? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val temperature: Double? = null,
    val speed: Double? = null,
    val distance: Double? = null,
) {
    init {
        require((latitude == null) == (longitude == null)) {
            "latitude and longitude must both be present or both be absent"
        }
        if (latitude != null && longitude != null) {
            require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
            require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
        }
    }

    /** `true` when this sample carries a position. */
    val hasPosition: Boolean get() = latitude != null
}

/** An uninterrupted run of [points]. Separate segments usually mean the recording was paused. */
public data class TrackSegment(val points: List<TrackPoint>)
