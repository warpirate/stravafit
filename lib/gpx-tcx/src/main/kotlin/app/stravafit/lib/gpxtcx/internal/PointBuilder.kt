// SPDX-License-Identifier: Apache-2.0
package app.stravafit.lib.gpxtcx.internal

import app.stravafit.lib.gpxtcx.TrackPoint
import java.time.Instant

/** Mutable accumulator for one point while its element is being read. */
internal class PointBuilder {
    var latitude: Double? = null
    var longitude: Double? = null
    var elevation: Double? = null
    var time: Instant? = null
    var heartRate: Int? = null
    var cadence: Int? = null
    var power: Int? = null
    var temperature: Double? = null
    var speed: Double? = null
    var distance: Double? = null

    /** `true` when the coordinates are present and within WGS-84 bounds. */
    val hasValidPosition: Boolean
        get() {
            val lat = latitude ?: return false
            val lon = longitude ?: return false
            return lat in -90.0..90.0 && lon in -180.0..180.0
        }

    /** Builds the point; an invalid or partial position is dropped rather than failing the file. */
    fun build(): TrackPoint {
        val valid = hasValidPosition
        return TrackPoint(
            latitude = if (valid) latitude else null,
            longitude = if (valid) longitude else null,
            elevation = elevation,
            time = time,
            heartRate = heartRate,
            cadence = cadence,
            power = power,
            temperature = temperature,
            speed = speed,
            distance = distance,
        )
    }
}
