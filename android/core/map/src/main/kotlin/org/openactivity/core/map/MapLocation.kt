// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map

import androidx.compose.runtime.Immutable
import org.openactivity.model.LatLng

/**
 * A location fix to show as the user's puck. The map never reads GPS itself: callers feed it from
 * the app's own location stream (the same one the recorder uses), so there is one source of truth
 * and no second location engine draining the battery.
 *
 * @property position where the user is.
 * @property accuracyMeters horizontal accuracy radius, drawn as a translucent circle.
 * @property bearingDegrees direction of travel, if known.
 * @property speedMetersPerSecond ground speed, if known.
 * @property timeMillis fix time (epoch milliseconds).
 */
@Immutable
data class MapLocation(
    val position: LatLng,
    val accuracyMeters: Float? = null,
    val bearingDegrees: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val timeMillis: Long = System.currentTimeMillis(),
)
